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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSettingRemovedTest {

    @Test
    fun the_settings_list_has_no_connection_row() {
        for (is_family in listOf(true, false)) {
            val ids = build_settings_sections(is_family).flatMap { section -> section.rows.map { it.id } }
            assertTrue(ids.contains("security"))
            assertFalse(ids.contains("connection"))
        }
    }

    @Test
    fun settings_search_cannot_reach_a_connection_screen() {
        assertTrue(settings_search_index.any { it.screen_id == "security" })
        assertFalse(settings_search_index.any { it.screen_id == "connection" })
    }
}
