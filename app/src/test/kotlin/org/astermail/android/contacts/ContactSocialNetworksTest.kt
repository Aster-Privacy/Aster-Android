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

package org.astermail.android.contacts

import org.astermail.android.ui.contacts.Contact
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSocialNetworksTest {

    private fun json_map(obj: JSONObject): Map<String, Any?> = obj.keys().asSequence().associateWith { obj.get(it) }

    private fun decode(json: String): ContactSocial? = decode_social(JSONObject(json))

    @Test
    fun `decodes known custom and unknown types`() {
        assertEquals(ContactSocial("github", "ada"), decode("""{"value":"ada","type":"GitHub"}"""))
        assertEquals(ContactSocial.custom("Portfolio", "https://ada.dev"), decode("""{"value":"https://ada.dev","type":"other","label":"Portfolio"}"""))
        assertEquals(ContactSocial("Matrix", "@ada:example.org"), decode("""{"value":"@ada:example.org","type":"Matrix"}"""))
        assertTrue(decode("""{"value":"x","type":"Matrix"}""")!!.is_custom)
        assertNull(decode("""{"type":"github"}"""))
    }

    @Test
    fun `encode omits an empty label and never labels a known type`() {
        assertEquals(json_map(JSONObject("""{"value":"x","type":"other"}""")), json_map(encode_social(ContactSocial.custom("", "x"))))
        assertEquals(json_map(JSONObject("""{"value":"x","type":"other","label":"Blog"}""")), json_map(encode_social(ContactSocial.custom("Blog", "x"))))
        assertEquals(json_map(JSONObject("""{"value":"ada","type":"github"}""")), json_map(encode_social(ContactSocial("github", "ada", "ignored"))))
    }

    @Test
    fun `normalize trims caps and drops empty values`() {
        val out = normalize_socials(
            listOf(
                ContactSocial.custom("Blog", "   "),
                ContactSocial.custom("  Blog  ", "  https://ada.dev  "),
                ContactSocial.custom("L".repeat(80), "v".repeat(700)),
            ),
        )
        assertEquals(2, out.size)
        assertEquals(ContactSocial.custom("Blog", "https://ada.dev"), out[0])
        assertEquals(MAX_SOCIAL_LABEL, out[1].label.length)
        assertEquals(MAX_SOCIAL_VALUE, out[1].value.length)
        assertEquals(MAX_SOCIAL_NETWORKS, normalize_socials((1..80).map { ContactSocial.custom("L", "v$it") }).size)
    }

    @Test
    fun `untouched socials leave the stored array byte identical`() {
        val raw = """[{"value":"ada","type":"github","x_extra":true},{"value":"@a:b","type":"Matrix"}]"""
        val obj = JSONObject().put(SOCIAL_NETWORKS_KEY, JSONArray(raw))
        val before = obj.toString()
        write_contact_socials(obj, null)
        assertEquals(before, obj.toString())
        write_contact_socials(obj, decode_contact_socials(obj))
        assertEquals(before, obj.toString())
    }

    @Test
    fun `adding a custom link keeps the other entries verbatim`() {
        val raw = """[{"value":"ada","type":"github","x_extra":true},{"value":"@a:b","type":"Matrix"}]"""
        val obj = JSONObject().put(SOCIAL_NETWORKS_KEY, JSONArray(raw))
        write_contact_socials(obj, decode_contact_socials(obj) + ContactSocial.custom("Blog", "https://ada.dev"))
        val arr = obj.getJSONArray(SOCIAL_NETWORKS_KEY)
        assertEquals(3, arr.length())
        assertTrue(arr.getJSONObject(0).getBoolean("x_extra"))
        assertEquals("Matrix", arr.getJSONObject(1).getString("type"))
        assertEquals("Blog", arr.getJSONObject(2).getString("label"))
        write_contact_socials(obj, emptyList())
        assertFalse(obj.has(SOCIAL_NETWORKS_KEY))
    }

    @Test
    fun `only http and https urls are opened`() {
        assertEquals("https://ada.dev/p?q=1", safe_http_url("  https://ada.dev/p?q=1 "))
        assertEquals("http://ada.dev", safe_http_url("http://ada.dev"))
        assertEquals("HTTPS://ADA.DEV", safe_http_url("HTTPS://ADA.DEV"))
        val rejected = listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<b>x</b>",
            "intent://scan#Intent;scheme=zxing;end",
            "file:///sdcard/a.txt",
            "content://contacts/1",
            "ftp://ada.dev",
            "https://user:pw@ada.dev",
            "https://ada.dev/a b",
            "https://ada.dev/\nx",
            "https:///nohost",
            "//ada.dev",
            "ada.dev",
            "",
        )
        for (value in rejected) assertNull(value, safe_http_url(value))
    }

    @Test
    fun `bare handles only become links for known networks`() {
        assertEquals("https://x.com/ada", safe_social_url(ContactSocial("twitter", "@ada")))
        assertEquals("https://github.com/ada", safe_social_url(ContactSocial("github", "ada")))
        assertEquals("https://linkedin.com/in/ada-l", safe_social_url(ContactSocial("linkedin", "ada-l")))
        assertEquals("https://mastodon.social/@ada", safe_social_url(ContactSocial("mastodon", "@ada@mastodon.social")))
        assertEquals("https://bsky.app/profile/ada.bsky.social", safe_social_url(ContactSocial("bluesky", "ada.bsky.social")))
        assertNull(safe_social_url(ContactSocial("bluesky", "ada")))
        assertNull(safe_social_url(ContactSocial.custom("Blog", "ada")))
        assertNull(safe_social_url(ContactSocial.custom("Blog", "javascript:alert(1)")))
        assertNull(safe_social_url(ContactSocial("github", "javascript:alert(1)")))
        assertNull(safe_social_url(ContactSocial("github", "a/../b")))
        assertEquals("https://ada.dev", safe_social_url(ContactSocial.custom("Blog", "https://ada.dev")))
    }

    @Test
    fun `vcard type token keeps only safe characters`() {
        assertEquals("MyBlog-1_x", social_vcard_type_token("My Blog-1_x;:,\"é"))
    }

    @Test
    fun `vcard export and import round trip socials`() {
        val socials = listOf(
            ContactSocial("github", "ada"),
            ContactSocial("mastodon", "@ada@mastodon.social"),
            ContactSocial.custom("My Blog", "https://ada.dev"),
            ContactSocial.custom("Matrix", "@ada:example.org"),
        )
        val card = contact_to_vcard(Contact(id = "1", name = "Social Test", email = "s@example.com", social_networks = socials))
        assertTrue(card.contains("X-SOCIALPROFILE;TYPE=github:ada"))
        assertTrue(card.contains(".X-SOCIALPROFILE;TYPE=MyBlog:https://ada.dev"))
        assertTrue(card.contains(".X-ABLabel:My Blog"))
        assertEquals(socials, parse_vcards(card).single().social_networks)
    }

    @Test
    fun `vcard import reads typed urls and keeps plain urls as the website`() {
        val card = listOf(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "FN:Url Test",
            "URL:https://home.example",
            "URL;TYPE=WORK:https://work.example",
            "URL;TYPE=Portfolio:https://folio.example",
            "item1.URL:https://shop.example",
            "item1.X-ABLabel:Shop",
            "X-SOCIALPROFILE;TYPE=Matrix:@ada:example.org",
            "X-SOCIALPROFILE;X-SERVICE-TYPE=GitHub:ada",
            "X-SOCIALPROFILE;TYPE=twitter:ada",
            "END:VCARD",
        ).joinToString("\r\n")
        val parsed = parse_vcards(card).single()
        assertEquals("https://home.example", parsed.website)
        assertEquals("ada", parsed.twitter)
        assertEquals(
            listOf(
                ContactSocial.custom("Portfolio", "https://folio.example"),
                ContactSocial.custom("Shop", "https://shop.example"),
                ContactSocial.custom("Matrix", "@ada:example.org"),
                ContactSocial("github", "ada"),
            ),
            parsed.social_networks,
        )
    }

    @Test
    fun `contacts without socials load unchanged`() {
        val card = contact_to_vcard(Contact(id = "1", name = "Plain", email = "p@example.com", website = "https://p.example"))
        assertFalse(card.contains("X-SOCIALPROFILE"))
        val parsed = parse_vcards(card).single()
        assertNull(parsed.social_networks)
        assertEquals("https://p.example", parsed.website)
    }

    @Test
    fun `merge unions socials without duplicates`() {
        val a = Contact(id = "1", name = "A", email = "a@example.com", social_networks = listOf(ContactSocial("github", "ada")))
        val b = Contact(id = "2", name = "A", email = "a@example.com", social_networks = listOf(ContactSocial("github", "ADA"), ContactSocial.custom("Blog", "https://ada.dev")))
        assertEquals(listOf(ContactSocial("github", "ada"), ContactSocial.custom("Blog", "https://ada.dev")), merge_contacts(listOf(a, b)).social_networks)
    }

    @Test
    fun `search matches value label and type name`() {
        val fold: (String) -> String = { it.lowercase() }
        val blog = ContactSocial.custom("Blog", "https://ada.dev")
        assertTrue(social_matches(blog, "Blog", "ada.dev", fold))
        assertTrue(social_matches(blog, "Blog", "blo", fold))
        assertFalse(social_matches(blog, "Blog", "zzz", fold))
    }
}
