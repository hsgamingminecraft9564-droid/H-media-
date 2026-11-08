package com.hmedia.player

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

// Runs on an emulator or a phone (connectedDebugAndroidTest). Not yet run on a device.
class WelcomeGateTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun guestButtonStaysLockedUntilConsentIsGiven() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = AppPrefs(context)
        prefs.setConsent(false)
        rule.setContent {
            HMediaTheme(prefs.theme, prefs.font) { WelcomeScreen(prefs) {} }
        }
        rule.onNodeWithText("Continue as Guest").assertIsNotEnabled()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("Continue as Guest").assertIsEnabled()
    }
}
