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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.tags.TagItem
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.mail.InboxItem
import org.astermail.android.ui.capture_screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetailFolderLabelChipsTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun sample_item(
        is_trashed: Boolean = false,
        is_archived: Boolean = false,
        is_spam: Boolean = false,
        labels: List<String> = emptyList(),
        item_type: String? = null,
    ) = InboxItem(
        id = "item-1",
        thread_token = "thread-1",
        thread_message_count = 1,
        sender_name = "Aster",
        sender_email = "team@astermail.org",
        subject = "Weekly digest",
        preview = "Hello there",
        timestamp = "2026-07-30T10:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = is_trashed,
        is_archived = is_archived,
        is_spam = is_spam,
        labels = labels,
        tag_tokens = emptyList(),
        raw_item = MailItem(id = "item-1", item_type = item_type),
    )

    private fun custom_folder(token: String, name: String) = LabelItem(
        id = token,
        label_token = token,
        encrypted_name = name,
        folder_type = "folder",
    )

    private fun sample_tag(token: String, name: String, icon: String?) = TagItem(
        id = token,
        tag_token = token,
        encrypted_name = name,
        name_nonce = "nonce",
        encrypted_color = "#22C55E",
        encrypted_icon = icon,
    )

    private fun render_row(
        item: InboxItem,
        folders: List<LabelItem>,
        tags: List<TagItem>,
        is_spam: Boolean = false,
        is_trashed: Boolean = false,
    ) {
        compose_rule.setContent {
            AsterTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AsterMaterial.colors.bg_primary),
                ) {
                    Text(
                        text = item.subject,
                        color = AsterMaterial.colors.text_primary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    val folder_chip = detail_folder_chip_for(
                        item = item,
                        folders = folders,
                        is_spam = is_spam,
                        is_trashed = is_trashed,
                    )
                    if (folder_chip != null || tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (folder_chip != null) {
                                detail_folder_chip(folder_chip)
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                tags.forEach { tag -> detail_label_chip(tag) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun folder_chip_sits_left_of_label_chip() {
        render_row(
            item = sample_item(labels = listOf("f1")),
            folders = listOf(custom_folder("f1", "Receipts")),
            tags = listOf(sample_tag("t1", "Newspaper", "bookmark")),
        )

        compose_rule.onNodeWithText("Receipts").assertIsDisplayed()
        compose_rule.onNodeWithText("Newspaper").assertIsDisplayed()
        capture_screenshot("detail_folder_and_label_chips", compose_rule.onRoot())

        val folder_bounds = compose_rule.onNodeWithTag("detail_folder_chip")
            .fetchSemanticsNode().boundsInRoot
        val label_bounds = compose_rule.onNodeWithTag("detail_label_chip")
            .fetchSemanticsNode().boundsInRoot
        val root_bounds = compose_rule.onRoot().fetchSemanticsNode().boundsInRoot

        assertTrue(
            "folder chip ($folder_bounds) must sit left of label chip ($label_bounds)",
            folder_bounds.right <= label_bounds.left,
        )
        assertTrue(
            "folder chip must hug the left edge",
            folder_bounds.left - root_bounds.left < root_bounds.width / 3f,
        )
        assertTrue(
            "label chip must hug the right edge",
            root_bounds.right - label_bounds.right < root_bounds.width / 3f,
        )
    }

    @Test
    fun folder_chip_shows_system_folder_when_no_custom_folder() {
        render_row(
            item = sample_item(is_archived = true),
            folders = emptyList(),
            tags = emptyList(),
        )
        compose_rule.onNodeWithText("Archive").assertIsDisplayed()
        capture_screenshot("detail_folder_chip_archive", compose_rule.onRoot())
    }

    @Test
    fun trash_and_spam_win_over_custom_folder() {
        render_row(
            item = sample_item(is_trashed = true, labels = listOf("f1")),
            folders = listOf(custom_folder("f1", "Receipts")),
            tags = emptyList(),
            is_trashed = true,
        )
        compose_rule.onNodeWithText("Trash").assertIsDisplayed()
        compose_rule.onNodeWithText("Receipts").assertDoesNotExist()
    }

    @Test
    fun folder_resolution_priority_is_stable() {
        assertEquals("archive", detail_system_folder_id(sample_item(is_archived = true)))
        assertEquals("drafts", detail_system_folder_id(sample_item(item_type = "draft")))
        assertEquals("scheduled", detail_system_folder_id(sample_item(item_type = "scheduled")))
        assertEquals("sent", detail_system_folder_id(sample_item(item_type = "sent")))
        assertEquals("inbox", detail_system_folder_id(sample_item()))
        assertEquals(
            "f1",
            detail_custom_folder(
                sample_item(labels = listOf("f1")),
                listOf(custom_folder("f1", "Receipts")),
            )?.label_token,
        )
    }
}
