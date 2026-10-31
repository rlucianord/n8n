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

        if (!blocked) IncomingCallNotifier.show(this, incomingNumber)
    }

}
