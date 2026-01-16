package com.kit.sms2mail.model

import android.os.Parcelable
import jakarta.mail.internet.InternetAddress
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    @SerialName("serviceStatus") val serviceStatus: Boolean = false,
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("expiresAt") val expiresAt: Long = 0L,
    @SerialName("refreshToken") val refreshToken: String? = null,
    @SerialName("authCode") val authCode: String? = null,
    @SerialName("forwardFromList") val forwardFromList: List<String> = listOf(),
    @SerialName("emailList") val emailList: List<String> = listOf(),
) : Parcelable {
    fun getRecipientAddresses() = emailList.map { InternetAddress(it) }

    fun isTokenExpired() = System.currentTimeMillis() >= expiresAt
}