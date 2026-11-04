package ca.cheungeric.lounge.ui.patchnotes

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes
import ca.cheungeric.lounge.ui.theme.LoungeTheme
import kotlinx.coroutines.launch

/** The ONE patch-note renderer, shared by Settings → Patch Notes and the What's New dialog. */
@Composable
fun PatchNoteEntry(entry: PatchNote, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(LoungeShapes.Medium)
    val bg = if (isCurrent) LoungeColors.PrimaryContainer.copy(alpha = 0.28f) else LoungeColors.SurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .background(bg, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.title,
                color = LoungeColors.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isCurrent) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "Current",
                    color = LoungeColors.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, LoungeColors.Primary), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            "v${entry.v} · ${entry.date}",
            color = LoungeColors.OnSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        entry.notes.forEach { note ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("•", color = LoungeColors.OnSurfaceVariant, fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(note, color = LoungeColors.OnSurface, fontSize = 16.sp)
            }
        }
    }
}

/**
 * Settings → "Patch Notes": heading with the current version, then every entry (newest first).
 * A plain Column so it can sit inside the Settings screen's own scroll container; each entry is
 * focusable so D-pad Up/Down walks (and scrolls) through the list.
 */
@Composable
fun PatchNotesList(notes: List<PatchNote>, current: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Patch Notes", color = LoungeColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Text("v$current", color = LoungeColors.OnSurfaceVariant, fontSize = 16.sp)
        }
        notes.forEach { entry ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            PatchNoteEntry(
                entry,
                isCurrent = entry.v == current,
                modifier = Modifier
                    .border(
                        BorderStroke(2.dp, if (focused) LoungeColors.FocusRing else LoungeColors.SurfaceVariant.copy(alpha = 0f)),
                        RoundedCornerShape(LoungeShapes.Medium),
                    )
                    .focusable(interactionSource = interaction),
            )
        }
    }
}

/**
 * "What's New" popup for [entries] (from [checkWhatsNew]). "Got it" takes focus; D-pad Up/Down
 * scroll the notes when they are taller than the card.
 */
@Composable
fun WhatsNewDialog(entries: List<PatchNote>, onDismiss: () -> Unit, current: Int = APP_VERSION) {
    Dialog(onDismissRequest = onDismiss) {
        WhatsNewCard(entries, onDismiss, current)
    }
}

@Composable
private fun WhatsNewCard(entries: List<PatchNote>, onDismiss: () -> Unit, current: Int) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val gotIt = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { gotIt.requestFocus() } }

    Column(
        Modifier
            .widthIn(max = 640.dp)
            .background(LoungeColors.Surface, RoundedCornerShape(LoungeShapes.Large))
            .padding(28.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val step = 160f
                when (ev.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (scroll.canScrollForward) {
                        scope.launch { scroll.animateScrollBy(step) }; true
                    } else false
                    KeyEvent.KEYCODE_DPAD_UP -> if (scroll.canScrollBackward) {
                        scope.launch { scroll.animateScrollBy(-step) }; true
                    } else false
                    else -> false
                }
            },
    ) {
        Text("What's New", color = LoungeColors.OnSurface, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.padding(top = 16.dp))
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            entries.forEach { PatchNoteEntry(it, isCurrent = it.v == current) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.CenterEnd) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(gotIt),
                colors = ButtonDefaults.colors(
                    containerColor = LoungeColors.PrimaryContainer,
                    contentColor = LoungeColors.OnSurface,
                    focusedContainerColor = LoungeColors.Primary,
                    focusedContentColor = LoungeColors.Background,
                ),
            ) {
                Text("Got it", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 540, backgroundColor = 0xFF14121A, showBackground = true)
@Composable
private fun WhatsNewCardPreview() {
    LoungeTheme { Box(Modifier.padding(32.dp)) { WhatsNewCard(PATCH_NOTES, {}, APP_VERSION) } }
}

@Preview(widthDp = 960, heightDp = 540, backgroundColor = 0xFF14121A, showBackground = true)
@Composable
private fun PatchNotesListPreview() {
    LoungeTheme { Box(Modifier.padding(32.dp)) { PatchNotesList(PATCH_NOTES, APP_VERSION) } }
}
