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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxSkeletonPurgeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun purge_removes_legacy_rows_and_keeps_geometry() {
        val store = context.getSharedPreferences("aster_inbox_skeleton_geometry", Context.MODE_PRIVATE)
        store.edit()
            .putString("rows_inbox", "sender\u001fsubject\u001fpreview\u001f10:00\u001f1")
            .putString("rows_sent", "sender\u001fsubject\u001fpreview\u001f10:00\u001f0")
            .putString("list_density", "compact")
            .putInt("first_row_height_px", 180)
            .commit()

        purge_legacy_skeleton_rows(context)

        val reloaded = context.getSharedPreferences("aster_inbox_skeleton_geometry", Context.MODE_PRIVATE)
        assertFalse(reloaded.all.keys.any { it.startsWith("rows_") })
        assertEquals("compact", reloaded.getString("list_density", null))
        assertEquals(180, reloaded.getInt("first_row_height_px", 0))
    }
}
