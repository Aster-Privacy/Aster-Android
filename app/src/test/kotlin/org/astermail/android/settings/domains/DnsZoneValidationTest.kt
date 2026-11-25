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

import org.astermail.android.api.domains.DnsZoneRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsZoneValidationTest {

    private val zone = "example.com"

    private fun draft(
        type: DnsRecordType,
        host: String = "www",
        value: String = "",
        priority: String = "",
        weight: String = "",
        port: String = "",
        ttl: Int = DnsTtlPreset.one_hour.seconds,
    ) = DnsRecordDraft(type = type, host = host, value = value, priority = priority, weight = weight, port = port, ttl = ttl)

    @Test
    fun normalize_host_reduces_apex_and_fqdn_forms() {
        assertEquals("@", normalize_dns_host("", zone))
        assertEquals("@", normalize_dns_host("@", zone))
        assertEquals("@", normalize_dns_host("Example.com.", zone))
        assertEquals("www", normalize_dns_host("www.example.com", zone))
        assertEquals("www", normalize_dns_host(" WWW ", zone))
    }

    @Test
    fun host_rules() {
        assertNull(validate_dns_host("@", zone))
        assertNull(validate_dns_host("blog", zone))
        assertEquals(DnsFieldError.host_wildcard, validate_dns_host("*", zone))
        assertEquals(DnsFieldError.host_invalid, validate_dns_host("-bad", zone))
        assertEquals(DnsFieldError.host_invalid, validate_dns_host("café", zone))
        assertEquals(DnsFieldError.host_protected, validate_dns_host("_dmarc", zone))
        assertEquals(DnsFieldError.host_protected, validate_dns_host("aster1._domainkey", zone))
        assertEquals(DnsFieldError.host_too_long, validate_dns_host("a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(50), zone))
    }

    @Test
    fun aster_dkim_selector_detection() {
        assertTrue(is_aster_dkim_host("aster._domainkey"))
        assertTrue(is_aster_dkim_host("aster2._domainkey"))
        assertFalse(is_aster_dkim_host("google._domainkey"))
        assertFalse(is_aster_dkim_host("asterx._domainkey"))
        assertFalse(is_aster_dkim_host("aster"))
    }

    @Test
    fun ipv4_parsing_and_reserved_ranges() {
        assertNotNull(parse_ipv4("8.8.8.8"))
        assertNull(parse_ipv4("256.1.1.1"))
        assertNull(parse_ipv4("01.1.1.1"))
        assertNull(parse_ipv4("1.1.1"))
        assertTrue(is_reserved_ipv4(parse_ipv4("10.0.0.1")!!))
        assertTrue(is_reserved_ipv4(parse_ipv4("192.168.1.1")!!))
        assertTrue(is_reserved_ipv4(parse_ipv4("172.20.0.1")!!))
        assertTrue(is_reserved_ipv4(parse_ipv4("127.0.0.1")!!))
        assertTrue(is_reserved_ipv4(parse_ipv4("100.64.0.1")!!))
        assertFalse(is_reserved_ipv4(parse_ipv4("172.32.0.1")!!))
        assertFalse(is_reserved_ipv4(parse_ipv4("93.184.216.34")!!))
    }

    @Test
    fun ipv6_parsing_and_reserved_ranges() {
        assertNotNull(parse_ipv6("2606:4700::1111"))
        assertNotNull(parse_ipv6("2606:4700:0:0:0:0:0:1111"))
        assertNull(parse_ipv6("2606::4700::1"))
        assertNull(parse_ipv6("fe80::1%eth0"))
        assertNull(parse_ipv6("12345::1"))
        assertTrue(is_reserved_ipv6(parse_ipv6("::1")!!))
        assertTrue(is_reserved_ipv6(parse_ipv6("fd00::1")!!))
        assertTrue(is_reserved_ipv6(parse_ipv6("fe80::1")!!))
        assertTrue(is_reserved_ipv6(parse_ipv6("2001:db8::1")!!))
        assertFalse(is_reserved_ipv6(parse_ipv6("2606:4700::1111")!!))
    }

    @Test
    fun a_record_valid_and_invalid() {
        val ok = validate_dns_draft(draft(DnsRecordType.a, value = " 93.184.216.34 "), zone)
        assertTrue(ok.errors.is_valid)
        assertEquals("93.184.216.34", ok.request!!.value)
        assertEquals("www", ok.request!!.host)
        assertNull(ok.request!!.priority)
        assertEquals(DnsFieldError.value_required, validate_dns_draft(draft(DnsRecordType.a, value = ""), zone).errors.value)
        assertEquals(DnsFieldError.ipv4_invalid, validate_dns_draft(draft(DnsRecordType.a, value = "1.2.3"), zone).errors.value)
        assertEquals(DnsFieldError.ip_private, validate_dns_draft(draft(DnsRecordType.a, value = "10.1.1.1"), zone).errors.value)
    }

    @Test
    fun cname_rules() {
        assertEquals(DnsFieldError.host_apex_cname, validate_dns_draft(draft(DnsRecordType.cname, host = "@", value = "target.net"), zone).errors.host)
        assertEquals(DnsFieldError.target_is_self, validate_dns_draft(draft(DnsRecordType.cname, host = "www", value = "www.example.com."), zone).errors.value)
        assertEquals(DnsFieldError.target_invalid, validate_dns_draft(draft(DnsRecordType.cname, value = "localhost"), zone).errors.value)
        val ok = validate_dns_draft(draft(DnsRecordType.cname, value = "Target.NET."), zone)
        assertEquals("target.net", ok.request!!.value)
    }

    @Test
    fun txt_rules() {
        assertEquals(DnsFieldError.spf_at_apex, validate_dns_draft(draft(DnsRecordType.txt, host = "@", value = "v=spf1 -all"), zone).errors.value)
        assertEquals(DnsFieldError.txt_too_long, validate_dns_draft(draft(DnsRecordType.txt, value = "a".repeat(DNS_TXT_MAX + 1)), zone).errors.value)
        assertEquals(DnsFieldError.txt_invalid_chars, validate_dns_draft(draft(DnsRecordType.txt, value = "line\nbreak"), zone).errors.value)
        assertTrue(validate_dns_draft(draft(DnsRecordType.txt, host = "@", value = "site-verification=abc"), zone).errors.is_valid)
    }

    @Test
    fun mx_rules() {
        assertEquals(DnsFieldError.host_apex_mx, validate_dns_draft(draft(DnsRecordType.mx, host = "@", value = "mx.other.net", priority = "10"), zone).errors.host)
        assertEquals(DnsFieldError.priority_required, validate_dns_draft(draft(DnsRecordType.mx, host = "mail", value = "mx.other.net"), zone).errors.priority)
        assertEquals(DnsFieldError.priority_out_of_range, validate_dns_draft(draft(DnsRecordType.mx, host = "mail", value = "mx.other.net", priority = "70000"), zone).errors.priority)
        val ok = validate_dns_draft(draft(DnsRecordType.mx, host = "mail", value = "mx.other.net", priority = "10"), zone)
        assertEquals(10, ok.request!!.priority)
    }

    @Test
    fun srv_rules() {
        assertEquals(DnsFieldError.srv_host_invalid, validate_dns_draft(draft(DnsRecordType.srv, host = "sip", value = "sip.other.net", priority = "1", weight = "1", port = "5060"), zone).errors.host)
        assertEquals(DnsFieldError.port_out_of_range, validate_dns_draft(draft(DnsRecordType.srv, host = "_sip._tcp", value = "sip.other.net", priority = "1", weight = "1", port = "0"), zone).errors.port)
        assertEquals(DnsFieldError.weight_required, validate_dns_draft(draft(DnsRecordType.srv, host = "_sip._tcp", value = "sip.other.net", priority = "1", port = "5060"), zone).errors.weight)
        val ok = validate_dns_draft(draft(DnsRecordType.srv, host = "_sip._tcp", value = "sip.other.net", priority = "1", weight = "5", port = "5060"), zone)
        assertEquals("5 5060 sip.other.net", ok.request!!.value)
        assertEquals(1, ok.request!!.priority)
    }

    @Test
    fun ttl_must_be_in_range() {
        assertEquals(DnsFieldError.ttl_out_of_range, validate_dns_draft(draft(DnsRecordType.a, value = "93.184.216.34", ttl = 60), zone).errors.ttl)
        assertEquals(DnsFieldError.ttl_out_of_range, validate_dns_draft(draft(DnsRecordType.a, value = "93.184.216.34", ttl = DNS_TTL_MAX + 1), zone).errors.ttl)
        assertEquals(DNS_TTL_MIN, clamp_dns_ttl(1))
    }

    @Test
    fun conflicts_with_existing_records() {
        val existing = listOf(
            DnsZoneRecord(id = "1", type = "CNAME", host = "www", value = "target.net"),
            DnsZoneRecord(id = "2", type = "A", host = "api", value = "93.184.216.34"),
            DnsZoneRecord(id = "3", type = "TXT", host = "spf", value = "v=spf1 -all"),
        )
        assertEquals(DnsFieldError.cname_conflict, validate_dns_draft(draft(DnsRecordType.a, host = "www", value = "93.184.216.34"), zone, existing).errors.host)
        assertEquals(DnsFieldError.duplicate_record, validate_dns_draft(draft(DnsRecordType.a, host = "api", value = "93.184.216.34"), zone, existing).errors.value)
        assertEquals(DnsFieldError.spf_conflict, validate_dns_draft(draft(DnsRecordType.txt, host = "spf", value = "v=spf1 include:x.net -all"), zone, existing).errors.value)
        assertTrue(validate_dns_draft(draft(DnsRecordType.a, host = "api", value = "93.184.216.34"), zone, existing, replacing_id = "2").errors.is_valid)
    }

    @Test
    fun caa_rules_and_wire_format() {
        assertNull(validate_caa_value(CaaTag.issue, "letsencrypt.org"))
        assertNull(validate_caa_value(CaaTag.issue, ";"))
        assertNull(validate_caa_value(CaaTag.iodef, "mailto:security@example.com"))
        assertEquals(DnsFieldError.caa_value_invalid, validate_caa_value(CaaTag.iodef, "security@example.com"))
        assertEquals(DnsFieldError.caa_value_invalid, validate_caa_value(CaaTag.issue, "bad\"quote.org"))
        val ok = validate_dns_draft(
            DnsRecordDraft(type = DnsRecordType.caa, host = "@", value = "letsencrypt.org", caa_tag = CaaTag.issuewild, caa_critical = true),
            zone,
        )
        assertEquals("128 issuewild \"letsencrypt.org\"", ok.request!!.value)
    }

    @Test
    fun draft_from_record_round_trips_srv_and_caa() {
        val srv = draft_from_record(DnsZoneRecord(id = "1", type = "SRV", host = "_sip._tcp", value = "5 5060 sip.other.net.", priority = 1))!!
        assertEquals("1", srv.priority)
        assertEquals("5", srv.weight)
        assertEquals("5060", srv.port)
        assertEquals("sip.other.net", srv.value)
        val caa = draft_from_record(DnsZoneRecord(id = "2", type = "CAA", host = "@", value = "128 iodef \"mailto:a@example.com\""))!!
        assertTrue(caa.caa_critical)
        assertEquals(CaaTag.iodef, caa.caa_tag)
        assertEquals("mailto:a@example.com", caa.value)
        assertNull(draft_from_record(DnsZoneRecord(id = "3", type = "NS", host = "@", value = "ns1.other.net")))
    }

    @Test
    fun invalid_draft_has_no_request() {
        val result = validate_dns_draft(draft(DnsRecordType.a, host = "*", value = "93.184.216.34"), zone)
        assertNull(result.request)
        assertFalse(result.errors.is_valid)
    }
}
