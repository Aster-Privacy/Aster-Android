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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryEmailKeyFallbackTest {

    private val address = "backup.person@example.org"
    private val current_identity_key = "current-identity-key-after-password-change"
    private val previous_identity_key = "identity-key-before-password-change"

    private fun seal(key: ByteArray, mime: Boolean = false, plaintext: String = address): Pair<String, String> {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = AesGcm.encrypt(key, nonce, plaintext.toByteArray(Charsets.UTF_8))
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

    @Test
    fun the_backfill_opens_an_address_sealed_under_an_identity_key() {
        val (ciphertext, nonce) = seal(identity_scoped_key(previous_identity_key))

        assertEquals(
            address,
            open_recovery_email_for_backfill(ciphertext, nonce, listOf(current_identity_key, previous_identity_key)),
        )
    }

    @Test
    fun the_backfill_never_tries_raw_account_data_keys() {
        val account_context_key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val (ciphertext, nonce) = seal(account_context_key.copyOf())

        assertNull(open_recovery_email_for_backfill(ciphertext, nonce, listOf(current_identity_key)))
    }

    @Test
    fun the_backfill_skips_a_plaintext_that_is_not_an_address() {
        for (plaintext in listOf("not an address", "a@b.co\nsecond line", "a@b.co,c@d.co", "{\"k\":1}")) {
            val (ciphertext, nonce) = seal(identity_scoped_key(current_identity_key), plaintext = plaintext)

            assertNull(open_recovery_email_for_backfill(ciphertext, nonce, listOf(current_identity_key)))
            assertEquals(plaintext, open_recovery_email(ciphertext, nonce, listOf(current_identity_key), emptyList()))
        }
    }

    @Test
    fun the_address_check_matches_the_web_rules() {
        assertTrue(is_valid_recovery_email("user@example.com"))
        assertTrue(is_valid_recovery_email("first.last+tag@sub.example.org"))
        assertTrue(is_valid_recovery_email("jürgen@müller.example"))
        assertTrue(is_valid_recovery_email("用户@例子.公司"))
        assertTrue(is_valid_recovery_email("x".repeat(64) + "@example.com"))
        assertFalse(is_valid_recovery_email("jürgen m@example.com"))
        assertFalse(is_valid_recovery_email("a@b.co;c@d.co"))
        assertFalse(is_valid_recovery_email("a@b@c.co"))
        assertFalse(is_valid_recovery_email("ü".repeat(60) + "@" + "ü".repeat(70) + ".example"))
        assertFalse(is_valid_recovery_email("user@example.com "))
        assertFalse(is_valid_recovery_email("user@example.com\n"))
        assertFalse(is_valid_recovery_email("a@b.co,c@d.co"))
        assertFalse(is_valid_recovery_email("no-at-sign"))
        assertFalse(is_valid_recovery_email(null))
        assertFalse(is_valid_recovery_email(""))
        assertFalse(is_valid_recovery_email("x".repeat(250) + "@example.com"))
        assertFalse(is_valid_recovery_email("\"first last\"@example.com"))
        assertFalse(is_valid_recovery_email("user@[192.0.2.1]"))
        assertFalse(is_valid_recovery_email("user@mail_host.example.com"))
        assertFalse(is_valid_recovery_email("x".repeat(65) + "@example.com"))
    }
}
