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
        versionCode = 8
        versionName = "1.7-test"
    }

    // Dos variantes: "phone" (pruebas en el telefono, arm64) y
    // "radio" (version final para el radio: 32-bit, release, v1.0).
    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            // Version de pruebas: solo arm64 (telefono del usuario).
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        create("radio") {
            dimension = "device"
            versionCode = 107
            versionName = "1.0.7-radio"
            // El radio es ARMv7 32-bit (AC8227L): SOLO armeabi-v7a.
            // El APK 32-bit tambien corre en el telefono 64-bit, asi
            // que se puede probar en el telefono antes del radio.
            ndk {
                abiFilters += "armeabi-v7a"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            // Version final: R8 + recursos recortados, no depurable.
            // Es lo que hace que la app sea fluida en el radio
            // (las versiones debug son lo que la hacia lenta).
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // GraphHopper 7.0 (v1.0.7) trae dependencias que 9.1 no traia
    // (jackson-dataformat-xml + woodstox/stax2, xmlgraphics-commons
    // + commons-io/commons-logging, jakarta.xml.bind-api +
    // jakarta.activation-api): varios JARs traen los mismos archivos
    // META-INF con contenido DISTINTO y AGP falla al fusionar los
    // recursos Java (":app:mergeRadioReleaseJavaResource", CI v1.0.7
    // run 44633509380: "2 files found with path
    // 'META-INF/NOTICE.md'"). Verificado jar por jar (md5): chocan
    // NOTICE.md y LICENSE.md (las dos jakarta), NOTICE (jackson
    // vs dataformat-xml vs xmlgraphics), LICENSE (jackson vacio vs
    // stax2 vs woodstox), NOTICE.txt y LICENSE.txt (commons-io vs
    // commons-logging) y los servicios Jackson JsonFactory y
    // ObjectCodec (core/databind vs dataformat-xml). Son solo textos
    // legales y registros ServiceLoader que la app no lee en tiempo
    // de ejecucion (GraphHopper construye XmlMapper directo): se
    // excluyen del APK. module-info.class NO se excluye: la v1.0.6
    // ya lo tenia duplicado (jackson-core + databind) y compilo.
    packaging {
        resources {
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/services/com.fasterxml.jackson.core.JsonFactory"
            excludes += "META-INF/services/com.fasterxml.jackson.core.ObjectCodec"
        }
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
    // GraphHopper 7.0 (v1.0.7): rutas 100% sin internet con el grafo
    // de RD empaquetado en el APK (lo construye el modulo :tools con
    // la MISMA version — el formato del grafo depende de la version,
    // no cambiarla sin reconstruir el grafo). Se bajo de 10.0 a 9.1
    // (v1.0.5) y de 9.1 a 7.0 (v1.0.7): en 10.0 el RAMDataAccess usa
    // VarHandle (VerifyError en API 27) y el MMapDataAccess usa
    // ByteBuffer.get(int, byte[], int, int), metodo que FALTA en el
    // framework de este radio ALPS (registro del usuario 19 nov:
    // "No virtual method get(I[BII)"). En 9.1 el RAMDataAccess es
    // Java puro y el grafo ya cargaba con largeHeap (v1.0.6), pero
    // el perfil "car" con CustomModel NO se puede crear: GraphHopper
    // compila el modelo a medida con Janino y Janino genera .class
    // de JVM, que ART no puede cargar en Android ("Cannot compile
    // expression: can't load this type of class file", registro del
    // usuario 19 nov 17:42:18). "fastest" NO existe en 8.0/9.1 (su
    // fabrica solo acepta "custom") y SI existe en 7.0
    // (FastestWeighting, Java puro, 0 referencias a Janino).
    // En 7.0 las clases de ruta (GHRequest, ResponsePath,
    // Instruction, Parameters) viven en el artefacto web-api, no en
    // core: se declara explicito.
    implementation("com.graphhopper:graphhopper-core:7.0")
    implementation("com.graphhopper:graphhopper-web-api:7.0")
}
