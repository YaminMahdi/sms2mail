package com.kit.sms2mail.model

data class Msg(
    val phone: String,
    val sms: String,
    val time : Long,
    val type: Int
)