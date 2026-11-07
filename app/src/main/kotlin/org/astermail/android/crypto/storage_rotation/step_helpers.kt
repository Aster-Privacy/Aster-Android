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

package org.astermail.android.crypto.storage_rotation

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

const val STORAGE_REKEY_PATH = "/core/v1/auth/me/storage-rekey"
const val USER_REKEY_PATH = "/core/v1/auth/me/rekey"
const val MAIL_TOKENS_PATH = "/core/v1/auth/me/storage-rekey/mail-tokens"
const val MAX_FULL_LIST_PASSES = 3
const val ID_PAGE_LIMIT = 500
const val MAX_ID_PAGES = 10_000
const val MAX_BATCH_CHARS = 4 * 1024 * 1024

enum class PageWalk { DONE, UNPAGED, MISSING }

enum class BatchOutcome { OK, CONFLICT, REJECTED }

enum class ItemOutcome { OK, CONFLICT, REJECTED, SKIPPED }

data class BatchReply(val outcome: BatchOutcome, val data: JsonElement?)

fun JsonElement?.as_object(): JsonObject? = this as? JsonObject

fun JsonElement?.as_array(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

fun JsonObject?.str(key: String): String? {
    val value = this?.get(key) as? JsonPrimitive ?: return null
    return if (value.isString) value.contentOrNull else null
}

fun JsonObject?.bool(key: String): Boolean? {
    val value = this?.get(key) as? JsonPrimitive ?: return null
    return if (value.isString) null else value.booleanOrNull
}

fun JsonObject?.long(key: String): Long? {
    val value = this?.get(key) as? JsonPrimitive ?: return null
    return if (value.isString) null else value.longOrNull
}

fun JsonObject?.objects(key: String): List<JsonObject> = this?.get(key).as_array().mapNotNull { it as? JsonObject }

fun JsonObject?.strings(key: String): List<String> = this?.get(key).as_array().mapNotNull {
    (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull
}

fun json_object(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    for ((key, value) in pairs) {
        val element: JsonElement = when (value) {
            null -> continue
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
        put(key, element)
    }
}

fun JsonObject.plus_fields(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(this + json_object(*pairs))

fun url_component(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

fun is_missing_status(status: Int?): Boolean =
    status != null && status in 400..499 && status != 401 && status != 403 && status != 429

suspend fun get_optional(ctx: ResealContext, path: String, what: String): JsonElement? {
    val reply = request_with_backoff(ctx) { ctx.api.get(path) }
    val data = reply.data
    if (!reply.error && data != null && data !is JsonNull) return data
    if (reply.error && is_missing_status(reply.status)) return null
    throw ResealInterrupted("$what:${reply.status ?: "network"}")
}

suspend fun get_required(ctx: ResealContext, path: String, what: String): JsonElement =
    require_ok(request_with_backoff(ctx) { ctx.api.get(path) }, what)

fun id_page_path(path: String, cursor: String?): String {
    val separator = if (path.contains("?")) "&" else "?"
    return "$path${separator}cursor=${url_component(cursor ?: "")}&limit=$ID_PAGE_LIMIT"
}

suspend fun walk_id_pages(
    ctx: ResealContext,
    path: String,
    what: String,
    start_cursor: String?,
    optional: Boolean = false,
    visit: suspend (JsonObject, String?) -> Unit,
): PageWalk {
    var cursor = start_cursor
    repeat(MAX_ID_PAGES) {
        val target = id_page_path(path, cursor)
        val raw = if (optional) get_optional(ctx, target, what) else get_required(ctx, target, what)
        if (raw == null) return PageWalk.MISSING
        val data = raw.as_object() ?: JsonObject(emptyMap())
        val has_more = data.bool("has_more")
        val next = if (has_more == true) data.str("next_cursor") else null
        if (has_more == null || (has_more && next == null) || (next != null && next == cursor)) {
            visit(data, null)
            mark_incomplete(ctx, what)
            return PageWalk.UNPAGED
        }
        visit(data, next)
        if (next == null) return PageWalk.DONE
        if (ctx.is_cancelled()) throw ResealInterrupted("cancelled")
        cursor = next
    }
    throw ResealInterrupted("$what:page_limit")
}

suspend fun collect_id_pages(
    ctx: ResealContext,
    path: String,
    list_key: String,
    what: String,
    optional: Boolean = false,
): List<JsonObject> {
    val rows = ArrayList<JsonObject>()
    walk_id_pages(ctx, path, what, null, optional) { data, _ -> rows.addAll(data.objects(list_key)) }
    return rows
}

sealed class SealedState {
    object Current : SealedState()
    object Unreadable : SealedState()
    class Stale(val plaintext: ByteArray) : SealedState()
}

fun classify_sealed(keys: ResealKeys, ciphertext: String?, nonce: String?): SealedState {
    if (opens_under_new(keys, ciphertext, nonce)) return SealedState.Current
    val plaintext = open_under_old(keys, ciphertext, nonce)
    return if (plaintext != null) SealedState.Stale(plaintext) else SealedState.Unreadable
}

sealed class ResealResult {
    object Current : ResealResult()
    object Unreadable : ResealResult()
    object Absent : ResealResult()
    data class Sealed(val value: SealedValue) : ResealResult()
}

fun reseal_field(keys: ResealKeys, ciphertext: String?, nonce: String?): ResealResult {
    if (ciphertext.isNullOrEmpty() || nonce.isNullOrEmpty()) return ResealResult.Absent
    return when (val sealed = classify_sealed(keys, ciphertext, nonce)) {
        SealedState.Current -> ResealResult.Current
        SealedState.Unreadable -> ResealResult.Unreadable
        is SealedState.Stale -> try {
            ResealResult.Sealed(seal_under_new(keys, sealed.plaintext))
        } finally {
            sealed.plaintext.fill(0)
        }
    }
}

fun tally_untouched(ctx: ResealContext, results: List<ResealResult>) {
    if (results.any { it == ResealResult.Unreadable }) ctx.counts.unreadable += 1
    else if (results.none { it is ResealResult.Sealed }) ctx.counts.current += 1
}

fun decode_text(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

private val loose_json = Json { ignoreUnknownKeys = true; isLenient = false }

fun parse_json_object(bytes: ByteArray): JsonObject? =
    runCatching { loose_json.parseToJsonElement(decode_text(bytes)) as? JsonObject }.getOrNull()

fun parse_json_text(text: String): JsonElement? = runCatching { loose_json.parseToJsonElement(text) }.getOrNull()

fun normalize_email(email: String): String = email.lowercase().trim()

suspend fun pause_between_batches(ctx: ResealContext) {
    ctx.sleep(BATCH_PAUSE_MS)
    if (ctx.is_cancelled()) throw ResealInterrupted("cancelled")
}

suspend fun send_batch_reply(ctx: ResealContext, what: String, send: suspend () -> ApiReply): BatchReply {
    val reply = request_with_backoff(ctx, send)
    if (!reply.error) return BatchReply(BatchOutcome.OK, reply.data)
    if (is_conflict_reply(reply)) return BatchReply(BatchOutcome.CONFLICT, reply.data)
    val status = reply.status
    if (status == null || status == 429 || status >= 500 || status == 401 || status == 403) {
        throw ResealInterrupted("$what:${status ?: "network"}")
    }
    return BatchReply(BatchOutcome.REJECTED, reply.data)
}

suspend fun send_batch(ctx: ResealContext, what: String, send: suspend () -> ApiReply): BatchOutcome =
    send_batch_reply(ctx, what, send).outcome

fun skipped_ids(data: JsonElement?): Set<String> {
    val record = data.as_object() ?: return emptySet()
    val out = HashSet<String>()
    record["skipped"].as_object()?.values?.forEach { value ->
        value.as_array().forEach { id -> (id as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let(out::add) }
    }
    out.addAll(record.strings("skipped_ids"))
    return out
}

fun default_item_id(item: JsonObject): String? = item.str("id")

suspend fun send_items_splitting(
    ctx: ResealContext,
    items: List<JsonObject>,
    what: String,
    on_failed_item: ((JsonObject, ItemOutcome) -> Unit)? = null,
    id_of: (JsonObject) -> String? = ::default_item_id,
    send: suspend (List<JsonObject>) -> ApiReply,
): List<JsonObject> {
    if (items.isEmpty()) return emptyList()
    val reply = send_batch_reply(ctx, what) { send(items) }
    if (reply.outcome == BatchOutcome.OK) {
        val skipped = skipped_ids(reply.data)
        val written = ArrayList<JsonObject>()
        for (item in items) {
            val id = id_of(item)
            if (id != null && id in skipped) {
                ctx.counts.skipped += 1
                on_failed_item?.invoke(item, ItemOutcome.SKIPPED)
            } else {
                written.add(item)
            }
        }
        ctx.counts.resealed += written.size
        return written
    }
    if (items.size == 1) {
        if (reply.outcome == BatchOutcome.CONFLICT) ctx.counts.conflicts += 1 else ctx.counts.rejected += 1
        on_failed_item?.invoke(
            items[0],
            if (reply.outcome == BatchOutcome.CONFLICT) ItemOutcome.CONFLICT else ItemOutcome.REJECTED,
        )
        return emptyList()
    }
    val written = ArrayList<JsonObject>()
    for (item in items) {
        written.addAll(send_items_splitting(ctx, listOf(item), what, on_failed_item, id_of, send))
    }
    return written
}

suspend fun post_storage_rekey(
    ctx: ResealContext,
    category: String,
    items: List<JsonObject>,
    on_failed_item: ((JsonObject, ItemOutcome) -> Unit)? = null,
): List<JsonObject> = send_items_splitting(ctx, items, "storage_rekey:$category", on_failed_item) { batch ->
    ctx.api.post(STORAGE_REKEY_PATH, json_object(category to JsonArray(batch)))
}

fun <T> chunk_by_chars(items: List<T>, max_items: Int, size: (T) -> Int): List<List<T>> {
    val out = ArrayList<List<T>>()
    var current = ArrayList<T>()
    var chars = 0
    for (item in items) {
        val item_chars = size(item)
        if (current.isNotEmpty() && (current.size >= max_items || chars + item_chars > MAX_BATCH_CHARS)) {
            out.add(current)
            current = ArrayList()
            chars = 0
        }
        current.add(item)
        chars += item_chars
    }
    if (current.isNotEmpty()) out.add(current)
    return out
}

fun finish_step(ctx: ResealContext, progress: StepProgress, incomplete_before: Int): StepResult {
    if (incomplete_count(ctx) > incomplete_before) {
        progress.cursor = null
        ctx.persist()
        return StepResult.INCOMPLETE
    }
    progress.done = true
    progress.cursor = null
    ctx.persist()
    return StepResult.DONE
}

fun mark_step_done(ctx: ResealContext, progress: StepProgress): StepResult {
    progress.done = true
    progress.cursor = null
    ctx.persist()
    return StepResult.DONE
}

suspend fun write_if_unchanged(
    ctx: ResealContext,
    what: String,
    still_same: suspend () -> Boolean,
    send: suspend () -> ApiReply,
): BatchOutcome {
    if (!still_same()) {
        ctx.counts.conflicts += 1
        return BatchOutcome.CONFLICT
    }
    val outcome = send_batch(ctx, what, send)
    when (outcome) {
        BatchOutcome.OK -> ctx.counts.resealed += 1
        BatchOutcome.CONFLICT -> ctx.counts.conflicts += 1
        BatchOutcome.REJECTED -> ctx.counts.rejected += 1
    }
    return outcome
}
