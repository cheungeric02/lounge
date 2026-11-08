package ca.cheungeric.lounge.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.ui.background.AmbientStyle
import ca.cheungeric.lounge.ui.background.BackgroundPrefs
import ca.cheungeric.lounge.ui.background.BackgroundSettings
import ca.cheungeric.lounge.ui.background.BgImage
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.remote.BraviaControl
import ca.cheungeric.lounge.remote.BraviaRpc
import ca.cheungeric.lounge.remote.RemoteKey
import ca.cheungeric.lounge.remote.RemoteStatus
import ca.cheungeric.lounge.remote.qr.QrCode
import ca.cheungeric.lounge.ui.ambient.AmbientIdle
import ca.cheungeric.lounge.ui.home.springFocus
import ca.cheungeric.lounge.ui.patchnotes.PATCH_NOTES
import ca.cheungeric.lounge.ui.patchnotes.PatchNotesList
import ca.cheungeric.lounge.ui.theme.LoungeButton
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes
import ca.cheungeric.lounge.update.UpdateState

/**
 * Settings: Home button, Phone remote, TV power control, Ambient screen, Updates, Hidden apps, Patch Notes, About. Stateless; the activity re-reads
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
    mediaAccessGranted: Boolean,
    onOpenMediaAccess: () -> Unit,
    braviaIp: String,
    braviaPsk: String,
    braviaTest: BraviaTestState,
    braviaBoxPort: Int,
    onBraviaBoxPort: (Int) -> Unit,
    onBraviaSave: (ip: String, psk: String) -> Unit,
    onBraviaTest: (ip: String, psk: String) -> Unit,
    ambientMinutes: Int,
    onAmbientMinutes: (Int) -> Unit,
    background: BackgroundSettings = BackgroundSettings(),
    onBackgroundChange: (BackgroundSettings) -> Unit = {},
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

        Section(stringResource(R.string.settings_background)) {
            BackgroundSection(background, onBackgroundChange)
        }

        Section(stringResource(R.string.settings_remote)) {
            PhoneRemoteSection(
                enabled = remoteEnabled,
                key = remoteKey,
                status = remoteStatus,
                homeServiceEnabled = homeServiceEnabled,
                onToggle = onRemoteToggle,
                onNewKey = onRemoteNewKey,
                mediaAccessGranted = mediaAccessGranted,
                onOpenMediaAccess = onOpenMediaAccess,
            )
        }

        Section(stringResource(R.string.settings_tv_power)) {
            TvPowerSection(braviaIp, braviaPsk, braviaTest, braviaBoxPort, onBraviaBoxPort, onBraviaSave, onBraviaTest)
        }

        Section(stringResource(R.string.settings_ambient)) {
            Text(
                stringResource(R.string.settings_ambient_desc),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AmbientIdle.OPTIONS.forEach { m ->
                    LoungeButton(
                        if (m == 0) stringResource(R.string.settings_ambient_off)
                        else stringResource(R.string.settings_ambient_minutes, m),
                        onClick = { onAmbientMinutes(m) },
                        primary = m == ambientMinutes,
                    )
                }
            }
            Text(
                stringResource(R.string.settings_ambient_style),
                style = MaterialTheme.typography.titleLarge,
                color = LoungeColors.OnSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AmbientStyle.entries.forEach { st ->
                    LoungeButton(
                        stringResource(
                            when (st) {
                                AmbientStyle.Webb -> R.string.settings_ambient_style_webb
                                AmbientStyle.Black -> R.string.settings_ambient_style_black
                            },
                        ),
                        onClick = { onBackgroundChange(background.copy(ambientStyle = st)) },
                        primary = st == background.ambientStyle,
                    )
                }
            }
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
            Text(
                stringResource(R.string.settings_about_photos),
                style = MaterialTheme.typography.titleMedium,
                color = LoungeColors.TertiaryText,
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
    mediaAccessGranted: Boolean,
    onOpenMediaAccess: () -> Unit,
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
    NowPlayingAccessRow(mediaAccessGranted, onOpenMediaAccess)
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

/** v6 Background: Home photo (or Black) and how dim it is; changes apply to Home immediately. */
@Composable
private fun BackgroundSection(bg: BackgroundSettings, onChange: (BackgroundSettings) -> Unit) {
    Text(
        stringResource(R.string.settings_bg_photo),
        style = MaterialTheme.typography.titleLarge,
        color = LoungeColors.OnSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BgImage.entries.forEach { img ->
            LoungeButton(
                stringResource(
                    when (img) {
                        BgImage.Cliffs -> R.string.settings_bg_cliffs
                        BgImage.Pillars -> R.string.settings_bg_pillars
                        BgImage.Deep -> R.string.settings_bg_deep
                        BgImage.Black -> R.string.settings_bg_black
                    },
                ),
                onClick = { onChange(bg.copy(image = img)) },
                primary = img == bg.image,
            )
        }
    }
    Text(
        stringResource(R.string.settings_bg_dim),
        style = MaterialTheme.typography.titleLarge,
        color = LoungeColors.OnSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BackgroundPrefs.DIM_LEVELS.forEachIndexed { i, d ->
            LoungeButton(
                stringResource(
                    when (i) {
                        0 -> R.string.settings_bg_dim_light
                        1 -> R.string.settings_bg_dim_medium
                        else -> R.string.settings_bg_dim_strong
                    },
                ),
                onClick = { onChange(bg.copy(dim = d)) },
                primary = d == bg.dim,
            )
        }
    }
}

/** v5 Settings → "TV power control" Test connection / Save feedback. */
sealed interface BraviaTestState {
    data object Idle : BraviaTestState
    data object Saved : BraviaTestState
    data object Testing : BraviaTestState
    data class Done(val result: BraviaControl.Result) : BraviaTestState
}

/**
 * TV power control: Bravia IP + Pre-Shared Key fields, Save, Test connection + result, setup steps.
 * The key field is a plain (non-password) text field so the Lounge Remote phone keyboard can fill
 * it; it is only shown masked while not focused.
 */
@Composable
private fun TvPowerSection(
    savedIp: String,
    savedPsk: String,
    test: BraviaTestState,
    boxPort: Int,
    onBoxPort: (Int) -> Unit,
    onSave: (ip: String, psk: String) -> Unit,
    onTest: (ip: String, psk: String) -> Unit,
) {
    var ip by remember(savedIp) { mutableStateOf(savedIp) }
    var psk by remember(savedPsk) { mutableStateOf(savedPsk) }

    Text(
        stringResource(R.string.settings_box_port),
        style = MaterialTheme.typography.titleLarge,
        color = LoungeColors.OnSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        (1..4).forEach { p ->
            LoungeButton(
                stringResource(R.string.settings_box_port_n, p),
                onClick = { onBoxPort(p) },
                primary = p == boxPort,
            )
        }
    }
    SettingsTextField(stringResource(R.string.settings_bravia_ip), ip, { ip = it }, KeyboardType.Uri, mask = false)
    SettingsTextField(stringResource(R.string.settings_bravia_psk), psk, { psk = it }, KeyboardType.Ascii, mask = true)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LoungeButton(stringResource(R.string.settings_bravia_save), onClick = { onSave(ip, psk) })
        LoungeButton(
            stringResource(R.string.settings_bravia_test),
            onClick = { if (test != BraviaTestState.Testing) onTest(ip, psk) },
            primary = true,
        )
    }
    braviaTestText(test)?.let {
        Text(it, style = MaterialTheme.typography.titleLarge, color = LoungeColors.OnSurface)
    }
    Text(
        stringResource(R.string.settings_bravia_steps),
        style = MaterialTheme.typography.titleMedium,
        color = LoungeColors.OnSurfaceVariant,
        modifier = Modifier.widthIn(max = 900.dp),
    )
    Text(
        stringResource(R.string.settings_bravia_box_awake),
        style = MaterialTheme.typography.titleMedium,
        color = LoungeColors.OnSurfaceVariant,
        modifier = Modifier.widthIn(max = 900.dp),
    )
}

@Composable
private fun braviaTestText(state: BraviaTestState): String? = when (state) {
    BraviaTestState.Idle -> null
    BraviaTestState.Saved -> stringResource(R.string.settings_bravia_saved)
    BraviaTestState.Testing -> stringResource(R.string.settings_bravia_testing)
    is BraviaTestState.Done -> when (val r = state.result) {
        is BraviaControl.Result.Ok -> stringResource(
            when (r.state) {
                BraviaRpc.PowerState.On -> R.string.settings_bravia_on
                BraviaRpc.PowerState.Standby -> R.string.settings_bravia_standby
                else -> R.string.settings_bravia_connected
            },
        )
        BraviaControl.Result.WrongKey -> stringResource(R.string.settings_bravia_wrong_key)
        BraviaControl.Result.Unreachable -> stringResource(R.string.settings_bravia_unreachable)
        BraviaControl.Result.NotConfigured -> stringResource(R.string.settings_bravia_not_configured)
        is BraviaControl.Result.Error -> stringResource(R.string.settings_bravia_error, r.message)
    }
}

/** Shows every character as a bullet without marking the field as a password (keeps phone typing working). */
private object BulletMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString("•".repeat(text.length)), OffsetMapping.Identity)
}

/** Labelled single-line text field with the shared spring focus ring; OK opens the keyboard. */
@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    mask: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = LoungeColors.OnSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = { v -> onValueChange(v.filterNot { it == '\n' || it == '\r' }.take(128)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = LoungeColors.OnSurface),
            cursorBrush = SolidColor(LoungeColors.Primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = if (mask && !focused && value.isNotEmpty()) BulletMask else VisualTransformation.None,
            interactionSource = interaction,
            modifier = Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .springFocus(focused, cornerRadius = LoungeShapes.Medium)
                .background(LoungeColors.SurfaceVariant, LoungeShapes.MediumShape)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** Phone remote -> Now Playing access: status, a shortcut to Notification access, always-visible manual steps. */
@Composable
private fun NowPlayingAccessRow(granted: Boolean, onOpen: () -> Unit) {
    Text(
        stringResource(R.string.settings_media_access),
        style = MaterialTheme.typography.titleLarge,
        color = LoungeColors.OnSurface,
    )
    Text(
        stringResource(if (granted) R.string.settings_media_access_granted else R.string.settings_media_access_not_granted),
        style = MaterialTheme.typography.titleLarge,
        color = LoungeColors.OnSurfaceVariant,
    )
    LoungeButton(stringResource(R.string.settings_open_media_access), onClick = onOpen)
    Text(
        stringResource(R.string.settings_media_access_steps),
        style = MaterialTheme.typography.titleLarge,
        color = LoungeColors.OnSurfaceVariant,
    )
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
