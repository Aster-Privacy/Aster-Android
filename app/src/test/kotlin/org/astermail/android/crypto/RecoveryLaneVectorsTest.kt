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
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RecoveryLaneVectorsTest {

    private lateinit var vectors: JSONObject

    @Before
    fun set_up() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            val text = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and android.util.Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlDecoder().decode(text.trimEnd('='))
            } else {
                java.util.Base64.getDecoder().decode(text)
            }
        }

        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("recovery_lane_vectors.json"),
        )
        vectors = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    @Test
    fun vector_file_matches_the_implemented_lane_version() {
        assertEquals(RecoveryLane.VERSION, vectors.getInt("version"))
    }

    @Test
    fun opens_every_shared_vector() {
        val cases = vectors.getJSONArray("cases")
        assertEquals(2, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val opened = RecoveryLane.open_unauthenticated(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
            )

            assertNotNull(case.getString("name"), opened)
            assertEquals(case.getString("name"), case.getString("plaintext"), opened)
        }
    }

    @Test
    fun keeps_older_vectors_out_of_the_sender_bound_opener() {
        val cases = vectors.getJSONArray("cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val opened = RecoveryLane.open(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                RecoveryLane.Transcript("", 0, 0, "", 1L),
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun opens_every_sender_bound_vector_through_both_parts() {
        val cases = vectors.getJSONArray("sender_bound_cases")
        assertEquals(2, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val bound = RecoveryLane.open(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                transcript(case),
            )
            val legacy = RecoveryLane.open_unauthenticated(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
            )

            assertEquals(case.getString("name"), case.getString("plaintext"), bound)
            assertEquals(case.getString("name"), case.getString("plaintext"), legacy)
        }
    }

    @Test
    fun carries_the_send_time_in_every_sender_bound_vector() {
        val cases = vectors.getJSONArray("sender_bound_cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            assertEquals(case.getString("name"), transcript(case).sent_at, lane_data(case).sent_at)
        }
    }

    @Test
    fun rejects_a_sender_bound_vector_under_another_send_time() {
        val cases = vectors.getJSONArray("sender_bound_cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val moved = transcript(case).let { it.copy(sent_at = it.sent_at + 1) }
            val opened = RecoveryLane.open(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                moved,
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun keeps_sender_bound_vectors_out_of_the_older_opener() {
        val cases = vectors.getJSONArray("sender_bound_cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val opened = RecoveryLane.open(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                null,
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun opens_every_older_sender_bound_vector_without_a_transcript() {
        val cases = vectors.getJSONArray("legacy_sender_bound_cases")
        assertEquals(2, cases.length())

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val data = lane_data(case)
            val opened = RecoveryLane.open(
                data,
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                null,
            )

            assertNull(case.getString("name"), data.sent_at)
            assertEquals(case.getString("name"), case.getString("plaintext"), opened)
        }
    }

    @Test
    fun rejects_an_older_sender_bound_vector_for_another_sender_key() {
        val cases = vectors.getJSONArray("legacy_sender_bound_cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val opened = RecoveryLane.open(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("recipient_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                null,
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun rejects_a_sender_bound_vector_for_another_sender_key() {
        val cases = vectors.getJSONArray("sender_bound_cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val opened = RecoveryLane.open(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("recipient_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                transcript(case),
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun rejects_a_sender_bound_vector_with_an_altered_sender_part() {
        val case = vectors.getJSONArray("sender_bound_cases").getJSONObject(0)
        val lane = lane_data(case)
        val sct = java.util.Base64.getDecoder().decode(lane.sct)
        sct[0] = (sct[0].toInt() xor 0xff).toByte()

        val opened = RecoveryLane.open(
            lane.copy(sct = java.util.Base64.getEncoder().encodeToString(sct)),
            case.getString("conversation_id"),
            case.getString("sender_identity_public"),
            own_keys(case),
            case.getString("recipient_pq_identity_public"),
            transcript(case),
        )

        assertNull(opened)
    }

    @Test
    fun rejects_a_sender_bound_vector_under_another_ratchet_header() {
        val cases = vectors.getJSONArray("sender_bound_cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val moved = transcript(case).let { it.copy(message_number = it.message_number + 1) }
            val opened = RecoveryLane.open(
                lane_data(case),
                case.getString("conversation_id"),
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
                moved,
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun rejects_every_vector_under_a_tampered_conversation_id() {
        val cases = vectors.getJSONArray("cases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val opened = RecoveryLane.open_unauthenticated(
                lane_data(case),
                case.getString("conversation_id") + "-tampered",
                case.getString("sender_identity_public"),
                own_keys(case),
                case.getString("recipient_pq_identity_public"),
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun rejects_a_vector_signed_for_a_different_sender() {
        val case = vectors.getJSONArray("cases").getJSONObject(0)

        val opened = RecoveryLane.open_unauthenticated(
            lane_data(case),
            case.getString("conversation_id"),
            "another-sender-identity",
            own_keys(case),
            case.getString("recipient_pq_identity_public"),
        )

        assertNull(opened)
    }

    private fun lane_data(case: JSONObject): RecoveryLane.Data {
        val lane = case.getJSONObject("lane")
        return RecoveryLane.Data(
            v = lane.getInt("v"),
            epk = lane.getString("epk"),
            kem_ct = if (lane.has("kem_ct")) lane.getString("kem_ct") else null,
            ciphertext = lane.getString("ciphertext"),
            nonce = lane.getString("nonce"),
            rid = lane.getString("rid"),
            sct = lane.optString("sct").ifEmpty { null },
            snonce = lane.optString("snonce").ifEmpty { null },
            t = if (lane.has("t")) lane.getLong("t") else null,
        )
    }

    private fun transcript(case: JSONObject): RecoveryLane.Transcript {
        val entry = case.getJSONObject("transcript")
        return RecoveryLane.Transcript(
            dh_public = entry.getString("dh_public"),
            previous_chain_length = entry.getInt("previous_chain_length"),
            message_number = entry.getInt("message_number"),
            nonce = entry.getString("nonce"),
            sent_at = entry.getLong("sent_at"),
        )
    }

    private fun own_keys(case: JSONObject): RecoveryLane.OwnKeys = RecoveryLane.OwnKeys(
        identity_jwk = case.getString("recipient_identity_jwk"),
        identity_public = case.getString("recipient_identity_public"),
        pq_identity_secret = case.getString("recipient_pq_identity_secret").ifBlank { null },
    )
}
