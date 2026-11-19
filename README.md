# Mapa Radio

Mapa de navegación estilo Tesla, hecho a medida para el radio Android del
usuario (ALPS Q91-A5-CPL, AC8227L, 32 bits, Android 8.1) y solo para
República Dominicana.

## v1 (esta rama, prueba en el teléfono)

APK de prueba general para que el usuario pruebe el diseño en su teléfono
(Samsung SM-S938B, arm64). Mapa en línea, sin nada offline todavía.

- Motor: MapLibre Native **10.3.7** — la última serie que corre con
  OpenGL ES 2.0. Desde 11.0.0 exige ES 3.0 y el radio (Mali-450) solo
  llega a ES 2.0. La versión del radio usará esta misma versión.
- Mapa: vectorial en línea de OpenFreeMap (sin clave), estilos
  `dark` (noche) y `liberty` (día).
- Ruta: servidor demo de OSRM (en línea). La versión del radio usará
  GraphHopper offline.
- Búsqueda: Photon (photon.komoot.io), tolerante a errores, con
  preferencia por República Dominicana.
- Pegado a la calle (snap-to-road): con ruta activa el carro se dibuja
  SOBRE la polilínea de la ruta; el GPS solo mide el avance. Estacionado
  (velocidad ~0) el carro se congela para que el ruido del GPS no lo mueva.
  Si el carro se sale de la ruta de forma seguida, recalcula solo.
- Guía estilo Tesla: panel a un lado con la maniobra, la calle y los
  metros; crece cuando el giro está cerca. Zoom automático: se acerca al
  acercarse un giro, se aleja en recto; en trayecto largo todo recto pasa
  a vista totalmente de arriba (pitch 0) con estilo simplificado (sin
  edificios ni zonas, solo calles, ruta y nombres).
- Controles: brújula (norte / hacia donde voy), día/noche, zoom +/−
  (anula el auto-zoom 20 s y luego vuelve solo).
- Lugares guardados en el teléfono (DataStore, sin cuentas).
- Long-press en el mapa: muestra el lugar y permite ir o guardarlo.

## Identidad

- Paquete: `app.maparadio.android`
- versionCode 1, versionName `1.0-test`
- minSdk 26, targetSdk/compileSdk 36
- v1: solo `arm64-v8a` (teléfono). La versión del radio será
  `armeabi-v7a` con mapa y rutas offline de RD.

## v1.6 (en construcción)

- Lugares guardados por categorías: Locales, Restaurantes y Favoritos.
  Cápsula de tres chips directamente debajo del buscador (mismo ancho
  que él); tocar un chip muestra los lugares de esa categoría en la tira
  de abajo (tocar = ruta, mantener = borrar). Al guardar un punto
  (long-press -> Guardar) se elige la categoría. Los lugares viejos
  migran a Favoritos.
- Enlace con el teléfono: el radio escucha en la red local (hotspot
  del teléfono, TCP 8080 + descubrimiento UDP 53535) y la app
  "Mapa Radio Móvil" le manda destinos: el pin aparece y la ruta se
  traza sola. La IP del radio se ve en Ajustes -> Teléfono.

## App del teléfono: Mapa Radio Móvil

Módulo `companion/` (paquete `app.maparadio.companion`, v1.0-test).
Sin mapa, ligera y rápida: recibe lo compartido desde Google Maps
(geo:, enlaces de google.com/maps y maps.app.goo.gl, resolviendo el
enlace corto), tiene su propio buscador Photon solo de RD, encuentra
el radio solo por broadcast y lo manda con un botón grande
"Enviar al radio". Si no lo encuentra, se escribe la IP a mano.
Compila en CI con `.github/workflows/build-companion.yml`.

## Compilación

GitHub Actions (`.github/workflows/build-mapa.yml`): push a la rama
`mapa-radio` → `./gradlew :app:assembleDebug` → artefacto
`mapa-radio-debug-apk`.

Firma: `~/workspace/mapa-radio.keystore` (alias `androiddebugkey`). TODAS
las versiones futuras deben firmarse con ese keystore para instalar
encima de la anterior.

## v1.2 (en construcción)

- Ajustes (engranaje): modo Oscuro / Claro / Automático (guardado, el
  botón rápido de día/noche fija el modo explícito); descarga y
  actualización del mapa offline de RD (OfflineManager, dos regiones
  dark+liberty, zoom 6–14, límite de tiles subido a 200.000) con progreso,
  tamaño y borrado.
- Botón de centrar (mira): fix fresco y preciso (≤30 m) con timeout de 8 s.
- Iconos más pequeños (botones 40dp, antes 56dp), barra de búsqueda más
  fina, panel de guía compacto y favoritos minimalistas de una línea.
- Mini-reproductor: lee TODAS las sesiones activas vía
  NotificationListenerService + MediaSessionManager (Spotify, YouTube
  Music, ViTune…), con anterior / play-pausa / siguiente y cambio de
  reproductor; en Ajustes se explica y se abre el acceso a notificaciones.
- Ubicación precisa: FusedLocationProvider (GPS+WiFi+celda) como fuente
  principal; solo se mueve el carro con fix ≤25 m (gracia de 10 s y
  tope de salto de 150 m para fixes de 25–60 m); nunca fixes viejos
  (>30 s) ni peores de 60 m; LocationManager solo de fallback.
- Búsqueda solo República Dominicana: Photon con countrycode=DO más
  filtro en cliente por cuadro de RD (lat 17.5–19.9, lon -72.0 a -68.6).

## v1.0 Final (radio, no es prueba)

- Build **release** con R8 + shrinkResources (las debug son lo que la
  hacia lenta), `versionCode 100`, `versionName 1.0-radio`, variante
  `radio`: **solo `armeabi-v7a`** (32-bit, AC8227L). El APK 32-bit
  tambien corre en el telefono 64-bit para probarlo antes del radio.
- Rutas **100% offline con GraphHopper 10.0**: el grafo de RD (extracto
  Geofabrik de Hispaniola, perfil carro + CH) se construye en CI
  (modulo `:tools`, misma version que la app) y va empaquetado en el
  APK (`assets/graphhopper`); en el primer arranque se copia al
  almacenamiento interno. Previsualizacion con hasta 3 alternativas
  offline; OSRM queda solo de respaldo con internet.
- Guia estilo Tesla igual que en v1.7 (panel que crece al girar,
  auto-zoom, cenital en rectas, carro pegado a la calle). Todo lo de
  v1.7 se mantiene: previsualizacion, favoritos por categoria,
  enlace del telefono, musica en 3 capsulas, mapa offline por WiFi,
  limites solo RD.
- Compilacion: `.github/workflows/build-mapa-final.yml` (se dispara
  al cambiar ese workflow o `final-release-trigger.txt`).

## v1.0.1 Final (arreglo del cierre al cargar el mapa)
- Causa: R8 borro el sistema de modulos de MapLibre. MapLibre carga
  por reflexion (Class.forName) el cargador de la libreria nativa
  (LibraryLoaderProviderImpl) y el cliente HTTP (HttpRequestImpl, con
  OkHttp), y las reglas del AAR no los conservan. En el dex de la
  v1.0 Final faltaban LibraryLoader, ModuleProviderImpl,
  HttpRequestImpl y TODO OkHttp (verificado contra el dex de la v1.7
  debug, donde estan todos). La app abria bien porque
  Mapbox.getInstance() no toca esos modulos; el cierre llegaba al
  cargar el mapa (primera llamada nativa / primer fetch del estilo).
- Arreglo: `-keep class com.mapbox.mapboxsdk.**`, okhttp3, okio y
  gson en `app/proguard-rules.pro`. versionCode 101, 1.0.1-radio.
- Nuevo: registro de errores — un manejador de excepciones no
  controladas escribe la traza en filesDir/crash.log (version, fecha,
  modelo, hilo) y Ajustes -> Registro de errores la muestra y la
  comparte (ACTION_SEND con el texto). Sin internet ni dependencias.

## v1.0.2 Final (arreglo REAL del cierre al cargar el mapa)
- La v1.0.1 NO arreglo el cierre. El registro de errores del usuario
  (19 nov 10:35:33, v1.0.1, API 27, hilo gh-init) lo dejo claro:
  `java.lang.VerifyError: Verifier rejected class
  com.graphhopper.storage.RAMDataAccess` — GraphHopper 10.0 usa
  VarHandle (byteArrayViewVarHandle) en RAMDataAccess y el
  verificador DEX de API 27 rechaza ese bytecode. El arreglo R8 de
  la v1.0.1 era real en el dex pero irrelevante para este cierre.
- Arreglo: el grafo se abre con DAType MMAP (`gh.init(config)` con
  `graph.dataaccess=MMAP`, antes de los setters). Verificado en el
  jar de GH 10.0: RAMDataAccess es la UNICA clase que referencia
  VarHandle; MMapDataAccess usa MappedByteBuffer y su unico Unsafe
  (invokeCleaner) solo corre en close(). El grafo empaquetado no
  cambia (el formato en disco no depende del DAType).
- Blindaje: se atrapa Throwable (no solo Exception) alrededor de
  GraphHopper.load() y de las rutas, con Toast "Rutas sin conexión
  no disponibles" si falla la carga; un fallo del router nunca
  vuelve a cerrar la app (respaldo OSRM con internet).
  versionCode 102, 1.0.2-radio.

## v1.0.3 Final (ruta no se calculaba + chips solo icono + carro enorme)
- Prueba del usuario (19 nov ~13:20 AST, v1.0.2, directo en el radio):
  el mapa carga y va fluido (el cierre esta arreglado), pero "Me sale
  que no se pudo calcular la ruta y tiene Internet". Causa: no se pudo
  probar en el radio cual capa fallaba, porque TODOS los fallos
  colapsaban en el mismo Toast generico sin motivo ni registro. Los
  dos OSRM (project-osrm.org y routing.openstreetmap.de) responden
  bien desde fuera, asi que el fallo estaba en el camino del radio
  (hotspot lento/limitado, timeout 10/15 s) y/o en el grafo offline
  aun copiandose (82 MB, decenas de segundos en el primer arranque).
- Arreglo de rutas: (1) motivo concreto en la UI y en el registro
  (CrashLog.logEvent): GH_NOT_READY / GH_ERROR / OSRM_NETWORK /
  OSRM_HTTP / OSRM_NO_ROUTE, con detalle; (2) si el grafo aun se esta
  cargando y la ruta online fallo, se espera hasta 45 s a que este
  listo ("Preparando rutas sin conexión…") y se reintenta offline;
  (3) si no hay fix GPS, se espera hasta 20 s ("Esperando señal
  GPS…") y si no llega se sigue con Santo Domingo como siempre;
  (4) OSRM con espejo: si router.project-osrm.org falla, se prueba
  routing.openstreetmap.de (misma API); (5) estado de carga del
  grafo (GhStatus NOT_STARTED/LOADING/READY/FAILED + loadError).
- Chips de categorias (v1.6) ahora SOLO icono: los tres TextView con
  texto pasan a ImageView (tienda/campana/estrella), mismo tamano,
  mismo comportamiento (tocar muestra la categoria, tocar otra vez
  la esconde); el texto queda solo en contentDescription.
- Carro enorme (foto del usuario 19 nov 12:24 p. m.): el marcador
  era un bitmap fijo de 110x180 px = 110x180 dp en el radio (160 dpi),
  casi un tercio de la pantalla. Ahora se dibuja a ~48dp de ancho
  (48x78 dp, mismo dibujo escalado por el canvas, x densidad para
  que sea nitido); la rotacion y el snap-to-road no cambian.
- Capsula de musica con bordes un poco redondeados (v1.0.3):
  bg_capsule_music pasa de 24dp a 14dp de radio (sutil, no muy
  redonda); mismo tamano, mismo layout, plana, sin blur ni sombras.
  versionCode 103, 1.0.3-radio.
