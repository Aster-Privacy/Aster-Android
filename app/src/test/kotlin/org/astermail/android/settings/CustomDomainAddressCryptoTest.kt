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

package org.astermail.android.settings

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.crypto.AesGcm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDomainAddressCryptoTest {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (it + 1).toByte() }
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    private fun directory_address(local_part: String = "merchant.finance") = CustomDomainAddressInfo(
        id = "directory-address",
        domain_name = "custom.example",
        encrypted_local_part = encoder.encodeToString(local_part.toByteArray(Charsets.UTF_8)),
        local_part_nonce = "",
        local_part_hash = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(local_part.toByteArray(Charsets.UTF_8))),
    )

    private fun decrypt(address: CustomDomainAddressInfo): String =
        decrypt_custom_domain_address_local_part(address) { ciphertext, iv ->
            require(iv.isNotBlank()) { "no nonce" }
            String(AesGcm.decrypt(key, decoder.decode(iv), decoder.decode(ciphertext)), Charsets.UTF_8)
        }

    @Test
    fun nonce_less_directory_address_preserves_dots_without_needing_a_key() {
        val result = runCatching {
            decrypt_custom_domain_address_local_part(directory_address()) { _, _ ->
                error("Encryption keys are unavailable")
            }
        }.getOrNull()
        assertEquals("merchant.finance", result)
    }

    @Test
    fun distinct_directory_generated_addresses_are_recovered_independently() {
        val local_parts = listOf("bank.finance", "card.finance", "budget.finance", "clothes.shopping")
        val recovered = local_parts.map { runCatching { decrypt(directory_address(it)) }.getOrNull() }
        assertEquals(local_parts, recovered)
    }

    @Test
    fun aes_gcm_addresses_keep_using_the_existing_decryptor() {
        val address = directory_address().copy(
            encrypted_local_part = encoder.encodeToString(AesGcm.encrypt(key, nonce, "regular".toByteArray())),
            local_part_nonce = encoder.encodeToString(nonce),
            local_part_hash = "unrelated-keyed-hash",
        )
        assertEquals("regular", decrypt(address))
    }

    @Test
    fun a_present_nonce_never_falls_back_to_base64_text_even_with_a_matching_hash() {
        val address = directory_address().copy(local_part_nonce = encoder.encodeToString(nonce))
        assertTrue(runCatching { decrypt(address) }.isFailure)
    }

    @Test
    fun aes_gcm_authentication_failures_are_not_reinterpreted_as_directory_records() {
        val ciphertext = AesGcm.encrypt(key, nonce, "merchant.finance".toByteArray())
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        val address = directory_address().copy(
            encrypted_local_part = encoder.encodeToString(ciphertext),
            local_part_nonce = encoder.encodeToString(nonce),
        )
        assertTrue(runCatching { decrypt(address) }.isFailure)
    }

    @Test
    fun whitespace_nonce_is_not_treated_as_the_empty_nonce_format() {
        assertTrue(runCatching { decrypt(directory_address().copy(local_part_nonce = " ")) }.isFailure)
    }

    @Test
    fun nonce_less_address_without_hash_is_rejected() {
        assertTrue(runCatching { decrypt(directory_address().copy(local_part_hash = "")) }.isFailure)
    }

    @Test
    fun nonce_less_address_with_mismatched_hash_is_rejected() {
        val address = directory_address().copy(local_part_hash = encoder.encodeToString(ByteArray(32)))
        assertTrue(runCatching { decrypt(address) }.isFailure)
    }

    @Test
    fun invalid_base64_hash_is_rejected() {
        assertTrue(runCatching { decrypt(directory_address().copy(local_part_hash = "not!base64")) }.isFailure)
    }

    @Test
    fun invalid_base64_local_part_is_rejected() {
        assertTrue(runCatching { decrypt(directory_address().copy(encrypted_local_part = "not!base64")) }.isFailure)
    }

    @Test
    fun hash_is_checked_against_the_exact_dotted_local_part_not_the_normalized_address() {
        val address = directory_address().copy(local_part_hash = directory_address("merchantfinance").local_part_hash)
        assertTrue(runCatching { decrypt(address) }.isFailure)
    }

    @Test
    fun matching_hash_does_not_allow_invalid_local_parts() {
        val invalid = listOf("", ".merchant", "merchant.", "merchant..finance", "merchant@custom.example",
            "merchant finance", "merchant\nfinance", "mérchant.finance", "a".repeat(65))
        invalid.forEach { local_part ->
            assertTrue("Invalid local part accepted", runCatching { decrypt(directory_address(local_part)) }.isFailure)
        }
    }
}
