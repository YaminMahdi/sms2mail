package com.kit.sms2mail.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

data object Screens {
    @Serializable data object Home: NavKey
    @Serializable data object SmsSelection: NavKey
    @Serializable data object ContactSelection: NavKey
    @Serializable data object Dialog {
        @Serializable data object AddEmail: NavKey
        @Serializable data object Logout: NavKey
    }
}
