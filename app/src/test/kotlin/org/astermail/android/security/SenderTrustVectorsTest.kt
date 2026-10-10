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

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTrustVectorsTest {

    private fun load(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name))
        return JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private val data: JSONObject by lazy { load("sender_trust_data.json") }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun objects(array: JSONArray): List<JSONObject> =
        (0 until array.length()).map { array.getJSONObject(it) }

    private fun text(source: JSONObject, key: String): String? =
        if (source.has(key) && !source.isNull(key)) source.getString(key) else null

    private fun flag(source: JSONObject, key: String): Boolean? =
        if (source.has(key) && !source.isNull(key)) source.getBoolean(key) else null

    private fun input_of(source: JSONObject): SenderTrustInput =
        SenderTrustInput(
            name = text(source, "name"),
            email = text(source, "email"),
            reply_to = text(source, "reply_to"),
            spf_result = text(source, "spf_result"),
            dkim_result = text(source, "dkim_result"),
            dmarc_result = text(source, "dmarc_result"),
            sender_aligned = flag(source, "sender_aligned"),
            sender_verified = flag(source, "sender_verified"),
            sender_verified_domain = text(source, "sender_verified_domain"),
            is_internal = flag(source, "is_internal") == true,
            own_addresses = strings(source.optJSONArray("own_addresses")),
            contacts = source.optJSONArray("contacts")?.let { array ->
                objects(array).map { SenderTrustContact(it.optString("name", ""), strings(it.optJSONArray("emails"))) }
            } ?: emptyList(),
        )

    private fun aligned(name: String?, email: String): SenderTrust =
        evaluate_sender_trust(SenderTrustInput(name = name, email = email, sender_aligned = true))

    private fun brand_domains(): List<String> =
        objects(data.getJSONArray("brands")).flatMap { strings(it.getJSONArray("domains")) }

    @Test
    fun vectors_have_unique_ids() {
        val ids = objects(load("sender_trust_vectors.json").getJSONArray("vectors")).map { it.getString("id") }
        assertTrue(ids.size >= 82)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun every_shared_vector_matches() {
        for (vector in objects(load("sender_trust_vectors.json").getJSONArray("vectors"))) {
            val id = vector.getString("id")
            val result = evaluate_sender_trust(input_of(vector.getJSONObject("input")))
            val expect = vector.getJSONObject("expect")
            for (key in expect.keys()) {
                val label = "$id $key"
                when (key) {
                    "name" -> assertEquals(label, expect.getString(key), result.name)
                    "address" -> assertEquals(label, expect.getString(key), result.address)
                    "domain" -> assertEquals(label, expect.getString(key), result.domain)
                    "auth_state" -> assertEquals(label, expect.getString(key), result.auth_state.name)
                    "contact_notice" -> assertEquals(label, text(expect, key), result.contact_notice)
                    "reply_to" -> assertEquals(label, text(expect, key), result.reply_to)
                    "show_identity_marks" -> assertEquals(label, expect.getBoolean(key), result.show_identity_marks)
                    "show_verified_badge" -> assertEquals(label, expect.getBoolean(key), result.show_verified_badge)
                    "warning" -> {
                        if (expect.isNull(key)) {
                            assertNull(label, result.warning)
                        } else {
                            val warning = expect.getJSONObject(key)
                            assertEquals(label, warning.getString("kind"), result.warning?.kind?.name)
                            assertEquals(label, warning.getString("claimed"), result.warning?.claimed)
                        }
                    }
                    else -> throw AssertionError("unknown expectation $label")
                }
            }
        }
    }

    @Test
    fun every_confusable_maps_one_code_point_to_ascii() {
        val confusables = data.getJSONObject("confusables")
        for (from in confusables.keys()) {
            assertEquals(from, 1, from.codePointCount(0, from.length))
            assertTrue(from, Regex("^[a-z]$").matches(confusables.getString(from)))
        }
    }

    @Test
    fun brand_names_never_warn_on_their_own_domains() {
        for (brand in objects(data.getJSONArray("brands"))) {
            for (name in strings(brand.getJSONArray("names"))) {
                for (domain in strings(brand.getJSONArray("domains"))) {
                    val result = aligned(name, "noreply@$domain")
                    assertNull("$name $domain", result.warning)
                    assertEquals("$name $domain", name, result.name)
                }
            }
        }
    }

    @Test
    fun protected_domains_are_never_lookalikes_of_each_other() {
        val domains = strings(data.getJSONObject("home_brand").getJSONArray("domains")) +
            brand_domains() +
            strings(data.getJSONArray("lookalike_only_domains")) +
            strings(data.getJSONArray("relay_domains"))
        for (domain in domains) {
            assertNull(domain, aligned(null, "noreply@$domain").warning)
        }
    }

    @Test
    fun unambiguous_brand_names_warn_on_unrelated_domains() {
        for (brand in objects(data.getJSONArray("brands"))) {
            if (brand.optBoolean("ambiguous", false)) continue
            for (name in strings(brand.getJSONArray("names"))) {
                val result = aligned(name, "noreply@unrelated.example")
                assertEquals(name, SenderWarningKind.brand_name, result.warning?.kind)
                assertEquals(name, "", result.name)
            }
        }
    }

    @Test
    fun ordinary_names_do_not_warn() {
        val ordinary_names = listOf(
            "Jordan Lee",
            "Dr. María-José O'Neil",
            "李雷",
            "Иван Петров",
            "Αλέξανδρος",
            "محمد",
            "Ian Illingworth",
            "Bill Ill",
            "Lily Lilliput",
            "Apple",
            "Chase",
            "Meta",
            "Adobe",
            "Stripe",
            "Discord",
            "Aster",
            "Amazonia Travel",
            "Googleplex Tours",
            "Microsoftware Ltd",
            "Paypalace",
            "Team Rocket",
            "Customer Support",
            "noreply",
            "Billing",
            "Security Alerts",
            "The Outlook Inn Hotel",
            "Netflixing Podcast",
            "DHLS Logistics",
            "Yahoos Anonymous",
            "Spotifyers",
            "Bank of Ireland",
            "First America Title",
            "100% Pure",
            "L1 Capital",
            "O0O",
            "",
        )
        for (name in ordinary_names) {
            val result = evaluate_sender_trust(
                SenderTrustInput(
                    name = name,
                    email = "sender@ordinary-business.example",
                    spf_result = "pass",
                    dkim_result = "pass",
                    dmarc_result = "pass",
                    sender_aligned = true,
                ),
            )
            assertNull(name, result.warning)
            assertTrue(name, result.show_identity_marks)
        }
    }

    @Test
    fun ordinary_domains_do_not_warn() {
        val ordinary_domains = listOf(
            "example.com",
            "modern.io",
            "burn.example",
            "live.example",
            "mail.example.co.uk",
            "googl.example",
            "pay.example",
            "apple-tree-farm.example",
            "my-paypal-receipts.example",
            "microsoft-partner.example",
            "t-online.de",
            "gmx.net",
            "web.de",
            "orange.fr",
            "bbc.co.uk",
            "u-tokyo.ac.jp",
        )
        for (domain in ordinary_domains) {
            val result = aligned("Jordan Lee", "jordan@$domain")
            assertNull(domain, result.warning)
            assertEquals(domain, domain, result.domain)
        }
    }

    @Test
    fun confusable_spellings_share_a_skeleton() {
        assertEquals(sender_skeleton("PayPal"), sender_skeleton("PayPaI"))
        assertEquals(sender_skeleton("paypal"), sender_skeleton("paypa1"))
        assertEquals(sender_skeleton("microsoft"), sender_skeleton("rnicrosoft"))
        assertEquals(sender_skeleton("wise"), sender_skeleton("vvise"))
        assertEquals(sender_skeleton("google"), sender_skeleton("gооgle"))
        assertEquals(sender_skeleton("Paypal"), sender_skeleton("Páypal"))
        assertEquals(sender_skeleton("paypal"), sender_skeleton("pay​pal"))
        assertNotEquals(sender_skeleton("paypol"), sender_skeleton("paypal"))
    }

    @Test
    fun punycode_round_trips() {
        assertEquals("xn--mnchen-3ya.example", sender_domain_to_ascii("München.Example"))
        assertEquals("xn--r8jz45g.example", sender_domain_to_ascii("例え.example"))
        assertEquals("xn--pple-43d.com", sender_domain_to_ascii("аpple.com"))
        assertEquals("münchen.example", sender_domain_for_display("xn--mnchen-3ya.example"))
        assertEquals("xn--pple-43d.com", sender_domain_for_display("xn--pple-43d.com"))
        assertEquals("例え.example", sender_domain_for_display("xn--r8jz45g.example"))
    }

    @Test
    fun malformed_domains_are_rejected() {
        val malformed = listOf(
            "",
            "localhost",
            "exa mple.com",
            "-bad.example",
            "bad-.example",
            "a..example",
            "under_score.example",
            "192.168.1.1",
            "exam‮ple.com/",
            "a".repeat(64) + ".example",
        )
        for (domain in malformed) {
            assertEquals(domain, "", sender_domain_to_ascii(domain))
        }
    }

    @Test
    fun undecodable_punycode_labels_stay_ascii() {
        assertEquals("xn--999999999.example", sender_domain_for_display("xn--999999999.example"))
        assertEquals("xn--abc.example", sender_domain_for_display("xn--abc.example"))
    }

    @Test
    fun registrable_domain_is_found() {
        assertEquals("example.com", sender_registrable_domain("example.com"))
        assertEquals("example.com", sender_registrable_domain("a.b.example.com"))
        assertEquals("example.co.uk", sender_registrable_domain("a.example.co.uk"))
        assertEquals("u-tokyo.ac.jp", sender_registrable_domain("mail.u-tokyo.ac.jp"))
        assertEquals("example.io", sender_registrable_domain("a.b.example.io"))
    }

    @Test
    fun names_are_sanitized_for_any_input() {
        assertEquals("", sanitize_display_name(null))
        assertEquals("", sanitize_display_name("\ud83d"))
        assertEquals("ab", sanitize_display_name("a\ud83db"))
        assertEquals("Jordan", sanitize_display_name("'\"Jordan\"'"))
        assertEquals("O'Neil", sanitize_display_name("O'Neil"))
    }

    @Test
    fun hostile_input_is_deterministic_and_total() {
        val hostile = listOf(
            "\u0000",
            "@",
            "@@",
            "a@",
            "@b",
            "<>",
            "a@b@c.example",
            "‮@‮.‮",
            "x@xn--.example",
            "x@xn--a.example",
            "a".repeat(5000) + "@" + "b".repeat(5000) + ".example",
            "🙂@🙂.example",
        )
        for (email in hostile) {
            for (name in hostile) {
                val input = SenderTrustInput(name = name, email = email, reply_to = email)
                val first = evaluate_sender_trust(input)
                val second = evaluate_sender_trust(input)
                assertEquals(first, second)
                assertTrue(first.address.length <= 640)
            }
        }
    }
}
