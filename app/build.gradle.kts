plugins {
    id("com.android.application")
    // Sin org.jetbrains.kotlin.android: AGP 9.0+ trae soporte Kotlin integrado.
}

android {
    namespace = "app.maparadio.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.maparadio.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.6-test"

        // v1 de prueba: solo arm64 (telefono del usuario). La version del
        // radio usara armeabi-v7a.
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // MapLibre 10.3.7: ULTIMA serie que corre con OpenGL ES 2.0.
    // Desde 11.0.0 exige ES 3.0 y el radio (Mali-450) solo llega a ES 2.0.
    // Se usa la misma version en v1 y en la version del radio.
    implementation("org.maplibre.gl:android-sdk:10.3.7")
    // OkHttp viene como dependencia transitiva de MapLibre; se declara
    // explicito para usarlo en OSRM y Photon.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Ubicacion precisa: FusedLocationProvider (GPS+WiFi+celda) como
    // fuente principal en v1.2; LocationManager queda de fallback.
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
