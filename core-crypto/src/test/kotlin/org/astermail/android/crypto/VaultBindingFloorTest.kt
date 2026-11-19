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

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultBindingFloorTest {

    private val password = "correct horse battery staple".toByteArray(Charsets.UTF_8)
    private val plaintext = "{\"identity_key\":\"test\"}".toByteArray(Charsets.UTF_8)
    private var account: String? = null

    @Before
    fun set_up() {
        VaultBindingFloor.reset()
        account = "user-a"
        VaultBindingFloor.bind_account_source { account }
    }

    @After
    fun tear_down() {
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_account_source { null }
    }

    private fun unbound(): CryptoNative.VaultEnvelope =
        CryptoNative.encrypt_vault_with_password(plaintext, password, VaultKdf.DEFAULT_ITERATIONS, false)

    private fun bound(): CryptoNative.VaultEnvelope =
        CryptoNative.encrypt_vault_with_password(plaintext, password, VaultKdf.DEFAULT_ITERATIONS, true)

    private fun unwrap(envelope: CryptoNative.VaultEnvelope): CryptoNative.UnwrappedVault =
        CryptoNative.unwrap_vault(envelope.encrypted_vault, envelope.vault_nonce, password, null)

    @Test
    fun unbound_vault_opens_while_no_bound_vault_was_seen() {
        val opened = unwrap(unbound())
        assertArrayEquals(plaintext, opened.plaintext)
        assertFalse(opened.bound)
        assertTrue(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun bound_vault_raises_the_floor_for_its_account() {
        val legacy = unbound()
        val opened = unwrap(bound())
        assertTrue(opened.bound)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertThrows(Exception::class.java) { unwrap(legacy) }
        assertThrows(Exception::class.java) {
            CryptoNative.decrypt_vault_with_password(legacy.encrypted_vault, legacy.vault_nonce, password)
        }
    }

    @Test
    fun explicit_archive_read_still_opens_an_unbound_vault() {
        val archived = unbound()
        unwrap(bound())
        val opened = CryptoNative.unwrap_vault(
            archived.encrypted_vault,
            archived.vault_nonce,
            password,
            null,
            allow_unbound = true,
        )
        assertArrayEquals(plaintext, opened.plaintext)
        assertFalse(opened.bound)
    }

    @Test
    fun a_write_over_a_bound_floor_is_unbound_when_the_switch_is_off() {
        unwrap(bound())
        val written = unbound()
        assertFalse(written.bound)
        VaultBindingFloor.note_written(written.bound, encrypted_vault = written.encrypted_vault)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertTrue(VaultBindingFloor.allow_unbound(written.encrypted_vault))
        assertFalse(unwrap(written).bound)
    }

    @Test
    fun a_stored_bound_floor_does_not_make_writes_bound_after_a_restart() {
        val floor_store = memory_floor_store()
        floor_store.saved["user-a"] = true
        VaultBindingFloor.bind_store(floor_store)
        assertFalse(unbound().bound)
    }

    @Test
    fun an_unknown_floor_never_forces_a_bound_write() {
        assertFalse(unbound().bound)
        val floor_store = memory_floor_store()
        floor_store.saved["user-a"] = true
        floor_store.fail_reads = true
        VaultBindingFloor.bind_store(floor_store)
        assertFalse(unbound().bound)
        account = null
        assertFalse(unbound().bound)
    }

    @Test
    fun a_new_account_is_written_by_the_switch_not_the_previous_account_floor() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        unwrap(bound())
        val registered = unbound()
        assertFalse(registered.bound)
        VaultBindingFloor.note_written(registered.bound, "user-new")
        assertFalse(VaultBindingFloor.allow_unbound())
        account = "user-new"
        assertTrue(VaultBindingFloor.allow_unbound())
        assertFalse(unwrap(registered).bound)
        assertTrue(floor_store.saved["user-a"] == true)
    }

    @Test
    fun writes_follow_the_switch_while_the_floor_is_unbound() {
        val written = unbound()
        assertFalse(written.bound)
        assertTrue(VaultBindingFloor.allow_unbound())
        assertFalse(unwrap(written).bound)
    }

    @Test
    fun a_bound_write_raises_the_floor_only_once_it_is_noted() {
        val legacy = unbound()
        val written = bound()
        assertTrue(VaultBindingFloor.allow_unbound())
        VaultBindingFloor.note_written(written.bound)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertThrows(Exception::class.java) { unwrap(legacy) }
    }

    @Test
    fun an_unbound_write_of_its_own_vault_stays_readable_across_a_restart() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        unwrap(bound())
        val written = unbound()
        VaultBindingFloor.note_written(written.bound, encrypted_vault = written.encrypted_vault)
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertFalse(unwrap(written).bound)
    }

    @Test
    fun an_unbound_write_of_its_own_vault_does_not_lower_the_floor() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        val served = unbound()
        unwrap(bound())
        val written = unbound()
        VaultBindingFloor.note_written(written.bound, encrypted_vault = written.encrypted_vault)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertTrue(floor_store.saved["user-a"] == true)
        assertThrows(Exception::class.java) { unwrap(served) }
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertThrows(Exception::class.java) { unwrap(served) }
    }

    @Test
    fun a_noted_write_can_name_its_account() {
        account = null
        VaultBindingFloor.note_written(true, "user-c")
        account = "user-c"
        assertFalse(VaultBindingFloor.allow_unbound())
    }

    private class memory_floor_store : VaultBindingFloorStore {
        val saved = HashMap<String, Boolean>()
        val admitted = HashMap<String, Set<String>>()
        var fail_reads = false
        override fun is_bound(account: String): Boolean? = if (fail_reads) null else saved[account] ?: false
        override fun save(account: String, bound: Boolean): Boolean {
            saved[account] = bound
            return true
        }
        override fun admitted(account: String): Set<String>? = if (fail_reads) null else admitted[account].orEmpty()
        override fun save_admitted(account: String, digests: Set<String>): Boolean {
            admitted[account] = digests
            return true
        }
    }

    @Test
    fun the_floor_survives_a_restart_through_its_store() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        val legacy = unbound()
        unwrap(bound())
        assertTrue(floor_store.saved["user-a"] == true)
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertThrows(Exception::class.java) { unwrap(legacy) }
    }

    @Test
    fun forgetting_an_account_clears_its_stored_floor() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        unwrap(bound())
        VaultBindingFloor.forget("user-a")
        assertTrue(floor_store.saved["user-a"] == false)
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertTrue(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun an_unreadable_store_keeps_the_floor_held_in_memory() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        unwrap(bound())
        floor_store.fail_reads = true
        assertFalse(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun an_unreadable_store_at_cold_start_refuses_an_unbound_vault() {
        val floor_store = memory_floor_store()
        floor_store.saved["user-a"] = true
        floor_store.fail_reads = true
        VaultBindingFloor.bind_store(floor_store)
        val saved = unbound()
        assertFalse(saved.bound)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertThrows(Exception::class.java) { unwrap(saved) }
        assertThrows(Exception::class.java) {
            CryptoNative.decrypt_vault_with_password(saved.encrypted_vault, saved.vault_nonce, password)
        }
    }

    @Test
    fun an_unreadable_store_at_cold_start_still_opens_a_bound_vault_as_bound() {
        val floor_store = memory_floor_store()
        floor_store.fail_reads = true
        VaultBindingFloor.bind_store(floor_store)
        assertTrue(unwrap(bound()).bound)
    }

    @Test
    fun a_typed_password_opens_the_vault_when_the_store_is_unreadable() {
        val floor_store = memory_floor_store()
        floor_store.saved["user-a"] = true
        floor_store.fail_reads = true
        VaultBindingFloor.bind_store(floor_store)
        val saved = unbound()
        val opened = CryptoNative.unwrap_vault_after_password_sign_in(saved.encrypted_vault, saved.vault_nonce, password, null)
        assertArrayEquals(plaintext, opened.plaintext)
        assertFalse(opened.bound)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertTrue(VaultBindingFloor.allow_unbound(saved.encrypted_vault))
        assertTrue(floor_store.saved["user-a"] == true)
    }

    @Test
    fun a_configured_store_with_no_account_refuses_an_unbound_vault() {
        VaultBindingFloor.bind_store(memory_floor_store())
        account = null
        val saved = unbound()
        assertFalse(VaultBindingFloor.allow_unbound())
        assertThrows(Exception::class.java) { unwrap(saved) }
    }

    @Test
    fun a_password_sign_in_to_an_unbound_vault_keeps_it_readable_after_a_restart() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        account = null
        val other_client = unbound()
        account = "user-a"
        unwrap(bound())
        assertThrows(Exception::class.java) { unwrap(other_client) }
        val opened = CryptoNative.unwrap_vault_after_password_sign_in(
            other_client.encrypted_vault,
            other_client.vault_nonce,
            password,
            null,
        )
        assertArrayEquals(plaintext, opened.plaintext)
        assertFalse(opened.bound)
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertArrayEquals(plaintext, unwrap(other_client).plaintext)
        assertArrayEquals(
            plaintext,
            CryptoNative.decrypt_vault_with_password(other_client.encrypted_vault, other_client.vault_nonce, password),
        )
    }

    @Test
    fun a_password_sign_in_to_an_unbound_vault_keeps_the_floor_for_every_other_vault() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        val other_client = unbound()
        val older = unbound()
        unwrap(bound())
        CryptoNative.unwrap_vault_after_password_sign_in(other_client.encrypted_vault, other_client.vault_nonce, password, null)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertTrue(floor_store.saved["user-a"] == true)
        assertThrows(Exception::class.java) { unwrap(older) }
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertFalse(VaultBindingFloor.allow_unbound())
        assertThrows(Exception::class.java) { unwrap(older) }
    }

    @Test
    fun only_the_latest_admitted_vaults_stay_readable() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        unwrap(bound())
        val written = (0..VaultBindingFloor.MAX_ADMITTED_VAULTS).map { unbound() }
        written.forEach { VaultBindingFloor.note_written(false, encrypted_vault = it.encrypted_vault) }
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertThrows(Exception::class.java) { unwrap(written.first()) }
        assertFalse(unwrap(written.last()).bound)
    }

    @Test
    fun a_bound_vault_raises_the_floor_after_a_password_sign_in_to_an_unbound_vault() {
        val floor_store = memory_floor_store()
        VaultBindingFloor.bind_store(floor_store)
        account = null
        val other_client = unbound()
        account = "user-a"
        CryptoNative.unwrap_vault_after_password_sign_in(other_client.encrypted_vault, other_client.vault_nonce, password, null)
        assertFalse(floor_store.saved["user-a"] == true)
        unwrap(bound())
        assertTrue(floor_store.saved["user-a"] == true)
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_store(floor_store)
        assertFalse(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun a_wrong_password_at_sign_in_keeps_the_floor() {
        account = null
        val other_client = unbound()
        account = "user-a"
        unwrap(bound())
        assertThrows(Exception::class.java) {
            CryptoNative.unwrap_vault_after_password_sign_in(
                other_client.encrypted_vault,
                other_client.vault_nonce,
                "wrong password".toByteArray(Charsets.UTF_8),
                null,
            )
        }
        assertFalse(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun a_password_sign_in_keeps_the_floor_for_a_bound_vault() {
        val sealed = bound()
        unwrap(sealed)
        val opened = CryptoNative.unwrap_vault_after_password_sign_in(sealed.encrypted_vault, sealed.vault_nonce, password, null)
        assertTrue(opened.bound)
        assertFalse(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun floor_is_kept_per_account() {
        val legacy = unbound()
        unwrap(bound())
        account = "user-b"
        assertTrue(VaultBindingFloor.allow_unbound())
        assertFalse(unwrap(legacy).bound)
        account = "user-a"
        assertFalse(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun forgetting_an_account_lowers_its_floor() {
        unwrap(bound())
        VaultBindingFloor.forget("user-a")
        assertTrue(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun no_account_never_raises_the_floor() {
        account = null
        unwrap(bound())
        assertTrue(VaultBindingFloor.allow_unbound())
        account = "user-a"
        assertTrue(VaultBindingFloor.allow_unbound())
    }

    @Test
    fun plain_decrypt_does_not_raise_the_floor() {
        account = null
        val sealed = bound()
        account = "user-a"
        val opened = CryptoNative.open_vault_with_password(sealed.encrypted_vault, sealed.vault_nonce, password)
        assertTrue(opened.bound)
        assertTrue(VaultBindingFloor.allow_unbound())
    }
}
