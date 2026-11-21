package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.local.*
import com.example.ui.auth.PrivacyLockScreen
import com.example.ui.components.EmptyStudyState
import com.example.ui.theme.StudyFlowTheme
import com.example.security.PrivacyLockManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudyFlowUiSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `privacy lock screen renders pin input and unlock button`() {
        val salt = PrivacyLockManager.generateSaltBase64()
        val hash = PrivacyLockManager.hashPin("1234", salt)
        composeRule.setContent {
            StudyFlowTheme {
                PrivacyLockScreen(
                    expectedHash = hash,
                    salt = salt,
                    biometricEnabled = false,
                    onUnlocked = {}
                )
            }
        }
        composeRule.onNodeWithTag("privacy_pin_input").assertIsDisplayed()
        composeRule.onNodeWithTag("unlock_pin_button").assertIsDisplayed()
    }

    @Test
    fun `empty study state renders title and action`() {
        composeRule.setContent {
            StudyFlowTheme {
                EmptyStudyState(
                    icon = Icons.Filled.School,
                    title = "No Homework Due",
                    subtitle = "Add your next assignment to get started.",
                    actionLabel = "Add Homework",
                    onAction = {}
                )
            }
        }
        composeRule.onNodeWithText("No Homework Due").assertIsDisplayed()
        composeRule.onNodeWithText("Add Homework").assertIsDisplayed()
    }
}
