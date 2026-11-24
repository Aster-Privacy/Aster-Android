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

import java.io.IOException
import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsZoneErrorsTest {

    @Test
    fun codes_map_case_insensitively_with_optional_prefix() {
        assertEquals(DnsZoneReason.zone_not_managed, dns_zone_reason_for_code("ZONE_NOT_MANAGED"))
        assertEquals(DnsZoneReason.locked, dns_zone_reason_for_code("dns_locked"))
        assertEquals(DnsZoneReason.duplicate, dns_zone_reason_for_code(" duplicate_record "))
        assertEquals(DnsZoneReason.step_up, dns_zone_reason_for_code("step_up_required"))
        assertNull(dns_zone_reason_for_code(""))
        assertNull(dns_zone_reason_for_code(null))
        assertNull(dns_zone_reason_for_code("something_new"))
    }

    @Test
    fun error_code_wins_over_error_class() {
        assertEquals(DnsZoneReason.private_ip, dns_zone_reason(ApiError.ValidationError(listOf("x"), code = "PRIVATE_IP")))
        assertEquals(DnsZoneReason.protected_record, dns_zone_reason(ApiError.ForbiddenError(code = "forbidden", details = mapOf("reason" to "protected_record"))))
        assertEquals(DnsZoneReason.spf_conflict, dns_zone_reason(ApiError.Conflict("x", code = "SPF_CONFLICT")))
        assertEquals(DnsZoneReason.registrar_busy, dns_zone_reason(ApiError.RateLimited(code = "registrar_busy")))
        assertEquals(DnsZoneReason.step_up, dns_zone_reason(ApiError.PreconditionRequired(code = "STEP_UP_REQUIRED")))
    }

    @Test
    fun error_class_fallbacks() {
        assertEquals(DnsZoneReason.step_up, dns_zone_reason(ApiError.StepUpRequired()))
        assertEquals(DnsZoneReason.rate_limited, dns_zone_reason(ApiError.RateLimited()))
        assertEquals(DnsZoneReason.not_found, dns_zone_reason(ApiError.NotFoundError))
        assertEquals(DnsZoneReason.offline, dns_zone_reason(ApiError.NetworkError))
        assertEquals(DnsZoneReason.offline, dns_zone_reason(IOException("x")))
        assertEquals(DnsZoneReason.registrar_busy, dns_zone_reason(ApiError.ServerError(503)))
        assertEquals(DnsZoneReason.server, dns_zone_reason(ApiError.ServerError(500)))
        assertEquals(DnsZoneReason.locked, dns_zone_reason(ApiError.ForbiddenError()))
        assertEquals(DnsZoneReason.duplicate, dns_zone_reason(ApiError.Conflict("x")))
        assertEquals(DnsZoneReason.invalid_value, dns_zone_reason(ApiError.ValidationError(listOf("x"))))
        assertEquals(DnsZoneReason.generic, dns_zone_reason(IllegalStateException("x")))
    }

    @Test
    fun unavailable_covers_missing_route_and_unmanaged_zone() {
        assertTrue(is_dns_zone_unavailable(ApiError.NotFoundError))
        assertTrue(is_dns_zone_unavailable(ApiError.ServerError(501)))
        assertTrue(is_dns_zone_unavailable(ApiError.ForbiddenError(code = "ZONE_NOT_MANAGED")))
        assertFalse(is_dns_zone_unavailable(ApiError.ServerError(500)))
        assertFalse(is_dns_zone_unavailable(ApiError.ForbiddenError(code = "DNS_LOCKED")))
    }

    @Test
    fun every_reason_and_field_error_has_a_string() {
        DnsZoneReason.entries.forEach { assertTrue(dns_zone_reason_res(it) != 0) }
        DnsFieldError.entries.forEach { assertTrue(dns_field_error_res(it) != 0) }
        DnsProtectedReason.entries.forEach { assertTrue(dns_protected_reason_res(it) != 0) }
    }

    @Test
    fun reasons_point_at_the_right_field() {
        assertEquals(DnsDraftField.host, dns_zone_reason_field(DnsZoneReason.wildcard))
        assertEquals(DnsDraftField.host, dns_zone_reason_field(DnsZoneReason.cname_conflict))
        assertEquals(DnsDraftField.value, dns_zone_reason_field(DnsZoneReason.private_ip))
        assertEquals(DnsDraftField.ttl, dns_zone_reason_field(DnsZoneReason.invalid_ttl))
        assertEquals(DnsDraftField.port, dns_zone_reason_field(DnsZoneReason.invalid_port))
        assertNull(dns_zone_reason_field(DnsZoneReason.rate_limited))
        assertNull(dns_zone_reason_field(null))
    }

    @Test
    fun protected_reason_parsing() {
        assertEquals(DnsProtectedReason.mail_routing, dns_protected_reason(" MAIL_ROUTING "))
        assertEquals(DnsProtectedReason.sender_auth, dns_protected_reason("sender_auth"))
        assertEquals(DnsProtectedReason.other, dns_protected_reason(null))
        assertEquals(DnsProtectedReason.other, dns_protected_reason("new_reason"))
    }
}
