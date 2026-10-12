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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.tags.TagItem
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CurrentTagChipScreenshotTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun tag(token: String, name: String, color: String) = TagItem(
        id = "id_$token",
        tag_token = token,
        encrypted_name = name,
        name_nonce = "nonce_$token",
        encrypted_color = color,
        encrypted_icon = "",
    )

    private val tags = listOf(
        tag("receipts", "Receipts", "#F59E0B"),
        tag("travel", "Travel", "#22C55E"),
    )

    private fun item(index: Int, tag_tokens: List<String>) = InboxItem(
        id = "m$index",
        thread_token = null,
        thread_message_count = 1,
        sender_name = "Acme Store",
        sender_email = "orders@acme.test",
        subject = "Order confirmation $index",
        preview = "Thanks for shopping with us",
        timestamp = "2026-09-20T10:00:00Z",
        is_read = index % 2 == 0,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        tag_tokens = tag_tokens,
        raw_item = MailItem(id = "m$index"),
    )

    private val items = listOf(
        item(1, listOf("receipts")),
        item(2, listOf("receipts", "travel")),
        item(3, listOf("receipts")),
    )

    private fun render(folder: String) {
        val rows = flat_thread_rows(
            items.map { inbox_item_to_email(it, tags, hidden_tag_token = current_tag_token(folder)) },
        )
        compose_rule.setContent {
            AsterTheme(use_dark_theme = false) {
                Column(
                    modifier = Modifier
                        .width(400.dp)
                        .background(AsterMaterial.colors.bg_primary),
                ) {
                    rows.forEach { row ->
                        ThreadInboxRow(
                            thread = row,
                            on_click = {},
                            on_long_click = {},
                            on_toggle_star = {},
                        )
                    }
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun chip_count(name: String): Int =
        compose_rule.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun save_screenshot(name: String) {
        val bitmap = compose_rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun inbox_rows_show_every_tag_chip() {
        render("inbox")
        save_screenshot("current_tag_chip_inbox")

        assertEquals(3, chip_count("Receipts"))
        assertEquals(1, chip_count("Travel"))
    }

    @Test
    fun tag_view_rows_hide_only_the_viewed_tag_chip() {
        render("tag:receipts")
        save_screenshot("current_tag_chip_tag_view")

        assertEquals(0, chip_count("Receipts"))
        assertEquals(1, chip_count("Travel"))
    }
}
