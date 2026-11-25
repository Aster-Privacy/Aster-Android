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

import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DomainCheck
import org.astermail.android.api.settings.DomainHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainStatusTest {

    private fun domain(status: String = "active", grace: String? = null) =
        CustomDomain(id = "d1", domain_name = "example.com", status = status, downgrade_grace_expires_at = grace)

    @Test
    fun active_from_status_or_all_checks_verified() {
        assertTrue(is_domain_active(domain("ACTIVE")))
        assertFalse(is_domain_active(domain("pending")))
        assertTrue(
            is_domain_active(
                CustomDomain(id = "d", domain_name = "x.com", txt_verified = true, mx_verified = true, spf_verified = true, dkim_verified = true),
            ),
        )
        assertFalse(is_domain_active(CustomDomain(id = "d", domain_name = "x.com", txt_verified = true)))
    }

    @Test
    fun health_level_from_severity_then_status() {
        assertEquals(DomainHealthLevel.unknown, domain_health_level(null))
        assertEquals(DomainHealthLevel.critical, domain_health_level(DomainHealth(severity = "Error")))
        assertEquals(DomainHealthLevel.warning, domain_health_level(DomainHealth(severity = "warn")))
        assertEquals(DomainHealthLevel.ok, domain_health_level(DomainHealth(severity = "none")))
        assertEquals(DomainHealthLevel.warning, domain_health_level(DomainHealth(health_status = "degraded")))
        assertEquals(DomainHealthLevel.critical, domain_health_level(DomainHealth(health_status = "broken")))
        assertEquals(DomainHealthLevel.unknown, domain_health_level(DomainHealth()))
    }

    @Test
    fun tone_prefers_blocked_and_grace_over_health() {
        assertEquals(DomainTone.attention, domain_tone(domain("suspended"), DomainHealth(severity = "ok")))
        assertEquals(DomainTone.attention, domain_tone(domain(grace = "2026-12-01T00:00:00Z"), DomainHealth(severity = "ok")))
        assertEquals(DomainTone.pending, domain_tone(domain("pending"), null))
        assertEquals(DomainTone.attention, domain_tone(domain(), DomainHealth(severity = "warning")))
        assertEquals(DomainTone.verified, domain_tone(domain(), DomainHealth(severity = "ok")))
        assertEquals(DomainTone.verified, domain_tone(domain(), null))
    }

    @Test
    fun failing_checks_sort_first_then_by_record_order() {
        val checks = listOf(
            DomainCheck(key = "dmarc", outcome = "pass"),
            DomainCheck(key = "spf", outcome = "fail"),
            DomainCheck(key = "mx", outcome = "pass"),
            DomainCheck(key = "dkim", outcome = "warn"),
            DomainCheck(key = "other", outcome = "pass"),
        )
        assertEquals(listOf("spf", "dkim", "mx", "dmarc", "other"), ordered_domain_checks(checks).map { it.key })
    }

    @Test
    fun polling_stops_only_when_healthy() {
        assertTrue(should_keep_polling_health(null))
        assertTrue(should_keep_polling_health(DomainHealth(severity = "critical")))
        assertFalse(should_keep_polling_health(DomainHealth(severity = "ok")))
        assertEquals(15_000L, DOMAIN_HEALTH_POLL_MS)
    }

    @Test
    fun reason_and_label_mapping() {
        assertNotNull(domain_health_reason_res("MX_MISSING"))
        assertNotNull(domain_health_reason_res("spf_duplicate_records"))
        assertNull(domain_health_reason_res("something_new"))
        assertNull(domain_health_reason_res(null))
        assertNotNull(domain_check_label_res("DKIM"))
        assertNull(domain_check_label_res("bimi"))
        assertNotNull(domain_record_caveat_res("mx_replaces_existing"))
    }
}
