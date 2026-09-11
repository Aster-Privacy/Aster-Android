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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyReloadPolicyTest {

    private fun run_watchdog_cycle(
        policy: body_reload_policy,
        painted: Boolean,
        visual_ready: Boolean,
        content_height: Int,
    ): Pair<Int, Boolean> {
        policy.begin_load()
        var reloads = 0
        while (policy.reloads_remaining()) {
            if (!policy.should_reload(painted, visual_ready, content_height)) return reloads to false
            reloads++
        }
        return reloads to policy.should_regenerate(painted, visual_ready, content_height)
    }

    @Test
    fun painted_page_never_reloads_or_regenerates() {
        val policy = body_reload_policy()
        repeat(20) {
            val (reloads, regenerated) = run_watchdog_cycle(policy, painted = true, visual_ready = false, content_height = 0)
            assertEquals(0, reloads)
            assertFalse(regenerated)
        }
    }

    @Test
    fun painted_page_waiting_on_slow_images_is_not_reloaded() {
        val policy = body_reload_policy()
        assertFalse(policy.should_reload(painted = true, visual_ready = false, content_height = 0))
        assertFalse(policy.should_regenerate(painted = true, visual_ready = false, content_height = 0))
        assertFalse(policy.should_reload(painted = false, visual_ready = false, content_height = 480))
        assertFalse(policy.should_regenerate(painted = false, visual_ready = false, content_height = 480))
    }

    @Test
    fun never_painted_page_reloads_twice_per_load_and_regenerates_once() {
        val policy = body_reload_policy()
        var total_regenerations = 0
        repeat(10) {
            val (reloads, regenerated) = run_watchdog_cycle(policy, painted = false, visual_ready = false, content_height = 0)
            assertTrue(reloads <= 2)
            if (regenerated) total_regenerations++
        }
        assertEquals(1, total_regenerations)
    }

    @Test
    fun renderer_gone_regeneration_is_capped() {
        val policy = body_reload_policy()
        val actions = List(10) { policy.on_renderer_gone() }
        assertEquals(renderer_gone_action.regenerate, actions[0])
        assertEquals(renderer_gone_action.regenerate, actions[1])
        assertEquals(renderer_gone_action.plain_text_fallback, actions[2])
        assertTrue(actions.drop(3).all { it == renderer_gone_action.stop })
    }

    @Test
    fun renderer_gone_after_stop_never_regenerates_again() {
        val policy = body_reload_policy()
        while (policy.on_renderer_gone() != renderer_gone_action.stop) Unit
        repeat(50) {
            assertEquals(renderer_gone_action.stop, policy.on_renderer_gone())
            val (_, regenerated) = run_watchdog_cycle(policy, painted = false, visual_ready = false, content_height = 0)
            assertFalse(regenerated)
        }
    }

    @Test
    fun watchdog_and_renderer_gone_share_one_regeneration_budget() {
        val policy = body_reload_policy()
        assertTrue(run_watchdog_cycle(policy, painted = false, visual_ready = false, content_height = 0).second)
        assertEquals(renderer_gone_action.regenerate, policy.on_renderer_gone())
        assertEquals(renderer_gone_action.plain_text_fallback, policy.on_renderer_gone())
        assertEquals(renderer_gone_action.stop, policy.on_renderer_gone())
        assertFalse(run_watchdog_cycle(policy, painted = false, visual_ready = false, content_height = 0).second)
    }
}
