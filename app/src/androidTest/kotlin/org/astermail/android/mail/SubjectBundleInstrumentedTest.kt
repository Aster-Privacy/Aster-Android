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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubjectBundleInstrumentedTest {

    private fun encode_bundle(subject: String, body: String): String {
        return ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
            put("s", subject)
            put("b", body)
        }.toString()
    }

    @Test
    fun round_trips_web_style_bundle_on_device() {
        val result = extract_subject_bundle(
            ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Question\",\"b\":\"<p>Hi, I'm new here</p>\"}",
        )
        assertEquals("Question", result.subject)
        assertEquals("<p>Hi, I'm new here</p>", result.body)
    }

    @Test
    fun round_trips_unicode_bundle_on_device() {
        val subject = "café ☕ 你好"
        val body = "line1\nline2 \"quoted\" 🚀"
        val result = extract_subject_bundle(encode_bundle(subject, body))
        assertEquals(subject, result.subject)
        assertEquals(body, result.body)
    }

    @Test
    fun recovers_truncated_payload_on_device() {
        val truncated = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Re: \",\"b\":\"<p>Thanks!</p>"
        val result = extract_subject_bundle(truncated)
        assertEquals("Re: ", result.subject)
        assertEquals("<p>Thanks!</p>", result.body)
    }

    @Test
    fun recovers_raw_newline_payload_on_device() {
        val broken = ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Re: \",\"b\":\"line one\nline two\"}"
        val result = extract_subject_bundle(broken)
        assertEquals("Re: ", result.subject)
        assertEquals("line one\nline two", result.body)
    }

    @Test
    fun recovers_bom_framed_payload_on_device() {
        val framed = "\ufeff" + encode_bundle("Hi", "there")
        val result = extract_subject_bundle(framed)
        assertEquals("Hi", result.subject)
        assertEquals("there", result.body)
    }

    @Test
    fun leaves_plain_text_untouched_on_device() {
        val result = extract_subject_bundle("plain body text")
        assertNull(result.subject)
        assertEquals("plain body text", result.body)
    }

    private fun legacy_extract(body: String): SubjectBundle {
        if (body.isEmpty() || !body.startsWith(ASTER_SUBJECT_BUNDLE_PREFIX)) {
            return SubjectBundle(null, body)
        }
        val payload = body.substring(ASTER_SUBJECT_BUNDLE_PREFIX.length)
        try {
            val obj = JSONObject(payload)
            val s = obj.opt("s")
            val b = obj.opt("b")
            if (s is String && b is String) return SubjectBundle(s, b)
        } catch (_: Throwable) {
        }
        return SubjectBundle(null, body)
    }

    @Test
    fun new_extractor_recovers_every_payload_the_legacy_one_leaked() {
        val samples = listOf(
            ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Question\",\"b\":\"<p>Hi</p>",
            ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"\",\"b\":\"Hi, I'm new\nhere\"}",
            "\u0000" + ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Re: \",\"b\":\"body\"}",
            "\ufeff" + ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Hi\",\"b\":\"body\"}",
            ASTER_SUBJECT_BUNDLE_PREFIX + "{\"b\":\"body first\",\"s\":\"Subject\"}",
            ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":1,\"b\":\"body\"}",
        )
        for (sample in samples) {
            val fixed = extract_subject_bundle(sample)
            assertEquals(false, fixed.body.contains(ASTER_SUBJECT_BUNDLE_PREFIX))
            assertEquals(false, fixed.body.isEmpty())
        }
    }

    @Test
    fun legacy_extractor_leaked_the_marker_for_known_fatal_payloads() {
        val legacy_fatal = listOf(
            ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Question\",\"b\":\"<p>Hi</p>",
            " " + ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Re: \",\"b\":\"body\"}",
            "\ufeff" + ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Hi\",\"b\":\"body\"}",
            ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":1,\"b\":\"body\"}",
        )
        for (sample in legacy_fatal) {
            assertEquals(true, legacy_extract(sample).body.contains(ASTER_SUBJECT_BUNDLE_PREFIX))
            assertEquals(false, extract_subject_bundle(sample).body.contains(ASTER_SUBJECT_BUNDLE_PREFIX))
        }
    }
}
