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


package org.astermail.android.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.mail.inbox_leading_slot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxLeadingSlotInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val avatar_tag = "leading_slot_avatar"

    private fun render(on_avatar_click: (() -> Unit)?) {
        compose.setContent {
            AsterTheme {
                Row(modifier = Modifier.background(Color.White)) {
                    inbox_leading_slot(
                        show_pictures = true,
                        is_selected = false,
                        avatar_size = 40.dp,
                        on_avatar_click = on_avatar_click,
                    ) {
                        Box(modifier = Modifier.size(40.dp).testTag(avatar_tag)) {
                            Box(Modifier.size(20.dp).align(Alignment.TopStart).background(Color.Red))
                            Box(Modifier.size(20.dp).align(Alignment.BottomEnd).background(Color.Red))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assert_corners_drawn() {
        val pixels = compose.onNodeWithTag(avatar_tag, useUnmergedTree = true).captureToImage().toPixelMap()
        assertEquals(Color.Red.toArgb(), pixels[1, 1].toArgb())
        assertEquals(Color.Red.toArgb(), pixels[pixels.width - 2, pixels.height - 2].toArgb())
    }

    @Test
    fun tappable_slot_keeps_corner_avatars_visible() {
        render(on_avatar_click = {})
        assert_corners_drawn()
    }

    @Test
    fun static_slot_keeps_corner_avatars_visible() {
        render(on_avatar_click = null)
        assert_corners_drawn()
    }
}
