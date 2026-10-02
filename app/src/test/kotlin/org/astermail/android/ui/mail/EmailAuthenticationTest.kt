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

package org.astermail.android.ui.mail

import org.astermail.android.security.EmailAuthCheck
import org.astermail.android.security.EmailAuthCheckResult
import org.astermail.android.security.EmailAuthStatus
import org.astermail.android.security.EmailAuthVerdict
import org.astermail.android.security.auth_display_domain
import org.astermail.android.security.summarize_email_authentication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailAuthenticationTest {

    private fun verdict(
        spf: String?,
        dkim: String?,
        dmarc: String?,
    ): EmailAuthVerdict? = summarize_email_authentication(spf, dkim, dmarc)?.verdict

    private fun message(
        spf: String? = "pass",
        dkim: String? = "pass",
        dmarc: String? = "pass",
        item_type: String = "received",
        display_sender_email: String? = null,
    ) = ThreadMessage(
        id = "1",
        sender_name = "Shop",
        sender_email = "news@shop.test",
        to_label = "me",
        timestamp = 0L,
        body = "",
        item_type = item_type,
        spf_result = spf,
        dkim_result = dkim,
        dmarc_result = dmarc,
        display_sender_email = display_sender_email,
    )

    @Test
    fun is_authenticated_when_dmarc_passes_on_top_of_spf_or_dkim() {
        assertEquals(EmailAuthVerdict.authenticated, verdict("pass", "pass", "pass"))
        assertEquals(EmailAuthVerdict.authenticated, verdict("pass", "none", "pass"))
        assertEquals(EmailAuthVerdict.authenticated, verdict("fail", "pass", "pass"))
    }

    @Test
    fun fails_when_a_check_failed_and_dmarc_did_not_pass() {
        assertEquals(EmailAuthVerdict.failed, verdict("pass", "pass", "fail"))
        assertEquals(EmailAuthVerdict.failed, verdict("fail", "none", "none"))
        assertEquals(EmailAuthVerdict.failed, verdict(null, "fail", null))
        assertEquals(EmailAuthVerdict.failed, verdict("fail", "none", "temperror"))
    }

    @Test
    fun is_inconclusive_for_unusual_results() {
        assertEquals(EmailAuthVerdict.partial, verdict("softfail", "none", "none"))
        assertEquals(EmailAuthVerdict.partial, verdict("pass", "pass", "temperror"))
        assertEquals(EmailAuthVerdict.partial, verdict("none", "none", "pass"))
    }

    @Test
    fun does_not_call_a_message_spoofed_when_dmarc_passed() {
        assertEquals(EmailAuthVerdict.partial, verdict("fail", "fail", "pass"))
        assertEquals(EmailAuthVerdict.partial, verdict("fail", "none", "pass"))
    }

    @Test
    fun is_not_fully_verified_when_checks_are_only_absent() {
        assertEquals(EmailAuthVerdict.unverified, verdict("pass", "pass", "none"))
        assertEquals(EmailAuthVerdict.unverified, verdict("pass", null, null))
        assertEquals(EmailAuthVerdict.unverified, verdict("none", "none", "none"))
    }

    @Test
    fun shows_nothing_when_the_server_recorded_no_result() {
        assertNull(summarize_email_authentication(null, null, null))
        assertNull(summarize_email_authentication(" ", "MISSING", null))
    }

    @Test
    fun normalises_the_values_the_server_sends() {
        val summary = summarize_email_authentication(" PASS ", "HardFail", "TempError")!!
        assertEquals(
            listOf(
                EmailAuthCheckResult(EmailAuthCheck.spf, EmailAuthStatus.pass, "pass"),
                EmailAuthCheckResult(EmailAuthCheck.dkim, EmailAuthStatus.fail, "hardfail"),
                EmailAuthCheckResult(EmailAuthCheck.dmarc, EmailAuthStatus.other, "TEMPERROR"),
            ),
            summary.checks,
        )
        assertEquals(EmailAuthVerdict.failed, summary.verdict)
    }

    @Test
    fun caps_an_unexpected_value_without_splitting_characters() {
        val emoji = "\uD83D\uDE00"
        val value = summarize_email_authentication(emoji.repeat(40), null, null)!!.checks[0].value
        assertEquals(emoji.repeat(32), value)
    }

    @Test
    fun judges_only_received_messages() {
        assertNull(summarize_email_authentication(message(item_type = "sent")))
        assertEquals(EmailAuthVerdict.authenticated, summarize_email_authentication(message())?.verdict)
    }

    @Test
    fun shows_a_badge_only_when_checks_failed_or_were_inconclusive() {
        assertEquals(
            listOf(false, false, true, true),
            listOf(
                EmailAuthVerdict.authenticated,
                EmailAuthVerdict.unverified,
                EmailAuthVerdict.partial,
                EmailAuthVerdict.failed,
            ).map(::shows_auth_badge),
        )
    }

    @Test
    fun sender_status_follows_the_same_rules() {
        assertEquals(SenderAuthStatus.verified, sender_auth_status(message()))
        assertEquals(SenderAuthStatus.verified, sender_auth_status(message(display_sender_email = "hi@partner.test")))
        assertEquals(SenderAuthStatus.failed, sender_auth_status(message(spf = "fail", dkim = "none", dmarc = "fail")))
        assertEquals(SenderAuthStatus.failed, sender_auth_status(message(spf = " FAIL ", dkim = null, dmarc = null)))
        assertEquals(SenderAuthStatus.unknown, sender_auth_status(message(spf = "fail", dkim = "fail", dmarc = "pass")))
        assertEquals(SenderAuthStatus.unknown, sender_auth_status(message(spf = "softfail", dkim = null, dmarc = null)))
        assertEquals(SenderAuthStatus.unknown, sender_auth_status(message(spf = "fail", item_type = "sent")))
    }

    @Test
    fun shows_the_domain_the_checks_saw() {
        assertEquals("shop.test", auth_display_domain("News@Shop.Test"))
        assertEquals("shop.test", auth_display_domain("a@b@shop.test"))
        assertEquals("xn--exmple-cua.com", auth_display_domain("billing@\u202Eex\u00E4mple.com"))
        assertEquals("paypal.com.evil.com", auth_display_domain("billing@\u061Cpaypal.com\u061C.evil.com"))
    }

    @Test
    fun hides_domains_that_could_not_be_a_host_name() {
        listOf(
            "",
            "no-at-sign",
            "billing@",
            "billing@paypal.com/\u00FC.evil.com",
            "billing@paypal.com?x.evil.com",
            "billing@paypal.com:443",
            "billing@pay pal.com",
            "billing@paypal.com\u200D",
            "billing@" + "a.".repeat(5_000) + "com",
        ).forEach { assertEquals(it, "", auth_display_domain(it)) }
    }

    @Test
    fun hides_domains_the_old_idn_rules_would_show_as_another_domain() {
        listOf(
            "billing@stra\u00DFe.de",
            "BILLING@STRA\u1E9EE.DE",
            "a@\u03B1\u03B2\u03C2.gr",
        ).forEach { assertEquals(it, "", auth_display_domain(it)) }
        assertEquals("xn---x-b9be9f.gr", auth_display_domain("a@\u0391\u0392\u03A3-x.gr"))
        assertEquals("xn--mxac5c.gr", auth_display_domain("a@\u03B1\u03B2\u03C3.gr"))
        assertEquals("xn--mxac5c.gr", auth_display_domain("a@\u0391\u0392\u03A3.GR"))
    }
}
