package com.kit.sms2mail

import android.app.Activity
import android.content.IntentSender
import android.credentials.GetCredentialException
import android.util.Log
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
import com.kit.sms2mail.model.UserInfo
import com.kit.sms2mail.util.Constants
import com.kit.sms2mail.util.datastore.StoreKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume


class MainViewModel : ViewModel() {

    suspend fun googleSignIn(activity: Activity): IntentSender? =
        withContext(Dispatchers.IO) {
            val userInfo = getUser()
            if (userInfo.token != null){
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


    suspend fun saveUser(userInfo: UserInfo) {
        dataStore.save(StoreKeys.USER_INFO, userInfo)
    }

    suspend fun getUser(): UserInfo {
        return dataStore.read(StoreKeys.USER_INFO, UserInfo())
    }
}