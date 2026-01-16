package com.kit.sms2mail.data

import android.util.Base64
import android.util.Log
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.UserCredentials
import com.kit.sms2mail.BuildConfig
import com.kit.sms2mail.dataStore
import com.kit.sms2mail.model.UserInfo
import com.kit.sms2mail.util.Constants
import com.kit.sms2mail.util.datastore.StoreKeys
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Properties

class GmailService {
    private var cachedCredentials: UserCredentials? = null
    private var cachedUserEmail: String? = null

    private fun getOrCreateCredentials(userInfo: UserInfo): UserCredentials {
        if (cachedCredentials == null || cachedUserEmail != userInfo.email) {

            val builder = UserCredentials.newBuilder()
                .setClientId(Constants.WEB_CLIENT_ID)
                .setClientSecret(BuildConfig.WEB_CLIENT_SECRET)
                .setRefreshToken(userInfo.refreshToken)

            // If we have a valid stored token, use it (avoids initial API call)
            if (!userInfo.accessToken.isNullOrEmpty() && !userInfo.isTokenExpired()) {
                val accessToken = AccessToken.newBuilder()
                    .setTokenValue(userInfo.accessToken)
                    .setExpirationTime(Date(userInfo.expiresAt))
                    .build()
                builder.setAccessToken(accessToken)
            }

            cachedCredentials = builder.build()
            cachedUserEmail = userInfo.email
        }
        return cachedCredentials!!
    }

    /**
     * Send an email using Gmail API
     * @param originatingAddress Sender of the SMS
     * @param messageBody Body of the SMS
     * @return Message ID of the send email
     */
    suspend fun sendMail(originatingAddress: String?, messageBody: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val userInfo = dataStore.read(StoreKeys.USER_INFO, UserInfo())

                if (!BuildConfig.DEBUG && (originatingAddress.isNullOrEmpty() || originatingAddress !in userInfo.forwardFromList))
                    error("SMS from unknown sender")
                if (userInfo.refreshToken == null)
                    error("No refresh token available")

                val credentials = getOrCreateCredentials(userInfo)
                val service = Gmail.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    HttpCredentialsAdapter(credentials)
                ).setApplicationName("Sms2Mail").build()

                val email = createEmail(
                    to = userInfo.getRecipientAddresses()
                        .ifEmpty { error("No recipient addresses") },
                    from = userInfo.email,
                    subject = "Fwd: From `$originatingAddress`",
                    bodyText = "$messageBody\n___\n\nSent from Sms2Mail"
                )
                val message = createMessageWithEmail(email)
                val sentMessage = service.users().messages().send("me", message).execute()

                Log.d("GMAIL", "Message sent successfully. ID: ${sentMessage.id}")

                // Save updated token after successful call
                credentials.accessToken?.let { token ->
                    dataStore.save(
                        StoreKeys.USER_INFO,
                        userInfo.copy(
                            accessToken = token.tokenValue,
                            expiresAt = token.expirationTime?.time ?: 0L
                        )
                    )
                }

                sentMessage.id
            } catch (e: Exception) {
                Log.e("GMAIL", "Error sending email", e)
                null
            }
        }

    /**
     * Create a MimeMessage using the parameters provided
     */
    private fun createEmail(
        to: List<InternetAddress>,
        from: String,
        subject: String,
        bodyText: String
    ): MimeMessage {
        val props = Properties()
        val session = Session.getDefaultInstance(props, null)

        val email = MimeMessage(session)
        email.setFrom(InternetAddress(from))

        email.addRecipients(
            jakarta.mail.Message.RecipientType.TO,
            to.toTypedArray()
        )
        email.subject = subject
        email.setText(bodyText)
        return email
    }

    /**
     * Create a Message from a MimeMessage
     */
    private fun createMessageWithEmail(email: MimeMessage): Message {
        val buffer = ByteArrayOutputStream()
        email.writeTo(buffer)
        val bytes = buffer.toByteArray()
        val encodedEmail = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
        val message = Message()
        message.raw = encodedEmail
        return message
    }
}