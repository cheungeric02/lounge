package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.remote.NowPlaying
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes

/** What the Home header shows for the current media session (SPEC "v3 → TV Home v3"). */
data class HomeNowPlaying(
    val title: String,
    val app: String,
    val playing: Boolean,
    val art: ImageBitmap? = null,
) {
    companion object {
        /** Pure mapping: null when nothing is playing or paused. Blank title falls back to the app name. */
        fun of(s: NowPlaying.Session?, art: ImageBitmap?): HomeNowPlaying? {
            if (s == null || !(s.isPlaying || s.isPaused)) return null
            val title = s.title.ifBlank { s.app }
            return HomeNowPlaying(title = title, app = s.app, playing = s.isPlaying, art = art)
        }
    }
}

/** Small non-focusable header pill: art thumb, ▶/❚❚ glyph, title and app. D-pad order unaffected. */
@Composable
fun NowPlayingPill(np: HomeNowPlaying?, modifier: Modifier = Modifier) {
    if (np == null) return
    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .background(LoungeColors.SurfaceVariant, LoungeShapes.Pill)
            .padding(start = 10.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
        if (np.art != null) {
            Image(np.art, contentDescription = null, modifier = thumb, contentScale = ContentScale.Crop)
        } else {
            Box(thumb.background(LoungeColors.PrimaryContainer), contentAlignment = Alignment.Center) {
                Text("♪", style = MaterialTheme.typography.titleLarge, color = LoungeColors.OnSurface)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (np.playing) "▶" else "❚❚",
            style = MaterialTheme.typography.titleMedium,
            color = LoungeColors.Primary,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                np.title,
                style = MaterialTheme.typography.titleMedium,
                color = LoungeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (np.playing) np.app else "${np.app} · Paused",
                style = MaterialTheme.typography.bodyMedium,
                color = LoungeColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
