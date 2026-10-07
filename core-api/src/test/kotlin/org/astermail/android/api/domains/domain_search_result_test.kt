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

package org.astermail.android.api.domains

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class domain_search_result_test {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun missing_flag_means_the_domain_was_checked() {
        val response = json.decodeFromString(
            DomainSearchResponse.serializer(),
            """{"results":[{"domain":"open.com","available":true,"price_cents":1000,"renewal_price_cents":1200,"currency":"usd"},{"domain":"taken.com","available":false,"price_cents":null,"renewal_price_cents":null,"currency":"usd"}]}""",
        )
        assertEquals(2, response.results.size)
        assertFalse(response.results[0].availability_unknown)
        assertFalse(response.results[1].availability_unknown)
        assertTrue(response.results[0].is_purchasable())
        assertFalse(response.results[1].is_purchasable())
    }

    @Test
    fun reads_unchecked_domains() {
        val response = json.decodeFromString(
            DomainSearchResponse.serializer(),
            """{"results":[{"domain":"unchecked.com","available":false,"price_cents":null,"renewal_price_cents":null,"currency":"usd","availability_unknown":true}]}""",
        )
        val result = response.results.single()
        assertTrue(result.availability_unknown)
        assertFalse(result.available)
        assertFalse(result.is_purchasable())
    }

    @Test
    fun unchecked_domain_is_never_purchasable() {
        val odd = DomainSearchResult(
            domain = "odd.com",
            available = true,
            price_cents = 1000,
            availability_unknown = true,
        )
        assertFalse(odd.is_purchasable())
        assertFalse(DomainSearchResult(domain = "free.com", available = true).is_purchasable())
        assertTrue(DomainSearchResult(domain = "free.com", available = true, price_cents = 900).is_purchasable())
    }
}
