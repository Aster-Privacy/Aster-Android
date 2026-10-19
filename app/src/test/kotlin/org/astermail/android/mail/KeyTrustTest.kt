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

import org.astermail.android.api.keys.ExternalKeyFingerprintChange
import org.astermail.android.api.keys.ExternalKeyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyTrustTest {

    private fun external_key(
        email: String,
        change: ExternalKeyFingerprintChange? = null,
    ): ExternalKeyInfo = ExternalKeyInfo(
        email = email,
        found = change != null,
        fingerprint_change = change,
    )

    @Test
    fun internal_recipients_are_never_looked_up() {
        val candidates = external_key_trust_candidates(
            listOf("someone@astermail.org", "friend@example.com"),
        )
        assertEquals(listOf("friend@example.com"), candidates)
    }

    @Test
    fun candidates_are_normalized_and_deduplicated() {
        val candidates = external_key_trust_candidates(
            listOf("  Friend@Example.com ", "friend@example.com", "not-an-address"),
        )
        assertEquals(listOf("friend@example.com"), candidates)
    }

    @Test
    fun a_changed_fingerprint_is_reported() {
        val changes = key_changes_from_discovery(
            listOf(
                external_key(
                    "Friend@Example.com",
                    ExternalKeyFingerprintChange(
                        prior_fingerprint = "AAAA1111",
                        new_fingerprint = "BBBB2222",
                        source = "wkd",
                    ),
                ),
            ),
        )
        assertEquals(1, changes.size)
        assertEquals("friend@example.com", changes[0].email)
        assertEquals("AAAA1111", changes[0].prior_fingerprint)
        assertEquals("BBBB2222", changes[0].new_fingerprint)
    }

    @Test
    fun a_first_time_key_is_not_a_change() {
        val changes = key_changes_from_discovery(listOf(external_key("friend@example.com")))
        assertTrue(changes.isEmpty())
    }

    @Test
    fun a_blank_fingerprint_pair_is_ignored() {
        val changes = key_changes_from_discovery(
            listOf(
                external_key(
                    "friend@example.com",
                    ExternalKeyFingerprintChange(prior_fingerprint = "", new_fingerprint = "BBBB2222"),
                ),
                external_key(
                    "other@example.com",
                    ExternalKeyFingerprintChange(prior_fingerprint = "AAAA1111", new_fingerprint = "  "),
                ),
            ),
        )
        assertTrue(changes.isEmpty())
    }

    @Test
    fun every_changed_recipient_is_reported() {
        val changes = key_changes_from_discovery(
            listOf(
                external_key(
                    "one@example.com",
                    ExternalKeyFingerprintChange("AAAA", "BBBB"),
                ),
                external_key("two@example.com"),
                external_key(
                    "three@example.com",
                    ExternalKeyFingerprintChange("CCCC", "DDDD"),
                ),
            ),
        )
        assertEquals(listOf("one@example.com", "three@example.com"), changes.map { it.email })
    }

    @Test
    fun trust_cache_misses_until_every_recipient_is_stored() {
        val cache = ExternalKeyTrustCache(now_ms = { 1_000L })
        cache.store(listOf("one@example.com"), emptyList(), emptySet())
        assertNull(cache.fresh_changes(listOf("one@example.com", "two@example.com")))
        assertEquals(emptyList<RecipientKeyChange>(), cache.fresh_changes(listOf("one@example.com")))
    }

    @Test
    fun trust_cache_returns_stored_changes() {
        val cache = ExternalKeyTrustCache(now_ms = { 1_000L })
        val change = RecipientKeyChange("two@example.com", "AAAA", "BBBB")
        cache.store(listOf("one@example.com", "two@example.com"), listOf(change), setOf("two@example.com"))
        assertEquals(listOf(change), cache.fresh_changes(listOf("one@example.com", "two@example.com")))
    }

    @Test
    fun trust_cache_expires_after_ttl() {
        var now = 1_000L
        val cache = ExternalKeyTrustCache(ttl_ms = 500L, now_ms = { now })
        cache.store(listOf("one@example.com"), emptyList(), emptySet())
        now = 1_400L
        assertEquals(emptyList<RecipientKeyChange>(), cache.fresh_changes(listOf("one@example.com")))
        now = 1_600L
        assertNull(cache.fresh_changes(listOf("one@example.com")))
    }

    @Test
    fun trust_cache_forgets_an_acknowledged_recipient() {
        val cache = ExternalKeyTrustCache(now_ms = { 1_000L })
        cache.store(listOf("one@example.com"), listOf(RecipientKeyChange("one@example.com", "AAAA", "BBBB")), setOf("one@example.com"))
        cache.forget(" One@Example.com ")
        assertNull(cache.fresh_changes(listOf("one@example.com")))
    }

    @Test
    fun trust_cache_only_skips_the_send_check_for_recipients_without_a_key() {
        val cache = ExternalKeyTrustCache(now_ms = { 1_000L })
        cache.store(listOf("keyless@example.com", "keyed@example.com"), emptyList(), setOf("keyed@example.com"))
        assertTrue(cache.all_fresh_without_key(listOf("keyless@example.com")))
        assertFalse(cache.all_fresh_without_key(listOf("keyed@example.com")))
        assertFalse(cache.all_fresh_without_key(listOf("keyless@example.com", "keyed@example.com")))
        assertFalse(cache.all_fresh_without_key(listOf("keyless@example.com", "unknown@example.com")))
    }

    @Test
    fun trust_cache_send_skip_expires_after_ttl() {
        var now = 1_000L
        val cache = ExternalKeyTrustCache(ttl_ms = 500L, now_ms = { now })
        cache.store(listOf("keyless@example.com"), emptyList(), emptySet())
        now = 1_600L
        assertFalse(cache.all_fresh_without_key(listOf("keyless@example.com")))
    }

    @Test
    fun trust_cache_prunes_expired_entries_on_store() {
        var now = 1_000L
        val cache = ExternalKeyTrustCache(ttl_ms = 500L, now_ms = { now })
        cache.store(listOf("old@example.com"), emptyList(), emptySet())
        now = 1_600L
        cache.store(listOf("new@example.com"), emptyList(), emptySet())
        now = 1_000L
        assertNull(cache.fresh_changes(listOf("old@example.com")))
    }

    @Test
    fun keyed_recipients_lists_only_found_keys() {
        val keyed = keyed_recipients_from_discovery(
            listOf(
                ExternalKeyInfo(email = " Keyed@Example.com ", found = true),
                ExternalKeyInfo(email = "none@example.com", found = false),
            ),
        )
        assertEquals(setOf("keyed@example.com"), keyed)
    }
}
