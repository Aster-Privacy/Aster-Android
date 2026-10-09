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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteDetectionTest {

    @Test
    fun recognizes_localized_attributions() {
        val lines = listOf(
            "On Tue, Oct 6, 2026 at 9:00 AM Someone <a@example.com> wrote:",
            "On Monday, someone wrote:",
            "Am 07.10.2026 um 14:03 schrieb Max Muster <max@example.de>:",
            "Le mer. 7 oct. 2026 à 14:03, Jean Dupont <jean@example.fr> a écrit :",
            "El mié, 7 oct 2026 a las 14:03, Ana <ana@example.es> escribió:",
            "Il giorno mer 7 ott 2026 alle ore 14:03 Luca <luca@example.it> ha scritto:",
            "Em qua., 7 de out. de 2026 às 14:03, Rui <rui@example.pt> escreveu:",
            "Op wo 7 okt 2026 om 14:03 schreef Jan <jan@example.nl>:",
            "Den ons 7 okt. 2026 kl 14:03 skrev Erik <erik@example.se>:",
            "W dniu 7.10.2026 o 14:03, Jan Kowalski <jan@example.pl> pisze:",
            "7 окт. 2026 г., в 14:03, Иван <ivan@example.ru> написал(а):",
            "Li <li@example.cn> 于2026年10月7日周三 14:03写道：",
            "2026년 10월 7일 (수) 오후 2:03, Kim <kim@example.kr>님이 작성:",
        )
        for (line in lines) assertTrue(line, is_attribution_text(line))
    }

    @Test
    fun rejects_sentences_that_only_look_like_attributions() {
        assertFalse(is_attribution_text("Based on what you wrote:"))
        assertFalse(is_attribution_text("On reflection, I agree."))
        assertFalse(is_attribution_text("> On Mon, Oct 5, 2026 Someone wrote:"))
        assertFalse(is_attribution_text("On " + "x".repeat(400) + " wrote:"))
    }

    @Test
    fun finds_a_wrapped_attribution() {
        val lines = listOf(
            "Works for me.",
            "",
            "On Wed, Oct 7, 2026, 3:16 AM, Dr.-Ing. Someone Long <a@example.com>",
            "wrote:",
            "> earlier text",
        )

        assertEquals(QuoteStart(2, 4), find_quote_start(lines))
    }

    @Test
    fun a_sentence_starting_with_on_does_not_wrap() {
        assertNull(find_quote_start(listOf("On reflection, I agree.", "We wrote:", "Thanks.")))
    }

    @Test
    fun finds_a_header_block_and_its_separator() {
        val lines = listOf(
            "Confirmed.",
            "",
            "________________________________",
            "From: Someone <a@example.com>",
            "Sent: Tuesday, October 6, 2026 9:00 AM",
            "To: Me",
            "Subject: Friday",
            "older",
        )

        assertEquals(QuoteStart(2, lines.size), find_quote_start(lines))
    }

    @Test
    fun a_lone_from_line_is_not_a_header_block() {
        assertNull(find_quote_start(listOf("From: the team, with thanks.", "See you soon.", "Subject to change.")))
    }

    @Test
    fun finds_a_localized_original_message_marker() {
        assertEquals(QuoteStart(1, 2), find_quote_start(listOf("Passt.", "-----Ursprüngliche Nachricht-----", "alt")))
    }

    @Test
    fun finds_a_trailing_quote_run() {
        assertEquals(QuoteStart(2, 2), find_trailing_quote(listOf("Agreed.", "", "> one", "> two", "")))
        assertEquals(
            QuoteStart(1, 2),
            find_trailing_quote(listOf("Agreed.", "Max <max@example.de> schrieb:", "> one")),
        )
        assertNull(find_trailing_quote(listOf("> one", "reply")))
    }

    @Test
    fun interleaved_replies_have_no_quote_end() {
        val lines = listOf("attr", "> a", "", "reply", "", "> b", "", "more")

        assertNull(resolve_quote_end(lines, 1))
    }

    @Test
    fun text_after_the_quote_ends_the_fold() {
        assertEquals(QuoteEnd(2, false), resolve_quote_end(listOf("attr", "> a", "> b", "", "-- ", "Sig"), 1))
        assertEquals(QuoteEnd(3, true), resolve_quote_end(listOf("attr", "> a", "> b", ""), 1))
        assertEquals(QuoteEnd(2, true), resolve_quote_end(listOf("attr", "unquoted", "more"), 1))
    }

    @Test
    fun boilerplate_does_not_count_as_reply_text() {
        assertFalse(has_reply_text_before(listOf("", "__________", "-----Original Message-----", "x"), 3))
        assertTrue(has_reply_text_before(listOf("Thanks", "x"), 1))
    }

    @Test
    fun normalizes_non_breaking_spaces() {
        assertTrue(is_attribution_text(normalize_quote_line(" On Tue, Oct 6, 2026 Someone wrote: ")))
    }
}
