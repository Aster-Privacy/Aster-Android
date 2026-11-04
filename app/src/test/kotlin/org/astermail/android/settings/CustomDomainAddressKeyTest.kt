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

package org.astermail.android.settings

import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomDomainAddressKeyTest {

    private fun failed(id: String) = CustomDomainAddressInfo(
        id = id,
        domain_name = "adrielvelazquez.org",
        decryption_failed = true,
    )

    @Test
    fun decryption_failed_addresses_on_same_domain_share_address() {
        val first = failed("cda-1")
        val second = failed("cda-2")

        assertEquals(first.address, second.address)
    }

    @Test
    fun row_keys_are_unique_when_addresses_collide() {
        val rows = listOf(failed("cda-1"), failed("cda-2"), failed("cda-3"))

        val id_keys = rows.map { "cda_${it.id}" }

        assertEquals(3, id_keys.toSet().size)
    }
}
