package com.example.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.security.PrivacyLockManager

@Composable
fun PrivacyLockScreen(
    expectedHash: String,
    salt: String,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && PrivacyLockManager.isBiometricAvailable(context)) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                PrivacyLockManager.promptBiometric(
                    activity = activity,
                    onSuccess = onUnlocked,
                    onError = { /* User can fall back to PIN */ }
                )
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "StudyFlow Locked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Text("StudyFlow Locked", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Enter your App PIN to access your academic workspace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 8) pinInput = it.filter(Char::isDigit) },
                    label = { Text("App PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_pin_input")
                )
                Button(
                    onClick = {
                        if (PrivacyLockManager.verifyPin(pinInput, salt, expectedHash)) {
                            onUnlocked()
                        } else {
                            errorMsg = "Incorrect PIN. Please try again."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("unlock_pin_button")
                ) {
                    Text("Unlock")
                }

                if (biometricEnabled && PrivacyLockManager.isBiometricAvailable(context)) {
                    OutlinedButton(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) {
                                PrivacyLockManager.promptBiometric(
                                    activity = activity,
                                    onSuccess = onUnlocked,
                                    onError = { err -> errorMsg = err }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use Biometrics")
                    }
                }
            }
        }
    }
}
