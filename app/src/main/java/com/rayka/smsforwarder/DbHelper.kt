package com.rayka.smsforwarder

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "rayka_messages.db"
        private const val DB_VERSION = 1
        const val TABLE = "messages"

        @Volatile private var instance: DbHelper? = null
        fun get(context: Context): DbHelper =
            instance ?: synchronized(this) {
                instance ?: DbHelper(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                sms_key TEXT UNIQUE,
                sender TEXT,
                body TEXT,
                sim INTEGER,
                received_at INTEGER,
                sent_at INTEGER,
                mode TEXT,
                synced_main INTEGER DEFAULT 0,
                synced_local INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    private val lock = Any()

    /** Insert a new message if smsKey not already present. Returns row id, or -1 if it already existed. */
    fun insertIfNew(sender: String, body: String, sim: Int, receivedAt: Long, smsKey: String): Long {
        synchronized(lock) {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put("sms_key", smsKey)
                put("sender", sender)
                put("body", body)
                put("sim", sim)
                put("received_at", receivedAt)
                put("mode", MessageRecord.MODE_QUEUED)
                put("synced_main", 0)
                put("synced_local", 0)
            }
            return db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun markSyncedMain(id: Long, sentAt: Long, mode: String) {
        synchronized(lock) {
            val cv = ContentValues().apply {
                put("synced_main", 1)
                put("sent_at", sentAt)
                put("mode", mode)
            }
            writableDatabase.update(TABLE, cv, "_id=?", arrayOf(id.toString()))
        }
    }

    fun markSyncedLocal(id: Long, mode: String) {
        synchronized(lock) {
            val cv = ContentValues().apply {
                put("synced_local", 1)
                put("mode", mode)
            }
            writableDatabase.update(TABLE, cv, "_id=?", arrayOf(id.toString()))
        }
    }

    fun getPendingMain(): List<MessageRecord> {
        synchronized(lock) {
            val list = mutableListOf<MessageRecord>()
            val c = readableDatabase.rawQuery(
                "SELECT * FROM $TABLE WHERE synced_main=0 ORDER BY received_at ASC LIMIT 300", null
            )
            c.use {
                while (it.moveToNext()) list.add(cursorToRecord(it))
            }
            return list
        }
    }

    fun getAll(limit: Int = 500): List<MessageRecord> {
        synchronized(lock) {
            val list = mutableListOf<MessageRecord>()
            val c = readableDatabase.rawQuery(
                "SELECT * FROM $TABLE ORDER BY received_at DESC LIMIT ?", arrayOf(limit.toString())
            )
            c.use {
                while (it.moveToNext()) list.add(cursorToRecord(it))
            }
            return list
        }
    }

    fun counts(): Triple<Int, Int, Int> {
        synchronized(lock) {
            fun q(sql: String): Int {
                readableDatabase.rawQuery(sql, null).use { c ->
                    return if (c.moveToFirst()) c.getInt(0) else 0
                }
            }
            val total = q("SELECT COUNT(*) FROM $TABLE")
            val synced = q("SELECT COUNT(*) FROM $TABLE WHERE synced_main=1")
            val queued = q("SELECT COUNT(*) FROM $TABLE WHERE synced_main=0")
            return Triple(total, synced, queued)
        }
    }

    private fun cursorToRecord(c: android.database.Cursor): MessageRecord {
        return MessageRecord(
            id = c.getLong(c.getColumnIndexOrThrow("_id")),
            smsKey = c.getString(c.getColumnIndexOrThrow("sms_key")),
            sender = c.getString(c.getColumnIndexOrThrow("sender")),
            body = c.getString(c.getColumnIndexOrThrow("body")),
            sim = c.getInt(c.getColumnIndexOrThrow("sim")),
            receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at")),
            sentAt = c.getLong(c.getColumnIndexOrThrow("sent_at")).let { if (it == 0L) null else it },
            mode = c.getString(c.getColumnIndexOrThrow("mode")) ?: MessageRecord.MODE_QUEUED,
            syncedMain = c.getInt(c.getColumnIndexOrThrow("synced_main")) == 1,
            syncedLocal = c.getInt(c.getColumnIndexOrThrow("synced_local")) == 1
        )
    }
}
