package com.kit.sms2mail.ui.navigation

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.kit.sms2mail.MainViewModel
import com.kit.sms2mail.ui.dialogs.AddEmailDialog
import com.kit.sms2mail.ui.dialogs.LogoutDialog
import com.kit.sms2mail.ui.screens.ContactSelectionScreen
import com.kit.sms2mail.ui.screens.HomeScreen
import com.kit.sms2mail.ui.screens.SmsSelectionScreen

@Composable
fun SetupNavDisplay(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {

    val backStack = rememberNavBackStack(Screens.Home)
    val activity = LocalActivity.current as Activity

    // Collect states from ViewModel
    val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()
    Log.d("userInfo", "SetupNavDisplay: $userInfo")

    val smsList by viewModel.smsList.collectAsStateWithLifecycle()
    val contactList by viewModel.contactList.collectAsStateWithLifecycle()

    val forwardFromList by rememberUpdatedState(userInfo.forwardFromList)
    val emailList by rememberUpdatedState(userInfo.emailList)

    // Selection states for multi-select screens
    var selectedSmsItems by remember { mutableStateOf(setOf<String>()) }
    var selectedContactItems by remember { mutableStateOf(setOf<String>()) }
    NavDisplay(
        backStack = backStack,
        sceneStrategy = DialogSceneStrategy(),
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it + 1000 }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it + 1000 }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it }) //+ fadeOut(animationSpec = tween(delayMillis = 100))
        },
        entryProvider = entryProvider {
            entry<Screens.Home> {
                HomeScreen(
                    userInfo = userInfo,
                    forwardFromList = forwardFromList,
                    emailList = emailList,
                    serviceStatus = userInfo.serviceStatus,
                    onRemoveForward = { viewModel.removeFromForwardList(it) },
                    onRemoveEmail = { viewModel.removeEmail(it) },
                    onAddFromSms = {
                        if (viewModel.readSmsGranted)
                            viewModel.readAllSMS(activity.contentResolver)
                        selectedSmsItems = setOf()
                        backStack.add(Screens.SmsSelection)
                    },
                    onAddFromContact = {
                        if (viewModel.readContractsGranted)
                            viewModel.readContacts(activity.contentResolver)
                        selectedContactItems = setOf()
                        backStack.add(Screens.ContactSelection)
                    },
                    onAddEmail = { backStack.add(Screens.Dialog.AddEmail) },
                    onServiceStatusChange = {
                        if (it && (userInfo.forwardFromList.isEmpty() || userInfo.emailList.isEmpty()))
                            Toast.makeText(
                                activity,
                                "Please add at least one contact and one email",
                                Toast.LENGTH_SHORT
                            ).show()
                        else
                            viewModel.updateServiceStatus(it)
                    },
                    onLogoutClick = { backStack.add(Screens.Dialog.Logout) },
                    modifier = modifier,
                )
            }

            entry<Screens.SmsSelection> {
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
                    onBack = backStack::removeLastOrNull
                )
            }

            entry<Screens.ContactSelection> {
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
                    onBack = backStack::removeLastOrNull
                )
            }
            entry<Screens.Dialog.AddEmail>(
                metadata = DialogSceneStrategy.dialog(DialogProperties())
            ) {
                AddEmailDialog(
                    onDismiss = backStack::removeLastOrNull,
                    onConfirm = { email ->
                        backStack.removeLastOrNull()
                        viewModel.addEmail(email)
                    }
                )
            }
            entry<Screens.Dialog.Logout>(
                metadata = DialogSceneStrategy.dialog(DialogProperties())
            ) {
                LogoutDialog(
                    onDismiss = backStack::removeLastOrNull,
                    onConfirm = {
                        backStack.removeLastOrNull()
                        viewModel.logout(activity)
                    }
                )
            }
        }
    )

}