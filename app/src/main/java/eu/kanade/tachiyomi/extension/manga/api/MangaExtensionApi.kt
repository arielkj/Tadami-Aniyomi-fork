package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        return try {
            // Current community repos (e.g. keiyoushi) publish their full list in the
            // new extension-store index (index.json, proto3-JSON), while index.min.json
            // only carries placeholder entries. Try the new index first and fall back
            // to the legacy one for older repos.
            try {
                val response = networkService.client
                    .newCall(GET("$repoBaseUrl/index.json"))
                    .awaitSuccess()

                response.body.source().use { source ->
                    when (source.peek().readByte()) {
                        // "{..." new extension-store index
                        0x7B.toByte() -> json.decodeFromBufferedSource<NetworkExtensionStore>(source)
                            .toExtensions(repoBaseUrl)
                        // "[..." legacy min index served as index.json
                        0x5B.toByte() -> json.decodeFromBufferedSource<List<ExtensionJsonObject>>(source)
                            .toExtensions(repoBaseUrl)
                        else -> fetchLegacyExtensions(repoBaseUrl)
                    }
                }
            } catch (_: Exception) {
                fetchLegacyExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    private suspend fun fetchLegacyExtensions(repoBaseUrl: String): List<MangaExtension.Available> {
        val response = networkService.client
            .newCall(GET("$repoBaseUrl/index.min.json"))
            .awaitSuccess()

        return with(json) {
            response
                .parseAs<List<ExtensionJsonObject>>()
                .toExtensions(repoBaseUrl)
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= MangaExtensionLoader.LIB_VERSION_MIN && libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        // New-format entries carry the full apk url in apkName
        return if (extension.apkName.startsWith("http://") || extension.apkName.startsWith("https://")) {
            extension.apkName
        } else {
            "${extension.repoUrl}/apk/${extension.apkName}"
        }
    }

    private fun NetworkExtensionStore.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return extensionList?.extensions.orEmpty()
            .filter {
                val libVersion = it.extensionLib.toDoubleOrNull()
                libVersion != null &&
                    libVersion >= MangaExtensionLoader.LIB_VERSION_MIN &&
                    libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                val lang = it.sources.map { source -> source.language }.toSet()
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.packageName,
                    versionName = it.versionName,
                    versionCode = it.versionCode,
                    libVersion = it.extensionLib.toDouble(),
                    lang = if (lang.size == 1) lang.first() else "all",
                    isNsfw = it.contentWarning == "CONTENT_WARNING_MIXED" ||
                        it.contentWarning == "CONTENT_WARNING_NSFW",
                    sources = it.sources.map { source ->
                        MangaExtension.Available.MangaSource(
                            id = source.id,
                            lang = source.language,
                            name = source.name,
                            baseUrl = source.homeUrl,
                        )
                    },
                    // New-format entries carry the full apk url; getApkUrl returns it as-is
                    apkName = it.resources.apkUrl,
                    iconUrl = it.resources.iconUrl,
                    repoUrl = repoUrl,
                )
            }
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}

/**
 * New extension-store index (index.json, proto3-JSON) published alongside index.pb
 * by current community repos (e.g. keiyoushi). Only the fields used to list and
 * update extensions are declared; the shared Json instance ignores unknown keys.
 * 64-bit integers arrive as JSON strings, see [Proto3JsonInt64Serializer].
 */
@Serializable
private data class NetworkExtensionStore(
    val name: String = "",
    val extensionList: ExtensionList? = null,
) {
    @Serializable
    data class ExtensionList(
        val extensions: List<Extension> = emptyList(),
    )

    @Serializable
    data class Extension(
        val name: String,
        val packageName: String,
        val resources: Resources,
        val extensionLib: String,
        @Serializable(with = Proto3JsonInt64Serializer::class)
        val versionCode: Long,
        val versionName: String,
        val contentWarning: String = "",
        val sources: List<Source> = emptyList(),
    )

    @Serializable
    data class Resources(
        val apkUrl: String,
        val iconUrl: String,
    )

    @Serializable
    data class Source(
        @Serializable(with = Proto3JsonInt64Serializer::class)
        val id: Long,
        val name: String = "",
        val language: String = "all",
        val homeUrl: String = "",
    )
}

/**
 * Serializer for proto3 JSON-style 64-bit integers.
 *
 * google.protobuf.json_format serializes int64 as JSON *strings* ("106001",
 * "9124545289113350724"), which kotlinx Json cannot decode into [Long] without
 * coercion. This serializer accepts both the proto3-JSON string form and the
 * plain numeric form.
 */
private object Proto3JsonInt64Serializer : KSerializer<Long> {

    override val descriptor = PrimitiveSerialDescriptor("proto3JsonInt64", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        return when (decoder) {
            is JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                if (element !is JsonPrimitive) {
                    throw SerializationException(
                        "Expected 64-bit integer as JSON number or string, got: $element",
                    )
                }
                element.content.trim().toLongOrNull()
                    ?: throw SerializationException(
                        "Expected 64-bit integer as JSON number or string, got: '${element.content}'",
                    )
            }
            else -> decoder.decodeLong()
        }
    }

    override fun serialize(encoder: Encoder, value: Long) {
        when (encoder) {
            is JsonEncoder -> encoder.encodeJsonElement(JsonPrimitive(value))
            else -> encoder.encodeLong(value)
        }
    }
}
