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
package org.astermail.android.settings.domains

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsProviderFormatsTest {

    private val zone = "example.com"

    @Test
    fun at_style_keeps_relative_host_and_at_for_apex() {
        assertEquals("@", format_host_for_provider("@", zone, DnsProvider.cloudflare))
        assertEquals("_dmarc", format_host_for_provider("_dmarc.example.com", zone, DnsProvider.cloudflare))
    }

    @Test
    fun blank_style_leaves_apex_empty() {
        assertEquals("", format_host_for_provider("@", zone, DnsProvider.porkbun))
        assertEquals("", format_host_for_provider("example.com.", zone, DnsProvider.porkbun))
        assertEquals("aster._domainkey", format_host_for_provider("aster._domainkey", zone, DnsProvider.porkbun))
    }

    @Test
    fun fqdn_style_appends_the_zone() {
        assertEquals("example.com", format_host_for_provider("@", zone, DnsProvider.route53))
        assertEquals("_dmarc.example.com", format_host_for_provider("_dmarc", zone, DnsProvider.route53))
        assertEquals("_dmarc.example.com", format_host_for_provider("_dmarc.example.com", "Example.com.", DnsProvider.route53))
    }

    @Test
    fun other_provider_shows_the_host_as_given() {
        assertEquals("@", format_host_for_provider("", zone, DnsProvider.other))
        assertEquals("_dmarc.example.com", format_host_for_provider(" _dmarc.example.com ", zone, DnsProvider.other))
    }

    @Test
    fun dashboard_url_substitutes_a_safe_domain_only() {
        assertEquals("https://porkbun.com/account/dns/example.com", dns_provider_dashboard_url(DnsProvider.porkbun, "Example.com."))
        assertNull(dns_provider_dashboard_url(DnsProvider.porkbun, "evil.com/../x"))
        assertNull(dns_provider_dashboard_url(DnsProvider.porkbun, "a b.com"))
        assertNull(dns_provider_dashboard_url(DnsProvider.other, zone))
    }

    @Test
    fun from_id_falls_back_to_other() {
        assertEquals(DnsProvider.hetzner, DnsProvider.from_id("hetzner"))
        assertEquals(DnsProvider.other, DnsProvider.from_id(null))
        assertEquals(DnsProvider.other, DnsProvider.from_id("unknown"))
    }
}
