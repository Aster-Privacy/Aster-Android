//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import org.astermail.android.ui.mail.EmailHtmlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutolinkTest {

    private fun links(text: String) = Autolink.find(text).map { it.text }

    private fun hrefs(text: String) = Autolink.find(text).map { it.href }

    @Test
    fun leaves_a_closing_paren_and_comma_outside_a_parenthesised_url() {
        assertEquals(
            listOf("https://www.rfc-editor.org/rfc/rfc6530"),
            links("RFC 6530 (https://www.rfc-editor.org/rfc/rfc6530), which explains"),
        )
    }

    @Test
    fun leaves_a_closing_paren_and_period_outside_a_parenthesised_url() {
        assertEquals(listOf("https://uasg.tech"), links("the group (https://uasg.tech)."))
    }

    @Test
    fun keeps_a_balanced_paren_that_is_part_of_the_url() {
        assertEquals(
            listOf("https://en.wikipedia.org/wiki/Pikachu_(Electric)"),
            links("see https://en.wikipedia.org/wiki/Pikachu_(Electric) today"),
        )
        assertEquals(
            listOf("https://en.wikipedia.org/wiki/Foo_(bar)"),
            links("(https://en.wikipedia.org/wiki/Foo_(bar))"),
        )
    }

    @Test
    fun strips_sentence_punctuation_from_the_end_of_a_url() {
        for (tail in listOf(".", "?", "!", ":", ";", ",", "\"", "'", "]")) {
            assertEquals(tail, listOf("https://astermail.org/help"), links("Go to https://astermail.org/help$tail"))
        }
    }

    @Test
    fun keeps_query_strings_fragments_and_trailing_slashes() {
        assertEquals(listOf("https://a.example/p?x=1&y=2#frag/"), links("https://a.example/p?x=1&y=2#frag/"))
        assertEquals(listOf("https://a.example/p?x=1&y=2"), links("https://a.example/p?x=1&y=2, then"))
    }

    @Test
    fun links_www_addresses_with_an_http_href() {
        assertEquals(listOf("http://www.example.com"), hrefs("visit www.example.com."))
        assertEquals(listOf("www.example.com"), links("visit www.example.com."))
    }

    @Test
    fun does_not_link_a_bare_prefix_without_a_host() {
        assertEquals(emptyList<String>(), links("www. is not a link"))
        assertEquals(emptyList<String>(), links("https:// alone"))
    }

    @Test
    fun links_email_addresses_with_mailto() {
        assertEquals(listOf("mailto:support@astermail.org"), hrefs("Write to support@astermail.org."))
        assertEquals(listOf("support@astermail.org"), links("Write to support@astermail.org, thanks"))
        assertEquals(listOf("support@astermail.org"), links("(support@astermail.org)"))
    }

    @Test
    fun does_not_turn_a_url_containing_an_at_sign_into_an_email() {
        assertEquals(listOf("https://user@host.example/path"), hrefs("https://user@host.example/path"))
    }

    @Test
    fun trims_a_run_on_word_from_an_email_top_level_domain() {
        assertEquals(listOf("support@astermail.org"), links("support@astermail.orgThanks"))
    }

    @Test
    fun does_not_link_an_address_glued_to_a_word() {
        assertEquals(emptyList<String>(), links("xhttps://astermail.org"))
    }

    @Test
    fun split_reassembles_the_original_text() {
        val text = "a (https://x.example/y), b@c.org."
        val segments = Autolink.split(text)
        assertEquals(text, segments.joinToString("") { it.text })
        assertEquals(listOf("https://x.example/y", "b@c.org"), segments.filter { it.href != null }.map { it.text })
    }

    @Test
    fun plain_text_html_wraps_only_the_url_and_escapes_the_rest() {
        val html = build_plain_text_html("RFC (https://www.rfc-editor.org/rfc/rfc6530), <b> & more")
        assertTrue(
            html,
            html.contains(
                "<a href=\"https://www.rfc-editor.org/rfc/rfc6530\">https://www.rfc-editor.org/rfc/rfc6530</a>), &lt;b&gt; &amp; more",
            ),
        )
    }

    @Test
    fun plain_text_html_escapes_ampersands_inside_the_href() {
        val html = build_plain_text_html("https://a.example/p?x=1&y=2.")
        assertTrue(html, html.contains("<a href=\"https://a.example/p?x=1&amp;y=2\">https://a.example/p?x=1&amp;y=2</a>."))
    }

    @Test
    fun plain_text_html_does_not_swallow_an_escaped_angle_bracket() {
        val html = build_plain_text_html("<https://a.example/p>")
        assertTrue(html, html.contains("&lt;<a href=\"https://a.example/p\">https://a.example/p</a>&gt;"))
    }

    @Test
    fun plain_text_html_links_email_addresses() {
        val html = build_plain_text_html("mail me@x.org.")
        assertTrue(html, html.contains("<a href=\"mailto:me@x.org\">me@x.org</a>."))
    }

    @Test
    fun sanitizer_leaves_trailing_punctuation_outside_links_in_text_nodes() {
        val out = EmailHtmlSanitizer.sanitize("<p>The standard (https://www.rfc-editor.org/rfc/rfc6530), which</p>")
        assertTrue(out, out.contains("href=\"https://www.rfc-editor.org/rfc/rfc6530\""))
        assertTrue(out, out.contains(">https://www.rfc-editor.org/rfc/rfc6530</a>), which"))
    }

    @Test
    fun sanitizer_does_not_relink_text_already_inside_an_anchor() {
        val out = EmailHtmlSanitizer.sanitize("<p><a href=\"https://x.example/\">https://x.example/</a></p>")
        assertEquals(out, 1, Regex("<a ").findAll(out).count())
    }

    @Test
    fun sanitizer_links_email_addresses_in_text_nodes() {
        val out = EmailHtmlSanitizer.sanitize("<p>Reach hello@astermail.org.</p>")
        assertTrue(out, out.contains("href=\"mailto:hello@astermail.org\""))
        assertTrue(out, out.contains(">hello@astermail.org</a>."))
        assertFalse(out, out.contains("mailto:hello@astermail.org."))
    }
}
