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

import kotlinx.coroutines.runBlocking
import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsubscribeHeaderParsingTest {
    private val one_click_post = "List-Unsubscribe=One-Click"

    @Test
    fun parses_multiple_bracketed_targets_in_order() {
        val targets = parse_list_unsubscribe(
            "<mailto:leave@list.example.com?subject=unsubscribe>, <https://list.example.com/u/1>",
        )
        assertEquals(listOf("https://list.example.com/u/1"), targets.web_links)
        assertEquals(listOf("leave@list.example.com?subject=unsubscribe"), targets.mailto_addresses)
    }

    @Test
    fun unfolds_header_whitespace_inside_a_target() {
        val targets = parse_list_unsubscribe("<https://list.example.com/u/\r\n\t abc?id=1>,\r\n <mailto:a@b.example>")
        assertEquals("https://list.example.com/u/abc?id=1", targets.https_link)
        assertEquals("a@b.example", targets.mailto)
    }

    @Test
    fun accepts_unbracketed_comma_separated_targets() {
        val targets = parse_list_unsubscribe("mailto:a@b.example, https://b.example/u")
        assertEquals("a@b.example", targets.mailto)
        assertEquals("https://b.example/u", targets.https_link)
    }

    @Test
    fun prefers_https_over_http_regardless_of_order() {
        val targets = parse_list_unsubscribe("<http://b.example/u>, <https://b.example/secure>")
        assertEquals("https://b.example/secure", targets.preferred_web_link)
        assertEquals(listOf("http://b.example/u", "https://b.example/secure"), targets.web_links)
    }

    @Test
    fun falls_back_to_http_when_no_https_link_exists() {
        val targets = parse_list_unsubscribe("<http://b.example/u>")
        assertNull(targets.https_link)
        assertEquals("http://b.example/u", targets.preferred_web_link)
    }

    @Test
    fun keeps_mailto_parameters_and_decodes_the_at_sign_for_validation() {
        val targets = parse_list_unsubscribe("<mailto:leave%40list.example.com?subject=stop&body=go>")
        assertEquals("leave%40list.example.com?subject=stop&body=go", targets.mailto)
    }

    @Test
    fun rejects_dangerous_and_unknown_schemes() {
        val targets = parse_list_unsubscribe(
            "<javascript:alert(1)>, <intent://x#Intent;end>, <file:///etc/passwd>, " +
                "<data:text/html,hi>, <ftp://b.example/u>, <content://x/y>",
        )
        assertTrue(targets.web_links.isEmpty())
        assertTrue(targets.mailto_addresses.isEmpty())
    }

    @Test
    fun rejects_userinfo_hosts_and_empty_hosts() {
        val targets = parse_list_unsubscribe("<https://good.example@evil.example/u>, <https:///u>, <https:/b.example>")
        assertTrue(targets.web_links.isEmpty())
    }

    @Test
    fun rejects_malformed_mailto() {
        val targets = parse_list_unsubscribe("<mailto:>, <mailto:@b.example>, <mailto:a@>, <mailto:nobody?subject=x>")
        assertTrue(targets.mailto_addresses.isEmpty())
    }

    @Test
    fun drops_duplicates_and_oversized_targets() {
        val huge = "https://b.example/" + "a".repeat(3000)
        val targets = parse_list_unsubscribe("<https://b.example/u>, <https://b.example/u>, <$huge>")
        assertEquals(listOf("https://b.example/u"), targets.web_links)
    }

    @Test
    fun blank_header_yields_nothing() {
        assertTrue(parse_list_unsubscribe(null).web_links.isEmpty())
        assertTrue(parse_list_unsubscribe("   ").mailto_addresses.isEmpty())
    }

    @Test
    fun one_click_uses_the_https_link_even_when_http_comes_first() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<http://b.example/u>, <https://b.example/oc>",
            list_unsubscribe_post = one_click_post,
        )
        assertEquals("one-click", info.method)
        assertEquals("https://b.example/oc", info.unsubscribe_link)
    }

    @Test
    fun one_click_is_never_used_with_only_an_http_link() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<http://b.example/oc>",
            list_unsubscribe_post = one_click_post,
        )
        assertFalse(info.method == "one-click")
    }

    @Test
    fun mailto_only_sender_uses_mailto_method_with_params() {
        val info = detect_unsubscribe_info(list_unsubscribe = "<mailto:leave@b.example?subject=unsubscribe>")
        assertEquals("mailto", info.method)
        assertEquals("leave@b.example?subject=unsubscribe", info.unsubscribe_mailto)
    }

    @Test
    fun folded_link_only_header_is_detected() {
        val info = detect_unsubscribe_info(list_unsubscribe = "<https://b.example/\r\n u>")
        assertEquals("link", info.method)
        assertEquals("https://b.example/u", info.unsubscribe_link)
    }

    @Test
    fun web_url_check_only_accepts_http_and_https() {
        assertTrue(is_web_unsubscribe_url("https://b.example/u"))
        assertTrue(is_web_unsubscribe_url(" HTTP://b.example/u"))
        assertFalse(is_web_unsubscribe_url("mailto:a@b.example"))
        assertFalse(is_web_unsubscribe_url("javascript:alert(1)"))
        assertFalse(is_web_unsubscribe_url(null))
    }

    @Test
    fun rate_limit_stops_the_fallback_chain() = runBlocking {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<https://b.example/oc>, <mailto:leave@b.example>",
            list_unsubscribe_post = one_click_post,
        )
        val sent = mutableListOf<ProxyUnsubscribeRequest>()
        val outcome = execute_unsubscribe_checked(info) { request ->
            sent += request
            ProxyUnsubscribeResult.rate_limited
        }
        assertEquals(UnsubscribeOutcome.rate_limited, outcome)
        assertEquals(1, sent.size)
    }

    @Test
    fun rejected_one_click_falls_back_to_mailto() = runBlocking {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<https://b.example/oc>, <mailto:leave@b.example>",
            list_unsubscribe_post = one_click_post,
        )
        val sent = mutableListOf<String>()
        val outcome = execute_unsubscribe_checked(info) { request ->
            sent += request.method
            if (request.method == "mailto") ProxyUnsubscribeResult.accepted else ProxyUnsubscribeResult.rejected
        }
        assertEquals(UnsubscribeOutcome.unsubscribed, outcome)
        assertEquals(listOf("one-click", "mailto"), sent)
    }

    @Test
    fun thrown_errors_count_as_rejected_and_end_in_manual() = runBlocking {
        val info = detect_unsubscribe_info(list_unsubscribe = "<mailto:leave@b.example>")
        val outcome = execute_unsubscribe_checked(info) { throw IllegalStateException("boom") }
        assertEquals(UnsubscribeOutcome.manual_required, outcome)
    }

    @Test
    fun mailto_request_carries_the_receiving_address() = runBlocking {
        val info = detect_unsubscribe_info(list_unsubscribe = "<mailto:leave@b.example>")
        var seen: ProxyUnsubscribeRequest? = null
        execute_unsubscribe_checked(info, UnsubscribeSender(from_address = "me@alias.example")) { request ->
            seen = request
            ProxyUnsubscribeResult.accepted
        }
        assertEquals("me@alias.example", seen?.from_address)
    }
}
