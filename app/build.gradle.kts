plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "gr.volley.tacticsboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "gr.volley.tacticsboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

kotlinOptions {
    jvmTarget = "17"
}
