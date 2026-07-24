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

package org.astermail.android.mail

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectBundleTest {

    private fun encode_bundle(subject: String, body: String): String {
        return ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
            put("s", subject)
            put("b", body)
        }.toString()
    }

    @Test
    fun recovers_subject_and_body_for_ascii_content() {
        val result = extract_subject_bundle(encode_bundle("Re: invoice", "see attached"))
        assertEquals("Re: invoice", result.subject)
        assertEquals("see attached", result.body)
    }

    @Test
    fun recovers_subject_and_body_for_html_content() {
        val body = "<p>hi <b>there</b></p>"
        val result = extract_subject_bundle(encode_bundle("hi", body))
        assertEquals("hi", result.subject)
        assertEquals(body, result.body)
    }

    @Test
    fun recovers_content_with_unicode_and_newlines() {
        val subject = "café ☕"
        val body = "line1\nline2\n\"quoted\"\t🚀"
        val result = extract_subject_bundle(encode_bundle(subject, body))
        assertEquals(subject, result.subject)
        assertEquals(body, result.body)
    }

    @Test
    fun recovers_empty_subject() {
        val result = extract_subject_bundle(encode_bundle("", "body only"))
        assertEquals("", result.subject)
        assertEquals("body only", result.body)
    }

    @Test
    fun recovers_empty_body() {
        val result = extract_subject_bundle(encode_bundle("subject only", ""))
        assertEquals("subject only", result.subject)
        assertEquals("", result.body)
    }

    @Test
    fun returns_null_subject_when_no_prefix_present() {
        val result = extract_subject_bundle("plain body text")
        assertNull(result.subject)
        assertEquals("plain body text", result.body)
    }

    @Test
    fun returns_null_subject_for_empty_input() {
        val result = extract_subject_bundle("")
        assertNull(result.subject)
        assertEquals("", result.body)
    }

    @Test
    fun preserves_embedded_quotes() {
        val subject = "a \"quoted\" subject"
        val body = "line1\nline2\t\"quoted\""
        val result = extract_subject_bundle(encode_bundle(subject, body))
        assertEquals(subject, result.subject)
        assertEquals(body, result.body)
    }

    @Test
    fun strips_the_marker_when_the_payload_is_not_json() {
        val result = extract_subject_bundle(ASTER_SUBJECT_BUNDLE_PREFIX + "not json")
        assertNull(result.subject)
        assertEquals("not json", result.body)
    }

    @Test
    fun strips_the_marker_when_the_payload_lacks_required_fields() {
        val result = extract_subject_bundle(ASTER_SUBJECT_BUNDLE_PREFIX + "{\"x\":1}")
        assertNull(result.subject)
        assertEquals("{\"x\":1}", result.body)
    }

    @Test
    fun never_leaks_the_marker_for_an_unrecoverable_payload() {
        val garbage = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"only a subject\""
        val result = extract_subject_bundle(garbage)
        assertTrue(!result.body.contains(ASTER_SUBJECT_BUNDLE_PREFIX))
    }

    @Test
    fun scan_recovers_body_when_subject_is_wrong_type() {
        val wrong_types = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":1,\"b\":\"ok\"}"
        val result = extract_subject_bundle(wrong_types)
        assertNull(result.subject)
        assertEquals("ok", result.body)
    }

    @Test
    fun does_not_match_when_prefix_appears_mid_string() {
        val mid = "leading text " + encode_bundle("x", "y")
        val result = extract_subject_bundle(mid)
        assertNull(result.subject)
        assertEquals(mid, result.body)
    }

    @Test
    fun decodes_a_bundle_framed_by_control_characters() {
        val framed = "\u0000\u0001" + encode_bundle("Re: ", "<p>Thanks!</p>")
        val result = extract_subject_bundle(framed)
        assertEquals("Re: ", result.subject)
        assertEquals("<p>Thanks!</p>", result.body)
    }

    @Test
    fun decodes_a_bundle_framed_by_a_byte_order_mark() {
        val framed = "\ufeff" + encode_bundle("Hi", "there")
        val result = extract_subject_bundle(framed)
        assertEquals("Hi", result.subject)
        assertEquals("there", result.body)
    }

    @Test
    fun decodes_a_bundle_framed_by_whitespace_and_zero_width_characters() {
        val framed = " \n\u200b\u200e" + encode_bundle("Hi", "there")
        val result = extract_subject_bundle(framed)
        assertEquals("Hi", result.subject)
        assertEquals("there", result.body)
    }

    @Test
    fun recovers_a_payload_with_raw_newlines_inside_string_values() {
        val broken = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Re: \",\"b\":\"line one\nline two\"}"
        val result = extract_subject_bundle(broken)
        assertEquals("Re: ", result.subject)
        assertEquals("line one\nline two", result.body)
    }

    @Test
    fun recovers_a_truncated_payload() {
        val truncated = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Re: \",\"b\":\"<p>Thanks!</p>"
        val result = extract_subject_bundle(truncated)
        assertEquals("Re: ", result.subject)
        assertEquals("<p>Thanks!</p>", result.body)
    }

    @Test
    fun recovers_a_payload_whose_keys_are_ordered_body_first() {
        val reordered = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"b\":\"body text\",\"s\":\"Subject\"}"
        val result = extract_subject_bundle(reordered)
        assertEquals("Subject", result.subject)
        assertEquals("body text", result.body)
    }

    @Test
    fun recovers_a_payload_with_trailing_garbage_after_the_object() {
        val trailing = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Hi\",\"b\":\"body\"}\u0000\u0000garbage"
        val result = extract_subject_bundle(trailing)
        assertEquals("Hi", result.subject)
        assertEquals("body", result.body)
    }

    @Test
    fun recovers_a_payload_with_escaped_quote_at_subject_start() {
        val subject = "\"You've reached the right place\""
        val result = extract_subject_bundle(encode_bundle(subject, "body"))
        assertEquals(subject, result.subject)
        assertEquals("body", result.body)
    }

    @Test
    fun never_leaks_the_raw_bundle_marker_for_a_recoverable_payload() {
        val messy = "\u0000" + ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Re: \",\"b\":\"hello\"}"
        val result = extract_subject_bundle(messy)
        assertEquals("hello", result.body)
        assertTrue(!result.body.contains(ASTER_SUBJECT_BUNDLE_PREFIX))
    }

    @Test
    fun round_trips_the_android_send_wrap_format() {
        val wrapped = ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
            put("s", "Question")
            put("b", "<p>Hi, I'm new here 👋</p>")
        }.toString()
        val result = extract_subject_bundle(wrapped)
        assertEquals("Question", result.subject)
        assertEquals("<p>Hi, I'm new here 👋</p>", result.body)
    }
}
