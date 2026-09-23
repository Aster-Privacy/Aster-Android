//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.keys.parse_format_writes
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentCopySealTest {

    private val passphrase = "sent copy passphrase".toCharArray()

    private val own_key by lazy { PgpKeyGenerator.generate("Owner", "owner@astermail.org", passphrase) }
    private val other_key by lazy { PgpKeyGenerator.generate("Other", "other@astermail.org", passphrase) }

    private val envelope = """{"version":1,"subject":"Quarterly plan","body_html":"<p>Body text</p>"}"""

    private fun seal_own(): Pair<String, String> =
        SentCopySeal.seal(envelope, own_key.armored_private_key, passphrase)
            ?: throw AssertionError("seal failed")

    private fun armored(sealed: Pair<String, String>): String =
        String(Base64.getDecoder().decode(sealed.first), Charsets.UTF_8)

    @Test
    fun writes_an_armored_message_with_an_empty_nonce() {
        val sealed = seal_own()
        assertEquals("", sealed.second)
        assertTrue(armored(sealed).startsWith("-----BEGIN PGP MESSAGE-----"))
        assertFalse(armored(sealed).contains("Quarterly plan"))
    }

    @Test
    fun opens_with_the_own_key_and_a_valid_signature() {
        val opened = PgpDecryptor.decrypt_with_own_keys_status(
            armored(seal_own()),
            listOf(own_key.armored_private_key),
            passphrase,
        )
        assertEquals(envelope, opened?.plaintext)
        assertEquals(PgpSignatureStatus.VALID, opened?.signature)
    }

    @Test
    fun opens_after_the_key_moves_to_previous_keys() {
        val opened = PgpDecryptor.decrypt_with_own_keys_status(
            armored(seal_own()),
            listOf(other_key.armored_private_key, own_key.armored_private_key),
            passphrase,
        )
        assertEquals(envelope, opened?.plaintext)
    }

    @Test
    fun opens_after_the_key_is_relocked_with_a_new_password() {
        val sealed = seal_own()
        val new_password = "a brand new password".toCharArray()
        val relocked = relock(own_key.armored_private_key, passphrase, new_password)
        val opened = PgpDecryptor.decrypt_with_own_keys_status(armored(sealed), listOf(relocked), new_password)
        assertEquals(envelope, opened?.plaintext)
        assertEquals(PgpSignatureStatus.VALID, opened?.signature)
        assertNull(PgpDecryptor.decrypt_with_own_keys_status(armored(sealed), listOf(relocked), passphrase))
    }

    @Test
    fun flags_a_copy_signed_by_another_key() {
        val public_key = AccountKey.public_key_armored(own_key.armored_private_key)!!
        val forged = PgpEncryptor.encrypt_and_sign(
            envelope,
            listOf(public_key),
            other_key.armored_private_key,
            passphrase,
        )!!
        val opened = PgpDecryptor.decrypt_with_own_keys_status(forged, listOf(own_key.armored_private_key), passphrase)
        assertEquals(envelope, opened?.plaintext)
        assertTrue(opened?.signature != PgpSignatureStatus.VALID)
    }

    @Test
    fun returns_null_for_a_wrong_passphrase() {
        assertNull(SentCopySeal.seal(envelope, own_key.armored_private_key, "wrong".toCharArray()))
    }

    @Test
    fun returns_null_for_a_key_that_is_not_pgp() {
        assertNull(SentCopySeal.seal(envelope, """{"kty":"EC"}""", passphrase))
    }

    @Test
    fun capabilities_cache_the_answer() = runBlocking {
        var fetches = 0
        val capabilities = AccountKeyCapabilities({ fetches++; true }, { 1_000L })
        assertTrue(capabilities.format_writes())
        assertTrue(capabilities.format_writes())
        assertEquals(1, fetches)
    }

    @Test
    fun capabilities_refetch_after_the_cache_window() = runBlocking {
        var fetches = 0
        var now = 0L
        val capabilities = AccountKeyCapabilities({ fetches++; fetches > 1 }, { now })
        assertFalse(capabilities.format_writes())
        now = AccountKeyCapabilities.CACHE_MS + 1
        assertTrue(capabilities.format_writes())
        assertEquals(2, fetches)
    }

    @Test
    fun capabilities_treat_a_failure_as_off() = runBlocking {
        val capabilities = AccountKeyCapabilities({ throw IllegalStateException("offline") })
        assertFalse(capabilities.format_writes())
    }

    @Test
    fun parses_only_a_literal_true_flag() {
        assertTrue(parse_format_writes("""{"format_writes":true}"""))
        assertFalse(parse_format_writes("""{"format_writes":false}"""))
        assertFalse(parse_format_writes("""{"format_writes":"true"}"""))
        assertFalse(parse_format_writes("""{"format_writes":1}"""))
        assertFalse(parse_format_writes("""{}"""))
        assertFalse(parse_format_writes("[]"))
        assertFalse(parse_format_writes("not json"))
    }

    private fun relock(armored_private_key: String, old: CharArray, new: CharArray): String {
        val ring = PGPSecretKeyRing(
            PGPUtil.getDecoderStream(ByteArrayInputStream(armored_private_key.toByteArray(Charsets.UTF_8))),
            JcaKeyFingerprintCalculator(),
        )
        val digests = JcaPGPDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build()
        val relocked = PGPSecretKeyRing.copyWithNewPassword(
            ring,
            JcePBESecretKeyDecryptorBuilder(digests).setProvider(BouncyCastleProvider.PROVIDER_NAME).build(old),
            JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, digests.get(HashAlgorithmTags.SHA256))
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(new),
        )
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { relocked.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }
}
