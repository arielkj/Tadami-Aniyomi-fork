package app.maparadio.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Ajustes del usuario (modo oscuro/claro), en el telefono (DataStore). */
object AppPrefs {
    const val MODE_DARK = "dark"
    const val MODE_LIGHT = "light"
    const val MODE_AUTO = "auto"

    private val KEY_MODE = stringPreferencesKey("mode")

    private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(
        "mapa_radio_prefs"
    )

    suspend fun loadMode(context: Context): String {
        return context.prefsDataStore.data.first()[KEY_MODE] ?: MODE_DARK
    }

    suspend fun saveMode(context: Context, mode: String) {
        context.prefsDataStore.edit { it[KEY_MODE] = mode }
    }
}
