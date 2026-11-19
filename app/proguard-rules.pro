# MapLibre: las reglas de su AAR NO cubren su sistema de modulos.
# MapLibre carga por reflexion (Class.forName con el nombre de la
# clase) el cargador de la libreria nativa (LibraryLoaderProviderImpl)
# y el cliente HTTP (HttpRequestImpl, que usa OkHttp). R8 no ve esas
# referencias y los borra: en la v1.0 Final faltaban LibraryLoader,
# ModuleProviderImpl, HttpRequestImpl y TODO OkHttp, y la app se
# cerraba al cargar el mapa (la primera llamada nativa o el primer
# fetch del estilo). Se conserva el SDK entero y sus clientes HTTP.
# (La v1.7 debug no fallaba porque sin R8 nada se borra.)
-keep class com.mapbox.mapboxsdk.** { *; }
-keep class com.mapbox.geojson.** { *; }
-keep class com.google.gson.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
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
# GraphHopper 7.0 (v1.0.7) trae jakarta.activation-api, que es SOLO
# la API: las clases de implementacion com.sun.activation.registries.*
# (LogSupport, MailcapFile) no estan en el classpath y R8 las exige
# ("Missing classes detected", CI v1.0.7 run 44634329988). Nada en la
# app usa javax.activation en tiempo de ejecucion (es codigo de
# escritorio para leer archivos mailcap): solo se silencia el aviso.
-dontwarn com.sun.activation.**
# MapLibre, OkHttp y Play Services traen sus propias reglas de
# consumidor en sus AAR, pero NO cubren el sistema de modulos de
# MapLibre (ver los -keep de arriba): no quitarlos.
