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
 * The BridgeAthletic login and session.
 *
 * The password and the JWT are encrypted with a key held in the Android Keystore;
 * the email and the JWT expiry are ordinary preferences. Clearing the credentials
 * clears the session too.
 */
object BridgeSecretStore {

    private const val PREFS = "hc_bridge"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "hc_sync_bridge_secret"
    private const val IV_BYTES = 12

    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password_ciphertext"
    private const val KEY_JWT = "jwt_ciphertext"
    private const val KEY_JWT_EXP = "jwt_exp_ms"

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
            .remove(KEY_JWT)
            .remove(KEY_JWT_EXP)
            .apply()
    }

    fun getJwt(ctx: Context): String? {
        if (System.currentTimeMillis() >= getJwtExpiryMs(ctx)) return null
        return decrypt(ctx, KEY_JWT)
    }

    fun getJwtExpiryMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_JWT_EXP, 0L)

    fun setSession(ctx: Context, jwt: String, expiresAtMs: Long) {
        encryptInto(ctx, KEY_JWT, jwt)
        prefs(ctx).edit().putLong(KEY_JWT_EXP, expiresAtMs).apply()
    }

    fun clearSession(ctx: Context) {
        prefs(ctx).edit().remove(KEY_JWT).remove(KEY_JWT_EXP).apply()
    }

    // ── Keystore plumbing (same scheme as AiKeyStore) ──────────────────────

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
