package com.midoble.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import androidx.core.app.NotificationCompat

/** Screens calls, rejects only explicitly blocked numbers, and wakes the user for allowed calls. */
class BlockedCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val blocked = try {
            incomingNumber.isNotBlank() && BlockedNumbersStore.read(this)
                .any { PhoneNumberUtils.compare(this, incomingNumber, it) }
        } catch (_: Exception) {
            // A storage hiccup must never leave the cellular call unanswered by the screening service.
            false
        }

        val response = if (blocked) {
            CallResponse.Builder().setDisallowCall(true).setRejectCall(true)
                .setSkipCallLog(false).setSkipNotification(false).build()
        } else {
            CallResponse.Builder().setDisallowCall(false).setRejectCall(false)
                .setSkipCallLog(false).setSkipNotification(false).build()
        }

        // Answer Android's screening callback immediately; notification work follows.
        try {
            respondToCall(callDetails, response)
        } catch (_: Exception) {
            return
        }

        if (!blocked) notifyIncomingCall(incomingNumber)
    }

    private fun notifyIncomingCall(number: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "incoming_calls"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Llamadas entrantes", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "Avisos de llamadas para Mi Doble" }
                )
            }
            val openApp = packageManager.getLaunchIntentForPackage(packageName) ?: return
            openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            openApp.putExtra("incoming_call_number", number)
            val pending = PendingIntent.getActivity(
                this, 7101, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val caller = number.takeIf { it.isNotBlank() } ?: "Número no disponible"
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Mi Doble detectó una llamada")
                .setContentText(caller)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$caller está llamando. Toca aquí para abrir Mi Doble."))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending)
                .build()
            manager.notify(7101, notification)
            getSharedPreferences("mi_doble_profile", Context.MODE_PRIVATE).edit()
                .putLong("last_incoming_call_wakeup", System.currentTimeMillis())
                .putString("last_incoming_call_number", number)
                .putString("last_incoming_call_source", "CALL_SCREENING_SERVICE")
                .apply()
        } catch (_: SecurityException) {
            // Notification permission may be unavailable; call screening has already completed.
        } catch (_: Exception) {
            // Notification failures must not affect the phone call.
        }
    }
}
