package tachiyomi.domain.source.anime.service

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.source.anime.model.StubAnimeSource

interface AnimeSourceManager {

    val isInitialized: StateFlow<Boolean>

    val sources: Flow<List<AnimeSource>>

    val catalogueSources: Flow<List<AnimeCatalogueSource>>

    // V6: arranca la carga de extensiones instaladas (bajo demanda, en segundo
    // plano). Sin efecto si ya están cargadas o cargándose.
    fun ensureExtensionsLoaded() {}

    fun get(sourceKey: Long): AnimeSource?

    fun getOrStub(sourceKey: Long): AnimeSource

    fun getOnlineSources(): List<AnimeHttpSource>

    fun getCatalogueSources(): List<AnimeCatalogueSource>

    fun getStubSources(): List<StubAnimeSource>
}
