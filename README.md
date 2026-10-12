<<<<<<< HEAD
# north Android クライアント（最小構成）

ログイン（OAuth + PKCE）→ ホームタイムライン → 投稿 → いいね、まで。

## 手順
1. **Worker**: `cd worker && npx wrangler deploy` → 出てきたURLを控える
2. **north**: 設定 › 開発者向け でアプリ登録
   - リダイレクトURI: `https://<WorkerのURL>/callback`
   - 発行された client_id を控える
3. **Android Studio**: 「Empty Activity (Compose)」で `dev.rec877.north` として新規作成し、
   `app/src/main/` の `AndroidManifest.xml` と `java/dev/rec877/north/*.kt` を上書き/追加
4. `Config.kt` の `CLIENT_ID` と `REDIRECT_URI` を書き換える
5. 依存関係（`app/build.gradle.kts`）に追加:
   ```kotlin
   plugins { kotlin("plugin.serialization") }   // バージョンはKotlin本体に合わせる
   dependencies {
       implementation("com.squareup.okhttp3:okhttp:4.12.0")
       implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
       implementation("androidx.browser:browser:1.8.0")
       // Compose / material3 / activity-compose / lifecycle は新規プロジェクトのテンプレートのまま
   }
   ```

## 補足
- 認可codeはPKCEのverifierが無いと使えないので、Worker経由でも安全です（Workerはcodeを保存・ログしません）。
- 更新トークンはローテーション式。`AuthRepository` のMutexと `commit()` による即時保存は外さないでください。
- このコードはAndroid SDKの無い環境で書いたため、**ビルド確認はしていません**（Workerは構文チェックのみ実施）。
=======
# North-for-Android-Unofficial-
非公式です
>>>>>>> ceff7b2436b364c99ffedec5fa6694e1da91ccc8
