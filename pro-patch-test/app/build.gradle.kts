plugins { id("com.android.application") }

android {
    namespace = "com.musicflow.patchtest"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.musicflow.patchtest"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-test"
    }
}
