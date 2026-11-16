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
        versionCode = 5
        versionName = "2.0"
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

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
}
