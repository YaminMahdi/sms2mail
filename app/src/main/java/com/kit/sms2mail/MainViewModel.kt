package com.kit.sms2mail

import android.accounts.Account
import android.app.Activity
import android.content.ContentResolver
import android.content.IntentSender
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.gmail.GmailScopes
import com.kit.sms2mail.data.TokenValidatorService
import com.kit.sms2mail.model.Contact
import com.kit.sms2mail.model.Conversation
import com.kit.sms2mail.model.Msg
import com.kit.sms2mail.model.UserInfo
import com.kit.sms2mail.util.Constants
import com.kit.sms2mail.util.datastore.StoreKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Collections
import kotlin.coroutines.resume


class MainViewModel : ViewModel() {

    private val tokenValidatorService = TokenValidatorService()

    val userInfo = dataStore.readAsFlow(StoreKeys.USER_INFO, UserInfo())
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserInfo())

    private val _smsList = MutableStateFlow(listOf<Conversation>())
    val smsList = _smsList.asStateFlow()

    private val _contactList = MutableStateFlow(listOf<Contact>())
    val contactList = _contactList.asStateFlow()

    var readSmsGranted = false
    var readContractsGranted = false

    suspend fun googleSignIn(activity: Activity): IntentSender? =
        withContext(Dispatchers.IO) {
            if (dataStore.read(StoreKeys.USER_INFO, UserInfo()).accessToken != null) {
                return@withContext null
            }
            val signInGoogleOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(Constants.WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInGoogleOption)
                .build()
            try {
                val result = CredentialManager
                    .create(activity)
                    .getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential
                        .createFrom(credential.data)
                    val user = UserInfo(
                        email = googleCredential.id,
                        name = googleCredential.displayName,
                        phone = googleCredential.phoneNumber,
                        photoUrl = googleCredential.profilePictureUri?.toString()
                            ?.replace("=s96", "=s384")
                    )
//                    val requestedScopes = listOf(
//                        Scope("https://www.googleapis.com/auth/gmail.send"),
//                        Scope("https://www.googleapis.com/auth/userinfo.email"),
//                        Scope("https://www.googleapis.com/auth/userinfo.profile")
//                    )
                    val requestedScopes = listOf(
                        Scope(GmailScopes.GMAIL_SEND),
                        Scope("email"),
                        Scope("profile")
                    )
                    @Suppress("DEPRECATION")
                    val authorizationRequest = AuthorizationRequest.builder()
                        .requestOfflineAccess(Constants.WEB_CLIENT_ID, true)
                        .setRequestedScopes(requestedScopes)
                        .build()

                    val authorizationResult = suspendCancellableCoroutine { continuation ->
                        Identity.getAuthorizationClient(activity)
                            .authorize(authorizationRequest)
                            .addOnSuccessListener { authorizationResult ->
                                continuation.resume(authorizationResult)
                            }
                    }
                    if (authorizationResult.hasResolution()) {
                        // This means we need to show a consent screen to the user
                        saveUser(userInfo = user)
                        authorizationResult.pendingIntent?.intentSender
                        // Launch the intent using ActivityResultLauncher
                    } else {
                        // Access already granted! You can use authorizationResult.accessToken
                        saveUserAuthInfo(authResult = authorizationResult, userInfo = user)
                        null
                    }
                } else
                    error("Only Google Account Allowed")
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun saveUserAuthInfo(
        authResult: AuthorizationResult,
        userInfo: UserInfo = this@MainViewModel.userInfo.value
    ) {
        var user = userInfo.copy()
        val token = authResult.accessToken
        val authCode = authResult.serverAuthCode
        user = user.copy(
            authCode = authCode,
            accessToken = token
        )
//        user = tokenValidatorService.validateToken(userInfo = user)
        user = tokenValidatorService.generateRefreshToken(
            userInfo = user,
            clientId = Constants.WEB_CLIENT_ID,
            clientSecret = BuildConfig.WEB_CLIENT_SECRET,
        )
        saveUser(userInfo = user)
        Log.d("AUTH", "Access Token: $token")
    }

    fun addToForwardList(senderList: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = userInfo.value.forwardFromList.toMutableList()
            current.addAll(senderList)
            saveUser(
                userInfo.value.copy(
                    forwardFromList = current.distinct()
                )
            )
        }
    }

    fun removeFromForwardList(item: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = userInfo.value.forwardFromList.toMutableList()
            current.remove(item)
            saveUser(
                userInfo.value.copy(
                    forwardFromList = current
                )
            )
        }
    }

    fun addEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = userInfo.value.emailList.toMutableList()
            if (!current.contains(email)) {
                current.add(email)
                saveUser(
                    userInfo.value.copy(
                        emailList = current
                    )
                )
            }
        }
    }

    fun removeEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = userInfo.value.emailList.toMutableList()
            current.remove(email)
            saveUser(
                userInfo.value.copy(
                    emailList = current
                )
            )
        }
    }

    fun readAllSMS(cr: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            val totalMsgList = mutableListOf<Msg>()
            val conversations = mutableListOf<Conversation>()

            //collecting needed data of a sms
            val numberCol = Telephony.TextBasedSmsColumns.ADDRESS
            val textCol = Telephony.TextBasedSmsColumns.BODY
            val typeCol = Telephony.TextBasedSmsColumns.TYPE // 1 - Inbox, 2 - Sent
            val typeTime = Telephony.TextBasedSmsColumns.DATE

            val projection = arrayOf(numberCol, textCol, typeCol, typeTime)

            val cursor = cr.query(
                Telephony.Sms.CONTENT_URI,
                projection, null, null, Telephony.Sms.DATE
            )

            val numberColIdx = cursor!!.getColumnIndex(numberCol)
            val textColIdx = cursor.getColumnIndex(textCol)
            val typeColIdx = cursor.getColumnIndex(typeCol)
            val timeColIdx = cursor.getColumnIndex(typeTime)

            //collecting all sms in a single list
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberColIdx)
                val text = cursor.getString(textColIdx)
                val type = cursor.getString(typeColIdx).toInt()
                val time = cursor.getString(timeColIdx).toLong()

                val msg = Msg(phone = number, sms = text, time = time, type = type)
                totalMsgList.add(msg)

                Log.d("TAG", "$msg")
            }
            cursor.close()
            //building conversation
            //separate list for every phone number

            totalMsgList.forEach { msg ->

                //adding same contract with country code and without country code in a single conversation
                var fixDuplicate = false
                conversations.map { it.sender }.forEachIndexed { ind, pn ->
                    if ((pn.slice(4..<pn.length) == msg.phone) ||
                        (pn.slice(3..<pn.length) == msg.phone) ||
                        (pn.slice(2..<pn.length) == msg.phone)
                    ) {
                        conversations[ind].messages.add(msg)
                        fixDuplicate = true
                    }
                }

                if (conversations.map { it.sender }.contains(msg.phone)) {
                    val ind = conversations.map { it.sender }.indexOf(msg.phone)
                    conversations[ind].messages.add(msg)
                } else if (!fixDuplicate) {
                    conversations.add(Conversation(msg.phone, mutableListOf(msg)))
                }
            }
            //sorting is needed so that new sms comes in top
            conversations.sortByDescending { it.messages.last().time }

            _smsList.value = conversations
        }

    }

    fun readContacts(contentResolver: ContentResolver?) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(700)
            val contacts = getContractList(contentResolver)
            _contactList.value = contacts
        }
    }

    private suspend fun getContractList(contentResolver: ContentResolver?): List<Contact> {
        return withContext(Dispatchers.Default) {
            val projection = arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.PHOTO_URI,
                ContactsContract.Data.MIMETYPE
            )
            val order = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            val selection =
                ContactsContract.Data.MIMETYPE + " = ?" + " OR " +
                        ContactsContract.Data.MIMETYPE + " = ?" + " OR " +
                        ContactsContract.Data.MIMETYPE + " = ?"
            val selectionArgs = arrayOf(
                "%" + "@" + "%",
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            )
            val userContacts: MutableList<Contact> = mutableListOf()
            try {
                val cur = contentResolver?.query(
                    ContactsContract.Data.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    order
                )
                cur?.use {
                    while (cur.moveToNext()) {
                        val hasPhone =
                            cur.getIntOrNull(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                                ?: 0
                        val contactId =
                            cur.getIntOrNull(cur.getColumnIndex(ContactsContract.Data.CONTACT_ID))
                        val name =
                            cur.getStringOrNull(cur.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY))
                        val emailOrMobile =
                            cur.getStringOrNull(cur.getColumnIndex(ContactsContract.Data.DATA1))

                        val photo =
                            cur.getStringOrNull(cur.getColumnIndex(ContactsContract.Data.PHOTO_URI))
                        val displayPhotoUri = photo?.toUri()
                        val fis = displayPhotoUri?.let { contentResolver.openInputStream(it) }

                        val contactDetail = Contact(
                            contactId = contactId,
                            name = name,
                            drawable = fis.use { Drawable.createFromStream(fis, "src") }
                        )

                        if (hasPhone > 0 && emailOrMobile != null && !emailOrMobile.contains("@") && !emailOrMobile.contains(
                                "."
                            )
                        ) {
                            Collections.singletonList(emailOrMobile).forEach {
                                val number = it
                                    .removePrefix("+88")
                                    .replace("(", "")
                                    .replace(")", "")
                                    .replace("-", "")
                                    .replace(" ", "")

                                userContacts.add(contactDetail.copy(number = number))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Log.d("MainViewModel", "getContractList: $userContacts")
            userContacts.distinctBy { it.number }
        }
    }


    suspend fun saveUser(userInfo: UserInfo) {
        dataStore.save(StoreKeys.USER_INFO, userInfo)
    }

    fun logout(activity: Activity) {
        viewModelScope.launch {
            val account = Account(userInfo.value.email, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)
            val revokeAccessRequest = RevokeAccessRequest.builder()
                .setAccount(account)
                .setScopes(listOf(Scope(GmailScopes.GMAIL_SEND)))
                .build()

            Identity.getAuthorizationClient(activity)
                .revokeAccess(revokeAccessRequest)

            val token = userInfo.value.accessToken
            token?.let {
                Identity.getAuthorizationClient(activity)
                    .clearToken(ClearTokenRequest.builder().setToken(token).build())
            }
            dataStore.clear()
            activity.finish()
        }
    }

    fun updateServiceStatus(serviceStatus: Boolean) {
        viewModelScope.launch {
            saveUser(userInfo.value.copy(serviceStatus = serviceStatus))
        }
    }

    companion object {
        const val TAG = "MainViewModel"
    }
}