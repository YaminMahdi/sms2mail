package com.kit.sms2mail.data

import android.util.Log
import com.kit.sms2mail.model.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TokenInfoResponse(
    @SerialName("azp")
    val azp: String,
    @SerialName("aud")
    val aud: String,
    @SerialName("sub")
    val sub: String,
    @SerialName("scope")
    val scope: String,
    @SerialName("exp")
    val exp: String,
    @SerialName("expires_in")
    val expiresIn: String,
    @SerialName("email")
    val email: String,
    @SerialName("email_verified")
    val emailVerified: String,
    @SerialName("access_type")
    val accessType: String
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("scope")
    val scope: String,
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("id_token")
    val idToken: String? = null
)

class TokenValidatorService {

    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(Logging) {
                level = LogLevel.BODY
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("ktor", "log: $message")
                    }
                }
            }
        }
    }

    /**
     * Validates an access token and returns token information
     */
    suspend fun validateToken(userInfo: UserInfo): UserInfo {
        return withContext(Dispatchers.IO) {
            val response: TokenInfoResponse = client.get(
                "https://oauth2.googleapis.com/tokeninfo?access_token=${userInfo.accessToken}"
            ).body()
            val userInfo = userInfo.copy(
//                generatedAt = response.exp.toLongOrNull() ?: System.currentTimeMillis(),
//                expiresIn = response.expiresIn.toIntOrNull() ?: 3600,
                expiresAt = System.currentTimeMillis() + response.expiresIn.toLong() * 1000L
            )
            userInfo
        }
    }

    /**
     * Exchanges authorization code for tokens
     */
    suspend fun generateRefreshToken(
        userInfo: UserInfo,
        clientId: String,
        clientSecret: String,
        redirectUri: String = ""
    ): UserInfo {
        return withContext(Dispatchers.IO) {
            val response: TokenResponse = client.submitForm(
                url = "https://oauth2.googleapis.com/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", userInfo.authCode.toString())
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("redirect_uri", redirectUri)
                }
            ).body()
            val userInfo = userInfo.copy(
                accessToken = response.accessToken,
                expiresAt = System.currentTimeMillis() + response.expiresIn * 1000L,
                refreshToken = response.refreshToken,
            )
            userInfo
        }
    }

    /**
     * Refreshes an access token using a refresh token
     */
    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String
    ): Result<TokenResponse> {
        return try {
            withContext(Dispatchers.IO) {
                val response: TokenResponse = client.submitForm(
                    url = "https://oauth2.googleapis.com/token",
                    formParameters = parameters {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("client_id", clientId)
                        append("client_secret", clientSecret)
                    }
                ).body()
                Result.success(response)
            }
        } catch (e: Exception) {
            Log.e("TokenValidatorService", "Error refreshing access token", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a token is expired based on expires_in value
     */
    fun isTokenExpired(
        userInfo: UserInfo
    ): Boolean {
        return System.currentTimeMillis() >= userInfo.expiresAt
    }

    /**
     * Revokes an access token
     */
    suspend fun revokeToken(token: String): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                client.submitForm(
                    url = "https://oauth2.googleapis.com/revoke",
                    formParameters = parameters {
                        append("token", token)
                    }
                )
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("TokenValidatorService", "Error revoking token", e)
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}