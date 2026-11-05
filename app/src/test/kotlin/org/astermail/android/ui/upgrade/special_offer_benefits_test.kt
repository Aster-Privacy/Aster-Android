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

package org.astermail.android.ui.upgrade

import org.astermail.android.api.billing.AvailablePlan
import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialOfferBenefitsTest {

    private val star = AvailablePlan(
        code = "star",
        name = "Star",
        storage_limit_bytes = 16_106_127_360,
        max_email_aliases = 15,
        max_custom_domains = 5,
    )

    private val nova = AvailablePlan(
        code = "nova",
        name = "Nova",
        storage_limit_bytes = 536_870_912_000,
        max_email_aliases = -1,
        max_custom_domains = 30,
    )

    @Test
    fun star_lists_its_own_limits_without_vanguard() {
        assertEquals(
            listOf(
                special_offer_benefit.aliases(15),
                special_offer_benefit.storage("15 GB"),
                special_offer_benefit.domains(5),
            ),
            special_offer_benefits_for("star", star),
        )
    }

    @Test
    fun nova_lists_its_limits_and_vanguard() {
        assertEquals(
            listOf(
                special_offer_benefit.unlimited_aliases,
                special_offer_benefit.storage("500 GB"),
                special_offer_benefit.domains(30),
                special_offer_benefit.vanguard,
            ),
            special_offer_benefits_for("nova", nova),
        )
    }

    @Test
    fun unlimited_domains_are_shown_as_unlimited() {
        val plan = nova.copy(code = "supernova", max_custom_domains = -1)
        assertEquals(
            special_offer_benefit.unlimited_domains,
            special_offer_benefits_for("supernova", plan)[2],
        )
    }

    @Test
    fun missing_plan_data_never_claims_numbers() {
        assertEquals(emptyList<special_offer_benefit>(), special_offer_benefits_for("star", null))
        assertEquals(listOf(special_offer_benefit.vanguard), special_offer_benefits_for("Nova", null))
    }

    @Test
    fun storage_label_rounds_whole_units() {
        assertEquals("15 GB", special_offer_storage_label(16_106_127_360))
        assertEquals("1 TB", special_offer_storage_label(1_099_511_627_776))
    }
}
