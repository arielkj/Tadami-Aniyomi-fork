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
 * - Anime: horario de emisión de AniList (GraphQL público, sin clave).
 * - Películas y series: TMDB (necesita la clave gratuita del usuario).
 */
class UpcomingReleasesRepository(
    private val client: OkHttpClient = OkHttpClient(),
) {

    suspend fun fetchAiringAnime(daysAhead: Long = 7, perPage: Int = 20): List<AiringAnimeItem> {
        return withIOContext {
            val now = System.currentTimeMillis() / 1000
            val until = now + daysAhead * 86400
            val query = """
                query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}greater: Int, ${'$'}lesser: Int) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        airingSchedules(airingAt_greater: ${'$'}greater, airingAt_lesser: ${'$'}lesser, sort: TIME) {
                            airingAt
                            episode
                            media {
                                id
                                title { romaji english }
                                coverImage { large }
                            }
                        }
                    }
                }
            """.trimIndent()
            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("page", 1)
                    put("perPage", perPage)
                    put("greater", now.toInt())
                    put("lesser", until.toInt())
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
            val schedules = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("airingSchedules")
                ?: return@withIOContext emptyList<AiringAnimeItem>()

            val seen = mutableSetOf<Long>()
            val items = mutableListOf<AiringAnimeItem>()
            for (i in 0 until schedules.length()) {
                val schedule = schedules.optJSONObject(i) ?: continue
                val media = schedule.optJSONObject("media") ?: continue
                val id = media.optLong("id")
                if (id == 0L || !seen.add(id)) continue
                val titleObj = media.optJSONObject("title")
                val title = titleObj?.optString("romaji")?.takeIf { it.isNotBlank() }
                    ?: titleObj?.optString("english")?.takeIf { it.isNotBlank() }
                    ?: continue
                val coverUrl = media.optJSONObject("coverImage")
                    ?.optString("large")
                    ?.takeIf { it.isNotBlank() }
                items.add(
                    AiringAnimeItem(
                        anilistId = id,
                        title = title,
                        coverUrl = coverUrl,
                        airingAtEpochSeconds = schedule.optLong("airingAt"),
                        episode = schedule.optInt("episode"),
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
