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

import kotlinx.serialization.json.Json
import org.astermail.android.api.recovery.CompleteRecoveryResponse
import org.astermail.android.api.recovery.InitiateRecoveryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveredAccountAddressTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the server address replaces a wrong typed domain`() {
        assertEquals("flavio@astermail.org", parse_recovered_account_address("flavio@astermail.org"))
        assertEquals("flavio@aster.cx", parse_recovered_account_address(" Flavio@Aster.CX "))
    }

    @Test
    fun `unsupported or malformed addresses are ignored`() {
        assertNull(parse_recovered_account_address(null))
        assertNull(parse_recovered_account_address(""))
        assertNull(parse_recovered_account_address("flavio"))
        assertNull(parse_recovered_account_address("@astermail.org"))
        assertNull(parse_recovered_account_address("flavio@"))
        assertNull(parse_recovered_account_address("flavio@example.com"))
        assertNull(parse_recovered_account_address("fla vio@astermail.org"))
    }

    @Test
    fun `responses decode with and without the address`() {
        assertNull(json.decodeFromString<CompleteRecoveryResponse>("""{"success":true}""").account_address)
        assertEquals(
            "flavio@astermail.org",
            json.decodeFromString<CompleteRecoveryResponse>(
                """{"success":true,"account_address":"flavio@astermail.org"}""",
            ).account_address,
        )
        val initiate = "{\"encrypted_vault_backup\":\"a\",\"vault_backup_nonce\":\"b\",\"recovery_key_salt\":\"c\",\"encrypted_recovery_key\":\"d\",\"recovery_key_nonce\":\"e\",\"code_salt\":\"f\",\"recovery_token\":\"g\""
        assertNull(json.decodeFromString<InitiateRecoveryResponse>("$initiate}").account_address)
        assertEquals(
            "flavio@astermail.org",
            json.decodeFromString<InitiateRecoveryResponse>(
                """$initiate,"account_address":"flavio@astermail.org"}""",
            ).account_address,
        )
    }
}
