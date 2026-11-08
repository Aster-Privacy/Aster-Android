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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentEncryptionEvidenceTest {

    @Test
    fun every_outside_recipient_with_a_copy_gives_outside_scope() {
        val evidence = SentEncryptionEvidenceRules.build(
            all_recipients = listOf("Bob <bob@example.com>", "alice@astermail.org"),
            outside_recipients = listOf("bob@example.com"),
            client_copies = mapOf("Bob@Example.com" to "-----BEGIN PGP MESSAGE-----"),
            internal_copy_is_encrypted = true,
        )
        assertEquals(SentEncryptionEvidence(1, 1, 0), evidence)
        assertEquals(SentEncryptionScope.OUTSIDE, SentEncryptionEvidenceRules.scope(evidence))
    }

    @Test
    fun a_missing_copy_gives_no_scope() {
        val evidence = SentEncryptionEvidenceRules.build(
            all_recipients = listOf("bob@example.com", "eve@example.com"),
            outside_recipients = listOf("bob@example.com", "eve@example.com"),
            client_copies = mapOf("bob@example.com" to "armor"),
            internal_copy_is_encrypted = true,
        )
        assertEquals(1, evidence.outside_pgp)
        assertNull(SentEncryptionEvidenceRules.scope(evidence))
    }

    @Test
    fun readable_internal_copy_gives_no_scope() {
        val evidence = SentEncryptionEvidenceRules.build(
            all_recipients = listOf("alice@astermail.org"),
            outside_recipients = emptyList(),
            client_copies = null,
            internal_copy_is_encrypted = false,
        )
        assertNull(SentEncryptionEvidenceRules.scope(evidence))
    }

    @Test
    fun internal_only_send_gives_internal_scope() {
        val evidence = SentEncryptionEvidenceRules.build(
            all_recipients = listOf("alice@astermail.org"),
            outside_recipients = emptyList(),
            client_copies = null,
            internal_copy_is_encrypted = true,
        )
        assertEquals(SentEncryptionScope.INTERNAL, SentEncryptionEvidenceRules.scope(evidence))
    }

    @Test
    fun evidence_round_trips_through_the_envelope() {
        val evidence = SentEncryptionEvidence(2, 2, 0)
        val json = SentEncryptionEvidenceRules.with_evidence("{\"subject\":\"Hi\"}", evidence)
        assertEquals("Hi", JSONObject(json).getString("subject"))
        assertEquals(evidence, SentEncryptionEvidenceRules.read(JSONObject(json)))
        assertEquals(SentEncryptionScope.OUTSIDE, SentEncryptionEvidenceRules.scope_from_envelope_json(json))
    }

    @Test
    fun malformed_evidence_is_ignored() {
        assertNull(SentEncryptionEvidenceRules.scope_from_envelope_json("not json"))
        assertNull(SentEncryptionEvidenceRules.scope_from_envelope_json("{}"))
        assertNull(
            SentEncryptionEvidenceRules.scope_from_envelope_json(
                "{\"sent_encryption\":{\"outside\":1,\"outside_pgp\":2,\"internal_readable\":0}}",
            ),
        )
        assertNull(
            SentEncryptionEvidenceRules.scope_from_envelope_json(
                "{\"sent_encryption\":{\"outside\":1.5,\"outside_pgp\":1,\"internal_readable\":0}}",
            ),
        )
        assertNull(
            SentEncryptionEvidenceRules.scope_from_envelope_json(
                "{\"sent_encryption\":{\"outside\":-1,\"outside_pgp\":0,\"internal_readable\":0}}",
            ),
        )
        assertEquals("{}", SentEncryptionEvidenceRules.with_evidence("{}", null))
    }

    @Test
    fun end_to_end_display_matches_the_web_rule() {
        assertTrue(SentEncryptionEvidenceRules.shows_end_to_end(SentEncryptionScope.OUTSIDE, true, false, false))
        assertTrue(SentEncryptionEvidenceRules.shows_end_to_end(null, false, false, true))
        assertFalse(SentEncryptionEvidenceRules.shows_end_to_end(null, true, false, true))
        assertTrue(SentEncryptionEvidenceRules.shows_end_to_end(null, true, true, true))
        assertFalse(SentEncryptionEvidenceRules.shows_end_to_end(null, false, false, false))
        assertTrue(SentEncryptionEvidenceRules.reaches_outside_recipients(SentEncryptionScope.OUTSIDE, false))
        assertFalse(SentEncryptionEvidenceRules.reaches_outside_recipients(SentEncryptionScope.INTERNAL, true))
        assertTrue(SentEncryptionEvidenceRules.reaches_outside_recipients(null, true))
    }
}
