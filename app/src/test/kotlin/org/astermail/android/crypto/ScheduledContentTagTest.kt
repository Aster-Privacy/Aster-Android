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

package org.astermail.android.crypto

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledContentTagTest {

    private val identity_key = "identity-current"

    private fun vector_a() = JSONObject()
        .put("to_recipients", JSONArray(listOf("friend@example.com")))
        .put("cc_recipients", JSONArray())
        .put("bcc_recipients", JSONArray(listOf("hidden@example.org")))
        .put("subject", "lunch")
        .put("body", "<p>see you at noon</p>")
        .put("from", JSONObject().put("name", "Me").put("email", "me@astermail.org"))

    private fun vector_b(plain_flag: String = "is_plain_text") = JSONObject()
        .put("to_recipients", JSONArray(listOf("a@example.com", "b@example.com")))
        .put("cc_recipients", JSONArray(listOf("c@example.com")))
        .put("bcc_recipients", JSONArray())
        .put("subject", "Gr\u00fc\u00dfe")
        .put("body", "line one<br>line two")
        .put("plain_body", "line one\nline two")
        .put(plain_flag, true)
        .put("in_reply_to", "<x@y>")
        .put("thread_id", "t1")
        .put("reply_to_id", "r1")

    @Test
    fun matches_the_shared_vectors() {
        assertEquals("6cZ1oM3Sq8Z7Z/8lSNvcAMVKMac+WBhNY1ecX5JeMv0=", ScheduledContentTag.compute(identity_key, vector_a()))
        assertEquals("LRKYB/KcQHjpcbqTEo1/c8gLNJWtgHPX93u2/I//NY0=", ScheduledContentTag.compute(identity_key, vector_b()))
        assertEquals(
            "LRKYB/KcQHjpcbqTEo1/c8gLNJWtgHPX93u2/I//NY0=",
            ScheduledContentTag.compute(identity_key, vector_b("plain_text")),
        )
    }

    @Test
    fun the_tag_field_does_not_change_the_tag() {
        val tagged = ScheduledContentTag.tagged(identity_key, vector_a())
        assertEquals("6cZ1oM3Sq8Z7Z/8lSNvcAMVKMac+WBhNY1ecX5JeMv0=", tagged.getString(ScheduledContentTag.FIELD))
        assertTrue(ScheduledContentTag.verifies(JSONObject(tagged.toString()), listOf(identity_key)))
    }

    @Test
    fun a_previous_identity_key_still_verifies() {
        val tagged = ScheduledContentTag.tagged("identity-old", vector_a())
        assertTrue(ScheduledContentTag.verifies(tagged, listOf(identity_key, "", "identity-old")))
    }

    @Test
    fun any_changed_field_fails() {
        val changes = listOf<(JSONObject) -> Unit>(
            { it.put("subject", "dinner") },
            { it.put("body", "<p>see you at one</p>") },
            { it.put("plain_body", "x") },
            { it.put("is_plain_text", true) },
            { it.getJSONObject("from").put("name", "Someone") },
            { it.getJSONObject("from").put("email", "other@astermail.org") },
            { it.put("in_reply_to", "<a@b>") },
            { it.put("thread_id", "t2") },
            { it.put("reply_to_id", "r2") },
            { it.getJSONArray("to_recipients").put("extra@example.com") },
            { it.put("cc_recipients", JSONArray(listOf("friend@example.com"))) },
            { it.put("bcc_recipients", JSONArray()) },
        )
        for (change in changes) {
            val tagged = ScheduledContentTag.tagged(identity_key, vector_a())
            change(tagged)
            assertFalse(ScheduledContentTag.verifies(tagged, listOf(identity_key)))
        }
    }

    @Test
    fun missing_malformed_or_foreign_tags_fail() {
        assertFalse(ScheduledContentTag.verifies(vector_a(), listOf(identity_key)))
        for (bad in listOf<Any>("", "not base64 !", "AAAA", 42, JSONObject.NULL)) {
            assertFalse(ScheduledContentTag.verifies(vector_a().put(ScheduledContentTag.FIELD, bad), listOf(identity_key)))
        }
        val foreign = ScheduledContentTag.tagged("identity-other", vector_a())
        assertFalse(ScheduledContentTag.verifies(foreign, listOf(identity_key)))
    }

    @Test
    fun blank_and_placeholder_keys_never_verify() {
        val untagged = vector_a()
        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest("undefinedastermail-scheduled-tag-v1".toByteArray(Charsets.UTF_8))
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        val message = java.io.ByteArrayOutputStream()
        message.write("astermail-scheduled-tag-v1".toByteArray(Charsets.UTF_8))
        for (part in listOf("lunch", "<p>see you at noon</p>", "", "0", "Me", "me@astermail.org", "", "", "", "1", "friend@example.com", "0", "1", "hidden@example.org")) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            message.write(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            message.write(bytes)
        }
        val placeholder_tag = java.util.Base64.getEncoder().encodeToString(mac.doFinal(message.toByteArray()))
        untagged.put(ScheduledContentTag.FIELD, placeholder_tag)
        assertFalse(ScheduledContentTag.verifies(untagged, listOf("", " ", "undefined")))
        assertFalse(ScheduledContentTag.is_usable_key(null))
        assertFalse(ScheduledContentTag.is_usable_key(""))
        assertFalse(ScheduledContentTag.is_usable_key("undefined"))
        assertTrue(ScheduledContentTag.is_usable_key(identity_key))
    }
}
