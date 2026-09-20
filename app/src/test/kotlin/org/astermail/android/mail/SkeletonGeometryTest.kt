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

import org.astermail.android.ui.mail.SkeletonGeometry
import org.astermail.android.ui.mail.SkeletonPhase
import org.astermail.android.ui.mail.next_skeleton_geometry
import org.junit.Assert.assertEquals
import org.junit.Test

class SkeletonGeometryTest {

    private val comfortable = SkeletonGeometry(list_density = null, show_avatar = true, show_preview = true)
    private val compact = SkeletonGeometry(list_density = "compact", show_avatar = false, show_preview = false)

    @Test
    fun keeps_geometry_while_the_skeleton_is_on_screen() {
        assertEquals(comfortable, next_skeleton_geometry(comfortable, compact, SkeletonPhase.skeleton))
    }

    @Test
    fun adopts_geometry_once_the_skeleton_is_gone() {
        assertEquals(compact, next_skeleton_geometry(comfortable, compact, SkeletonPhase.content))
        assertEquals(compact, next_skeleton_geometry(comfortable, compact, SkeletonPhase.blank))
    }

    @Test
    fun keeps_geometry_until_preferences_load() {
        assertEquals(compact, next_skeleton_geometry(compact, null, SkeletonPhase.content))
        assertEquals(compact, next_skeleton_geometry(compact, null, SkeletonPhase.skeleton))
    }
}
