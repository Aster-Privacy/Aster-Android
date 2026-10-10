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
    fun sender_names_and_domains_are_left_to_sender_trust() {
        val cases = listOf(
            "Example Billing" to "billing@examp1e-secure.example",
            "billing@example.com" to "someone@other.example",
            "Chase Support" to "alerts@chase-secure-login.example",
        )
        for ((name, email) in cases) {
            val result = analyze(name, email)
            assertTrue(email, result.signals.isEmpty())
            assertEquals(email, PhishingLevel.safe, result.level)
        }
    }

    @Test
    fun passing_authentication_is_safe() {
        val result = analyze("Someone", "someone@example.com", spf = "pass", dkim = "pass", dmarc = "pass")
        assertTrue(result.signals.isEmpty())
        assertEquals(PhishingLevel.safe, result.level)
    }

    @Test
    fun a_failed_check_that_dmarc_passed_anyway_is_not_a_signal() {
        val result = analyze("Someone", "someone@example.com", spf = "fail", dkim = "pass", dmarc = "pass")
        assertFalse(has_signal(result, "sender_authentication_failed"))
    }

    @Test
    fun a_hard_failure_without_dmarc_is_a_signal() {
        val result = analyze("Someone", "someone@example.com", spf = " HardFail ", dmarc = null)
        assertTrue(has_signal(result, "sender_authentication_failed"))
    }

    @Test
    fun failing_authentication_adds_a_signal() {
        val result = analyze("Someone", "someone@example.com", spf = "fail", dmarc = "fail")
        assertTrue(has_signal(result, "sender_authentication_failed"))
        assertEquals(PhishingLevel.suspicious, result.level)
    }

    @Test
    fun internal_mail_is_never_analyzed() {
        val result = analyze_email("", "", "Someone", "someone@example.com", is_external = false, spf_result = "fail", dmarc_result = "fail")
        assertEquals(PhishingLevel.safe, result.level)
        assertTrue(result.signals.isEmpty())
    }
}
