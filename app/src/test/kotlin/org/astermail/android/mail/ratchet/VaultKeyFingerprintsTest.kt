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
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VaultKeyFingerprintsTest {

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun encode(fill: Byte, length: Int): String =
        java.util.Base64.getEncoder().encodeToString(ByteArray(length) { fill })

    private fun expected(fill: Byte, length: Int): String =
        java.util.Base64.getEncoder()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(ByteArray(length) { fill }))

    @Test
    fun `matches the sha256 the server computes over the published key bytes`() {
        val vault = JSONObject().put("ratchet_identity_public", encode(7, 65))

        assertEquals(listOf(expected(7, 65)), collect_vault_key_fingerprints(vault))
    }

    @Test
    fun `covers the post-quantum key and every retired key set`() {
        val previous = JSONArray().put(
            JSONObject()
                .put("ratchet_identity_public", encode(3, 65))
                .put("ratchet_pq_identity_public", encode(5, 1184)),
        )
        val vault = JSONObject()
            .put("ratchet_identity_public", encode(1, 65))
            .put("ratchet_pq_identity_public", encode(2, 1184))
            .put("ratchet_previous_keys", previous)

        val fingerprints = collect_vault_key_fingerprints(vault)

        assertEquals(4, fingerprints.size)
        assertEquals(4, fingerprints.toSet().size)
    }

    @Test
    fun `attests nothing when an identity key cannot be fingerprinted`() {
        val vault = JSONObject()
            .put("ratchet_identity_key", "{\"kty\":\"EC\",\"crv\":\"P-256\",\"d\":\"not-a-key\"}")
            .put("ratchet_pq_identity_public", encode(9, 1184))

        assertEquals(emptyList<String>(), collect_vault_key_fingerprints(vault))
    }

    @Test
    fun `reports nothing when the vault holds no ratchet public keys`() {
        assertEquals(emptyList<String>(), collect_vault_key_fingerprints(JSONObject()))
    }
}
