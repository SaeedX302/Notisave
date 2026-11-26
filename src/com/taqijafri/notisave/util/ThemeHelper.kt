package com.taqijafri.notisave.util

import android.app.Activity
import android.content.res.Configuration
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.Prefs

/** Applies the user's theme choice (system / light / dark) to an activity. */
object ThemeHelper {

    fun applyTheme(activity: Activity) {
        val prefs = Prefs(activity)
        val dark = when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> false
            Prefs.THEME_DARK -> true
            else -> {
                val night = activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
        activity.setTheme(
            if (dark) R.style.Theme_Notisave_Dark else R.style.Theme_Notisave_Light
        )
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
