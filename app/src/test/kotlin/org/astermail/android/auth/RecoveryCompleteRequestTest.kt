//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.auth

import org.astermail.android.crypto.storage_rotation.VAULT_GENERATION_FIELD
import org.astermail.android.crypto.storage_rotation.vault_generation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryCompleteRequestTest {
    private fun stored_vault(generation: Long): ByteArray =
        JSONObject()
            .put("identity_private_key", "raw-native-identity")
            .put(VAULT_GENERATION_FIELD, generation)
            .toString()
            .toByteArray(Charsets.UTF_8)

    private fun request_for(vault: RecoveryUploadVault) = recovery_complete_request(
        recovery_token = "token",
        password_hash = "hash",
        password_hash_v2 = "hash_v2",
        password_salt = "salt",
        encrypted_vault = "vault",
        vault_nonce = "nonce",
        shares = emptyList(),
        backup = RecoveryBackupFields("backup", "backup_nonce", "backup_salt"),
        vault = vault,
        vault_kdf_iterations = null,
    )

    private fun written_generation(vault: RecoveryUploadVault): Long =
        vault_generation(JSONObject(String(vault.plain, Charsets.UTF_8)))

    @Test
    fun recovered_request_carries_the_generation_past_the_server_write_count() {
        val vault = recovery_upload_vault(
            stored_vault(4),
            listOf("AAAA-BBBB"),
            "new password".toCharArray(),
            null,
            9L,
        )
        assertEquals(10L, written_generation(vault))
        assertEquals(10L, request_for(vault).vault_generation)
    }

    @Test
    fun recovered_request_carries_the_generation_past_the_stored_vault() {
        val vault = recovery_upload_vault(
            stored_vault(4),
            listOf("AAAA-BBBB"),
            "new password".toCharArray(),
            null,
            null,
        )
        assertEquals(5L, written_generation(vault))
        assertEquals(5L, request_for(vault).vault_generation)
    }
}
