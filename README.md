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

## Compilación

GitHub Actions (`.github/workflows/build-mapa.yml`): push a la rama
`mapa-radio` → `./gradlew :app:assembleDebug` → artefacto
`mapa-radio-debug-apk`.

Firma: `~/workspace/mapa-radio.keystore` (alias `androiddebugkey`). TODAS
las versiones futuras deben firmarse con ese keystore para instalar
encima de la anterior.
