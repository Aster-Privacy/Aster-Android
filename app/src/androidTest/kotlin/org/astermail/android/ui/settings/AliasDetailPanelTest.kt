// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.astermail.android.R
import org.astermail.android.api.aliases.AliasRule
import org.astermail.android.api.aliases.AliasRuleActions
import org.astermail.android.api.aliases.AliasRuleCondition
import org.astermail.android.api.aliases.AliasStatsResponse
import org.astermail.android.api.aliases.SENDER_PIN_MODE_ALLOWLIST
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.design.AsterTheme
import org.astermail.android.settings.AliasDetailState
import org.astermail.android.settings.DecryptedAliasContact
import org.astermail.android.settings.DecryptedAliasPin
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.settings.detail.alias_detail_panel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AliasDetailPanelTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun save_screenshot(name: String, node: SemanticsNodeInteraction = compose_rule.onRoot()) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun sample_alias() = AliasInfo(
        id = "alias-1",
        encrypted_local_part = "shopping",
        domain = "astermail.org",
        encrypted_display_name = "Shopping",
        encrypted_note = "Used for online orders",
        encrypted_websites = "example.com",
        created_at = "2026-03-04T10:00:00Z",
    )

    private fun sample_detail() = AliasDetailState(
        loading = false,
        loaded = true,
        stats = AliasStatsResponse(
            received = 42,
            forwarded = 40,
            blocked = 2,
            replied = 5,
            created_at = "2026-03-04T10:00:00Z",
        ),
        pin_mode = SENDER_PIN_MODE_ALLOWLIST,
        pins = listOf(
            DecryptedAliasPin(id = "p1", sender = "orders@example.com", is_blocked = false),
        ),
        contacts = listOf(
            DecryptedAliasContact(id = "c1", contact = "support@example.com", is_blocked = false),
        ),
        rules = listOf(
            AliasRule(
                id = "r1",
                conditions = listOf(
                    AliasRuleCondition(field = "subject", operator = "contains", value = "receipt"),
                ),
                actions = AliasRuleActions(label = "Receipts"),
                is_enabled = true,
            ),
        ),
    )

    private fun set_panel(detail: AliasDetailState = sample_detail()) {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(SettingsUiState())
        compose_rule.setContent {
            AsterTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    alias_detail_panel(alias = sample_alias(), detail = detail, vm = vm)
                }
            }
        }
        compose_rule.waitForIdle()
    }

    @Test
    fun expanded_panel_renders_every_settings_section() {
        set_panel()

        compose_rule.onNodeWithTag("alias_detail_panel").assertIsDisplayed()
        save_screenshot("alias_panel_top")

        listOf(
            R.string.alias_panel_display_name,
            R.string.alias_delivery_folder_title,
            R.string.alias_sender_pinning_title,
            R.string.alias_blocked_log_title,
            R.string.alias_contacts_title,
        ).forEach { res ->
            compose_rule.onNodeWithText(context.getString(res)).performScrollTo().assertIsDisplayed()
        }

        save_screenshot("alias_panel_bottom")
    }

    @Test
    fun stats_section_shows_real_counts() {
        set_panel()

        compose_rule.onNodeWithText("42").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("40").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("5").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun existing_pins_rules_and_contacts_are_listed() {
        set_panel()

        compose_rule.onNodeWithText("orders@example.com").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("support@example.com").performScrollTo().assertIsDisplayed()
        compose_rule.onNodeWithText("receipt", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loading_state_hides_sections_until_detail_arrives() {
        set_panel(AliasDetailState(loading = true))

        compose_rule.onNodeWithTag("alias_detail_panel").assertIsDisplayed()
        compose_rule.onNodeWithText(context.getString(R.string.alias_sender_pinning_title))
            .assertDoesNotExist()
    }
}
