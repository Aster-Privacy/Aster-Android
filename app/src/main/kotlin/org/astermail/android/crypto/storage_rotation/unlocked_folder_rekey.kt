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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.astermail.android.mail.SentMailResealCrypto

const val UNLOCKED_FOLDER_MAX_ITEMS_PER_UNLOCK = 50
const val UNLOCKED_FOLDER_MAX_PAGES_PER_UNLOCK = 20
const val UNLOCKED_FOLDER_PAGE_LIMIT = 50

fun unlocked_folder_candidates(items: List<JsonObject>): List<JsonObject> =
    items.filter { item ->
        val type = item.str("item_type")
        (type == SENT_ITEM_TYPE || type == DRAFT_ITEM_TYPE) &&
            !item.str("id").isNullOrEmpty() &&
            !item.str("encrypted_envelope").isNullOrEmpty() &&
            SentMailResealCrypto.is_sentinel_nonce(item.str("envelope_nonce"))
    }

suspend fun run_unlocked_folder_rekey(
    keys_source: () -> ResealKeys?,
    api: ResealApi,
    crypto: EnvelopeV2Crypto,
    items: List<JsonObject>,
    still_unlocked: () -> Boolean,
    draft_in_use: suspend (String) -> Boolean = { true },
    sleep: suspend (Long) -> Unit = { delay(it) },
    max_items: Int = UNLOCKED_FOLDER_MAX_ITEMS_PER_UNLOCK,
): ResealRunResult {
    val counts = StepCounts()
    val candidates = unlocked_folder_candidates(items)
    if (candidates.isEmpty()) return ResealRunResult(ResealRunStatus.NOT_NEEDED, counts)
    if (!still_unlocked()) return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, "folder_locked")
    val keys = keys_source() ?: return ResealRunResult(ResealRunStatus.NOT_NEEDED, counts, "keys_unavailable")
    try {
        val ctx = ResealContext(
            keys = keys,
            api = api,
            progress = ResealProgress(keys.key_id),
            persist = {},
            sleep = sleep,
            is_cancelled = { !still_unlocked() },
            counts = counts,
        )
        val sent = EnvelopeV2Step(crypto, max_items, SENT_ITEM_TYPE)
        val draft = EnvelopeV2Step(crypto, max_items, DRAFT_ITEM_TYPE, draft_in_use)
        val budget = WorkBudget(max_items)
        for (item in candidates) {
            if (!still_unlocked()) return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, "folder_locked")
            if (budget.exhausted()) return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, "budget")
            val step = if (item.str("item_type") == DRAFT_ITEM_TYPE) draft else sent
            step.convert_listed(ctx, item, budget)
        }
        return ResealRunResult(ResealRunStatus.DONE, counts, incomplete = ctx.incomplete.toList())
    } catch (deferred: ResealDeferred) {
        return ResealRunResult(
            ResealRunStatus.DEFERRED,
            counts,
            deferred.reason,
            retry_after_secs = deferred.retry_after_secs,
        )
    } catch (interrupted: ResealInterrupted) {
        return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, interrupted.message)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (caught: Throwable) {
        return ResealRunResult(ResealRunStatus.FAILED, counts, caught::class.simpleName ?: "error")
    } finally {
        keys.dispose()
    }
}

@Serializable
data class FolderRekeyCursor(
    val offset: Int = 0,
    val clean: Boolean = true,
    val done: Boolean = false,
)

interface FolderRekeyCursorStore {
    fun load(folder_id: String): FolderRekeyCursor?
    fun save(folder_id: String, cursor: FolderRekeyCursor)
    fun clear()
}

class MemoryFolderRekeyCursorStore : FolderRekeyCursorStore {
    private val cursors = HashMap<String, FolderRekeyCursor>()

    override fun load(folder_id: String): FolderRekeyCursor? = synchronized(cursors) { cursors[folder_id] }

    override fun save(folder_id: String, cursor: FolderRekeyCursor) {
        synchronized(cursors) { cursors[folder_id] = cursor }
    }

    override fun clear() {
        synchronized(cursors) { cursors.clear() }
    }
}

private val folder_cursor_json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun encode_folder_cursors(cursors: Map<String, FolderRekeyCursor>): String =
    folder_cursor_json.encodeToString(MapSerializer(String.serializer(), FolderRekeyCursor.serializer()), cursors)

fun decode_folder_cursors(text: String?): Map<String, FolderRekeyCursor> {
    if (text.isNullOrBlank()) return emptyMap()
    return runCatching {
        folder_cursor_json.decodeFromString(MapSerializer(String.serializer(), FolderRekeyCursor.serializer()), text)
    }.getOrDefault(emptyMap())
}

fun unlocked_folder_page_path(label_token: String, offset: Int): String =
    "$SENT_MESSAGES_PATH?label_token=${url_component(label_token)}&offset=$offset" +
        "&include_envelope=true&skip_total=true&limit=$UNLOCKED_FOLDER_PAGE_LIMIT"

data class FolderRekeyPage(val items: List<JsonObject>, val has_more: Boolean)

private fun keeps_going(result: ResealRunResult): Boolean =
    result.status == ResealRunStatus.DONE || result.status == ResealRunStatus.NOT_NEEDED

class UnlockedFolderRekeyQueue(
    private val scope: CoroutineScope,
    private val session_marker: (String) -> Long?,
    private val run_batch: suspend (items: List<JsonObject>, still_unlocked: () -> Boolean, max_items: Int) -> ResealRunResult,
    private val fetch_page: suspend (folder_id: String, offset: Int) -> FolderRekeyPage? = { _, _ -> null },
    private val cursors: FolderRekeyCursorStore = MemoryFolderRekeyCursorStore(),
    private val max_per_unlock: Int = UNLOCKED_FOLDER_MAX_ITEMS_PER_UNLOCK,
    private val max_pages_per_unlock: Int = UNLOCKED_FOLDER_MAX_PAGES_PER_UNLOCK,
) {
    private class FolderBudget(val marker: Long, var used: Int, var pages: Int, var job: Job?)

    private val folders = HashMap<String, FolderBudget>()

    fun offer(folder_id: String, items: List<JsonObject>): Job? {
        val listed = unlocked_folder_candidates(items)
        val marker = session_marker(folder_id) ?: return null
        val stored = runCatching { cursors.load(folder_id) }.getOrNull() ?: FolderRekeyCursor()
        if (listed.isEmpty() && stored.done) return null
        val entry: FolderBudget
        synchronized(folders) {
            val known = folders[folder_id]
            entry = if (known == null || known.marker != marker) {
                FolderBudget(marker, 0, 0, null).also { folders[folder_id] = it }
            } else {
                known
            }
            if (entry.job?.isActive == true) return null
            if (entry.used >= max_per_unlock) return null
            if (listed.isEmpty() && entry.pages >= max_pages_per_unlock) return null
        }
        val still_unlocked = { session_marker(folder_id) == marker }
        val job = scope.launch {
            try {
                drain(folder_id, listed, entry, still_unlocked)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            }
        }
        synchronized(folders) {
            if (folders[folder_id] === entry) entry.job = job
        }
        return job
    }

    private fun remaining(entry: FolderBudget): Int = synchronized(folders) { max_per_unlock - entry.used }

    private suspend fun convert(entry: FolderBudget, batch: List<JsonObject>, still_unlocked: () -> Boolean): ResealRunResult {
        synchronized(folders) { entry.used += batch.size }
        return run_batch(batch, still_unlocked, batch.size)
    }

    private fun save(folder_id: String, cursor: FolderRekeyCursor) {
        runCatching { cursors.save(folder_id, cursor) }
    }

    private suspend fun drain(
        folder_id: String,
        listed: List<JsonObject>,
        entry: FolderBudget,
        still_unlocked: () -> Boolean,
    ) {
        var cursor = runCatching { cursors.load(folder_id) }.getOrNull() ?: FolderRekeyCursor()
        if (listed.isNotEmpty()) {
            cursor = cursor.copy(clean = false, done = false)
            save(folder_id, cursor)
            val batch = listed.take(remaining(entry))
            if (batch.isEmpty() || !keeps_going(convert(entry, batch, still_unlocked))) return
        }
        while (!cursor.done && still_unlocked()) {
            val budget = remaining(entry)
            if (budget <= 0) return
            val pages_left = synchronized(folders) {
                if (entry.pages >= max_pages_per_unlock) false else {
                    entry.pages += 1
                    true
                }
            }
            if (!pages_left) return
            val page = fetch_page(folder_id, cursor.offset) ?: return
            if (!still_unlocked()) return
            val found = unlocked_folder_candidates(page.items)
            if (found.isNotEmpty()) {
                val batch = found.take(budget)
                val result = convert(entry, batch, still_unlocked)
                if (result.counts.resealed > 0) {
                    cursor = cursor.copy(clean = false)
                    save(folder_id, cursor)
                }
                if (!keeps_going(result) || batch.size < found.size) return
            }
            cursor = when {
                page.has_more && page.items.isNotEmpty() -> cursor.copy(offset = cursor.offset + page.items.size)
                cursor.clean -> FolderRekeyCursor(done = true)
                else -> FolderRekeyCursor()
            }
            save(folder_id, cursor)
        }
    }

    fun forget() {
        synchronized(folders) {
            folders.values.forEach { it.job?.cancel() }
            folders.clear()
        }
    }
}
