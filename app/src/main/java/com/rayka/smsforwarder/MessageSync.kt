package com.rayka.smsforwarder

import android.content.Context
import android.provider.Telephony
import org.json.JSONObject

/**
 * Central place for all "what happens to one SMS" logic, shared by the live
 * receiver, the periodic background service, and the boot-time catch-up scan.
 * Every function here does blocking network / DB work -> always call from a
 * background thread, never from the main thread or a BroadcastReceiver directly.
 */
object MessageSync {

    private fun payloadFor(r: MessageRecord): JSONObject = JSONObject().apply {
        put("sender", r.sender)
        put("message", r.body)
        put("sim_slot", r.sim)
        put("received_at", r.receivedAt)
    }

    /** Insert (if new) and immediately try to deliver a single incoming SMS. */
    fun handleIncoming(context: Context, sender: String, body: String, sim: Int, receivedAt: Long, smsKey: String) {
        val db = DbHelper.get(context)
        val id = db.insertIfNew(sender, body, sim, receivedAt, smsKey)
        if (id == -1L) return // duplicate, already known
        val record = MessageRecord(id, smsKey, sender, body, sim, receivedAt, null, MessageRecord.MODE_QUEUED, false, false)
        attemptDeliver(context, record)
        if (receivedAt > Prefs.lastSmsTimestamp) Prefs.lastSmsTimestamp = receivedAt
    }

    /** Try main server, then local server, according to the two independent toggles. */
    fun attemptDeliver(context: Context, record: MessageRecord) {
        val db = DbHelper.get(context)
        val payload = payloadFor(record)
        val onlineOn = Prefs.onlineEnabled
        val offlineOn = Prefs.offlineEnabled

        var deliveredMain = false
        if (onlineOn && NetworkUtils.isOnline(context)) {
            deliveredMain = NetworkUtils.postJson(Prefs.mainUrl, payload)
            if (deliveredMain) {
                val mode = if (record.syncedLocal) "QUEUED_THEN_SENT" else MessageRecord.MODE_ONLINE
                db.markSyncedMain(record.id, System.currentTimeMillis(), mode)
                return
            }
        }

        if (!deliveredMain && offlineOn && !record.syncedLocal) {
            val deliveredLocal = NetworkUtils.postJson(Prefs.localUrl, payload)
            if (deliveredLocal) {
                db.markSyncedLocal(record.id, MessageRecord.MODE_OFFLINE)
            }
        }
    }

    /** Re-try every message not yet confirmed on the main server. Called on every service tick. */
    fun flushPending(context: Context) {
        val db = DbHelper.get(context)
        val pending = db.getPendingMain()
        for (record in pending) {
            attemptDeliver(context, record)
        }
    }

    /**
     * Compares the last message we ever processed against the phone's SMS inbox and
     * ingests anything newer — this is what recovers messages that arrived while the
     * phone/app was completely off. Safe to call every time the app or service starts.
     */
    fun catchUpMissedSms(context: Context) {
        val since = Prefs.lastSmsTimestamp
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.SUB_ID)
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC LIMIT 500"
            )?.use { c ->
                val idxId = c.getColumnIndex(Telephony.Sms._ID)
                val idxAddr = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val idxBody = c.getColumnIndex(Telephony.Sms.BODY)
                val idxDate = c.getColumnIndex(Telephony.Sms.DATE)
                val idxSub = c.getColumnIndex(Telephony.Sms.SUB_ID)
                while (c.moveToNext()) {
                    val smsId = if (idxId >= 0) c.getString(idxId) else c.position.toString()
                    val addr = if (idxAddr >= 0) c.getString(idxAddr) ?: "" else ""
                    val body = if (idxBody >= 0) c.getString(idxBody) ?: "" else ""
                    val date = if (idxDate >= 0) c.getLong(idxDate) else System.currentTimeMillis()
                    val sub = if (idxSub >= 0) c.getInt(idxSub) else -1
                    handleIncoming(context, addr, body, sub, date, "content:$smsId")
                }
            }
        } catch (_: SecurityException) {
            // READ_SMS permission not granted yet — nothing to catch up on.
        }
    }
}
