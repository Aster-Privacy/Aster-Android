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

package org.astermail.android.recovery

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.crypto.AesGcm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryEmailKeyFallbackTest {

    private val address = "backup.person@example.org"
    private val current_identity_key = "current-identity-key-after-password-change"
    private val previous_identity_key = "identity-key-before-password-change"

    private fun seal(key: ByteArray, mime: Boolean = false): Pair<String, String> {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = AesGcm.encrypt(key, nonce, address.toByteArray(Charsets.UTF_8))
        val encoder = if (mime) Base64.getMimeEncoder(8, "\n".toByteArray()) else Base64.getEncoder()
        return encoder.encodeToString(ciphertext) to encoder.encodeToString(nonce)
    }

    private fun identity_scoped_key(identity_key: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest((identity_key + "astermail-recovery-email-v1").toByteArray(Charsets.UTF_8))

    @Test
    fun an_address_sealed_under_a_previous_identity_key_is_unreadable_with_the_current_key_alone() {
        val (ciphertext, nonce) = seal(identity_scoped_key(previous_identity_key))

        assertNull(open_recovery_email(ciphertext, nonce, listOf(current_identity_key), emptyList()))
    }

    @Test
    fun an_address_sealed_under_a_previous_identity_key_opens_with_the_previous_keys() {
        val (ciphertext, nonce) = seal(identity_scoped_key(previous_identity_key))

        val opened = open_recovery_email(
            ciphertext,
            nonce,
            listOf(current_identity_key, previous_identity_key),
            emptyList(),
        )

        assertEquals(address, opened)
    }

    @Test
    fun an_address_sealed_under_an_account_data_key_opens_with_the_decrypt_keks() {
        val account_context_key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val (ciphertext, nonce) = seal(account_context_key.copyOf())
        val keks = listOf(
            Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
            Base64.getEncoder().encodeToString(account_context_key),
        )

        assertEquals(address, open_recovery_email(ciphertext, nonce, listOf(current_identity_key), keks))
    }

    @Test
    fun an_address_sealed_under_the_current_identity_key_still_opens() {
        val (ciphertext, nonce) = seal(identity_scoped_key(current_identity_key))

        assertEquals(address, open_recovery_email(ciphertext, nonce, listOf(current_identity_key), emptyList()))
    }

    @Test
    fun the_current_key_matches_the_shared_derivation() {
        val (ciphertext, nonce) = seal(derive_recovery_email_key(current_identity_key))

        assertEquals(address, open_recovery_email(ciphertext, nonce, listOf(current_identity_key), emptyList()))
    }

    @Test
    fun wrapped_base64_from_the_server_still_opens() {
        val (ciphertext, nonce) = seal(identity_scoped_key(previous_identity_key), mime = true)

        assertEquals(
            address,
            open_recovery_email(ciphertext, nonce, listOf(current_identity_key, previous_identity_key), emptyList()),
        )
    }

    @Test
    fun no_matching_key_returns_null_instead_of_throwing() {
        val (ciphertext, nonce) = seal(identity_scoped_key("some-other-account"))
        val keks = listOf(Base64.getEncoder().encodeToString(ByteArray(16)), "not base64 at all")

        assertNull(
            open_recovery_email(ciphertext, nonce, listOf("", current_identity_key, previous_identity_key), keks),
        )
    }

    @Test
    fun malformed_ciphertext_returns_null() {
        assertNull(open_recovery_email("%%%", "%%%", listOf(current_identity_key), emptyList()))
    }
}
