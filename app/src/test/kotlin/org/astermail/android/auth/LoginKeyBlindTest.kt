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

package org.astermail.android.auth

import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.storage.InMemoryPrefs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginKeyBlindTest {

    private class PlainPrefs(private val inner: InMemoryPrefs = InMemoryPrefs()) : SharedPreferences by inner

    private val secret = ByteArray(32) { (it + 3).toByte() }
    private val other_secret = ByteArray(32) { (it + 90).toByte() }
    private val salt = ByteArray(16) { (it + 1).toByte() }
    private val user_id = "0f8b2a6c-1d3e-4f5a-9b7c-8d6e5f4a3b2c"
    private val email_hash = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest("someone@example.com".toByteArray(Charsets.UTF_8)),
    )
    private val hash_pin = LoginProof.user_hash_pin_key(email_hash)
    private val id_pin = LoginProof.user_id_pin_key(user_id)

    private fun stored(prefs: SharedPreferences): String =
        prefs.all.values.joinToString("\n") { it.toString() }

    private fun legacy_prefs(): SharedPreferences {
        val prefs = PlainPrefs()
        prefs.edit()
            .putString("pinned_accounts", LoginProof.serialize_pins(listOf(id_pin, hash_pin)))
            .putString("login_v3_accounts", LoginProof.serialize_pins(listOf(hash_pin)))
            .putString("login_v3_owners", "$user_id=$hash_pin")
            .putString(
                "login_salt_records",
                LoginSaltRule.serialize_records(listOf(LoginSaltRecord(email_hash, LoginSaltRule.encode_salt(salt)))),
            )
            .commit()
        return prefs
    }

    @Test
    fun records_written_by_older_versions_keep_working_and_lose_the_email_hash() {
        val prefs = legacy_prefs()
        assertTrue(stored(prefs).contains(email_hash))
        val store = LoginProofPinStore(prefs) { secret }
        assertTrue(store.is_pinned(listOf(hash_pin)))
        assertTrue(store.is_pinned(listOf(id_pin)))
        assertTrue(store.is_v3_pinned(listOf(hash_pin)))
        assertArrayEquals(salt, store.find(listOf(email_hash)))
        assertFalse(stored(prefs).contains(email_hash))
        assertFalse(stored(prefs).contains(id_pin))
    }

    @Test
    fun new_records_never_store_the_email_hash() {
        val prefs = PlainPrefs()
        val store = LoginProofPinStore(prefs) { secret }
        store.pin(listOf(id_pin, hash_pin))
        store.pin_v3(listOf(hash_pin))
        store.remember_v3_owner(user_id, listOf(hash_pin))
        store.put(listOf(email_hash), salt)
        assertFalse(stored(prefs).contains(email_hash))
        val reopened = LoginProofPinStore(prefs) { secret }
        assertTrue(reopened.is_pinned(listOf(hash_pin)))
        assertTrue(reopened.is_v3_pinned(listOf(hash_pin)))
        assertArrayEquals(salt, reopened.find(listOf(email_hash)))
    }

    @Test
    fun a_different_device_secret_cannot_match_the_records() {
        val prefs = PlainPrefs()
        LoginProofPinStore(prefs) { secret }.apply {
            pin(listOf(hash_pin))
            put(listOf(email_hash), salt)
        }
        val other = LoginProofPinStore(prefs) { other_secret }
        assertFalse(other.is_pinned(listOf(hash_pin)))
        assertNull(other.find(listOf(email_hash)))
    }

    @Test
    fun removing_an_account_clears_its_migrated_entries() {
        val prefs = legacy_prefs()
        val store = LoginProofPinStore(prefs) { secret }
        store.unpin_v3_for_account(user_id, emptyList())
        store.unpin(listOf(id_pin, hash_pin))
        store.remove(listOf(email_hash))
        assertFalse(store.is_v3_pinned(listOf(hash_pin)))
        assertFalse(store.is_pinned(listOf(hash_pin)))
        assertNull(store.find(listOf(email_hash)))
        assertEquals("", prefs.getString("login_v3_owners", null))
    }

    @Test
    fun without_a_device_secret_no_unblinded_pin_is_written() {
        val prefs = PlainPrefs()
        val store = LoginProofPinStore(prefs) { null }
        store.pin(listOf(id_pin, hash_pin))
        store.pin_v3(listOf(hash_pin))
        store.remember_v3_owner(user_id, listOf(hash_pin))
        store.put(listOf(email_hash), salt)
        assertFalse(stored(prefs).contains(email_hash))
        assertFalse(stored(prefs).contains(id_pin))
    }

    @Test
    fun a_lost_device_secret_requires_v3_only_when_the_server_offers_it() {
        val prefs = PlainPrefs()
        LoginProofPinStore(prefs) { secret }.pin_v3(listOf(hash_pin))
        val without_secret = LoginProofPinStore(prefs) { null }
        assertTrue(without_secret.is_v3_seen(listOf(hash_pin), offered = true))
        assertFalse(without_secret.is_v3_seen(listOf(hash_pin), offered = false))
    }

    @Test
    fun a_replaced_device_secret_requires_v3_only_when_the_server_offers_it() {
        val prefs = PlainPrefs()
        LoginProofPinStore(prefs) { secret }.pin_v3(listOf(hash_pin))
        val replaced = LoginProofPinStore(prefs) { other_secret }
        assertTrue(replaced.is_v3_seen(listOf(hash_pin), offered = true))
        assertFalse(replaced.is_v3_seen(listOf(hash_pin), offered = false))
    }

    @Test
    fun the_same_device_secret_keeps_the_pin_across_restarts() {
        val prefs = PlainPrefs()
        LoginProofPinStore(prefs) { secret }.pin_v3(listOf(hash_pin))
        repeat(3) {
            val reopened = LoginProofPinStore(prefs) { secret }
            assertTrue(reopened.is_v3_pinned(listOf(hash_pin)))
            assertTrue(reopened.is_v3_seen(listOf(hash_pin), offered = false))
        }
        val blind = LoginKeyBlind(secret)
        val once = blind.pin(hash_pin)
        assertEquals(once, blind.pin(once))
    }

    @Test
    fun without_a_device_secret_older_records_stay_readable() {
        val prefs = legacy_prefs()
        val store = LoginProofPinStore(prefs) { null }
        assertTrue(store.is_pinned(listOf(hash_pin)))
        assertArrayEquals(salt, store.find(listOf(email_hash)))
        assertTrue(stored(prefs).contains(email_hash))
    }

    @Test
    fun the_device_secret_is_created_once_and_never_kept_in_memory_only_storage() {
        val prefs = PlainPrefs()
        val first = LoginKeyBlind.load_or_create_secret(prefs)
        assertNotNull(first)
        assertArrayEquals(first, LoginKeyBlind.load_or_create_secret(prefs))
        assertNull(LoginKeyBlind.load_or_create_secret(InMemoryPrefs()))
        assertNull(LoginKeyBlind.load_or_create_secret(null))
    }

    @Test
    fun blinded_keys_stay_valid_entries() {
        val blind = LoginKeyBlind(secret)
        val pin = blind.pin(hash_pin)
        assertTrue(LoginProof.is_valid_pin_entry(pin))
        assertEquals(pin, blind.pin(pin))
        val record = blind.record(email_hash)
        assertTrue(LoginSaltRule.is_valid_record_key(record))
        assertEquals(record, blind.record(record))
    }
}
