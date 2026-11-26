package com.taqijafri.notisave.util

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import com.taqijafri.notisave.service.NotiListenerService
import java.util.Calendar
import java.util.Date

object Constants {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val MESSENGER = "com.facebook.orca"
    const val TELEGRAM = "org.telegram.messenger"
    const val TELEGRAM_WEB = "org.telegram.messenger.web"
    const val INSTAGRAM = "com.instagram.android"
    const val FACEBOOK = "com.facebook.katana"
    const val SMS_GOOGLE = "com.google.android.apps.messaging"
    const val SMS_SAMSUNG = "com.samsung.android.messaging"
    const val SMS_ANDROID = "com.android.mms"

    /** WhatsApp packages (regular + Business) — used for the deleted-messages view. */
    val WHATSAPP_PACKAGES = setOf(WHATSAPP, WHATSAPP_BUSINESS)

    /** Packages never saved: our own app and the system UI. */
    val BLOCKED_PACKAGES = setOf(
        "com.taqijafri.notisave",
        "com.android.systemui"
    )

    const val WHATSAPP_CONTACT_URL = "https://wa.me/923077982214"
}

object AppInfo {

    fun getAppName(pm: PackageManager, packageName: String): String {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun getAppIcon(pm: PackageManager, packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

object ListenerHelper {

    /** True when the user has granted Notification Access to Notisave. */
    fun isListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, NotiListenerService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(cn.flattenToString())
    }

    fun openListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Forces Android to rebind the listener (the standard workaround when it stalls). */
    fun restartListener(context: Context) {
        val pm = context.packageManager
        val cn = ComponentName(context, NotiListenerService::class.java)
        pm.setComponentEnabledSetting(
            cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}

object TimeUtils {

    /** Friendly relative time: "Just now", "5m ago", "2h ago", "Yesterday", else "12 Mar, 10:24". */
    fun relativeTime(timeMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timeMs
        if (diff < 60_000) return "Just now"
        if (diff < 3_600_000) return "${diff / 60_000}m ago"
        val calNow = Calendar.getInstance()
        val calThen = Calendar.getInstance().apply { timeInMillis = timeMs }
        val sameDay = calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "${diff / 3_600_000}h ago"
        calNow.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
        if (yesterday) return "Yesterday"
        return DateFormat.format("dd MMM, HH:mm", Date(timeMs)).toString()
    }

    /** Full timestamp for the detail screen. */
    fun fullTime(timeMs: Long): String =
        DateFormat.format("dd MMM yyyy, hh:mm a", Date(timeMs)).toString()

    fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun startOfWeek(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

/** Pulls the readable text out of a posted notification's extras. */
object NotificationText {

    /**
     * Returns Pair(title, text). Prefers MessagingStyle messages when present
     * (WhatsApp group chats), then big text, then the short text.
     */
    fun extract(extras: android.os.Bundle): Pair<String, String> {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

        // MessagingStyle (group chats): join the individual messages.
        val messagesText = try {
            val arr = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (arr != null && arr.isNotEmpty()) {
                arr.filterIsInstance<Notification.MessagingStyle.Message>()
                    .mapNotNull { it.text?.toString() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            } else ""
        } catch (e: Exception) {
            ""
        }

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()

        val body = when {
            messagesText.isNotBlank() -> messagesText
            bigText.isNotBlank() -> bigText
            text.isNotBlank() -> text
            subText.isNotBlank() -> subText
            else -> ""
        }
        return Pair(title, body)
    }
}
