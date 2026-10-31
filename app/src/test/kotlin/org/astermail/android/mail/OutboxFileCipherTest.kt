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

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutboxFileCipherTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val key = ByteArray(32) { 7 }
    private val cipher = OutboxFileCipher { key.copyOf() }
    private val payload = """[{"filename":"secret.pdf","content":"QUJD"}]""".toByteArray()

    @Test
    fun `a sealed file round trips and hides the payload`() {
        val sealed = cipher.seal("send-1.json", payload)

        assertTrue(OutboxFileCipher.is_sealed(sealed))
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("secret.pdf"))
        assertArrayEquals(payload, cipher.open("send-1.json", sealed))
    }

    @Test
    fun `sealing twice never repeats the ciphertext`() {
        assertFalse(cipher.seal("send-1.json", payload).contentEquals(cipher.seal("send-1.json", payload)))
    }

    @Test
    fun `a legacy plaintext file is still readable`() {
        assertArrayEquals(payload, cipher.open("send-1.json", payload))
    }

    @Test
    fun `a legacy plaintext file is sealed in place`() {
        val dir = temp.newFolder("outbox_attachments")
        val file = File(dir, "send-1.json").apply { writeBytes(payload) }

        assertTrue(reseal_outbox_file(file, cipher, payload))

        val stored = file.readBytes()
        assertTrue(OutboxFileCipher.is_sealed(stored))
        assertFalse(String(stored, Charsets.ISO_8859_1).contains("secret.pdf"))
        assertArrayEquals(payload, cipher.open("send-1.json", stored))
        assertEquals(listOf("send-1.json"), dir.list()!!.toList())
    }

    @Test
    fun `a legacy file stays readable when it cannot be sealed`() {
        val dir = temp.newFolder("outbox_attachments")
        val file = File(dir, "send-1.json").apply { writeBytes(payload) }

        assertFalse(reseal_outbox_file(file, OutboxFileCipher { null }, payload))

        assertArrayEquals(payload, file.readBytes())
        assertEquals(listOf("send-1.json"), dir.list()!!.toList())
    }

    @Test
    fun `a file moved to another send is rejected`() {
        val sealed = cipher.seal("send-1.json", payload)

        assertThrows(OutboxPayloadException::class.java) { cipher.open("send-2.json", sealed) }
    }

    @Test
    fun `a tampered file is rejected`() {
        val sealed = cipher.seal("send-1.json", payload)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()

        assertThrows(OutboxPayloadException::class.java) { cipher.open("send-1.json", sealed) }
    }

    @Test
    fun `a different key cannot open the file`() {
        val sealed = cipher.seal("send-1.json", payload)
        val other = OutboxFileCipher { ByteArray(32) { 9 } }

        assertThrows(OutboxPayloadException::class.java) { other.open("send-1.json", sealed) }
    }

    @Test
    fun `a missing key refuses to stage and fails the send cleanly`() {
        val keyless = OutboxFileCipher { null }

        assertThrows(java.io.IOException::class.java) { keyless.seal("send-1.json", payload) }
        val sealed = cipher.seal("send-1.json", payload)
        val err = assertThrows(OutboxPayloadException::class.java) { keyless.open("send-1.json", sealed) }
        assertTrue(is_permanent_attachment_failure_cause(err))
    }

    @Test
    fun `the key handed to the cipher is wiped after use`() {
        val handed = mutableListOf<ByteArray>()
        val tracking = OutboxFileCipher { key.copyOf().also { handed.add(it) } }

        tracking.open("send-1.json", tracking.seal("send-1.json", payload))

        assertTrue(handed.size == 2 && handed.all { copy -> copy.all { it == 0.toByte() } })
    }

    private fun key_store(
        read: () -> String?,
        write: (String) -> Boolean,
        new_key: () -> ByteArray = { ByteArray(32) { 9 } },
    ) = OutboxFileKeyStore(
        read_encoded = read,
        write_encoded = write,
        encode = { java.util.Base64.getEncoder().encodeToString(it) },
        decode = { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() },
        new_key = new_key,
    )

    @Test
    fun `a key store read failure never replaces the stored key`() {
        var writes = 0
        val store = key_store(read = { throw IllegalStateException("keystore locked") }, write = { writes++; true })

        assertTrue(store.key() == null)
        assertTrue(writes == 0)
    }

    @Test
    fun `an unreadable stored key fails closed instead of being replaced`() {
        var writes = 0
        val store = key_store(read = { "%%%" }, write = { writes++; true })

        assertTrue(store.key() == null)
        assertTrue(writes == 0)
    }

    @Test
    fun `the key is created once and reused by every caller`() {
        var stored: String? = null
        var created = 0
        val store = key_store(
            read = { stored },
            write = { stored = it; true },
            new_key = { created++; ByteArray(32) { created.toByte() } },
        )
        val threads = (1..8).map { Thread { store.key() } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val first = store.key()!!
        first.fill(0)

        assertTrue(created == 1)
        assertArrayEquals(ByteArray(32) { 1 }, store.key())
    }

    @Test
    fun `a key that cannot be saved is never used`() {
        val store = key_store(read = { null }, write = { false })

        assertTrue(store.key() == null)
    }
}
