package com.kit.sms2mail

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

//using receiver like this with priority in manifest so that Toast work when app is closed
open class MsgReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        try {
            //converting intent data to sms List
            val smsList = Telephony.Sms.Intents.getMessagesFromIntent(intent).toList()
            smsList.forEach { sms ->
                Log.d("MsgReceiver", "onReceive: $sms")
                SendEmailWorker.enqueueOneTimeWork(
                    context = context,
                    originatingAddress = sms.originatingAddress,
                    messageBody = sms.messageBody
                )
            }
        }
        // Exception can occur while getting data from intent
        catch (e: Exception) {
            e.printStackTrace()
        }
    }
}