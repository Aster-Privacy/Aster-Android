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

package org.astermail.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPreviewSubjectTest {

    private fun strip(subject: String, preview: String): String =
        MailPollingWorker.preview_without_subject(subject, preview)

    @Test
    fun body_that_opens_with_the_subject_drops_the_repeat() {
        assertEquals(
            "Here is what changed this week.",
            strip(
                "Tutu (Formerly Walt) Release Notes",
                "Tutu (Formerly Walt) Release Notes Here is what changed this week.",
            ),
        )
    }

    @Test
    fun match_ignores_case_and_whitespace_and_separators() {
        assertEquals(
            "Doors open at 9.",
            strip("Big  Sale Today", "big sale\ntoday - Doors open at 9."),
        )
    }

    @Test
    fun body_identical_to_subject_shows_nothing_extra() {
        assertEquals("", strip("Weekly digest", "Weekly digest"))
    }

    @Test
    fun body_that_is_a_cut_off_subject_shows_nothing_extra() {
        assertEquals("", strip("A very long subject line here", "A very long subj"))
    }

    @Test
    fun unrelated_body_is_kept_whole() {
        assertEquals(
            "Can we meet tomorrow?",
            strip("Lunch", "Can we meet tomorrow?"),
        )
    }

    @Test
    fun body_that_only_shares_a_prefix_is_kept_whole() {
        assertEquals(
            "Lunch plans changed, see below",
            strip("Lunch tomorrow", "Lunch plans changed, see below"),
        )
    }

    @Test
    fun subject_matching_only_part_of_a_word_keeps_body() {
        assertEquals("Lunchtime moved to 1", strip("Lunch", "Lunchtime moved to 1"))
    }

    @Test
    fun blank_body_is_empty() {
        assertEquals("", strip("Hello", "   "))
    }

    @Test
    fun blank_subject_keeps_body() {
        assertEquals("Body text", strip("", "Body text"))
    }
}
