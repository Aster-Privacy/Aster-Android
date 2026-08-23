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

package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRecommendationTest {
    private fun gb(n: Long): Long = n * 1024L * 1024L * 1024L

    @Test
    fun `family plan codes map to the family ladder`() {
        assertEquals(plan_ladder_kind.FAMILY, plan_ladder_of("duo"))
        assertEquals(plan_ladder_kind.FAMILY, plan_ladder_of("family"))
    }

    @Test
    fun `everything else maps to the individual ladder`() {
        assertEquals(plan_ladder_kind.INDIVIDUAL, plan_ladder_of("free"))
        assertEquals(plan_ladder_kind.INDIVIDUAL, plan_ladder_of("supernova"))
        assertEquals(plan_ladder_kind.INDIVIDUAL, plan_ladder_of(null))
    }

    @Test
    fun `individual plans are ordered`() {
        assertEquals(0, plan_rank_of("free"))
        assertEquals(3, plan_rank_of("supernova"))
    }

    @Test
    fun `unknown plans rank below every known plan`() {
        assertEquals(-1, plan_rank_of("enterprise"))
        assertEquals(-1, plan_rank_of(null))
    }

    @Test
    fun `percentage is zero when the limit is missing`() {
        assertEquals(0.0, storage_percent_of(gb(1), 0L), 0.0001)
        assertEquals(0.0, storage_percent_of(gb(1), -1L), 0.0001)
    }

    @Test
    fun `percentage clamps to one hundred`() {
        assertEquals(100.0, storage_percent_of(gb(20), gb(10)), 0.0001)
    }

    @Test
    fun `users without a paid plan are recommended nova`() {
        val result = compute_plan_recommendation("free", 0L, gb(1))

        assertFalse(result.is_paid)
        assertEquals("nova", result.recommended_plan_code)
        assertFalse(result.suggest_storage_addon)
    }

    @Test
    fun `a paid user with room to spare is recommended nothing`() {
        val result = compute_plan_recommendation("star", gb(1), gb(100))

        assertNull(result.recommended_plan_code)
        assertFalse(result.storage_is_tight)
        assertFalse(result.suggest_storage_addon)
    }

    @Test
    fun `the next tier up is recommended when storage is tight`() {
        val result = compute_plan_recommendation("star", gb(90), gb(100))

        assertTrue(result.storage_is_tight)
        assertEquals("nova", result.recommended_plan_code)
    }

    @Test
    fun `a family subscriber is never recommended an individual plan`() {
        val result = compute_plan_recommendation("duo", gb(95), gb(100))

        assertEquals(plan_ladder_kind.FAMILY, result.ladder)
        assertEquals("family", result.recommended_plan_code)
    }

    @Test
    fun `the top family plan points at add-on storage`() {
        val result = compute_plan_recommendation("family", gb(2900), gb(3000))

        assertTrue(result.is_top_tier)
        assertNull(result.recommended_plan_code)
        assertTrue(result.suggest_storage_addon)
    }

    @Test
    fun `the top individual plan is acknowledged`() {
        val result = compute_plan_recommendation("supernova", gb(10), gb(500))

        assertTrue(result.is_top_tier)
        assertTrue(result.suggest_storage_addon)
        assertNull(result.recommended_plan_code)
    }

    @Test
    fun `an unknown plan code counts as unpaid`() {
        val result = compute_plan_recommendation("enterprise", gb(1), gb(10))

        assertFalse(result.is_paid)
        assertEquals("nova", result.recommended_plan_code)
    }

    @Test
    fun `a blank plan code counts as unpaid`() {
        val result = compute_plan_recommendation("", gb(1), gb(10))

        assertFalse(result.is_paid)
        assertEquals("nova", result.recommended_plan_code)
    }
}
