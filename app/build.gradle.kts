plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ch.albmedia.saver"
    compileSdk = 35

    defaultConfig {
        applicationId = "ch.albmedia.saver"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
