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

import org.astermail.android.mail.build_plain_text_html
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailBodyNativeTest {

    private fun prepare(
        body: String,
        is_newsletter: Boolean = false,
        simple_dark: Boolean = false,
    ): String = prepare_email_body(
        body = body,
        forwarded_label = "Forwarded message",
        image_failed_label = "Image unavailable",
        is_newsletter = is_newsletter,
        simple_dark = simple_dark,
    )

    @Test
    fun bare_urls_become_links_without_script() {
        val prepared = prepare("<p>Read https://example.org/docs today</p>")

        assertTrue(prepared.contains("<a href=\"https://example.org/docs\">https://example.org/docs</a>"))
    }

    @Test
    fun an_address_inside_an_anchor_is_left_alone() {
        val prepared = prepare("<p><a href=\"mailto:a@b.com\">a@b.com</a></p>")

        assertEquals(1, prepared.split("<a ").size - 1)
    }

    @Test
    fun quoted_replies_collapse_into_a_native_disclosure() {
        val body = "<div>Thanks</div><div>On Monday, someone wrote:</div>" +
            "<blockquote>the original message body</blockquote>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("<details class=\"aster-quoted-wrapper\">"))
        assertTrue(prepared.contains("<summary class=\"aster-quote-toggle\">"))
        assertTrue(prepared.contains("aster-quoted-content"))
        assertFalse(prepared.contains("<button"))
        assertFalse(prepared.contains("onclick"))
    }

    @Test
    fun an_image_gets_a_tap_target_the_app_can_route() {
        val prepared = prepare("<p><img src=\"https://example.org/a.png\"></p>")

        assertTrue(prepared.contains("class=\"aster-image-zoom\""))
        assertTrue(prepared.contains("href=\"asterimg:https%3A%2F%2Fexample.org%2Fa.png\""))
    }

    @Test
    fun a_remote_image_carries_its_failure_label_as_data() {
        val prepared = prepare("<p><img src=\"https://example.org/a.png\"></p>")

        assertTrue(prepared.contains("$FAILED_IMAGE_LABEL_ATTRIBUTE=\"Image unavailable\""))
    }

    @Test
    fun a_fixed_height_cell_can_still_grow() {
        val prepared = prepare("<table><tr><td style=\"height:20px\">a long line of text</td></tr></table>")

        assertTrue(prepared.contains("height:auto"))
        assertTrue(prepared.contains("min-height:20px"))
    }

    @Test
    fun dark_rendering_lifts_unreadable_inline_text() {
        val prepared = prepare("<p style=\"color:#111111\">hello</p>", simple_dark = true)

        assertTrue(prepared.contains("#e8e8e8"))
    }

    @Test
    fun simple_dark_lightens_dark_wrapper_whose_text_is_in_children() {
        val prepared = prepare(
            "<div style=\"font-family:sans-serif;color:#222\"><p style=\"margin:0\">hello</p></div>",
            simple_dark = true,
        )

        assertTrue(prepared.contains("color:#e8e8e8"))
        assertTrue(!prepared.contains("color:#222"))
    }

    @Test
    fun simple_dark_keeps_dark_wrapper_text_around_a_light_box() {
        val prepared = prepare(
            "<div style=\"color:#333\"><div style=\"background:#f4f4f4\">hello</div></div>",
            simple_dark = true,
        )

        assertTrue(prepared.contains("color:#333"))
        assertFalse(prepared.contains("#e8e8e8"))
    }

    @Test
    fun malformed_markup_falls_back_to_the_original_body() {
        val body = "plain text with no markup at all"

        assertEquals(body, prepare(body).trim())
    }

    @Test
    fun translatable_text_survives_a_round_trip() {
        val body = "<p>Bonjour tout le monde</p><p>Deuxieme phrase ici</p>"
        val segments = extract_translatable_segments(body)

        assertEquals(listOf("Bonjour tout le monde", "Deuxieme phrase ici"), segments)

        val applied = apply_translated_segments(body, listOf("Hello everyone", "Second sentence here"))

        assertTrue(applied.contains("Hello everyone"))
        assertTrue(applied.contains("Second sentence here"))
        assertFalse(applied.contains("Bonjour"))
    }

    @Test
    fun script_and_style_text_is_never_sent_for_translation() {
        val body = "<style>.a{color:red}</style><script>alert('hi')</script><p>Bonjour le monde</p>"

        assertEquals(listOf("Bonjour le monde"), extract_translatable_segments(body))
    }

    @Test
    fun a_count_mismatch_leaves_the_body_untouched() {
        val body = "<p>Bonjour tout le monde</p><p>Deuxieme phrase ici</p>"

        assertEquals(body, apply_translated_segments(body, listOf("only one")))
    }

    @Test
    fun a_declared_background_is_read_without_script() {
        assertEquals("#ffffff", detect_body_background("<table bgcolor=\"#ffffff\"><tr><td>a</td></tr></table>"))
        assertTrue(background_reads_light("#ffffff"))
        assertFalse(background_reads_light("#101010"))
        assertFalse(background_reads_light(null))
    }

    @Test
    fun an_unsafe_background_value_is_refused() {
        assertNull(detect_body_background("<div style=\"background-color:url(javascript:alert(1))\">a</div>"))
    }
    @Test
    fun a_fixed_width_layout_reports_the_width_the_viewport_must_fit() {
        val body = "<div style=\"width:900px\"><p style=\"width:900px\">Your statement is ready.</p></div>"

        assertEquals(900, declared_content_width(body))
    }

    @Test
    fun an_ordinary_email_asks_for_no_fitting() {
        assertNull(declared_content_width("<p>Are we still on for Thursday?</p>"))
    }

    @Test
    fun a_wide_image_alone_does_not_shrink_the_page() {
        assertNull(declared_content_width("<p>Photo</p><img src=\"https://example.org/a.png\" width=\"1200\">"))
    }

    @Test
    fun an_absurd_width_is_capped() {
        assertEquals(MAX_FIT_CONTENT_WIDTH, declared_content_width("<div style=\"width:9000px\">wide</div>"))
    }

    @Test
    fun a_max_width_declaration_is_not_a_fixed_width() {
        assertNull(declared_content_width("<div style=\"max-width:900px\">fluid</div>"))
    }

    @Test
    fun a_newsletter_table_width_attribute_is_read() {
        assertEquals(640, declared_content_width("<table width=\"640\"><tr><td>Hello</td></tr></table>"))
    }

    @Test
    fun a_fixed_width_capped_by_a_fluid_max_width_is_already_responsive() {
        val body = "<div style=\"width:100%\"><div style=\"width:640px;max-width:100%;margin:0 auto\"><p>Welcome</p></div></div>"

        assertNull(declared_content_width(body))
    }


    @Test
    fun a_forward_that_is_only_a_quote_stays_expanded() {
        val body = "<br><div class=\"aster_quote gmail_quote\">" +
            "<div class=\"aster_quote_attr gmail_attr\"><div><b>From:</b> a sender</div></div>" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertFalse(prepared.contains("aster-quoted-wrapper"))
        assertTrue(prepared.contains("aster-quoted-content"))
        assertTrue(prepared.contains("the original message body"))
    }

    @Test
    fun blank_lines_before_a_collapsed_quote_are_dropped() {
        val body = "<div>Reply number 8.</div><br><br><br>" +
            "<div class=\"aster_quote gmail_quote\">" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("aster-quoted-wrapper"))
        assertFalse(prepared.contains("<br>"))
    }

    @Test
    fun blank_lines_inside_the_last_block_before_a_quote_are_dropped() {
        val body = "<div>Reply number 8.<br><br></div>" +
            "<div class=\"aster_quote gmail_quote\">" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("aster-quoted-wrapper"))
        assertFalse(prepared.contains("<br>"))
    }

    @Test
    fun a_forward_with_its_own_text_still_collapses_the_quote() {
        val body = "<div>Passing this along.</div><div class=\"aster_quote gmail_quote\">" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("aster-quoted-wrapper"))
    }

    @Test
    fun a_message_that_is_only_a_forward_wrapper_stays_visible() {
        val prepared = prepare("<div class=\"protonmail_quote\"><p>Hi there, a clan is a group.</p></div>")

        assertFalse(prepared.contains("<details"))
        assertTrue(prepared.contains("<div class=\"aster-quoted-content aster-quoted-solo\"><div class=\"protonmail_quote\">"))
    }

    @Test
    fun a_quote_class_on_another_tag_stays_visible() {
        val prepared = prepare("<table class=\"gmail_quote\"><tbody><tr><td>Hi there</td></tr></tbody></table>")

        assertTrue(prepared.contains("aster-quoted-solo\"><table class=\"gmail_quote\""))
    }

    @Test
    fun a_second_quote_block_outside_the_disclosure_stays_visible() {
        val prepared = prepare(
            "<p>Reply text</p><div class=\"gmail_quote\">First</div>" +
                "<p>More text</p><div class=\"gmail_quote\">Second</div>",
        )

        assertTrue(prepared.contains("aster-quoted-wrapper"))
        assertTrue(prepared.contains("aster-quoted-solo\"><div class=\"gmail_quote\">Second"))
    }

    @Test
    fun content_hidden_by_its_own_inline_style_is_shown() {
        val prepared = prepare("<div style=\"display:none;color:red\"><p>Hi there</p></div>")

        assertFalse(prepared.contains("display:none"))
        assertTrue(prepared.contains("color:red"))
    }

    @Test
    fun a_hidden_preheader_stays_hidden_when_other_text_is_visible() {
        val prepared = prepare("<div style=\"display:none\">Preview</div><p>Visible body</p>")

        assertTrue(prepared.contains("display:none"))
    }

    @Test
    fun a_hidden_preheader_stays_hidden_when_the_text_resets_a_zero_font_size() {
        val preheader = "display:none;font-size:1px;color:#ffffff;line-height:1px;" +
            "max-height:0px;max-width:0px;opacity:0;overflow:hidden;"
        val prepared = prepare(
            "<div style=\"$preheader\">Your parcel is on its way</div>" +
                "<div style=\"font-size:0px;padding:20px 0\">" +
                "<div style=\"display:inline-block;width:100%\">" +
                "<div style=\"font-family:Arial;font-size:14px;line-height:1.5\">Order 1234 has shipped</div>" +
                "</div></div>",
        )

        assertTrue(prepared.contains(preheader))
        assertTrue(prepared.contains("font-size:0px;padding:20px 0"))
    }

    @Test
    fun a_hidden_preheader_stays_hidden_above_images_in_zero_font_size_columns() {
        val prepared = prepare(
            "<div style=\"display:none;opacity:0\">Preview</div>" +
                "<div style=\"font-size:0px\"><img src=\"https://a.test/banner.png\" width=\"600\"></div>",
        )

        assertTrue(prepared.contains("display:none;opacity:0"))
    }

    @Test
    fun text_left_at_a_zero_font_size_is_shown() {
        val prepared = prepare("<div style=\"font-size:0px;color:red\"><span>Hi there</span></div>")

        assertFalse(prepared.contains("font-size:0px"))
        assertTrue(prepared.contains("color:red"))
    }

    @Test
    fun partial_opacity_is_not_treated_as_hidden() {
        val prepared = prepare("<div style=\"opacity:0.5\"><p>Hi there</p></div>")

        assertTrue(prepared.contains("opacity:0.5"))
    }

    @Test
    fun content_hidden_by_a_style_sheet_rule_is_shown() {
        val prepared = prepare(
            "<style>.wrap{display:none}</style><div class=\"wrap\"><p>Hi there</p></div>",
        )

        assertTrue(prepared.contains("class=\"wrap\" style=\"display:revert !important"))
    }

    @Test
    fun a_style_sheet_hidden_block_stays_hidden_when_other_text_is_visible() {
        val prepared = prepare(
            "<style>.mobile{display:none}</style><div class=\"mobile\">Small</div><p>Visible body</p>",
        )

        assertFalse(prepared.contains("display:revert"))
    }

    @Test
    fun rules_inside_media_queries_are_ignored() {
        val prepared = prepare(
            "<style>@media (max-width:1px){.wrap{display:none}}</style><div class=\"wrap\">Hi there</div>",
        )

        assertFalse(prepared.contains("display:revert"))
    }

    @Test
    fun an_html_part_with_nothing_to_show_yields_to_the_text_part() {
        assertNull(renderable_html_part("<html><body><div><br></div></body></html>", "Hi there"))
        assertNull(renderable_html_part("<img src=\"https://a.test/p.gif\" width=\"1\" height=\"1\">", "Hi there"))
        assertNull(renderable_html_part("", "Hi there"))
    }

    @Test
    fun an_html_part_with_content_is_kept() {
        assertEquals("<p>Hello</p>", renderable_html_part("<p>Hello</p>", "Hi there"))
        assertEquals("<div></div>", renderable_html_part("<div></div>", ""))
        assertEquals(
            "<img src=\"https://a.test/b.png\" width=\"600\">",
            renderable_html_part("<img src=\"https://a.test/b.png\" width=\"600\">", "Hi there"),
        )
    }

    private fun prepare_plain(text: String): String = prepare(build_plain_text_html(text))

    private fun visible_text(prepared: String): String {
        val doc = Jsoup.parseBodyFragment(prepared)
        doc.select("details.aster-quoted-wrapper").remove()
        return doc.body().text()
    }

    private fun hidden_text(prepared: String): String =
        Jsoup.parseBodyFragment(prepared).select("details.aster-quoted-wrapper .aster-quoted-content")
            .joinToString("\n") { it.text() }

    private fun folds(prepared: String): Boolean = prepared.contains("aster-quoted-wrapper")

    private val localized_attributions = listOf(
        "de" to "Am 07.10.2026 um 14:03 schrieb Max Muster <max@example.de>:",
        "fr" to "Le mer. 7 oct. 2026 à 14:03, Jean Dupont <jean@example.fr> a écrit :",
        "es" to "El mié, 7 oct 2026 a las 14:03, Ana <ana@example.es> escribió:",
        "it" to "Il giorno mer 7 ott 2026 alle ore 14:03 Luca <luca@example.it> ha scritto:",
        "pt" to "Em qua., 7 de out. de 2026 às 14:03, Rui <rui@example.pt> escreveu:",
        "nl" to "Op wo 7 okt 2026 om 14:03 schreef Jan <jan@example.nl>:",
        "sv" to "Den ons 7 okt. 2026 kl 14:03 skrev Erik <erik@example.se>:",
        "pl" to "W dniu 7.10.2026 o 14:03, Jan Kowalski <jan@example.pl> pisze:",
        "ru" to "7 окт. 2026 г., в 14:03, Иван <ivan@example.ru> написал(а):",
        "zh" to "Li <li@example.cn> 于2026年10月7日周三 14:03写道：",
        "ko" to "2026년 10월 7일 (수) 오후 2:03, Kim <kim@example.kr>님이 작성:",
    )

    @Test
    fun a_localized_plain_attribution_folds_the_quote() {
        for ((lang, line) in localized_attributions) {
            val prepared = prepare_plain(listOf("Reply body here.", "", line, "> earlier text", "> more").joinToString("\n"))

            assertTrue(lang, visible_text(prepared).contains("Reply body here."))
            assertFalse(lang, visible_text(prepared).contains("earlier text"))
            assertTrue(lang, hidden_text(prepared).contains("earlier text"))
        }
    }

    @Test
    fun a_localized_html_attribution_folds_the_blockquote() {
        val prepared = prepare(
            "<div>Danke!</div><div class=\"x\">Am 07.10.2026 um 14:03 schrieb Max &lt;max@example.de&gt;:<br></div>" +
                "<blockquote>alt</blockquote>",
        )

        assertTrue(visible_text(prepared).contains("Danke!"))
        assertFalse(visible_text(prepared).contains("alt"))
    }

    @Test
    fun the_reported_plain_reply_folds_from_the_attribution_down() {
        val body = listOf(
            "Of course and done :)",
            "",
            "Cheers,",
            ".mario",
            "",
            "On 10/8/26 04:00, Aster Team wrote:",
            "> Received! Looking forward to talking then.",
            ">",
            "> Would you also be able to invite my co-founder to the meeting as well?",
            "> This is his email: someone@example.com",
            "> <mailto:someone@example.com>",
            ">",
            "> Thanks,",
            "> Athanasios",
            ">",
            "> On Wed, Oct 7, 2026, 3:16 AM, Dr.-Ing. Mario Heiderich <mario@example.de>",
            "> wrote:",
            ">",
            ">     Hi Athanasios,",
            "",
        ).joinToString("\r\n")
        val prepared = prepare_plain(body)
        val visible = visible_text(prepared)
        val hidden = hidden_text(prepared)

        assertTrue(visible.contains("Of course and done :)"))
        assertTrue(visible.contains("Cheers,"))
        assertTrue(visible.contains(".mario"))
        assertFalse(visible.contains("Aster Team wrote:"))
        assertFalse(visible.contains("Received!"))
        assertFalse(visible.contains("Hi Athanasios"))
        assertTrue(hidden.contains("On 10/8/26 04:00, Aster Team wrote:"))
        assertTrue(hidden.contains("Received! Looking forward"))
        assertTrue(hidden.contains("Hi Athanasios"))
    }

    @Test
    fun a_quote_sharing_a_paragraph_with_the_reply_folds() {
        val prepared = prepare_plain(
            "Sounds good.\nThanks\nOn Tue, Oct 6, 2026 at 2:59 AM Someone <a@example.com> wrote:\n" +
                "> earlier text\n> more earlier text",
        )

        assertTrue(visible_text(prepared).contains("Sounds good."))
        assertTrue(visible_text(prepared).contains("Thanks"))
        assertFalse(visible_text(prepared).contains("earlier text"))
        assertFalse(visible_text(prepared).contains("wrote:"))
    }

    @Test
    fun a_wrapped_attribution_folds() {
        val prepared = prepare_plain(
            "Works for me.\n\nOn Wed, Oct 7, 2026, 3:16 AM, Dr.-Ing. Someone Long <a@example.com>\nwrote:\n> earlier text",
        )

        assertTrue(visible_text(prepared).contains("Works for me."))
        assertFalse(visible_text(prepared).contains("earlier text"))
        assertFalse(visible_text(prepared).contains("wrote:"))
    }

    @Test
    fun a_message_with_no_reply_text_stays_expanded() {
        val prepared = prepare_plain("On Mon, Oct 5, 2026 Someone wrote:\n> only quote")

        assertFalse(folds(prepared))
        assertTrue(prepared.contains("only quote"))
    }

    @Test
    fun an_html_header_block_after_a_rule_folds() {
        val prepared = prepare(
            "<div>Thanks, see attached.</div><div id=\"appendonsend\"></div>" +
                "<hr style=\"display:inline-block;width:98%\">" +
                "<div id=\"divRplyFwdMsg\" dir=\"ltr\"><font face=\"Calibri\"><b>From:</b> Someone &lt;a@example.com&gt;<br>" +
                "<b>Sent:</b> Wednesday, October 7, 2026 2:03 PM<br>" +
                "<b>To:</b> Me &lt;b@example.com&gt;<br>" +
                "<b>Subject:</b> Re: plans</font><div>&nbsp;</div></div>" +
                "<div>earlier body text</div>",
        )
        val doc = Jsoup.parseBodyFragment(prepared)

        assertTrue(visible_text(prepared).contains("Thanks, see attached."))
        assertFalse(visible_text(prepared).contains("earlier body text"))
        assertFalse(visible_text(prepared).contains("Subject:"))
        assertEquals(doc.select("hr").size, doc.select("details.aster-quoted-wrapper hr").size)
    }

    @Test
    fun a_word_style_header_block_folds() {
        val prepared = prepare(
            "<div class=\"WordSection1\"><p class=\"MsoNormal\">Approved.<o:p></o:p></p>" +
                "<div style=\"border:none;border-top:solid #E1E1E1 1.0pt;padding:3.0pt 0in 0in 0in\">" +
                "<p class=\"MsoNormal\"><b>From:</b> Someone &lt;a@example.com&gt;<br><b>Sent:</b> Tuesday, " +
                "October 6, 2026 9:00 AM<br><b>To:</b> Me<br><b>Subject:</b> Budget<o:p></o:p></p></div>" +
                "<p class=\"MsoNormal\">older request<o:p></o:p></p></div>",
        )

        assertTrue(visible_text(prepared).contains("Approved."))
        assertFalse(visible_text(prepared).contains("older request"))
    }

    @Test
    fun a_plain_header_block_under_a_separator_folds() {
        val prepared = prepare_plain(
            listOf(
                "Confirmed for Friday.",
                "",
                "________________________________",
                "From: Someone <a@example.com>",
                "Sent: Tuesday, October 6, 2026 9:00 AM",
                "To: Me <b@example.com>",
                "Subject: Friday",
                "",
                "older request",
            ).joinToString("\n"),
        )

        assertTrue(visible_text(prepared).contains("Confirmed for Friday."))
        assertFalse(visible_text(prepared).contains("older request"))
        assertFalse(visible_text(prepared).contains("____"))
    }

    @Test
    fun a_german_header_block_folds() {
        val prepared = prepare_plain(
            "Passt.\n\nVon: Max <max@example.de>\nGesendet: Dienstag, 6. Oktober 2026 09:00\nAn: Ich\nBetreff: Termin\n\nalte Nachricht",
        )

        assertTrue(visible_text(prepared).contains("Passt."))
        assertFalse(visible_text(prepared).contains("alte Nachricht"))
    }

    @Test
    fun a_localized_original_message_marker_folds() {
        val prepared = prepare_plain("Passt.\n\n-----Ursprüngliche Nachricht-----\nalte Nachricht")

        assertTrue(visible_text(prepared).contains("Passt."))
        assertFalse(visible_text(prepared).contains("alte Nachricht"))
    }

    @Test
    fun a_lone_from_line_does_not_fold() {
        val prepared = prepare_plain("From: the team, with thanks.\nSee you soon.\nSubject to change.")

        assertFalse(folds(prepared))
    }

    @Test
    fun interleaved_inline_replies_stay_visible() {
        val prepared = prepare_plain(
            listOf(
                "Answers inline.",
                "",
                "On Tue, Oct 6, 2026 at 9:00 AM Someone <a@example.com> wrote:",
                "> Can you make Friday?",
                "",
                "Yes, Friday works.",
                "",
                "> And bring the slides?",
                "",
                "Will do.",
            ).joinToString("\n"),
        )

        assertFalse(folds(prepared))
        assertTrue(visible_text(prepared).contains("Yes, Friday works."))
        assertTrue(visible_text(prepared).contains("Will do."))
        assertTrue(visible_text(prepared).contains("And bring the slides?"))
    }

    @Test
    fun a_bottom_posted_reply_stays_visible_below_the_toggle() {
        val prepared = prepare_plain(
            "On Tue, Oct 6, 2026 at 9:00 AM Someone <a@example.com> wrote:\n> Can you make Friday?\n> Let me know.\n\n" +
                "Yes, Friday works.",
        )

        assertTrue(visible_text(prepared).contains("Yes, Friday works."))
        assertFalse(visible_text(prepared).contains("Can you make Friday?"))
        assertTrue(prepared.indexOf("aster-quoted-wrapper") < prepared.indexOf("Yes, Friday works."))
    }

    @Test
    fun a_signature_after_the_quote_stays_visible() {
        val prepared = prepare_plain(
            "Sounds good.\n\nOn Tue, Oct 6, 2026 at 9:00 AM Someone <a@example.com> wrote:\n> earlier text\n\n-- \n" +
                "Jordan, Example Inc.",
        )

        assertTrue(visible_text(prepared).contains("Sounds good."))
        assertTrue(visible_text(prepared).contains("Jordan, Example Inc."))
        assertFalse(visible_text(prepared).contains("earlier text"))
    }

    @Test
    fun a_trailing_quote_run_without_attribution_folds() {
        val prepared = prepare_plain("Agreed.\n\n> earlier one\n> earlier two")

        assertTrue(visible_text(prepared).contains("Agreed."))
        assertFalse(visible_text(prepared).contains("earlier one"))
    }

    @Test
    fun a_nested_attribution_inside_a_wrapper_folds() {
        val prepared = prepare(
            "<div dir=\"ltr\"><div>Top reply.</div><div><br></div><div class=\"q\"><div>On Tue, Oct 6, 2026 at 9:00 AM " +
                "Someone &lt;a@example.com&gt; wrote:<br></div><blockquote>older</blockquote></div></div>",
        )

        assertTrue(visible_text(prepared).contains("Top reply."))
        assertFalse(visible_text(prepared).contains("older"))
    }

    @Test
    fun a_sentence_ending_in_wrote_does_not_fold() {
        val prepared = prepare_plain("Based on what you wrote:\nthe plan is fine.\nOne more thing.")

        assertFalse(folds(prepared))
    }

    @Test
    fun a_sentence_that_starts_with_on_does_not_fold() {
        val prepared = prepare_plain("On reflection, I agree.\nWe should ship it.\nThanks.")

        assertFalse(folds(prepared))
    }

    @Test
    fun folding_twice_keeps_one_toggle() {
        val text = "Reply.\n\nOn Tue, Oct 6, 2026 at 9:00 AM Someone <a@example.com> wrote:\n> earlier"
        val twice = prepare(prepare_plain(text))

        assertEquals(1, twice.split("aster-quote-toggle").size - 1)
        assertTrue(visible_text(twice).contains("Reply."))
    }
}
