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

package org.astermail.android.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectedMimeBuilderTest {

    @Test
    fun `plain text part drops head style and script content`() {
        val html = "<html><head><title>Invoice</title><style>p { color: red; }</style></head>" +
            "<body><script>var x = 1 < 2;</script><p>Hello</p></body></html>"

        assertEquals("Hello", ProtectedMimeBuilder.html_to_plain_text(html))
    }

    @Test
    fun `plain text part decodes each entity once`() {
        val html = "<p>Use &amp;lt;b&amp;gt; for bold &amp; more</p><p>Caf&#233; &#x2713;</p>"

        assertEquals(
            "Use &lt;b&gt; for bold & more\r\nCafé ✓",
            ProtectedMimeBuilder.html_to_plain_text(html),
        )
    }
}
