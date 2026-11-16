package tachiyomi.domain.source.manga.service

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.source.manga.model.StubMangaSource

interface MangaSourceManager {

    val isInitialized: StateFlow<Boolean>

    val catalogueSources: Flow<List<CatalogueSource>>

    // V6: arranca la carga de extensiones instaladas (bajo demanda, en segundo
    // plano). Sin efecto si ya están cargadas o cargándose.
    fun ensureExtensionsLoaded() {}

    fun get(sourceKey: Long): MangaSource?

    fun getOrStub(sourceKey: Long): MangaSource

    fun getOnlineSources(): List<HttpSource>

    fun getCatalogueSources(): List<CatalogueSource>

    fun getStubSources(): List<StubMangaSource>
}
