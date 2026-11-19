@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ==================================================
echo  リリース用タグ作成 (push すると GitHub Actions が APK をビルド)
echo ==================================================
echo.

rem ---- バージョン入力 ----
set /p VER=バージョンを入力してください (例: 1.2.3): 
if "%VER%"=="" (
    echo バージョンが入力されていません。中止します。
    goto :end
)
rem 先頭に v を付けて入力された場合は取り除く
if /i "%VER:~0,1%"=="v" set VER=%VER:~1%

echo %VER%|findstr /R "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo バージョンは 1.2.3 の形式で入力してください。中止します。
    goto :end
)

rem ---- リリース種別 ----
echo.
echo リリースの種類を選んでください
echo   [1] 通常リリース (regular)
echo   [2] 緊急リリース (emergency)
choice /c 12 /n /m "番号を入力 (1 or 2): "
if errorlevel 2 (
    set TYPE=emergency
) else (
    set TYPE=regular
)

set TAG=v%VER%-%TYPE%
echo.
echo 作成するタグ: %TAG%

rem ---- 既存タグの確認 ----
git rev-parse -q --verify "refs/tags/%TAG%" >nul 2>&1
if not errorlevel 1 (
    echo タグ %TAG% は既に存在します。中止します。
    goto :end
)

rem ---- 最終確認 ----
echo.
choice /c YN /m "このタグを作成して GitHub に push しますか"
if errorlevel 2 (
    echo 中止しました。
    goto :end
)

rem ---- build.gradle.kts の versionName / versionCode を更新（F-Droid はタグ時点のこの値を読む） ----
echo.
echo [1/4] build.gradle.kts のバージョンを更新中...
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\bump-version.ps1" -Version %VER%
if errorlevel 1 goto :fail

rem ---- 未コミットの変更があればコミット ----
set DIRTY=
for /f %%i in ('git status --porcelain') do set DIRTY=1
if defined DIRTY (
    echo.
    echo [2/4] 未コミットの変更をコミット中...
    git add .
    git commit -m "Release %TAG%"
    if errorlevel 1 goto :fail
) else (
    echo.
    echo [2/4] 未コミットの変更はありません。
)

echo [3/4] ブランチを push 中...
git push
if errorlevel 1 goto :fail

echo [4/4] タグ %TAG% を作成して push 中...
git tag %TAG%
if errorlevel 1 goto :fail
git push origin %TAG%
if errorlevel 1 goto :fail

echo.
echo --------------------------------------------------
echo タグ %TAG% を push しました。
echo GitHub の Actions タブでビルドの進行を確認できます。
echo --------------------------------------------------
goto :end

:fail
echo.
echo エラーが発生しました。上のメッセージを確認してください。

:end
echo.
pause