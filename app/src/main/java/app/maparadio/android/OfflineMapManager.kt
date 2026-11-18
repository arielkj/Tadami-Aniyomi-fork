package app.maparadio.android

import android.content.Context
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.offline.OfflineManager
import com.mapbox.mapboxsdk.offline.OfflineRegion
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition
import org.json.JSONObject

/**
 * Mapa sin conexion de Republica Dominicana (v1.2).
 *
 * Descarga la region de RD para los DOS estilos (oscuro y claro) con
 * OfflineManager, para que cambiar de modo funcione tambien sin internet.
 * Una vez descargada, MapLibre sirve los tiles desde la base offline
 * cuando no hay red (el estilo y sus fuentes quedan dentro de la region).
 *
 * Zoom 6-14: a zoom 15 el numero de tiles se cuadruplica y la descarga
 * se vuelve demasiado grande para un telefono de prueba; a 14 ya se ven
 * todas las calles con nombre, que es lo que necesita la navegacion.
 */
object OfflineMapManager {

    private const val REGION_DARK = "rd-dark"
    private const val REGION_LIGHT = "rd-light"

    // Cuadro de Republica Dominicana.
    private val DR_BOUNDS: LatLngBounds = LatLngBounds.Builder()
        .include(LatLng(19.9, -68.6)) // noreste
        .include(LatLng(17.5, -72.0)) // suroeste
        .build()

    private const val MIN_ZOOM = 6.0
    private const val MAX_ZOOM = 14.0

    // El limite por defecto de MapLibre es 6000 tiles por region; RD a
    // zoom 6-14 lo supera con creces, asi que se sube antes de descargar.
    private const val TILE_LIMIT = 200_000L

    data class RegionInfo(val name: String, val complete: Boolean, val sizeBytes: Long)

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    private fun metadata(name: String): ByteArray =
        JSONObject().put("name", name).toString().toByteArray(Charsets.UTF_8)

    private fun regionName(region: OfflineRegion): String? {
        return try {
            JSONObject(String(region.metadata, Charsets.UTF_8)).optString("name")
        } catch (_: Exception) {
            null
        }
    }

    private fun listOurs(
        context: Context,
        onResult: (List<OfflineRegion>) -> Unit,
        onError: (String) -> Unit,
    ) {
        OfflineManager.getInstance(context).listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(regions: Array<OfflineRegion>?) {
                    onResult((regions ?: emptyArray()).filter {
                        regionName(it) == REGION_DARK || regionName(it) == REGION_LIGHT
                    })
                }

                override fun onError(error: String) = onError(error)
            }
        )
    }

    /** Estado actual: que regiones existen, si estan completas y su tamano. */
    fun queryStatus(context: Context, onResult: (List<RegionInfo>) -> Unit) {
        listOurs(context, { regions ->
            if (regions.isEmpty()) {
                onResult(emptyList())
                return@listOurs
            }
            val out = ArrayList<RegionInfo>()
            var pending = regions.size
            for (region in regions) {
                val name = regionName(region) ?: continue
                region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) {
                        synchronized(out) {
                            if (status != null) {
                                out.add(RegionInfo(name, status.isComplete, status.completedResourceSize))
                            } else {
                                out.add(RegionInfo(name, false, 0L))
                            }
                            pending--
                            if (pending == 0) onResult(out)
                        }
                    }

                    override fun onError(error: String?) {
                        synchronized(out) {
                            out.add(RegionInfo(name, false, 0L))
                            pending--
                            if (pending == 0) onResult(out)
                        }
                    }
                })
            }
        }, { onResult(emptyList()) })
    }

    /**
     * Descarga (o re-descarga) las dos regiones. onProgress recibe
     * 0..1 y el tamano descargado hasta ahora. onDone: exito o error.
     */
    fun downloadAll(
        context: Context,
        styleDarkUrl: String,
        styleLightUrl: String,
        onProgress: (Float, Long) -> Unit,
        onDone: (Boolean, String?) -> Unit,
    ) {
        val manager = OfflineManager.getInstance(context)
        manager.setOfflineMapboxTileCountLimit(TILE_LIMIT)
        // Borrar antes las nuestras para no duplicar regiones.
        deleteAll(context) {
            val jobs = listOf(REGION_DARK to styleDarkUrl, REGION_LIGHT to styleLightUrl)
            val totals = LongArray(jobs.size) // recursos completados por region
            val required = LongArray(jobs.size)
            var created = 0
            var failed: String? = null
            for ((index, job) in jobs.withIndex()) {
                val (name, styleUrl) = job
                val definition = OfflineTilePyramidRegionDefinition(
                    styleUrl,
                    DR_BOUNDS,
                    MIN_ZOOM,
                    MAX_ZOOM,
                    context.resources.displayMetrics.density,
                )
                manager.createOfflineRegion(
                    definition,
                    metadata(name),
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(region: OfflineRegion) {
                            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                                override fun onStatusChanged(status: OfflineRegionStatus) {
                                    totals[index] = status.completedResourceCount
                                    required[index] = status.requiredResourceCount
                                    onProgress(
                                        if (required.sum() > 0) {
                                            totals.sum().toFloat() / required.sum()
                                        } else 0f,
                                        status.completedResourceSize,
                                    )
                                    if (status.isComplete) {
                                        created++
                                        if (created == jobs.size) onDone(true, null)
                                    }
                                }

                                override fun onError(error: com.mapbox.mapboxsdk.offline.OfflineRegionError) {
                                    if (failed == null) {
                                        failed = error.message
                                        onDone(false, error.message)
                                    }
                                }

                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    if (failed == null) {
                                        failed = "Límite de tiles superado ($limit)"
                                        onDone(false, failed)
                                    }
                                }
                            })
                            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                        }

                        override fun onError(error: String) {
                            if (failed == null) {
                                failed = error
                                onDone(false, error)
                            }
                        }
                    },
                )
            }
        }
    }

    /** Borra las regiones de RD (las dos), si existen. */
    fun deleteAll(context: Context, onDone: () -> Unit) {
        listOurs(context, { regions ->
            if (regions.isEmpty()) {
                onDone()
                return@listOurs
            }
            var pending = regions.size
            for (region in regions) {
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        pending--
                        if (pending == 0) onDone()
                    }

                    override fun onError(error: String) {
                        pending--
                        if (pending == 0) onDone()
                    }
                })
            }
        }, { onDone() })
    }
}
