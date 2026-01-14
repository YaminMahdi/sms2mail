package com.kit.sms2mail.model

data class Conversation(
    val sender: String,
    val messages: MutableList<Msg>
)
