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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.capture_screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun plan(
    code: String,
    price_cents: Int,
    yearly_price_cents: Int,
    aliases: Int,
    domains: Int,
    storage_gb: Long,
) = AvailablePlan(
    id = code,
    code = code,
    name = code.replaceFirstChar { it.uppercase() },
    storage_limit_bytes = storage_gb * 1024L * 1024L * 1024L,
    max_email_aliases = aliases,
    max_custom_domains = domains,
    price_cents = price_cents,
    yearly_price_cents = yearly_price_cents,
    billing_period = "month",
    is_current = false,
)

private val offered_plans = listOf(
    plan("star", 300, 2880, aliases = 10, domains = 1, storage_gb = 20),
    plan("nova", 600, 5760, aliases = 30, domains = 3, storage_gb = 100),
    plan("supernova", 1200, 11520, aliases = 100, domains = 10, storage_gb = 500),
)

@RunWith(AndroidJUnit4::class)
class UpgradePlanCardTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun render(billing_interval: String = "month") {
        compose_rule.setContent {
            AsterTheme {
                var selected by remember { mutableStateOf("nova") }
                Column(modifier = Modifier.padding(AsterSpacing.xl)) {
                    offered_plans.forEach { option ->
                        UpgradePlanCard(
                            plan = option,
                            is_selected = option.code == selected,
                            is_recommended = option.code == "nova",
                            billing_interval = billing_interval,
                            currency = "usd",
                            on_select = { selected = option.code },
                        )
                        Spacer(Modifier.height(AsterSpacing.md).fillMaxWidth())
                    }
                }
            }
        }
    }

    @Test
    fun shows_every_offered_plan_with_its_monthly_price() {
        render()

        compose_rule.onNodeWithText("Star").assertIsDisplayed()
        compose_rule.onNodeWithText("Nova").assertIsDisplayed()
        compose_rule.onNodeWithText("Supernova").assertIsDisplayed()
        compose_rule.onNodeWithText("$3.00", substring = true).assertIsDisplayed()
        compose_rule.onNodeWithText("$6.00", substring = true).assertIsDisplayed()
        compose_rule.onNodeWithText("$12.00", substring = true).assertIsDisplayed()

        capture_screenshot("upgrade_plan_cards_monthly", compose_rule.onRoot())
    }

    @Test
    fun shows_yearly_prices_when_the_year_period_is_selected() {
        render(billing_interval = "year")

        compose_rule.onNodeWithText("$28.80", substring = true).assertIsDisplayed()
        compose_rule.onNodeWithText("$57.60", substring = true).assertIsDisplayed()
        compose_rule.onNodeWithText("$115.20", substring = true).assertIsDisplayed()

        capture_screenshot("upgrade_plan_cards_yearly", compose_rule.onRoot())
    }

    @Test
    fun marks_the_recommended_plan() {
        render()

        compose_rule.onNodeWithText("Recommended", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun selects_a_plan_when_its_card_is_tapped() {
        render()

        compose_rule.onNodeWithText("Supernova").performClick()
        compose_rule.onNodeWithText("Supernova").assertIsDisplayed()

        capture_screenshot("upgrade_plan_cards_selected_supernova", compose_rule.onRoot())
    }
}
