package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.ui.theme.LoungeButton
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes

/** "Update available — vN" with Install / Later (Home topContent). [status] replaces the title while busy. */
@Composable
fun UpdateBanner(
    versionCode: Int,
    status: String?,
    showInstall: Boolean,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BannerCard(
        title = stringResource(R.string.banner_update_available, versionCode),
        body = status,
        modifier = modifier,
    ) {
        if (showInstall) LoungeButton(stringResource(R.string.banner_install), onClick = onInstall, primary = true)
        LoungeButton(stringResource(R.string.banner_later), onClick = onLater)
    }
}

/** Onboarding tip shown while the Home-button service is off. */
@Composable
fun HomeButtonTip(onSetUp: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    BannerCard(
        title = stringResource(R.string.tip_home_title),
        body = stringResource(R.string.tip_home_body),
        modifier = modifier,
    ) {
        LoungeButton(stringResource(R.string.tip_set_up), onClick = onSetUp, primary = true)
        LoungeButton(stringResource(R.string.tip_dismiss), onClick = onDismiss)
    }
}

@Composable
private fun BannerCard(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit,
) {
    Row(
        modifier
            .widthIn(max = 1100.dp)
            .background(LoungeColors.SurfaceVariant, LoungeShapes.LargeShape)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = LoungeColors.OnSurface)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = LoungeColors.OnSurfaceVariant)
            }
        }
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            buttons()
        }
    }
}
