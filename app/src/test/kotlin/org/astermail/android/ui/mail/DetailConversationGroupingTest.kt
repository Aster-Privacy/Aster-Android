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

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailConversationGroupingTest {

    @Test
    fun `the saved setting decides once preferences are loaded`() {
        assertEquals(false, detail_conversation_grouping(preference = false, preferences_locked = false, fallback = true))
        assertEquals(true, detail_conversation_grouping(preference = true, preferences_locked = false, fallback = false))
    }

    @Test
    fun `the last known choice is used before preferences load`() {
        assertEquals(false, detail_conversation_grouping(preference = null, preferences_locked = false, fallback = false))
        assertEquals(true, detail_conversation_grouping(preference = null, preferences_locked = false, fallback = true))
    }

    @Test
    fun `locked preferences never override the last known choice`() {
        assertEquals(false, detail_conversation_grouping(preference = true, preferences_locked = true, fallback = false))
    }
}
