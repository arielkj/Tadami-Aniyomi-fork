package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.novel.LibraryNovel
import java.io.File

/**
 * V7.4 (B1): instantánea ligera de la biblioteca en disco.
 *
 * El error de la pantalla negra: la lista cargada vivía SOLO en memoria
 * (StateScreenModel + shareIn replay=1). Cuando Android mata el proceso en
 * segundo plano — o el usuario cierra la app desde recientes — esa lista
 * muere con él, y al reabrir hay que releer los ~1.900 animes y ~5.500
 * mangas de la BD página a página detrás de un LoadingScreen (isLoading
 * no se apaga hasta la primera emisión, que espera al combine completo +
 * getDownloadCount por título).
 *
 * Este almacén guarda la última lista completa que emitió cada sección
 * (anime/manga/novela) en un JSON en filesDir (nunca en cacheDir: el
 * sistema puede vaciarlo). Al arrancar en frío, el flujo paginado emite
 * primero la instantánea — la lista aparece DE INMEDIATO con
 * isLoading=false — y la carga fresca de la BD corre detrás, parcheando
 * solo lo que cambió (patchLibraryMap). Si la instantánea no existe o
 * está corrupta, se ignora y se carga de la BD como antes.
 *
 * Solo se guarda la lista en crudo (LibraryAnime/LibraryManga/
 * LibraryNovel): los contadores de descarga y los filtros se calculan
 * igual que siempre sobre la lista fresca; en la emisión de la
 * instantánea los contadores se saltan (B5) y se rellenan con la emisión
 * fresca. Las extensiones no intervienen: la instantánea sale de la BD
 * local y la carga tampoco las toca.
 */
data class PagedLibraryEmission<T>(val items: List<T>, val isSnapshot: Boolean)

@Serializable
private data class AnimeDto(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val pinned: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val fetchInterval: Int,
    val dateAdded: Long,
    val viewerFlags: Long,
    val episodeFlags: Long,
    val coverLastModified: Long,
    val backgroundLastModified: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val notes: String,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val backgroundUrl: String?,
    val updateStrategy: String,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val fetchType: String,
    val parentId: Long?,
    val seasonFlags: Long,
    val seasonNumber: Double,
    val seasonSourceOrder: Long,
    val customTitle: String?,
    val customArtist: String?,
    val customAuthor: String?,
    val customDescription: String?,
    val customGenre: List<String>?,
    val customStatus: Long?,
    val completedAt: Long?,
)

@Serializable
private data class LibraryAnimeDto(
    val anime: AnimeDto,
    val category: Long,
    val totalCount: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val fillermarkCount: Long,
    val latestUpload: Long,
    val episodeFetchedAt: Long,
    val lastSeen: Long,
)

@Serializable
private data class AnimeSnapshotFile(
    val version: Int = 1,
    val items: List<LibraryAnimeDto> = emptyList(),
)

@Serializable
private data class MangaDto(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val pinned: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val fetchInterval: Int,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val author: String?,
    val description: String?,
    val notes: String,
    val genre: List<String>?,
    val status: Long,
    val rating: Float,
    val thumbnailUrl: String?,
    val updateStrategy: String,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val customTitle: String?,
    val customArtist: String?,
    val customAuthor: String?,
    val customDescription: String?,
    val customGenre: List<String>?,
    val customStatus: Long?,
    val completedAt: Long?,
)

@Serializable
private data class LibraryMangaDto(
    val manga: MangaDto,
    val category: Long,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
)

@Serializable
private data class MangaSnapshotFile(
    val version: Int = 1,
    val items: List<LibraryMangaDto> = emptyList(),
)

@Serializable
private data class NovelDto(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val pinned: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val fetchInterval: Int,
    val dateAdded: Long,
    val viewerFlags: Long,
    val chapterFlags: Long,
    val coverLastModified: Long,
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    val notes: String,
    val genre: List<String>?,
    val status: Long,
    val thumbnailUrl: String?,
    val updateStrategy: String,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val customTitle: String?,
    val customAuthor: String?,
    val customDescription: String?,
    val customGenre: List<String>?,
    val customStatus: Long?,
    val completedAt: Long?,
)

@Serializable
private data class LibraryNovelDto(
    val novel: NovelDto,
    val category: Long,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
)

@Serializable
private data class NovelSnapshotFile(
    val version: Int = 1,
    val items: List<LibraryNovelDto> = emptyList(),
)

private fun Anime.toDto() = AnimeDto(
    id = id,
    source = source,
    favorite = favorite,
    pinned = pinned,
    lastUpdate = lastUpdate,
    nextUpdate = nextUpdate,
    fetchInterval = fetchInterval,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    episodeFlags = episodeFlags,
    coverLastModified = coverLastModified,
    backgroundLastModified = backgroundLastModified,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    notes = notes,
    genre = genre,
    status = status,
    thumbnailUrl = thumbnailUrl,
    backgroundUrl = backgroundUrl,
    updateStrategy = updateStrategy.name,
    initialized = initialized,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    fetchType = fetchType.name,
    parentId = parentId,
    seasonFlags = seasonFlags,
    seasonNumber = seasonNumber,
    seasonSourceOrder = seasonSourceOrder,
    customTitle = customTitle,
    customArtist = customArtist,
    customAuthor = customAuthor,
    customDescription = customDescription,
    customGenre = customGenre,
    customStatus = customStatus,
    completedAt = completedAt,
)

private fun AnimeDto.toAnime() = Anime(
    id = id,
    source = source,
    favorite = favorite,
    pinned = pinned,
    lastUpdate = lastUpdate,
    nextUpdate = nextUpdate,
    fetchInterval = fetchInterval,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    episodeFlags = episodeFlags,
    coverLastModified = coverLastModified,
    backgroundLastModified = backgroundLastModified,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    notes = notes,
    genre = genre,
    status = status,
    thumbnailUrl = thumbnailUrl,
    backgroundUrl = backgroundUrl,
    updateStrategy = runCatching { AnimeUpdateStrategy.valueOf(updateStrategy) }
        .getOrDefault(AnimeUpdateStrategy.ALWAYS_UPDATE),
    initialized = initialized,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    fetchType = runCatching { FetchType.valueOf(fetchType) }.getOrDefault(FetchType.Seasons),
    parentId = parentId,
    seasonFlags = seasonFlags,
    seasonNumber = seasonNumber,
    seasonSourceOrder = seasonSourceOrder,
    customTitle = customTitle,
    customArtist = customArtist,
    customAuthor = customAuthor,
    customDescription = customDescription,
    customGenre = customGenre,
    customStatus = customStatus,
    completedAt = completedAt,
)

private fun LibraryAnime.toDto() = LibraryAnimeDto(
    anime = anime.toDto(),
    category = category,
    totalCount = totalCount,
    seenCount = seenCount,
    bookmarkCount = bookmarkCount,
    fillermarkCount = fillermarkCount,
    latestUpload = latestUpload,
    episodeFetchedAt = episodeFetchedAt,
    lastSeen = lastSeen,
)

private fun LibraryAnimeDto.toLibraryAnime() = LibraryAnime(
    anime = anime.toAnime(),
    category = category,
    totalCount = totalCount,
    seenCount = seenCount,
    bookmarkCount = bookmarkCount,
    fillermarkCount = fillermarkCount,
    latestUpload = latestUpload,
    episodeFetchedAt = episodeFetchedAt,
    lastSeen = lastSeen,
)

private fun Manga.toDto() = MangaDto(
    id = id,
    source = source,
    favorite = favorite,
    pinned = pinned,
    lastUpdate = lastUpdate,
    nextUpdate = nextUpdate,
    fetchInterval = fetchInterval,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    coverLastModified = coverLastModified,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    notes = notes,
    genre = genre,
    status = status,
    rating = rating,
    thumbnailUrl = thumbnailUrl,
    updateStrategy = updateStrategy.name,
    initialized = initialized,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    customTitle = customTitle,
    customArtist = customArtist,
    customAuthor = customAuthor,
    customDescription = customDescription,
    customGenre = customGenre,
    customStatus = customStatus,
    completedAt = completedAt,
)

private fun MangaDto.toManga() = Manga(
    id = id,
    source = source,
    favorite = favorite,
    pinned = pinned,
    lastUpdate = lastUpdate,
    nextUpdate = nextUpdate,
    fetchInterval = fetchInterval,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    coverLastModified = coverLastModified,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    notes = notes,
    genre = genre,
    status = status,
    rating = rating,
    thumbnailUrl = thumbnailUrl,
    updateStrategy = runCatching { UpdateStrategy.valueOf(updateStrategy) }
        .getOrDefault(UpdateStrategy.ALWAYS_UPDATE),
    initialized = initialized,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    customTitle = customTitle,
    customArtist = customArtist,
    customAuthor = customAuthor,
    customDescription = customDescription,
    customGenre = customGenre,
    customStatus = customStatus,
    completedAt = completedAt,
)

private fun LibraryManga.toDto() = LibraryMangaDto(
    manga = manga.toDto(),
    category = category,
    totalChapters = totalChapters,
    readCount = readCount,
    bookmarkCount = bookmarkCount,
    latestUpload = latestUpload,
    chapterFetchedAt = chapterFetchedAt,
    lastRead = lastRead,
)

private fun LibraryMangaDto.toLibraryManga() = LibraryManga(
    manga = manga.toManga(),
    category = category,
    totalChapters = totalChapters,
    readCount = readCount,
    bookmarkCount = bookmarkCount,
    latestUpload = latestUpload,
    chapterFetchedAt = chapterFetchedAt,
    lastRead = lastRead,
)

private fun Novel.toDto() = NovelDto(
    id = id,
    source = source,
    favorite = favorite,
    pinned = pinned,
    lastUpdate = lastUpdate,
    nextUpdate = nextUpdate,
    fetchInterval = fetchInterval,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    coverLastModified = coverLastModified,
    url = url,
    title = title,
    author = author,
    description = description,
    notes = notes,
    genre = genre,
    status = status,
    thumbnailUrl = thumbnailUrl,
    updateStrategy = updateStrategy.name,
    initialized = initialized,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    customTitle = customTitle,
    customAuthor = customAuthor,
    customDescription = customDescription,
    customGenre = customGenre,
    customStatus = customStatus,
    completedAt = completedAt,
)

private fun NovelDto.toNovel() = Novel(
    id = id,
    source = source,
    favorite = favorite,
    pinned = pinned,
    lastUpdate = lastUpdate,
    nextUpdate = nextUpdate,
    fetchInterval = fetchInterval,
    dateAdded = dateAdded,
    viewerFlags = viewerFlags,
    chapterFlags = chapterFlags,
    coverLastModified = coverLastModified,
    url = url,
    title = title,
    author = author,
    description = description,
    notes = notes,
    genre = genre,
    status = status,
    thumbnailUrl = thumbnailUrl,
    updateStrategy = runCatching { UpdateStrategy.valueOf(updateStrategy) }
        .getOrDefault(UpdateStrategy.ALWAYS_UPDATE),
    initialized = initialized,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    customTitle = customTitle,
    customAuthor = customAuthor,
    customDescription = customDescription,
    customGenre = customGenre,
    customStatus = customStatus,
    completedAt = completedAt,
)

private fun LibraryNovel.toDto() = LibraryNovelDto(
    novel = novel.toDto(),
    category = category,
    totalChapters = totalChapters,
    readCount = readCount,
    bookmarkCount = bookmarkCount,
    latestUpload = latestUpload,
    chapterFetchedAt = chapterFetchedAt,
    lastRead = lastRead,
)

private fun LibraryNovelDto.toLibraryNovel() = LibraryNovel(
    novel = novel.toNovel(),
    category = category,
    totalChapters = totalChapters,
    readCount = readCount,
    bookmarkCount = bookmarkCount,
    latestUpload = latestUpload,
    chapterFetchedAt = chapterFetchedAt,
    lastRead = lastRead,
)

object LibrarySnapshotStore {

    private const val SNAPSHOT_VERSION = 1
    private const val DIR_NAME = "library_snapshots"
    private const val ANIME_FILE = "library_snapshot_anime.json"
    private const val MANGA_FILE = "library_snapshot_manga.json"
    private const val NOVEL_FILE = "library_snapshot_novel.json"

    private val json = Json { ignoreUnknownKeys = true }

    // Última lista escrita por sección: si la carga fresca es idéntica a
    // la ya guardada no se reescribe el archivo (la actualización
    // programada dispara muchas invalidaciones seguidas).
    private var lastWrittenAnime: List<LibraryAnime>? = null
    private var lastWrittenManga: List<LibraryManga>? = null
    private var lastWrittenNovel: List<LibraryNovel>? = null

    fun readAnimeSnapshot(context: Context): List<LibraryAnime>? {
        return runCatching {
            val text = readFile(context, ANIME_FILE) ?: return null
            val file = json.decodeFromString<AnimeSnapshotFile>(text)
            if (file.version != SNAPSHOT_VERSION) return null
            file.items.map { it.toLibraryAnime() }.also { lastWrittenAnime = it }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "LibrarySnapshot: bad anime snapshot, ignoring" }
        }.getOrNull()
    }

    fun writeAnimeSnapshotIfChanged(context: Context, items: List<LibraryAnime>) {
        if (items == lastWrittenAnime) return
        runCatching {
            val text = json.encodeToString(AnimeSnapshotFile(items = items.map { it.toDto() }))
            writeFile(context, ANIME_FILE, text)
            lastWrittenAnime = items
        }.onFailure {
            logcat(LogPriority.WARN, it) { "LibrarySnapshot: could not write anime snapshot" }
        }
    }

    fun readMangaSnapshot(context: Context): List<LibraryManga>? {
        return runCatching {
            val text = readFile(context, MANGA_FILE) ?: return null
            val file = json.decodeFromString<MangaSnapshotFile>(text)
            if (file.version != SNAPSHOT_VERSION) return null
            file.items.map { it.toLibraryManga() }.also { lastWrittenManga = it }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "LibrarySnapshot: bad manga snapshot, ignoring" }
        }.getOrNull()
    }

    fun writeMangaSnapshotIfChanged(context: Context, items: List<LibraryManga>) {
        if (items == lastWrittenManga) return
        runCatching {
            val text = json.encodeToString(MangaSnapshotFile(items = items.map { it.toDto() }))
            writeFile(context, MANGA_FILE, text)
            lastWrittenManga = items
        }.onFailure {
            logcat(LogPriority.WARN, it) { "LibrarySnapshot: could not write manga snapshot" }
        }
    }

    fun readNovelSnapshot(context: Context): List<LibraryNovel>? {
        return runCatching {
            val text = readFile(context, NOVEL_FILE) ?: return null
            val file = json.decodeFromString<NovelSnapshotFile>(text)
            if (file.version != SNAPSHOT_VERSION) return null
            file.items.map { it.toLibraryNovel() }.also { lastWrittenNovel = it }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "LibrarySnapshot: bad novel snapshot, ignoring" }
        }.getOrNull()
    }

    fun writeNovelSnapshotIfChanged(context: Context, items: List<LibraryNovel>) {
        if (items == lastWrittenNovel) return
        runCatching {
            val text = json.encodeToString(NovelSnapshotFile(items = items.map { it.toDto() }))
            writeFile(context, NOVEL_FILE, text)
            lastWrittenNovel = items
        }.onFailure {
            logcat(LogPriority.WARN, it) { "LibrarySnapshot: could not write novel snapshot" }
        }
    }

    private fun snapshotDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun readFile(context: Context, name: String): String? {
        val file = File(snapshotDir(context), name)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { file.readText() }.getOrNull()
    }

    private fun writeFile(context: Context, name: String, text: String) {
        val dir = snapshotDir(context)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        val dest = File(dir, name)
        if (!tmp.renameTo(dest)) {
            // renameTo no debería fallar dentro del mismo directorio, pero
            // por si acaso se copia y se borra el temporal.
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }
}
