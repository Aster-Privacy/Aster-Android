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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

const val ESCROW_LIST_PATH = "/crypto/v1/ratchet/plaintexts"
const val ESCROW_UPSERT_PATH = "/crypto/v1/ratchet/plaintext"
const val ESCROW_DELETE_PATH = "/crypto/v1/ratchet/plaintexts/delete"
private const val ESCROW_PAGE_SIZE = 500
private const val ESCROW_WRITES_PER_PAUSE = 25
private const val ESCROW_DELETE_BATCH = 500
private const val MAX_ESCROW_PAGES = 10_000
private const val ESCROW_BOUND_PREFIX = "v3:"

fun escrow_aad_v2(message_id: String): ByteArray = "aster.escrow.v2\u0000$message_id".toByteArray(Charsets.UTF_8)

fun escrow_aad_v3(message_key: String): ByteArray = "aster.escrow.v3\u0000$message_key".toByteArray(Charsets.UTF_8)

fun escrow_aad_modes(message_id: String): List<ByteArray?> {
    if (message_id.startsWith(ESCROW_BOUND_PREFIX)) {
        return listOf(escrow_aad_v3(message_id.substring(ESCROW_BOUND_PREFIX.length)))
    }
    return listOf(escrow_aad_v2(message_id), null)
}

sealed class EscrowOpen {
    object Current : EscrowOpen()
    object Unreadable : EscrowOpen()
    class Stale(val plaintext: ByteArray, val aad: ByteArray?) : EscrowOpen()
}

fun open_escrow_row(keys: ResealKeys, message_id: String, ciphertext_b64: String?, nonce_b64: String?): EscrowOpen {
    val ciphertext = decode_b64(ciphertext_b64) ?: return EscrowOpen.Unreadable
    val nonce = decode_b64(nonce_b64) ?: return EscrowOpen.Unreadable
    val modes = escrow_aad_modes(message_id)
    for (aad in modes) {
        val current = gcm_open(keys.new_escrow, ciphertext, nonce, aad)
        if (current != null) {
            current.fill(0)
            return EscrowOpen.Current
        }
    }
    for (aad in modes) {
        val stale = open_with_any(keys.old_escrow, ciphertext, nonce, aad)
        if (stale != null) return EscrowOpen.Stale(stale, aad)
    }
    return EscrowOpen.Unreadable
}

private suspend fun delete_unreadable_escrow(ctx: ResealContext, message_ids: List<String>) {
    if (message_ids.isEmpty()) return
    val still_current = ctx.still_current ?: return
    if (!still_current()) return
    for (batch in message_ids.chunked(ESCROW_DELETE_BATCH)) {
        val body = json_object("message_ids" to JsonArray(batch.map { JsonPrimitive(it) }))
        val outcome = send_batch(ctx, "escrow_delete") { ctx.api.post(ESCROW_DELETE_PATH, body) }
        if (outcome == BatchOutcome.OK) ctx.counts.deleted += batch.size
    }
}

val escrow_step: ResealStep = object : ResealStep {
    override val name = "escrow"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "escrow")
        if (progress.done) return StepResult.DONE
        var cursor = progress.cursor
        repeat(MAX_ESCROW_PAGES) {
            val query = buildString {
                append("paged=true&limit=").append(ESCROW_PAGE_SIZE)
                if (!cursor.isNullOrEmpty()) append("&cursor=").append(url_component(cursor!!))
            }
            val data = get_required(ctx, "$ESCROW_LIST_PATH?$query", "escrow_list").as_object()
            val unreadable = ArrayList<String>()
            var writes = 0
            for (row in data.objects("entries")) {
                val message_id = row.str("message_id") ?: continue
                when (val opened = open_escrow_row(ctx.keys, message_id, row.str("encrypted_plaintext"), row.str("plaintext_nonce"))) {
                    EscrowOpen.Current -> {
                        ctx.counts.current += 1
                        continue
                    }
                    EscrowOpen.Unreadable -> {
                        ctx.counts.unreadable += 1
                        unreadable.add(message_id)
                        continue
                    }
                    is EscrowOpen.Stale -> try {
                        val sealed = seal_under_new(ctx.keys, opened.plaintext, opened.aad, ctx.keys.new_escrow)
                        val body = json_object(
                            "message_id" to message_id,
                            "encrypted_plaintext" to sealed.ciphertext,
                            "plaintext_nonce" to sealed.nonce,
                        )
                        val outcome = send_batch(ctx, "escrow_upsert") { ctx.api.post(ESCROW_UPSERT_PATH, body) }
                        if (outcome == BatchOutcome.OK) ctx.counts.resealed += 1 else ctx.counts.rejected += 1
                    } finally {
                        opened.plaintext.fill(0)
                    }
                }
                writes += 1
                if (writes % ESCROW_WRITES_PER_PAUSE == 0) pause_between_batches(ctx)
            }
            delete_unreadable_escrow(ctx, unreadable)
            cursor = data.str("next_cursor")
            if (cursor.isNullOrEmpty()) {
                return mark_step_done(ctx, progress)
            }
            progress.cursor = cursor
            ctx.persist()
            pause_between_batches(ctx)
        }
        return StepResult.INTERRUPTED
    }
}
