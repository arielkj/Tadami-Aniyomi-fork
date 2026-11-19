plugins {
    id("com.android.application")
    // Sin org.jetbrains.kotlin.android: AGP 9.0+ trae soporte Kotlin integrado.
}

android {
    namespace = "app.maparadio.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.maparadio.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2-test"

        // Solo el telefono del usuario (arm64). La app del radio
        // seguira siendo la que corre en el radio (armeabi-v7a).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
