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

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

object ScheduledContentTag {

    const val FIELD = "content_tag"
    private const val DOMAIN = "astermail-scheduled-tag-v1"
    private const val PUBLIC_PLACEHOLDER_KEY = "undefined"

    fun compute(identity_key: String, envelope: JSONObject): String {
        require(is_usable_key(identity_key))
        val key = MessageDigest.getInstance("SHA-256").digest((identity_key + DOMAIN).toByteArray(Charsets.UTF_8))
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return Base64.getEncoder().encodeToString(mac.doFinal(message_of(envelope)))
        } finally {
            key.fill(0)
        }
    }

    fun tagged(identity_key: String, envelope: JSONObject): JSONObject {
        envelope.remove(FIELD)
        return envelope.put(FIELD, compute(identity_key, envelope))
    }

    fun is_tagged(envelope: JSONObject): Boolean = envelope.has(FIELD)

    fun verifies(envelope: JSONObject, keys: List<String>): Boolean {
        if (envelope.isNull(FIELD)) return false
        val claimed = envelope.opt(FIELD) as? String ?: return false
        val claimed_bytes = runCatching { Base64.getDecoder().decode(claimed) }.getOrNull() ?: return false
        if (claimed_bytes.size != 32) return false
        var matched = false
        for (key in keys) {
            if (!is_usable_key(key)) continue
            val expected = Base64.getDecoder().decode(compute(key, envelope))
            if (MessageDigest.isEqual(expected, claimed_bytes)) matched = true
        }
        return matched
    }

    fun is_usable_key(identity_key: String?): Boolean =
        !identity_key.isNullOrBlank() && identity_key != PUBLIC_PLACEHOLDER_KEY

    private fun message_of(envelope: JSONObject): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(DOMAIN.toByteArray(Charsets.UTF_8))
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            out.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            out.write(bytes)
        }
        val from = envelope.optJSONObject("from")
        add(text_of(envelope, "subject"))
        add(text_of(envelope, "body"))
        add(text_of(envelope, "plain_body"))
        add(if (is_true(envelope, "is_plain_text") || is_true(envelope, "plain_text")) "1" else "0")
        add(text_of(from, "name"))
        add(text_of(from, "email"))
        add(text_of(envelope, "in_reply_to"))
        add(text_of(envelope, "thread_id"))
        add(text_of(envelope, "reply_to_id"))
        for (field in listOf("to_recipients", "cc_recipients", "bcc_recipients")) {
            val list = addresses_of(envelope.optJSONArray(field))
            add(list.size.toString())
            list.forEach(::add)
        }
        return out.toByteArray()
    }

    private fun text_of(obj: JSONObject?, key: String): String {
        if (obj == null || obj.isNull(key)) return ""
        return obj.opt(key)?.toString().orEmpty()
    }

    private fun is_true(obj: JSONObject, key: String): Boolean = obj.opt(key) == true

    private fun addresses_of(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            if (array.isNull(index)) "" else array.opt(index).toString()
        }
    }
}
