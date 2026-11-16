package mihon.buildlogic

import org.gradle.api.JavaVersion as GradleJavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget as KotlinJvmTarget

object AndroidConfig {
    // TEMPORAL para compilar en esta VM (16 nov 2026): la plataforma android-37
    // es solo de canal preview y no se pudo descargar aquí; 36 compila igual
    // (targetSdk 35, sin APIs de 37 en el código). Revertir a 37 en un equipo
    // con el SDK completo.
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 35
    const val MIN_SDK = 26
    const val NDK = "27.1.12297006"

    val JavaVersion = GradleJavaVersion.VERSION_17
    val JvmTarget = KotlinJvmTarget.JVM_17
}
