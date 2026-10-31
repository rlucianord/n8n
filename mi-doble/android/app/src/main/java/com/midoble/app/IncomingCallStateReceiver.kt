package com.midoble.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

/**
 * Backup wake-up path for incoming cellular calls.
 *
 * CallScreeningService remains the primary path. This receiver makes Android
 * wake Mi Doble from PHONE_STATE_CHANGED as a fallback, then posts a user-tappable
 * notification instead of attempting a restricted background activity launch.
 */
class IncomingCallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (phoneState == TelephonyManager.EXTRA_STATE_IDLE) {
            IncomingCallNotifier.dismiss(context)
            return
        }
        if (phoneState != TelephonyManager.EXTRA_STATE_RINGING) return

        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
        val blocked = number.isNotBlank() && BlockedNumbersStore.read(context)
            .any { PhoneNumberUtils.compare(context, number, it) }
        if (blocked) return

        IncomingCallNotifier.show(context, number)
    }

}
