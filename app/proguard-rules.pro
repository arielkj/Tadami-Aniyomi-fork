# Reglas R8 para la version final (release) del radio.
# GraphHopper usa reflexion (Jackson, Janino, encoded values) y sus
# clases se cargan por nombre: se conserva el paquete entero.
-keep class com.graphhopper.** { *; }
-keep class com.carrotsearch.hppc.** { *; }
-keep class org.locationtech.jts.** { *; }
-keep class org.codehaus.janino.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.openstreetmap.osmosis.** { *; }
-dontwarn com.graphhopper.**
-dontwarn org.codehaus.janino.**
-dontwarn org.openstreetmap.osmosis.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.slf4j.**
# MapLibre, OkHttp y Play Services traen sus propias reglas de
# consumidor en sus AAR; no hace falta repetirlas aqui.
