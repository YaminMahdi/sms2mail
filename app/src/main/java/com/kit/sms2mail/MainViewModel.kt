package com.kit.sms2mail

import android.app.Activity
import android.content.ContentResolver
import android.content.IntentSender
import android.credentials.GetCredentialException
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
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kit.sms2mail.model.Contact
import com.kit.sms2mail.model.Conversation
import com.kit.sms2mail.model.Msg
import com.kit.sms2mail.model.UserInfo
import com.kit.sms2mail.util.Constants
import com.kit.sms2mail.util.datastore.StoreKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlin.coroutines.resume


class MainViewModel : ViewModel() {

    private var _userInfo : UserInfo? = null

    private val _smsList = MutableStateFlow(listOf<Conversation>())
    val smsList = _smsList.asStateFlow()

    private val _contactList = MutableStateFlow(listOf<Contact>())
    val contactList = _contactList.asStateFlow()

    // Forward list - contains selected senders (from SMS) and contacts (from Contacts)
    private val _forwardList = MutableStateFlow(listOf<String>())
    val forwardList = _forwardList.asStateFlow()

    // Email list - contains email addresses to send to
    private val _emailList = MutableStateFlow(listOf<String>())
    val emailList = _emailList.asStateFlow()

    suspend fun googleSignIn(activity: Activity): IntentSender? =
        withContext(Dispatchers.IO) {
            val userInfo = getUser()
            if (userInfo.token != null) {
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
                    saveUser(user)
                    val requestedScopes = listOf(
                        Scope("https://www.googleapis.com/auth/gmail.send"),
                        Scope("https://www.googleapis.com/auth/userinfo.email"),
                        Scope("https://www.googleapis.com/auth/userinfo.profile")
                    )
                    val authorizationRequest = AuthorizationRequest.builder()
                        .setRequestedScopes(requestedScopes)
                        .build()

                    suspendCancellableCoroutine { continuation ->
                        Identity.getAuthorizationClient(activity)
                            .authorize(authorizationRequest)
                            .addOnSuccessListener { authorizationResult ->
                                if (authorizationResult.hasResolution()) {
                                    // This means we need to show a consent screen to the user
                                    val pendingIntent = authorizationResult.pendingIntent
                                    // Launch the intent using ActivityResultLauncher
                                    continuation.resume(pendingIntent?.intentSender)
                                } else {
                                    // Access already granted! You can use authorizationResult.accessToken
                                    val token = authorizationResult.accessToken
                                    val grantedScopes = authorizationResult.grantedScopes

                                    viewModelScope.launch {
                                        saveUser(
                                            userInfo = user.copy(
                                                token = token,
                                                grantedScopes = grantedScopes
                                            )
                                        )
                                    }

                                    Log.d("AUTH", "Access Token: $token")

                                    continuation.resume(null)
                                }
                            }
                    }

                } else
                    error("Only Google Account Allowed")
            } catch (e: GetCredentialCancellationException) {
                if (e.type == GetCredentialException.TYPE_USER_CANCELED) error("null")
                e.printStackTrace()
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    fun addToForwardList(senderList: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _forwardList.value.toMutableList()
            senderList.forEach { sender ->
                if (!current.contains(sender))
                    current.add(sender)
            }
            _forwardList.value = current
            saveUser(getUser().copy(
                forwardList = current
            ))
        }
    }

    fun removeFromForwardList(item: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _forwardList.value.toMutableList()
            current.remove(item)
            _forwardList.value = current
            saveUser(getUser().copy(
                forwardList = current
            ))
        }
    }

    fun addEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _emailList.value.toMutableList()
            if (!current.contains(email)) {
                current.add(email)
                _emailList.value = current
                saveUser(getUser().copy(
                    emailList = current
                ))
            }
        }
    }

    fun removeEmail(email: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _emailList.value.toMutableList()
            current.remove(email)
            _emailList.value = current
            saveUser(getUser().copy(
                emailList = current
            ))
        }
    }

    //Phone   //conversation of that phone number
    fun saveMsg(sms: Msg) {
        //working on Input Output thread for better performance
        viewModelScope.launch(Dispatchers.IO) {
            val tmpConv = _smsList.value.toMutableList()

            //adding same contract with country code and without country code in a single conversation
            var fixDuplicate = false
            tmpConv.map { it.sender }.forEachIndexed { ind, pn ->
                if ((pn.slice(4..<pn.length) == sms.phone) ||
                    (pn.slice(3..<pn.length) == sms.phone) ||
                    (pn.slice(2..<pn.length) == sms.phone)
                ) {
                    tmpConv[ind].messages.add(sms)
                    fixDuplicate = true
                }
            }

            //adding sms to old conversation if exist
            if (tmpConv.map { it.sender }.contains(sms.phone)) {
                val ind = tmpConv.map { it.sender }.indexOf(sms.phone)
                tmpConv[ind].messages.add(sms)
            }
            //creating new conversation
            else if (!fixDuplicate) {
                tmpConv.add(0, Conversation(sender = sms.phone, messages = mutableListOf(sms)))
            }
            //sorting is needed so that new sms comes in top
            tmpConv.sortByDescending { it.messages.last().time }

            _smsList.value = tmpConv
        }
    }

    fun readAllSMS(cr: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
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
        _userInfo = userInfo
        dataStore.save(StoreKeys.USER_INFO, userInfo)
    }

    suspend fun getUser(): UserInfo {
        return _userInfo ?: dataStore.read(StoreKeys.USER_INFO, UserInfo()).also {
            _userInfo = it
            _forwardList.value = it.forwardList
            _emailList.value = it.emailList
        }
    }

    fun logout() {
        viewModelScope.launch {
            dataStore.clear()
            _userInfo = null
            _forwardList.value = listOf()
            _emailList.value = listOf()
        }
    }
}