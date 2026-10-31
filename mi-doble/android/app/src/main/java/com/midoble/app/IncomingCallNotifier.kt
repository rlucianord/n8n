package com.midoble.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Single incoming-call notification path for the screening, phone-state and Telecom services.
 * The full-screen intent lets Android present the call decision UI instead of trying to
 * start an Activity directly from a background service (which Android may block).
 */
object IncomingCallNotifier {
    private const val CHANNEL_ID = "incoming_calls"
    private const val NOTIFICATION_ID = 7101

    fun show(context: Context, number: String?) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Llamadas entrantes",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Avisos y opciones para llamadas entrantes de Mi Doble"
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                )
            }

            val caller = number?.takeIf { it.isNotBlank() } ?: "Número no disponible"
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            launchIntent.putExtra("incoming_call_number", caller)
            launchIntent.putExtra("managed_by_incall_service", true)

            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Mi Doble: llamada entrante")
                .setContentText(caller)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "$caller está llamando. Selecciona si la atiendes tú o si autorizas a Mi Doble."
                    )
                )
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
            context.getSharedPreferences("mi_doble_profile", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_incoming_call_wakeup", System.currentTimeMillis())
                .putString("last_incoming_call_number", caller)
                .putString("last_incoming_call_source", context.javaClass.simpleName)
                .apply()
        } catch (_: SecurityException) {
            // Notification permission or full-screen-intent access may be restricted by Android.
        } catch (_: Exception) {
            // A notification issue must never interfere with the cellular call itself.
        }
    }
}
