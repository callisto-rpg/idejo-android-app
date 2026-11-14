// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.idejo.app.core.model.AccessToken
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals and opens small secrets with one key. */
interface SecretBox {
    fun seal(plain: ByteArray): ByteArray

    /** Null when [sealed] was not sealed with this key (a restored or tampered copy). */
    fun open(sealed: ByteArray): ByteArray?
}

/**
 * AES-256-GCM with [key]: a random 12-byte IV, then the ciphertext and its tag. The key
 * never leaves its provider.
 */
open class AesGcmBox(
    private val key: () -> SecretKey,
) : SecretBox {
    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size <= IV_BYTES) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** The app's key in the Android Keystore (SI-04, SY-02): made once, never exported. */
class KeystoreBox(
    private val alias: String = "idejo.tokens",
) : AesGcmBox({ keystoreKey(alias) })

private fun keystoreKey(alias: String): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
        KeyGenParameterSpec
            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build(),
    )
    return generator.generateKey()
}

private const val KEY_BITS = 256

/** Tokens as they are stored: sealed, then Base64. Never logged (SI-04). */
class TokenVault(
    private val box: SecretBox,
) {
    fun seal(token: AccessToken): String =
        Base64.getEncoder().encodeToString(box.seal(token.value.toByteArray()))

    fun open(sealed: String?): AccessToken? {
        val bytes =
            try {
                Base64.getDecoder().decode(sealed ?: return null)
            } catch (_: IllegalArgumentException) {
                return null
            }
        return box.open(bytes)?.let { AccessToken(String(it)) }
    }
}
