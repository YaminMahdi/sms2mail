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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.android.gms.auth.api.identity.Identity
import com.kit.sms2mail.ui.navigation.ContactSelectionRoute
import com.kit.sms2mail.ui.navigation.HomeRoute
import com.kit.sms2mail.ui.navigation.SmsSelectionRoute
import com.kit.sms2mail.ui.screens.AddEmailDialog
import com.kit.sms2mail.ui.screens.ContactSelectionScreen
import com.kit.sms2mail.ui.screens.HomeScreen
import com.kit.sms2mail.ui.screens.SmsSelectionScreen
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
                Sms2MailApp(viewModel = viewModel)
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
fun Sms2MailApp(viewModel: MainViewModel) {
    val backStack = rememberNavBackStack(HomeRoute)

    // Collect states from ViewModel
    val smsList by viewModel.smsList.collectAsStateWithLifecycle()
    val contactList by viewModel.contactList.collectAsStateWithLifecycle()
    val forwardList by viewModel.forwardList.collectAsStateWithLifecycle()
    val emailList by viewModel.emailList.collectAsStateWithLifecycle()

    // State for dialogs
    var showEmailDialog by remember { mutableStateOf(false) }

    // Selection states for multi-select screens
    var selectedSmsItems by remember { mutableStateOf(setOf<String>()) }
    var selectedContactItems by remember { mutableStateOf(setOf<String>()) }


    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        forwardList = forwardList,
                        emailList = emailList,
                        serviceStatus = true,
                        onRemoveForward = { viewModel.removeFromForwardList(it) },
                        onRemoveEmail = { viewModel.removeEmail(it) },
                        onAddFromSms = {
                            selectedSmsItems = setOf()
                            backStack.add(SmsSelectionRoute)
                        },
                        onAddFromContact = {
                            selectedContactItems = setOf()
                            backStack.add(ContactSelectionRoute)
                        },
                        onAddEmail = { showEmailDialog = true },
                        onServiceStatusChange = {  },
                        onLogoutClick = viewModel::logout,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                entry<SmsSelectionRoute> {
                    SmsSelectionScreen(
                        smsList = smsList,
                        selectedSenders = selectedSmsItems,
                        onSenderToggle = { sender ->
                            selectedSmsItems = if (selectedSmsItems.contains(sender)) {
                                selectedSmsItems - sender
                            } else {
                                selectedSmsItems + sender
                            }
                        },
                        onConfirm = {
                            viewModel.addToForwardList(selectedSmsItems.toList())
                            backStack.removeLastOrNull()
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                entry<ContactSelectionRoute> {
                    ContactSelectionScreen(
                        contactList = contactList,
                        selectedContacts = selectedContactItems,
                        onContactToggle = { number ->
                            selectedContactItems = if (selectedContactItems.contains(number)) {
                                selectedContactItems - number
                            } else {
                                selectedContactItems + number
                            }
                        },
                        onConfirm = {
                            viewModel.addToForwardList(selectedContactItems.toList())
                            backStack.removeLastOrNull()
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        )
    }

    // Email Dialog
    if (showEmailDialog) {
        AddEmailDialog(
            onDismiss = { showEmailDialog = false },
            onConfirm = { email ->
                viewModel.addEmail(email)
                showEmailDialog = false
            }
        )
    }
}