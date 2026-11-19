// Constructor del grafo offline de RD (Hispaniola) para la version
// final del radio. Modulo Java puro: usa la MISMA version de
// GraphHopper que la app (9.1) — el formato del grafo depende de
// la version y el grafo empaquetado en el APK tiene que abrirlo la app.
import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.graphhopper:graphhopper-core:9.1")
    // Para ver el progreso del import en los logs de CI.
    runtimeOnly("ch.qos.logback:logback-classic:1.5.8")
}

// ./gradlew :tools:buildGraph -Ppbf=<ruta al .osm.pbf> -PgraphDir=<salida>
val mainSourceSet = extensions.getByType<SourceSetContainer>().getByName("main")

tasks.register<JavaExec>("buildGraph") {
    group = "graphhopper"
    description = "Construye el grafo de RD (perfil carro + CH) desde el extracto OSM."
    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("tools.BuildGraph")
    // El import de Hispaniola cabe de sobra en 2 GB; el runner tiene 7.
    maxHeapSize = "2g"
    doFirst {
        val pbf = project.findProperty("pbf") as? String
            ?: throw GradleException("Falta -Ppbf=<ruta al .osm.pbf>")
        val graphDir = project.findProperty("graphDir") as? String
            ?: throw GradleException("Falta -PgraphDir=<directorio de salida>")
        args(pbf, graphDir)
    }
}
