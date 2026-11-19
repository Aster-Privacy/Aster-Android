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

package org.astermail.android.crypto.storage_rotation

import io.mockk.mockk
import org.astermail.android.api.keys.BoundWriteSwitches
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.VaultBindingFloor
import org.astermail.android.crypto.VaultKdf
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class SessionVaultCodecTest {

    private val password = "correct horse battery staple"

    private fun codec(): SessionVaultCodec =
        SessionVaultCodec(mockk(relaxed = true)) { password.toByteArray(Charsets.UTF_8) }

    @Before
    fun set_up() {
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_account_source { "user-a" }
        BoundWriteSwitches.clear()
    }

    @After
    fun tear_down() {
        VaultBindingFloor.reset()
        VaultBindingFloor.bind_account_source { null }
        BoundWriteSwitches.clear()
    }

    @Test
    fun a_vault_written_unbound_over_a_raised_floor_passes_its_own_read_back() {
        val raised = CryptoNative.encrypt_vault_with_password(
            "{}".toByteArray(Charsets.UTF_8),
            password.toByteArray(Charsets.UTF_8),
            VaultKdf.DEFAULT_ITERATIONS,
            true,
        )
        CryptoNative.unwrap_vault(raised.encrypted_vault, raised.vault_nonce, password.toByteArray(Charsets.UTF_8), null)
        assertFalse(VaultBindingFloor.allow_unbound())
        val sealed = codec().encrypt_at(JSONObject().put("identity_key", "next"), VaultKdf.DEFAULT_ITERATIONS)
        assertNotNull(sealed)
        assertFalse(sealed!!.bound)
        val read_back = codec().decrypt_sealed(sealed)
        assertEquals("next", read_back?.getString("identity_key"))
    }
}
