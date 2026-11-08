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

import kotlinx.coroutines.test.runTest
import org.astermail.android.api.keys.ExternalKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientEncryptionRulesTest {

    private val now = 1_800_000_000_000L

    private fun info(
        email: String = "bob@example.com",
        found: Boolean = true,
        public_key: String? = "-----BEGIN PGP PUBLIC KEY BLOCK-----",
        will_encrypt: Boolean = false,
        expires_at: String? = null,
    ) = ExternalKeyInfo(
        email = email,
        found = found,
        public_key = public_key,
        will_encrypt = will_encrypt,
        expires_at = expires_at,
    )

    @Test
    fun trusted_key_encrypts_without_explicit_request() {
        assertEquals(
            RecipientEncryption.ENCRYPT,
            RecipientEncryptionRules.decide(info(will_encrypt = true), null, false, false),
        )
    }

    @Test
    fun keyserver_only_key_does_not_encrypt_by_default() {
        assertEquals(
            RecipientEncryption.PLAIN,
            RecipientEncryptionRules.decide(info(will_encrypt = false), null, false, false),
        )
    }

    @Test
    fun keyserver_only_key_encrypts_when_listed_or_forced_or_required() {
        val key = info(will_encrypt = false)
        assertEquals(RecipientEncryption.ENCRYPT, RecipientEncryptionRules.decide(key, null, false, true))
        assertEquals(RecipientEncryption.ENCRYPT, RecipientEncryptionRules.decide(key, true, false, false))
        assertEquals(RecipientEncryption.ENCRYPT, RecipientEncryptionRules.decide(key, null, true, false))
    }

    @Test
    fun explicit_request_without_key_falls_back_to_server() {
        assertEquals(
            RecipientEncryption.SERVER,
            RecipientEncryptionRules.decide(info(found = false, public_key = null), null, false, true),
        )
        assertEquals(RecipientEncryption.SERVER, RecipientEncryptionRules.decide(null, true, false, false))
    }

    @Test
    fun force_off_sends_plain_even_with_trusted_key() {
        assertEquals(
            RecipientEncryption.PLAIN,
            RecipientEncryptionRules.decide(info(will_encrypt = true), false, false, false),
        )
    }

    @Test
    fun copies_are_skipped_only_when_encryption_is_turned_off_for_everyone() {
        assertFalse(RecipientEncryptionRules.wants_copies(false, false, emptyList()))
        assertTrue(RecipientEncryptionRules.wants_copies(false, true, emptyList()))
        assertTrue(RecipientEncryptionRules.wants_copies(false, false, listOf("bob@example.com")))
        assertTrue(RecipientEncryptionRules.wants_copies(null, false, emptyList()))
    }

    @Test
    fun copy_failure_blocks_explicit_sends_and_listed_oversize_sends() {
        assertTrue(RecipientEncryptionRules.copy_failure_throws(explicit = true, listed = false, too_large = false))
        assertFalse(RecipientEncryptionRules.copy_failure_throws(explicit = false, listed = false, too_large = false))
        assertTrue(RecipientEncryptionRules.copy_failure_throws(explicit = true, listed = true, too_large = true))
        assertFalse(RecipientEncryptionRules.copy_failure_throws(explicit = true, listed = false, too_large = true))
    }

    @Test
    fun listed_match_is_case_and_space_insensitive() {
        assertTrue(RecipientEncryptionRules.is_listed(" Bob@Example.com ", listOf("bob@example.com")))
        assertFalse(RecipientEncryptionRules.is_listed("", listOf("")))
        assertFalse(RecipientEncryptionRules.is_listed("eve@example.com", listOf("bob@example.com")))
    }

    @Test
    fun lock_status_follows_key_state() {
        assertEquals(RecipientLockStatus.ENCRYPTED, RecipientEncryptionRules.lock_status_for(info(will_encrypt = true), now))
        assertEquals(RecipientLockStatus.AVAILABLE, RecipientEncryptionRules.lock_status_for(info(), now))
        assertEquals(
            RecipientLockStatus.KEY_INVALID,
            RecipientEncryptionRules.lock_status_for(info(expires_at = "2020-01-01T00:00:00Z"), now),
        )
        assertEquals(
            RecipientLockStatus.AVAILABLE,
            RecipientEncryptionRules.lock_status_for(info(expires_at = "2099-01-01T00:00:00Z"), now),
        )
        assertEquals(
            RecipientLockStatus.TRANSIT,
            RecipientEncryptionRules.lock_status_for(info(found = false, public_key = null), now),
        )
    }

    @Test
    fun encrypted_lock_respects_the_override() {
        assertTrue(RecipientEncryptionRules.shows_encrypted_lock(RecipientLockStatus.ENCRYPTED, null))
        assertFalse(RecipientEncryptionRules.shows_encrypted_lock(RecipientLockStatus.ENCRYPTED, false))
        assertFalse(RecipientEncryptionRules.shows_encrypted_lock(RecipientLockStatus.AVAILABLE, null))
        assertTrue(RecipientEncryptionRules.shows_encrypted_lock(RecipientLockStatus.AVAILABLE, true))
        assertFalse(RecipientEncryptionRules.shows_encrypted_lock(RecipientLockStatus.TRANSIT, true))
        assertFalse(RecipientEncryptionRules.shows_encrypted_lock(RecipientLockStatus.UNKNOWN, true))
    }

    @Test
    fun effective_status_never_claims_encryption_without_a_key() {
        assertEquals(
            RecipientLockStatus.AVAILABLE,
            RecipientEncryptionRules.effective_status(RecipientLockStatus.AVAILABLE, null, toggleable = true),
        )
        assertEquals(
            RecipientLockStatus.ENCRYPTED,
            RecipientEncryptionRules.effective_status(RecipientLockStatus.AVAILABLE, true, toggleable = true),
        )
        assertEquals(
            RecipientLockStatus.TRANSIT,
            RecipientEncryptionRules.effective_status(RecipientLockStatus.ENCRYPTED, false, toggleable = false),
        )
        assertEquals(
            RecipientLockStatus.KEY_INVALID,
            RecipientEncryptionRules.effective_status(RecipientLockStatus.KEY_INVALID, true, toggleable = false),
        )
    }

    @Test
    fun internal_recipient_shows_transit_when_an_outside_recipient_is_present() {
        assertEquals(RecipientLockStatus.ENCRYPTED, RecipientEncryptionRules.displayed_internal_status(false))
        assertEquals(RecipientLockStatus.TRANSIT, RecipientEncryptionRules.displayed_internal_status(true))
    }

    @Test
    fun toggle_turns_encryption_on_then_back_to_default() {
        assertEquals(true, RecipientEncryptionRules.toggle_override(null))
        assertNull(RecipientEncryptionRules.toggle_override(true))
        assertEquals(true, RecipientEncryptionRules.toggle_override(false))
    }

    @Test
    fun collect_lists_only_recipients_that_show_the_lock() {
        val statuses = mapOf(
            "a@example.com" to RecipientLockStatus.ENCRYPTED,
            "b@example.com" to RecipientLockStatus.AVAILABLE,
            "c@example.com" to RecipientLockStatus.TRANSIT,
        )
        val outside = listOf("A@example.com", "a@example.com", "b@example.com", "c@example.com", "d@example.com")
        assertEquals(listOf("a@example.com"), RecipientEncryptionRules.collect_pgp_recipients(outside, statuses, null))
        assertEquals(
            listOf("a@example.com", "b@example.com"),
            RecipientEncryptionRules.collect_pgp_recipients(outside, statuses, true),
        )
        assertEquals(emptyList<String>(), RecipientEncryptionRules.collect_pgp_recipients(outside, statuses, false))
    }

    @Test
    fun statuses_mark_undiscovered_addresses_as_transit() {
        val result = RecipientEncryptionRules.statuses_from_discovery(
            listOf("Bob@example.com", "eve@example.com"),
            listOf(info(email = "bob@example.com", will_encrypt = true)),
            now,
        )
        assertEquals(RecipientLockStatus.ENCRYPTED, result["bob@example.com"])
        assertEquals(RecipientLockStatus.TRANSIT, result["eve@example.com"])
    }

    @Test
    fun lookup_retries_after_failures_and_uses_the_first_result() = runTest {
        val waits = mutableListOf<Long>()
        var calls = 0
        val result = RecipientEncryptionRules.lookup_lock_statuses(
            listOf("bob@example.com"),
            now_ms = { now },
            wait = { waits.add(it) },
            discover = {
                calls += 1
                if (calls < 3) error("offline") else listOf(info(will_encrypt = true))
            },
        )
        assertEquals(3, calls)
        assertEquals(listOf(2000L, 4000L), waits)
        assertEquals(RecipientLockStatus.ENCRYPTED, result["bob@example.com"])
    }

    @Test
    fun lookup_reports_unknown_when_every_attempt_fails() = runTest {
        val result = RecipientEncryptionRules.lookup_lock_statuses(
            listOf("bob@example.com"),
            now_ms = { now },
            wait = {},
            discover = { error("offline") },
        )
        assertEquals(RecipientLockStatus.UNKNOWN, result["bob@example.com"])
    }

    @Test
    fun lookup_reports_transit_when_no_key_is_published() = runTest {
        val result = RecipientEncryptionRules.lookup_lock_statuses(
            listOf("bob@example.com"),
            now_ms = { now },
            wait = {},
            discover = { emptyList() },
        )
        assertEquals(RecipientLockStatus.TRANSIT, result["bob@example.com"])
    }

    @Test
    fun lookup_skips_discovery_without_outside_recipients() = runTest {
        var calls = 0
        val result = RecipientEncryptionRules.lookup_lock_statuses(
            listOf(" "),
            now_ms = { now },
            wait = {},
            discover = {
                calls += 1
                emptyList()
            },
        )
        assertEquals(0, calls)
        assertTrue(result.isEmpty())
    }
}
