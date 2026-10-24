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

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTrustBypassTest {

    private fun load(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name))
        return JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private val confusables: Map<String, String> by lazy {
        val source = load("sender_trust_confusables.json").getJSONObject("confusables")
        source.keys().asSequence().associateWith { source.getString(it) }
    }

    private val data: JSONObject by lazy { load("sender_trust_data.json") }

    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }

    private fun char_of(code: Int): String = String(Character.toChars(code))

    private fun points(text: String): List<String> = text.codePoints().toArray().map { char_of(it) }

    private fun hex_of(code: Int): String = "U+" + Integer.toHexString(code).uppercase(Locale.ROOT).padStart(4, '0')

    private fun lower(text: String): String = text.lowercase(Locale.ROOT)

    private fun evaluate(
        name: String,
        email: String,
        own_addresses: List<String> = emptyList(),
        contacts: List<SenderTrustContact> = emptyList(),
    ): SenderTrust = evaluate_sender_trust(
        SenderTrustInput(
            name = name,
            email = email,
            spf_result = "pass",
            dkim_result = "pass",
            dmarc_result = "pass",
            sender_aligned = true,
            own_addresses = own_addresses,
            contacts = contacts,
        ),
    )

    private fun letters_by_target(pattern: Regex): Map<String, List<String>> {
        val by_letter = HashMap<String, MutableList<String>>()
        for ((from, to) in confusables) {
            if (to.length != 1 || !pattern.matches(to)) continue
            if (lower(Normalizer.normalize(from, Normalizer.Form.NFKC)) != lower(from)) continue
            by_letter.getOrPut(to) { ArrayList() }.add(from)
        }
        return by_letter
    }

    private val invisible_points = intArrayOf(
        0xfff0, 0xfff1, 0xfff2, 0xfff3, 0xfff4, 0xfff5, 0xfff6, 0xfff7, 0xfff8,
        0x00ad, 0x034f, 0x061c, 0x115f, 0x1160, 0x17b4, 0x17b5, 0x180b, 0x180e,
        0x180f, 0x200b, 0x200c, 0x200d, 0x2060, 0x2064, 0x2065, 0x206f, 0x3164,
        0xfe00, 0xfe0f, 0xfeff, 0xffa0, 0x1bca0, 0x1d173, 0xe0001, 0xe0020, 0xe0100,
        0xe0fff,
    )

    @Test
    fun `invisible characters do not hide a brand name`() {
        for (code in invisible_points) {
            val mark = char_of(code)
            for (name in listOf(
                "Pay${mark}Pal Security",
                "${mark}P${mark}a${mark}y${mark}P${mark}a${mark}l$mark Security",
            )) {
                val result = evaluate(name, "billing@evil.example")
                assertEquals("${hex_of(code)} $name", SenderWarningKind.brand_name, result.warning?.kind)
                assertEquals(hex_of(code), "", result.name)
                assertFalse(hex_of(code), result.show_identity_marks)
            }
        }
    }

    @Test
    fun `invisible characters do not hide the home brand name`() {
        for (code in invisible_points) {
            val mark = char_of(code)
            for (name in listOf("Aster$mark Mail Security", "As${mark}ter Ma${mark}il Security")) {
                val result = evaluate(name, "billing@evil.example")
                assertEquals("${hex_of(code)} $name", SenderWarningKind.home_brand_name, result.warning?.kind)
                assertFalse(hex_of(code), result.show_identity_marks)
            }
        }
    }

    @Test
    fun `invisible characters do not hide an address in the name`() {
        for (code in invisible_points) {
            val mark = char_of(code)
            for (name in listOf(
                "ceo@victim${mark}corp.com",
                "ceo$mark@victimcorp.com",
                "ceo@${mark}victimcorp$mark.com",
            )) {
                val result = evaluate(name, "billing@evil.example")
                assertEquals(
                    "${hex_of(code)} $name",
                    SenderWarning(SenderWarningKind.address_in_name, "ceo@victimcorp.com"),
                    result.warning,
                )
                assertFalse(hex_of(code), result.show_identity_marks)
            }
        }
    }

    @Test
    fun `invisible characters are dropped from the shown name`() {
        for (code in invisible_points) {
            assertEquals(hex_of(code), "Jordan Lee", sanitize_display_name("Jor${char_of(code)}dan Lee"))
        }
    }

    @Test
    fun `emoji joiner sequences stay in the shown name`() {
        val family = "👨‍👩‍👧 Family"
        assertEquals(family, sanitize_display_name(family))
        assertEquals("Love ❤️", sanitize_display_name("Love ❤️"))
        assertEquals("Team 1️⃣", sanitize_display_name("Team 1️⃣"))
        assertEquals("می‌رزا", sanitize_display_name("می‌رزا"))
    }

    @Test
    fun `reported lookalike letters share a skeleton with the brand`() {
        assertEquals(sender_skeleton("PayPal"), sender_skeleton("ᏢayᏢal"))
        assertEquals(sender_skeleton("PayPal"), sender_skeleton("ⲢayⲢal"))
        assertEquals(sender_skeleton("PayPal"), sender_skeleton("ꓑayꓑal"))
        assertEquals(sender_skeleton("paypal"), sender_skeleton("ƿaypal"))
        assertEquals(sender_skeleton("astermail"), sender_skeleton("astermaiȴ"))
        assertEquals(sender_skeleton("Aster"), sender_skeleton("Ꭺster"))
        assertEquals(sender_skeleton("Google"), sender_skeleton("Ꮐoogle"))
        assertEquals(sender_skeleton("Microsoft"), sender_skeleton("Ꮇicrosoft"))
        assertEquals(sender_skeleton("chase"), sender_skeleton("ϲhase"))
    }

    @Test
    fun `cherokee small letters map like their capitals`() {
        for (code in 0xab70..0xabbf) {
            val small = char_of(code)
            val capital = small.uppercase(Locale.ROOT)
            if (confusables[small] != null) continue
            val expected = confusables[capital] ?: continue
            assertEquals(hex_of(code), expected, sender_skeleton(small))
        }
    }

    @Test
    fun `every generated confusable maps one code point to ascii`() {
        assertTrue(confusables.size > 2000)
        val ascii = Regex("^[a-z0-9]+$")
        for ((from, to) in confusables) {
            assertEquals(from, 1, from.codePointCount(0, from.length))
            assertTrue(from, from.codePointAt(0) > 0x7f)
            assertTrue(from, ascii.matches(to))
        }
    }

    @Test
    fun `lookalike brand names warn`() {
        for (name in listOf(
            "ᏢayᏢal",
            "ⲢayⲢal",
            "ꓑayꓑal",
            "ᏢayᏢal Security",
            "Ꮐoogle",
            "Ꮇicrosoft",
            "Ꮐoogle Security",
            "Ꮇicrosoft Support",
            "ꮲayꮲal",
            "ꮲayꮲal Security",
        )) {
            val result = evaluate(name, "x@evil.example")
            assertNotNull(name, result.warning)
            assertEquals(name, "", result.name)
            assertFalse(name, result.show_identity_marks)
        }
    }

    @Test
    fun `lookalike home brand names warn`() {
        for (name in listOf("Ꭺster Mail", "Ꭺster Mail Security", "ꭺster Mail")) {
            val result = evaluate(name, "x@evil.example")
            assertEquals(name, SenderWarningKind.home_brand_name, result.warning?.kind)
            assertFalse(name, result.show_identity_marks)
        }
    }

    @Test
    fun `swapping one letter of a brand name for a lookalike still warns`() {
        val by_letter = letters_by_target(Regex("^[a-z]$"))
        val misses = ArrayList<String>()
        val brands = data.getJSONArray("brands")
        for (index in 0 until brands.length()) {
            val brand = brands.getJSONObject(index)
            if (brand.optBoolean("ambiguous", false)) continue
            for (name in strings(brand.getJSONArray("names"))) {
                val letters = points(name)
                for (position in letters.indices) {
                    for (lookalike in by_letter[lower(letters[position])].orEmpty()) {
                        val forged = letters.toMutableList()
                        forged[position] = lookalike
                        val text = forged.joinToString("")
                        if (evaluate(text, "x@evil.example").warning == null) misses.add("$name -> $text")
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), misses)
    }

    @Test
    fun `reported lookalike domains stay in ascii form`() {
        assertEquals("xn--aypal-ytb.com", sender_domain_for_display("xn--aypal-ytb.com"))
        assertEquals("xn--astermai-mhd.org", sender_domain_for_display("xn--astermai-mhd.org"))
    }

    @Test
    fun `reported lookalike domains warn`() {
        for ((email, claimed) in listOf(
            "support@xn--aypal-ytb.com" to "paypal.com",
            "support@xn--astermai-mhd.org" to "astermail.org",
            "service@xn--aypal-ytb.com" to "paypal.com",
        )) {
            val result = evaluate("Support", email)
            assertEquals(email, result.address)
            assertEquals(email.substringAfter('@'), result.domain)
            assertEquals(SenderWarning(SenderWarningKind.lookalike_domain, claimed), result.warning)
            assertFalse(email, result.show_identity_marks)
        }
    }

    @Test
    fun `a decoded lookalike of a protected domain is never shown`() {
        val by_letter = letters_by_target(Regex("^[a-z0-9]$"))
        val leaks = ArrayList<String>()
        val domains = ArrayList(strings(data.getJSONObject("home_brand").getJSONArray("domains")))
        val brands = data.getJSONArray("brands")
        for (index in 0 until brands.length()) {
            domains.addAll(strings(brands.getJSONObject(index).getJSONArray("domains")))
        }
        for (domain in domains) {
            val label = domain.substringBefore('.')
            val rest = domain.substringAfter('.')
            val letters = points(label)
            for (position in letters.indices) {
                for (lookalike in by_letter[letters[position]].orEmpty()) {
                    val forged = letters.toMutableList()
                    forged[position] = lookalike
                    val ascii = sender_domain_to_ascii(forged.joinToString("") + "." + rest)
                    if (ascii.isEmpty() || ascii == domain) continue
                    val shown = sender_domain_for_display(ascii)
                    val result = evaluate("Support", "support@$ascii")
                    val owned = sender_registrable_domain(domain) == domain
                    if (shown != ascii || (owned && result.warning == null)) {
                        leaks.add("$domain -> $shown ${result.warning?.kind}")
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), leaks)
    }

    @Test
    fun `ordinary internationalized domains are still shown decoded`() {
        for (domain in listOf(
            "münchen.example",
            "straße.example",
            "bücher.example",
            "øresund.example",
            "例え.example",
            "пример.example",
            "παράδειγμα.example",
            "日本語.jp",
            "مثال.example",
            "דוגמה.example",
        )) {
            val ascii = sender_domain_to_ascii(domain)
            assertTrue(domain, ascii.contains("xn--"))
            assertEquals(domain, domain, sender_domain_for_display(ascii))
        }
    }

    @Test
    fun `labels in lookalike only scripts stay in ascii form`() {
        for (domain in listOf(
            "ᏢᎪᎩ.example",
            "ⲣⲁⲛ.example",
            "ꓑꓮ.example",
            "pɑypal.example",
            "ınfo.example",
        )) {
            val ascii = sender_domain_to_ascii(domain)
            assertTrue(domain, ascii.contains("xn--"))
            assertEquals(domain, ascii, sender_domain_for_display(ascii))
        }
    }

    @Test
    fun `international names are kept without a warning`() {
        for (name in listOf(
            "José García",
            "Müller",
            "Søren Kierkegaard",
            "Łukasz Nowak",
            "Nguyễn Văn An",
            "Đặng Thu Thảo",
            "Björk Guðmundsdóttir",
            "山田太郎",
            "김민준",
            "محمد علي",
            "יעל כהן",
            "Сергей Иванов",
            "Ελένη Παπαδοπούλου",
            "Հայկ Պետրոսյան",
            "ქეთევან",
            "สมชาย ใจดี",
            "राहुल शर्मा",
            "Jordan 🙂",
            "👨‍👩‍👧 Family",
        )) {
            val result = evaluate(name, "person@ordinary-business.example")
            assertNull(name, result.warning)
            assertEquals(name, name, result.name)
            assertTrue(name, result.show_identity_marks)
        }
    }

    @Test
    fun `a relay alias that puts a protected address in its name warns`() {
        for (name in listOf(
            "security@astermail.org",
            "support@aster.cx",
            "service@paypal.com",
            "service@paypal.de",
            "no-reply@accounts.google.com",
            "security@paypa1.com",
        )) {
            for (relay in listOf("x7k2@aleeas.com", "x7k2@simplelogin.com")) {
                val result = evaluate(name, relay)
                assertNotNull("$name via $relay", result.warning)
                assertEquals(name, "", result.name)
                assertFalse(name, result.show_identity_marks)
            }
        }
    }

    @Test
    fun `the reported relay case is an address in the name`() {
        assertEquals(
            SenderWarning(SenderWarningKind.address_in_name, "security@astermail.org"),
            evaluate("security@astermail.org", "x7k2@aleeas.com").warning,
        )
    }

    @Test
    fun `a relay alias that claims your own or a contact's address warns`() {
        val own_addresses = listOf("me@victimcorp.com")
        val contacts = listOf(SenderTrustContact("Robin Park", listOf("robin@partner.example")))
        for (name in listOf("robin@partner.example", "me@victimcorp.com")) {
            val result = evaluate(name, "abc123@mozmail.com", own_addresses, contacts)
            assertEquals(name, SenderWarning(SenderWarningKind.address_in_name, name), result.warning)
        }
    }

    @Test
    fun `ordinary forwarded senders from relays are still accepted`() {
        for ((name, email) in listOf(
            "friend@example.org" to "abc123@mozmail.com",
            "friend@example.org [via Relay]" to "abc123@mozmail.com",
            "Jordan Lee" to "x7k2@aleeas.com",
        )) {
            val result = evaluate(name, email)
            assertNull(name, result.warning)
            assertEquals(name, name, result.name)
        }
    }
}
