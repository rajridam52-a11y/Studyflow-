package com.example.security

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.security.SecureRandom

object PrivacyLockManager {
    private val random = SecureRandom()

    fun generateSaltBase64(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$saltBase64:$pin".toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(input)
        return Base64.encodeToString(hashed, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, saltBase64: String, expectedHash: String): Boolean {
        if (pin.isBlank() || saltBase64.isBlank() || expectedHash.isBlank()) return false
        val actual = hashPin(pin, saltBase64)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun promptBiometric(
        activity: FragmentActivity,
        title: String = "Unlock StudyFlow",
        subtitle: String = "Verify your identity to access your study workspace",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use App PIN")
            .build()
        prompt.authenticate(info)
    }
}
