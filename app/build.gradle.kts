import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 公開して問題ない Cloudflare Worker のURLだけを渡す。Discord Webhook はWorkerの秘密情報に置く。
val bugReportEndpoint = (findProperty("BUG_REPORT_ENDPOINT") as String?)
    ?.replace("\\", "\\\\")
    ?.replace("\"", "\\\"")
    .orEmpty()

// リリース署名: CI では環境変数、ローカルでは keystore.properties(.gitignore 済み)から読む。
// どちらも無ければ署名なしでビルドし、一部だけ設定されている場合は構成エラーにする。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }

val releaseKeyStorePath = signingValue("KEYSTORE_PATH", "storeFile")
val releaseKeyStorePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("KEY_PASSWORD", "keyPassword")
val signingValues = listOf(releaseKeyStorePath, releaseKeyStorePassword, releaseKeyAlias, releaseKeyPassword)
if (signingValues.any { it != null } && signingValues.any { it == null }) {
    throw GradleException(
        "Incomplete release signing configuration. Set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD together."
    )
}
if (releaseKeyStorePath != null) {
    val keyStoreFile = file(releaseKeyStorePath)
    if (!keyStoreFile.isFile || keyStoreFile.length() == 0L) {
        throw GradleException("Release keystore is missing or empty: $releaseKeyStorePath")
    }
}

android {
    namespace = "dev.rec877.north"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.rec877.north"
        minSdk = 26
        targetSdk = 36
        // F-Droid / 更新判定のため、リリースごとに必ず増やすこと（CIなら -PVERSION_CODE=... で渡せる）。
        // F-Droid の自動更新検出は、この行の数値リテラルを読むので、既定値は数値のまま残す。
        versionCode = (findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 10201
        // CI ではタグから -PVERSION_NAME=1.2.3 を渡す。ローカルでは下の既定値。
        versionName = (findProperty("VERSION_NAME") as String?) ?: "1.2.1"

        // x86 / x86_64 は実機でほぼ使われず、LiteRT のネイティブライブラリ分だけ APK が膨らむ。
        // splits ではなく abiFilters にしているのは、UpdateManager が Release の最初の .apk を取るため
        // (APK を複数に分けると別 ABI のものを掴んで端末に入らなくなる)。
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        buildConfigField("String", "BUG_REPORT_ENDPOINT", "\"$bugReportEndpoint\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 配布形態の違い（コードは src/full と src/fdroid の Distribution.kt）:
    //   full   = GitHub Releases の APK。アプリ内アップデートと判定モデルの初回ダウンロードあり。
    //   fdroid = F-Droid 向け。アプリ内アップデート無し、REQUEST_INSTALL_PACKAGES 無し、モデルは同梱のみ。
    // CI は assembleFullRelease を使い、F-Droid の metadata は gradle: [fdroid] を指定する。
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution" }
        create("fdroid") { dimension = "distribution" }
    }

    // Google 署名用の依存関係メタデータ(暗号化ブロブ)をAPKへ入れない。F-Droid の再現ビルドで差が出るため。
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            if (releaseKeyStorePath != null) {
                storeFile = file(releaseKeyStorePath)
                storePassword = releaseKeyStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeyStorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 次の段階: true にして isShrinkResources も有効化すると更に小さくなる。
            // リリースビルドで全画面を動作確認してから切り替えること。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // パッケージ名の後ろに自動で「.beta」を付与して別アプリ化
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-DEBUG"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.ai.edge.litert:litert:1.4.1")
    implementation("com.google.ai.edge.litert:litert-api:1.4.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.browser:browser:1.8.0")
    // 画像表示（追加）
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    // 動画再生（追加）
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0") // LocalLifecycleOwner（動画をバックグラウンドで止める）
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

}