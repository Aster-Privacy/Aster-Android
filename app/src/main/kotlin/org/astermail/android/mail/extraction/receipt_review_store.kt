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
package org.astermail.android.mail.extraction

import org.json.JSONObject
import java.util.Locale

const val receipt_review_store_key = "receipt_review_v1"

private const val max_email_reviews = 300
private const val max_sender_memories = 200
private const val max_review_field_length = 80
private const val max_review_key_length = 320

private val review_control_chars = Regex("[\\u0000-\\u001F\\u007F\\u200B-\\u200F\\u2028-\\u202E\\u2060\\uFEFF<>]")
private val review_whitespace = Regex("\\s+")

interface receipt_review_storage {
    fun read(): String?
    fun write(value: String)
}

class memory_receipt_review_storage(private var value: String? = null) : receipt_review_storage {
    override fun read(): String? = value
    override fun write(value: String) {
        this.value = value
    }
}

data class ReceiptEmailReview(
    val merchant_name: String?,
    val order_id: String?,
    val confirmed: Boolean,
    val hidden: Boolean,
    val updated_at: Long,
) {
    val is_corrected: Boolean
        get() = merchant_name != null || order_id != null
}

data class ReceiptSenderMemory(
    val merchant_name: String,
    val updated_at: Long,
)

data class ReceiptReviewData(
    val emails: Map<String, ReceiptEmailReview>,
    val senders: Map<String, ReceiptSenderMemory>,
) {
    companion object {
        val empty = ReceiptReviewData(emptyMap(), emptyMap())
    }
}

data class ReceiptReviewPatch(
    val merchant_name: String? = null,
    val order_id: String? = null,
    val confirmed: Boolean? = null,
    val hidden: Boolean? = null,
)

fun sanitize_review_text(raw: String?): String? {
    if (raw == null) return null
    val cleaned = raw
        .replace(review_control_chars, "")
        .replace(review_whitespace, " ")
        .trim()
        .take(max_review_field_length)
    return cleaned.ifEmpty { null }
}

fun sanitize_review_key(raw: String): String = raw.trim().lowercase(Locale.ROOT).take(max_review_key_length)

private fun JSONObject.optional_string(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).ifEmpty { null } else null

private fun parse_review_data(raw: String?): ReceiptReviewData {
    if (raw.isNullOrBlank()) return ReceiptReviewData.empty
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return ReceiptReviewData.empty
    val emails = LinkedHashMap<String, ReceiptEmailReview>()
    root.optJSONObject("emails")?.let { emails_json ->
        for (key in emails_json.keys()) {
            val entry = emails_json.optJSONObject(key) ?: continue
            emails[key] = ReceiptEmailReview(
                merchant_name = sanitize_review_text(entry.optional_string("merchant_name")),
                order_id = sanitize_review_text(entry.optional_string("order_id")),
                confirmed = entry.optBoolean("confirmed", false),
                hidden = entry.optBoolean("hidden", false),
                updated_at = entry.optLong("updated_at", 0L),
            )
        }
    }
    val senders = LinkedHashMap<String, ReceiptSenderMemory>()
    root.optJSONObject("senders")?.let { senders_json ->
        for (key in senders_json.keys()) {
            val entry = senders_json.optJSONObject(key) ?: continue
            val merchant_name = sanitize_review_text(entry.optional_string("merchant_name")) ?: continue
            senders[key] = ReceiptSenderMemory(merchant_name, entry.optLong("updated_at", 0L))
        }
    }
    return ReceiptReviewData(emails, senders)
}

private fun serialize_review_data(data: ReceiptReviewData): String {
    val emails = JSONObject()
    for ((key, review) in data.emails) {
        emails.put(
            key,
            JSONObject()
                .put("merchant_name", review.merchant_name ?: JSONObject.NULL)
                .put("order_id", review.order_id ?: JSONObject.NULL)
                .put("confirmed", review.confirmed)
                .put("hidden", review.hidden)
                .put("updated_at", review.updated_at),
        )
    }
    val senders = JSONObject()
    for ((key, memory) in data.senders) {
        senders.put(
            key,
            JSONObject()
                .put("merchant_name", memory.merchant_name)
                .put("updated_at", memory.updated_at),
        )
    }
    return JSONObject().put("emails", emails).put("senders", senders).toString()
}

private fun prune_review_data(data: ReceiptReviewData): ReceiptReviewData {
    val emails = if (data.emails.size > max_email_reviews) {
        data.emails.entries
            .sortedByDescending { it.value.updated_at }
            .take(max_email_reviews)
            .associate { it.key to it.value }
    } else {
        data.emails
    }
    val senders = if (data.senders.size > max_sender_memories) {
        data.senders.entries
            .sortedByDescending { it.value.updated_at }
            .take(max_sender_memories)
            .associate { it.key to it.value }
    } else {
        data.senders
    }
    return ReceiptReviewData(emails, senders)
}

class receipt_review_store(
    private val storage: receipt_review_storage,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun load(): ReceiptReviewData = parse_review_data(storage.read())

    fun review_for(email_id: String): ReceiptEmailReview? = load().emails[sanitize_review_key(email_id)]

    fun remembered_merchant(merchant_source: String): String? =
        load().senders[sanitize_review_key(merchant_source)]?.merchant_name

    fun apply_review(
        email_id: String,
        merchant_source: String,
        merchant_name: String?,
        order_id: String?,
    ): Pair<String?, String?> {
        val data = load()
        val review = data.emails[sanitize_review_key(email_id)]
        val remembered = if (review?.merchant_name == null) {
            data.senders[sanitize_review_key(merchant_source)]?.merchant_name
        } else {
            null
        }
        return (review?.merchant_name ?: remembered ?: merchant_name) to (review?.order_id ?: order_id)
    }

    fun save_correction(email_id: String, merchant_source: String, merchant_name: String?, order_id: String?) {
        val clean_merchant = sanitize_review_text(merchant_name)
        val clean_order_id = sanitize_review_text(order_id)
        val timestamp = now()
        val data = update_review(
            email_id,
            ReceiptReviewPatch(merchant_name = clean_merchant, order_id = clean_order_id, confirmed = false, hidden = false),
            replace_fields = true,
        )
        if (clean_merchant == null) return
        val sender_key = sanitize_review_key(merchant_source)
        if (sender_key.isEmpty()) return
        val senders = LinkedHashMap(data.senders)
        senders[sender_key] = ReceiptSenderMemory(clean_merchant, timestamp)
        persist(ReceiptReviewData(data.emails, senders))
    }

    fun confirm(email_id: String) {
        update_review(email_id, ReceiptReviewPatch(confirmed = true, hidden = false))
    }

    fun hide(email_id: String) {
        update_review(email_id, ReceiptReviewPatch(hidden = true))
    }

    private fun update_review(
        email_id: String,
        patch: ReceiptReviewPatch,
        replace_fields: Boolean = false,
    ): ReceiptReviewData {
        val key = sanitize_review_key(email_id)
        if (key.isEmpty()) return load()
        val data = load()
        val current = data.emails[key]
        val merged = ReceiptEmailReview(
            merchant_name = if (replace_fields) patch.merchant_name else patch.merchant_name ?: current?.merchant_name,
            order_id = if (replace_fields) patch.order_id else patch.order_id ?: current?.order_id,
            confirmed = patch.confirmed ?: current?.confirmed ?: false,
            hidden = patch.hidden ?: current?.hidden ?: false,
            updated_at = now(),
        )
        val emails = LinkedHashMap(data.emails)
        emails[key] = merged
        val next = ReceiptReviewData(emails, data.senders)
        persist(next)
        return next
    }

    private fun persist(data: ReceiptReviewData) {
        storage.write(serialize_review_data(prune_review_data(data)))
    }
}
