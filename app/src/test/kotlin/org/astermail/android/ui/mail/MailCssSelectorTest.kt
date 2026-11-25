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
import org.junit.Assert.assertTrue
import org.junit.Test

class MailCssSelectorTest {

    private fun prepare(body: String): String = prepare_email_body(
        body = body,
        forwarded_label = "Forwarded message",
        image_failed_label = "Image unavailable",
        is_newsletter = false,
        simple_dark = false,
    )

    @Test
    fun plain_selectors_are_accepted() {
        val accepted = listOf(
            "p",
            "*",
            ".wrap",
            "#main",
            "div.wrap",
            "td.a.b#c",
            "table td > p",
            "h1 + p",
            "[hidden]",
            "a[href]",
            "img[src^=\"https://\"]",
            "div[class*='mobile']",
            "td[ data-x = y ]",
            "span[lang$=en]",
            "  .padded  ",
        )
        for (selector in accepted) {
            assertTrue(selector, is_simple_mail_selector(selector))
        }
    }

    @Test
    fun selectors_that_need_evaluation_are_rejected() {
        val rejected = listOf(
            "",
            "p:matches(a*a*a*b)",
            "p:matchesOwn(x)",
            "div:has(p)",
            "style:containsData(x)",
            "p:contains(x)",
            "li:nth-child(2n+1)",
            "a::before",
            "h1 ~ p",
            "[class~=x]",
            "[class|=x]",
            "[a=\"x\\\"\"]",
            "[a=\"[x]\"]",
            "@media",
            "p,",
            "> p",
            "p >",
            "p > > a",
            "p + > a",
            "a b c d e",
            "p*",
            "pa*",
            ".",
            "#",
            "[]",
            "[a=]",
            "[a=\"x\"",
            "x".repeat(MAIL_SELECTOR_MAX_LENGTH + 1),
        )
        for (selector in rejected) {
            assertFalse(selector, is_simple_mail_selector(selector))
        }
    }

    @Test
    fun selector_groups_are_split_trimmed_and_deduplicated() {
        val selectors = mail_selectors(sequenceOf(" .a , p:hover, .b", ".a,div > p"))
        assertEquals(listOf(".a", ".b", "div > p"), selectors)
    }

    @Test
    fun selector_count_is_capped() {
        val groups = (0 until MAIL_SELECTOR_MAX_COUNT * 3).map { ".c$it" }.asSequence()
        assertEquals(MAIL_SELECTOR_MAX_COUNT, mail_selectors(groups).size)
    }

    @Test
    fun block_comments_are_removed() {
        assertEquals("a{}b", strip_css_block_comments("a{}/* x */b"))
        assertEquals("ab", strip_css_block_comments("a/* one *//* two */b"))
        assertEquals("a/**b", strip_css_block_comments("a/*x*//**b"))
        assertEquals("a*/b", strip_css_block_comments("a*/b"))
        assertEquals("ab/* open", strip_css_block_comments("a/**/b/* open"))
        assertEquals("plain", strip_css_block_comments("plain"))
    }

    @Test(timeout = 2_000)
    fun an_unterminated_comment_run_finishes_quickly() {
        val css = "/*".repeat(100_000)
        assertEquals(css, strip_css_block_comments(css))
    }

    @Test(timeout = 2_000)
    fun a_regex_selector_in_a_hiding_rule_is_not_evaluated() {
        val prepared = prepare(
            "<style>p:matches(a*a*a*a*a*a*b){display:none}</style><p>" + "a".repeat(100) + "</p>",
        )
        assertTrue(prepared.contains("a".repeat(100)))
        assertFalse(prepared.contains("display:revert"))
    }

    @Test(timeout = 2_000)
    fun a_regex_selector_in_a_background_rule_is_not_evaluated() {
        val out = lighten_dark_email_text(
            "<style>p:matches(a*a*a*a*a*a*b){background:url(x.png)}</style><p>" + "a".repeat(100) + "</p>",
        )
        assertFalse(out.contains(BACKGROUND_IMAGE_ATTRIBUTE))
    }

    @Test
    fun a_plain_background_rule_still_marks_its_elements() {
        val out = lighten_dark_email_text(
            "<style>td.hero{background-image:url(hero.png)}</style>" +
                "<table><tr><td class=\"hero\" style=\"color:#111111\">Hero</td></tr></table>",
        )
        assertTrue(out.contains(BACKGROUND_IMAGE_ATTRIBUTE))
        assertTrue(out.contains("#111111"))
    }

    @Test(timeout = 2_000)
    fun a_long_style_sheet_without_braces_finishes_quickly() {
        val prepared = prepare("<style>" + "a".repeat(200_000) + "</style><p>Hi there</p>")
        assertTrue(prepared.contains("Hi there"))
        lighten_dark_email_text("<style>" + "a".repeat(200_000) + "</style><p>Hi there</p>")
    }

    @Test(timeout = 2_000)
    fun repeated_background_properties_finish_quickly() {
        assertFalse(declares_background_image("background:".repeat(100_000)))
        assertTrue(declares_background_image("color:red;background: #fff url(a.png)"))
        assertTrue(declares_background_image("background-image : url (a.png)"))
        assertFalse(declares_background_image("background:#fff;color:url(a.png)"))
    }

    @Test(timeout = 2_000)
    fun an_unclosed_color_function_finishes_quickly() {
        val out = lighten_dark_email_text(
            "<div style=\"background:" + "rgb(".repeat(50_000) + "\">Hi</div>",
        )
        assertTrue(out.contains("Hi"))
    }
}
