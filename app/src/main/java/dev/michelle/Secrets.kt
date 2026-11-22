package dev.michelle

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Anthropic API key, kept encrypted in the app's private storage with a key that never leaves the
 * Android Keystore. Never logged, never in the code or in backups (allowBackup is off).
 */
class Secrets(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    var apiKey: String?
        get() = prefs.getString(API_KEY, null)?.let(::decrypt)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(API_KEY) else putString(API_KEY, encrypt(value.trim()))
            }.apply()
        }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        (store.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV))
        String(cipher.doFinal(sealed, IV, sealed.size - IV))
    }.getOrNull()

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "michelle-api-key"
        const val CIPHER = "AES/GCM/NoPadding"
        const val IV = 12
        const val API_KEY = "api_key"
    }
}
