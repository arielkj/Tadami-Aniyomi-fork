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

/**
 * Lugares guardados del usuario, en el telefono (DataStore). Sin cuentas.
 *
 * v1.6: cada lugar tiene una CATEGORIA. El usuario guarda a mano los
 * lugares principales que visita, agrupados en tres categorias exactas:
 * Locales, Restaurantes y Favoritos. Los lugares guardados en versiones
 * anteriores (sin categoria) migran a Favoritos.
 */
data class Favorite(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val category: String = FavoriteCategory.FAVORITOS,
)

object FavoriteCategory {
    const val LOCALES = "locales"
    const val RESTAURANTES = "restaurantes"
    const val FAVORITOS = "favoritos"

    val ALL = listOf(LOCALES, RESTAURANTES, FAVORITOS)

    fun label(category: String, context: Context): String = when (category) {
        LOCALES -> context.getString(R.string.cat_locales)
        RESTAURANTES -> context.getString(R.string.cat_restaurantes)
        else -> context.getString(R.string.cat_favoritos)
    }
}

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
            // Sin "cat" (versiones viejas) -> Favoritos.
            val cat = o.optString("cat").takeIf { it in FavoriteCategory.ALL }
                ?: FavoriteCategory.FAVORITOS
            out.add(
                Favorite(
                    name = o.optString("name"),
                    address = o.optString("address"),
                    lat = o.optDouble("lat"),
                    lon = o.optDouble("lon"),
                    category = cat,
                )
            )
        }
        return out
    }

    suspend fun loadByCategory(context: Context, category: String): List<Favorite> {
        return load(context).filter { it.category == category }
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
                    put("cat", f.category)
                }
            )
        }
        context.favDataStore.edit { it[KEY] = arr.toString() }
    }
}
