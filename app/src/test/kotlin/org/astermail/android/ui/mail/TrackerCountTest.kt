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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrackerCountTest {

    private val mixed =
        """<p>Hi<img src="https://track.example.com/o/1.gif" width="1" height="1"></p>""" +
            """<p>Later<img src="https://track.example.com/o/2.gif" width="1" height="1"></p>""" +
            """<img src="https://cdn.example.org/spacer.gif" width="2" height="2" alt="">""" +
            """<img src="https://hidden.example/open?id=1" width="1" height="1" style="display:none">""" +
            """<p>Bye<img src="//px.example/p.gif" width="1" height="1"></p>"""

    @Test
    fun the_banner_and_the_tracker_list_count_the_same_pixels() {
        val report = EmailHtmlSanitizer.analyze_trackers(mixed)
        val banner = count_external_content(mixed, report)

        assertEquals(4, report.pixel_count)
        assertEquals(
            listOf("track.example.com" to 2, "hidden.example" to 1, "px.example" to 1),
            report.pixel_domains,
        )
        assertEquals(report.pixel_count, banner.tracker_count)
        assertEquals(report.pixel_count, report.pixel_domains.sumOf { it.second })
        assertEquals(1, banner.image_count)
        assertEquals(
            listOf("https://cdn.example.org/spacer.gif"),
            banner.items.filter { it.type == ExternalContentType.image }.map { it.url },
        )
        assertEquals(4, banner.items.count { it.type == ExternalContentType.tracker })
    }

    @Test
    fun hidden_tracking_pixels_are_counted() {
        val report = EmailHtmlSanitizer.analyze_trackers(
            """<img src="https://open.mailmetrics.example/o/1.gif" width="1" height="1">""" +
                """<img src="https://hidden.example/open?id=1" width="1" height="1" style="display:none">""" +
                """<img src="https://zero.example/open?id=2" width="0" height="0">""",
        )

        assertEquals(3, report.pixel_count)
        assertEquals(
            listOf("open.mailmetrics.example", "hidden.example", "zero.example"),
            report.pixel_domains.map { it.first },
        )
    }

    @Test
    fun the_reader_removes_every_pixel_the_tracker_list_counts() {
        val sanitized = EmailHtmlSanitizer.sanitize(mixed)

        assertFalse(sanitized.contains("track.example.com"))
        assertFalse(sanitized.contains("hidden.example"))
        assertFalse(sanitized.contains("px.example"))
        assertEquals(
            0,
            EmailHtmlSanitizer.analyze_trackers(sanitized).pixel_count,
        )
    }
}
