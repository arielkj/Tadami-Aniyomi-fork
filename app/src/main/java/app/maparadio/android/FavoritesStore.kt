package app.maparadio.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Lugares guardados del usuario, en el telefono (DataStore). Sin cuentas. */
data class Favorite(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
)

private val Context.favDataStore: DataStore<Preferences> by preferencesDataStore("mapa_radio")

object FavoritesStore {
    private val KEY = stringPreferencesKey("favorites_json")

    suspend fun load(context: Context): List<Favorite> {
        val json = context.favDataStore.data.first()[KEY] ?: return emptyList()
        val out = ArrayList<Favorite>()
        val arr = try {
            JSONArray(json)
        } catch (_: Exception) {
            return emptyList()
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Favorite(
                    name = o.optString("name"),
                    address = o.optString("address"),
                    lat = o.optDouble("lat"),
                    lon = o.optDouble("lon"),
                )
            )
        }
        return out
    }

    suspend fun add(context: Context, fav: Favorite) {
        val list = load(context).toMutableList()
        // No duplicar el mismo punto.
        if (list.any { RouteEngine.metersBetween(
                com.mapbox.mapboxsdk.geometry.LatLng(it.lat, it.lon),
                com.mapbox.mapboxsdk.geometry.LatLng(fav.lat, fav.lon),
            ) < 50.0 }) return
        list.add(0, fav)
        save(context, list)
    }

    suspend fun remove(context: Context, fav: Favorite) {
        val list = load(context).filterNot {
            it.name == fav.name && it.lat == fav.lat && it.lon == fav.lon
        }
        save(context, list)
    }

    private suspend fun save(context: Context, list: List<Favorite>) {
        val arr = JSONArray()
        for (f in list) {
            arr.put(
                JSONObject().apply {
                    put("name", f.name)
                    put("address", f.address)
                    put("lat", f.lat)
                    put("lon", f.lon)
                }
            )
        }
        context.favDataStore.edit { it[KEY] = arr.toString() }
    }
}
