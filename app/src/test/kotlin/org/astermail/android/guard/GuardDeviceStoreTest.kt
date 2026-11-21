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

package org.astermail.android.guard

import java.util.UUID
import org.astermail.android.storage.InMemoryPrefs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GuardDeviceStoreTest {
    private val user = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val other = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    private val device = UUID.fromString("a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6")
    private val keys = generate_box_keys()
    private val secret = ByteArray(20) { (it * 7).toByte() }

    private fun save(store: GuardDeviceStore) = store.save_enrollment(
        user, device, secret, keys.x25519_sk, keys.mlkem_sk, keys.x25519_pk, keys.mlkem_pk, keys.ed25519_pk, keys.ed25519_sk,
    )

    @Test
    fun generated_keys_have_wire_sizes() {
        assertEquals(32, keys.x25519_sk.size)
        assertEquals(32, keys.x25519_pk.size)
        assertEquals(1184, keys.mlkem_pk.size)
        assertEquals(32, keys.ed25519_pk.size)
        assertEquals(32, keys.ed25519_sk!!.size)
    }

    @Test
    fun enrollment_round_trips() {
        val store = GuardDeviceStore(InMemoryPrefs())
        save(store)
        assertEquals(device, store.device_id(user))
        assertArrayEquals(secret, store.code_secret(user))
        assertEquals(keys, store.box_keys(user))
        assertNull(store.device_id(other))
    }

    @Test
    fun clear_removes_every_field() {
        val prefs = InMemoryPrefs()
        val store = GuardDeviceStore(prefs)
        save(store)
        store.clear(user)
        assertNull(store.device_id(user))
        assertNull(store.code_secret(user))
        assertNull(store.box_keys(user))
        assertEquals(0, prefs.all.size)
    }

    @Test
    fun pending_sign_in_round_trips_and_clears() {
        val store = GuardDeviceStore(InMemoryPrefs())
        val pending = guard_pending_sign_in("r1", "t1", 42, "2026-11-15T10:00:00Z", keys, "u1")
        store.save_pending_sign_in(pending)
        val restored = store.pending_sign_in()
        assertNotNull(restored)
        assertEquals(pending, restored)
        store.clear_pending_sign_in()
        assertNull(store.pending_sign_in())
    }

    @Test
    fun corrupt_record_reads_as_absent() {
        val prefs = InMemoryPrefs()
        prefs.edit().putString("enr_$user", "{").commit()
        assertNull(GuardDeviceStore(prefs).device_id(user))
    }

    private fun save_for(store: GuardDeviceStore, id: UUID, alias: String?) = store.save_enrollment(
        id, device, secret, keys.x25519_sk, keys.mlkem_sk, keys.x25519_pk, keys.mlkem_pk, keys.ed25519_pk, keys.ed25519_sk, alias,
    )

    @Test
    fun each_account_keeps_its_own_alias() {
        val store = GuardDeviceStore(InMemoryPrefs())
        save_for(store, user, "alias_a")
        save_for(store, other, "alias_b")
        assertEquals("alias_a", store.action_alias(user, user))
        assertEquals("alias_b", store.action_alias(other, user))
        assertEquals(setOf(user, other), store.enrolled_users().toSet())
    }

    @Test
    fun legacy_alias_goes_to_the_current_account_once() {
        val store = GuardDeviceStore(InMemoryPrefs())
        save_for(store, user, null)
        save_for(store, other, null)
        assertEquals(GuardActionKey.default_alias(other), store.action_alias(other, user))
        assertNull(store.stored_action_alias(other))
        assertEquals(GuardActionKey.LEGACY_ALIAS, store.action_alias(user, user))
        assertEquals(GuardActionKey.LEGACY_ALIAS, store.stored_action_alias(user))
        assertEquals(GuardActionKey.LEGACY_ALIAS, store.action_alias(user, other))
        assertEquals(GuardActionKey.default_alias(other), store.action_alias(other, other))
    }

    @Test
    fun clear_returns_the_alias_and_leaves_other_accounts() {
        val store = GuardDeviceStore(InMemoryPrefs())
        save_for(store, user, "alias_a")
        save_for(store, other, "alias_b")
        assertEquals("alias_a", store.clear(user))
        assertNull(store.device_id(user))
        assertEquals(device, store.device_id(other))
        assertEquals(listOf(other), store.enrolled_users())
        assertNull(store.clear(user))
    }

    @Test
    fun unenrolled_account_gets_its_default_alias() {
        val store = GuardDeviceStore(InMemoryPrefs())
        assertEquals(GuardActionKey.default_alias(user), store.action_alias(user, user))
        assertNotEquals(GuardActionKey.default_alias(user), GuardActionKey.default_alias(other))
    }
}
