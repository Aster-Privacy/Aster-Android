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

package org.astermail.android.billing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

private const val mb = 1024L * 1024L

class AttachmentLimitsTest {
    private val prod_plans = listOf(
        "free" to 25L * mb,
        "star" to 50L * mb,
        "nova" to 100L * mb,
        "supernova" to 250L * mb,
        "duo" to 100L * mb,
        "family" to 100L * mb,
    )

    @Before
    fun setup() {
        AttachmentLimits.reset()
        AttachmentLimits.update_plan_limits(prod_plans)
    }

    @After
    fun teardown() {
        AttachmentLimits.reset()
    }

    @Test
    fun star_user_with_26mb_file_is_offered_nova() {
        AttachmentLimits.update(50L * mb)
        assertEquals(100L * mb, AttachmentLimits.upgrade_target_bytes(26L * mb))
    }

    @Test
    fun star_user_with_200mb_file_is_offered_supernova() {
        AttachmentLimits.update(50L * mb)
        assertEquals(250L * mb, AttachmentLimits.upgrade_target_bytes(200L * mb))
    }

    @Test
    fun file_larger_than_every_plan_falls_back_to_the_ceiling() {
        AttachmentLimits.update(50L * mb)
        assertEquals(250L * mb, AttachmentLimits.upgrade_target_bytes(900L * mb))
    }

    @Test
    fun supernova_user_is_never_offered_an_upgrade() {
        AttachmentLimits.update(250L * mb)
        assertFalse(AttachmentLimits.can_upgrade())
    }

    @Test
    fun unknown_plan_limits_fall_back_to_the_ceiling() {
        AttachmentLimits.reset()
        AttachmentLimits.update(50L * mb)
        AttachmentLimits.update_upgrade_ceiling(250L * mb)
        assertEquals(250L * mb, AttachmentLimits.upgrade_target_bytes(26L * mb))
    }
}
