package ca.cheungeric.lounge.remote.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val QUIET_ZONE_MODULES = 4

/** Draws [text] as a QR code: black modules on a white rounded card with a 4-module quiet zone. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrEncoder.encode(text) }
    Canvas(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
    ) {
        val cells = matrix.size + QUIET_ZONE_MODULES * 2
        val cell = size.minDimension / cells
        // Slight overlap avoids hairline seams between adjacent modules after anti-aliasing.
        val drawCell = Size(cell + 0.5f, cell + 0.5f)
        val originX = (size.width - cell * cells) / 2f + cell * QUIET_ZONE_MODULES
        val originY = (size.height - cell * cells) / 2f + cell * QUIET_ZONE_MODULES
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, topLeft = Offset(originX + x * cell, originY + y * cell), size = drawCell)
                }
            }
        }
    }
}
