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
import io.mockk.unmockkStatic
import org.astermail.android.crypto.ratchet.AddressIdentity
import org.astermail.android.crypto.ratchet.AddressKeySource
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddressIdentityVectorsTest {

    private lateinit var vectors: JSONObject

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            val text = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlDecoder().decode(text.trimEnd('='))
            } else {
                java.util.Base64.getDecoder().decode(text)
            }
        }
        every { android.util.Base64.encodeToString(any<ByteArray>(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }
        AddressIdentity.clear_cache()
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("address_identity_vectors.json"),
        )
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        AddressIdentity.clear_cache()
        unmockkStatic(android.util.Base64::class)
    }

    private fun fixed_bytes(fill: Int, length: Int): ByteArray =
        ByteArray(length) { i -> ((fill + i * 7) and 0xff).toByte() }

    private fun fixed_key(fill: Int): Pair<String, String> {
        val scalar = fixed_bytes(fill, 32)
        return AddressIdentity.jwk_for_scalar(scalar) to AddressIdentity.public_for_scalar(scalar)
    }

    private fun current_source(): AddressKeySource {
        val identity = fixed_key(11)
        val signed_prekey = fixed_key(23)
        return AddressKeySource(
            identity_jwk = identity.first,
            identity_public = identity.second,
            signed_prekey_jwk = signed_prekey.first,
            signed_prekey_public = signed_prekey.second,
            pq_identity_seed = java.util.Base64.getEncoder().encodeToString(fixed_bytes(53, 64)),
        )
    }

    private fun previous_source(): AddressKeySource {
        val identity = fixed_key(37)
        val signed_prekey = fixed_key(41)
        return AddressKeySource(
            identity_jwk = identity.first,
            identity_public = identity.second,
            signed_prekey_jwk = signed_prekey.first,
            signed_prekey_public = signed_prekey.second,
        )
    }

    @Test
    fun fixed_vault_matches_the_published_vault() {
        val vault = vectors.getJSONObject("vault")
        val current = current_source()
        val previous = vault.getJSONArray("ratchet_previous_keys").getJSONObject(0)
        assertEquals(vault.getString("ratchet_identity_key"), current.identity_jwk)
        assertEquals(vault.getString("ratchet_identity_public"), current.identity_public)
        assertEquals(vault.getString("ratchet_signed_prekey"), current.signed_prekey_jwk)
        assertEquals(vault.getString("ratchet_signed_prekey_public"), current.signed_prekey_public)
        assertEquals(vault.getString("ratchet_pq_identity_seed"), current.pq_identity_seed)
        assertEquals(previous.getString("ratchet_identity_key"), previous_source().identity_jwk)
        assertEquals(previous.getString("ratchet_signed_prekey_public"), previous_source().signed_prekey_public)
    }

    @Test
    fun derives_the_published_address_forms() {
        val expected = vectors.getJSONArray("forms").let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        assertEquals(expected, AddressIdentity.entry_forms(vectors.getString("address")))
        assertEquals(listOf("jodoe@example.org"), AddressIdentity.entry_forms(" JoDoe@Example.org "))
        assertEquals(emptyList<String>(), AddressIdentity.entry_forms("@example.org"))
        assertEquals(emptyList<String>(), AddressIdentity.entry_forms("jo@"))
        assertEquals(emptyList<String>(), AddressIdentity.entry_forms("+tag@example.org"))
    }

    @Test
    fun derives_the_same_key_sets_for_every_form() {
        val derived = vectors.getJSONArray("derived")
        for (index in 0 until derived.length()) {
            val vector = derived.getJSONObject(index)
            val set = assertNotNullSet(AddressIdentity.derive_key_set(current_source(), vector.getString("form")))
            assertEquals(vector.getString("identity_jwk"), set.identity_jwk)
            assertEquals(vector.getString("identity_public"), set.identity_public)
            assertEquals(vector.getString("signed_prekey_jwk"), set.signed_prekey_jwk)
            assertEquals(vector.getString("signed_prekey_public"), set.signed_prekey_public)
            val pq_public = set.pq_identity_public.orEmpty()
            assertEquals(vector.getString("pq_public_sha_prefix"), pq_public.take(64))
            assertEquals(vector.getInt("pq_public_length"), pq_public.length)
        }
    }

    private fun assertNotNullSet(value: org.astermail.android.crypto.ratchet.AddressKeySet?) =
        checkNotNull(value)

    @Test
    fun signs_the_entry_deterministically_and_verifies_it() {
        val expected = vectors.getJSONObject("entry")
        val forms = vectors.getJSONArray("forms")
        val entry = AddressIdentity.sign_entry(
            current_source(),
            listOf(previous_source().identity_jwk),
            forms.getString(0),
        )
        assertNotNull(entry)
        entry!!
        assertEquals(expected.getString("address"), entry.address)
        assertEquals(expected.getString("kem_identity_key"), entry.kem_identity_key)
        assertEquals(expected.getString("signed_prekey"), entry.signed_prekey)
        assertEquals(expected.getString("pq_kem_public_key"), entry.pq_kem_public_key)
        assertEquals(expected.getString("signed_prekey_signature"), entry.signed_prekey_signature)

        val verification = AddressIdentity.verify_entry(
            vectors.getString("address"),
            entry.kem_identity_key,
            entry.signed_prekey,
            entry.signed_prekey_signature,
            entry.pq_kem_public_key,
        )
        val expected_verification = vectors.getJSONObject("verification")
        assertEquals(expected_verification.getBoolean("valid"), verification.valid)
        val endorsers = expected_verification.getJSONArray("endorsers")
        assertEquals((0 until endorsers.length()).map { endorsers.getString(it) }, verification.endorsers)
    }

    @Test
    fun rejects_an_entry_for_another_address() {
        val entry = vectors.getJSONObject("entry")
        val verification = AddressIdentity.verify_entry(
            "someone.else@example.org",
            entry.getString("kem_identity_key"),
            entry.getString("signed_prekey"),
            entry.getString("signed_prekey_signature"),
            entry.getString("pq_kem_public_key"),
        )
        assertFalse(verification.valid)
    }

    @Test
    fun rejects_an_entry_with_a_swapped_post_quantum_key() {
        val entry = vectors.getJSONObject("entry")
        val swapped = AddressIdentity.derive_key_set(current_source(), "jodoe@example.org")!!.pq_identity_public
        val verification = AddressIdentity.verify_entry(
            vectors.getString("address"),
            entry.getString("kem_identity_key"),
            entry.getString("signed_prekey"),
            entry.getString("signed_prekey_signature"),
            swapped,
        )
        assertFalse(verification.valid)
    }

    @Test
    fun recognizes_own_address_identity_keys_from_previous_account_keys() {
        val address = vectors.getString("address")
        val derived = vectors.getJSONArray("derived").getJSONObject(0)
        assertTrue(
            AddressIdentity.is_own_address_identity_key(
                listOf(current_source().identity_jwk, previous_source().identity_jwk),
                listOf(address),
                derived.getString("identity_public"),
            ),
        )
        assertFalse(
            AddressIdentity.is_own_address_identity_key(
                listOf(current_source().identity_jwk),
                listOf("someone.else@example.org"),
                derived.getString("identity_public"),
            ),
        )
        val endorser = vectors.getJSONObject("verification").getJSONArray("endorsers").getString(0)
        assertTrue(
            AddressIdentity.is_own_address_identity_key(
                listOf(previous_source().identity_jwk),
                listOf(address),
                endorser,
            ),
        )
    }

    @Test
    fun receiver_key_sets_cover_current_and_previous_keys_for_every_form() {
        val sets = AddressIdentity.receiver_key_sets(
            current_source(),
            listOf(previous_source()),
            vectors.getString("address"),
        )
        assertEquals(4, sets.size)
        val derived = vectors.getJSONArray("derived")
        assertEquals(derived.getJSONObject(0).getString("identity_public"), sets[0].identity_public)
        assertEquals(derived.getJSONObject(1).getString("identity_public"), sets[1].identity_public)
        assertNotEquals(sets[0].identity_public, sets[2].identity_public)
        assertEquals(null, sets[2].pq_identity_public)
    }
}
