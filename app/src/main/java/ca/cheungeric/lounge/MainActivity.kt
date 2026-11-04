package ca.cheungeric.lounge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeTheme

/** HOME + LEANBACK_LAUNCHER activity. TODO(T4): host HomeScreen / SettingsScreen, boot checks. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoungeTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LoungeColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Lounge", color = LoungeColors.OnSurface, fontSize = 48.sp)
                }
            }
        }
    }
}
