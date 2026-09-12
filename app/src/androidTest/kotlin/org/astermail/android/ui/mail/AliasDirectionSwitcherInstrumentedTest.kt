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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.mail.alias_direction_all
import org.astermail.android.mail.alias_direction_received
import org.astermail.android.mail.alias_direction_sent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AliasDirectionSwitcherInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val changes = mutableListOf<String>()

    private fun render(dark: Boolean, initial: String = alias_direction_all) {
        compose.setContent {
            var value by remember { mutableStateOf(initial) }
            AsterTheme(use_dark_theme = dark) {
                Box(
                    modifier = Modifier
                        .width(400.dp)
                        .background(AsterMaterial.colors.bg_primary)
                        .padding(16.dp),
                ) {
                    alias_direction_switcher(
                        value = value,
                        on_change = {
                            changes.add(it)
                            value = it
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun segment(id: String) = compose.onNodeWithTag("alias_direction_$id")

    private fun save(name: String) {
        val bitmap = runCatching { compose.onRoot().captureToImage().asAndroidBitmap() }
            .getOrNull() ?: return
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun only_the_current_direction_is_selected() {
        render(dark = false, initial = alias_direction_received)

        segment(alias_direction_all).assertIsNotSelected()
        segment(alias_direction_received).assertIsSelected()
        segment(alias_direction_sent).assertIsNotSelected()
    }

    @Test
    fun tapping_a_segment_moves_the_selection() {
        render(dark = true)
        save("alias_direction_dark_all")

        segment(alias_direction_sent).performClick()
        compose.waitForIdle()

        assertEquals(listOf(alias_direction_sent), changes)
        segment(alias_direction_sent).assertIsSelected()
        segment(alias_direction_all).assertIsNotSelected()
        save("alias_direction_dark_sent")
    }

    @Test
    fun tapping_the_selected_segment_does_nothing() {
        render(dark = false)
        save("alias_direction_light_all")

        segment(alias_direction_all).performClick()
        compose.waitForIdle()

        assertEquals(emptyList<String>(), changes)
    }

    @Test
    fun an_unknown_value_falls_back_to_all() {
        render(dark = false, initial = "bogus")

        segment(alias_direction_all).assertIsSelected()
    }
}
