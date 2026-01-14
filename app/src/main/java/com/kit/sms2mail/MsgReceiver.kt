package com.kit.sms2mail

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Base64
import android.util.Log
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.kit.sms2mail.model.UserInfo
import com.kit.sms2mail.util.datastore.StoreKeys
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Properties

//using receiver like this with priority in manifest so that Toast work when app is closed
open class MsgReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        try {
            //converting intent data to sms List
            val smsList = Telephony.Sms.Intents.getMessagesFromIntent(intent).toList()
            smsList.forEach { sms ->
                //showing Toast not Notification as you asked for
                Log.d("MsgReceiver", "onReceive: $sms")
                sendMail(
                    subject = "From ${sms.originatingAddress}",
                    bodyText = sms.messageBody
                )
            }
        }
        // Exception can occur while getting data from intent
        catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * Send an email using Gmail API
     * @param subject Email subject
     * @param bodyText Email body text
     * @return Message ID of the send email
     */
    fun sendMail(
        subject: String,
        bodyText: String
    ) = CoroutineScope(Dispatchers.IO).launch {
        try {
            val userInfo = dataStore.read(StoreKeys.USER_INFO, UserInfo())
            // Create Gmail service
            val accessToken = AccessToken.newBuilder()
                .setTokenValue(userInfo.token ?: error("Access token is null"))
                .setScopes(userInfo.grantedScopes)
                .build()
            val credentials = GoogleCredentials.create(accessToken ?: error("Access token is null"))
                .createScoped(GmailScopes.GMAIL_SEND)
            val requestInitializer = HttpCredentialsAdapter(credentials)

            val service = Gmail.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer
            ).setApplicationName("Sms2Mail")
                .build()

            // Create email message
            val email = createEmail(
                to = userInfo.getRecipientAddresses()
                    .ifEmpty { listOf(InternetAddress("abdullah@karoothitbd.com")) },  //error("No recipient addresses")
                from = userInfo.email,
                subject = subject,
                bodyText = bodyText
            )
            val message = createMessageWithEmail(email)

            // Send the email
            val sentMessage = service.users().messages().send("me", message).execute()

            Log.d("GMAIL", "Message sent successfully. ID: ${sentMessage.id}")
            sentMessage.id
        } catch (e: Exception) {
            Log.e("GMAIL", "Error sending email", e)
            throw e
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