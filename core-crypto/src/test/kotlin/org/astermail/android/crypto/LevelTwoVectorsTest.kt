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

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelTwoVectorsTest {

    private fun b64(value: String): ByteArray = Base64.getDecoder().decode(value)

    private val data_kek = b64("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
    private val legacy_kek = b64("ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=")
    private val envelope_plaintext = "{\"subject\":\"Vector test\",\"n\":7}".toByteArray(Charsets.UTF_8)
    private val ciphertext_current_kek =
        b64("oKGio6SlpqeoqaqrrK2ur8DBwsPExcbHyMnKyxmUrphDVc4W9hq0u8Q/kCWAkTj6oNg/L8FnTtfoYrei0cSkxxjeICkSF8UMMji/")
    private val ciphertext_legacy_kek_only =
        b64("oKGio6SlpqeoqaqrrK2ur8DBwsPExcbHyMnKywLFIOOJnavHPVGWAESVDuvrt2eV3125NPGXHdSgj9M13jpUGS2PQGNB/A8W43oA")

    private val vault_password = "correct horse battery staple".toByteArray(Charsets.UTF_8)
    private val vault_nonce = b64("MDEyMzQ1Njc4OTo7")
    private val vault_plaintext =
        "{\"identity_key\":\"test\",\"data_kek\":\"AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=\"}"
            .toByteArray(Charsets.UTF_8)
    private val vault_600k_no_aad =
        b64("EBESExQVFhcYGRobHB0eHzSa66nA+rHMINpdiOTpkjSCLhwwf3wsJL83mK7tmNaM7vJPzgxj+NUyvRlcWDdrwRNULF7InqurSUTICamu5KacrQS2IjGLyOnx9LZ0MC2EoKMAMr5335JJHdmKF1VwBXA=")
    private val vault_600k_aad =
        b64("EBESExQVFhcYGRobHB0eHzSa66nA+rHMINpdiOTpkjSCLhwwf3wsJL83mK7tmNaM7vJPzgxj+NUyvRlcWDdrwRNULF7InqurSUTICamu5KacrQS2IjGLyOnx9LZ0MC2EoHqqTrmesmoHLQ6kNpv6xHU=")
    private val vault_310k_no_aad =
        b64("EBESExQVFhcYGRobHB0eH50xxkcJB5SCtgtwRihOGlJpA8baPelSa7kLsGCC/XD/WniEAFD+wtkjtM9nC5jHqjiir62dXVYDU2og79rnDzdr682La1IW1eHEi6OMTTtMkITkVo8ts1yICXjRFG6sx9o=")

    @Test
    fun envelope_marker_is_two() {
        assertEquals("Ag==", Base64.getEncoder().encodeToString(EnvelopeV2.marker()))
        assertTrue(EnvelopeV2.is_marker(b64("Ag==")))
        assertFalse(EnvelopeV2.is_marker(b64("AQ==")))
    }

    @Test
    fun current_kek_vector_opens_with_data_kek() {
        assertArrayEquals(envelope_plaintext, EnvelopeV2.open_with(ciphertext_current_kek, data_kek))
        assertArrayEquals(envelope_plaintext, EnvelopeV2.open(ciphertext_current_kek, listOf(data_kek, legacy_kek)))
    }

    @Test
    fun legacy_vector_needs_the_legacy_kek() {
        assertNull(EnvelopeV2.open_with(ciphertext_legacy_kek_only, data_kek))
        assertArrayEquals(envelope_plaintext, EnvelopeV2.open_with(ciphertext_legacy_kek_only, legacy_kek))
        assertArrayEquals(envelope_plaintext, EnvelopeV2.open(ciphertext_legacy_kek_only, listOf(data_kek, legacy_kek)))
    }

    @Test
    fun seal_round_trips_at_the_same_size_as_level_one() {
        val sealed = EnvelopeV2.seal(envelope_plaintext, data_kek)
        assertEquals(16 + 12 + envelope_plaintext.size + 16, sealed.size)
        assertArrayEquals(envelope_plaintext, EnvelopeV2.open(sealed, listOf(legacy_kek, data_kek)))
        assertNull(EnvelopeV2.open(sealed, listOf(legacy_kek)))
    }

    @Test
    fun vault_600k_vector_decrypts_at_600k() {
        val plaintext = CryptoNative.decrypt_vault_with_password(vault_600k_no_aad, vault_nonce, vault_password, 600000)
        assertArrayEquals(vault_plaintext, plaintext)
    }

    @Test
    fun vault_600k_vector_fails_at_the_default_count() {
        assertThrows(Exception::class.java) {
            CryptoNative.decrypt_vault_with_password(vault_600k_no_aad, vault_nonce, vault_password)
        }
    }

    @Test
    fun vault_600k_aad_vector_opens_through_the_vault_reader() {
        assertArrayEquals(
            vault_plaintext,
            CryptoNative.decrypt_vault_with_password(vault_600k_aad, vault_nonce, vault_password, 600000),
        )
    }

    @Test
    fun vault_600k_aad_vector_refuses_a_different_aad() {
        val salt = vault_600k_aad.copyOfRange(0, 16)
        val key = PasswordKdf.derive_aes_key(vault_password, salt, 600000)
        assertThrows(Exception::class.java) {
            AesGcm.decrypt(
                key,
                vault_nonce,
                vault_600k_aad.copyOfRange(16, vault_600k_aad.size),
                "aster-vault-v2".toByteArray(Charsets.UTF_8),
            )
        }
        assertThrows(Exception::class.java) {
            AesGcm.decrypt(key, vault_nonce, vault_600k_aad.copyOfRange(16, vault_600k_aad.size))
        }
    }

    @Test
    fun vault_600k_aad_vector_needs_its_aad() {
        val salt = vault_600k_aad.copyOfRange(0, 16)
        val key = PasswordKdf.derive_aes_key(vault_password, salt, 600000)
        val plaintext = AesGcm.decrypt(
            key,
            vault_nonce,
            vault_600k_aad.copyOfRange(16, vault_600k_aad.size),
            "aster-vault-v1".toByteArray(Charsets.UTF_8),
        )
        assertArrayEquals(vault_plaintext, plaintext)
    }

    @Test
    fun vault_310k_vector_decrypts_with_the_default_count() {
        assertArrayEquals(
            vault_plaintext,
            CryptoNative.decrypt_vault_with_password(vault_310k_no_aad, vault_nonce, vault_password),
        )
        assertArrayEquals(
            vault_plaintext,
            CryptoNative.decrypt_vault_with_password(vault_310k_no_aad, vault_nonce, vault_password, 310000),
        )
    }

    @Test
    fun vault_round_trips_at_600k() {
        val sealed = CryptoNative.encrypt_vault_with_password(vault_plaintext, vault_password, 600000)
        assertArrayEquals(
            vault_plaintext,
            CryptoNative.decrypt_vault_with_password(sealed.encrypted_vault, sealed.vault_nonce, vault_password, 600000),
        )
    }

    @Test
    fun vault_write_is_bound_only_when_asked() {
        val salt_bytes = 16
        val plain_form = CryptoNative.encrypt_vault_with_password(vault_plaintext, vault_password, 310000)
        val bound_form = CryptoNative.encrypt_vault_with_password(vault_plaintext, vault_password, 310000, true)
        fun open(envelope: CryptoNative.VaultEnvelope, aad: ByteArray?): ByteArray {
            val salt = envelope.encrypted_vault.copyOfRange(0, salt_bytes)
            val body = envelope.encrypted_vault.copyOfRange(salt_bytes, envelope.encrypted_vault.size)
            val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(
                    javax.crypto.spec.PBEKeySpec(
                        String(vault_password, Charsets.UTF_8).toCharArray(),
                        salt,
                        310000,
                        256,
                    ),
                ).encoded
            return AesGcm.decrypt(key, envelope.vault_nonce, body, aad)
        }
        assertArrayEquals(vault_plaintext, open(plain_form, null))
        assertThrows(Exception::class.java) { open(plain_form, "aster-vault-v1".toByteArray(Charsets.UTF_8)) }
        assertArrayEquals(vault_plaintext, open(bound_form, "aster-vault-v1".toByteArray(Charsets.UTF_8)))
        assertThrows(Exception::class.java) { open(bound_form, null) }
        assertArrayEquals(
            vault_plaintext,
            CryptoNative.decrypt_vault_with_password(
                bound_form.encrypted_vault,
                bound_form.vault_nonce,
                vault_password,
                310000,
            ),
        )
    }

    @Test
    fun work_factor_accepts_only_the_two_counts() {
        assertEquals(310000, VaultKdf.from_server(null))
        assertEquals(310000, VaultKdf.from_server(310000))
        assertEquals(600000, VaultKdf.from_server(600000))
        assertThrows(IllegalArgumentException::class.java) { VaultKdf.from_server(100000) }
        assertThrows(IllegalArgumentException::class.java) {
            CryptoNative.encrypt_vault_with_password(vault_plaintext, vault_password, 400000)
        }
        assertNull(VaultKdf.to_server(310000))
        assertEquals(600000, VaultKdf.to_server(600000))
    }
}
