package com.taqijafri.notisave.util

import android.content.Context
import android.util.Base64
import com.taqijafri.notisave.data.NotiDbHelper
import com.taqijafri.notisave.data.SavedNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object NotificationBackup {
    private const val MAGIC = "NOTISAVE-ENC-1"
    private const val ITERATIONS = 210_000
    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

    fun export(context: Context, uri: android.net.Uri, password: CharArray) {
        require(password.size >= 8) { "Use a password with at least 8 characters" }
        val data = JSONArray()
        NotiDbHelper.getInstance(context).getAllNotifications().forEach { item ->
            data.put(JSONObject().apply {
                put("package", item.packageName); put("app", item.appName)
                put("title", item.title); put("text", item.text); put("bigText", item.bigText)
                put("time", item.postTime); put("read", item.isRead)
            })
        }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        val document = listOf(MAGIC, Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .joinToString("\n")
        context.contentResolver.openOutputStream(uri, "w")!!.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(document) }
        }
    }

    fun import(context: Context, uri: android.net.Uri, password: CharArray): Int {
        require(password.size >= 8) { "Use a password with at least 8 characters" }
        val bytes = context.contentResolver.openInputStream(uri)!!.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BACKUP_BYTES) { "Backup file is too large" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        val document = String(bytes, Charsets.UTF_8).split('\n')
        require(document.size == 4 && document[0] == MAGIC) { "Unsupported backup file" }
        val salt = Base64.decode(document[1], Base64.DEFAULT)
        val iv = Base64.decode(document[2], Base64.DEFAULT)
        val encrypted = Base64.decode(document[3], Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val json = JSONArray(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        require(json.length() <= 100_000) { "Backup contains too many records" }
        val restored = ArrayList<SavedNotification>(json.length())
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            restored.add(SavedNotification(
                packageName = item.getString("package"), appName = item.getString("app"),
                title = item.getString("title"), text = item.getString("text"),
                bigText = item.optString("bigText"), postTime = item.getLong("time"),
                isRead = item.optBoolean("read")
            ))
        }
        return NotiDbHelper.getInstance(context).restoreNotifications(restored)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
