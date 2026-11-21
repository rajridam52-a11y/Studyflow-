package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppPreferences
import com.example.data.local.PreferencesManager
import com.example.reminders.NotificationChannels
import com.example.security.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.PrivacyLockScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.navigation.StudyFlowAppShell
import com.example.ui.theme.StudyFlowTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        NotificationChannels.ensureChannels(this)
        enableEdgeToEdge()

        val preferencesManager = PreferencesManager(applicationContext)

        setContent {
            val preferences by preferencesManager.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = AppPreferences())
            val currentUser by Firebase.auth.authStateFlow()
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            StudyFlowTheme(
                themeMode = preferences.themeMode,
                accentPalette = preferences.accentPalette
            ) {
                if (currentUser == null) {
                    AuthScreen(onAuthSuccess = {})
                } else {
                    val user = currentUser!!
                    key(user.uid) {
                        var unlocked by remember(user.uid, preferences.pinLockEnabled) {
                            mutableStateOf(!preferences.pinLockEnabled)
                        }
                        if (preferences.pinLockEnabled && !unlocked &&
                            preferences.pinHash.isNotBlank() && preferences.pinSalt.isNotBlank()
                        ) {
                            PrivacyLockScreen(
                                expectedHash = preferences.pinHash,
                                salt = preferences.pinSalt,
                                biometricEnabled = preferences.biometricEnabled,
                                onUnlocked = { unlocked = true }
                            )
                        } else {
                            StudyFlowAppShell(
                                user = user,
                                preferences = preferences,
                                preferencesManager = preferencesManager
                            )
                        }
                    }
                }
            }
        }
    }
}
