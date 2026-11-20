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

## v1.0.4 Final (Bluetooth como segundo camino para mandar destinos)
- El usuario (19 nov ~13:37 AST) pidio que el destino tambien se
  pueda mandar por Bluetooth o WiFi Directo, ademas del hotspot.
  WiFi Directo NO se hizo: es poco confiable en Android 8.1 y
  mientras esta conectado el radio pierde el internet del hotspot
  (sin mapa online ni rutas online). Bluetooth clasico RFCOMM si:
  es el respaldo cuando no hay hotspot; el hotspot sigue siendo el
  principal (ademas le da internet al radio).
- UUID (el MISMO en el radio y en el telefono): SPP estandar
  00001101-0000-1000-8000-00805F9B34FB, servicio "MapaRadio".
- Radio (BluetoothLinkServer.kt, sin librerias nuevas, hilos propios,
  onResume/onPause igual que PhoneLinkServer): escucha RFCOMM; el
  telefono manda UNA linea JSON {"name", "lat", "lng"} y el radio
  contesta "OK" (vale, dentro de RD) o "BAD" (fuera de RD o JSON
  invalido). El destino se valida con PhoneLinkServer.parseDestination
  — la MISMA validacion que el POST /destination — y se maneja igual
  (pin + vista previa + Toast "Ruta recibida del teléfono"). En API 27
  BLUETOOTH / BLUETOOTH_ADMIN son de instalacion, no se pide nada.
- Si el Bluetooth esta apagado o el stack del radio no deja abrir el
  servidor, NO se cierra la app: queda en CrashLog.logEvent y se ve
  en Ajustes -> Telefono ("Bluetooth: escuchando / apagado / no
  disponible en este radio / no se pudo abrir (este radio no lo
  permite)"); el WiFi sigue funcionando.
- Telefono (BluetoothLink.kt + MainActivity, v1.0.2-test): "Enviar al
  radio" prueba WiFi primero y si falla (o no hay radio por WiFi)
  cae a Bluetooth solo; el radio se elige UNA vez de los dispositivos
  emparejados ("Elegir radio Bluetooth") y se recuerda
  (SharedPreferences); no se escanea (evita BLUETOOTH_SCAN). En
  Android 12+ pide BLUETOOTH_CONNECT en tiempo de ejecucion y si se
  niega lo dice claro. Toasts distintos por camino ("por WiFi" /
  "por Bluetooth").
- Para probarlo: (1) emparejar el telefono con el radio UNA vez en
  Ajustes de Bluetooth de Android; (2) en la app del telefono, "Elegir
  radio Bluetooth" y tocar el radio; (3) con el hotspot APAGADO,
  compartir un lugar desde Google Maps -> "Enviar al radio" (debe
  llegar por Bluetooth); (4) con el hotspot ENCENDIDO, enviar otra
  vez (debe llegar por WiFi como siempre).
- Si el Bluetooth del radio no deja abrir el servidor: en Ajustes ->
  Telefono saldra "Bluetooth: no se pudo abrir (este radio no lo
  permite)" y en Ajustes -> Registro de errores quedara el motivo
  (CrashLog.logEvent); el WiFi sigue siendo el camino. No hay nada
  que hacer en el telefono en ese caso: el fallo es del stack
  Bluetooth del radio, y la app lo aguanta sin cerrarse.
  versionCode 103 -> 104, versionName 1.0.4-radio (radio);
  companion versionCode 2 -> 3, versionName 1.0.2-test.

## v1.0.5 Final (rutas offline por fin en el radio + capsula de musica redondeada de verdad)
- Causa REAL de que la ruta no saliera (registro compartido por el
  usuario 19 nov ~14:59 AST, eventos 13:42-13:47, v1.0.4): "grafo
  offline no cargó (No virtual method get(I[BII)
  Ljava/nio/ByteBuffer; in class Ljava/nio/ByteBuffer; or its super
  classes (declaration of 'java.nio.ByteBuffer' appears in
  /system/framework/core-oj.jar))" + OSRM_NETWORK (DNS sin respuesta
  x4 y un "Handshake failed": el hotspot no tenia internet real en
  ese momento). O sea: las DOS vias fallaron por causas distintas —
  el offline por un metodo que falta en el framework ALPS, el online
  por falta de internet real (el icono de WiFi no garantiza
  internet). El cierre viejo de las 10:35:50 en el mismo registro es
  historia de la v1.0.1; la v1.0.4 no se ha vuelto a cerrar.
- La v1.0.2 esquivo el VerifyError de VarHandle con MMAP, pero el
  MMapDataAccess de GraphHopper 10.0 usa ByteBuffer.get(int, byte[],
  int, int) (bulk absoluto, de Java 9) y ese metodo NO EXISTE en el
  framework de este radio (dice API 27 pero su core-oj.jar esta
  recortado). Con MMAP el grafo nunca llego a cargar.
- Arreglo: bajar GraphHopper de 10.0 a **9.1** y abrir el grafo con
  DAType **RAM_STORE**. Verificado en los jars antes de decidir:
  en 9.1 NINGUNA clase referencia VarHandle; su RAMDataAccess es
  Java puro sobre byte[] (sin Unsafe, sin VarHandle, sin ByteBuffer
  en todo el jar); el unico que usa el bulk ByteBuffer.get es
  MMapDataAccess, que con RAM_STORE nunca se carga (la verificacion
  DEX solo rechaza la clase que se carga, y el NoSuchMethodError del
  bulk solo salta al llamarlo). La API que usa la app es identica
  en 9.1 (GHRequest, ResponsePath, Instruction, Parameters,
  CustomModel, CHProfile, Profile, load()/init()/route()). El
  String.isBlank() que usa GraphHopper al parsear los encoded
  values SI existe en este radio (la v1.0.4 llego hasta el load()).
  El grafo se reconstruye en CI con la misma 9.1 (:tools) y el id
  cambia a gh91-dr-v1 para que el radio lo vuelva a copiar en el
  primer arranque (el formato del grafo depende de la version: el
  grafo gh10 NO lo abre GH 9.1). RAM_STORE mete los 82 MB en el
  heap (4 GB de RAM, cabe) en vez de paginarlos como MMAP.
- Capsula de musica (foto del usuario 19 nov 2:03 p. m., v1.0.4):
  seguia con esquinas CUADRADAS aunque bg_capsule_music ya era de
  14dp. Causa: la portada llena la capsula de borde a borde y el
  clipToOutline del XML (con el outline que sale del fondo
  redondeado) no la recortaba en este radio — verificado en el APK
  de la 1.0.4: el layout trae clipToOutline=true y el fondo 14dp,
  y aun asi se veia cuadrada. Arreglo: outline EXPLICITO en codigo
  (MainActivity, al enlazar las vistas): ViewOutlineProvider con
  setRoundRect(0, 0, width, height, 14dp) en musicCard Y en
  musicArt, con clipToOutline=true en las dos — el arte queda
  redondeado aunque falle el recorte del contenedor; getOutline se
  llama en cada layout, asi que la animacion de peso vuelve a
  recortar en cada frame. Mismo radio 14dp, plano, sin blur ni
  sombras; la capsula del mapa (24dp) no se toca.
- Todo lo de la v1.0.4 se mantiene: Bluetooth RFCOMM, chips solo
  icono, carro ~48dp, registro de errores con motivo de ruta, OSRM
  con espejo, espera al grafo y al GPS.
  versionCode 104 -> 105, versionName 1.0.5-radio.

## v1.0.6 Final (largeHeap: el grafo ya carga)
- Prueba del usuario (19 nov ~18:04 AST, v1.0.5): el grafo 9.1/RAM
  NO cargaba por FALTA DE MEMORIA — "Failed to allocate a 1048592
  byte allocation with 617480 free bytes and 603KB until OOM, max
  allowed footprint 50331648, growth limit 50331648" (eventos
  16:59-17:01). El radio limita el heap de la app a 48 MB y el
  grafo necesita ~86 MB en memoria (edges 31 + geometry 21 + nodes
  7 + location_index 10 + nodes_ch_car 5 + shortcuts_car 10 +
  edgekv 2 MB). El respaldo OSRM tambien fallaba ("Handshake
  failed", sin internet real/TLS en este equipo).
- Arreglo (un solo cambio): android:largeHeap="true" en el
  <application> del AndroidManifest. Mismo GraphHopper 9.1, mismo
  DAType RAM_STORE, mismo grafo gh91-dr-v1 (no se reconstruyo).
  versionCode 105 -> 106, versionName 1.0.6-radio.

## v1.0.7 Final (rutas offline por fin: GraphHopper 7.0 + fastest)
- Prueba del usuario (19 nov ~17:42 AST, v1.0.6, evento de ruta):
  largeHeap FUNCIONO — el grafo ya carga (no mas OOM). NUEVO
  FALLO, el unico que quedaba: "grafo offline no cargó (Could not
  create weighting for profile: 'car'. ... Error: Cannot compile
  expression: can't load this type of class file)". Causa: el
  perfil "car" usaba un CustomModel y GraphHopper compila el modelo
  a medida con Janino en tiempo de ejecucion; Janino genera .class
  de JVM, que ART no puede cargar en Android. El modelo a medida
  NUNCA puede funcionar en este radio.
- Arreglo: bajar GraphHopper de 9.1 a **7.0** y usar el ponderado
  **"fastest"** de serie (FastestWeighting, Java puro, 0 referencias
  a Janino). "fastest" NO existe en 8.0/9.1 (su DefaultWeighting-
  Factory solo acepta "custom": verificado en el codigo fuente) y
  SI existe en 7.0 (verificado: la fabrica crea FastestWeighting y
  el RAMDataAccess de 7.0 tiene 0 referencias a VarHandle). La API
  que usa la app es identica en 7.0, con una excepcion: en 7.0 las
  clases de ruta (GHRequest, ResponsePath, Instruction, Parameters)
  viven en el artefacto graphhopper-web-api, no en core, asi que la
  app declara graphhopper-web-api:7.0 explicito. Detalle de 7.0: los
  valores codificados del vehiculo se crean con setVehiclesString
  ("car") y en setEncodedValuesString solo va "road_access", que
  exige "fastest" (su fabrica no conoce "car_access"); el perfil
  lleva setVehicle("car") + setWeighting("fastest").
  import.osm.ignored_highways SE MANTIENE: 7.0 lo exige (sin el,
  init() tira IllegalArgumentException).
- Verificado ANTES de construir (prueba local en JVM con los jars
  7.0 reales): grafo sintetico de Santo Domingo (16 nodos, 8 vias
  residenciales, CH preparado, archivos nodes_ch_car +
  shortcuts_car presentes, properties sin custom_model) carga con
  RAM_STORE y calcula una ruta de 650 m / 52 s por ALT_ROUTE (sin
  CH) y por CH, con 2 instrucciones. El grafo de RD se reconstruye
  en CI con la MISMA 7.0 (:tools) y el id cambia a gh70-dr-v1 (el
  formato del grafo depende de la version: el grafo gh91 NO lo abre
  GH 7.0). Todo lo demas se mantiene: largeHeap, RAM_STORE,
  Bluetooth RFCOMM, chips solo icono, carro ~48dp, capsula de
  musica 14dp, registro de errores.
  versionCode 106 -> 107, versionName 1.0.7-radio.

## v1.0.8 Final (la ruta la calcula el telefono y llega entera al radio)
- Problema: el grafo offline (GraphHopper 7.0, gh70-dr-v1) tarda
  minutos en cargar en el radio y el primer intento de ruta falla
  con "grafo offline no listo"; el respaldo OSRM del radio tampoco
  es fiable porque el hotspot muchas veces no tiene internet real.
  GraphHopper SE QUEDA como respaldo local, sin tocarlo.
- Arreglo: el TELEFONO calcula la ruta (tiene internet y GPS) y
  manda la ruta ENTERA al radio, que solo la dibuja y la guia.
  Telefono (OsrmRoute.kt, v1.0.3-test): origen = GPS del telefono
  (Fused lastLocation, si no LocationManager, si no un fix fresco
  de hasta 12 s; el intento Fused tiene tope de 8 s para no dejar
  el envio colgado; sin fix, aviso claro y no se manda nada); OSRM
  con los mismos dos servidores espejo y la misma User-Agent que el
  radio, primera ruta (la mas rapida); la polilinea se afina con
  Douglas-Peucker a 10 m (reimplementado en Kotlin; referencia:
  libs/geometry/simplification.hpp de CoMaps — CoMaps en si NO se
  usa: su nucleo es C++ con formato .mwm propio y un render que
  exige OpenGL ES 3.0, que el Mali-450 del radio no tiene). El JSON
  (PhoneRouteJson.kt, IDENTICO en los dos modulos salvo el paquete)
  es {"type":"route","name":...,"distance":...,"duration":...,
  "points":[[lat,lng],...],"steps":[{"t","m","n","d","s"}]} con
  coordenadas a 5 decimales; SD -> Santiago: 1.958 puntos OSRM,
  307 tras afinar, 7.803 bytes de JSON. Errores con motivo en la UI: sin
  internet en el telefono, OSRM sin ruta, sin ubicacion.
- Transporte: WiFi POST /route (PhoneLinkServer, 200/400) y
  Bluetooth RFCOMM (misma linea JSON, el radio la lee entera con
  readLine; el telefono espera hasta 30 s la respuesta). El mensaje
  de destino solo (sin "type") sigue funcionando igual; si el radio
  es viejo y contesta 404 a /route (o BAD por Bluetooth), el
  telefono cae al destino solo y el radio calcula como antes.
- Radio: la ruta recibida se muestra en el mismo panel de
  previsualizacion (una sola ruta, sin alternativas) y guia con el
  mismo panel y el mismo pegado a la calle; queda marcada como ruta
  del telefono. Desvio: NO falla de inmediato. La ruta sigue
  dibujada y el panel guia de vuelta al punto mas cercano de la
  linea (flecha con el rumbo + metros hasta la linea); al volver a
  caer sobre la linea, la guia sigue sola desde ese punto, sin
  internet y sin pedir otra ruta. Solo si el usuario se mantiene
  fuera (~30 s de fixes seguidos, o 10 fixes a mas de 500 m) se
  pasa al respaldo: con grafo offline listo se recalcula en local;
  si no, Toast "Te desviaste de la ruta. Pídela de nuevo desde el
  teléfono.", queda en el registro (CrashLog) y se cancela la guia.
- Companera v1.0.3-test, arreglo pequeno en la misma version: el
  titulo "Mapa Radio Movil" quedaba muy arriba y cubria parte de
  las notificaciones; la pantalla principal ahora baja su padding
  superior lo que mide la barra de estado (WindowInsets en
  MainActivity sobre el ScrollView raiz, id rootScroll). Si el
  telefono ya deja el contenido debajo de la barra, el inset es 0
  y no cambia nada. Sin tocar nada mas del diseno.
- Rutas sin conexion (misma v1.0.8, AMBAS opciones funcionando):
  el acceso al grafo pasa de RAM_STORE a MMAP (GraphHopper 7.0):
  verificado en el jar 7.0 (javap + escaneo completo del jar) que
  su MMapDataAccess usa gets RELATIVOS (position() + get(byte[],
  int, int)), no el get absoluto ByteBuffer.get(int, byte[], int,
  int) que falta en este radio (ese era de GH 10.0 y mato el MMAP
  de la v1.0.2). Con MMAP no se meten 81 MB en el heap ni la carga
  tarda minutos: el SO pagina el archivo bajo demanda. Si MMAP
  falla en el radio, se reintenta con RAM_STORE y el fallo queda en
  el registro. SIN PROBAR EN EL RADIO TODAVIA: MMAP es hipotesis
  verificada en jar y en JVM, no hecho en el aparato. La copia es
  reanudable (copy-progress.txt: si la app se cierra a mitad, solo
  se vuelve a copiar el archivo a medias; el marcador se escribe al
  final). noCompress NO se aplica: el grafo ya viene comprimido al
  53% en el APK (81 MB -> 42 MB) y descomprimir son segundos, no
  minutos; sin comprimir el APK crecería ~38 MB sin ganar nada.
  La espera en previsualizacion sube de 45 s a 180 s con mensaje
  de progreso ("Preparando rutas sin conexión… (N s)"). Cada fase
  queda en el registro con su tiempo (copia por archivo grande,
  load, total; LISTO y FALLO con motivo). El detalle ya no miente:
  READY y NOT_STARTED tenian el mismo texto "no listo". El grafo
  NO se reconstruye: sigue gh70-dr-v1. Verificado en JVM con el
  grafo real extraido del APK v1.0.7 (GhTest, misma config que el
  radio): MMAP carga en 955 ms, ALT_ROUTE en 1.339 ms y CH en
  201 ms; RAM_STORE carga en 1.821 ms, ALT_ROUTE en 839 ms y CH en
  86 ms. Ruta SD -> Santiago en ambos casos: 1 ruta, 151.100 m,
  6.294 s, 942 puntos, 12 instrucciones. Ademas, escaneo de los
  jars core y web-api 7.0: CERO referencias al get absoluto
  (I[BII)Ljava/nio/ByteBuffer ni a VarHandle.
  versionCode 107 -> 108, versionName 1.0.8-radio (radio);
  companion versionCode 3 -> 4, versionName 1.0.3-test.
