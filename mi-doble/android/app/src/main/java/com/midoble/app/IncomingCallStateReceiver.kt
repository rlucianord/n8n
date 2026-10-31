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
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return

        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
        val blocked = number.isNotBlank() && BlockedNumbersStore.read(context)
            .any { PhoneNumberUtils.compare(context, number, it) }
        if (blocked) return

        showIncomingCallNotification(context, number)
    }

    private fun showIncomingCallNotification(context: Context, number: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "incoming_calls"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Llamadas entrantes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Avisos de llamadas para Mi Doble" }
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.putExtra("incoming_call_number", number)
        val pendingIntent = PendingIntent.getActivity(
            context,
            7101,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = number.takeIf { it.isNotBlank() } ?: "Número no disponible"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Mi Doble detectó una llamada")
            .setContentText(caller)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$caller está llamando. Toca aquí para abrir Mi Doble."
                )
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            manager.notify(7101, notification)
            context.getSharedPreferences("mi_doble_profile", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_incoming_call_wakeup", System.currentTimeMillis())
                .putString("last_incoming_call_number", number)
                .putString("last_incoming_call_source", "PHONE_STATE_RECEIVER")
                .apply()
        } catch (_: SecurityException) {
            // Android 13+ may deny notifications until the user grants permission.
        }
    }
}
