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

import org.astermail.android.ui.mail.skeleton_defer_ms
import org.astermail.android.ui.mail.skeleton_visible_after
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayedSkeletonTest {

    @Test
    fun `a load under 150ms never shows the skeleton`() {
        (0L until 150L).forEach { elapsed ->
            assertFalse(skeleton_visible_after(has_data = false, pending = true, pending_for_ms = elapsed))
        }
        assertTrue(skeleton_defer_ms >= 150L)
    }

    @Test
    fun `a slow load without data shows the skeleton`() {
        assertTrue(skeleton_visible_after(has_data = false, pending = true, pending_for_ms = skeleton_defer_ms))
    }

    @Test
    fun `existing data is never replaced by the skeleton`() {
        assertFalse(skeleton_visible_after(has_data = true, pending = true, pending_for_ms = 10_000L))
        assertFalse(skeleton_visible_after(has_data = false, pending = false, pending_for_ms = 10_000L))
    }
}
