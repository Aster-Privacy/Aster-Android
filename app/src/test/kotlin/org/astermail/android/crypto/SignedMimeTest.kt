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

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.ByteArrayInputStream
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignedMimeTest {

    private val passphrase = "correct horse battery staple"

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun sample_input(
        subject: String = "Signed and encrypted test",
        body: String = "<p>Hello there</p>",
        attachments: List<ProtectedMimeAttachment> = emptyList(),
    ) = ProtectedMimeInput(
        subject = subject,
        body = body,
        is_html = true,
        from = "jperry@astermail.org",
        to = listOf("recipient@example.com"),
        cc = emptyList(),
        attachments = attachments,
    )

    @Test
    fun builds_a_protected_headers_entity() {
        val mime = ProtectedMimeBuilder.build(sample_input())

        assertTrue(mime.startsWith("Content-Type: multipart/mixed;"))
        assertTrue(mime.contains("protected-headers=\"v1\""))
        assertTrue(mime.contains("Content-Type: text/rfc822-headers"))
        assertTrue(mime.contains("From: jperry@astermail.org"))
        assertTrue(mime.contains("To: recipient@example.com"))
        assertTrue(mime.contains("Subject: Signed and encrypted test"))
        assertTrue(mime.contains("multipart/alternative"))
        assertFalse(mime.contains("\n\n"))
    }

    @Test
    fun encodes_non_ascii_subjects_as_encoded_words() {
        val mime = ProtectedMimeBuilder.build(sample_input(subject = "Signed ✓ test"))

        assertTrue(mime.contains("Subject: =?UTF-8?B?"))
        assertFalse(mime.contains("Subject: Signed ✓ test"))
    }

    @Test
    fun includes_attachments_as_base64_parts() {
        val mime = ProtectedMimeBuilder.build(
            sample_input(
                attachments = listOf(
                    ProtectedMimeAttachment(
                        filename = "note.txt",
                        content_type = "text/plain",
                        data_base64 = java.util.Base64.getEncoder()
                            .encodeToString("hello".toByteArray()),
                    ),
                ),
            ),
        )

        assertTrue(mime.contains("Content-Disposition: attachment; filename=\"note.txt\""))
        assertTrue(mime.contains("Content-Transfer-Encoding: base64"))
    }

    @Test
    fun uses_a_unique_boundary_per_message() {
        val first = ProtectedMimeBuilder.build(sample_input())
        val second = ProtectedMimeBuilder.build(sample_input())

        assertNotEquals(
            first.substringAfter("boundary=\"").substringBefore("\""),
            second.substringAfter("boundary=\"").substringBefore("\""),
        )
    }

    @Test
    fun signs_the_entity_so_the_published_key_verifies_it() {
        val keys = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())
        val mime = ProtectedMimeBuilder.build(sample_input()).toByteArray(Charsets.UTF_8)

        val signed = PgpSigner.sign_detached(mime, keys.armored_private_key, passphrase.toCharArray())

        assertNotNull(signed)
        assertEquals("pgp-sha512", signed!!.micalg)
        assertTrue(signed.signature.startsWith("-----BEGIN PGP SIGNATURE-----"))
        assertTrue(verify(mime, signed.signature, keys.armored_public_key))
    }

    @Test
    fun rejects_a_signature_over_different_bytes() {
        val keys = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())
        val mime = ProtectedMimeBuilder.build(sample_input()).toByteArray(Charsets.UTF_8)

        val signed = PgpSigner.sign_detached(mime, keys.armored_private_key, passphrase.toCharArray())!!

        assertFalse(verify(mime + "tampered".toByteArray(), signed.signature, keys.armored_public_key))
    }

    @Test
    fun returns_null_for_a_wrong_passphrase() {
        val keys = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())
        val mime = ProtectedMimeBuilder.build(sample_input()).toByteArray(Charsets.UTF_8)

        assertEquals(null, PgpSigner.sign_detached(mime, keys.armored_private_key, "wrong".toCharArray()))
    }

    @Test
    fun writes_an_interop_fixture_when_requested() {
        val out = System.getenv("ASTER_SIGNED_MIME_FIXTURE_OUT") ?: return

        val sender = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())
        val recipient = PgpKeyGenerator.generate("Bob", "bob@example.com", passphrase.toCharArray())
        val input = sample_input(
            subject = "Signed and encrypted ✓",
            attachments = listOf(
                ProtectedMimeAttachment(
                    filename = "note.txt",
                    content_type = "text/plain",
                    data_base64 = java.util.Base64.getEncoder().encodeToString("hello".toByteArray()),
                ),
            ),
        )
        val mime = ProtectedMimeBuilder.build(input).toByteArray(Charsets.UTF_8)
        val signed = PgpSigner.sign_detached(mime, sender.armored_private_key, passphrase.toCharArray())!!

        val fields = linkedMapOf(
            "mime_base64" to java.util.Base64.getEncoder().encodeToString(mime),
            "signature" to signed.signature,
            "micalg" to signed.micalg,
            "sender_public_key" to sender.armored_public_key,
            "recipient_public_key" to recipient.armored_public_key,
            "recipient_private_key" to recipient.armored_private_key,
            "passphrase" to passphrase,
        )

        val json = fields.entries.joinToString(",", "{", "}") { (key, value) ->
            "\"$key\":\"${json_escape(value)}\""
        }

        java.io.File(out).writeText(json)
    }

    @Test
    fun writes_an_end_to_end_fixture_when_requested() {
        val out = System.getenv("ASTER_SIGNED_MIME_E2E_FIXTURE_OUT") ?: return

        val body = "Test test&nbsp;"
        val sender = PgpKeyGenerator.generate("Alice", "alice@astermail.org", passphrase.toCharArray())
        val mime = ProtectedMimeBuilder.build(
            ProtectedMimeInput(
                subject = "Test",
                body = body,
                is_html = true,
                from = "alice@astermail.org",
                to = listOf("external@example.org"),
                cc = emptyList(),
                attachments = emptyList(),
            ),
        ).toByteArray(Charsets.UTF_8)
        val signed = PgpSigner.sign_detached(mime, sender.armored_private_key, passphrase.toCharArray())!!

        val fields = linkedMapOf(
            "mime_base64" to java.util.Base64.getEncoder().encodeToString(mime),
            "signature" to signed.signature,
            "micalg" to signed.micalg,
            "sender_public_key" to sender.armored_public_key,
            "expected_subject" to "Test",
            "expected_html" to body,
        )

        val json = fields.entries.joinToString(",", "{", "}") { (key, value) ->
            "\"$key\":\"${json_escape(value)}\""
        }

        java.io.File(out).writeText(json)
    }

    private fun json_escape(value: String): String {
        val builder = StringBuilder()

        for (c in value) {
            when {
                c == '"' -> builder.append("\\\"")
                c == '\\' -> builder.append("\\\\")
                c == '\n' -> builder.append("\\n")
                c == '\r' -> builder.append("\\r")
                c == '\t' -> builder.append("\\t")
                c.code < 0x20 -> builder.append("\\u%04x".format(c.code))
                else -> builder.append(c)
            }
        }

        return builder.toString()
    }

    private fun verify(data: ByteArray, armored_signature: String, armored_public_key: String): Boolean {
        val signature_stream = PGPUtil.getDecoderStream(
            ByteArrayInputStream(armored_signature.toByteArray(Charsets.UTF_8)),
        )
        val signature_list = JcaPGPObjectFactory(signature_stream).nextObject() as PGPSignatureList
        val signature = signature_list[0]

        val key_stream = PGPUtil.getDecoderStream(
            ByteArrayInputStream(armored_public_key.toByteArray(Charsets.UTF_8)),
        )
        val public_keys = PGPPublicKeyRingCollection(key_stream, JcaKeyFingerprintCalculator())
        val public_key = public_keys.getPublicKey(signature.keyID) ?: return false

        signature.init(JcaPGPContentVerifierBuilderProvider(), public_key)
        signature.update(data)

        return signature.verify()
    }
}
