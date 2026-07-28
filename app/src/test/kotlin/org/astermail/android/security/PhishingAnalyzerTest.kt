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

package org.astermail.android.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhishingAnalyzerTest {

    private fun analyze(
        name: String,
        email: String,
        spf: String? = null,
        dkim: String? = null,
        dmarc: String? = null,
    ) = analyze_email(
        html_content = "",
        text_content = "",
        sender_name = name,
        sender_email = email,
        is_external = true,
        spf_result = spf,
        dkim_result = dkim,
        dmarc_result = dmarc,
    )

    private fun has_signal(result: PhishingResult, name: String) = result.signals.any { it.name == name }

    @Test
    fun regional_brand_domain_is_not_a_spoof() {
        val result = analyze("Amazon.ca", "shipment-tracking@amazon.ca")
        assertFalse(has_signal(result, "display_name_brand_spoof"))
        assertEquals(PhishingLevel.safe, result.level)
    }

    @Test
    fun brand_bulk_mail_domain_is_not_a_spoof() {
        val result = analyze("Facebook", "notification@facebookmail.com")
        assertFalse(has_signal(result, "display_name_brand_spoof"))
    }

    @Test
    fun multi_label_suffix_domain_resolves_to_brand_label() {
        val result = analyze("Microsoft Account Team", "account-security-noreply@microsoft.co.uk")
        assertFalse(has_signal(result, "display_name_brand_spoof"))
    }

    @Test
    fun personal_name_matching_an_ambiguous_brand_is_not_a_spoof() {
        val result = analyze("Chase Miller", "chase.miller@example.com")
        assertFalse(has_signal(result, "display_name_brand_spoof"))
        assertEquals(PhishingLevel.safe, result.level)
    }

    @Test
    fun ambiguous_brand_with_service_context_is_still_flagged() {
        val result = analyze("Chase Support", "alerts@chase-secure-login.com")
        assertTrue(has_signal(result, "display_name_brand_spoof"))
    }

    @Test
    fun brand_display_name_on_unrelated_domain_is_flagged() {
        val result = analyze("PayPal Service", "billing@pypal-secure.com")
        assertTrue(has_signal(result, "display_name_brand_spoof"))
    }

    @Test
    fun passing_authentication_downgrades_a_lone_display_name_signal() {
        val result = analyze(
            "PayPal Service",
            "billing@merchant-notices.example",
            spf = "pass",
            dkim = "pass",
            dmarc = "pass",
        )
        assertTrue(has_signal(result, "display_name_brand_spoof"))
        assertEquals(PhishingLevel.safe, result.level)
    }

    @Test
    fun failing_authentication_adds_a_signal() {
        val result = analyze("Someone", "someone@example.com", spf = "fail", dmarc = "fail")
        assertTrue(has_signal(result, "sender_authentication_failed"))
        assertEquals(PhishingLevel.suspicious, result.level)
    }

    @Test
    fun internal_mail_is_never_analyzed() {
        val result = analyze_email("", "", "PayPal", "x@evil.com", is_external = false)
        assertEquals(PhishingLevel.safe, result.level)
        assertTrue(result.signals.isEmpty())
    }
}
