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
}
