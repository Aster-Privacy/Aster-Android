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

package org.astermail.android.ui.settings.mail_rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesContentTransitionTest {
    @Test
    fun leaving_the_skeleton_does_not_fade() {
        assertFalse(rules_content_animates(rules_content_loading_key, reduce_motion = false))
    }

    @Test
    fun content_changes_after_load_fade() {
        assertTrue(rules_content_animates(2, reduce_motion = false))
        assertTrue(rules_content_animates(1, reduce_motion = false))
    }

    @Test
    fun reduced_motion_never_fades() {
        assertFalse(rules_content_animates(2, reduce_motion = true))
        assertFalse(rules_content_animates(rules_content_loading_key, reduce_motion = true))
    }
}
