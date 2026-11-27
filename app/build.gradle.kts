plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val musicflowSigningStore = System.getenv("MUSICFLOW_SIGNING_KEYSTORE")
android {
    namespace = "com.musicflow.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.musicflow.preview"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 754
        versionName = "0.7.54"
    }
    // Signed releases use one persistent key stored in GitHub Actions secrets.
    // Local debug builds keep working when the release key is unavailable.
    signingConfigs {
        if (!musicflowSigningStore.isNullOrBlank()) {
            create("musicflowRelease") {
                storeFile = file(musicflowSigningStore)
                storePassword = System.getenv("MUSICFLOW_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("MUSICFLOW_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("MUSICFLOW_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Debug builds must never replace a signed production package.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        getByName("release") {
            isMinifyEnabled = false
            if (!musicflowSigningStore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("musicflowRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.core:core:1.16.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
