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
import io.mockk.mockk
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.crypto.storage_rotation.VaultKeyView
import org.astermail.android.crypto.storage_rotation.build_reseal_keys
import org.astermail.android.crypto.storage_rotation.collect_old_storage_raws
import org.astermail.android.crypto.storage_rotation.gcm_open
import org.astermail.android.crypto.storage_rotation.gcm_seal
import org.astermail.android.crypto.storage_rotation.open_under_old
import org.astermail.android.crypto.storage_rotation.opens_under_new
import org.astermail.android.crypto.storage_rotation.seal_under_new
import org.astermail.android.mail.LevelTwoEnvelope
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

    private fun retire(store: SessionKeyStore): Boolean {
        val swept = StorageWriteKey.sweep_keys(store) ?: return false
        return try {
            StorageWriteKey.retire_legacy_keys(store, swept)
        } finally {
            swept.forEach { it.fill(0) }
        }
    }

    @Test
    fun `before retirement old rows under the unstretched key still open but never under the login proof`() {
        val store = signed_in_store()
        val under_proof = seal("old contact".toByteArray(), login_proof)
        val under_v1 = seal("old template".toByteArray(), v1())

        val keys = StorageWriteKey.read_keys(store)

        assertNull(open_with_any(under_proof, keys))
        assertNull(open_with_any(under_proof, StorageWriteKey.lookup_keys(store)))
        assertEquals("old template", open_with_any(under_v1, keys)?.toString(Charsets.UTF_8))
        assertFalse(store.legacy_storage_keys_retired())
    }

    @Test
    fun `the legacy read keys are exactly the unstretched key`() {
        val legacy = encoded(StorageWriteKey.legacy_read_keys(signed_in_store()))

        assertEquals(setOf(StorageWriteKey.encode_key(v1())), legacy)
    }

    @Test
    fun `the login proof never joins the sweep keys`() {
        val swept = encoded(StorageWriteKey.sweep_keys(signed_in_store())!!)

        assertEquals(setOf(StorageWriteKey.encode_key(v1())), swept)
    }

    @Test
    fun `the login proof carried in the vault key list is not a read key`() {
        val store = signed_in_store(listOf(StorageWriteKey.encode_key(login_proof)))

        assertFalse(encoded(StorageWriteKey.read_keys(store)).contains(StorageWriteKey.encode_key(login_proof)))
        assertFalse(encoded(StorageWriteKey.lookup_keys(store)).contains(StorageWriteKey.encode_key(login_proof)))
    }

    @Test
    fun `the login proof is offered only as an unverified key before retirement`() {
        val store = signed_in_store()

        assertArrayEquals(login_proof, StorageWriteKey.unverified_login_proof_key(store))
        assertTrue(StorageWriteKey.is_login_proof(store, login_proof.copyOf()))
        assertFalse(StorageWriteKey.is_login_proof(store, data_kek.copyOf()))

        assertTrue(retire(store))
        assertNull(StorageWriteKey.unverified_login_proof_key(store))
    }

    @Test
    fun `a public constant stored as the login proof is never offered`() {
        val store = store()
        store.put(StorageWriteKey.public_mail_kek_raws().first())

        assertNull(StorageWriteKey.unverified_login_proof_key(store))
    }

    @Test
    fun `read keys put every trusted key before any legacy key`() {
        val earlier = ByteArray(32) { 9 }
        val keys = StorageWriteKey.read_keys(signed_in_store(listOf(StorageWriteKey.encode_key(earlier))))
            .map(StorageWriteKey::encode_key)

        assertEquals(StorageWriteKey.encode_key(data_kek), keys[0])
        assertEquals(StorageWriteKey.encode_key(earlier), keys[1])
        assertEquals(StorageWriteKey.encode_key(v1()), keys[2])
        assertFalse(keys.contains(StorageWriteKey.encode_key(login_proof)))
    }

    @Test
    fun `a retired account never reads with the login proof or the unstretched key`() {
        val store = signed_in_store()
        assertTrue(retire(store))

        val reads = encoded(StorageWriteKey.read_keys(store))
        val lookups = encoded(StorageWriteKey.lookup_keys(store))

        assertTrue(store.legacy_storage_keys_retired())
        assertTrue(StorageWriteKey.legacy_read_keys(store).isEmpty())
        assertFalse(reads.contains(StorageWriteKey.encode_key(login_proof)))
        assertFalse(reads.contains(StorageWriteKey.encode_key(v1())))
        assertFalse(lookups.contains(StorageWriteKey.encode_key(v1())))
        assertEquals(setOf(StorageWriteKey.encode_key(data_kek)), reads - all_public_constants())
    }

    @Test
    fun `a retired unstretched key carried in the vault key list no longer opens rows`() {
        val earlier = ByteArray(32) { 9 }
        val store = signed_in_store(listOf(StorageWriteKey.encode_key(v1()), StorageWriteKey.encode_key(earlier)))
        val under_v1 = seal("old template".toByteArray(), v1())
        val older = seal("older".toByteArray(), earlier)

        assertEquals("old template", open_with_any(under_v1, StorageWriteKey.read_keys(store))?.toString(Charsets.UTF_8))

        assertTrue(retire(store))
        val reads = StorageWriteKey.read_keys(store)
        val lookups = StorageWriteKey.lookup_keys(store)

        assertNull(open_with_any(under_v1, reads))
        assertNull(open_with_any(under_v1, lookups))
        assertFalse(encoded(lookups).contains(StorageWriteKey.encode_key(v1())))
        assertEquals("older", open_with_any(older, reads)?.toString(Charsets.UTF_8))
        assertEquals(setOf(StorageWriteKey.encode_key(data_kek), StorageWriteKey.encode_key(earlier)), encoded(reads) - all_public_constants())
    }

    @Test
    fun `retirement keeps only hashes of the retired keys`() {
        val store = signed_in_store()
        assertTrue(retire(store))

        val hashes = store.retired_storage_key_hashes()
        val raw = setOf(StorageWriteKey.encode_key(v1()), StorageWriteKey.encode_key(login_proof))

        assertEquals(setOf(StorageWriteKey.retired_key_hash(v1()), StorageWriteKey.retired_key_hash(login_proof)), hashes.toSet())
        assertTrue(hashes.none { it in raw })
    }

    @Test
    fun `without the password the keys are not retired`() {
        val store = SessionKeyStore(null)
        store.put_data_kek(data_kek.copyOf())
        store.put(login_proof.copyOf())

        assertFalse(retire(store))
        assertFalse(store.legacy_storage_keys_retired())
        assertTrue(store.retired_storage_key_hashes().isEmpty())
    }

    @Test
    fun `a retired key stays refused when the password is not in memory`() {
        val earlier = ByteArray(32) { 9 }
        val hashes = StorageWriteKey.retirement_hashes(StorageWriteKey.sweep_keys(signed_in_store())!!)!!
        val store = SessionKeyStore(null)
        store.put_data_kek(data_kek.copyOf())
        store.put_legacy_keks(listOf(StorageWriteKey.encode_key(v1()), StorageWriteKey.encode_key(earlier)))
        store.retire_legacy_storage_keys(hashes)

        val reads = encoded(StorageWriteKey.read_keys(store))

        assertFalse(reads.contains(StorageWriteKey.encode_key(v1())))
        assertEquals(setOf(StorageWriteKey.encode_key(data_kek), StorageWriteKey.encode_key(earlier)), reads - all_public_constants())
    }

    @Test
    fun `an older retirement without hashes keeps every vault key it cannot identify`() {
        val earlier = ByteArray(32) { 9 }
        val store = SessionKeyStore(null)
        store.put_data_kek(data_kek.copyOf())
        store.put(login_proof.copyOf())
        store.put_legacy_keks(
            listOf(
                StorageWriteKey.encode_key(v1()),
                StorageWriteKey.encode_key(earlier),
                StorageWriteKey.encode_key(login_proof),
            ),
        )
        store.retire_legacy_storage_keys(emptyList())

        val locked_reads = encoded(StorageWriteKey.read_keys(store))
        assertTrue(locked_reads.contains(StorageWriteKey.encode_key(earlier)))
        assertFalse(locked_reads.contains(StorageWriteKey.encode_key(login_proof)))

        store.put_passphrase(passphrase.copyOf())
        val reads = encoded(StorageWriteKey.read_keys(store))

        assertEquals(setOf(StorageWriteKey.encode_key(data_kek), StorageWriteKey.encode_key(earlier)), reads - all_public_constants())
    }

    @Test
    fun `an older retirement without hashes records them once the password is known`() {
        val earlier = ByteArray(32) { 9 }
        val store = SessionKeyStore(null)
        store.put_data_kek(data_kek.copyOf())
        store.put(login_proof.copyOf())
        store.retire_legacy_storage_keys(emptyList())
        assertFalse(StorageWriteKey.backfill_retired_hashes(store))
        assertTrue(store.retired_storage_key_hashes().isEmpty())

        store.put_passphrase(passphrase.copyOf())
        assertEquals(emptyList<ByteArray>(), StorageWriteKey.sweep_keys(store))

        val expected = setOf(StorageWriteKey.retired_key_hash(v1()), StorageWriteKey.retired_key_hash(login_proof))
        assertEquals(expected, store.retired_storage_key_hashes().toSet())
        assertTrue(store.legacy_storage_keys_retired())
        assertFalse(StorageWriteKey.backfill_retired_hashes(store))

        store.put_passphrase("a different password".toByteArray(Charsets.UTF_8))
        store.put_legacy_keks(
            listOf(
                StorageWriteKey.encode_key(v1()),
                StorageWriteKey.encode_key(earlier),
                StorageWriteKey.encode_key(login_proof),
            ),
        )
        assertEquals(listOf(StorageWriteKey.encode_key(earlier)), store.get_legacy_keks())
        assertEquals(
            setOf(StorageWriteKey.encode_key(data_kek), StorageWriteKey.encode_key(earlier)),
            encoded(StorageWriteKey.read_keys(store)) - all_public_constants(),
        )
    }

    @Test
    fun `a backfill that cannot be saved is not repeated on every read`() {
        val store = io.mockk.spyk(SessionKeyStore(null))
        store.put_data_kek(data_kek.copyOf())
        store.put(login_proof.copyOf())
        store.retire_legacy_storage_keys(emptyList())
        store.put_passphrase(passphrase.copyOf())
        io.mockk.every { store.retire_legacy_storage_keys(match { it.isNotEmpty() }) } returns false

        val first = encoded(StorageWriteKey.read_keys(store))
        val second = encoded(StorageWriteKey.read_keys(store))
        assertNotNull(StorageWriteKey.sweep_keys(store))

        io.mockk.verify(exactly = 1) { store.retire_legacy_storage_keys(match { it.isNotEmpty() }) }
        assertEquals(setOf(StorageWriteKey.encode_key(data_kek)), first - all_public_constants())
        assertEquals(first, second)
    }

    @Test
    fun `reading keys after an older retirement records the hashes`() {
        val store = SessionKeyStore(null)
        store.put_data_kek(data_kek.copyOf())
        store.put(login_proof.copyOf())
        store.retire_legacy_storage_keys(emptyList())
        store.put_passphrase(passphrase.copyOf())

        StorageWriteKey.read_keys(store)

        assertTrue(StorageWriteKey.retired_key_hash(v1()) in store.retired_storage_key_hashes())
    }

    @Test
    fun `retirement hashes the keys the sweep used even after the password changes`() {
        val store = signed_in_store()
        val swept = StorageWriteKey.sweep_keys(store)!!
        store.put_passphrase("a different password".toByteArray(Charsets.UTF_8))
        assertTrue(StorageWriteKey.retire_legacy_keys(store, swept))
        store.put_legacy_keks(listOf(StorageWriteKey.encode_key(v1())))

        assertFalse(encoded(StorageWriteKey.read_keys(store)).contains(StorageWriteKey.encode_key(v1())))
        assertTrue(store.get_legacy_keks().orEmpty().isEmpty())
        assertTrue(store.get_decrypt_keks().isEmpty())
    }

    @Test
    fun `a sweep without the password waits instead of retiring`() {
        val store = SessionKeyStore(null)
        store.put(login_proof.copyOf())

        assertNull(StorageWriteKey.sweep_keys(store))
        assertFalse(StorageWriteKey.retire_legacy_keys(store, emptyList()))
        assertFalse(store.legacy_storage_keys_retired())
    }

    @Test
    fun `password keys left in the vault skip a retired key`() {
        val other = ByteArray(32) { 3 }
        val retired = listOf(StorageWriteKey.retired_key_hash(v1()))
        val kept = StorageWriteKey.not_retired(listOf(v1(), other.copyOf()), retired)

        assertEquals(setOf(StorageWriteKey.encode_key(other)), encoded(kept))
        assertEquals(2, StorageWriteKey.not_retired(listOf(v1(), other.copyOf()), emptyList()).size)
    }

    @Test
    fun `dropping retired keys keeps every other key`() {
        val keep = ByteArray(32) { 1 }
        val drop = ByteArray(32) { 2 }
        val out = StorageWriteKey.drop_retired_keys(listOf(keep.copyOf(), drop.copyOf()), listOf(drop.copyOf()))

        assertEquals(1, out.size)
        assertArrayEquals(keep, out[0])
        assertTrue(StorageWriteKey.drop_retired_keys(emptyList(), listOf(drop)).isEmpty())
    }

    @Test
    fun `a server forged row under the login proof is refused after retirement`() {
        val store = signed_in_store()
        val forged = seal("injected contact".toByteArray(), login_proof)
        assertTrue(retire(store))

        assertNull(open_with_any(forged, StorageWriteKey.read_keys(store)))
        assertNull(open_with_any(forged, StorageWriteKey.lookup_keys(store)))
    }

    @Test
    fun `a retired account still reads rows under its data key and earlier vault keys`() {
        val earlier = ByteArray(32) { 9 }
        val store = signed_in_store(listOf(StorageWriteKey.encode_key(earlier)))
        val current = seal("current".toByteArray(), data_kek)
        val older = seal("older".toByteArray(), earlier)
        assertTrue(retire(store))

        val keys = StorageWriteKey.read_keys(store)

        assertEquals("current", open_with_any(current, keys)?.toString(Charsets.UTF_8))
        assertEquals("older", open_with_any(older, keys)?.toString(Charsets.UTF_8))
    }

    @Test
    fun `signing out without an account id forgets the retirement`() {
        val store = signed_in_store()
        assertTrue(retire(store))

        store.clear()

        assertFalse(store.legacy_storage_keys_retired())
        assertTrue(store.retired_storage_key_hashes().isEmpty())
    }

    @Test
    fun `signing out and back in keeps the retirement for that account`() {
        val store = signed_in_store()
        store.put_user_id("account-a")
        assertTrue(retire(store))
        val hashes = store.retired_storage_key_hashes().toSet()

        store.clear()
        store.put_passphrase(passphrase.copyOf())
        store.put_data_kek(data_kek.copyOf())
        store.put(login_proof.copyOf())
        store.put_user_id("account-a")

        assertTrue(store.legacy_storage_keys_retired())
        assertEquals(hashes, store.retired_storage_key_hashes().toSet())
        assertTrue(StorageWriteKey.legacy_read_keys(store).isEmpty())
        assertEquals(emptyList<ByteArray>(), StorageWriteKey.sweep_keys(store))
        assertNull(StorageWriteKey.unverified_login_proof_key(store))
        assertFalse(encoded(StorageWriteKey.read_keys(store)).contains(StorageWriteKey.encode_key(v1())))
    }

    @Test
    fun `a retirement for one account does not retire another`() {
        val store = signed_in_store()
        store.put_user_id("account-a")
        assertTrue(retire(store))

        store.clear()
        store.put_passphrase(passphrase.copyOf())
        store.put(login_proof.copyOf())
        store.put_user_id("account-b")

        assertFalse(store.legacy_storage_keys_retired())
        assertTrue(store.retired_storage_key_hashes().isEmpty())
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

        val constants = StorageWriteKey.public_mail_kek_raws().size
        assertEquals(3 + constants, keys.size)
        assertArrayEquals(write, keys[0])
        assertArrayEquals(legacy, keys[1])
        assertArrayEquals(password, keys[2])
        assertTrue(keys.drop(3).all { StorageWriteKey.is_public_mail_kek(it) })
    }

    @Test
    fun `lookup keys keep the constants as a read only fallback for old hashes`() {
        val write = ByteArray(32) { 1 }
        val constant = StorageWriteKey.public_mail_kek_raws().first()
        val old_hash = hmac_hex(constant, "token")

        val keys = StorageWriteKey.lookup_keys(write.copyOf(), emptyList())

        assertTrue(keys.any { old_hash == hmac_hex(it, "token") })
        assertEquals(false, StorageWriteKey.is_public_mail_kek(keys.first()))
        val fresh = hmac_hex(keys.first(), "token")
        assertEquals(false, fresh == old_hash)
    }

    private fun hmac_hex(key: ByteArray, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
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

    private val public_mail_kek = MessageDigest.getInstance("SHA-256")
        .digest("undefinedastermail-envelope-v1".toByteArray(Charsets.UTF_8))

    private val public_import_kek = MessageDigest.getInstance("SHA-256")
        .digest("undefinedastermail-import-v1".toByteArray(Charsets.UTF_8))

    @Test
    fun `stored data keys leave out the public mail constants`() {
        val trusted = ByteArray(32) { (it + 40).toByte() }
        val planted = seal("planted".toByteArray(Charsets.UTF_8), public_mail_kek)
        val keys = StorageWriteKey.lookup_keys(
            null,
            listOf(
                Base64.getEncoder().encodeToString(public_mail_kek),
                Base64.getEncoder().encodeToString(public_import_kek),
                Base64.getEncoder().encodeToString(trusted),
            ),
        )

        val real = keys.filterNot { StorageWriteKey.is_public_mail_kek(it) }
        assertEquals(1, real.size)
        assertArrayEquals(trusted, real.single())
        assertNull("a value sealed under a public constant must not open", open_with_any(planted, real))
        assertTrue(StorageWriteKey.is_public_mail_kek(keys.last()))
    }

    private fun all_public_constants(): Set<String> =
        StorageWriteKey.UNSET_IDENTITY_MATERIALS
            .flatMap { material -> StorageWriteKey.PUBLIC_CONSTANT_CONTEXTS.map { material + it } }
            .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8)) }
            .map { Base64.getEncoder().encodeToString(it) }
            .toSet()

    @Test
    fun `read keys try the real keys first and still open data sealed under a public constant`() {
        val legacy = seal("legacy mail".toByteArray(Charsets.UTF_8), public_mail_kek)
        val planted_order = listOf(
            Base64.getEncoder().encodeToString(public_mail_kek),
            Base64.getEncoder().encodeToString(public_import_kek),
        )

        val keys = StorageWriteKey.read_keys(store(legacy_keks = planted_order))

        assertArrayEquals(data_kek, keys.first())
        assertEquals("legacy mail", open_with_any(legacy, keys)?.toString(Charsets.UTF_8))
        val encoded_keys = keys.map(StorageWriteKey::encode_key)
        assertTrue(encoded_keys.indexOf(Base64.getEncoder().encodeToString(public_mail_kek)) > 0)
    }

    @Test
    fun `read keys open a constant sealed item even when the vault list omits it`() {
        val legacy = seal("legacy mail".toByteArray(Charsets.UTF_8), public_import_kek)

        val keys = StorageWriteKey.read_keys(store())

        assertEquals("legacy mail", open_with_any(legacy, keys)?.toString(Charsets.UTF_8))
    }

    @Test
    fun `level two reads try real keys before the public mail constants`() {
        val store = mockk<SessionKeyStore>()
        every { store.get_data_kek() } returns null
        every { store.get_decrypt_keks() } returns listOf(
            Base64.getEncoder().encodeToString(public_mail_kek),
            Base64.getEncoder().encodeToString(data_kek),
        )

        val keys = LevelTwoEnvelope.read_keys(store)

        assertArrayEquals(data_kek, keys.first())
        assertEquals(1, keys.count { it.contentEquals(public_mail_kek) })
        assertTrue(keys.indexOfFirst { it.contentEquals(public_mail_kek) } > 0)
    }

    @Test
    fun `resealing never treats a public mail constant as an older key`() {
        val view = VaultKeyView(
            data_kek = null,
            data_kek_random = true,
            storage_rekey_state = null,
            prior_keys = listOf(Base64.getEncoder().encodeToString(public_import_kek)),
            legacy_keks = listOf(
                Base64.getEncoder().encodeToString(public_mail_kek),
                Base64.getEncoder().encodeToString(data_kek),
            ),
        )

        val old = collect_old_storage_raws(view)

        val encoded_old = old.map(StorageWriteKey::encode_key)
        assertEquals(encoded_old.size, encoded_old.toSet().size)
        assertArrayEquals(data_kek, old.first())
        assertEquals(setOf(StorageWriteKey.encode_key(data_kek)), encoded_old.toSet())
        assertTrue(old.none { StorageWriteKey.is_public_mail_kek(it) })
    }

    @Test
    fun `a row under the login proof is never resealed under the new key`() {
        val new_key = ByteArray(32) { (it + 90).toByte() }
        val view = VaultKeyView(
            data_kek = Base64.getEncoder().encodeToString(new_key),
            data_kek_random = true,
            storage_rekey_state = null,
            prior_keys = listOf(Base64.getEncoder().encodeToString(login_proof)),
            legacy_keks = listOf(Base64.getEncoder().encodeToString(data_kek)),
        )
        val under_proof = gcm_seal(login_proof, "server row".toByteArray(Charsets.UTF_8))
        val under_old = gcm_seal(data_kek, "own row".toByteArray(Charsets.UTF_8))

        val old = collect_old_storage_raws(view, listOf(login_proof.copyOf()), listOf(login_proof.copyOf()))
        assertEquals(setOf(StorageWriteKey.encode_key(data_kek)), old.map(StorageWriteKey::encode_key).toSet())

        val reseal_keys = build_reseal_keys(view, listOf(login_proof.copyOf()), listOf(login_proof.copyOf()))!!
        assertNull(open_under_old(reseal_keys, under_proof.ciphertext, under_proof.nonce))
        assertNotNull(open_under_old(reseal_keys, under_old.ciphertext, under_old.nonce))
    }

    @Test
    fun `a legacy item sealed under a public constant is left out of the reseal but stays readable`() {
        val new_key = ByteArray(32) { (it + 90).toByte() }
        val view = VaultKeyView(
            data_kek = Base64.getEncoder().encodeToString(new_key),
            data_kek_random = true,
            storage_rekey_state = null,
            prior_keys = emptyList(),
            legacy_keks = listOf(Base64.getEncoder().encodeToString(public_mail_kek)),
        )
        val legacy = gcm_seal(public_mail_kek, "legacy item".toByteArray(Charsets.UTF_8))
        val reseal_keys = build_reseal_keys(view)!!

        assertTrue(!opens_under_new(reseal_keys, legacy.ciphertext, legacy.nonce))
        assertNull(open_under_old(reseal_keys, legacy.ciphertext, legacy.nonce))

        val readers = StorageWriteKey.lookup_keys(
            new_key,
            listOf(Base64.getEncoder().encodeToString(public_mail_kek)),
        )
        val ciphertext = Base64.getDecoder().decode(legacy.ciphertext)
        val nonce = Base64.getDecoder().decode(legacy.nonce)
        assertArrayEquals(
            "legacy item".toByteArray(Charsets.UTF_8),
            readers.firstNotNullOfOrNull { gcm_open(it, ciphertext, nonce) },
        )
    }

    @Test
    fun `an ordinary older key still opens stored data`() {
        val sealed = seal("kept".toByteArray(Charsets.UTF_8), data_kek)
        val keys = StorageWriteKey.lookup_keys(null, listOf(Base64.getEncoder().encodeToString(data_kek)))

        assertArrayEquals("kept".toByteArray(Charsets.UTF_8), open_with_any(sealed, keys))
    }
}
