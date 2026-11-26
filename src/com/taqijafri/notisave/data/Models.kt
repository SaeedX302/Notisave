package com.taqijafri.notisave.data

/** One notification captured by the listener and stored locally. */
data class SavedNotification(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String = "",
    val postTime: Long,
    val isRead: Boolean = false
) {
    /** The full message body: expanded text when available, otherwise the short text. */
    val fullText: String
        get() = if (bigText.isNotBlank()) bigText else text
}

/** An installed app with its save toggle and saved-notification count. */
data class AppEntry(
    val packageName: String,
    val appName: String,
    val savedCount: Int,
    val enabled: Boolean
)

/** App ranking for the dashboard. */
data class TopApp(
    val packageName: String,
    val appName: String,
    val count: Int
)
