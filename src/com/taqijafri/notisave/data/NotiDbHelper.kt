package com.taqijafri.notisave.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local SQLite storage for saved notifications.
 * Everything stays on the device — no server, no account, no upload.
 */
class NotiDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "notisave.db"
        private const val DB_VERSION = 1

        private const val T_NOTI = "notifications"
        private const val T_APPS = "app_settings"

        @Volatile
        private var instance: NotiDbHelper? = null

        fun getInstance(context: Context): NotiDbHelper {
            return instance ?: synchronized(this) {
                instance ?: NotiDbHelper(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $T_NOTI (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                app_name TEXT,
                title TEXT,
                text TEXT,
                big_text TEXT,
                post_time INTEGER NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_noti_time ON $T_NOTI(post_time DESC)")
        db.execSQL("CREATE INDEX idx_noti_pkg ON $T_NOTI(package_name)")
        db.execSQL(
            """CREATE TABLE $T_APPS (
                package_name TEXT PRIMARY KEY,
                enabled INTEGER NOT NULL DEFAULT 1
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the first release; nothing to migrate yet.
    }

    // ---------- insert / dedupe ----------

    fun insert(n: SavedNotification): Long {
        val values = ContentValues().apply {
            put("package_name", n.packageName)
            put("app_name", n.appName)
            put("title", n.title)
            put("text", n.text)
            put("big_text", n.bigText)
            put("post_time", n.postTime)
            put("is_read", if (n.isRead) 1 else 0)
        }
        return writableDatabase.insert(T_NOTI, null, values)
    }

    /** True when an identical notification was saved very recently (re-posts / duplicates). */
    fun isDuplicate(packageName: String, title: String, text: String, withinMs: Long): Boolean {
        val cutoff = System.currentTimeMillis() - withinMs
        val db = readableDatabase
        val c = db.rawQuery(
            "SELECT 1 FROM $T_NOTI WHERE package_name=? AND title=? AND text=? AND post_time>=? LIMIT 1",
            arrayOf(packageName, title, text, cutoff.toString())
        )
        val found = c.moveToFirst()
        c.close()
        return found
    }

    // ---------- queries ----------

    private fun rowToNotification(c: android.database.Cursor): SavedNotification {
        return SavedNotification(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            packageName = c.getString(c.getColumnIndexOrThrow("package_name")) ?: "",
            appName = c.getString(c.getColumnIndexOrThrow("app_name")) ?: "",
            title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
            text = c.getString(c.getColumnIndexOrThrow("text")) ?: "",
            bigText = c.getString(c.getColumnIndexOrThrow("big_text")) ?: "",
            postTime = c.getLong(c.getColumnIndexOrThrow("post_time")),
            isRead = c.getInt(c.getColumnIndexOrThrow("is_read")) == 1
        )
    }

    /**
     * Notifications newest-first, optionally filtered by package and/or search text.
     * Search matches app name, sender (title) and message content.
     */
    fun getNotifications(packageFilter: String?, query: String?, limit: Int = 500): List<SavedNotification> {
        val where = StringBuilder()
        val args = ArrayList<String>()
        if (!packageFilter.isNullOrBlank()) {
            where.append("package_name=?")
            args.add(packageFilter)
        }
        if (!query.isNullOrBlank()) {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("(app_name LIKE ? OR title LIKE ? OR text LIKE ? OR big_text LIKE ?)")
            val q = "%$query%"
            args.add(q); args.add(q); args.add(q); args.add(q)
        }
        val sql = "SELECT * FROM $T_NOTI" +
            (if (where.isNotEmpty()) " WHERE $where" else "") +
            " ORDER BY post_time DESC LIMIT $limit"
        val c = readableDatabase.rawQuery(sql, args.toTypedArray())
        val out = ArrayList<SavedNotification>()
        while (c.moveToNext()) out.add(rowToNotification(c))
        c.close()
        return out
    }

    fun getRecent(limit: Int = 5): List<SavedNotification> = getNotifications(null, null, limit)

    fun getById(id: Long): SavedNotification? {
        val c = readableDatabase.rawQuery(
            "SELECT * FROM $T_NOTI WHERE id=? LIMIT 1", arrayOf(id.toString())
        )
        val n = if (c.moveToFirst()) rowToNotification(c) else null
        c.close()
        return n
    }

    fun getTotalCount(): Int = countQuery("SELECT COUNT(*) FROM $T_NOTI")

    fun getCountSince(sinceMs: Long): Int =
        countQuery("SELECT COUNT(*) FROM $T_NOTI WHERE post_time>=?", arrayOf(sinceMs.toString()))

    fun getCountByPackage(packageName: String): Int =
        countQuery(
            "SELECT COUNT(*) FROM $T_NOTI WHERE package_name=?",
            arrayOf(packageName)
        )

    fun getUnreadCount(): Int =
        countQuery("SELECT COUNT(*) FROM $T_NOTI WHERE is_read=0")

    private fun countQuery(sql: String, args: Array<String> = emptyArray()): Int {
        val c = readableDatabase.rawQuery(sql, args)
        val n = if (c.moveToFirst()) c.getInt(0) else 0
        c.close()
        return n
    }

    fun getTopApps(limit: Int = 5): List<TopApp> {
        val c = readableDatabase.rawQuery(
            "SELECT package_name, app_name, COUNT(*) AS cnt FROM $T_NOTI " +
                "GROUP BY package_name ORDER BY cnt DESC LIMIT $limit",
            null
        )
        val out = ArrayList<TopApp>()
        while (c.moveToNext()) {
            out.add(
                TopApp(
                    packageName = c.getString(0) ?: "",
                    appName = c.getString(1) ?: "",
                    count = c.getInt(2)
                )
            )
        }
        c.close()
        return out
    }

    /** Distinct packages that have saved notifications, newest activity first. */
    fun getAppsWithNotifications(): List<Pair<String, String>> {
        val c = readableDatabase.rawQuery(
            "SELECT package_name, app_name FROM $T_NOTI GROUP BY package_name ORDER BY MAX(post_time) DESC",
            null
        )
        val out = ArrayList<Pair<String, String>>()
        while (c.moveToNext()) out.add(Pair(c.getString(0) ?: "", c.getString(1) ?: ""))
        c.close()
        return out
    }

    // ---------- mutations ----------

    fun delete(id: Long): Int =
        writableDatabase.delete(T_NOTI, "id=?", arrayOf(id.toString()))

    fun deleteAll(): Int = writableDatabase.delete(T_NOTI, null, null)

    /** Deletes notifications older than [cutoffMs]; returns rows removed. */
    fun deleteOlderThan(cutoffMs: Long): Int =
        writableDatabase.delete(T_NOTI, "post_time<?", arrayOf(cutoffMs.toString()))

    fun markRead(id: Long) {
        val v = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update(T_NOTI, v, "id=?", arrayOf(id.toString()))
    }

    fun markAllRead() {
        val v = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update(T_NOTI, v, null, null)
    }

    // ---------- per-app save toggles ----------

    fun isAppEnabled(packageName: String): Boolean {
        val c = readableDatabase.rawQuery(
            "SELECT enabled FROM $T_APPS WHERE package_name=? LIMIT 1",
            arrayOf(packageName)
        )
        // No saved choice yet: only WhatsApp saves by default; the user can
        // enable any other app from the Apps tab.
        val enabled = if (c.moveToFirst()) c.getInt(0) == 1
        else packageName in com.taqijafri.notisave.util.Constants.WHATSAPP_PACKAGES
        c.close()
        return enabled
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val v = ContentValues().apply {
            put("package_name", packageName)
            put("enabled", if (enabled) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(T_APPS, null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- storage ----------

    fun getDbSizeBytes(context: Context): Long {
        val f = context.getDatabasePath(DB_NAME)
        return if (f.exists()) f.length() else 0L
    }
}
