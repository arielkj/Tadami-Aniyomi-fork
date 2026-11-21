package eu.kanade.presentation.entries.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberCoverReloadTick
import eu.kanade.presentation.components.rememberThemeAwareCoverErrorPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import kotlinx.coroutines.delay
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.presentation.core.util.LocalAppHaptics

enum class ItemCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    Thumb(16f / 9f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
        errorPainter: Painter? = null,
    ) {
        val model = resolveCoverModel(data)
        val appHaptics = LocalAppHaptics.current
        val imageModifier = modifier
            .aspectRatio(ratio)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = {
                            appHaptics.tap()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )

        val resolvedErrorPainter = errorPainter ?: rememberThemeAwareCoverErrorPainter()
        val context = LocalContext.current

        if (isLoadableCoverData(model)) {
            val coverReloadTick = rememberCoverReloadTick()
            // v7.9.14: una portada que falla se quedaba para siempre en el
            // dibujo "LOADING?" (el error no se reintentaba nunca: solo se
            // recargaba al recrear la pantalla o al recuperar la red). Un
            // solo reintento automático tras un error transitorio (ráfaga
            // de peticiones al deslizar rápido, tropiezo del CDN) recupera
            // la mayoría; si la URL está muerta de verdad, el segundo error
            // se queda en el dibujo de error sin más intentos.
            var coverRetryTick by remember(model) { mutableIntStateOf(0) }
            // Un solo reintento por portada, con una pequeña espera para
            // no saturar el CDN cuando el fallo es por ráfaga. Si la celda
            // se recicla a otra portada antes, el efecto se cancela (la
            // clave cambia) y no se reintenta la portada equivocada.
            var coverFailed by remember(model) { mutableStateOf(false) }
            LaunchedEffect(coverFailed) {
                if (coverFailed && coverRetryTick == 0) {
                    delay(700)
                    coverRetryTick = 1
                }
            }
            val coverRequest = remember(context, model, coverReloadTick, coverRetryTick) {
                buildAuroraCoverImageRequest(context, model) {
                    if (coverRetryTick > 0) {
                        // Reintento: directo a la red, sin reutilizar una
                        // entrada de caché que pueda estar corrupta.
                        memoryCachePolicy(CachePolicy.DISABLED)
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
            }
            // Don't pass a `placeholder` painter directly: Coil's transformOf()
            // would unconditionally override the painter restored via
            // `placeholderMemoryCacheKey`, causing a gray flash even for covers
            // that are already in the memory cache. This transform only falls
            // back to the flat placeholder when no memory-cached cover is
            // available, and maps error/fallback states to the error painter.
            val transform = remember(resolvedErrorPainter) {
                { state: AsyncImagePainter.State ->
                    when (state) {
                        is AsyncImagePainter.State.Loading ->
                            if (state.painter == null) {
                                state.copy(painter = CoverPlaceholderPainter)
                            } else {
                                state
                            }
                        is AsyncImagePainter.State.Error -> state.copy(painter = resolvedErrorPainter)
                        else -> state
                    }
                }
            }
            AsyncImage(
                model = coverRequest,
                transform = transform,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error && coverRetryTick == 0) {
                        coverFailed = true
                    }
                },
                contentDescription = contentDescription,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
            )
        } else {
            Image(
                painter = resolvedErrorPainter,
                contentDescription = contentDescription,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal fun resolveCoverModel(data: Any?): Any? {
    return when (data) {
        is String -> data.takeIf { it.isNotBlank() }
        is Anime -> data
        is Manga -> data
        is Novel -> data
        is AnimeCover -> data
        is MangaCover -> data
        is NovelCover -> data
        else -> data
    }
}

internal fun isLoadableCoverData(data: Any?): Boolean {
    return when (data) {
        null -> false
        is String -> data.isNotBlank()
        is NovelCover -> !data.url.isNullOrBlank() || data.isNovelFavorite
        is AnimeCover -> !data.url.isNullOrBlank() || data.isAnimeFavorite
        is MangaCover -> !data.url.isNullOrBlank() || data.isMangaFavorite
        is Novel -> !data.thumbnailUrl.isNullOrBlank() || data.favorite
        is Anime -> !data.thumbnailUrl.isNullOrBlank() || data.favorite
        is Manga -> !data.thumbnailUrl.isNullOrBlank() || data.favorite
        else -> true
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
private val CoverPlaceholderPainter = ColorPainter(CoverPlaceholderColor)
