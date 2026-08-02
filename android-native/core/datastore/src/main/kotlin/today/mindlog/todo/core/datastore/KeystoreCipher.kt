package today.mindlog.todo.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256/GCM against a non-exportable Android Keystore key.
 *
 * Deliberately not `EncryptedSharedPreferences`: `androidx.security:security-
 * crypto` is deprecated and its last release was an alpha. Sixty lines here
 * buy a supported API, a `Flow`-shaped store consistent with the rest, and —
 * the real gain — an explicit answer when the key becomes unusable. A key is
 * invalidated by a restored backup or a lock-screen change; that surfaces as
 * [KeyPermanentlyUnusable] and ends at the sign-in screen, rather than as the
 * opaque crash EncryptedSharedPreferences produces.
 */
@Singleton
class KeystoreCipher @Inject constructor() {

    class KeyPermanentlyUnusable(cause: Throwable) : Exception(cause)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // Note: `setUnlockedDeviceRequired` is left off on purpose.
                    // It reads as free hardening, but it would stop the token
                    // being readable while the screen is locked — which is
                    // exactly when the background event stream needs to
                    // reconnect.
                    .build(),
            )
        }.generateKey()
    }

    /** Returns Base64 of `iv || ciphertext`. */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    @Throws(KeyPermanentlyUnusable::class)
    fun decrypt(blob: String): String {
        try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            }
            return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // Any failure here means the stored blob can never be read again:
            // wrong key, rotated key, or a blob restored from another device.
            throw KeyPermanentlyUnusable(e)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "mindlog_todo_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
