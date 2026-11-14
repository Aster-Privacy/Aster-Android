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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForwardingAliasTest {

    private val sl_alias = "reverse_alias_718jakwi@simplelogin.co"

    private fun headers(vararg entries: Pair<String, String>): List<Pair<String, String>> =
        entries.toList()

    @Test
    fun extracts_original_sender_from_simplelogin_forward() {
        val result = detect_forwarded_alias(
            headers(
                "X-SimpleLogin-Type" to "Forward",
                "X-SimpleLogin-Original-From" to "Hi Example <hi@example.com>",
                "From" to "John <reverse_alias_718jakwi@simplelogin.co>",
            ),
            sl_alias,
        )
        assertEquals("simplelogin", result?.service)
        assertEquals("hi@example.com", result?.original?.email)
        assertEquals("Hi Example", result?.original?.name)
    }

    @Test
    fun falls_back_to_envelope_from_when_original_from_absent() {
        val result = detect_forwarded_alias(
            headers(
                "X-SimpleLogin-Type" to "Forward",
                "X-SimpleLogin-Envelope-From" to "hi@example.com",
            ),
            sl_alias,
        )
        assertEquals("hi@example.com", result?.original?.email)
    }

    @Test
    fun header_names_are_case_insensitive() {
        val result = detect_forwarded_alias(
            headers("x-simplelogin-original-from" to "hi@example.com"),
            sl_alias,
        )
        assertEquals("hi@example.com", result?.original?.email)
    }

    @Test
    fun handles_addy_original_sender() {
        val result = detect_forwarded_alias(
            headers("X-AnonAddy-Original-Sender" to "hi@example.com"),
            "alias@anonaddy.me",
        )
        assertEquals("addy", result?.service)
        assertEquals("hi@example.com", result?.original?.email)
    }

    @Test
    fun ignores_simplelogin_reply_direction() {
        val result = detect_forwarded_alias(
            headers(
                "X-SimpleLogin-Type" to "Reply",
                "X-SimpleLogin-Original-From" to "hi@example.com",
            ),
            sl_alias,
        )
        assertNull(result)
    }

    @Test
    fun returns_null_when_original_equals_the_alias() {
        val result = detect_forwarded_alias(
            headers("X-SimpleLogin-Original-From" to sl_alias),
            sl_alias,
        )
        assertNull(result)
    }

    @Test
    fun does_not_trust_spoofed_headers_from_non_forwarder_sender() {
        val result = detect_forwarded_alias(
            headers(
                "X-SimpleLogin-Type" to "Forward",
                "X-SimpleLogin-Original-From" to "Your Bank <security@bank.com>",
                "From" to "Attacker <attacker@evil.com>",
            ),
            "attacker@evil.com",
        )
        assertNull(result)
    }

    @Test
    fun does_not_trust_addy_headers_from_non_addy_sender() {
        val result = detect_forwarded_alias(
            headers("X-AnonAddy-Original-Sender" to "security@bank.com"),
            "attacker@evil.com",
        )
        assertNull(result)
    }

    @Test
    fun returns_null_for_ordinary_mail() {
        val result = detect_forwarded_alias(
            headers("From" to "Real Sender <real@gmail.com>"),
            "real@gmail.com",
        )
        assertNull(result)
    }

    @Test
    fun returns_null_without_raw_headers() {
        assertNull(detect_forwarded_alias(emptyList(), sl_alias))
    }

    @Test
    fun matches_simplelogin_subdomain_alias() {
        val result = detect_forwarded_alias(
            headers("X-SimpleLogin-Original-From" to "hi@example.com"),
            "rev@mail.simplelogin.co",
        )
        assertEquals("hi@example.com", result?.original?.email)
    }

    @Test
    fun parses_quoted_display_name_with_comma() {
        val result = detect_forwarded_alias(
            headers("X-SimpleLogin-Original-From" to "\"Doe, John\" <john@example.com>"),
            sl_alias,
        )
        assertEquals("john@example.com", result?.original?.email)
        assertEquals("Doe, John", result?.original?.name)
    }

    @Test
    fun extracts_address_when_display_name_is_rfc2047_encoded() {
        val result = detect_forwarded_alias(
            headers("X-SimpleLogin-Original-From" to "=?utf-8?B?w4ZsaWNl?= <alice@example.com>"),
            sl_alias,
        )
        assertEquals("alice@example.com", result?.original?.email)
    }

    @Test
    fun handles_addy_original_sender_with_display_name() {
        val result = detect_forwarded_alias(
            headers("X-AnonAddy-Original-Sender" to "Jane <jane@example.com>"),
            "alias@addy.io",
        )
        assertEquals("addy", result?.service)
        assertEquals("jane@example.com", result?.original?.email)
        assertEquals("Jane", result?.original?.name)
    }

    @Test
    fun resolve_produces_display_fields_for_forwarded_message() {
        val display = resolve_forwarding_display(
            sl_alias,
            headers("X-SimpleLogin-Original-From" to "Hi Example <hi@example.com>"),
        )
        assertEquals("Hi Example", display?.display_sender_name)
        assertEquals("hi@example.com", display?.display_sender_email)
        assertEquals("simplelogin", display?.forwarding_service)
    }

    @Test
    fun resolve_derives_display_name_from_address_when_none_given() {
        val display = resolve_forwarding_display(
            sl_alias,
            headers("X-SimpleLogin-Original-From" to "hi@example.com"),
        )
        assertEquals("hi", display?.display_sender_name)
        assertEquals("hi@example.com", display?.display_sender_email)
    }

    @Test
    fun resolve_returns_null_for_ordinary_mail() {
        assertNull(
            resolve_forwarding_display(
                "real@gmail.com",
                headers("From" to "real@gmail.com"),
            ),
        )
    }

    @Test
    fun parse_email_address_handles_bare_address() {
        val parsed = parse_email_address("  <hi@example.com>  ")
        assertEquals("hi@example.com", parsed?.email)
        assertNull(parsed?.name)
    }

    @Test
    fun parse_email_address_rejects_invalid_input() {
        assertNull(parse_email_address(""))
        assertNull(parse_email_address("not an email"))
        assertNull(parse_email_address("Name <not-an-email>"))
    }

    @Test
    fun displayed_sender_prefers_display_fields() {
        assertEquals("Hi Example", displayed_sender_name("Hi Example", "John"))
        assertEquals("hi@example.com", displayed_sender_email("hi@example.com", sl_alias))
    }

    @Test
    fun displayed_sender_falls_back_to_literal() {
        assertEquals("John", displayed_sender_name(null, "John"))
        assertEquals(sl_alias, displayed_sender_email(null, sl_alias))
    }

    @Test
    fun reads_name_from_addy_original_from_header() {
        val result = detect_forwarded_alias(
            headers(
                "X-AnonAddy-Original-From-Header" to "Jane Doe <jane@example.com>",
                "X-AnonAddy-Original-Sender" to "jane@example.com",
            ),
            "shop@addy.io",
        )
        assertEquals("addy", result?.service)
        assertEquals("Jane Doe", result?.original?.name)
        assertEquals("jane@example.com", result?.original?.email)
    }

    @Test
    fun accepts_addy_shared_domains() {
        listOf("4wrd.cc", "mailer.me", "addymail.com").forEach { domain ->
            val result = detect_forwarded_alias(
                headers("X-AnonAddy-Original-Sender" to "jane@example.com"),
                "shop@$domain",
            )
            assertEquals("jane@example.com", result?.original?.email)
        }
    }

    @Test
    fun ignores_lookalike_forwarder_domain() {
        assertNull(
            detect_forwarded_alias(
                headers("X-AnonAddy-Original-Sender" to "jane@example.com"),
                "shop@notaddy.io",
            ),
        )
    }

    @Test
    fun rejects_addy_forward_when_dmarc_failed() {
        assertNull(
            detect_forwarded_alias(
                headers(
                    "X-AnonAddy-Original-From-Header" to "Bank <security@bank.example>",
                    "X-AnonAddy-Authentication-Results" to "mx.addy.io; spf=fail; dmarc=fail (p=reject)",
                ),
                "shop@addy.io",
            ),
        )
    }

    @Test
    fun rejects_conflicting_duplicate_headers() {
        assertNull(
            detect_forwarded_alias(
                headers(
                    "X-AnonAddy-Original-Sender" to "jane@example.com",
                    "X-AnonAddy-Original-Sender" to "ceo@bank.example",
                ),
                "shop@addy.io",
            ),
        )
    }

    @Test
    fun rejects_oversized_header() {
        assertNull(
            detect_forwarded_alias(
                headers(
                    "X-AnonAddy-Original-From-Header" to "${"a".repeat(1000)} <jane@example.com>",
                ),
                "shop@addy.io",
            ),
        )
    }

    @Test
    fun does_not_resolve_when_forwarder_failed_authentication() {
        assertNull(
            resolve_forwarding_display(
                "shop@addy.io",
                headers("X-AnonAddy-Original-Sender" to "jane@example.com"),
                ForwardingAuthResults(dkim_result = "fail", dmarc_result = "fail", sender_aligned = false),
            ),
        )
    }

    @Test
    fun resolves_when_forwarder_authenticated() {
        val result = resolve_forwarding_display(
            "shop@addy.io",
            headers("X-AnonAddy-Original-From-Header" to "Jane Doe <jane@example.com>"),
            ForwardingAuthResults(dkim_result = "pass", dmarc_result = "pass", sender_aligned = true),
        )
        assertEquals("Jane Doe", result?.display_sender_name)
        assertEquals("jane@example.com", result?.display_sender_email)
    }

    @Test
    fun decodes_encoded_word_names() {
        val parsed = parse_original_sender("=?UTF-8?B?Sm9zw6kgR2FyY8OtYQ==?= <jose@example.com>")
        assertEquals("Jos\u00e9 Garc\u00eda", parsed?.name)
        val quoted = parse_original_sender("=?ISO-8859-1?Q?Andr=E9_Smith?= <andre@example.com>")
        assertEquals("Andr\u00e9 Smith", quoted?.name)
    }

    @Test
    fun strips_control_and_bidi_characters() {
        val parsed = parse_original_sender("Pay\u202epal\u0007 Team <team@example.com>")
        assertEquals("Pay pal Team", parsed?.name)
    }

    @Test
    fun caps_long_display_names() {
        val parsed = parse_original_sender("${"n".repeat(300)} <long@example.com>")
        assertEquals(128, parsed?.name?.length)
    }

    @Test
    fun forwarding_address_returns_literal_from_only_when_forwarded() {
        assertEquals("shop@addy.io", forwarding_address("jane@example.com", "shop@addy.io"))
        assertNull(forwarding_address(null, "jane@example.com"))
        assertNull(forwarding_address("Jane@example.com", "jane@example.com"))
    }
}
