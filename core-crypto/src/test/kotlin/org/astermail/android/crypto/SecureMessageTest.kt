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

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureMessageTest {

    private class OpenedAttachment(val filename: String, val content_type: String, val size_bytes: Long, val data: ByteArray)

    private class Opened(val subject: String, val body: String, val attachments: List<OpenedAttachment>)

    private val password = "correct horse battery staple 42"

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private fun open_field(key: ByteArray, field: SecureMessage.Field, aad: ByteArray): ByteArray =
        AesGcm.decrypt(key, decode(field.nonce), decode(field.ciphertext), aad)

    private fun content_key(candidate: String, sealed: SecureMessage.Sealed): ByteArray {
        val material = SecureMessage.derive_password_material(candidate, decode(sealed.kdf_salt))
        val seed = AesGcm.decrypt(material.kdf_key, decode(sealed.kem_seed_nonce), decode(sealed.encrypted_kem_seed))
        val keypair = RatchetCrypto.ml_kem_768_keypair_from_seed(seed)
        val shared = RatchetCrypto.ml_kem_768_decapsulate(decode(sealed.kem_ciphertext), keypair.secret_key)
        return SecureMessage.derive_content_key(shared, material.kdf_key)
    }

    private fun open(candidate: String, sealed: SecureMessage.Sealed): Opened {
        val key = content_key(candidate, sealed)
        val subject = open_field(key, sealed.encrypted_subject, SecureMessage.field_aad("subject"))
        val body = open_field(key, sealed.encrypted_body, SecureMessage.field_aad("body"))
        val attachments = sealed.attachments_bundle?.let { bundle ->
            val outer = JSONObject(String(decode(bundle), Charsets.UTF_8))
            val entries = JSONArray(
                String(
                    open_field(
                        key,
                        SecureMessage.Field(outer.getString("ciphertext"), outer.getString("nonce")),
                        SecureMessage.field_aad("attachments_bundle"),
                    ),
                    Charsets.UTF_8,
                ),
            )
            (0 until entries.length()).map { sequence ->
                val entry = entries.getJSONObject(sequence)
                val meta = JSONObject(
                    String(
                        open_field(
                            key,
                            SecureMessage.Field(entry.getString("encrypted_meta"), entry.getString("meta_nonce")),
                            SecureMessage.attachment_aad(sequence, "meta"),
                        ),
                        Charsets.UTF_8,
                    ),
                )
                OpenedAttachment(
                    filename = String(
                        open_field(
                            key,
                            SecureMessage.Field(entry.getString("encrypted_filename"), entry.getString("filename_nonce")),
                            SecureMessage.attachment_aad(sequence, "filename"),
                        ),
                        Charsets.UTF_8,
                    ),
                    content_type = meta.getString("content_type"),
                    size_bytes = meta.getLong("size_bytes"),
                    data = open_field(
                        key,
                        SecureMessage.Field(entry.getString("ciphertext"), entry.getString("nonce")),
                        SecureMessage.attachment_aad(sequence, "data"),
                    ),
                )
            }
        }.orEmpty()
        return Opened(String(subject, Charsets.UTF_8), String(body, Charsets.UTF_8), attachments)
    }

    @Test
    fun `a sealed message opens with its password`() {
        val sealed = SecureMessage.seal(password, "Quarterly numbers", "<p>Grüße ✓</p>")

        val opened = open(password, sealed)

        assertEquals("Quarterly numbers", opened.subject)
        assertEquals("<p>Grüße ✓</p>", opened.body)
        assertNull(sealed.attachments_bundle)
    }

    @Test
    fun `a sealed message does not open with another password`() {
        val sealed = SecureMessage.seal(password, "Subject", "Body")

        assertThrows(Exception::class.java) { open("correct horse battery staple 43", sealed) }
    }

    @Test
    fun `sealed fields have the sizes the server and the web reader expect`() {
        val sealed = SecureMessage.seal(password, "Subject", "Body")

        assertEquals(16, decode(sealed.kdf_salt).size)
        assertEquals(32, decode(sealed.auth_proof).size)
        assertEquals(1088, decode(sealed.kem_ciphertext).size)
        assertEquals(64 + 16, decode(sealed.encrypted_kem_seed).size)
        assertEquals(12, decode(sealed.kem_seed_nonce).size)
        assertEquals(12, decode(sealed.encrypted_subject.nonce).size)
        assertEquals("Subject".length + 16, decode(sealed.encrypted_subject.ciphertext).size)
        assertEquals("Body".length + 16, decode(sealed.encrypted_body.ciphertext).size)
    }

    @Test
    fun `the proof is the hash of the password verifier and never the verifier`() {
        val sealed = SecureMessage.seal(password, "Subject", "Body")
        val material = SecureMessage.derive_password_material(password, decode(sealed.kdf_salt))

        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(material.auth_verifier),
            decode(sealed.auth_proof),
        )
        assertFalse(material.auth_verifier.contentEquals(decode(sealed.auth_proof)))
        assertFalse(material.kdf_key.contentEquals(material.auth_verifier))
    }

    @Test
    fun `password material matches the PBKDF2 reference`() {
        val salt = ByteArray(16) { it.toByte() }
        val material = SecureMessage.derive_password_material("pässword", salt)
        val reference = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec("pässword".toCharArray(), salt, 310_000, 512))
            .encoded

        assertArrayEquals(reference.copyOfRange(0, 32), material.kdf_key)
        assertArrayEquals(reference.copyOfRange(32, 64), material.auth_verifier)
    }

    @Test
    fun `the subject and the body cannot be swapped`() {
        val sealed = SecureMessage.seal(password, "Subject", "Body")
        val key = content_key(password, sealed)

        assertThrows(Exception::class.java) {
            open_field(key, sealed.encrypted_subject, SecureMessage.field_aad("body"))
        }
        assertThrows(Exception::class.java) {
            open_field(key, sealed.encrypted_body, SecureMessage.field_aad("subject"))
        }
        assertThrows(Exception::class.java) {
            AesGcm.decrypt(key, decode(sealed.encrypted_body.nonce), decode(sealed.encrypted_body.ciphertext))
        }
    }

    @Test
    fun `attachments travel inside the sealed bundle`() {
        val first = ByteArray(5000) { (it * 7).toByte() }
        val second = byteArrayOf(1, 2, 3)
        val sealed = SecureMessage.seal(
            password,
            "Subject",
            "Body",
            listOf(
                SecureMessage.Attachment("résumé.pdf", "application/pdf", first),
                SecureMessage.Attachment("note.txt", "text/plain", second),
            ),
        )

        val opened = open(password, sealed)

        assertEquals(2, opened.attachments.size)
        assertEquals("résumé.pdf", opened.attachments[0].filename)
        assertEquals("application/pdf", opened.attachments[0].content_type)
        assertEquals(5000L, opened.attachments[0].size_bytes)
        assertArrayEquals(first, opened.attachments[0].data)
        assertEquals("note.txt", opened.attachments[1].filename)
        assertArrayEquals(second, opened.attachments[1].data)
        val outer = String(decode(sealed.attachments_bundle!!), Charsets.UTF_8)
        assertFalse(outer.contains("note.txt"))
        assertFalse(outer.contains("application/pdf"))
    }

    @Test
    fun `attachments cannot be reordered inside the bundle`() {
        val sealed = SecureMessage.seal(
            password,
            "Subject",
            "Body",
            listOf(
                SecureMessage.Attachment("a.txt", "text/plain", byteArrayOf(1)),
                SecureMessage.Attachment("b.txt", "text/plain", byteArrayOf(2)),
            ),
        )
        val key = content_key(password, sealed)
        val outer = JSONObject(String(decode(sealed.attachments_bundle!!), Charsets.UTF_8))
        val entries = JSONArray(
            String(
                open_field(
                    key,
                    SecureMessage.Field(outer.getString("ciphertext"), outer.getString("nonce")),
                    SecureMessage.field_aad("attachments_bundle"),
                ),
                Charsets.UTF_8,
            ),
        )
        val second = entries.getJSONObject(1)

        assertThrows(Exception::class.java) {
            open_field(
                key,
                SecureMessage.Field(second.getString("ciphertext"), second.getString("nonce")),
                SecureMessage.attachment_aad(0, "data"),
            )
        }
    }

    @Test
    fun `every seal uses a fresh salt and fresh nonces`() {
        val first = SecureMessage.seal(password, "Subject", "Body")
        val second = SecureMessage.seal(password, "Subject", "Body")

        assertNotEquals(first.kdf_salt, second.kdf_salt)
        assertNotEquals(first.auth_proof, second.auth_proof)
        assertNotEquals(first.kem_ciphertext, second.kem_ciphertext)
        assertNotEquals(first.encrypted_subject.nonce, second.encrypted_subject.nonce)
        assertNotEquals(first.encrypted_subject.nonce, first.encrypted_body.nonce)
        assertNotEquals(first.encrypted_body.ciphertext, second.encrypted_body.ciphertext)
    }

    @Test
    fun `sealed output holds neither the password nor the plaintext`() {
        val sealed = SecureMessage.seal(password, "Launch plan", "The launch is on Tuesday")
        val everything = sealed.toString()

        assertFalse(everything.contains(password))
        assertFalse(everything.contains("Launch plan"))
        assertFalse(everything.contains("Tuesday"))
        assertTrue(everything.contains(sealed.kdf_salt))
    }

    @Test
    fun `a message sealed by the web client opens here`() {
        val vector = JSONObject(
            javaClass.classLoader!!.getResourceAsStream("secure_message_web_vector.json")!!
                .bufferedReader().use { it.readText() },
        )
        val bundle = vector.getJSONObject("bundle")
        fun field(name: String) = bundle.getJSONObject(name).let {
            SecureMessage.Field(it.getString("ciphertext"), it.getString("nonce"))
        }
        val sealed = SecureMessage.Sealed(
            kdf_salt = vector.getString("kdf_salt"),
            auth_proof = vector.getString("auth_proof"),
            kem_ciphertext = bundle.getString("kem_ciphertext"),
            encrypted_kem_seed = bundle.getString("encrypted_kem_seed"),
            kem_seed_nonce = bundle.getString("kem_seed_nonce"),
            encrypted_subject = field("encrypted_subject"),
            encrypted_body = field("encrypted_body"),
            attachments_bundle = bundle.optString("encrypted_attachments_bundle").ifEmpty { null },
        )

        val opened = open(vector.getString("password"), sealed)

        assertEquals(vector.getString("subject"), opened.subject)
        assertEquals(vector.getString("body"), opened.body)
        assertEquals(1, opened.attachments.size)
        assertEquals(vector.getString("attachment_filename"), opened.attachments[0].filename)
        assertArrayEquals(decode(vector.getString("attachment_data")), opened.attachments[0].data)
        val material = SecureMessage.derive_password_material(vector.getString("password"), decode(sealed.kdf_salt))
        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(material.auth_verifier),
            decode(sealed.auth_proof),
        )
    }

    @Test
    fun `writes a vector for the web reader`() {
        val sealed = SecureMessage.seal(
            password,
            "Android subject ✓",
            "<p>Android body</p>",
            listOf(SecureMessage.Attachment("android.txt", "text/plain", "from android".toByteArray())),
        )
        val vector = JSONObject()
            .put("password", password)
            .put("subject", "Android subject ✓")
            .put("body", "<p>Android body</p>")
            .put("attachment_filename", "android.txt")
            .put("attachment_data", Base64.getEncoder().encodeToString("from android".toByteArray()))
            .put("kdf_salt", sealed.kdf_salt)
            .put("auth_proof", sealed.auth_proof)
            .put(
                "bundle",
                JSONObject()
                    .put("kem_ciphertext", sealed.kem_ciphertext)
                    .put("encrypted_kem_seed", sealed.encrypted_kem_seed)
                    .put("kem_seed_nonce", sealed.kem_seed_nonce)
                    .put(
                        "encrypted_subject",
                        JSONObject()
                            .put("ciphertext", sealed.encrypted_subject.ciphertext)
                            .put("nonce", sealed.encrypted_subject.nonce),
                    )
                    .put(
                        "encrypted_body",
                        JSONObject()
                            .put("ciphertext", sealed.encrypted_body.ciphertext)
                            .put("nonce", sealed.encrypted_body.nonce),
                    )
                    .put("encrypted_attachments_bundle", sealed.attachments_bundle),
            )
        val target = System.getenv("ASTER_SECURE_MESSAGE_VECTOR_OUT") ?: return

        java.io.File(target).writeText(vector.toString())
    }
}
