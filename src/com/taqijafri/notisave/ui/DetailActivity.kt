package com.taqijafri.notisave.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.util.AppInfo
import com.taqijafri.notisave.util.ThemeHelper
import com.taqijafri.notisave.util.TimeUtils

/** Full view of one saved notification. */
class DetailActivity : Activity() {

    companion object {
        const val EXTRA_ID = "notification_id"
    }

    private var notificationId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        notificationId = intent.getLongExtra(EXTRA_ID, -1)
        val db = NotiDbHelper.getInstance(this)
        val n = db.getById(notificationId)
        if (n == null) {
            finish()
            return
        }
        db.markRead(n.id)

        val icon = findViewById<ImageView>(R.id.detail_icon)
        val drawable = AppInfo.getAppIcon(packageManager, n.packageName)
        if (drawable != null) icon.setImageDrawable(drawable)
        else icon.setImageResource(android.R.drawable.sym_def_app_icon)

        findViewById<TextView>(R.id.detail_app_name).text = n.appName
        findViewById<TextView>(R.id.detail_time).text = TimeUtils.fullTime(n.postTime)
        findViewById<TextView>(R.id.detail_package).text = n.packageName
        findViewById<TextView>(R.id.detail_title).text =
            if (n.title.isNotBlank()) n.title else n.appName
        findViewById<TextView>(R.id.detail_text).text = n.fullText

        findViewById<Button>(R.id.btn_detail_open).setOnClickListener {
            if (!AppInfo.openApp(this, n.packageName)) {
                Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btn_detail_share).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${n.appName} — ${n.title}\n${n.fullText}\n${TimeUtils.fullTime(n.postTime)}"
                )
            }
            startActivity(Intent.createChooser(share, getString(R.string.btn_share)))
        }
        findViewById<Button>(R.id.btn_detail_delete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setPositiveButton(R.string.btn_delete) { _, _ ->
                    db.delete(n.id)
                    finish()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
    }
}
