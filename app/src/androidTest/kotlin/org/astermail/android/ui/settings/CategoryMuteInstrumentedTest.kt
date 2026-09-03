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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.category_settings_section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryMuteInstrumentedTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val toggled = mutableListOf<String>()
    private val enabled_changes = mutableListOf<List<String>>()

    private fun render(
        enabled: List<String> = listOf("promotions", "social", "updates"),
        muted: List<String> = emptyList(),
    ) {
        compose_rule.setContent {
            AsterTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AsterMaterial.colors.bg_primary)
                        .verticalScroll(rememberScrollState()),
                ) {
                    category_settings_section(
                        enabled_categories = enabled,
                        custom_categories = emptyList(),
                        custom_category_limit = 0,
                        muted_categories = muted,
                        on_enabled_change = { enabled_changes.add(it) },
                        on_custom_change = {},
                        on_toggle_muted = { toggled.add(it) },
                        on_upgrade = {},
                    )
                }
            }
        }
    }

    @Test
    fun an_enabled_category_offers_a_mute_control() {
        render()
        compose_rule
            .onNodeWithContentDescription("Mute notifications for Promotions")
            .assertIsDisplayed()
    }

    @Test
    fun muting_a_category_reports_only_that_category() {
        render()
        compose_rule
            .onNodeWithContentDescription("Mute notifications for Social")
            .performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("social"), toggled)
        assertTrue(enabled_changes.isEmpty())
    }

    @Test
    fun a_muted_category_offers_the_reverse_control() {
        render(muted = listOf("updates"))
        compose_rule
            .onNodeWithContentDescription("Unmute notifications for Updates")
            .performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("updates"), toggled)
    }

    @Test
    fun a_disabled_category_cannot_be_muted() {
        render(enabled = listOf("social", "updates"))
        compose_rule
            .onNodeWithContentDescription("Mute notifications for Promotions")
            .performClick()
        compose_rule.waitForIdle()

        assertTrue(toggled.isEmpty())
    }
}
