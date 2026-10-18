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

class MessageContentAnalysisTest {

    private val body =
        """<html><head><link rel="stylesheet" href="https://cdn.example.org/a.css">""" +
            """<style>@font-face { font-family: X; src: url("https://fonts.example.net/x.woff2"); }</style></head>""" +
            """<body><p>Hi<img src="https://track.example.com/o/1.gif" width="1" height="1"></p>""" +
            """<img src="https://cdn.example.org/hero.png" width="600" height="200">""" +
            """<a href="https://shop.example.com/?utm_source=news">Shop</a></body></html>"""

    @Test
    fun one_pass_matches_the_separate_tracker_and_banner_counts() {
        val report = EmailHtmlSanitizer.analyze_trackers(body)
        val counts = count_external_content(body, report)

        val analysis = analyze_message_content(body)

        assertEquals(report, analysis.tracker_report)
        assertEquals(counts, analysis.external_counts)
        assertEquals(1, analysis.external_counts.tracker_count)
        assertEquals(1, analysis.external_counts.image_count)
        assertEquals(1, analysis.external_counts.font_count)
        assertEquals(1, analysis.external_counts.css_count)
    }

    @Test
    fun a_missing_body_reports_nothing() {
        val analysis = analyze_message_content(null)

        assertEquals(EmailHtmlSanitizer.TrackerReport(), analysis.tracker_report)
        assertEquals(ExternalContentCounts(0, 0, 0, 0), analysis.external_counts)
        assertEquals(MessageContentAnalysis(), analysis)
    }

    @Test
    fun a_cached_body_skips_sanitizing_when_translation_is_off() {
        assertFalse(needs_cached_translation_source(null, translate_active = false))
        assertFalse(needs_cached_translation_source("<p>a</p>", translate_active = false))
    }

    @Test
    fun a_cached_body_still_builds_the_translation_source_once() {
        assertTrue(needs_cached_translation_source(null, translate_active = true))
        assertFalse(needs_cached_translation_source("<p>a</p>", translate_active = true))
    }
}
