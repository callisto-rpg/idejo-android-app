// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import org.idejo.app.core.model.AccessToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** SI-04: tokens are kept sealed with a key the app cannot export. */
class TokenVaultTest {
    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val vault = TokenVault(AesGcmBox { key })
    private val token = AccessToken("idj_secret-token")

    @Test
    fun `a sealed token opens with its key and does not show the token`() {
        val sealed = vault.seal(token)
        assertFalse(sealed.contains("secret"))
        assertEquals(token, vault.open(sealed))
    }

    @Test
    fun `the same token seals differently each time (a fresh IV)`() {
        assertFalse(vault.seal(token) == vault.seal(token))
    }

    @Test
    fun `another key, a tampered copy or garbage opens to nothing`() {
        val sealed = vault.seal(token)
        assertNull(TokenVault(AesGcmBox(::newKey)).open(sealed))

        val bytes = Base64.getDecoder().decode(sealed)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(vault.open(Base64.getEncoder().encodeToString(bytes)))

        assertNull(vault.open("not base64 !"))
        assertNull(vault.open("AAAA"))
        assertNull(vault.open(null))
    }
}
