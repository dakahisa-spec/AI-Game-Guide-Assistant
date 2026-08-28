package com.aigameguide.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("secure_ai_settings", Context.MODE_PRIVATE)
    private val alias = "ai_game_guide_provider_keys"
    private val legacyAlias = "ai_game_guide_api_key"

    fun hasKey(providerId: String = "openai"): Boolean = prefs.contains(ciphertextKey(providerId)) ||
        (providerId == "openai" && prefs.contains("ciphertext"))

    fun save(providerId: String, apiKey: String) {
        if (apiKey.isBlank()) {
            clear(providerId)
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(ivKey(providerId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(ciphertextKey(providerId), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun save(apiKey: String) = save("openai", apiKey)

    fun load(providerId: String = "openai"): String? = runCatching {
        val legacy = providerId == "openai" && !prefs.contains(ivKey(providerId))
        val iv = Base64.decode(prefs.getString(if (legacy) "iv" else ivKey(providerId), null), Base64.NO_WRAP)
        val encrypted = Base64.decode(prefs.getString(if (legacy) "ciphertext" else ciphertextKey(providerId), null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(if (legacy) legacyAlias else alias), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    fun masked(providerId: String): String? = load(providerId)?.let { key ->
        val suffix = key.takeLast(3)
        "${key.take(3)}••••••••••$suffix"
    }

    fun clear(providerId: String) = prefs.edit()
        .remove(ivKey(providerId)).remove(ciphertextKey(providerId)).apply()

    fun clear() = clear("openai")

    private fun ivKey(providerId: String) = "${providerId}_iv"
    private fun ciphertextKey(providerId: String) = "${providerId}_ciphertext"

    private fun getOrCreateKey(aliasName: String = alias): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(aliasName, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(aliasName, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }
}
