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
// どちらも無ければ署名なしでビルドされ、デバッグビルドや第三者のビルドは影響を受けない。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)

android {
    namespace = "dev.rec877.north"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.rec877.north"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        // CI ではタグから -PVERSION_NAME=1.2.3 を渡す。ローカルでは下の既定値。
        versionName = (findProperty("VERSION_NAME") as String?) ?: "1.1.5"

        // x86 / x86_64 は実機でほぼ使われず、LiteRT のネイティブライブラリ分だけ APK が膨らむ。
        // splits ではなく abiFilters にしているのは、UpdateManager が Release の最初の .apk を取るため
        // (APK を複数に分けると別 ABI のものを掴んで端末に入らなくなる)。
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        buildConfigField("String", "BUG_REPORT_ENDPOINT", "\"$bugReportEndpoint\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val path = signingValue("KEYSTORE_PATH", "storeFile")
            if (path != null) {
                storeFile = file(path)
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signingConfigs.getByName("release").storeFile != null) {
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