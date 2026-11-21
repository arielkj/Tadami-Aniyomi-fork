package eu.kanade.tachiyomi.data.cover

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Alternative covers from public, key-free sources:
 * - Manga: MangaDex — full-size original covers (uploads.mangadex.org).
 * - Anime: Kitsu — original poster image (media.kitsu.app).
 *
 * One entry at a time, chosen by the user. Nothing is ever applied
 * automatically: the user picks one cover and it becomes that entry's
 * custom cover.
 */
data class AlternativeCover(
    /** Full-size image, used when the cover is applied. */
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
    /** MangaDex manga id; null for Kitsu (one poster per candidate). */
    val sourceId: String?,
)

object AlternativeCoverRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun client() = Injekt.get<NetworkHelper>().client

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

    /**
     * Search MangaDex for a manga by title. Returns up to 5 candidates,
     * each with its main cover. Call [loadMangaDexCovers] for the full
     * cover list of one candidate.
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
            val year = attrs["year"]?.let { it as? JsonPrimitive }?.contentOrNull
            var mainCover: AlternativeCover? = null
            manga.arr("relationships")?.forEach { relElement ->
                val rel = relElement as? JsonObject ?: return@forEach
                if (rel.str("type") != "cover_art") return@forEach
                val relAttrs = rel.obj("attributes") ?: return@forEach
                val fileName = relAttrs.str("fileName") ?: return@forEach
                val volume = relAttrs.str("volume")
                mainCover = AlternativeCover(
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
     * Search Kitsu for an anime by title. Returns up to 5 candidates,
     * each with its single original poster.
     */
    suspend fun searchKitsu(title: String): List<AlternativeCoverCandidate> {
        val url = "https://kitsu.io/api/edge/anime".toHttpUrl().newBuilder()
            .addQueryParameter("filter[text]", title)
            .addQueryParameter("page[limit]", "5")
            .build()
        val root = getJson(url.toString())
        val data = root.arr("data") ?: return emptyList()
        return data.mapNotNull { element ->
            val anime = element as? JsonObject ?: return@mapNotNull null
            val attrs = anime.obj("attributes") ?: return@mapNotNull null
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
                    url = posterUrl,
                    thumbnailUrl = large ?: posterUrl,
                    label = null,
                ),
                sourceId = null,
            )
        }
    }
}
