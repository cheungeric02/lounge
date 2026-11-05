package ca.cheungeric.lounge.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.remote.RemoteKey
import ca.cheungeric.lounge.remote.RemoteStatus
import ca.cheungeric.lounge.remote.qr.QrCode
import ca.cheungeric.lounge.ui.patchnotes.PATCH_NOTES
import ca.cheungeric.lounge.ui.patchnotes.PatchNotesList
import ca.cheungeric.lounge.ui.theme.LoungeButton
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes
import ca.cheungeric.lounge.update.UpdateState

/**
 * Settings: Home button, Phone remote, Updates, Hidden apps, Patch Notes, About. Stateless; the activity re-reads
 * [homeServiceEnabled] on resume. Initial focus goes to the first button.
 */
@Composable
fun SettingsScreen(
    appVersion: Int,
    homeServiceEnabled: Boolean,
    updateState: UpdateState,
    hiddenApps: List<AppEntry>,
    onOpenAccessibility: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onUnhide: (AppEntry) -> Unit,
    remoteEnabled: Boolean,
    remoteKey: String?,
    remoteStatus: RemoteStatus,
    onRemoteToggle: (Boolean) -> Unit,
    onRemoteNewKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstButton.requestFocus() }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(LoungeColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            color = LoungeColors.OnSurface,
        )

        Section(stringResource(R.string.settings_home_button)) {
            Text(
                stringResource(if (homeServiceEnabled) R.string.settings_home_on else R.string.settings_home_off),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurfaceVariant,
            )
            LoungeButton(
                stringResource(R.string.settings_open_accessibility),
                onClick = onOpenAccessibility,
                modifier = Modifier.focusRequester(firstButton),
            )
            Text(
                stringResource(R.string.settings_home_steps),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurfaceVariant,
            )
        }

        Section(stringResource(R.string.settings_remote)) {
            PhoneRemoteSection(
                enabled = remoteEnabled,
                key = remoteKey,
                status = remoteStatus,
                homeServiceEnabled = homeServiceEnabled,
                onToggle = onRemoteToggle,
                onNewKey = onRemoteNewKey,
            )
        }

        Section(stringResource(R.string.settings_updates)) {
            Text(
                stringResource(R.string.settings_current_version, appVersion),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LoungeButton(stringResource(R.string.settings_check_updates), onClick = onCheckForUpdates)
                if (canInstall(updateState)) {
                    LoungeButton(stringResource(R.string.banner_install), onClick = onInstallUpdate, primary = true)
                }
            }
            updateStatusText(updateState)?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, color = LoungeColors.OnSurface)
            }
        }

        Section(stringResource(R.string.settings_hidden_apps)) {
            if (hiddenApps.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_hidden_apps),
                    style = MaterialTheme.typography.titleLarge,
                    color = LoungeColors.TertiaryText,
                )
            } else {
                hiddenApps.forEach { app ->
                    Row(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = LoungeColors.OnSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.padding(start = 16.dp))
                        LoungeButton(stringResource(R.string.settings_unhide), onClick = { onUnhide(app) })
                    }
                }
            }
        }

        Section(title = null) {
            PatchNotesList(PATCH_NOTES, appVersion, Modifier.widthIn(max = 960.dp))
        }

        Section(stringResource(R.string.settings_about)) {
            Text(
                stringResource(R.string.settings_about_version, appVersion),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurface,
            )
            Text(
                stringResource(R.string.settings_about_repo),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurfaceVariant,
            )
        }
    }
}

/**
 * Phone remote: On/Off toggle; when on, the pairing QR + key + short URL, status line, "New key"
 * with a confirm step, and a note when the Home-button service is off.
 */
@Composable
private fun PhoneRemoteSection(
    enabled: Boolean,
    key: String?,
    status: RemoteStatus,
    homeServiceEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onNewKey: () -> Unit,
) {
    var confirming by remember(enabled) { mutableStateOf(false) }
    // Bumped when the confirm row closes so focus returns to "New key" instead of being lost.
    var refocusNewKey by remember { mutableIntStateOf(0) }
    val newKeyButton = remember { FocusRequester() }
    val cancelButton = remember { FocusRequester() }

    LoungeButton(
        stringResource(if (enabled) R.string.settings_remote_on else R.string.settings_remote_off),
        onClick = { onToggle(!enabled) },
        primary = enabled,
    )
    if (!enabled) return

    Text(
        stringResource(remoteStatusText(status)),
        style = MaterialTheme.typography.titleLarge,
        color = LoungeColors.OnSurface,
    )
    if (key != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            QrCode(RemoteKey.pairingUrl(key), Modifier.size(220.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    key,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, fontWeight = FontWeight.W500),
                    color = LoungeColors.OnSurface,
                )
                Text(
                    stringResource(R.string.settings_remote_short_url),
                    style = MaterialTheme.typography.titleLarge,
                    color = LoungeColors.OnSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_remote_scan),
                    style = MaterialTheme.typography.titleMedium,
                    color = LoungeColors.TertiaryText,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
    }
    if (!homeServiceEnabled) {
        Text(
            stringResource(R.string.settings_remote_home_off_note),
            style = MaterialTheme.typography.titleLarge,
            color = LoungeColors.OnSurfaceVariant,
        )
    }
    if (confirming) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { cancelButton.requestFocus() }
        }
        Text(
            stringResource(R.string.settings_remote_new_key_confirm),
            style = MaterialTheme.typography.titleLarge,
            color = LoungeColors.OnSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LoungeButton(
                stringResource(R.string.settings_remote_new_key_yes),
                onClick = {
                    onNewKey()
                    confirming = false
                    refocusNewKey++
                },
                primary = true,
            )
            LoungeButton(
                stringResource(R.string.settings_remote_cancel),
                onClick = {
                    confirming = false
                    refocusNewKey++
                },
                modifier = Modifier.focusRequester(cancelButton),
            )
        }
    } else {
        if (refocusNewKey > 0) {
            LaunchedEffect(refocusNewKey) {
                withFrameNanos { }
                runCatching { newKeyButton.requestFocus() }
            }
        }
        LoungeButton(
            stringResource(R.string.settings_remote_new_key),
            onClick = { confirming = true },
            modifier = Modifier.focusRequester(newKeyButton),
        )
    }
}

internal fun remoteStatusText(status: RemoteStatus): Int = when (status) {
    RemoteStatus.Connected -> R.string.settings_remote_status_connected
    RemoteStatus.Connecting -> R.string.settings_remote_status_connecting
    RemoteStatus.Offline -> R.string.settings_remote_status_offline
    RemoteStatus.Off -> R.string.settings_remote_status_off
}

/** Tonal card with an optional heading. */
@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .widthIn(max = 1000.dp)
            .fillMaxWidth()
            .background(LoungeColors.Surface, LoungeShapes.LargeShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = LoungeColors.OnSurface)
        }
        content()
    }
}

/** States where an Install (or retry) button makes sense. */
internal fun canInstall(state: UpdateState): Boolean = when (state) {
    is UpdateState.Available, is UpdateState.PermissionRequired -> true
    is UpdateState.Failed -> state.info != null
    else -> false
}

@Composable
internal fun updateStatusText(state: UpdateState): String? = when (state) {
    UpdateState.Idle -> null
    UpdateState.Checking -> stringResource(R.string.update_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
    is UpdateState.Available -> stringResource(R.string.update_available, state.info.versionCode)
    is UpdateState.Downloading ->
        if (state.progress < 0f) stringResource(R.string.update_downloading_unknown)
        else stringResource(R.string.update_downloading, (state.progress * 100).toInt().coerceIn(0, 100))
    UpdateState.Installing -> stringResource(R.string.update_installing)
    is UpdateState.PermissionRequired -> stringResource(R.string.update_permission_required)
    is UpdateState.Failed -> state.message
}
