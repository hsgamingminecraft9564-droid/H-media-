package com.hmedia.voidengine.features.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Hardware-backed (where available), non-exportable AES-256 key in the Android Keystore. */
object VaultKeys {
    private const val PROVIDER = "AndroidKeyStore"

    /**
     * @param authValiditySeconds 0 = no authentication needed. > 0 = the key only works for that many seconds
     * after the user unlocked with biometrics/device credential (run BiometricPrompt first). Only applied
     * when the key is first created.
     */
    @Suppress("DEPRECATION")
    fun getOrCreate(alias: String, authValiditySeconds: Int = 0): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER)
        keyStore.load(null)
        val existing = keyStore.getKey(alias, null)
        if (existing is SecretKey) return existing

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (authValiditySeconds > 0) {
            spec.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                spec.setUserAuthenticationParameters(
                    authValiditySeconds,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            } else {
                spec.setUserAuthenticationValidityDurationSeconds(authValiditySeconds)
            }
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(spec.build())
        return generator.generateKey()
    }
}
