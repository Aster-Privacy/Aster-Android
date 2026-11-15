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

package org.astermail.android.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasWebsiteValidationTest {

    @Test
    fun accepts_long_generic_top_level_domains() {
        assertEquals("https://federated.nexus", validate_alias_website_input("federated.nexus"))
        assertEquals("https://federated.nexus", validate_alias_website_input("https://federated.nexus/"))
        assertEquals("https://sub.example.travel", validate_alias_website_input("sub.example.travel"))
        assertEquals("https://studio.photography", validate_alias_website_input("studio.photography"))
        assertEquals("https://netflix.com", validate_alias_website_input("netflix.com"))
        assertEquals("https://shop.co.uk", validate_alias_website_input("shop.co.uk"))
        assertEquals("https://amazon.com/account", validate_alias_website_input("https://amazon.com/account"))
    }

    @Test
    fun accepts_punycode_and_converts_unicode_hosts() {
        assertEquals("https://xn--80aswg.xn--p1ai", validate_alias_website_input("xn--80aswg.xn--p1ai"))
        assertEquals("https://xn--80aswg.xn--p1ai", validate_alias_website_input("сайт.рф"))
    }

    @Test
    fun normalizes_case_trailing_dot_and_default_port() {
        assertEquals(
            "https://federated.nexus/Path?q=1",
            validate_alias_website_input("HTTPS://Federated.NEXUS./Path?q=1"),
        )
        assertEquals("https://federated.nexus", validate_alias_website_input("https://federated.nexus:443/"))
        assertEquals("https://federated.nexus:8443", validate_alias_website_input("https://federated.nexus:8443"))
    }

    @Test
    fun accepts_every_bundled_top_level_domain() {
        val rejected = IANA_TLDS.filter { validate_alias_website_input("example.$it") == null }
        assertTrue(IANA_TLDS.size > 1000)
        assertEquals(emptyList<String>(), rejected)
    }

    @Test
    fun rejects_hosts_that_are_not_public_domains() {
        listOf(
            "foo",
            "foo.",
            "-a.com",
            "a-.com",
            "a..com",
            "localhost",
            "http://localhost:8080",
            "app.localhost",
            "printer.local",
            "db.internal",
            "127.0.0.1",
            "http://10.0.0.1/admin",
            "http://[::1]/",
            "example.zz",
            "test.cpm",
            "foo.123",
            "https://federated.nexus:99999",
        ).forEach { assertNull(it, validate_alias_website_input(it)) }
    }

    @Test
    fun rejects_credentials_and_non_web_schemes() {
        listOf(
            "http://user:pass@x.com",
            "https://user@x.com",
            "https://@x.com",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "java\tscript:alert(1)",
            "data:text/html,<script>1</script>",
            "ftp://files.example.com",
            "file:///etc/passwd",
            "https://x.com\\@evil.com",
        ).forEach { assertNull(it, validate_alias_website_input(it)) }
    }

    @Test
    fun rejects_oversized_hosts_and_labels() {
        assertNull(validate_alias_website_input("a".repeat(296) + ".com"))
        assertFalse(is_plausible_alias_website_host("a".repeat(64) + ".com"))
        assertTrue(is_plausible_alias_website_host("a".repeat(63) + ".com"))
        assertFalse(is_plausible_alias_website_host(List(4) { "a".repeat(63) }.joinToString(".") + ".com"))
    }

    @Test
    fun stored_entries_with_credentials_are_dropped() {
        assertNull(normalize_alias_website_url("http://user:pass@x.com"))
        assertEquals("https://ok.com", normalize_alias_website_url("ok.com"))
        assertEquals("https://federated.nexus", normalize_alias_website_url("https://federated.nexus"))
    }
}
