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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import compose.icons.TablerIcons
import compose.icons.tablericons.Language
import compose.icons.tablericons.World
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.common.label_icon_catalog
import org.astermail.android.ui.drawer.resolve_label_icon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabelIconRenderTest {

    @get:Rule
    val compose_rule = createComposeRule()

    @Test
    fun the_row_chip_resolves_globe_to_the_world_glyph_not_the_translate_glyph() {
        val resolved = material_icon_from_name("globe")

        assertNotNull("globe must resolve to an icon", resolved)
        assertEquals(TablerIcons.World, resolved)
        assertNotEquals(TablerIcons.Language, resolved)
        assertNotEquals(TablerIcons.World.name, TablerIcons.Language.name)
    }

    @Test
    fun the_row_chip_and_the_drawer_agree_on_every_catalog_icon() {
        label_icon_catalog.forEach { (key, vector) ->
            assertEquals(key, vector, material_icon_from_name(key))
            assertEquals(key, vector, resolve_label_icon(key))
        }
    }

    @Test
    fun an_inbox_row_renders_a_globe_label_chip() {
        compose_rule.setContent {
            AsterTheme {
                ThreadInboxRow(
                    thread = ThreadRow(
                        thread_id = "t1",
                        newest = Email(
                            id = "m1",
                            sender_name = "Acme Store",
                            sender_email = "orders@acme.test",
                            subject = "Your receipt",
                            preview = "Thanks for your order",
                            received_at = 1_770_000_000_000L,
                            is_read = true,
                            is_starred = false,
                            has_attachment = false,
                        ),
                        message_count = 1,
                        has_unread = false,
                        has_encrypted = false,
                        total_trackers = 0,
                        has_attachment = false,
                        is_starred = false,
                        label_colors = listOf(Color(0xFF3B82F6)),
                        label_names = listOf("Home"),
                        label_icons = listOf("globe"),
                    ),
                    on_click = {},
                    on_long_click = {},
                    on_toggle_star = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        compose_rule.onNodeWithText("Home", useUnmergedTree = true).assertExists()
    }
}
