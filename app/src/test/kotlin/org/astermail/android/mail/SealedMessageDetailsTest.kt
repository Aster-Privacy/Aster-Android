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

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedMessageDetailsTest {

    private val web_bundle = ASTER_SUBJECT_BUNDLE_PREFIX +
        "{\"s\":\"Lunch\",\"b\":\"<p>See you at noon</p>\",\"details\":{\"v\":1,\"from_name\":\"Ada Park\"," +
        "\"to\":[{\"name\":\"\",\"email\":\"bob@astermail.org\"}]," +
        "\"cc\":[{\"name\":\"Cy\",\"email\":\"cy@aster.cx\"}],\"sent_at\":\"2026-11-12T10:00:00.000Z\"}}"

    private fun envelope() = DecryptedEnvelope(
        subject = "",
        body_text = "",
        body_html = null,
        from_name = "Served Name",
        from_email = "ada@astermail.org",
        to = listOf("" to "served@astermail.org"),
        cc = emptyList(),
        sent_at = "2026-11-01T00:00:00Z",
    )

    @Test
    fun reads_details_written_by_the_web_client() {
        val bundle = extract_subject_bundle(web_bundle)
        assertEquals("Lunch", bundle.subject)
        assertEquals("<p>See you at noon</p>", bundle.body)
        assertEquals(
            SealedMessageDetails(
                from_name = "Ada Park",
                to = listOf(SealedAddress("", "bob@astermail.org")),
                cc = listOf(SealedAddress("Cy", "cy@aster.cx")),
                sent_at = "2026-11-12T10:00:00.000Z",
            ),
            bundle.details,
        )
    }

    @Test
    fun a_bundle_without_details_reads_as_before() {
        val bundle = extract_subject_bundle(ASTER_SUBJECT_BUNDLE_PREFIX + "{\"s\":\"Hi\",\"b\":\"body\"}")
        assertEquals("Hi", bundle.subject)
        assertEquals("body", bundle.body)
        assertNull(bundle.details)
        assertEquals(envelope(), envelope().with_sealed_details(bundle.details))
    }

    @Test
    fun details_come_only_from_the_outer_layer() {
        val inner = web_bundle
        val outer = ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
            put("s", "Outer")
            put("b", inner)
        }.toString()
        val bundle = extract_subject_bundle(outer)
        assertEquals("<p>See you at noon</p>", bundle.body)
        assertNull(bundle.details)
    }

    @Test
    fun written_bundle_round_trips() {
        val details = build_sealed_details(
            from_name = " \"Ada Park\" ",
            to = listOf("bob@astermail.org", "Cy Lee <cy@aster.cx>", "  "),
            cc = emptyList(),
            now = Instant.parse("2026-11-12T10:00:00Z"),
        )
        val text = build_subject_bundle_text("Lunch", "<p>See you</p>", details)
        val payload = JSONObject(text.removePrefix(ASTER_SUBJECT_BUNDLE_PREFIX))
        assertEquals(1, payload.getJSONObject("details").getInt("v"))
        val bundle = extract_subject_bundle(text)
        assertEquals("Lunch", bundle.subject)
        assertEquals("<p>See you</p>", bundle.body)
        assertEquals(
            SealedMessageDetails(
                from_name = "Ada Park",
                to = listOf(SealedAddress("", "bob@astermail.org"), SealedAddress("Cy Lee", "cy@aster.cx")),
                cc = emptyList(),
                sent_at = "2026-11-12T10:00:00.000Z",
            ),
            bundle.details,
        )
    }

    @Test
    fun scheduled_time_is_used_as_the_sent_time() {
        val details = build_sealed_details(null, emptyList(), emptyList(), "2026-12-01T08:30:00+02:00")
        assertEquals("2026-12-01T06:30:00.000Z", details.sent_at)
        assertEquals("", details.from_name)
    }

    @Test
    fun sender_name_follows_the_chosen_address() {
        assertEquals("Work", sealed_sender_name("Work", "hash", "Ada"))
        assertEquals("", sealed_sender_name(null, "hash", "Ada"))
        assertEquals("Ada", sealed_sender_name("", null, "Ada"))
        assertEquals("", sealed_sender_name(null, null, null))
    }

    @Test
    fun malformed_details_are_ignored() {
        fun parse(json: String) = parse_sealed_details(JSONObject(json))
        val valid = "{\"v\":1,\"from_name\":\"A\",\"to\":[],\"cc\":[],\"sent_at\":\"2026-11-12T10:00:00.000Z\"}"
        assertEquals("A", parse(valid)?.from_name)
        assertNull(parse_sealed_details(null))
        assertNull(parse_sealed_details("text"))
        assertNull(parse(valid.replace("\"v\":1", "\"v\":2")))
        assertNull(parse(valid.replace("\"from_name\":\"A\"", "\"from_name\":5")))
        assertNull(parse(valid.replace("2026-11-12T10:00:00.000Z", "soon")))
        assertNull(parse(valid.replace("\"to\":[]", "\"to\":\"bob\"")))
        assertNull(parse(valid.replace("\"cc\":[],", "")))
        assertNull(parse(valid.replace("\"to\":[]", "\"to\":[{\"name\":\"B\"}]")))
        assertNull(parse(valid.replace("\"to\":[]", "\"to\":[{\"name\":7,\"email\":\"b@aster.cx\"}]")))
        assertNull(parse(valid.replace("\"to\":[]", "\"to\":[{\"email\":\"" + "a".repeat(321) + "\"}]")))
    }

    @Test
    fun sealed_details_replace_the_served_values() {
        val details = extract_subject_bundle(web_bundle).details
        val sealed = envelope().with_sealed_details(details)
        assertTrue(sealed.details_sealed)
        assertEquals("Ada Park", sealed.from_name)
        assertEquals(listOf("" to "bob@astermail.org"), sealed.to)
        assertEquals(listOf("Cy" to "cy@aster.cx"), sealed.cc)
        assertEquals("2026-11-12T10:00:00.000Z", sealed.sent_at)
        assertEquals("ada@astermail.org", sealed.from_email)
    }

    @Test
    fun an_empty_sealed_name_falls_back_to_the_address() {
        val details = SealedMessageDetails("", emptyList(), emptyList(), "2026-11-12T10:00:00.000Z")
        assertEquals("ada", envelope().with_sealed_details(details).from_name)
    }

    @Test
    fun details_apply_only_to_a_confirmed_sealed_message() {
        assertTrue(carries_sealed_details(true, false, false))
        assertFalse(carries_sealed_details(false, false, false))
        assertFalse(carries_sealed_details(true, true, false))
        assertFalse(carries_sealed_details(true, false, true))
    }

    @Test
    fun snapshot_keeps_the_sealed_time() {
        val snapshot = thread_snapshot_json.decodeFromString(
            thread_snapshot_message.serializer(),
            "{\"id\":\"m\",\"sender_name\":\"A\",\"sender_email\":\"a@aster.cx\",\"to_label\":\"\"," +
                "\"timestamp\":\"2026-11-01T00:00:00Z\",\"body_text\":\"\",\"raw_item\":{\"id\":\"m\"}}",
        )
        assertNull(thread_message_of(snapshot).sealed_sent_at)
        val restored = thread_message_of(
            thread_snapshot_of(thread_message_of(snapshot).copy(sealed_sent_at = "2026-11-12T10:00:00.000Z")),
        )
        assertEquals("2026-11-12T10:00:00.000Z", restored.sealed_sent_at)
    }

    @Test
    fun sealed_subject_replaces_the_served_subject() {
        val details = extract_subject_bundle(web_bundle).details
        val served = envelope().copy(subject = "Served subject")

        assertEquals("Lunch", served.with_sealed_details(details, "Lunch").subject)
        assertEquals("Served subject", served.with_sealed_details(details, null).subject)
    }

    @Test
    fun sealed_details_drop_the_served_reply_to() {
        val details = extract_subject_bundle(web_bundle).details
        val served = envelope().copy(
            raw_headers = listOf(
                " Reply-To " to "other@example.com",
                "reply-to" to "second@example.com",
                "Delivered-To" to "bob@astermail.org",
            ),
        )

        val sealed = served.with_sealed_details(details, "Lunch")

        assertEquals(listOf("Delivered-To" to "bob@astermail.org"), sealed.raw_headers)
    }

    @Test
    fun without_sealed_details_the_served_subject_and_reply_to_stay() {
        val served = envelope().copy(
            subject = "Served subject",
            raw_headers = listOf("Reply-To" to "other@example.com"),
        )

        assertEquals(served, served.with_sealed_details(null, "Lunch"))
    }
}
