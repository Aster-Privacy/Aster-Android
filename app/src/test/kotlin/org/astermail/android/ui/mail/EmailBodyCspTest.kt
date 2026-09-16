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
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailBodyCspTest {

    private fun render(body: String, translate_mode: String = "off"): String =
        build_email_html(
            body = body,
            is_dark = false,
            fg_hex = "#111111",
            link_hex = "#0b57d0",
            forwarded_label = "Forwarded",
            image_failed_label = "Image blocked",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = translate_mode,
        )

    private fun csp_of(document: String): String {
        val marker = "<meta http-equiv=\"Content-Security-Policy\" content=\""
        val start = document.indexOf(marker)
        assertTrue("no content security policy in the document", start >= 0)
        val from = start + marker.length
        return document.substring(from, document.indexOf('"', from))
    }

    private fun script_tags(document: String): List<String> =
        Regex("<script[ >][^>]*>?", RegexOption.IGNORE_CASE).findAll(document).map { it.value }.toList()

    @Test
    fun denies_everything_the_body_does_not_need() {
        val csp = csp_of(render("<p>hello</p>"))

        assertTrue(csp.contains("default-src 'none'"))
        assertTrue(csp.contains("object-src 'none'"))
        assertTrue(csp.contains("frame-src 'none'"))
        assertTrue(csp.contains("form-action 'none'"))
        assertTrue(csp.contains("base-uri 'none'"))
    }

    @Test
    fun the_document_denies_script_outright() {
        val csp = csp_of(render("<p>hello</p>"))

        assertTrue(csp.contains("script-src 'none'"))
        assertTrue(csp.contains("worker-src 'none'"))
        assertTrue(csp.contains("connect-src 'none'"))
        assertTrue("no nonce may be issued", !csp.contains("nonce-"))
        assertTrue(
            "the policy must not allow inline script",
            !Regex("script-src[^;]*'unsafe-inline'").containsMatchIn(csp),
        )
        assertTrue(
            "the policy must not allow eval",
            !Regex("script-src[^;]*unsafe-eval").containsMatchIn(csp),
        )
    }

    @Test
    fun the_rendered_document_carries_no_script_of_its_own() {
        assertEquals(emptyList<String>(), script_tags(render("<p>hello</p>")))
    }

    @Test
    fun a_smuggled_script_tag_stays_inert() {
        val smuggled = "<p>hello</p><script>window.AsterTranslateBridge.translate('x')</script>"
        val document = render(smuggled)

        assertTrue(csp_of(document).contains("script-src 'none'"))
        assertEquals(1, script_tags(document).size)
    }

    @Test
    fun translation_never_widens_the_policy() {
        val csp = csp_of(render("<p>hello</p>", translate_mode = "auto"))

        assertTrue(csp.contains("script-src 'none'"))
        assertTrue(!csp.contains("bergamot"))
        assertTrue(!csp.contains("wasm-unsafe-eval"))
    }

    @Test
    fun the_last_resort_document_also_denies_script() {
        val document = plain_text_fallback_document("<b>hi</b>", "#ffffff", "#111111")

        assertTrue(document.contains("Content-Security-Policy"))
        assertTrue(csp_of(document).contains("script-src 'none'"))
        assertEquals(emptyList<String>(), script_tags(document))
    }
}
