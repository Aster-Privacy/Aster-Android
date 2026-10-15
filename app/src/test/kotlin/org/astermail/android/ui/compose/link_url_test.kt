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

package org.astermail.android.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class link_url_test {
    @Test
    fun bare_domain_gets_https() {
        assertEquals("https://example.com", normalize_link_url("example.com"))
        assertEquals("https://example.com/a?b=1", normalize_link_url("  example.com/a?b=1 "))
    }

    @Test
    fun full_url_is_kept() {
        assertEquals("https://example.com/path", normalize_link_url("https://example.com/path"))
        assertEquals("http://example.com", normalize_link_url("http://example.com"))
        assertEquals("HTTPS://Example.com", normalize_link_url("HTTPS://Example.com"))
    }

    @Test
    fun doubled_scheme_is_collapsed() {
        assertEquals("https://example.com", normalize_link_url("https://https://example.com"))
        assertEquals("http://example.com", normalize_link_url("https://http://example.com"))
        assertEquals("https://example.com", normalize_link_url("http://https://example.com"))
        assertEquals("https://example.com", normalize_link_url("https://https://https://example.com"))
        assertEquals("https://example.com", normalize_link_url("HTTPS://https://example.com"))
    }

    @Test
    fun collapse_leaves_single_scheme_alone() {
        assertEquals("https://example.com", collapse_doubled_link_scheme("https://example.com"))
        assertEquals("example.com", collapse_doubled_link_scheme("example.com"))
    }

    @Test
    fun email_becomes_mailto() {
        assertEquals("mailto:someone@example.com", normalize_link_url("someone@example.com"))
        assertEquals("mailto:someone@example.com", normalize_link_url("mailto:someone@example.com"))
    }

    @Test
    fun host_with_port_gets_https() {
        assertEquals("https://example.com:8080/x", normalize_link_url("example.com:8080/x"))
    }

    @Test
    fun unsafe_or_empty_input_is_rejected() {
        assertNull(normalize_link_url(""))
        assertNull(normalize_link_url("   "))
        assertNull(normalize_link_url("https://"))
        assertNull(normalize_link_url("https://https://"))
        assertNull(normalize_link_url("mailto:"))
        assertNull(normalize_link_url("javascript:alert(1)"))
        assertNull(normalize_link_url("data:text/html,x"))
        assertNull(normalize_link_url("exa mple.com"))
        assertNull(normalize_link_url("a".repeat(max_link_url_length + 1)))
    }
}
