package com.kit.sms2mail.model

import android.graphics.drawable.Drawable

data class Contact(
    var contactId: Int? = null,
    var name: String?= null,
    val number: String?= null,
    var drawable: Drawable?= null,
    var isUser: Boolean = false,
)