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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class signature_editor_network_test {
    @Test
    fun only_inline_requests_are_allowed() {
        assertTrue(signature_editor_request_allowed("data"))
        assertTrue(signature_editor_request_allowed("DATA"))
        assertTrue(signature_editor_request_allowed("about"))
        assertFalse(signature_editor_request_allowed("https"))
        assertFalse(signature_editor_request_allowed("http"))
        assertFalse(signature_editor_request_allowed("file"))
        assertFalse(signature_editor_request_allowed("content"))
        assertFalse(signature_editor_request_allowed(null))
    }

    @Test
    fun document_carries_a_restrictive_policy_and_nonce() {
        val doc = build_signature_document("<b>Bob</b>", "abc123")
        assertTrue(doc.contains("Content-Security-Policy"))
        assertTrue(doc.contains("default-src 'none'"))
        assertTrue(doc.contains("img-src data:;"))
        assertTrue(doc.contains("script-src 'nonce-abc123'"))
        assertTrue(doc.contains("<script nonce=\"abc123\">"))
        assertFalse(doc.contains("<script>"))
        assertTrue(doc.indexOf("Content-Security-Policy") < doc.indexOf("<b>Bob</b>"))
    }

    @Test
    fun sanitized_signature_drops_remote_css_and_fonts() {
        val out = sanitize_signature_html(
            "<style>@import url(https://example.com/a.css); " +
                "@font-face{font-family:x;src:url(https://example.com/f.woff)}</style><p>Bob</p>",
        )
        assertFalse(out.contains("a.css"))
        assertFalse(out.contains("f.woff"))
        assertTrue(out.contains("Bob"))
    }
}
