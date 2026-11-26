package com.taqijafri.notisave

import android.app.Activity
import android.app.AlertDialog
import android.app.Fragment
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.ui.AppsFragment
import com.taqijafri.notisave.ui.HomeFragment
import com.taqijafri.notisave.ui.NotificationsFragment
import com.taqijafri.notisave.ui.SettingsFragment
import com.taqijafri.notisave.ui.StatusesFragment
import com.taqijafri.notisave.util.ListenerHelper
import com.taqijafri.notisave.util.ThemeHelper

/**
 * Single-activity host with bottom navigation:
 * Home, Notifications, Apps, Settings.
 */
class MainActivity : Activity() {

    companion object {
        const val TAB_HOME = 0
        const val TAB_NOTIFICATIONS = 1
        const val TAB_STATUSES = 2
        const val TAB_APPS = 3
        const val TAB_SETTINGS = 4
    }

    private lateinit var prefs: Prefs
    private lateinit var db: NotiDbHelper

    private val homeFragment = HomeFragment()
    private val notificationsFragment = NotificationsFragment()
    private val statusesFragment = StatusesFragment()
    private val appsFragment = AppsFragment()
    private val settingsFragment = SettingsFragment()

    private var currentTab = -1

    private lateinit var navIcons: Array<ImageView>
    private lateinit var navLabels: Array<TextView>
    private lateinit var navItems: Array<LinearLayout>
    private lateinit var badge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        db = NotiDbHelper.getInstance(this)

        navIcons = arrayOf(
            findViewById(R.id.nav_home_icon),
            findViewById(R.id.nav_notifications_icon),
            findViewById(R.id.nav_statuses_icon),
            findViewById(R.id.nav_apps_icon),
            findViewById(R.id.nav_settings_icon)
        )
        navLabels = arrayOf(
            findViewById(R.id.nav_home_label),
            findViewById(R.id.nav_notifications_label),
            findViewById(R.id.nav_statuses_label),
            findViewById(R.id.nav_apps_label),
            findViewById(R.id.nav_settings_label)
        )
        navItems = arrayOf(
            findViewById(R.id.nav_home),
            findViewById(R.id.nav_notifications),
            findViewById(R.id.nav_statuses),
            findViewById(R.id.nav_apps),
            findViewById(R.id.nav_settings)
        )
        badge = findViewById(R.id.nav_badge)

        navItems.forEachIndexed { index, item ->
            item.setOnClickListener { selectTab(index) }
        }

        selectTab(savedInstanceState?.getInt("tab", TAB_HOME) ?: TAB_HOME)
        showWelcomeIfFirstRun()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab)
    }

    override fun onResume() {
        super.onResume()
        refreshBadge()
    }

    fun selectTab(index: Int) {
        if (index == currentTab) return
        currentTab = index
        val fragment: Fragment = when (index) {
            TAB_NOTIFICATIONS -> notificationsFragment
            TAB_STATUSES -> statusesFragment
            TAB_APPS -> appsFragment
            TAB_SETTINGS -> settingsFragment
            else -> homeFragment
        }
        fragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        updateNavAppearance()
        refreshBadge()
    }

    /** Called by Settings when the theme changes. */
    fun recreateWithTheme() {
        recreate()
    }

    fun refreshBadge() {
        if (!::db.isInitialized) return
        val unread = db.getUnreadCount()
        if (unread > 0) {
            badge.visibility = View.VISIBLE
            badge.text = if (unread > 99) "99+" else unread.toString()
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun updateNavAppearance() {
        val selectedColor = Color.parseColor("#4F46E5")
        val unselectedColor = Color.parseColor("#9CA3AF")
        navIcons.forEachIndexed { i, icon ->
            icon.imageTintList = ColorStateList.valueOf(
                if (i == currentTab) selectedColor else unselectedColor
            )
        }
        navLabels.forEachIndexed { i, label ->
            label.setTextColor(if (i == currentTab) selectedColor else unselectedColor)
        }
    }

    private fun showWelcomeIfFirstRun() {
        if (prefs.privacyShown) return
        AlertDialog.Builder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_msg)
            .setPositiveButton(R.string.btn_continue) { _, _ ->
                prefs.privacyShown = true
                ListenerHelper.openListenerSettings(this)
            }
            .setNegativeButton(R.string.btn_not_now) { _, _ ->
                prefs.privacyShown = true
            }
            .setCancelable(false)
            .show()
    }
}
