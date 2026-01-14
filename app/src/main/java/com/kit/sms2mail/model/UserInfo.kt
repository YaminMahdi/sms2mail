package com.kit.sms2mail.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import jakarta.mail.internet.InternetAddress

/**
 * Represents user information.
 * @property email The user's email address.
 * @property name The user's name.
 * @property phone The user's phone number.
 * @property photoUrl The URL of the user's profile photo.
 */
@Serializable
@Parcelize
data class UserInfo(
    @SerialName("email") val email: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,

    @SerialName("token") val token: String? = null,
    @SerialName("grantedScopes") val grantedScopes: List<String> = listOf(),
    @SerialName("fromContracts") val fromContracts: List<String> = listOf(),
    @SerialName("toMails") val toMails: List<String> = listOf(),
) : Parcelable {
    fun getRecipientAddresses() = toMails.map { InternetAddress(it) }
}