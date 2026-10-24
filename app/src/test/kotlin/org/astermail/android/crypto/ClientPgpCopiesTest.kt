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

import org.astermail.android.api.keys.ExternalKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientPgpCopiesTest {

    private val passphrase = "correct horse battery staple"

    private fun key_info(
        email: String,
        found: Boolean = true,
        public_key: String? = "-----BEGIN PGP PUBLIC KEY BLOCK-----",
        will_encrypt: Boolean = true,
    ) = ExternalKeyInfo(email = email, found = found, public_key = public_key, will_encrypt = will_encrypt)

    @Test
    fun encrypts_only_when_a_usable_key_will_be_used() {
        assertTrue(ClientPgpCopies.encrypts_for(key_info("a@example.com")))
        assertFalse(ClientPgpCopies.encrypts_for(null))
        assertFalse(ClientPgpCopies.encrypts_for(key_info("a@example.com", found = false)))
        assertFalse(ClientPgpCopies.encrypts_for(key_info("a@example.com", public_key = null)))
        assertFalse(ClientPgpCopies.encrypts_for(key_info("a@example.com", will_encrypt = false)))
    }

    @Test
    fun outside_recipients_are_deduplicated_and_internal_ones_dropped() {
        val recipients = ClientPgpCopies.outside_recipients(
            listOf(" Bob@example.com", "bob@example.com", "", "alice@astermail.org"),
        ) { it.endsWith("@astermail.org") }

        assertEquals(listOf("Bob@example.com"), recipients)
    }

    @Test
    fun recipient_keys_require_every_recipient_to_encrypt() {
        val keys = ClientPgpCopies.recipient_keys(
            listOf("bob@example.com", "carol@example.com"),
            listOf(key_info("Bob@example.com"), key_info("carol@example.com")),
        )
        assertEquals(listOf("bob@example.com", "carol@example.com"), keys?.keys?.toList())

        assertNull(
            ClientPgpCopies.recipient_keys(
                listOf("bob@example.com", "carol@example.com"),
                listOf(key_info("bob@example.com"), key_info("carol@example.com", will_encrypt = false)),
            ),
        )
        assertNull(
            ClientPgpCopies.recipient_keys(
                listOf("bob@example.com"),
                emptyList(),
            ),
        )
    }

    @Test
    fun signed_entity_wraps_the_mime_and_signature() {
        val mime = "Content-Type: text/plain\r\n\r\nhello".toByteArray()
        val entity = String(
            ClientPgpCopies.build_signed_mime_entity(mime, "-----BEGIN PGP SIGNATURE-----\nabc\n-----END PGP SIGNATURE-----", "pgp-sha256"),
            Charsets.UTF_8,
        )

        assertTrue(entity.startsWith("Content-Type: multipart/signed; micalg=\"pgp-sha256\"; protocol=\"application/pgp-signature\"; boundary=\"--=_astermail_signed_"))
        assertTrue(entity.contains("\"\r\n\r\n--"))
        assertTrue(entity.contains("\r\nContent-Type: text/plain\r\n\r\nhello\r\n--"))
        assertTrue(entity.contains("Content-Type: application/pgp-signature; name=\"signature.asc\"\r\n"))
        assertTrue(entity.contains("-----BEGIN PGP SIGNATURE-----\r\nabc\r\n-----END PGP SIGNATURE-----\r\n--"))
        assertTrue(entity.endsWith("--\r\n"))
        assertFalse(entity.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun copy_limit_grows_with_attachments_up_to_the_cap() {
        assertEquals(10L * 1024 * 1024, ClientPgpCopies.copy_limit_bytes(0))
        assertEquals(12L * 1024 * 1024, ClientPgpCopies.copy_limit_bytes(1024 * 1024))
        assertEquals(48L * 1024 * 1024, ClientPgpCopies.copy_limit_bytes(40L * 1024 * 1024))
    }

    @Test
    fun copies_are_armored_messages_each_recipient_can_decrypt() {
        val bob = PgpKeyGenerator.generate("Bob", "bob@example.com", passphrase.toCharArray())
        val carol = PgpKeyGenerator.generate("Carol", "carol@example.com", passphrase.toCharArray())
        val entity = "Content-Type: text/plain\r\n\r\nsecret body".toByteArray()

        val copies = ClientPgpCopies.encrypt_copies(
            entity,
            linkedMapOf("bob@example.com" to bob.armored_public_key, "carol@example.com" to carol.armored_public_key),
            0,
        )

        assertNotNull(copies)
        assertEquals(setOf("bob@example.com", "carol@example.com"), copies!!.keys)
        copies.values.forEach { assertTrue(ClientPgpCopies.is_armored_message(it)) }
        assertEquals(
            "Content-Type: text/plain\r\n\r\nsecret body",
            PgpDecryptor.decrypt(copies.getValue("bob@example.com"), bob.armored_private_key, passphrase.toCharArray()),
        )
        assertNull(PgpDecryptor.decrypt(copies.getValue("bob@example.com"), carol.armored_private_key, passphrase.toCharArray()))
    }

    @Test
    fun copies_are_skipped_for_bad_keys_or_oversized_attachments() {
        val bob = PgpKeyGenerator.generate("Bob", "bob@example.com", passphrase.toCharArray())
        val entity = "body".toByteArray()

        assertNull(ClientPgpCopies.encrypt_copies(entity, linkedMapOf("bob@example.com" to "not a key"), 0))
        assertNull(ClientPgpCopies.encrypt_copies(entity, emptyMap(), 0))
        assertNull(
            ClientPgpCopies.encrypt_copies(
                entity,
                linkedMapOf("bob@example.com" to bob.armored_public_key),
                ClientPgpCopies.MAX_COPY_ATTACHMENT_BYTES + 1,
            ),
        )
    }
}
