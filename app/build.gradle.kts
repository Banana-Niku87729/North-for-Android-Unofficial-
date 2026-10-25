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
        versionName = "1.1.0"

        buildConfigField("String", "BUG_REPORT_ENDPOINT", "\"$bugReportEndpoint\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
