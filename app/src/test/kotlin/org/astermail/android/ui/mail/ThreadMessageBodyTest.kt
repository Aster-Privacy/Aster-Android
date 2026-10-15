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

import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.mail.ThreadMessageDecrypted
import org.astermail.android.mail.build_plain_text_html
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadMessageBodyTest {

    private fun decrypted(body_text: String, body_html: String? = null) = ThreadMessageDecrypted(
        id = "m1",
        sender_name = "zuhri",
        sender_email = "zuhri@example.com",
        to_label = "me",
        timestamp = "2026-09-28T07:14:00Z",
        body_text = body_text,
        body_html = body_html,
        is_encrypted = true,
        is_read = true,
        raw_item = ThreadMessageItem(id = "m1", item_type = "received"),
    )

    private val patch_body = """
        |From: zuhri <zuhri@example.com>
        |
        |---
        | README.md  |  2 ++
        | src/main.c | 10 ++++++++++
        | 2 files changed, 12 insertions(+)
        |
        |diff --git a/src/main.c b/src/main.c
        |new file mode 100644
        |--- /dev/null
        |+++ b/src/main.c
        |@@ -0,0 +1,10 @@
        |+#include <stdio.h>
        |+#include <stdlib.h>
        |+
        |+int main(void) {
        |+	if (a < b && c > d) {
        |+		puts("hi");
        |+	}
        |+	return 0;
        |+}
        |--
        |2.43.0
    """.trimMargin()

    private val pgp_message = """
        |-----BEGIN PGP MESSAGE-----
        |
        |hQIMA1zLqvW0K9aEAQ/+Ixq0P2fZ4mB6
        |=Ab12
        |-----END PGP MESSAGE-----
    """.trimMargin()

    @Test
    fun git_patch_body_is_shown_unchanged() {
        val mapped = thread_message_to_mock(decrypted(patch_body))

        assertEquals(patch_body, mapped.body)
        assertNull(mapped.body_html)
    }

    @Test
    fun undecryptable_pgp_message_keeps_its_armor() {
        val mapped = thread_message_to_mock(decrypted(pgp_message))

        assertEquals(pgp_message, mapped.body)
        assertFalse(mapped.body.isBlank())
    }

    @Test
    fun inline_pgp_keeps_the_text_around_it() {
        val body = "Here is the file.\n\n$pgp_message\n\nThanks"

        val mapped = thread_message_to_mock(decrypted(body))

        assertEquals(body, mapped.body)
    }

    @Test
    fun clearsigned_message_is_shown() {
        val body = """
            |-----BEGIN PGP SIGNED MESSAGE-----
            |Hash: SHA512
            |
            |Meet at <noon> tomorrow.
            |-----BEGIN PGP SIGNATURE-----
            |
            |iQIzBAEBCgAdFiEEWE92uf0Sjv7f2i1CuFR/pHWLqVAFAmcABCD=
            |=x9Zq
            |-----END PGP SIGNATURE-----
        """.trimMargin()

        val mapped = thread_message_to_mock(decrypted(body))

        assertEquals(body, mapped.body)
    }

    @Test
    fun patch_with_pgp_block_in_the_diff_is_shown() {
        val body = "$patch_body\n+$pgp_message"

        val mapped = thread_message_to_mock(decrypted(body))

        assertEquals(body, mapped.body)
    }

    @Test
    fun html_with_armor_is_kept_when_text_is_also_armor() {
        val html = "<pre>$pgp_message</pre>"

        val mapped = thread_message_to_mock(decrypted(pgp_message, html))

        assertEquals(html, mapped.body_html)
        assertEquals(pgp_message, mapped.body)
    }

    @Test
    fun html_only_armor_is_kept() {
        val html = "<div>$pgp_message</div>"

        val mapped = thread_message_to_mock(decrypted("", html))

        assertEquals(html, mapped.body_html)
        assertTrue(mapped.body.contains("-----BEGIN PGP MESSAGE-----"))
    }

    @Test
    fun armored_html_is_dropped_when_text_holds_the_decrypted_body() {
        val mapped = thread_message_to_mock(decrypted("Decrypted hello", "<p>$pgp_message</p>"))

        assertNull(mapped.body_html)
        assertEquals("Decrypted hello", mapped.body)
    }

    @Test
    fun patch_that_edits_html_is_not_stripped() {
        val body = """
            |---
            | index.html | 1 +
            |
            |diff --git a/index.html b/index.html
            |@@ -1,3 +1,4 @@
            | <div>
            |+<p>Hello</p><br>
            | </div>
            |--
            |2.43.0
        """.trimMargin()

        val mapped = thread_message_to_mock(decrypted(body))

        assertEquals(body, mapped.body)
    }

    @Test
    fun html_text_body_is_still_stripped() {
        val mapped = thread_message_to_mock(decrypted("<p>Hello</p><br><p>World</p>"))

        assertFalse(mapped.body.contains("<p>"))
        assertTrue(mapped.body.contains("Hello"))
        assertTrue(mapped.body.contains("World"))
    }

    @Test
    fun plain_text_render_escapes_and_keeps_patch_layout() {
        val html = build_plain_text_html(patch_body)

        assertTrue(html.contains("#include &lt;stdio.h&gt;"))
        assertTrue(html.contains("if (a &lt; b &amp;&amp; c &gt; d)"))
        assertTrue(html.contains("white-space:pre-wrap"))
        assertTrue(html.contains("\t\tputs"))
        assertFalse(html.contains("<stdio.h>"))
    }
}
