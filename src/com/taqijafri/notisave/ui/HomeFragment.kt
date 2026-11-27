package com.taqijafri.notisave.ui

import android.app.Activity
import android.app.Fragment
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.notisave.MainActivity
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.util.AppInfo
import com.taqijafri.notisave.util.Constants
import com.taqijafri.notisave.util.ListenerHelper
import com.taqijafri.notisave.util.StatusHelper
import com.taqijafri.notisave.util.TimeUtils

/** Dashboard: pending setup, statistics, top apps, recent notifications. */
class HomeFragment : Fragment() {

    private lateinit var db: NotiDbHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        db = NotiDbHelper.getInstance(activity)

        view.findViewById<Button>(R.id.btn_access).setOnClickListener {
            ListenerHelper.openListenerSettings(activity)
        }
        view.findViewById<TextView>(R.id.see_all_apps).setOnClickListener {
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_APPS)
        }
        view.findViewById<TextView>(R.id.see_all_recent).setOnClickListener {
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_NOTIFICATIONS)
        }
        view.findViewById<Button>(R.id.btn_choose_folder).setOnClickListener {
            StatusHelper.openFolderPicker(this)
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != StatusHelper.PICK_FOLDER_REQUEST) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val uri = data?.data
        if (resultCode == Activity.RESULT_OK && uri != null) {
            val ok = StatusHelper.handleFolderPicked(activity, uri)
            Toast.makeText(
                activity,
                if (ok) R.string.status_folder_picked else R.string.status_folder_failed,
                Toast.LENGTH_SHORT
            ).show()
            refresh()
        }
    }

    fun refresh() {
        val view = view ?: return
        val activity = activity ?: return

        // --- setup: only show what is still missing ---
        val needsAccess = !ListenerHelper.isListenerEnabled(activity)
        val needsFolder = !StatusHelper.hasFolderAccess(activity)
        view.findViewById<View>(R.id.access_row).visibility = if (needsAccess) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.folder_row).visibility = if (needsFolder) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.setup_divider).visibility =
            if (needsAccess && needsFolder) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.setup_card).visibility =
            if (needsAccess || needsFolder) View.VISIBLE else View.GONE

        // --- stats ---
        view.findViewById<TextView>(R.id.stat_total).text = db.getTotalCount().toString()
        view.findViewById<TextView>(R.id.stat_today).text =
            db.getCountSince(TimeUtils.startOfToday()).toString()
        view.findViewById<TextView>(R.id.stat_week).text =
            db.getCountSince(TimeUtils.startOfWeek()).toString()
        val waCount = Constants.WHATSAPP_PACKAGES.sumOf { db.getCountByPackage(it) }
        view.findViewById<TextView>(R.id.stat_whatsapp).text = waCount.toString()

        // --- top apps ---
        val topContainer = view.findViewById<LinearLayout>(R.id.top_apps_container)
        topContainer.removeAllViews()
        val topApps = db.getTopApps(5)
        if (topApps.isEmpty()) {
            val empty = TextView(activity).apply {
                text = getString(R.string.no_notifications_yet)
                setTextColor(resources.getColor(android.R.color.darker_gray))
                textSize = 14f
            }
            topContainer.addView(empty)
        } else {
            val inflater = LayoutInflater.from(activity)
            for (app in topApps) {
                val row = inflater.inflate(R.layout.item_top_app, topContainer, false)
                val icon = row.findViewById<ImageView>(R.id.top_app_icon)
                val drawable = AppInfo.getAppIcon(activity.packageManager, app.packageName)
                if (drawable != null) icon.setImageDrawable(drawable)
                else icon.setImageResource(android.R.drawable.sym_def_app_icon)
                row.findViewById<TextView>(R.id.top_app_name).text = app.appName
                row.findViewById<TextView>(R.id.top_app_count).text = app.count.toString()
                row.setOnClickListener {
                    (activity as? MainActivity)?.selectTab(MainActivity.TAB_NOTIFICATIONS)
                }
                topContainer.addView(row)
            }
        }

        // --- recent ---
        val recentContainer = view.findViewById<LinearLayout>(R.id.recent_container)
        recentContainer.removeAllViews()
        val recent = db.getRecent(5)
        val redact = Prefs(activity).redactPreviews
        if (recent.isEmpty()) {
            val empty = TextView(activity).apply {
                text = getString(R.string.empty_hint)
                setTextColor(resources.getColor(android.R.color.darker_gray))
                textSize = 14f
            }
            recentContainer.addView(empty)
        } else {
            val inflater = LayoutInflater.from(activity)
            for (n in recent) {
                val row = inflater.inflate(R.layout.item_notification, recentContainer, false)
                val icon = row.findViewById<ImageView>(R.id.item_icon)
                val drawable = AppInfo.getAppIcon(activity.packageManager, n.packageName)
                if (drawable != null) icon.setImageDrawable(drawable)
                else icon.setImageResource(android.R.drawable.sym_def_app_icon)
                row.findViewById<TextView>(R.id.item_app_name).text = n.appName
                row.findViewById<TextView>(R.id.item_time).text =
                    TimeUtils.relativeTime(n.postTime)
                row.findViewById<TextView>(R.id.item_title).text = if (redact) getString(R.string.preview_hidden)
                    else if (n.title.isNotBlank()) n.title else n.appName
                row.findViewById<TextView>(R.id.item_text).text = if (redact) getString(R.string.preview_hidden) else n.text
                row.findViewById<View>(R.id.item_unread_dot).visibility =
                    if (n.isRead) View.GONE else View.VISIBLE
                row.setOnClickListener {
                    db.markRead(n.id)
                    openDetail(n.id)
                    refresh()
                }
                row.findViewById<View>(R.id.item_delete).setOnClickListener {
                    db.delete(n.id)
                    refresh()
                }
                recentContainer.addView(row)
            }
        }
    }

    private fun openDetail(id: Long) {
        val intent = Intent(activity, DetailActivity::class.java)
        intent.putExtra(DetailActivity.EXTRA_ID, id)
        startActivity(intent)
    }
}
