package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.cheungeric.lounge.ui.theme.LoungeColors

/** Simple Canvas glyphs (no icon library dependency). */

@Composable
internal fun GearGlyph(modifier: Modifier = Modifier, color: Color = LoungeColors.OnSurface) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val c = center
        val outer = size.minDimension / 2f
        val body = outer * 0.70f
        val toothW = outer * 0.36f
        val toothH = outer * 0.34f
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = c) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x - toothW / 2f, c.y - outer),
                    size = Size(toothW, toothH + outer * 0.1f),
                    cornerRadius = CornerRadius(toothW * 0.25f),
                )
            }
        }
        drawCircle(color, radius = body, center = c)
        drawCircle(Color.Black, radius = body * 0.42f, center = c, blendMode = BlendMode.Clear)
    }
}

@Composable
internal fun WeatherIcon(glyph: WeatherGlyph, description: String, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = description }) {
        when (glyph) {
            WeatherGlyph.Sun -> sun(center, size.minDimension * 0.22f)
            WeatherGlyph.Moon -> moon(center, size.minDimension * 0.30f)
            WeatherGlyph.PartlyCloudyDay -> {
                sun(Offset(size.width * 0.36f, size.height * 0.36f), size.minDimension * 0.16f)
                cloud(yShift = 0.08f)
            }
            WeatherGlyph.PartlyCloudyNight -> {
                moon(Offset(size.width * 0.36f, size.height * 0.34f), size.minDimension * 0.20f)
                cloud(yShift = 0.08f)
            }
            WeatherGlyph.Cloud -> cloud()
            WeatherGlyph.Fog -> fog()
            WeatherGlyph.Drizzle -> { cloud(yShift = -0.10f); drops(count = 3, long = false) }
            WeatherGlyph.Rain -> { cloud(yShift = -0.10f); drops(count = 3, long = true) }
            WeatherGlyph.Snow -> { cloud(yShift = -0.10f); flakes() }
            WeatherGlyph.Thunder -> { cloud(yShift = -0.10f); bolt() }
        }
    }
}

private val Sunny = Color(0xFFFFD27A)
private val CloudColor = LoungeColors.OnSurface
private val Accent = LoungeColors.Primary

private fun DrawScope.sun(c: Offset, r: Float) {
    drawCircle(Sunny, radius = r, center = c)
    val stroke = r * 0.32f
    repeat(8) { i ->
        rotate(i * 45f, pivot = c) {
            drawLine(Sunny, Offset(c.x, c.y - r * 1.45f), Offset(c.x, c.y - r * 1.9f), stroke, StrokeCap.Round)
        }
    }
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val path = Path().apply {
        addOval(Rect(c, r))
    }
    val bite = Path().apply {
        addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.45f), r * 0.9f))
    }
    val crescent = Path().apply { op(path, bite, PathOperation.Difference) }
    drawPath(crescent, Sunny)
}

/** Cloud of three overlapping circles on a rounded base; yShift moves it up (negative) or down. */
private fun DrawScope.cloud(yShift: Float = 0f) {
    val w = size.width
    val h = size.height
    val baseY = h * (0.66f + yShift)
    drawRoundRect(
        CloudColor,
        topLeft = Offset(w * 0.16f, baseY - h * 0.16f),
        size = Size(w * 0.70f, h * 0.20f),
        cornerRadius = CornerRadius(h * 0.10f),
    )
    drawCircle(CloudColor, radius = w * 0.15f, center = Offset(w * 0.36f, baseY - h * 0.12f))
    drawCircle(CloudColor, radius = w * 0.20f, center = Offset(w * 0.56f, baseY - h * 0.18f))
}

private fun DrawScope.fog() {
    val stroke = size.height * 0.08f
    listOf(0.32f to 0.82f, 0.50f to 0.74f, 0.68f to 0.86f).forEachIndexed { i, (y, end) ->
        val start = if (i == 1) 0.26f else 0.14f
        drawLine(CloudColor, Offset(size.width * start, size.height * y), Offset(size.width * end, size.height * y), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.drops(count: Int, long: Boolean) {
    val stroke = size.width * 0.07f
    val len = size.height * if (long) 0.16f else 0.08f
    for (i in 0 until count) {
        val x = size.width * (0.34f + i * 0.16f)
        val y = size.height * 0.72f
        drawLine(Accent, Offset(x, y), Offset(x - len * 0.3f, y + len), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.flakes() {
    for (i in 0 until 3) {
        drawCircle(CloudColor, radius = size.width * 0.045f, center = Offset(size.width * (0.34f + i * 0.16f), size.height * (0.78f + (i % 2) * 0.06f)))
    }
}

private fun DrawScope.bolt() {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.54f, h * 0.62f)
        lineTo(w * 0.40f, h * 0.82f)
        lineTo(w * 0.52f, h * 0.82f)
        lineTo(w * 0.46f, h * 0.98f)
        lineTo(w * 0.64f, h * 0.74f)
        lineTo(w * 0.52f, h * 0.74f)
        close()
    }
    drawPath(p, Sunny)
}
