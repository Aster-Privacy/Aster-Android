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
package org.astermail.android.crypto

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.storage.SessionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePublicKekFilterTest {

    private fun constant(context: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("undefined" + context).toByteArray(Charsets.UTF_8))

    @Test
    fun `decrypt key lists drop the public mail constants and keep real keys`() {
        val real = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 7).toByte() })
        val kept = StorageWriteKey.real_decrypt_keks(
            listOf(
                Base64.getEncoder().encodeToString(constant("astermail-envelope-v1")),
                real,
                Base64.getEncoder().encodeToString(constant("astermail-import-v1")),
            ),
        )

        assertEquals(listOf(real), kept)
    }

    private fun public_constant_count(): Int =
        StorageWriteKey.PUBLIC_CONSTANT_CONTEXTS.toSet().size * StorageWriteKey.UNSET_IDENTITY_MATERIALS.size

    @Test
    fun `the empty and null identity placeholders derive public constants too`() {
        for (material in listOf("", "null")) {
            for (context in listOf("astermail-envelope-v1", "astermail-draft-v2", "astermail-tags-v1")) {
                val key = MessageDigest.getInstance("SHA-256").digest((material + context).toByteArray(Charsets.UTF_8))

                assertTrue("'$material' with $context should be a public constant", StorageWriteKey.is_public_mail_kek(key))
            }
        }
    }

    @Test
    fun `the legacy constants are exposed only as raw read keys`() {
        val raws = StorageWriteKey.public_mail_kek_raws()

        assertEquals(public_constant_count(), raws.size)
        assertTrue(raws.all { StorageWriteKey.is_public_mail_kek(it) })
    }

    @Test
    fun `the fallback list puts real keys first and every constant last`() {
        val real = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 7).toByte() })
        val list = StorageWriteKey.with_public_fallback(
            listOf(Base64.getEncoder().encodeToString(constant("astermail-envelope-v1")), real),
        )

        assertEquals(real, list.first())
        assertEquals(1 + public_constant_count(), list.size)
    }

    @Test
    fun `opening through a constant flags a pending reseal until a sweep clears it`() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        val sealed_with = constant("astermail-import-v1")
        val encoded = StorageWriteKey.with_public_fallback(emptyList())

        val opened = StorageWriteKey.open_with_decrypt_keks(encoded, { store.note_public_constant_use() }) { key ->
            if (key.contentEquals(sealed_with)) "ok".toByteArray() else null
        }

        assertEquals("ok", opened?.toString(Charsets.UTF_8))
        assertTrue(store.public_constant_use_pending())
        store.clear_public_constant_use(store.public_constant_use_seq())
        assertEquals(false, store.public_constant_use_pending())
    }

    @Test
    fun `a use during a sweep survives the clear`() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        store.note_public_constant_use()
        val seen = store.public_constant_use_seq()
        store.note_public_constant_use()

        store.clear_public_constant_use(seen)

        assertTrue(store.public_constant_use_pending())
    }

    @Test
    fun `the flag is per account`() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        store.note_public_constant_use()
        store.put_user_id("user-b")

        assertEquals(false, store.public_constant_use_pending())
        store.put_user_id("user-a")
        assertTrue(store.public_constant_use_pending())
    }

    @Test
    fun `read keys opened through a constant note the use`() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        val constant_key = constant("astermail-calendar-v2")

        val opened = StorageWriteKey.open_noting_constants(store, StorageWriteKey.with_public_fallback(emptyList()).map { Base64.getDecoder().decode(it) }) { keys ->
            if (keys.any { it.contentEquals(constant_key) }) "ok" else null
        }

        assertEquals("ok", opened)
        assertTrue(store.public_constant_use_pending())
    }

    @Test
    fun `opening through a real key does not flag a reseal`() {
        val store = SessionKeyStore(null)
        store.put_user_id("user-a")
        val real_key = ByteArray(32) { (it + 7).toByte() }
        val encoded = StorageWriteKey.with_public_fallback(listOf(Base64.getEncoder().encodeToString(real_key)))

        StorageWriteKey.open_with_decrypt_keks(encoded, { store.note_public_constant_use() }) { key ->
            if (key.contentEquals(real_key)) byteArrayOf(1) else null
        }

        assertEquals(false, store.public_constant_use_pending())
    }
}
