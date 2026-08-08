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

package org.astermail.android.ui.mail

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ChipAndDetailsScreenshotTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val alias = "thehindu.3month@aster.cx"

    private val alias_entries = listOf(AliasLabelEntry(alias))

    private val palette = listOf(
        Color(0xFFEF4444) to "Urgent",
        Color(0xFFF59E0B) to "Receipts",
        Color(0xFF22C55E) to "Travel",
        Color(0xFF3B82F6) to "Work",
        Color(0xFFA855F7) to "Family",
    )

    @Before
    fun seed_alias_indicator_store() {
        alias_indicator_store.set_enabled(true)
        alias_indicator_store.set_labels(build_alias_label_map(alias_entries))
        alias_indicator_store.set_token_labels(build_alias_token_label_map(alias_entries))
    }

    private fun save_screenshot(name: String) {
        val bitmap = compose_rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun save_device_screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = instrumentation.targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun row(index: Int, color: Color, name: String): ThreadRow {
        val email = Email(
            id = "m$index",
            sender_name = "The Hindu",
            sender_email = "news@thehindu.com",
            subject = "Morning briefing",
            preview = "Today's headlines",
            received_at = 1_000L,
            is_read = index % 2 == 0,
            is_starred = false,
            has_attachment = false,
            received_on = alias,
            routing_token = null,
        )
        return ThreadRow(
            thread_id = "t$index",
            newest = email,
            message_count = 1,
            has_unread = index % 2 != 0,
            has_encrypted = false,
            total_trackers = 0,
            has_attachment = false,
            is_starred = false,
            label_colors = listOf(color),
            label_names = listOf(name),
            label_icons = listOf(""),
        )
    }

    private fun capture_rows(name: String, dark: Boolean) {
        compose_rule.setContent {
            AsterTheme(use_dark_theme = dark) {
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .background(AsterMaterial.colors.bg_primary),
                ) {
                    palette.forEachIndexed { index, entry ->
                        ThreadInboxRow(
                            thread = row(index, entry.first, entry.second),
                            on_click = {},
                            on_long_click = {},
                            on_toggle_star = {},
                        )
                    }
                }
            }
        }
        compose_rule.waitForIdle()
        save_screenshot(name)
    }

    @Test
    fun capture_inbox_chips_light() {
        capture_rows("inbox_chips_light", dark = false)
    }

    @Test
    fun capture_inbox_chips_dark() {
        capture_rows("inbox_chips_dark", dark = true)
    }

    private fun capture_details(name: String, dark: Boolean) {
        compose_rule.setContent {
            AsterTheme(use_dark_theme = dark) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AsterMaterial.colors.bg_primary),
                ) {
                    message_details_panel(
                        sender = "The Hindu <news@thehindu.com>",
                        reply_to = "noreply@thehindu.com",
                        is_encrypted = true,
                        tracker_count = 3,
                        date_text = "8 August 2026 at 09:14",
                        received_on = "thehindu.3month@aster.cx",
                        authentication = "SPF pass, DKIM pass, DMARC pass",
                        authentication_failed = false,
                        on_show_trackers = {},
                    )
                }
            }
        }
        compose_rule.waitForIdle()
        save_screenshot(name)
    }

    private fun capture_security_dialog(name: String, dark: Boolean) {
        compose_rule.setContent {
            AsterTheme(use_dark_theme = dark) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AsterMaterial.colors.bg_primary),
                ) {
                    message_details_panel(
                        sender = "The Hindu <news@thehindu.com>",
                        reply_to = "noreply@thehindu.com",
                        is_encrypted = true,
                        tracker_count = 3,
                        date_text = "8 August 2026 at 09:14",
                        received_on = "thehindu.3month@aster.cx",
                        authentication = "SPF pass, DKIM pass, DMARC pass",
                        authentication_failed = false,
                        on_show_trackers = {},
                    )
                }
            }
        }
        compose_rule.onNodeWithText("View encryption details").performClick()
        compose_rule.waitForIdle()
        save_device_screenshot(name)
    }

    @Test
    fun capture_security_dialog_light() {
        capture_security_dialog("security_dialog_light", dark = false)
    }

    @Test
    fun capture_security_dialog_dark() {
        capture_security_dialog("security_dialog_dark", dark = true)
    }

    @Test
    fun capture_details_panel_light() {
        capture_details("details_panel_light", dark = false)
    }

    @Test
    fun capture_details_panel_dark() {
        capture_details("details_panel_dark", dark = true)
    }
}
