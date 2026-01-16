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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.Identity
import com.kit.sms2mail.ui.navigation.SetupNavDisplay
import com.kit.sms2mail.ui.theme.Sms2MailTheme
import kotlinx.coroutines.MainScope
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
            MainScope().launch {
                viewModel.saveUserAuthInfo(authResult = authResult)
            }
            // Request SMS permissions
            requestPermissions()
        } else {
            // User canceled or there was an error
            Log.e("AUTH", "Authorization failed or cancelled by user")
        }
    }

    // Modern permission launcher for SMS permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.readSmsGranted = permissions[android.Manifest.permission.READ_SMS] ?: false
        viewModel.readContractsGranted =
            permissions[android.Manifest.permission.READ_CONTACTS] ?: false

        if (viewModel.readSmsGranted)
            viewModel.readAllSMS(contentResolver)
        if (viewModel.readContractsGranted)
            viewModel.readContacts(contentResolver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val intentSender = viewModel.googleSignIn(this@MainActivity)
            if (intentSender != null) {
                authorizationLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            } else {
                // Request SMS permissions
                requestPermissions()
            }
        }
        setContent {
            Sms2MailTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupNavDisplay(
                        viewModel = viewModel,
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
        ) {
            viewModel.readSmsGranted = true
            viewModel.readAllSMS(contentResolver)
        } else {
            permissions.add(android.Manifest.permission.RECEIVE_SMS)
            permissions.add(android.Manifest.permission.READ_SMS)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.readContractsGranted = true
            viewModel.readContacts(contentResolver)
        } else
            permissions.add(android.Manifest.permission.READ_CONTACTS)

        if (permissions.isNotEmpty())
            permissionLauncher.launch(permissions.toTypedArray())
    }
}