package com.taqijafri.notisave.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import com.taqijafri.notisave.R
import com.taqijafri.notisave.data.Prefs

object AppLockGate {
    fun requiresUnlock(context: Context): Boolean {
        val prefs = Prefs(context)
        if (!prefs.appLockEnabled) return false
        if (prefs.lastUnlockAt == 0L) return true
        val elapsed = SystemClock.elapsedRealtime() - prefs.lastUnlockAt
        return elapsed !in 0..30_000L
    }

    fun authenticate(activity: Activity, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < 29 ||
            !(activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure) {
            Toast.makeText(activity, R.string.app_lock_unavailable, Toast.LENGTH_LONG).show()
            activity.finish()
            return
        }
        android.hardware.biometrics.BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.app_lock_title))
            .setDescription(activity.getString(R.string.app_lock_message))
            .setDeviceCredentialAllowed(true)
            .build()
            .authenticate(activity.mainExecutor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        Prefs(activity).lastUnlockAt = SystemClock.elapsedRealtime()
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        activity.finish()
                    }
                })
    }
}
