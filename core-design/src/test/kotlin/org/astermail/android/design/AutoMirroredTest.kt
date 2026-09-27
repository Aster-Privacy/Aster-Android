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


package org.astermail.android.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMirroredTest {
    private fun sample_icon(): ImageVector =
        ImageVector.Builder(
            name = "sample",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addGroup(name = "outer", rotate = 15f)
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(4f, 12f)
                lineTo(20f, 12f)
            }
            clearGroup()
            path(fill = SolidColor(Color.Red)) {
                moveTo(0f, 0f)
                lineTo(2f, 2f)
                close()
            }
        }.build()

    @Test
    fun mirrored_copy_flips_in_rtl_and_keeps_every_node() {
        val icon = sample_icon()
        val mirrored = icon.auto_mirrored()

        assertFalse(icon.autoMirror)
        assertTrue(mirrored.autoMirror)
        assertEquals(icon.name, mirrored.name)
        assertEquals(icon.viewportWidth, mirrored.viewportWidth)
        assertEquals(icon.root.size, mirrored.root.size)

        val group = mirrored.root[0] as VectorGroup
        assertEquals("outer", group.name)
        assertEquals(15f, group.rotation)
        val inner = group[0] as VectorPath
        assertEquals(2f, inner.strokeLineWidth)
        assertEquals((icon.root[0] as VectorGroup)[0].let { (it as VectorPath).pathData }, inner.pathData)
        assertEquals(SolidColor(Color.Red), (mirrored.root[1] as VectorPath).fill)
    }

    @Test
    fun mirrored_icons_are_cached_and_idempotent() {
        val icon = sample_icon()
        val mirrored = icon.auto_mirrored()

        assertSame(mirrored, icon.auto_mirrored())
        assertSame(mirrored, mirrored.auto_mirrored())
    }
}
