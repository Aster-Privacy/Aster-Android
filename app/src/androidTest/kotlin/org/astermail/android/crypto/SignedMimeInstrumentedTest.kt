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

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignedMimeInstrumentedTest {

    private val passphrase = "correct horse battery staple"

    private fun sample_mime(): ByteArray {
        val body = "<p>Hello there</p>"

        return ProtectedMimeBuilder.build(
            ProtectedMimeInput(
                subject = "Signed and encrypted ✓",
                body = body,
                is_html = true,
                from = "emu@astermail.org",
                to = listOf("recipient@example.com"),
                cc = emptyList(),
                attachments = listOf(
                    ProtectedMimeAttachment(
                        filename = "note.txt",
                        content_type = "text/plain",
                        data_base64 = android.util.Base64.encodeToString(
                            "hello".toByteArray(),
                            android.util.Base64.NO_WRAP,
                        ),
                    ),
                ),
            ),
        ).toByteArray(Charsets.UTF_8)
    }

    @Test
    fun builds_and_signs_a_protected_entity_on_device() {
        val keys = PgpKeyGenerator.generate("Emu User", "emu@astermail.org", passphrase.toCharArray())
        val mime = sample_mime()
        val text = String(mime, Charsets.UTF_8)

        assertTrue(text.startsWith("Content-Type: multipart/mixed;"))
        assertTrue(text.contains("protected-headers=\"v1\""))
        assertTrue(text.contains("Subject: =?UTF-8?B?"))
        assertTrue(text.contains("Content-Disposition: attachment; filename=\"note.txt\""))
        assertFalse(text.contains("\n\n"))

        val signed = PgpSigner.sign_detached(mime, keys.armored_private_key, passphrase.toCharArray())

        assertNotNull(signed)
        assertEquals("pgp-sha512", signed!!.micalg)
        assertTrue(verify(mime, signed.signature, keys.armored_public_key))
        assertFalse(verify(mime + "x".toByteArray(), signed.signature, keys.armored_public_key))
    }

    @Test
    fun keeps_an_html_alternative_for_a_body_without_visible_tags() {
        val body = "Test test&nbsp;"
        val text = ProtectedMimeBuilder.build(
            ProtectedMimeInput(
                subject = "Test",
                body = body,
                is_html = true,
                from = "emu@astermail.org",
                to = listOf("recipient@example.com"),
                cc = emptyList(),
                attachments = emptyList(),
            ),
        )

        assertTrue(text.contains("multipart/alternative"))
        assertTrue(text.contains("Content-Type: text/html; charset=utf-8"))
        assertFalse(
            text.contains("Content-Type: text/plain; charset=utf-8\r\nContent-Transfer-Encoding: 8bit"),
        )

        val marker = "Content-Type: text/html; charset=utf-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
        val payload = text.substring(text.indexOf(marker) + marker.length).substringBefore("\r\n")
        val decoded = String(
            android.util.Base64.decode(payload, android.util.Base64.DEFAULT),
            Charsets.UTF_8,
        )

        assertEquals(body, decoded)
    }

    @Test
    fun base64_round_trips_through_the_device_encoder() {
        val mime = sample_mime()
        val encoded = android.util.Base64.encodeToString(mime, android.util.Base64.NO_WRAP)

        assertFalse(encoded.contains("\n"))
        assertTrue(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT).contentEquals(mime))
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
