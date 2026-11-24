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
 * The Fitr login and access token.
 *
 * The password and the token are encrypted with a key held in the Android
 * Keystore; the email is an ordinary preference. Clearing the credentials
 * clears the session too.
 */
object FitrSecretStore {

    private const val PREFS = "hc_fitr"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "hc_sync_fitr_secret"
    private const val IV_BYTES = 12

    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password_ciphertext"
    private const val KEY_AUTH = "auth_ciphertext"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasCredentials(ctx: Context): Boolean =
        !getEmail(ctx).isNullOrBlank() && !getPassword(ctx).isNullOrBlank()

    fun getEmail(ctx: Context): String? =
        prefs(ctx).getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun getPassword(ctx: Context): String? = decrypt(ctx, KEY_PASSWORD)

    fun setCredentials(ctx: Context, email: String, password: String) {
        prefs(ctx).edit().putString(KEY_EMAIL, email.trim()).apply()
        encryptInto(ctx, KEY_PASSWORD, password)
    }

    fun clearCredentials(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .remove(KEY_AUTH)
            .apply()
    }

    fun getAuth(ctx: Context): String? = decrypt(ctx, KEY_AUTH)

    fun setAuth(ctx: Context, auth: String) = encryptInto(ctx, KEY_AUTH, auth)

    fun clearSession(ctx: Context) {
        prefs(ctx).edit().remove(KEY_AUTH).apply()
    }

    // ── Keystore plumbing (same scheme as BridgeSecretStore) ───────────────

    private fun encryptInto(ctx: Context, name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        prefs(ctx).edit()
            .putString(name, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    private fun decrypt(ctx: Context, name: String): String? {
        val encoded = prefs(ctx).getString(name, null) ?: return null
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
            prefs(ctx).edit().remove(name).apply()
            null
        }
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
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
