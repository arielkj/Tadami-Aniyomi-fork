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
# Dependencias transitivas de GraphHopper que referencian clases de
# escritorio (java.awt, javax.imageio, javax.xml.stream) que no
# existen en Android: ese codigo nunca se ejecuta en la app (es de
# lectura KML/TIFF y de OSGi), asi que solo se silencia el aviso.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.stream.**
-dontwarn aQute.bnd.annotation.**
-dontwarn org.apache.xmlgraphics.**
-dontwarn com.ctc.wstx.**
-dontwarn org.locationtech.jts.**
# MapLibre, OkHttp y Play Services traen sus propias reglas de
# consumidor en sus AAR; no hace falta repetirlas aqui.
