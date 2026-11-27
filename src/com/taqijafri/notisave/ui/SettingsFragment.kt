package com.taqijafri.notisave.ui

import android.app.AlertDialog
import android.app.Fragment
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.notisave.MainActivity
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.Prefs
import com.taqijafri.notisave.util.Constants
import com.taqijafri.notisave.util.ListenerHelper
import com.taqijafri.notisave.util.ThemeHelper

/** Settings: theme, retention, permissions, privacy, about developer. */
class SettingsFragment : Fragment() {

    private lateinit var db: NotiDbHelper
    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        db = NotiDbHelper.getInstance(activity)
        prefs = Prefs(activity)

        view.findViewById<Switch>(R.id.switch_capture_pause).apply {
            isChecked = prefs.capturePaused
            setOnCheckedChangeListener { _, enabled -> prefs.capturePaused = enabled }
        }
        view.findViewById<Switch>(R.id.switch_redact_previews).apply {
            isChecked = prefs.redactPreviews
            setOnCheckedChangeListener { _, enabled ->
                prefs.redactPreviews = enabled
                (activity as? MainActivity)?.recreate()
            }
        }

        // --- theme ---
        val themeGroup = view.findViewById<RadioGroup>(R.id.theme_group)
        when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> themeGroup.check(R.id.theme_light)
            Prefs.THEME_DARK -> themeGroup.check(R.id.theme_dark)
            else -> themeGroup.check(R.id.theme_system)
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.themeMode = when (checkedId) {
                R.id.theme_light -> Prefs.THEME_LIGHT
                R.id.theme_dark -> Prefs.THEME_DARK
                else -> Prefs.THEME_SYSTEM
            }
            (activity as? MainActivity)?.recreateWithTheme()
        }

        // --- retention ---
        val retentionGroup = view.findViewById<RadioGroup>(R.id.retention_group)
        when (prefs.retentionDays) {
            Prefs.RETENTION_30 -> retentionGroup.check(R.id.retention_30)
            Prefs.RETENTION_60 -> retentionGroup.check(R.id.retention_60)
            Prefs.RETENTION_UNLIMITED -> retentionGroup.check(R.id.retention_unlimited)
            else -> retentionGroup.check(R.id.retention_90)
        }
        retentionGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.retentionDays = when (checkedId) {
                R.id.retention_30 -> Prefs.RETENTION_30
                R.id.retention_60 -> Prefs.RETENTION_60
                R.id.retention_unlimited -> Prefs.RETENTION_UNLIMITED
                else -> Prefs.RETENTION_90
            }
        }

        // --- storage ---
        val total = db.getTotalCount()
        val size = ThemeHelper.formatSize(db.getDbSizeBytes(activity))
        view.findViewById<TextView>(R.id.storage_size).text = "$size • $total"
        view.findViewById<Button>(R.id.btn_prune).setOnClickListener {
            val days = prefs.retentionDays
            if (days > 0) {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.prune_confirm_title)
                    .setMessage(getString(R.string.prune_confirm_message, days))
                    .setPositiveButton(R.string.btn_delete) { _, _ ->
                        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                        db.deleteOlderThan(cutoff)
                        Toast.makeText(activity, R.string.pruned_toast, Toast.LENGTH_SHORT).show()
                        val newTotal = db.getTotalCount()
                        val newSize = ThemeHelper.formatSize(db.getDbSizeBytes(activity))
                        view.findViewById<TextView>(R.id.storage_size).text = "$newSize • $newTotal"
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }

        // --- permissions ---
        view.findViewById<TextView>(R.id.access_status_text).text =
            getString(
                if (ListenerHelper.isListenerEnabled(activity)) R.string.status_enabled
                else R.string.status_disabled
            )
        view.findViewById<Button>(R.id.btn_access_settings).setOnClickListener {
            ListenerHelper.openListenerSettings(activity)
        }
        view.findViewById<Button>(R.id.btn_restart_listener).setOnClickListener {
            ListenerHelper.restartListener(activity)
            Toast.makeText(activity, R.string.listener_restarted, Toast.LENGTH_SHORT).show()
        }

        // --- privacy ---
        view.findViewById<Button>(R.id.btn_privacy).setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle(R.string.privacy_dialog_title)
                .setMessage(R.string.privacy_full)
                .setPositiveButton(R.string.btn_understand, null)
                .show()
        }

        // --- about developer / WhatsApp ---
        view.findViewById<Button>(R.id.btn_whatsapp).setOnClickListener {
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(Constants.WHATSAPP_CONTACT_URL)
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(activity, R.string.whatsapp_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        val view = view ?: return
        view.findViewById<TextView>(R.id.access_status_text).text =
            getString(
                if (ListenerHelper.isListenerEnabled(activity)) R.string.status_enabled
                else R.string.status_disabled
            )
    }
}
