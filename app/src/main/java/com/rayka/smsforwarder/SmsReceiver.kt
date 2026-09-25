package com.rayka.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        Prefs.init(context)

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "unknown"
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis
        val sim = intent.getIntExtra("subscription", intent.getIntExtra("slot", -1))
        val smsKey = "recv:$sender:$timestamp"

        val pending = goAsync()
        executor.execute {
            try {
                MessageSync.handleIncoming(context, sender, body, sim, timestamp, smsKey)
                // Nudge the service to flush anything else pending right away too.
                ForwarderService.kick(context)
            } finally {
                pending.finish()
            }
        }
    }
}
