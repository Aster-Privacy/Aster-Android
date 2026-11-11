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

package org.astermail.android.mail

import io.mockk.mockk
import org.astermail.android.crypto.PgpDecryptionResult
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpSignatureStatus
import org.astermail.android.crypto.SentEncryptionScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpBodyFieldsTest {

    private val passphrase = "correct horse battery staple".toCharArray()

    private val recipient by lazy {
        PgpKeyGenerator.generate("Recipient", "recipient@astermail.org", passphrase)
    }

    private val stranger by lazy {
        PgpKeyGenerator.generate("Stranger", "stranger@example.com", passphrase)
    }

    private val plaintext = "Hi,\n\nThis is a test message.\n\nDavid"

    private val armored_stub = "$PGP_ENCRYPTED_MESSAGE_HEADER\n\nwcBMA0\n=abcd\n-----END PGP MESSAGE-----"

    private val signed_stub = "-----BEGIN PGP SIGNED MESSAGE-----\nHash: SHA512\n\nHello\n" +
        "-----BEGIN PGP SIGNATURE-----\n\nwcBMA0\n=abcd\n-----END PGP SIGNATURE-----"

    private fun armor_for(public_key: String): String =
        PgpEncryptor.encrypt_to_keys(plaintext, listOf(public_key))!!

    private fun armor_in_html(armor: String): String =
        "<html><body>" + armor.replace("\r", "").replace("\n", "<br/>") + "</body></html>"

    private fun decrypt_as_recipient(armor: String): PgpDecryptionResult? = runCatching {
        PgpDecryptor.decrypt_with_status(armor, recipient.armored_private_key, passphrase, null)
    }.getOrNull()

    private fun fields(
        body_text: String,
        body_html: String? = null,
        pgp_encrypted: Boolean = false,
        pgp_signature: PgpSignatureStatus = PgpSignatureStatus.NONE,
    ) = PgpBodyFields(body_text, body_html, pgp_encrypted, pgp_signature)

    private fun decrypted(pgp_encrypted: Boolean, scope: SentEncryptionScope?) = ThreadMessageDecrypted(
        id = "m1",
        sender_name = "Me",
        sender_email = "me@astermail.org",
        to_label = "them",
        timestamp = "2026-11-08T10:00:00Z",
        body_text = "hello",
        body_html = null,
        is_encrypted = true,
        is_read = true,
        raw_item = mockk(relaxed = true),
        pgp_encrypted = pgp_encrypted,
        sent_encryption_scope = scope,
    )

    @Test
    fun armored_text_that_does_not_decrypt_is_not_marked_encrypted() {
        val resolved = resolve_pgp_body_fields(fields(armored_stub)) { null }

        assertFalse(resolved.pgp_encrypted)
        assertEquals(armored_stub, resolved.body_text)
        assertEquals(PgpSignatureStatus.NONE, resolved.pgp_signature)
    }

    @Test
    fun a_result_without_plaintext_is_not_marked_encrypted_or_signed() {
        val resolved = resolve_pgp_body_fields(fields(armored_stub)) {
            PgpDecryptionResult(null, PgpSignatureStatus.VALID)
        }

        assertFalse(resolved.pgp_encrypted)
        assertEquals(PgpSignatureStatus.NONE, resolved.pgp_signature)
    }

    @Test
    fun armored_html_that_does_not_decrypt_is_not_marked_encrypted() {
        val resolved = resolve_pgp_body_fields(fields("", armored_stub)) { null }

        assertFalse(resolved.pgp_encrypted)
        assertEquals(armored_stub, resolved.body_html)
    }

    @Test
    fun inline_html_armor_that_does_not_decrypt_is_not_marked_encrypted() {
        val html = armor_in_html(armor_for(stranger.armored_public_key))
        val resolved = resolve_pgp_body_fields(fields("", html), ::decrypt_as_recipient)

        assertFalse(resolved.pgp_encrypted)
        assertEquals(html, resolved.body_html)
    }

    @Test
    fun a_message_sealed_to_another_key_is_not_marked_encrypted() {
        val armor = armor_for(stranger.armored_public_key)
        val resolved = resolve_pgp_body_fields(fields(armor), ::decrypt_as_recipient)

        assertFalse(resolved.pgp_encrypted)
        assertEquals(armor, resolved.body_text)
    }

    @Test
    fun a_signed_only_body_is_not_marked_encrypted() {
        val resolved = resolve_pgp_body_fields(fields(signed_stub)) { null }

        assertFalse(resolved.pgp_encrypted)
        assertEquals(PgpSignatureStatus.NONE, resolved.pgp_signature)
    }

    @Test
    fun a_message_sealed_to_the_reader_decrypts_and_is_marked_encrypted() {
        val resolved = resolve_pgp_body_fields(
            fields(armor_for(recipient.armored_public_key)),
            ::decrypt_as_recipient,
        )

        assertTrue(resolved.pgp_encrypted)
        assertEquals(plaintext, resolved.body_text.replace("\r\n", "\n"))
        assertEquals(PgpSignatureStatus.NONE, resolved.pgp_signature)
    }

    @Test
    fun inline_html_armor_sealed_to_the_reader_is_marked_encrypted() {
        val html = armor_in_html(armor_for(recipient.armored_public_key))
        val resolved = resolve_pgp_body_fields(fields("", html), ::decrypt_as_recipient)

        assertTrue(resolved.pgp_encrypted)
        assertNull(resolved.body_html)
        assertEquals(plaintext, resolved.body_text.replace("\r\n", "\n"))
    }

    @Test
    fun armored_html_that_decrypts_is_marked_encrypted() {
        val resolved = resolve_pgp_body_fields(fields("", armored_stub)) {
            PgpDecryptionResult("<p>Hello</p>", PgpSignatureStatus.NONE)
        }

        assertTrue(resolved.pgp_encrypted)
        assertEquals("<p>Hello</p>", resolved.body_html)
    }

    @Test
    fun the_signature_state_comes_from_the_decryption_result() {
        val valid = resolve_pgp_body_fields(fields(armored_stub)) {
            PgpDecryptionResult("Hello", PgpSignatureStatus.VALID)
        }
        val unverified = resolve_pgp_body_fields(fields(armored_stub)) {
            PgpDecryptionResult("Hello", PgpSignatureStatus.UNVERIFIED)
        }

        assertEquals(PgpSignatureStatus.VALID, valid.pgp_signature)
        assertEquals(PgpSignatureStatus.UNVERIFIED, unverified.pgp_signature)
    }

    @Test
    fun a_body_without_armor_keeps_what_the_envelope_established() {
        var calls = 0
        val resolved = resolve_pgp_body_fields(
            fields("Hello", "<p>Hello</p>", pgp_encrypted = true, pgp_signature = PgpSignatureStatus.VALID),
        ) {
            calls++
            null
        }

        assertEquals(0, calls)
        assertTrue(resolved.pgp_encrypted)
        assertEquals(PgpSignatureStatus.VALID, resolved.pgp_signature)
        assertEquals("Hello", resolved.body_text)
        assertEquals("<p>Hello</p>", resolved.body_html)
    }

    @Test
    fun an_unreadable_message_placeholder_is_not_marked_encrypted() {
        val placeholder = pgp_placeholder_envelope_of("Encrypted message", "Unable to decrypt", true)

        assertFalse(placeholder.pgp_encrypted)
        assertTrue(placeholder.is_pgp_placeholder)
        assertEquals("Encrypted message", placeholder.subject)
        assertEquals("Unable to decrypt", placeholder.body_text)
        assertFalse(pgp_placeholder_envelope_of("s", "b", false).is_pgp_placeholder)
    }

    @Test
    fun a_snapshot_saved_under_the_earlier_rules_drops_the_pgp_flag_and_keeps_the_scope() {
        val earlier = thread_snapshot_of(decrypted(true, SentEncryptionScope.OUTSIDE)).copy(encryption_rules = 1)
        val restored = thread_message_of(earlier)

        assertFalse(restored.pgp_encrypted)
        assertEquals(SentEncryptionScope.OUTSIDE, restored.sent_encryption_scope)
    }

    @Test
    fun a_snapshot_saved_under_the_current_rules_keeps_the_pgp_flag() {
        val restored = thread_message_of(thread_snapshot_of(decrypted(true, null)))

        assertTrue(restored.pgp_encrypted)
    }
}
