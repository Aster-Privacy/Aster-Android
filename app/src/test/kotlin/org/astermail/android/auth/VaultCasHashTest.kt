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

import org.astermail.android.api.keys.CurrentVaultResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class VaultCasHashTest {

    private fun sha256_b64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))

    @Test
    fun uses_server_reported_hash_when_present() {
        val result = CurrentVaultResult.Available("AAAA", "BBBB", null, "server_hash")
        assertEquals("server_hash", expected_vault_sha256(result))
    }

    @Test
    fun hashes_decoded_vault_bytes_when_server_hash_missing() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val result = CurrentVaultResult.Available(encoded, "BBBB")
        assertEquals(sha256_b64(bytes), expected_vault_sha256(result))
    }

    @Test
    fun blank_server_hash_falls_back_to_local_hash() {
        val bytes = "vault".toByteArray()
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val result = CurrentVaultResult.Available(encoded, "BBBB", null, "  ")
        assertEquals(sha256_b64(bytes), expected_vault_sha256(result))
    }

    @Test
    fun no_hash_without_a_server_vault() {
        assertNull(expected_vault_sha256(CurrentVaultResult.Missing))
        assertNull(expected_vault_sha256(CurrentVaultResult.Unavailable))
    }

    @Test
    fun undecodable_vault_yields_no_hash() {
        assertNull(expected_vault_sha256(CurrentVaultResult.Available("not base64 !!", "BBBB")))
    }
}
