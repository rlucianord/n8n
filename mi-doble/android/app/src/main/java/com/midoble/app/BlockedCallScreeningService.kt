package com.midoble.app

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils

/** Rejects only numbers explicitly saved by the user. All other calls pass through. */
class BlockedCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val blocked = incomingNumber.isNotBlank() && BlockedNumbersStore.read(this)
            .any { PhoneNumberUtils.compare(this, incomingNumber, it) }
        val response = if (blocked) {
            CallResponse.Builder().setDisallowCall(true).setRejectCall(true)
                .setSkipCallLog(false).setSkipNotification(false).build()
        } else {
            CallResponse.Builder().setDisallowCall(false).setRejectCall(false)
                .setSkipCallLog(false).setSkipNotification(false).build()
        }
        respondToCall(callDetails, response)
    }
}
