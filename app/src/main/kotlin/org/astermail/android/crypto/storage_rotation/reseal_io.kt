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

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

const val MAX_REQUEST_ATTEMPTS = 5
const val BASE_BACKOFF_MS = 1000L
const val MAX_BACKOFF_MS = 30_000L
const val BATCH_PAUSE_MS = 150L

data class ApiReply(
    val status: Int?,
    val data: JsonElement? = null,
    val code: String? = null,
    val retry_after_secs: Long? = null,
) {
    val error: Boolean get() = status == null || status !in 200..299
}

interface ResealApi {
    suspend fun get(path: String): ApiReply
    suspend fun post(path: String, body: JsonElement): ApiReply
    suspend fun put(path: String, body: JsonElement): ApiReply
}

@Serializable
data class HashMove(
    val from_hash: String,
    val to_hash: String,
    var cursor: String? = null,
)

@Serializable
data class StepProgress(
    var done: Boolean = false,
    var cursor: String? = null,
)

@Serializable
data class ResealProgress(
    val key_id: String,
    val steps: MutableMap<String, StepProgress> = mutableMapOf(),
    val hash_moves: MutableList<HashMove> = mutableListOf(),
)

interface ProgressStore {
    fun load(): ResealProgress?
    fun save(progress: ResealProgress)
    fun clear()
}

private val progress_json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun encode_progress(progress: ResealProgress): String = progress_json.encodeToString(ResealProgress.serializer(), progress)

fun decode_progress(raw: String?): ResealProgress? {
    if (raw.isNullOrBlank()) return null
    return runCatching { progress_json.decodeFromString(ResealProgress.serializer(), raw) }.getOrNull()
}

class MemoryProgressStore(initial: ResealProgress? = null) : ProgressStore {
    private var stored: String? = initial?.let(::encode_progress)
    override fun load(): ResealProgress? = decode_progress(stored)
    override fun save(progress: ResealProgress) {
        stored = encode_progress(progress)
    }
    override fun clear() {
        stored = null
    }
    fun snapshot(): ResealProgress? = decode_progress(stored)
}

class StepCounts {
    var resealed = 0
    var current = 0
    var unreadable = 0
    var conflicts = 0
    var rejected = 0
    var deleted = 0
    var skipped = 0

    override fun toString(): String =
        "resealed=$resealed current=$current unreadable=$unreadable conflicts=$conflicts " +
            "rejected=$rejected deleted=$deleted skipped=$skipped"
}

open class ResealInterrupted(message: String) : Exception(message)

class ResealDeferred(val reason: String, val retry_after_secs: Long) : ResealInterrupted(reason)

enum class StepResult { DONE, INTERRUPTED, INCOMPLETE }

interface ResealStep {
    val name: String
    suspend fun run(ctx: ResealContext): StepResult
}

class ResealContext(
    val keys: ResealKeys,
    val api: ResealApi,
    val progress: ResealProgress,
    val persist: () -> Unit,
    val sleep: suspend (Long) -> Unit = { delay(it) },
    val is_cancelled: () -> Boolean = { false },
    val still_current: (suspend () -> Boolean)? = null,
    val counts: StepCounts = StepCounts(),
    val incomplete: MutableList<String> = mutableListOf(),
)

fun mark_incomplete(ctx: ResealContext, what: String) {
    ctx.incomplete.add(what)
}

fun incomplete_count(ctx: ResealContext): Int = ctx.incomplete.size

private fun is_transient(reply: ApiReply): Boolean {
    if (!reply.error) return false
    val status = reply.status ?: return true
    return status == 429 || status >= 500
}

fun backoff_delay(reply: ApiReply, attempt: Int): Long {
    val retry_after = reply.retry_after_secs
    if (retry_after != null && retry_after > 0) return minOf(retry_after * 1000, MAX_BACKOFF_MS * 4)
    return minOf(BASE_BACKOFF_MS shl attempt, MAX_BACKOFF_MS)
}

suspend fun request_with_backoff(ctx: ResealContext, send: suspend () -> ApiReply): ApiReply {
    var reply = send()
    for (attempt in 1 until MAX_REQUEST_ATTEMPTS) {
        if (!is_transient(reply)) return reply
        if (ctx.is_cancelled()) return reply
        ctx.sleep(backoff_delay(reply, attempt - 1))
        reply = send()
    }
    return reply
}

fun require_ok(reply: ApiReply, what: String): JsonElement {
    val data = reply.data
    if (reply.error || data == null) throw ResealInterrupted("$what:${reply.status ?: "network"}")
    return data
}

fun is_conflict_reply(reply: ApiReply): Boolean =
    (reply.status == 409 && reply.code != STORAGE_REKEY_OLD_CLIENT_CODE) || reply.code == "CONFLICT"

fun step_progress(ctx: ResealContext, name: String): StepProgress =
    ctx.progress.steps.getOrPut(name) { StepProgress() }

fun reply_code(body: JsonElement?): String? =
    ((body as? JsonObject)?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
