package app.maparadio.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import android.media.MediaMetadata
import android.os.SystemClock

/**
 * Musica (v1.2): lee TODOS los reproductores con sesion activa.
 *
 * El servicio en si no hace nada: su existencia (y que el usuario lo
 * active en Ajustes del sistema) es lo que permite a MediaSessionManager
 * devolver las sesiones activas de Spotify, YouTube Music, ViTune y
 * cualquier otro reproductor. Sin ese permiso, getActiveSessions
 * devuelve vacio (o lanza SecurityException) y la barra se oculta.
 */
class MusicListenerService : NotificationListenerService()

data class PlayerInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val controller: MediaController,
    /** Portada del album (null si el reproductor no da ninguna). */
    val albumArt: Bitmap?,
    /** Duracion de la cancion en ms (0 si se desconoce). */
    val durationMs: Long,
    /** Posicion actual en ms (0 si se desconoce). */
    val positionMs: Long,
)

object MusicController {

    /**
     * Cache de portadas por reproductor + cancion (v1.3): la tarjeta de
     * musica se refresca cada segundo para el progreso, pero la portada
     * solo se vuelve a leer cuando cambia la cancion. Sin esto, leer la
     * URI de la portada cada segundo gastaria CPU en el radio.
     */
    private val artCache = object : LinkedHashMap<String, Bitmap?>(4) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap?>): Boolean {
            return size > 4
        }
    }

    fun hasAccess(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun openAccessSettings(context: Context) {
        context.startActivity(
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Todas las sesiones de medios activas ahora mismo. */
    fun activePlayers(context: Context): List<PlayerInfo> {
        if (!hasAccess(context)) return emptyList()
        val msm = try {
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        } catch (_: Exception) {
            return emptyList()
        }
        val component = ComponentName(context, MusicListenerService::class.java)
        val sessions = try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        val pm = context.packageManager
        val out = ArrayList<PlayerInfo>()
        for (controller in sessions) {
            val pkg = controller.packageName ?: continue
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
            val md = try {
                controller.metadata
            } catch (_: Exception) {
                null
            }
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.music_no_player)
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist)
            val playing = try {
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
            } catch (_: Exception) {
                false
            }
            val state = try {
                controller.playbackState
            } catch (_: Exception) {
                null
            }
            val duration = try {
                md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            } catch (_: Exception) {
                0L
            }
            // Posicion en vivo: si suena, se suma lo transcurrido desde
            // que el reproductor reporto la posicion.
            val position = try {
                val base = state?.position ?: 0L
                if (playing && state != null) {
                    base + (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime)
                } else {
                    base
                }
            } catch (_: Exception) {
                0L
            }
            out.add(
                PlayerInfo(
                    pkg, appName, title, artist, playing, controller,
                    cachedAlbumArt(context, md, "$pkg|$title"), duration, position,
                )
            )
        }
        return out
    }

    /** Portada con cache: solo se decodifica cuando cambia la cancion. */
    private fun cachedAlbumArt(context: Context, md: MediaMetadata?, key: String): Bitmap? {
        if (artCache.containsKey(key)) return artCache[key]
        val art = albumArt(context, md)
        artCache[key] = art
        return art
    }

    /**
     * Portada del album, escalada a 480 px como maximo para no gastar
     * memoria. Prueba el bitmap directo, luego la URI (content:// del
     * reproductor). Null si no hay portada o no se puede leer.
     */
    private fun albumArt(context: Context, md: MediaMetadata?): Bitmap? {
        if (md == null) return null
        val direct = try {
            md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        } catch (_: Exception) {
            null
        }
        if (direct != null) return downscale(direct)
        val uri = try {
            md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val bytes = stream.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxDim / sample > 480) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downscale(bmp: Bitmap): Bitmap {
        val maxDim = maxOf(bmp.width, bmp.height)
        if (maxDim <= 480) return bmp
        val scale = 480f / maxDim
        return Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true
        )
    }

    fun togglePlayPause(player: PlayerInfo) {
        try {
            val tc = player.controller.transportControls
            if (player.isPlaying) tc.pause() else tc.play()
        } catch (_: Exception) {
        }
    }

    fun skipNext(player: PlayerInfo) {
        try {
            player.controller.transportControls.skipToNext()
        } catch (_: Exception) {
        }
    }

    fun skipPrevious(player: PlayerInfo) {
        try {
            player.controller.transportControls.skipToPrevious()
        } catch (_: Exception) {
        }
    }
}
