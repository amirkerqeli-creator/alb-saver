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
        versionCode = 4
        versionName = "1.3"
    }
    // Fester Schlüssel: so lässt sich jede neue APK als Update über die alte installieren
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
