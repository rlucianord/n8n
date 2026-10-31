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

/** Screens calls, rejects only explicitly blocked numbers, and notifies the user about allowed calls. */
class BlockedCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val blocked = incomingNumber.isNotBlank() && BlockedNumbersStore.read(this)
            .any { PhoneNumberUtils.compare(this, incomingNumber, it) }

        if (!blocked) notifyIncomingCall(incomingNumber)

        val response = if (blocked) {
            CallResponse.Builder().setDisallowCall(true).setRejectCall(true)
                .setSkipCallLog(false).setSkipNotification(false).build()
        } else {
            CallResponse.Builder().setDisallowCall(false).setRejectCall(false)
                .setSkipCallLog(false).setSkipNotification(false).build()
        }
        respondToCall(callDetails, response)
    }

    private fun notifyIncomingCall(number: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "incoming_calls"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Llamadas entrantes", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Avisos de llamadas para Mi Doble" }
            )
        }
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incoming_call_number", number)
        } ?: return
        val pending = PendingIntent.getActivity(
            this, 7101, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val caller = number.takeIf { it.isNotBlank() } ?: "Número no disponible"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Llamada entrante")
            .setContentText(caller)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$caller está llamando. Abre Mi Doble para consultar las opciones disponibles."))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try { manager.notify(7101, notification) } catch (_: SecurityException) { }
    }
}
