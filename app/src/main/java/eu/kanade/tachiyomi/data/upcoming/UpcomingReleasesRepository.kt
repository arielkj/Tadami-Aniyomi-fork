package eu.kanade.tachiyomi.data.upcoming

import eu.kanade.tachiyomi.data.track.tmdb.TmdbApi
import eu.kanade.tachiyomi.data.track.tmdb.TmdbUpcomingItem
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import tachiyomi.core.common.util.lang.withIOContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Un anime que estrena (o emite un episodio nuevo) pronto, según AniList.
 * No hace falta cuenta: el horario de emisión de AniList es público.
 */
data class AiringAnimeItem(
    val anilistId: Long,
    val title: String,
    val coverUrl: String?,
    // Segundos epoch del próximo episodio.
    val airingAtEpochSeconds: Long,
    val episode: Int,
)

/**
 * v7.9.2 — estrenos próximos para la parte superior de Actualizaciones.
 * - Anime: estrenos nuevos del mes en AniList (GraphQL público, sin clave).
 * - Películas y series: TMDB (necesita la clave gratuita del usuario).
 */
class UpcomingReleasesRepository(
    private val client: OkHttpClient = OkHttpClient(),
) {

    /**
     * v7.9.4 — estrenos NUEVOS del mes en todo AniList, sin filtrar por
     * biblioteca (ni solo biblioteca ni solo fuera de ella): animes que
     * estrenan este mes, tanto los que aún no salen (NOT_YET_RELEASED)
     * como los que ya estrenaron este mes y siguen en emisión
     * (RELEASING), ordenados por popularidad. La ventana de 7 días del
     * horario de emisión dejaba el carrusel vacío en cuanto pasaba el día
     * (airingAt_greater: now excluía los episodios de hoy ya emitidos).
     * La fecha es la del estreno y el episodio es el 1 (el del estreno).
     */
    suspend fun fetchMonthlyPremieres(
        month: YearMonth = YearMonth.now(),
        perPage: Int = 25,
    ): List<AiringAnimeItem> {
        return withIOContext {
            val firstDay = month.atDay(1)
            val lastDay = month.atEndOfMonth()
            val greater = firstDay.year * 10000 + firstDay.monthValue * 100 + firstDay.dayOfMonth
            val lesser = lastDay.year * 10000 + lastDay.monthValue * 100 + lastDay.dayOfMonth
            val query = """
                query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}greater: Int, ${'$'}lesser: Int) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        media(type: ANIME, status_in: [NOT_YET_RELEASED, RELEASING], startDate_greater: ${'$'}greater, startDate_lesser: ${'$'}lesser, sort: POPULARITY_DESC) {
                            id
                            title { romaji english }
                            coverImage { large }
                            startDate { year month day }
                        }
                    }
                }
            """.trimIndent()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", 1)
                    put("perPage", perPage)
                    put("greater", greater)
                    put("lesser", lesser)
                }
            }.toString()

            val response = client.newCall(
                POST(
                    "https://graphql.anilist.co",
                    body = payload.toRequestBody(jsonMime),
                ),
            ).execute()
            val body = response.use {
                if (!it.isSuccessful) return@withIOContext emptyList<AiringAnimeItem>()
                it.body.string()
            }
            val media = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media")
                ?: return@withIOContext emptyList<AiringAnimeItem>()

            val items = mutableListOf<AiringAnimeItem>()
            for (i in 0 until media.length()) {
                val entry = media.optJSONObject(i) ?: continue
                val id = entry.optLong("id")
                if (id == 0L) continue
                val titleObj = entry.optJSONObject("title")
                val title = titleObj?.optString("romaji")?.takeIf { it.isNotBlank() }
                    ?: titleObj?.optString("english")?.takeIf { it.isNotBlank() }
                    ?: continue
                val coverUrl = entry.optJSONObject("coverImage")
                    ?.optString("large")
                    ?.takeIf { it.isNotBlank() }
                // Fecha de estreno (sin día -> día 1); sin año o mes no se
                // puede fechar el estreno y se salta.
                val start = entry.optJSONObject("startDate") ?: continue
                val year = start.optInt("year").takeIf { it > 0 } ?: continue
                val monthValue = start.optInt("month").takeIf { it in 1..12 } ?: continue
                val day = start.optInt("day").takeIf { it in 1..31 } ?: 1
                val premiereDate = runCatching {
                    LocalDate.of(year, monthValue, day)
                }.getOrNull() ?: continue
                val airingAt = premiereDate
                    .atStartOfDay(ZoneId.systemDefault())
                    .toEpochSecond()
                items.add(
                    AiringAnimeItem(
                        anilistId = id,
                        title = title,
                        coverUrl = coverUrl,
                        airingAtEpochSeconds = airingAt,
                        episode = 1,
                    ),
                )
            }
            items
        }
    }

    suspend fun fetchUpcomingMovies(apiKey: String): List<TmdbUpcomingItem> {
        return withIOContext {
            TmdbApi(client, apiKey).getUpcomingMovies()
        }
    }

    suspend fun fetchUpcomingSeries(apiKey: String): List<TmdbUpcomingItem> {
        return withIOContext {
            TmdbApi(client, apiKey).getUpcomingTv()
        }
    }
}
