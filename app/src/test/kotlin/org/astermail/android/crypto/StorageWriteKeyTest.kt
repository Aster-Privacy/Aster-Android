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

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.storage.SessionKeyStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageWriteKeyTest {

    private val passphrase = "correct horse battery staple".toByteArray(Charsets.UTF_8)

    private val data_kek = ByteArray(32) { (it * 7 + 1).toByte() }

    private val nonce = ByteArray(12) { it.toByte() }

    private fun seal(plaintext: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(plaintext)
    }

    private fun open(ciphertext: ByteArray, key: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.doFinal(ciphertext)
    }.getOrNull()

    private fun open_with_any(ciphertext: ByteArray, keys: List<ByteArray>): ByteArray? =
        keys.firstNotNullOfOrNull { open(ciphertext, it) }

    private fun store(
        with_data_kek: Boolean = true,
        kdf_version: Int? = null,
        legacy_keks: List<String> = emptyList(),
    ): SessionKeyStore {
        val store = SessionKeyStore(null)
        store.put_passphrase(passphrase.copyOf())
        if (with_data_kek) store.put_data_kek(data_kek.copyOf())
        if (kdf_version != null) store.put_storage_kdf_version(kdf_version)
        if (legacy_keks.isNotEmpty()) store.put_legacy_keks(legacy_keks)
        return store
    }

    private fun v1(): ByteArray = CryptoNative.derive_storage_key(passphrase.copyOf())

    private fun v2(): ByteArray = CryptoNative.derive_stretched_storage_key(passphrase.copyOf())

    private val login_proof = ByteArray(32) { (it * 3 + 5).toByte() }

    private fun signed_in_store(legacy_keks: List<String> = emptyList()): SessionKeyStore =
        store(legacy_keks = legacy_keks).also { it.put(login_proof.copyOf()) }

    private fun encoded(keys: List<ByteArray>): Set<String> = keys.map(StorageWriteKey::encode_key).toSet()

    @Test
    fun `before retirement old rows under the login proof and the unstretched key still open`() {
        val store = signed_in_store()
        val under_proof = seal("old contact".toByteArray(), login_proof)
        val under_v1 = seal("old template".toByteArray(), v1())

        val keys = StorageWriteKey.read_keys(store)

        assertEquals("old contact", open_with_any(under_proof, keys)?.toString(Charsets.UTF_8))
        assertEquals("old template", open_with_any(under_v1, keys)?.toString(Charsets.UTF_8))
        assertFalse(store.legacy_storage_keys_retired())
    }

    @Test
    fun `the legacy read keys are exactly the login proof and the unstretched key`() {
        val legacy = encoded(StorageWriteKey.legacy_read_keys(signed_in_store()))

        assertEquals(setOf(StorageWriteKey.encode_key(login_proof), StorageWriteKey.encode_key(v1())), legacy)
    }

    @Test
    fun `read keys put every trusted key before any legacy key`() {
        val earlier = ByteArray(32) { 9 }
        val keys = StorageWriteKey.read_keys(signed_in_store(listOf(StorageWriteKey.encode_key(earlier))))
            .map(StorageWriteKey::encode_key)

        assertEquals(StorageWriteKey.encode_key(data_kek), keys[0])
        assertEquals(StorageWriteKey.encode_key(earlier), keys[1])
        assertEquals(setOf(StorageWriteKey.encode_key(login_proof), StorageWriteKey.encode_key(v1())), keys.drop(2).toSet())
    }

    @Test
    fun `a retired account never reads with the login proof or the unstretched key`() {
        val store = signed_in_store()
        store.retire_legacy_storage_keys()

        val reads = encoded(StorageWriteKey.read_keys(store))
        val lookups = encoded(StorageWriteKey.lookup_keys(store))

        assertTrue(store.legacy_storage_keys_retired())
        assertTrue(StorageWriteKey.legacy_read_keys(store).isEmpty())
        assertFalse(reads.contains(StorageWriteKey.encode_key(login_proof)))
        assertFalse(reads.contains(StorageWriteKey.encode_key(v1())))
        assertFalse(lookups.contains(StorageWriteKey.encode_key(v1())))
        assertEquals(setOf(StorageWriteKey.encode_key(data_kek)), reads)
    }

    @Test
    fun `a server forged row under the login proof is refused after retirement`() {
        val store = signed_in_store()
        val forged = seal("injected contact".toByteArray(), login_proof)
        store.retire_legacy_storage_keys()

        assertNull(open_with_any(forged, StorageWriteKey.read_keys(store)))
        assertNull(open_with_any(forged, StorageWriteKey.lookup_keys(store)))
    }

    @Test
    fun `a retired account still reads rows under its data key and earlier vault keys`() {
        val earlier = ByteArray(32) { 9 }
        val store = signed_in_store(listOf(StorageWriteKey.encode_key(earlier)))
        val current = seal("current".toByteArray(), data_kek)
        val older = seal("older".toByteArray(), earlier)
        store.retire_legacy_storage_keys()

        val keys = StorageWriteKey.read_keys(store)

        assertEquals("current", open_with_any(current, keys)?.toString(Charsets.UTF_8))
        assertEquals("older", open_with_any(older, keys)?.toString(Charsets.UTF_8))
    }

    @Test
    fun `signing out forgets the retirement`() {
        val store = signed_in_store()
        store.retire_legacy_storage_keys()

        store.clear()

        assertFalse(store.legacy_storage_keys_retired())
    }

    @Test
    fun `writer ciphertext opens with the data key and not with the password keys`() {
        val write_key = StorageWriteKey.write_key(store())
        assertNotNull(write_key)
        assertArrayEquals(data_kek, write_key)

        val sealed = seal("contact".toByteArray(), write_key!!)

        assertArrayEquals("contact".toByteArray(), open(sealed, data_kek))
        assertNull(open(sealed, v1()))
        assertNull(open(sealed, v2()))
    }

    @Test
    fun `without a data key no password key is used for writes`() {
        assertNull(StorageWriteKey.write_key(store(with_data_kek = false)))
        assertNull(StorageWriteKey.write_key(store(with_data_kek = false, kdf_version = 1)))
        assertNull(StorageWriteKey.write_key(store(with_data_kek = false, kdf_version = 2)))
        assertNull(StorageWriteKey.write_key(store(with_data_kek = false, kdf_version = 3)))
        assertFalse(StorageWriteKey.is_known_kdf_version(-1))
        assertTrue(StorageWriteKey.is_known_kdf_version(null))
    }

    @Test
    fun `an unknown kdf version still lets readers open v1 data`() {
        val sealed = seal("alias".toByteArray(), v1())

        val keys = StorageWriteKey.read_keys(store(with_data_kek = false, kdf_version = 3))

        assertArrayEquals("alias".toByteArray(), open_with_any(sealed, keys))
    }

    @Test
    fun `a data key of the wrong length gives no write key`() {
        assertNull(StorageWriteKey.select_write_key(ByteArray(16) { 1 }))
    }

    @Test
    fun `no data key gives no write key`() {
        assertNull(StorageWriteKey.select_write_key(null))
    }

    @Test
    fun `the sign up key is random and differs from the password key`() {
        val first = StorageWriteKey.random_data_kek()
        val second = StorageWriteKey.random_data_kek()

        assertEquals(32, first.size)
        assertTrue(first.any { it.toInt() != 0 })
        assertFalse(first.contentEquals(second))
        assertFalse(first.contentEquals(v1()))
        assertFalse(first.contentEquals(v2()))
    }

    @Test
    fun `the sign up key rejects an all zero draw`() {
        var calls = 0
        val random = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) {
                calls += 1
                if (calls == 1) bytes.fill(0) else bytes.fill(5)
            }
        }

        val key = StorageWriteKey.random_data_kek(random)

        assertEquals(2, calls)
        assertTrue(key.all { it.toInt() == 5 })
    }

    @Test
    fun `legacy v1 contacts aliases and metadata still open after the upgrade`() {
        val old_key = v1()
        val legacy = listOf(StorageWriteKey.encode_key(old_key))
        val fixtures = listOf("contact", "alias", "metadata").map { it to seal(it.toByteArray(), old_key) }

        val keys = StorageWriteKey.read_keys(store(legacy_keks = legacy))

        assertArrayEquals(data_kek, keys.first())
        for ((plain, sealed) in fixtures) {
            assertArrayEquals(plain.toByteArray(), open_with_any(sealed, keys))
        }
    }

    @Test
    fun `data from an earlier legacy key opens through the legacy list`() {
        val earlier = ByteArray(32) { 42 }
        val sealed = seal("old".toByteArray(), earlier)

        val keys = StorageWriteKey.read_keys(store(legacy_keks = listOf(StorageWriteKey.encode_key(earlier))))

        assertArrayEquals("old".toByteArray(), open_with_any(sealed, keys))
    }

    @Test
    fun `read keys are unique and start with the write key`() {
        val legacy = listOf(StorageWriteKey.encode_key(data_kek), StorageWriteKey.encode_key(v1()))

        val keys = StorageWriteKey.read_keys(store(legacy_keks = legacy))
        val encoded = keys.map(StorageWriteKey::encode_key)

        assertArrayEquals(data_kek, keys.first())
        assertEquals(encoded.size, encoded.toSet().size)
        assertTrue(encoded.contains(StorageWriteKey.encode_key(v1())))
    }

    @Test
    fun `lookup keys put the write key first then legacy then password keys`() {
        val write = ByteArray(32) { 1 }
        val legacy = ByteArray(32) { 2 }
        val password = ByteArray(32) { 3 }

        val keys = StorageWriteKey.lookup_keys(
            write.copyOf(),
            listOf(StorageWriteKey.encode_key(legacy), "not-a-key", StorageWriteKey.encode_key(write)),
            listOf(password.copyOf(), legacy.copyOf()),
        )

        assertEquals(3, keys.size)
        assertArrayEquals(write, keys[0])
        assertArrayEquals(legacy, keys[1])
        assertArrayEquals(password, keys[2])
    }

    @Test
    fun `lookup keys for an upgraded account include the old password key`() {
        val keys = StorageWriteKey.lookup_keys(store()).map(StorageWriteKey::encode_key)

        assertEquals(StorageWriteKey.encode_key(data_kek), keys.first())
        assertTrue(keys.contains(StorageWriteKey.encode_key(v1())))
    }

    @Test
    fun `decode key accepts only 32 byte keys`() {
        assertNull(StorageWriteKey.decode_key(Base64.getEncoder().encodeToString(ByteArray(16))))
        assertNull(StorageWriteKey.decode_key("%%%"))
        assertArrayEquals(data_kek, StorageWriteKey.decode_key(StorageWriteKey.encode_key(data_kek)))
    }
}
