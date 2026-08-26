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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.translation_settings_section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSettingsSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private var requested_mode: String? = null
    private var changed_mode: String? = null
    private var wifi_only_change: Boolean? = null
    private var packs_cleared = false

    private fun label(id: Int): String = context.getString(id)

    private fun render(mode: String, wifi_only: Boolean = true, pack_bytes: Long = 0L) {
        requested_mode = null
        changed_mode = null
        wifi_only_change = null
        packs_cleared = false

        compose.setContent {
            AsterTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    translation_settings_section(
                        context = context,
                        translate_incoming = mode,
                        translate_languages = setOf("en"),
                        translate_never = emptySet(),
                        wifi_only = wifi_only,
                        pack_bytes = pack_bytes,
                        on_request_mode = { requested_mode = it },
                        on_mode_change = { changed_mode = it },
                        on_languages_change = {},
                        on_never_change = {},
                        on_wifi_only_change = { wifi_only_change = it },
                        on_packs_cleared = { packs_cleared = true },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun turning_translation_on_asks_before_it_changes_the_setting() {
        render(mode = "off")

        compose.onNodeWithText(label(R.string.translate_mode_ask)).performClick()
        compose.waitForIdle()

        assertEquals("ask", requested_mode)
        assertNull(changed_mode)
    }

    @Test
    fun switching_between_modes_while_on_does_not_ask_again() {
        render(mode = "ask")

        compose.onNodeWithText(label(R.string.translate_mode_always)).performClick()
        compose.waitForIdle()

        assertEquals("always", changed_mode)
        assertNull(requested_mode)
    }

    @Test
    fun turning_translation_off_does_not_ask() {
        render(mode = "always")

        compose.onNodeWithText(label(R.string.translate_mode_off)).performClick()
        compose.waitForIdle()

        assertEquals("off", changed_mode)
        assertNull(requested_mode)
    }

    @Test
    fun the_wifi_only_setting_reports_the_value_it_is_switched_to() {
        render(mode = "ask", wifi_only = true)

        compose.onNodeWithText(label(R.string.translate_wifi_only_label))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        assertEquals(false, wifi_only_change)
    }

    @Test
    fun an_empty_device_reports_no_language_packs() {
        render(mode = "ask", pack_bytes = 0L)

        compose.onNodeWithText(label(R.string.translation_storage_empty))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun removing_every_pack_reports_the_cache_was_cleared() {
        render(mode = "ask", pack_bytes = 49_000_000L)

        compose.onNodeWithText(label(R.string.translation_storage_remove_all))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        assertTrue(packs_cleared)
    }
}
