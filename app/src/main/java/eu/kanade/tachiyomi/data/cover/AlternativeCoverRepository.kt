package eu.kanade.tachiyomi.data.cover

import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Alternative covers from public, key-free sources (TMDB excepted: it
 * uses the user's own key from Ajustes → Seguimiento, if set):
 * - Manga: MangaDex (originals) + Jikan (MyAnimeList) + Kitsu.
 * - Anime: Kitsu (original poster) + Jikan (MyAnimeList) + TMDB (TV).
 *
 * One entry at a time, chosen by the user. Nothing is ever applied
 * automatically: the user picks one cover and it becomes that entry's
 * custom cover.
 *
 * ORIGINAL SIZE (v7.9.21): the [AlternativeCover.url] saved as the
 * custom cover is ALWAYS the full-resolution original of that source —
 * MangaDex without any size suffix, Kitsu `original`, Jikan
 * `large_image_url`, TMDB `/t/p/original`. The thumbnail is only for
 * the picker grid; never the saved cover. The AI upscaler does not work
 * well, so the quality has to come from the cover itself.
 *
 * MangaDex note: api.mangadex.org / uploads.mangadex.org challenge
 * browser-like User-Agents with a JS proof-of-work page. All requests
 * here go through the shared NetworkHelper client, whose
 * MangaDexUserAgentInterceptor swaps the agent for a plain one on
 * *.mangadex.org hosts only (verified: plain UA gets JSON 200 + real
 * JPEGs, the WebView UA gets the challenge page).
 */
data class AlternativeCover(
    /** Full-size image, used when the cover is applied. ALWAYS original. */
    val url: String,
    /** Smaller image, used for the thumbnails in the picker. */
    val thumbnailUrl: String,
    /** e.g. "Vol. 1" for a MangaDex volume cover, null otherwise. */
    val label: String?,
)

data class AlternativeCoverCandidate(
    val title: String,
    val subtitle: String?,
    /** Main cover (first cover) of the candidate, if any. */
    val cover: AlternativeCover?,
    /** MangaDex manga id; null for single-poster sources. */
    val sourceId: String?,
    /** Where this candidate comes from: "MangaDex", "Kitsu", "Jikan", "TMDB". */
    val source: String,
)

/** Result of searching every source for one query. */
data class AlternativeCoverSearchResult(
    val candidates: List<AlternativeCoverCandidate>,
    /** Sources that answered with no results (or errored). */
    val emptySources: List<String>,
    /** True when TMDB was skipped because no API key is set. */
    val tmdbNeedsKey: Boolean,
)

object AlternativeCoverRepository {

    const val SOURCE_MANGADEX = "MangaDex"
    const val SOURCE_KITSU = "Kitsu"
    const val SOURCE_JIKAN = "Jikan"
    const val SOURCE_TMDB = "TMDB"

    private val json = Json { ignoreUnknownKeys = true }

    private fun client() = Injekt.get<NetworkHelper>().client

    private fun tmdbApiKey(): String =
        runCatching { Injekt.get<UiPreferences>().tmdbApiKey().get().trim() }.getOrDefault("")

    private suspend fun getJson(url: String): JsonObject {
        val request = Request.Builder().url(url).build()
        client().newCall(request).awaitSuccess().use { response ->
            return json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    /**
     * Search every source for one query. Each source is tried on its own:
     * one failing source never blocks the others. Results keep the source
     * order below, so the dialog can group them by source.
     */
    suspend fun searchAll(title: String, isManga: Boolean): AlternativeCoverSearchResult {
        val sources: List<Pair<String, suspend () -> List<AlternativeCoverCandidate>>> =
            if (isManga) {
                listOf(
                    SOURCE_MANGADEX to { searchMangaDex(title) },
                    SOURCE_JIKAN to { searchJikan(title, isManga = true) },
                    SOURCE_KITSU to { searchKitsu(title, isManga = true) },
                )
            } else {
                listOf(
                    SOURCE_KITSU to { searchKitsu(title, isManga = false) },
                    SOURCE_JIKAN to { searchJikan(title, isManga = false) },
                    SOURCE_TMDB to { searchTmdbTv(title) },
                )
            }
        val candidates = mutableListOf<AlternativeCoverCandidate>()
        val emptySources = mutableListOf<String>()
        var tmdbNeedsKey = false
        for ((source, search) in sources) {
            if (source == SOURCE_TMDB && tmdbApiKey().isEmpty()) {
                tmdbNeedsKey = true
                continue
            }
            val found = runCatching { search() }.getOrDefault(emptyList())
            if (found.isEmpty()) {
                emptySources += source
            } else {
                candidates += found
            }
        }
        return AlternativeCoverSearchResult(candidates, emptySources, tmdbNeedsKey)
    }

    /**
     * Search MangaDex for a manga by title. Returns up to 5 candidates,
     * each with its main cover. Call [loadMangaDexCovers] for the full
     * cover list of one candidate.
     *
     * MangaDex also matches alternative titles: searching "Bulpyeonhan
     * Jinsil" returns the manga whose English title is "An Uncomfortable
     * Truth" — so candidates are shown as they come, never filtered by
     * name equality.
     */
    suspend fun searchMangaDex(title: String): List<AlternativeCoverCandidate> {
        val url = "https://api.mangadex.org/manga".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("limit", "5")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("contentRating[]", "erotica")
            .addQueryParameter("contentRating[]", "pornographic")
            .build()
        val root = getJson(url.toString())
        val data = root.arr("data") ?: return emptyList()
        return data.mapNotNull { element ->
            val manga = element as? JsonObject ?: return@mapNotNull null
            val id = manga.str("id") ?: return@mapNotNull null
            val attrs = manga.obj("attributes") ?: return@mapNotNull null
            val titleObj = attrs.obj("title")
            val mainTitle = titleObj?.str("en")
                ?: titleObj?.str("ja-ro")
                ?: titleObj?.values?.firstOrNull()
                    ?.let { it as? JsonPrimitive }?.contentOrNull
                ?: return@mapNotNull null
            // "year" is a NUMBER in MangaDex JSON; contentOrNull is null
            // for non-string primitives, so read it as an int.
            val year = attrs.num("year")?.toString()
            var mainCover: AlternativeCover? = null
            manga.arr("relationships")?.forEach { relElement ->
                val rel = relElement as? JsonObject ?: return@forEach
                if (rel.str("type") != "cover_art") return@forEach
                val relAttrs = rel.obj("attributes") ?: return@forEach
                val fileName = relAttrs.str("fileName") ?: return@forEach
                val volume = relAttrs.str("volume")
                mainCover = AlternativeCover(
                    // Original: no size suffix. The .512.jpg variant is
                    // only the thumbnail below.
                    url = "https://uploads.mangadex.org/covers/$id/$fileName",
                    thumbnailUrl = "https://uploads.mangadex.org/covers/$id/$fileName.512.jpg",
                    label = volume?.let { "Vol. $it" },
                )
            }
            AlternativeCoverCandidate(
                title = mainTitle,
                subtitle = year,
                cover = mainCover,
                sourceId = id,
                source = SOURCE_MANGADEX,
            )
        }
    }

    /**
     * All covers of one MangaDex manga, at full original size.
     * Thumbnails use the 512px variant so the picker stays fluid.
     */
    suspend fun loadMangaDexCovers(mangaId: String): List<AlternativeCover> {
        val url = "https://api.mangadex.org/cover".toHttpUrl().newBuilder()
            .addQueryParameter("manga[]", mangaId)
            .addQueryParameter("limit", "100")
            .addQueryParameter("order[volume]", "asc")
            .build()
        val root = getJson(url.toString())
        val data = root.arr("data") ?: return emptyList()
        return data.mapNotNull { element ->
            val cover = element as? JsonObject ?: return@mapNotNull null
            val attrs = cover.obj("attributes") ?: return@mapNotNull null
            val fileName = attrs.str("fileName") ?: return@mapNotNull null
            val volume = attrs.str("volume")
            AlternativeCover(
                url = "https://uploads.mangadex.org/covers/$mangaId/$fileName",
                thumbnailUrl = "https://uploads.mangadex.org/covers/$mangaId/$fileName.512.jpg",
                label = volume?.let { "Vol. $it" },
            )
        }
    }

    /**
     * Search Kitsu for an anime or a manga by title. Returns up to 5
     * candidates, each with its single original poster.
     */
    suspend fun searchKitsu(title: String, isManga: Boolean): List<AlternativeCoverCandidate> {
        val kind = if (isManga) "manga" else "anime"
        val url = "https://kitsu.io/api/edge/$kind".toHttpUrl().newBuilder()
            .addQueryParameter("filter[text]", title)
            .addQueryParameter("page[limit]", "5")
            .build()
        val root = getJson(url.toString())
        val data = root.arr("data") ?: return emptyList()
        return data.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val attrs = item.obj("attributes") ?: return@mapNotNull null
            val titles = attrs.obj("titles")
            val mainTitle = attrs.str("canonicalTitle")
                ?: titles?.str("en")
                ?: titles?.str("en_jp")
                ?: return@mapNotNull null
            val poster = attrs.obj("posterImage")
            val original = poster?.str("original")
            val large = poster?.str("large")
            val posterUrl = original ?: large ?: return@mapNotNull null
            val year = attrs.str("startDate")?.take(4)
            AlternativeCoverCandidate(
                title = mainTitle,
                subtitle = year,
                cover = AlternativeCover(
                    // Original poster; "large" (550x780) only as fallback.
                    url = posterUrl,
                    thumbnailUrl = large ?: posterUrl,
                    label = null,
                ),
                sourceId = null,
                source = SOURCE_KITSU,
            )
        }
    }

    /**
     * Search Jikan (MyAnimeList, no key) for an anime or a manga by title.
     * Returns up to 5 candidates, each with its single poster. Jikan's
     * biggest image is `large_image_url` — that is the original saved.
     */
    suspend fun searchJikan(title: String, isManga: Boolean): List<AlternativeCoverCandidate> {
        val kind = if (isManga) "manga" else "anime"
        val url = "https://api.jikan.moe/v4/$kind".toHttpUrl().newBuilder()
            .addQueryParameter("q", title)
            .addQueryParameter("limit", "5")
            .addQueryParameter("order_by", "members")
            .addQueryParameter("sort", "desc")
            .build()
        val root = getJson(url.toString())
        val data = root.arr("data") ?: return emptyList()
        return data.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val mainTitle = item.str("title_english")
                ?: item.str("title")
                ?: item.str("title_japanese")
                ?: return@mapNotNull null
            val images = item.obj("images")?.obj("jpg")
            val large = images?.str("large_image_url")
            val imageUrl = images?.str("image_url")
            val posterUrl = large ?: imageUrl ?: return@mapNotNull null
            val year = item.num("year")?.toString()
                ?: item.obj("published")?.obj("prop")?.obj("from")?.num("year")?.toString()
            AlternativeCoverCandidate(
                title = mainTitle,
                subtitle = year,
                cover = AlternativeCover(
                    url = posterUrl,
                    thumbnailUrl = imageUrl ?: posterUrl,
                    label = null,
                ),
                sourceId = null,
                source = SOURCE_JIKAN,
            )
        }
    }

    /**
     * Search TMDB TV shows (anime are TV in TMDB) by title. Only runs
     * when the user set their own TMDB key (Ajustes → Seguimiento);
     * without a key the caller skips this source. Posters at full
     * original size (up to 2000x3000).
     */
    suspend fun searchTmdbTv(title: String): List<AlternativeCoverCandidate> {
        val apiKey = tmdbApiKey()
        if (apiKey.isEmpty()) return emptyList()
        val url = "https://api.themoviedb.org/3/search/tv".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", title)
            .addQueryParameter("language", "es-ES")
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("page", "1")
            .build()
        val root = getJson(url.toString())
        val data = root.arr("results") ?: return emptyList()
        return data.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val mainTitle = item.str("name")
                ?: item.str("original_name")
                ?: return@mapNotNull null
            val posterPath = item.str("poster_path") ?: return@mapNotNull null
            val year = item.str("first_air_date")?.take(4)
            AlternativeCoverCandidate(
                title = mainTitle,
                subtitle = year,
                cover = AlternativeCover(
                    url = "https://image.tmdb.org/t/p/original$posterPath",
                    thumbnailUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                    label = null,
                ),
                sourceId = null,
                source = SOURCE_TMDB,
            )
        }.take(5)
    }
}
