package com.kit.sms2mail

import android.app.Application
import com.kit.sms2mail.util.datastore.DataStoreManager

class Sms2MailApp : Application() {
    val dataStore by lazy { DataStoreManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Sms2MailApp
    }
}

val dataStore
    get() = Sms2MailApp.instance.dataStore