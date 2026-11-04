package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.ui.theme.LoungeColors

/** "Continue watching" label + 3 dashed, non-focusable placeholder cards (dev flag only; Trakt is phase 2). */
@Composable
fun ContinueWatchingPlaceholder(modifier: Modifier = Modifier) {
    val tile = TileSize.Favorite
    Column(modifier) {
        RowLabel(stringResource(R.string.home_continue_watching))
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(horizontal = OverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            repeat(3) {
                Spacer(
                    Modifier
                        .size(tile.width, tile.height)
                        .drawBehind {
                            val stroke = 2.dp.toPx()
                            val r = tile.cornerRadius.toPx()
                            drawRoundRect(
                                color = LoungeColors.Outline,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                cornerRadius = CornerRadius(r, r),
                                style = Stroke(
                                    width = stroke,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                                ),
                            )
                        },
                )
            }
        }
    }
}
