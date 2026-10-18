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


package org.astermail.android.ui.theme

import android.graphics.drawable.Drawable
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeFrameCallbackTest {

    private val drawable = mockk<Drawable>(relaxed = true)

    @Test
    fun each_invalidation_requests_exactly_one_redraw() {
        var frames = 0
        val callback = ThemeFrameCallback(on_frame = { frames++ }, post_at = { _, _ -> }, remove = {})
        assertEquals(0, frames)
        callback.invalidateDrawable(drawable)
        assertEquals(1, frames)
        callback.invalidateDrawable(drawable)
        callback.invalidateDrawable(drawable)
        assertEquals(3, frames)
    }

    @Test
    fun scheduled_frame_is_posted_at_the_requested_time_without_redrawing() {
        var frames = 0
        val posted = mutableListOf<Pair<Runnable, Long>>()
        val callback = ThemeFrameCallback(
            on_frame = { frames++ },
            post_at = { what, time -> posted.add(what to time) },
            remove = {},
        )
        val next = Runnable { callback.invalidateDrawable(drawable) }
        callback.scheduleDrawable(drawable, next, 4_200L)
        assertEquals(1, posted.size)
        assertSame(next, posted[0].first)
        assertEquals(4_200L, posted[0].second)
        assertEquals(0, frames)
        posted[0].first.run()
        assertEquals(1, frames)
    }

    @Test
    fun unscheduled_frame_is_removed() {
        val removed = mutableListOf<Runnable>()
        val callback = ThemeFrameCallback(on_frame = {}, post_at = { _, _ -> }, remove = { removed.add(it) })
        val next = Runnable {}
        callback.unscheduleDrawable(drawable, next)
        assertEquals(listOf(next), removed)
    }
}
