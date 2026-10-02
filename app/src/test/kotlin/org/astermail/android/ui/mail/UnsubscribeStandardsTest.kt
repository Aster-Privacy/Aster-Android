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

class UnsubscribeStandardsTest {
    private val one_click_post = "List-Unsubscribe=One-Click"

    @Test
    fun one_click_needs_the_exact_post_value() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<https://sender.example.com/oc>",
            list_unsubscribe_post = "yes",
        )
        assertEquals("link", info.method)
        assertNull(info.list_unsubscribe_post)
    }

    @Test
    fun post_value_matches_in_any_letter_case() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<https://sender.example.com/oc>",
            list_unsubscribe_post = " list-unsubscribe=one-click ",
        )
        assertEquals("one-click", info.method)
        assertEquals(one_click_post, info.list_unsubscribe_post)
        assertNull(get_manual_unsubscribe_url(info))
    }

    @Test
    fun one_click_never_targets_a_plain_http_endpoint() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<http://sender.example.com/oc>",
            list_unsubscribe_post = one_click_post,
        )
        assertFalse(info.has_unsubscribe)
        assertEquals("none", info.method)
        assertNull(get_manual_unsubscribe_url(info))
    }

    @Test
    fun one_click_picks_the_https_endpoint_among_several() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<mailto:stop@sender.example.com>, <http://sender.example.com/a>, " +
                "<https://sender.example.com/b>",
            list_unsubscribe_post = one_click_post,
        )
        assertEquals("one-click", info.method)
        assertEquals("https://sender.example.com/b", info.unsubscribe_link)
        assertEquals("stop@sender.example.com", info.unsubscribe_mailto)
    }

    @Test
    fun failed_signature_check_disables_one_click() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<https://sender.example.com/oc>, <mailto:stop@sender.example.com>",
            list_unsubscribe_post = one_click_post,
            dkim_result = "fail",
        )
        assertEquals("mailto", info.method)
        assertEquals("mailto:stop@sender.example.com", get_manual_unsubscribe_url(info))
    }

    @Test
    fun passing_or_missing_signature_result_allows_one_click() {
        for (dkim_result in listOf("pass", "PASS", null, "")) {
            val info = detect_unsubscribe_info(
                list_unsubscribe = "<https://sender.example.com/oc>",
                list_unsubscribe_post = one_click_post,
                dkim_result = dkim_result,
            )
            assertEquals("one-click", info.method)
        }
    }

    @Test
    fun address_is_preferred_over_a_plain_page() {
        val info = detect_unsubscribe_info(
            list_unsubscribe = "<https://sender.example.com/u/42>, <mailto:stop@sender.example.com>",
        )
        assertEquals("mailto", info.method)
        assertEquals("https://sender.example.com/u/42", get_manual_unsubscribe_url(info))
    }

    @Test
    fun plain_header_page_is_a_link() {
        val info = detect_unsubscribe_info(list_unsubscribe = "<https://sender.example.com/u/42>")
        assertEquals("link", info.method)
        assertEquals("https://sender.example.com/u/42", get_manual_unsubscribe_url(info))
    }

    @Test
    fun one_click_request_always_carries_the_standard_body() {
        val requests = build_proxy_unsubscribe_requests(
            UnsubscribeInfo(
                has_unsubscribe = true,
                method = "one-click",
                unsubscribe_link = "https://sender.example.com/oc",
                list_unsubscribe_post = "list-unsubscribe=one-click",
            ),
        )
        assertEquals(1, requests.size)
        assertEquals("one-click", requests[0].method)
        assertEquals(one_click_post, requests[0].list_unsubscribe_post)
    }

    @Test
    fun plain_link_is_never_requested_for_the_user() {
        val info = UnsubscribeInfo(
            has_unsubscribe = true,
            method = "link",
            unsubscribe_link = "https://sender.example.com/unsubscribe?id=1",
            unsubscribe_page_url = "https://sender.example.com/unsubscribe?id=1",
        )
        assertTrue(build_proxy_unsubscribe_requests(info).isEmpty())
        var calls = 0
        val outcome = runBlocking {
            execute_unsubscribe(info) {
                calls += 1
                true
            }
        }
        assertEquals(UnsubscribeOutcome.manual_required, outcome)
        assertEquals(0, calls)
    }

    @Test
    fun failed_one_click_falls_back_to_the_address() {
        val sent = mutableListOf<ProxyUnsubscribeRequest>()
        val outcome = runBlocking {
            execute_unsubscribe(
                UnsubscribeInfo(
                    has_unsubscribe = true,
                    method = "one-click",
                    unsubscribe_link = "https://sender.example.com/oc",
                    unsubscribe_mailto = "stop@sender.example.com",
                ),
            ) { request ->
                sent += request
                request.method == "mailto"
            }
        }
        assertEquals(UnsubscribeOutcome.unsubscribed, outcome)
        assertEquals(listOf("one-click", "mailto"), sent.map { it.method })
    }

    @Test
    fun thrown_request_counts_as_a_failure() {
        val outcome = runBlocking {
            execute_unsubscribe(
                UnsubscribeInfo(
                    has_unsubscribe = true,
                    method = "mailto",
                    unsubscribe_mailto = "stop@sender.example.com",
                ),
            ) { throw IllegalStateException("offline") }
        }
        assertEquals(UnsubscribeOutcome.manual_required, outcome)
    }
}
