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

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.mail.AccountDataConversion
import org.astermail.android.mail.LevelTwoEnvelope
import org.astermail.android.mail.SentMailResealCrypto
import org.astermail.android.storage.SessionKeyStore

const val ENVELOPE_V2_STEP = "envelope_v2"
const val ENVELOPE_V2_DRAFT_STEP = "envelope_v2_draft"
const val SENT_ITEM_TYPE = "sent"
const val DRAFT_ITEM_TYPE = "draft"

enum class EnvelopeV2Scope(val step_suffix: String) {
    BY_TYPE(""),
    TRASH("_trash"),
    SPAM("_spam"),
}
const val ENVELOPE_V2_PATH = "$STORAGE_REKEY_PATH/envelope-v2"
const val SENT_MESSAGES_PATH = "/mail/v1/messages"
const val ATTACHMENT_META_BATCH_PATH = "/mail/v1/attachments/meta/batch"
const val ENVELOPE_V2_PAGE_LIMIT = 50
const val ENVELOPE_V2_MAX_ITEMS_PER_RUN = 200
const val ENVELOPE_CHANGED_CODE = "ENVELOPE_CHANGED"
const val ENVELOPE_V2_UPGRADE_REQUIRED_CODE = "CLIENT_UPGRADE_REQUIRED"
const val ENVELOPE_V2_NOT_ADMITTED_REASON = "level2_not_admitted"
const val ENVELOPE_V2_UPGRADE_REASON = "upgrade_required"
const val ENVELOPE_V2_NOT_DUE_REASON = "not_due"
const val ENVELOPE_V2_RESCAN = "envelope_v2_rescan"
const val ENVELOPE_V2_RESCAN_AFTER_MS = 7L * 24L * 60L * 60L * 1000L
const val ENVELOPE_V2_FIRST_RETRY_MS = 60L * 60L * 1000L
const val ENVELOPE_V2_MAX_RETRY_MS = ENVELOPE_V2_RESCAN_AFTER_MS

data class EnvelopeV2Rescan(val next_at_ms: Long, val retry_delay_ms: Long)

fun read_envelope_v2_rescan(progress: ResealProgress): EnvelopeV2Rescan? {
    val parts = progress.steps[ENVELOPE_V2_RESCAN]?.cursor?.split(":") ?: return null
    if (parts.size != 2) return null
    val next_at = parts[0].toLongOrNull() ?: return null
    val delay = parts[1].toLongOrNull() ?: return null
    return EnvelopeV2Rescan(next_at, delay)
}

fun next_envelope_v2_retry_delay(previous: EnvelopeV2Rescan?): Long {
    val prior = previous?.retry_delay_ms?.takeIf { it > 0 } ?: return ENVELOPE_V2_FIRST_RETRY_MS
    if (prior >= ENVELOPE_V2_MAX_RETRY_MS / 2) return ENVELOPE_V2_MAX_RETRY_MS
    return (prior * 2).coerceIn(ENVELOPE_V2_FIRST_RETRY_MS, ENVELOPE_V2_MAX_RETRY_MS)
}

internal fun refuse_if_unavailable(reply: ApiReply) {
    if (!reply.error) return
    if (reply.status == 409 && reply.code == VAULT_LEVEL2_NOT_ADMITTED_CODE) {
        throw ResealDeferred(ENVELOPE_V2_NOT_ADMITTED_REASON, REKEY_NOT_ADMITTED_WAIT_SECS)
    }
    if (reply.status == 426 || reply.code == ENVELOPE_V2_UPGRADE_REQUIRED_CODE) {
        throw ResealDeferred(ENVELOPE_V2_UPGRADE_REASON, REKEY_NOT_ADMITTED_WAIT_SECS)
    }
}

suspend fun request_level2_admission(api: ResealApi): Boolean {
    val reply = try {
        api.post(ENVELOPE_V2_PATH, JsonObject(emptyMap()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        return false
    }
    if (reply.error) return false
    return reply.data.as_object().bool("level2_admitted") == true
}

interface EnvelopeV2Crypto {
    fun is_v2(sealed_b64: String): Boolean
    fun open_v1(sealed_b64: String): ByteArray?
    fun seal_v2(plaintext: ByteArray): String?
}

class SessionEnvelopeV2Crypto(private val store: SessionKeyStore) : EnvelopeV2Crypto {

    override fun is_v2(sealed_b64: String): Boolean {
        val opened = LevelTwoEnvelope.open_with_store(sealed_b64, store) ?: return false
        opened.fill(0)
        return true
    }

    override fun open_v1(sealed_b64: String): ByteArray? {
        val passphrase = store.get_passphrase() ?: return null
        return try {
            SentMailResealCrypto.open(sealed_b64, passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    override fun seal_v2(plaintext: ByteArray): String? {
        val key = LevelTwoEnvelope.write_key(store) ?: return null
        return try {
            LevelTwoEnvelope.seal_b64(plaintext, key)
        } finally {
            key.fill(0)
        }
    }
}

internal class WorkBudget(val limit: Int) {
    var used = 0

    fun spend() {
        used += 1
    }

    fun exhausted(): Boolean = used >= limit
}

private fun decoded_size(value: String?): Int? =
    value?.let { runCatching { Base64.getMimeDecoder().decode(it.trim()).size }.getOrNull() }

private fun digest_of(vararg parts: String?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (part in parts) {
        val bytes = (part ?: "").toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(bytes)
    }
    return Base64.getEncoder().encodeToString(digest.digest())
}

private fun may_have_attachments(item: JsonObject): Boolean =
    item.bool("has_attachments") == true ||
        (item.long("attachment_count") ?: 0) > 0 ||
        (item["has_attachments"] == null && item["attachment_count"] == null)

class EnvelopeV2Step(
    private val crypto: EnvelopeV2Crypto,
    private val max_items: Int = ENVELOPE_V2_MAX_ITEMS_PER_RUN,
    private val item_type: String = SENT_ITEM_TYPE,
    private val item_in_use: suspend (String) -> Boolean = { false },
    private val scope: EnvelopeV2Scope = EnvelopeV2Scope.BY_TYPE,
) : ResealStep {

    override val name: String =
        (if (item_type == SENT_ITEM_TYPE) ENVELOPE_V2_STEP else ENVELOPE_V2_DRAFT_STEP) + scope.step_suffix

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, name)
        if (progress.done) return StepResult.DONE
        val incomplete_before = incomplete_count(ctx)
        val budget = WorkBudget(max_items)
        var cursor = progress.cursor
        repeat(MAX_ID_PAGES) {
            if (ctx.is_cancelled()) throw ResealInterrupted("$name:cancelled")
            val data = get_required(ctx, page_path(cursor), "$name:list").as_object() ?: JsonObject(emptyMap())
            for (item in data.objects("items")) {
                if (budget.exhausted()) {
                    progress.cursor = cursor
                    ctx.persist()
                    return StepResult.INTERRUPTED
                }
                convert_item(ctx, item, budget)
            }
            val has_more = data.bool("has_more") == true
            val next = data.str("next_cursor")
            if (!has_more) return finish_step(ctx, progress, incomplete_before)
            if (next.isNullOrEmpty() || next == cursor) {
                mark_incomplete(ctx, name)
                return finish_step(ctx, progress, incomplete_before)
            }
            cursor = next
            progress.cursor = cursor
            ctx.persist()
            pause_between_batches(ctx)
        }
        throw ResealInterrupted("$name:page_limit")
    }

    private fun page_path(cursor: String?): String {
        val filter = when (scope) {
            EnvelopeV2Scope.BY_TYPE -> "item_type=${url_component(item_type)}"
            EnvelopeV2Scope.TRASH -> "is_trashed=true"
            EnvelopeV2Scope.SPAM -> "is_spam=true"
        }
        val base = "$SENT_MESSAGES_PATH?$filter&include_envelope=true&skip_total=true&limit=$ENVELOPE_V2_PAGE_LIMIT"
        return if (cursor.isNullOrEmpty()) base else "$base&cursor=${url_component(cursor)}"
    }

    internal suspend fun convert_listed(ctx: ResealContext, item: JsonObject, budget: WorkBudget) {
        if (item.str("item_type") != item_type) return
        convert_item(ctx, item, budget)
    }

    private suspend fun convert_item(ctx: ResealContext, item: JsonObject, budget: WorkBudget) {
        val id = item.str("id") ?: return
        val row_type = item.str("item_type")
        if (scope == EnvelopeV2Scope.BY_TYPE && row_type != null && row_type != item_type) return
        if (scope != EnvelopeV2Scope.BY_TYPE && row_type != item_type) return
        val nonce = item.str("envelope_nonce")
        val is_v1 = SentMailResealCrypto.is_sentinel_nonce(nonce)
        val is_v2 = LevelTwoEnvelope.is_marker_b64(nonce)
        if (!is_v1 && !is_v2) return
        if (is_in_use(id)) {
            ctx.counts.skipped += 1
            mark_incomplete(ctx, "$name:in_use")
            return
        }
        if (may_have_attachments(item)) convert_metas(ctx, id, budget)
        if (is_v2) {
            ctx.counts.current += 1
            return
        }
        convert_envelope(ctx, id, item.str("encrypted_envelope"), budget)
    }

    private suspend fun convert_envelope(
        ctx: ResealContext,
        id: String,
        sealed: String?,
        budget: WorkBudget,
    ) {
        if (sealed.isNullOrEmpty()) return
        val expected_sha256 = LevelTwoEnvelope.stored_sha256_hex(sealed)
        if (expected_sha256 == null) {
            ctx.counts.skipped += 1
            return
        }
        budget.spend()
        val replacement = rewrap(ctx, sealed) ?: return
        val reply = ctx.api.put(
            "$SENT_MESSAGES_PATH/${url_component(id)}",
            json_object(
                "encrypted_envelope" to replacement,
                "envelope_nonce" to LevelTwoEnvelope.marker_b64(),
                "expected_envelope_sha256" to expected_sha256,
            ),
        )
        refuse_if_unavailable(reply)
        val status = reply.status
        when {
            !reply.error -> ctx.counts.resealed += 1
            status == 409 -> {
                ctx.counts.conflicts += 1
                mark_incomplete(ctx, "$name:envelope")
            }
            status == 404 -> ctx.counts.skipped += 1
            status == null || status == 401 || status == 403 || status == 429 || status >= 500 ->
                throw ResealInterrupted("$name:envelope:${status ?: "network"}")
            else -> ctx.counts.rejected += 1
        }
    }

    private suspend fun convert_metas(ctx: ResealContext, mail_id: String, budget: WorkBudget) {
        for (meta in fetch_metas(ctx, mail_id)) {
            val meta_id = meta.str("id") ?: continue
            val sealed = meta.str("encrypted_meta")
            val meta_nonce = meta.str("meta_nonce")
            if (sealed.isNullOrEmpty()) continue
            if (SentMailResealCrypto.is_pgp_armored_b64(sealed)) continue
            if (AccountDataConversion.is_legacy_meta_nonce(meta_nonce)) continue
            if (crypto.is_v2(sealed)) {
                ctx.counts.current += 1
                continue
            }
            budget.spend()
            val replacement = rewrap(ctx, sealed) ?: continue
            val expected = digest_of(sealed, meta_nonce)
            val outcome = write_if_unchanged(
                ctx,
                "$name:attachment_meta",
                still_same = { fetch_meta_digest(ctx, mail_id, meta_id) == expected },
            ) {
                ctx.api.put(
                    "/mail/v1/attachments/${url_component(meta_id)}/meta",
                    json_object(
                        "encrypted_meta" to replacement,
                        "meta_nonce" to LevelTwoEnvelope.meta_marker_b64(),
                    ),
                ).also(::refuse_if_unavailable)
            }
            if (outcome == BatchOutcome.CONFLICT) mark_incomplete(ctx, "$name:attachment_meta")
        }
    }

    private suspend fun is_in_use(id: String): Boolean = try {
        item_in_use(id)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        true
    }

    private fun rewrap(ctx: ResealContext, sealed: String): String? {
        val plaintext = crypto.open_v1(sealed)
        if (plaintext == null) {
            ctx.counts.unreadable += 1
            return null
        }
        val replacement = try {
            crypto.seal_v2(plaintext)
        } finally {
            plaintext.fill(0)
        }
        if (replacement == null || decoded_size(replacement) != decoded_size(sealed)) {
            ctx.counts.skipped += 1
            return null
        }
        return replacement
    }

    private suspend fun fetch_metas(ctx: ResealContext, mail_id: String): List<JsonObject> {
        val reply = request_with_backoff(ctx) {
            ctx.api.post(
                ATTACHMENT_META_BATCH_PATH,
                json_object("mail_ids" to JsonArray(listOf(JsonPrimitive(mail_id)))),
            )
        }
        if (reply.error && is_missing_status(reply.status)) return emptyList()
        val data = require_ok(reply, "$name:attachment_meta")
        return data.as_object()?.get("items").as_object().objects(mail_id)
    }

    private suspend fun fetch_meta_digest(ctx: ResealContext, mail_id: String, meta_id: String): String? {
        val meta = fetch_metas(ctx, mail_id).firstOrNull { it.str("id") == meta_id } ?: return null
        return digest_of(meta.str("encrypted_meta"), meta.str("meta_nonce"))
    }
}

suspend fun run_envelope_v2_sweep(
    view: VaultKeyView,
    api: ResealApi,
    store: ProgressStore,
    crypto: EnvelopeV2Crypto,
    is_cancelled: () -> Boolean = { false },
    sleep: suspend (Long) -> Unit = { delay(it) },
    max_items: Int = ENVELOPE_V2_MAX_ITEMS_PER_RUN,
    draft_in_use: suspend (String) -> Boolean = { true },
    now_ms: () -> Long = System::currentTimeMillis,
    on_not_admitted: () -> Unit = {},
): ResealRunResult {
    val counts = StepCounts()
    val keys = build_reseal_keys(view)
        ?: return ResealRunResult(ResealRunStatus.NOT_NEEDED, counts, "keys_unavailable")
    try {
        val stored = runCatching { store.load() }.getOrNull()
        val progress = if (stored != null && stored.key_id == keys.key_id) stored else ResealProgress(keys.key_id)
        val rescan = read_envelope_v2_rescan(progress)
        if (rescan != null && rescan.next_at_ms >= 0) {
            if (now_ms() < rescan.next_at_ms) {
                return ResealRunResult(ResealRunStatus.DONE, counts, ENVELOPE_V2_NOT_DUE_REASON)
            }
            if (rescan.retry_delay_ms == 0L) progress.steps.clear()
            progress.steps[ENVELOPE_V2_RESCAN] = StepProgress(cursor = "-1:${rescan.retry_delay_ms}")
            runCatching { store.save(progress) }
        }
        val ctx = ResealContext(
            keys = keys,
            api = api,
            progress = progress,
            persist = { store.save(progress) },
            sleep = sleep,
            is_cancelled = is_cancelled,
            counts = counts,
        )
        val steps = listOf(
            EnvelopeV2Step(crypto, max_items, SENT_ITEM_TYPE),
            EnvelopeV2Step(crypto, max_items, SENT_ITEM_TYPE, scope = EnvelopeV2Scope.TRASH),
            EnvelopeV2Step(crypto, max_items, SENT_ITEM_TYPE, scope = EnvelopeV2Scope.SPAM),
            EnvelopeV2Step(crypto, max_items, DRAFT_ITEM_TYPE, draft_in_use),
            EnvelopeV2Step(crypto, max_items, DRAFT_ITEM_TYPE, draft_in_use, EnvelopeV2Scope.TRASH),
        )
        for (step in steps) {
            if (step.run(ctx) == StepResult.INTERRUPTED) {
                return ResealRunResult(ResealRunStatus.INTERRUPTED, counts, step.name)
            }
        }
        val pass_complete = ctx.incomplete.isEmpty() && steps.all { progress.steps[it.name]?.done == true }
        val retry_delay = if (pass_complete) 0L else next_envelope_v2_retry_delay(rescan)
        val wait = if (pass_complete) ENVELOPE_V2_RESCAN_AFTER_MS else retry_delay
        progress.steps[ENVELOPE_V2_RESCAN] = StepProgress(cursor = "${now_ms() + wait}:$retry_delay")
        runCatching { store.save(progress) }
        return ResealRunResult(ResealRunStatus.DONE, counts, incomplete = ctx.incomplete.toList())
    } catch (deferred: ResealDeferred) {
        if (deferred.reason == ENVELOPE_V2_NOT_ADMITTED_REASON) runCatching(on_not_admitted)
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
