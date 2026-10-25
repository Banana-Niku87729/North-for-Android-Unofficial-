@echo off
cd /d "%~dp0"

echo [1/3] 変更されたファイルをステージング中 (git add)...
git add .

echo [2/3] コミットを作成中 (git commit)...
rem 日時をコミットメッセージにして自動コミットします
set CURRENT_TIME=%date% %time%
git commit -m "Auto update: %CURRENT_TIME%"

echo [3/3] GitHubへアップロード中 (git push)...
git push

echo --------------------------------------------------
echo GitHubへの更新が完了しました！
pause
