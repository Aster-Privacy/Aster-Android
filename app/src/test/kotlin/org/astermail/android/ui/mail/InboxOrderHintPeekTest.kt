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

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InboxOrderHintPeekTest {
    private val subject = "Your order has shipped"
    private val preview = "Order #123-4567890-1234567 total: \$10.00 shipped, arriving Monday"

    @Before
    fun reset() {
        clear_inbox_order_hints()
    }

    @After
    fun cleanup() {
        clear_inbox_order_hints()
    }

    @Test
    fun peek_on_a_miss_reports_unknown_and_never_computes() {
        val first = peek_inbox_order_hint("m1", subject, preview)
        assertFalse(first.known)
        assertNull(first.hint)
        val second = peek_inbox_order_hint("m1", subject, preview)
        assertFalse(second.known)
    }

    @Test
    fun peek_returns_the_hint_once_it_is_computed_off_the_ui() {
        cached_inbox_order_hint("m2", subject, preview)
        val lookup = peek_inbox_order_hint("m2", subject, preview)
        assertTrue(lookup.known)
        assertNotNull(lookup.hint)
    }

    @Test
    fun peek_treats_a_changed_preview_as_unknown() {
        cached_inbox_order_hint("m3", subject, preview)
        assertFalse(peek_inbox_order_hint("m3", subject, "Lunch on Friday?").known)
    }

    @Test
    fun blank_mail_is_known_without_any_work() {
        val lookup = peek_inbox_order_hint("m4", "", "")
        assertTrue(lookup.known)
        assertNull(lookup.hint)
    }

    @Test
    fun peek_stays_fast_while_patterns_compile_on_another_thread() {
        val warm = Thread { detect_inbox_order_hint(subject, preview) }
        warm.start()
        val started = System.nanoTime()
        repeat(200) { peek_inbox_order_hint("p$it", subject, preview) }
        val elapsed_ms = (System.nanoTime() - started) / 1_000_000
        warm.join()
        assertTrue("peek took $elapsed_ms ms", elapsed_ms < 250)
    }
}
