// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkCopyTest {

    private class Recorder(private val succeeds: Boolean) {
        val written = mutableListOf<String>()
        var copied = false
        var failed = false

        fun copy(link: String) = copy_external_link(
            link = link,
            write_clip = { _, text ->
                written += text
                succeeds
            },
            on_copied = { copied = true },
            on_failed = { failed = true },
        )
    }

    @Test
    fun `the exact url is written to the clipboard`() {
        val link = "https://example.com/path/to/page?utm_source=mail&id=42&q=a%20b#section-2"
        val recorder = Recorder(succeeds = true)
        recorder.copy(link)
        assertEquals(listOf(link), recorder.written)
        assertTrue(recorder.copied)
        assertFalse(recorder.failed)
    }

    @Test
    fun `a long url is copied in full without trimming`() {
        val link = "https://tracking.example.net/click?" + "token=" + "x".repeat(600) + "&next=%2Fhome "
        val recorder = Recorder(succeeds = true)
        recorder.copy(link)
        assertEquals(link, recorder.written.single())
    }

    @Test
    fun `a clipboard failure reports failure instead of success`() {
        val recorder = Recorder(succeeds = false)
        recorder.copy("https://example.com")
        assertFalse(recorder.copied)
        assertTrue(recorder.failed)
    }
}
