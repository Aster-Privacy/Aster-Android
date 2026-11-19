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
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class RecoveryLaneSenderProofTest {

    private lateinit var cases: List<JSONObject>

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
        val array = JSONObject(stream.bufferedReader().use { it.readText() }).getJSONArray("cases")
        cases = (0 until array.length()).map { array.getJSONObject(it) }
    }

    @After
    fun tear_down() {
        unmockkStatic(android.util.Base64::class)
    }

    @Test
    fun refuses_a_lane_sealed_without_the_sender_identity_key() {
        val sender_identity_public = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)

        for (case in cases) {
            val recipient_pq_public = case.getString("recipient_pq_identity_public")
            val planted = RecoveryLane.seal_unbound(
                "planted message key",
                case.getString("conversation_id"),
                sender_identity_public,
                RecoveryLane.RecipientKeys(
                    case.getString("recipient_identity_public"),
                    recipient_pq_public.ifBlank { null },
                ),
            )

            val opened = RecoveryLane.open(
                planted,
                case.getString("conversation_id"),
                sender_identity_public,
                RecoveryLane.OwnKeys(
                    identity_jwk = case.getString("recipient_identity_jwk"),
                    identity_public = case.getString("recipient_identity_public"),
                    pq_identity_secret = case.getString("recipient_pq_identity_secret").ifBlank { null },
                ),
                recipient_pq_public,
                RecoveryLane.Transcript("", 0, 0, "", 1L),
            )

            assertNull(case.getString("name"), opened)
        }
    }

    @Test
    fun refuses_to_seal_a_lane_without_the_sender_identity_key() {
        val case = cases.first()
        val recipient_pq_public = case.getString("recipient_pq_identity_public")

        assertThrows(RecoveryLane.SenderKeyMissingException::class.java) {
            RecoveryLane.seal(
                "message key",
                case.getString("conversation_id"),
                RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw),
                RecoveryLane.RecipientKeys(
                    case.getString("recipient_identity_public"),
                    recipient_pq_public.ifBlank { null },
                ),
                null,
                RecoveryLane.Transcript("header", 0, 0, "nonce", 1L),
            )
        }
    }
}
