package ca.cheungeric.lounge.data

import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
    val launchIntent: Intent?,
)
