package app.maparadio.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import android.media.MediaMetadata

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
)

object MusicController {

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
            out.add(PlayerInfo(pkg, appName, title, artist, playing, controller))
        }
        return out
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
