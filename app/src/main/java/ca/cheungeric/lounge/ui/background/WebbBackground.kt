package ca.cheungeric.lounge.ui.background

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.ui.theme.LoungeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bundled Webb photo for [this]; null for [BgImage.Black]. Credit: NASA, ESA, CSA, STScI (CC BY 4.0). */
@get:DrawableRes
val BgImage.drawableRes: Int?
    get() = when (this) {
        BgImage.Cliffs -> R.drawable.webb_cosmic_cliffs
        BgImage.Pillars -> R.drawable.webb_pillars
        BgImage.Deep -> R.drawable.webb_deep_field
        BgImage.Black -> null
    }

/** Keeps only the most recently decoded photo (~3 MB at inSampleSize 2) for Home, drawer and ambient. */
private object WebbBitmapCache {
    private var key: BgImage? = null
    private var bitmap: ImageBitmap? = null

    @Synchronized
    fun get(image: BgImage): ImageBitmap? = bitmap.takeIf { key == image }

    @Synchronized
    fun put(image: BgImage, bmp: ImageBitmap) {
        key = image
        bitmap = bmp
    }

    fun decode(res: Resources, image: BgImage): ImageBitmap? {
        get(image)?.let { return it }
        return decodeFresh(res, image)?.also { put(image, it) }
    }

    /** Uncached decode (previews/screenshot tests, where a bitmap must not outlive its render session). */
    fun decodeFresh(res: Resources, image: BgImage): ImageBitmap? {
        val id = image.drawableRes ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        return runCatching { BitmapFactory.decodeResource(res, id, opts) }.getOrNull()?.asImageBitmap()
    }
}

/** Decodes [image] once (off the main thread, half resolution); null while loading or for Black. */
@Composable
fun rememberWebbBitmap(image: BgImage): ImageBitmap? {
    val res = LocalContext.current.resources
    // Previews/screenshot tests can't wait for the IO decode, so decode inline there.
    val inspection = LocalInspectionMode.current
    val state = remember(image) {
        mutableStateOf(if (inspection) WebbBitmapCache.decodeFresh(res, image) else WebbBitmapCache.get(image))
    }
    if (inspection) return state.value
    LaunchedEffect(image) {
        state.value = withContext(Dispatchers.IO) { WebbBitmapCache.decode(res, image) }
    }
    return state.value
}

/**
 * Full-screen Home/drawer background: the Webb photo (Crop) under a black scrim of [dim]% plus a
 * gradient darkening the bottom ~45%; Black or a not-yet-decoded photo shows plain Background.
 * [glow] (the focused app's tint) is drawn as a soft radial glow at the bottom.
 */
@Composable
fun WebbBackdrop(
    image: BgImage,
    dim: Int,
    modifier: Modifier = Modifier,
    glow: Color? = null,
) {
    val bmp = if (image == BgImage.Black) null else rememberWebbBitmap(image)
    Box(modifier.fillMaxSize().background(LoungeColors.Background)) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0, 100) / 100f)))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.6f),
                    ),
                ),
            )
        }
        if (glow != null) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height * 1.05f)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.3f), Color.Transparent),
                        center = c,
                        radius = size.width * 0.6f,
                    ),
                )
            }
        }
    }
}

/** Ken Burns zoom for the drifting screensaver. */
private const val KEN_BURNS_SCALE = 1.18f
private const val KEN_BURNS_LEG_MS = 90_000

/**
 * Screensaver background: [image] at ~30% brightness, scaled 1.18× and panning slowly back and forth
 * (one leg ≈ 90 s) so no pixel stays lit in place.
 */
@Composable
fun KenBurnsBackdrop(image: BgImage, modifier: Modifier = Modifier) {
    val bmp = rememberWebbBitmap(image)
    val t = rememberInfiniteTransition(label = "kenBurns")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(KEN_BURNS_LEG_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "kenBurnsPan",
    )
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (bmp != null) {
            Image(
                bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = KEN_BURNS_SCALE
                        scaleY = KEN_BURNS_SCALE
                        // Overscan per side is (scale - 1) / 2 of the size; stay inside it.
                        val spare = (KEN_BURNS_SCALE - 1f) / 2f * 0.9f
                        translationX = (p * 2f - 1f) * spare * size.width
                        translationY = (p * 2f - 1f) * spare * size.height * 0.5f
                    },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
        }
    }
}
