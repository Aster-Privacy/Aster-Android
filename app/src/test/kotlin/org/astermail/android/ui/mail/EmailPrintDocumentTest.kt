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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailPrintDocumentTest {

    private val labels = email_print_labels(
        from = "From",
        to = "To",
        cc = "Cc",
        date = "Date",
        image_blocked = "[Image blocked]",
    )

    private val remote_image = "https://cdn.example/hero.png"

    private val remote_image_html = "<p>Hello</p><img src=\"$remote_image\" width=\"600\" height=\"300\" alt=\"Hero\">"

    private val remote_img_src = Regex("<img[^>]*src=\"(https?:)?//", RegexOption.IGNORE_CASE)

    private fun message(
        body: String = "Hello",
        body_html: String? = null,
        to: List<String> = listOf("bob@example.com"),
        cc: List<String> = emptyList(),
        to_label: String = "Bob",
        sender_name: String = "Alice",
    ) = ThreadMessage(
        id = "m1",
        sender_name = sender_name,
        sender_email = "alice@example.com",
        to_label = to_label,
        to_addresses = to,
        cc_addresses = cc,
        timestamp = 0L,
        body = body,
        body_html = body_html,
    )

    @Test
    fun header_contains_subject_sender_recipients_and_date() {
        val html = build_email_print_html(
            msg = message(cc = listOf("carol@example.com")),
            subject = "Quarterly <report>",
            date_text = "Sep 11, 2026, 9:00 AM",
            labels = labels,
            body_html = "<p>Body text</p>",
        )

        assertTrue(html.contains("<h1>Quarterly &lt;report&gt;</h1>"))
        assertTrue(html.contains("<th>From</th><td>Alice &lt;alice@example.com&gt;</td>"))
        assertTrue(html.contains("<th>To</th><td>bob@example.com</td>"))
        assertTrue(html.contains("<th>Cc</th><td>carol@example.com</td>"))
        assertTrue(html.contains("<th>Date</th><td>Sep 11, 2026, 9:00 AM</td>"))
        assertTrue(html.contains("<p>Body text</p>"))
    }

    @Test
    fun recipients_fall_back_to_the_to_label_and_empty_cc_is_omitted() {
        val html = build_email_print_html(message(to = emptyList(), to_label = "Team"), "Hi", "today", labels, "")

        assertTrue(html.contains("<th>To</th><td>Team</td>"))
        assertFalse(html.contains("<th>Cc</th>"))
    }

    @Test
    fun sender_address_is_shown_once_when_the_name_matches() {
        assertEquals("alice@example.com", print_sender_line(message(sender_name = "alice@example.com")))
        assertEquals("alice@example.com", print_sender_line(message(sender_name = " ")))
    }

    @Test
    fun blocked_remote_images_never_reach_the_print_document() {
        val body = build_email_print_body(
            message(body_html = remote_image_html),
            allow_external = false,
            sanitize_options = EmailHtmlSanitizer.SanitizeOptions(),
            image_blocked_label = labels.image_blocked,
        )

        assertTrue(body.contains("Hello"))
        assertFalse(remote_img_src.containsMatchIn(body))
        assertTrue(body.contains("blocked-image"))
    }

    @Test
    fun allowed_remote_images_load_through_the_image_proxy() {
        val body = build_email_print_body(
            message(body_html = remote_image_html),
            allow_external = true,
            sanitize_options = EmailHtmlSanitizer.SanitizeOptions(),
            image_blocked_label = labels.image_blocked,
        )

        assertTrue(body.contains(REMOTE_IMAGE_PROXY_BASE + java.net.URLEncoder.encode(remote_image, "UTF-8")))
        assertFalse(body.contains("src=\"$remote_image\""))
    }

    @Test
    fun html_body_scripts_are_removed() {
        val body = build_email_print_body(
            message(body_html = "<p>ok</p><script>alert(1)</script>"),
            allow_external = true,
            sanitize_options = EmailHtmlSanitizer.SanitizeOptions(),
            image_blocked_label = labels.image_blocked,
        )

        assertTrue(body.contains("ok"))
        assertFalse(body.contains("<script", ignoreCase = true))
    }

    @Test
    fun plain_text_body_is_escaped() {
        val body = build_email_print_body(
            message(body = "<script>alert(1)</script>\nline two"),
            allow_external = false,
            sanitize_options = EmailHtmlSanitizer.SanitizeOptions(),
            image_blocked_label = labels.image_blocked,
        )

        assertTrue(body.contains("&lt;script&gt;alert(1)&lt;/script&gt;\nline two"))
        assertFalse(body.contains("<script"))
    }

    @Test
    fun job_name_removes_unsafe_characters_and_falls_back_when_blank() {
        assertEquals("Invoice 12 34 final", print_job_name("Invoice 12/34: \"final\"\n", "Aster email"))
        assertEquals("Aster email", print_job_name(" \n\t ", "Aster email"))
        assertEquals(80, print_job_name("a".repeat(200), "Aster email").length)
    }

    @Test
    fun activity_is_found_behind_context_wrappers() {
        val activity = mockk<Activity>()
        val wrapper = mockk<ContextWrapper>()
        every { wrapper.baseContext } returns activity

        assertSame(activity, find_activity(wrapper))
        assertSame(activity, find_activity(activity))
        assertNull(find_activity(mockk<Context>()))
    }

    @Test
    fun finishing_the_print_job_releases_the_web_view_exactly_once() {
        val delegate = mockk<PrintDocumentAdapter>(relaxed = true)
        var releases = 0
        val adapter = retaining_print_adapter(delegate, "failed") { releases++ }

        adapter.onLayout(null, mockk<PrintAttributes>(relaxed = true), null, mockk(relaxed = true), null)
        adapter.onWrite(emptyArray(), mockk<ParcelFileDescriptor>(relaxed = true), null, mockk(relaxed = true))
        assertEquals(0, releases)

        adapter.onFinish()
        adapter.onFinish()

        assertEquals(1, releases)
        verify(exactly = 2) { delegate.onFinish() }
    }

    @Test
    fun release_still_happens_when_the_delegate_fails_to_finish() {
        val delegate = mockk<PrintDocumentAdapter>(relaxed = true)
        every { delegate.onFinish() } throws IllegalStateException("destroyed")
        var releases = 0

        retaining_print_adapter(delegate, "failed") { releases++ }.onFinish()

        assertEquals(1, releases)
    }

    @Test
    fun write_and_layout_failures_are_reported_instead_of_thrown() {
        val delegate = mockk<PrintDocumentAdapter>(relaxed = true)
        every { delegate.onWrite(any(), any(), any(), any()) } throws IllegalStateException("printing is already pending")
        every { delegate.onLayout(any(), any(), any(), any(), any()) } throws IllegalArgumentException("no media size")
        val write_callback = mockk<PrintDocumentAdapter.WriteResultCallback>(relaxed = true)
        val layout_callback = mockk<PrintDocumentAdapter.LayoutResultCallback>(relaxed = true)
        val adapter = retaining_print_adapter(delegate, "failed") {}

        adapter.onWrite(arrayOf<PageRange>(), mockk(relaxed = true), null, write_callback)
        adapter.onLayout(null, mockk(relaxed = true), null, layout_callback, null)

        verify { write_callback.onWriteFailed("failed") }
        verify { layout_callback.onLayoutFailed("failed") }
    }
}
