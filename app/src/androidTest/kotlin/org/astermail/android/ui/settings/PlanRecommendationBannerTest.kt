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

package org.astermail.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.compute_plan_recommendation
import org.astermail.android.ui.settings.detail.plan_recommendation_banner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanRecommendationBannerTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val resources =
        InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun gb(n: Long): Long = n * 1024L * 1024L * 1024L

    private fun render(
        plan_code: String,
        used: Long,
        limit: Long,
        current_name: String?,
        tier_name: String?,
    ) {
        val recommendation = compute_plan_recommendation(plan_code, used, limit)
        compose_rule.setContent {
            AsterTheme {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    plan_recommendation_banner(
                        recommendation = recommendation,
                        current_plan_name = current_name,
                        recommended_tier_name = tier_name,
                    )
                }
            }
        }
    }

    @Test
    fun top_family_plan_names_the_plan_in_the_recognition_banner() {
        render("family", gb(2900), gb(3000), "Family", null)

        compose_rule.onNodeWithText(resources.getString(R.string.settings_plan_top_tier_title))
            .assertIsDisplayed()
        compose_rule.onNodeWithText(
            resources.getString(R.string.settings_plan_top_tier_note, "Family"),
        ).assertIsDisplayed()
    }

    @Test
    fun top_individual_plan_names_the_plan_in_the_recognition_banner() {
        render("supernova", gb(10), gb(500), "Supernova", null)

        compose_rule.onNodeWithText(resources.getString(R.string.settings_plan_top_tier_title))
            .assertIsDisplayed()
        compose_rule.onNodeWithText(
            resources.getString(R.string.settings_plan_top_tier_note, "Supernova"),
        ).assertIsDisplayed()
    }

    @Test
    fun tight_storage_names_the_current_plan_and_the_next_tier() {
        render("star", gb(90), gb(100), "Star", "Nova")

        compose_rule.onNodeWithText(
            resources.getString(R.string.settings_plan_current_title, "Star"),
        ).assertIsDisplayed()
        compose_rule.onNodeWithText(
            resources.getString(R.string.settings_plan_storage_tight_note, 90, "Nova"),
        ).assertIsDisplayed()
    }

    @Test
    fun a_paid_user_with_room_to_spare_sees_their_own_plan() {
        render("star", gb(1), gb(100), "Star", "Nova")

        compose_rule.onNodeWithText(
            resources.getString(R.string.settings_plan_current_title, "Star"),
        ).assertIsDisplayed()
        compose_rule.onNodeWithText(
            resources.getString(R.string.settings_plan_current_note, 1),
        ).assertIsDisplayed()
        compose_rule.onNodeWithText(resources.getString(R.string.settings_plan_top_tier_title))
            .assertDoesNotExist()
    }

    @Test
    fun an_unpaid_user_sees_no_banner() {
        render("free", gb(9), gb(10), "Free", null)

        compose_rule.onNodeWithText(resources.getString(R.string.settings_plan_top_tier_title))
            .assertDoesNotExist()
        compose_rule.onNodeWithText(
            resources.getString(R.string.settings_plan_current_title, "Free"),
        ).assertDoesNotExist()
    }
}
