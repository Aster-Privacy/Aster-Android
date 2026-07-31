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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.design.AsterTheme
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.ui.settings.detail.alias_export_dialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val LARGE_ALIAS_COUNT = 2_132

@RunWith(AndroidJUnit4::class)
class AliasExportDialogTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun large_state(): SettingsUiState = SettingsUiState(
        aliases = (0 until LARGE_ALIAS_COUNT).map { index ->
            AliasInfo(
                id = "alias_$index",
                encrypted_local_part = "user.$index",
                domain = "astermail.org",
                created_at = "2026-07-31T00:00:00Z",
                updated_at = "2026-07-31T00:00:00Z",
            )
        },
    )

    @Test
    fun dialog_opens_with_two_thousand_aliases() {
        compose_rule.setContent {
            AsterTheme {
                alias_export_dialog(
                    state = large_state(),
                    on_dismiss = {},
                    on_load_directories = {},
                    on_load_ghost_aliases = {},
                )
            }
        }

        compose_rule
            .onNodeWithText(context.getString(R.string.alias_export_title))
            .assertIsDisplayed()
        compose_rule
            .onNodeWithText(context.getString(R.string.alias_export_source_aliases))
            .assertIsDisplayed()
    }

    @Test
    fun confirm_step_renders_and_export_writes_archive() {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("aster-aliases-export-") }
            ?.forEach { it.delete() }

        var dismissed = false
        compose_rule.setContent {
            AsterTheme {
                alias_export_dialog(
                    state = large_state(),
                    on_dismiss = { dismissed = true },
                    on_load_directories = {},
                    on_load_ghost_aliases = {},
                )
            }
        }

        compose_rule.onNodeWithText(context.getString(R.string.next)).performClick()
        compose_rule.waitForIdle()

        compose_rule
            .onNodeWithText(context.getString(R.string.alias_export_warning_title))
            .assertIsDisplayed()

        compose_rule
            .onNodeWithText(context.getString(R.string.alias_export_download))
            .performClick()

        compose_rule.waitUntil(timeoutMillis = 60_000) { dismissed }

        val archive = context.cacheDir.listFiles()
            ?.firstOrNull { it.name.startsWith("aster-aliases-export-") }
        assertTrue(archive != null && archive.length() > 0)
    }
}
