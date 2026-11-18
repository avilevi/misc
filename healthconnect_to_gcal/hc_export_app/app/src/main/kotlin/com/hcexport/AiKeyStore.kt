package com.hcexport

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
 * Stores one encrypted API key per provider. Every key is encrypted with a key
 * held in the Android Keystore; the plaintext is never written to disk or to an
 * instance-state bundle. The legacy single-key field is migrated to the Claude
 * provider on first read.
 */
object AiKeyStore {

    private const val PREFS = "hc_ai"
    private const val LEGACY_KEY = "api_key_ciphertext"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "hc_sync_ai_key"
    private const val IV_BYTES = 12

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun entryName(providerKey: String): String = "api_key_$providerKey"

    fun hasKey(ctx: Context, providerKey: String): Boolean =
        getApiKey(ctx, providerKey) != null

    fun getApiKey(ctx: Context, providerKey: String): String? {
        val prefs = prefs(ctx)
        val name = entryName(providerKey)
        val encoded = prefs.getString(name, null)
            ?: if (providerKey == AiProviders.CLAUDE) {
                // Migrate the pre-provider key to the Claude slot.
                prefs.getString(LEGACY_KEY, null)?.also { legacy ->
                    prefs.edit().putString(name, legacy).remove(LEGACY_KEY).apply()
                }
            } else {
                null
            }
            ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size <= IV_BYTES) return null
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val ciphertext = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(128, iv),
                )
            }
            String(cipher.doFinal(ciphertext), Charsets.UTF_8).ifBlank { null }
        } catch (_: Exception) {
            // A key that cannot be decrypted is useless; make the user re-enter it.
            clear(ctx, providerKey)
            null
        }
    }

    fun setApiKey(ctx: Context, providerKey: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clear(ctx, providerKey)
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        prefs(ctx).edit()
            .putString(entryName(providerKey), Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    fun clear(ctx: Context, providerKey: String) {
        prefs(ctx).edit().apply {
            remove(entryName(providerKey))
            if (providerKey == AiProviders.CLAUDE) remove(LEGACY_KEY)
        }.apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
