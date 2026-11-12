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

package org.astermail.android.mail.ratchet

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ratchet.AddressBundleUpload
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.AddressIdentity
import org.astermail.android.crypto.ratchet.AddressKeySource
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddressIdentityDirectoryTest {

    private val primary = "owner@astermail.org"
    private val alias = "Owner.Alias@aster.cx"
    private val peer_alias = "peer.side@aster.cx"
    private val conversation_id = "conv-1"

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var ratchet_api: RatchetApi
    private lateinit var identity_pins: RatchetIdentityPinStore
    private lateinit var own_source: AddressKeySource
    private lateinit var peer_source: AddressKeySource
    private lateinit var peer_previous_jwk: String
    private var clock = 1_000_000L
    private var pinned: String? = null
    private var rooted = false
    private var owner_pin: String? = null

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }
        every { Base64.decode(any<String>(), any()) } answers {
            val value = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                var padded = value
                val mod = padded.length % 4
                if (mod != 0) padded += "=".repeat(4 - mod)
                java.util.Base64.getUrlDecoder().decode(padded)
            } else {
                java.util.Base64.getDecoder().decode(value)
            }
        }
        AddressIdentity.clear_cache()

        own_source = new_source()
        peer_source = new_source()
        peer_previous_jwk = to_private_jwk(RatchetCrypto.generate_p256_keypair())

        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_user_email() } returns primary
        every { session_key_store.get_ratchet_identity_jwk() } returns own_source.identity_jwk
        every { session_key_store.get_ratchet_identity_public_b64() } returns own_source.identity_public
        every { session_key_store.get_ratchet_signed_prekey_jwk() } returns own_source.signed_prekey_jwk
        every { session_key_store.get_ratchet_signed_prekey_public_b64() } returns own_source.signed_prekey_public
        every { session_key_store.get_ratchet_pq_identity_secret() } returns null
        every { session_key_store.get_ratchet_pq_identity_public() } returns null
        every { session_key_store.get_ratchet_previous_keys_json() } returns null

        ratchet_api = mockk(relaxed = true)
        coEvery { ratchet_api.upload_address_bundle(any()) } returns AddressBundleUpload.Accepted(active = true)

        identity_pins = mockk(relaxed = true)
        every { identity_pins.fingerprint_of(any()) } answers { "fp:" + firstArg<String>() }
        every { identity_pins.pinned_fingerprint(any()) } answers { pinned }
        every { identity_pins.is_address_rooted(any()) } answers { rooted }
        every { identity_pins.owner_key_pin(any()) } answers { owner_pin }
    }

    @After
    fun teardown() {
        AddressIdentity.clear_cache()
        unmockkStatic(Base64::class)
    }

    private fun to_private_jwk(kp: RatchetCrypto.EcKeyPair): String {
        val d = RatchetCrypto.private_to_raw_d(kp.private_key)
        val x = kp.public_raw.copyOfRange(1, 33)
        val y = kp.public_raw.copyOfRange(33, 65)
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"${RatchetCrypto.b64url_encode(x)}\",\"y\":\"${RatchetCrypto.b64url_encode(y)}\",\"d\":\"${RatchetCrypto.b64url_encode(d)}\"}"
    }

    private fun new_source(): AddressKeySource {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        return AddressKeySource(
            identity_jwk = to_private_jwk(identity),
            identity_public = RatchetCrypto.b64_encode(identity.public_raw),
            signed_prekey_jwk = to_private_jwk(spk),
            signed_prekey_public = RatchetCrypto.b64_encode(spk.public_raw),
        )
    }

    private fun new_directory(): AddressIdentityDirectory =
        AddressIdentityDirectory(session_key_store, ratchet_api, identity_pins).also { it.now_ms = { clock } }

    private fun peer_entry(previous: List<String> = emptyList()): PrekeyBundleResponse {
        val entry = checkNotNull(AddressIdentity.sign_entry(peer_source, previous, peer_alias))
        return PrekeyBundleResponse(
            user_id = "u_peer",
            kem_identity_key = entry.kem_identity_key,
            signed_prekey = entry.signed_prekey,
            signed_prekey_signature = entry.signed_prekey_signature,
            pq_kem_public_key = entry.pq_kem_public_key,
            address_scoped = true,
        )
    }

    private fun peer_account(scoped: Boolean? = null): PrekeyBundleResponse = PrekeyBundleResponse(
        user_id = "u_peer",
        kem_identity_key = peer_source.identity_public,
        signed_prekey = peer_source.signed_prekey_public,
        signed_prekey_signature = "",
        address_scoped = scoped,
    )

    @Test
    fun `primary address signs with the account identity`() = runTest {
        val identity = new_directory().resolve_sender_identity(primary)
        assertEquals(own_source.identity_public, identity?.identity_public)
        coVerify(exactly = 0) { ratchet_api.upload_address_bundle(any()) }
    }

    @Test
    fun `active alias signs with the derived identity and caches the state`() = runTest {
        val directory = new_directory()
        val identity = directory.resolve_sender_identity(alias)
        val expected = AddressIdentity.derive_sender_identity(own_source.identity_jwk, alias)
        assertNotNull(identity)
        assertEquals(expected?.identity_public, identity?.identity_public)
        assertNotEquals(own_source.identity_public, identity?.identity_public)
        assertEquals(identity?.identity_public, directory.own_address_identity(alias, identity!!))
        directory.resolve_sender_identity(alias)
        coVerify(exactly = 2) { ratchet_api.upload_address_bundle(any()) }
    }

    @Test
    fun `inactive alias falls back to the account identity`() = runTest {
        coEvery { ratchet_api.upload_address_bundle(any()) } returns AddressBundleUpload.Accepted(active = false)
        val directory = new_directory()
        val identity = directory.resolve_sender_identity(alias)
        assertEquals(own_source.identity_public, identity?.identity_public)
        assertNull(directory.own_address_identity(alias, identity!!))
    }

    @Test
    fun `unsupported server keeps the account identity and stops uploading`() = runTest {
        coEvery { ratchet_api.upload_address_bundle(any()) } returns AddressBundleUpload.Unsupported
        val directory = new_directory()
        assertEquals(own_source.identity_public, directory.resolve_sender_identity(alias)?.identity_public)
        directory.publish_address_entries(listOf("other@aster.cx", "third@aster.cx"))
        coVerify(exactly = 1) { ratchet_api.upload_address_bundle(any()) }
        clock += AddressIdentityDirectory.INACTIVE_RETRY_MS + 1
        directory.resolve_sender_identity(alias)
        coVerify(exactly = 2) { ratchet_api.upload_address_bundle(any()) }
    }

    @Test
    fun `failed upload refuses to pick an identity and retries after a minute`() = runTest {
        coEvery { ratchet_api.upload_address_bundle(any()) } returns AddressBundleUpload.Failed
        val directory = new_directory()
        assertNull(directory.resolve_sender_identity(alias))
        assertEquals(AddressEntryState.FAILED, directory.address_entry_state(alias))
        coEvery { ratchet_api.upload_address_bundle(any()) } returns AddressBundleUpload.Accepted(active = true)
        clock += AddressIdentityDirectory.FAILED_RETRY_MS + 1
        assertEquals(AddressEntryState.ACTIVE, directory.address_entry_state(alias))
    }

    @Test
    fun `publishing skips the primary address`() = runTest {
        new_directory().publish_address_entries(listOf(primary, " OWNER@astermail.org "))
        coVerify(exactly = 0) { ratchet_api.upload_address_bundle(any()) }
    }

    @Test
    fun `unpinned scoped entry is trusted as an entry`() = runTest {
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns peer_entry()
        val resolved = new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id)
        assertEquals(DirectoryTrust.ENTRY, resolved?.trust)
        assertTrue(resolved!!.scoped)
    }

    @Test
    fun `entry endorsed by the pinned earlier key is accepted as a rotation`() = runTest {
        val earlier = checkNotNull(AddressIdentity.derive_sender_identity(peer_previous_jwk, peer_alias))
        pinned = "fp:" + earlier.identity_public
        rooted = true
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns peer_entry(listOf(peer_previous_jwk))
        val resolved = new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id)
        assertEquals(DirectoryTrust.ENDORSED, resolved?.trust)
    }

    @Test
    fun `unendorsed replacement of a rooted pin is rejected`() = runTest {
        pinned = "fp:someone-else"
        rooted = true
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns peer_entry()
        val resolved = new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id)
        assertEquals(DirectoryTrust.REPLACED, resolved?.trust)
        assertTrue(is_directory_trust_rejected(resolved!!.trust))
    }

    @Test
    fun `unrooted pin mismatch falls back to the account bundle`() = runTest {
        pinned = "fp:account-key"
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns peer_entry()
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns peer_account()
        val resolved = new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id)
        assertEquals(DirectoryTrust.ACCOUNT, resolved?.trust)
        assertFalse(resolved!!.scoped)
        assertEquals(peer_source.identity_public, resolved.bundle.kem_identity_key)
    }

    @Test
    fun `fallback refuses an account lookup that is still scoped`() = runTest {
        pinned = "fp:account-key"
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns peer_entry()
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns peer_account(scoped = true)
        assertNull(new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id))
    }

    @Test
    fun `badly signed entry is invalid once the address is rooted`() = runTest {
        rooted = true
        pinned = "fp:x"
        val forged = peer_entry().copy(signed_prekey = own_source.signed_prekey_public)
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns forged
        val resolved = new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id)
        assertEquals(DirectoryTrust.INVALID, resolved?.trust)
    }

    @Test
    fun `account bundle for a rooted address is a downgrade`() = runTest {
        rooted = true
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns peer_account()
        val resolved = new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id)
        assertEquals(DirectoryTrust.DOWNGRADED, resolved?.trust)
    }

    @Test
    fun `server without scoped lookups keeps account trust`() = runTest {
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns null
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns peer_account()
        val resolved = new_directory().resolve_recipient_bundle("peer", peer_alias, conversation_id)
        assertEquals(DirectoryTrust.ACCOUNT, resolved?.trust)
    }

    @Test
    fun `own derived identity is recognized for every address form`() = runTest {
        val directory = new_directory()
        val derived = checkNotNull(AddressIdentity.derive_sender_identity(own_source.identity_jwk, alias))
        assertTrue(directory.is_own_address_identity_key(listOf(alias), derived.identity_public))
        assertFalse(directory.is_own_address_identity_key(listOf(alias), peer_source.identity_public))
        val sets = directory.receiver_key_sets(alias)
        assertTrue(sets.any { it.identity_public == derived.identity_public })
    }
}
