package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource

class GlobalAnimeSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : AnimeSearchScreenModel(
    State(
        searchQuery = initialQuery,
    ),
) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                // v7.9.26: solo en esta pantalla, sin guardar — la preferencia
                // guardada del usuario no se toca.
                setSourceFilter(AnimeSourceFilter.All, persist = false)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AnimeSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
