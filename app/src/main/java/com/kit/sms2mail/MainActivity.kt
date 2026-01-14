package com.kit.sms2mail

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.Identity
import com.kit.sms2mail.ui.theme.Sms2MailTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    // Initialize the launcher
    val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // The user gave permission! Now extract the authorization result.
            val authResult = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(result.data)

            val token = authResult.accessToken
            val grantedScopes = authResult.grantedScopes
            lifecycleScope.launch {
                viewModel.saveUser(
                    viewModel.getUser().copy(
                        token = token,
                        grantedScopes = grantedScopes
                    )
                )
            }
            Log.d("AUTH", "Access Token: $token")

            // Proceed to use the token for API calls (Drive, Calendar, etc.)
        } else {
            // User canceled or there was an error
            Log.e("AUTH", "Authorization failed or cancelled by user")
        }
    }

    // Modern permission launcher for SMS permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readSmsGranted = permissions[android.Manifest.permission.READ_SMS] ?: false
        val readContractsGranted = permissions[android.Manifest.permission.READ_CONTACTS] ?: false

        if (readSmsGranted) {
            viewModel.readAllSMS(contentResolver)
        }
        if (readContractsGranted) {
            viewModel.readContacts(contentResolver)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val intentSender = viewModel.googleSignIn(this@MainActivity)
            intentSender?.let {
                authorizationLauncher.launch(
                    IntentSenderRequest.Builder(it).build()
                )
            }
        }
        // Request SMS permissions
        requestPermissions()
        setContent {
            Sms2MailTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
            viewModel.readAllSMS(contentResolver)
        else {
            permissions.add(android.Manifest.permission.RECEIVE_SMS)
            permissions.add(android.Manifest.permission.READ_SMS)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
            viewModel.readContacts(contentResolver)
        else
            permissions.add(android.Manifest.permission.READ_CONTACTS)

        if (permissions.isNotEmpty())
            permissionLauncher.launch(permissions.toTypedArray())
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Sms2MailTheme {
        Greeting("Android")
    }
}