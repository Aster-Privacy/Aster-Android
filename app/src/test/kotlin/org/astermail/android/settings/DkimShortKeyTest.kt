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

import org.astermail.android.R
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.api.settings.DomainCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DkimShortKeyTest {

    private val long_value = "v=DKIM1; k=rsa; p=" + "A".repeat(392)
    private val short_value = "v=DKIM1; k=rsa; p=" + "A".repeat(216)

    private fun dkim_record(
        value: String = long_value,
        verified: Boolean = false,
        caveat_key: String? = null,
        purpose: String = "dkim",
    ) = DnsRecord(
        type = "TXT",
        name = "aster._domainkey.example.com",
        value = value,
        verified = verified,
        purpose = purpose,
        required = true,
        caveat_key = caveat_key,
    )

    @Test
    fun `short key is offered when health says it can shorten`() {
        assertTrue(should_offer_short_dkim_key(dkim_record(), can_shorten = true, can_manage = true))
    }

    @Test
    fun `short key is offered for an unverified long value caveat`() {
        val record = dkim_record(caveat_key = DKIM_LONG_VALUE_CAVEAT)
        assertTrue(should_offer_short_dkim_key(record, can_shorten = false, can_manage = true))
    }

    @Test
    fun `short key is not offered for a verified long value caveat without health`() {
        val record = dkim_record(verified = true, caveat_key = DKIM_LONG_VALUE_CAVEAT)
        assertFalse(should_offer_short_dkim_key(record, can_shorten = false, can_manage = true))
    }

    @Test
    fun `short key is not offered without any signal`() {
        assertFalse(should_offer_short_dkim_key(dkim_record(), can_shorten = false, can_manage = true))
    }

    @Test
    fun `short key is not offered for a value of 255 characters or fewer`() {
        val at_limit = "x".repeat(DKIM_TXT_SEGMENT_LIMIT)
        assertFalse(should_offer_short_dkim_key(dkim_record(value = at_limit), can_shorten = true, can_manage = true))
        assertFalse(should_offer_short_dkim_key(dkim_record(value = short_value), can_shorten = true, can_manage = true))
        val over_limit = "x".repeat(DKIM_TXT_SEGMENT_LIMIT + 1)
        assertTrue(should_offer_short_dkim_key(dkim_record(value = over_limit), can_shorten = true, can_manage = true))
    }

    @Test
    fun `short key is not offered for a shared domain`() {
        val record = dkim_record(caveat_key = DKIM_LONG_VALUE_CAVEAT)
        assertFalse(should_offer_short_dkim_key(record, can_shorten = true, can_manage = false))
    }

    @Test
    fun `short key is not offered for a record that is not dkim`() {
        val record = dkim_record(purpose = "spf", caveat_key = DKIM_LONG_VALUE_CAVEAT)
        assertFalse(should_offer_short_dkim_key(record, can_shorten = true, can_manage = true))
    }

    @Test
    fun `recognized hint takes precedence over reason on a failing check`() {
        val cases = mapOf(
            "dkim_truncated" to R.string.domain_hint_dkim_truncated,
            "dkim_quoted" to R.string.domain_hint_dkim_quoted,
            "dkim_other_key" to R.string.domain_hint_dkim_other_key,
        )
        cases.forEach { (hint, expected) ->
            val check = DomainCheck(key = "dkim", outcome = "fail", reason = "dkim_missing_or_stale", hint = hint)
            assertEquals(expected, domain_check_message_res(check))
        }
    }

    @Test
    fun `unknown hint falls back to the reason text`() {
        val check = DomainCheck(key = "dkim", outcome = "fail", reason = "dkim_missing_or_stale", hint = "dkim_future_hint")
        assertEquals(R.string.domain_reason_dkim_missing_or_stale, domain_check_message_res(check))
    }

    @Test
    fun `missing hint falls back to the reason text`() {
        val check = DomainCheck(key = "dkim", outcome = "fail", reason = "dkim_missing_or_stale")
        assertEquals(R.string.domain_reason_dkim_missing_or_stale, domain_check_message_res(check))
    }

    @Test
    fun `unknown reason and hint fall back to the generic failure text`() {
        val check = DomainCheck(key = "dkim", outcome = "fail", reason = "something_new")
        assertEquals(R.string.domain_check_generic_failure, domain_check_message_res(check))
    }

    @Test
    fun `hints are ignored for pass and unknown outcomes`() {
        listOf("pass", "unknown").forEach { outcome ->
            val check = DomainCheck(key = "dkim", outcome = outcome, reason = "dkim_missing_or_stale", hint = "dkim_truncated")
            assertNull(domain_check_message_res(check))
        }
    }

    @Test
    fun `caveat text maps only the long value caveat`() {
        assertEquals(R.string.dns_caveat_dkim_long_value, dns_caveat_res(DKIM_LONG_VALUE_CAVEAT))
        assertNull(dns_caveat_res(null))
        assertNull(dns_caveat_res("other"))
    }

    @Test
    fun `find dkim check picks the dkim entry`() {
        val checks = listOf(
            DomainCheck(key = "mx", outcome = "pass"),
            DomainCheck(key = "dkim", outcome = "fail", hint = "dkim_quoted"),
        )
        assertEquals("dkim_quoted", find_dkim_check(checks)?.hint)
        assertNull(find_dkim_check(listOf(DomainCheck(key = "mx", outcome = "pass"))))
    }

    @Test
    fun `replace dkim record swaps the dkim entry in place`() {
        val mx = DnsRecord(type = "MX", name = "example.com", value = "mx.astermail.org", purpose = "mx")
        val old = dkim_record()
        val replacement = dkim_record(value = short_value).copy(name = "aster2._domainkey.example.com")
        val result = replace_dkim_record(listOf(mx, old), replacement)
        assertEquals(listOf(mx, replacement), result)
    }

    @Test
    fun `replace dkim record appends when no dkim entry exists`() {
        val mx = DnsRecord(type = "MX", name = "example.com", value = "mx.astermail.org", purpose = "mx")
        val replacement = dkim_record(value = short_value)
        assertEquals(listOf(mx, replacement), replace_dkim_record(listOf(mx), replacement))
    }
}
