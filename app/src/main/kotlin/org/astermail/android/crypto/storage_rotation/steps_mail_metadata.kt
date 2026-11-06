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
import kotlinx.serialization.json.JsonObject

const val MAIL_METADATA_PATH = "/core/v1/auth/me/storage-rekey/mail-metadata"
const val MAIL_METADATA_CONTEXT = "mail-item-metadata"
private const val MAIL_METADATA_BATCH_SIZE = 250

private fun build_metadata_item(ctx: ResealContext, row: JsonObject): JsonObject? {
    val id = row.str("id") ?: return null
    val ciphertext = decode_b64(row.str("encrypted_metadata"))
    val nonce = decode_b64(row.str("metadata_nonce"))
    val hash = row.str("metadata_hash")
    if (ciphertext == null || nonce == null || hash.isNullOrEmpty()) {
        if (!row.str("encrypted_metadata").isNullOrEmpty()) ctx.counts.unreadable += 1
        return null
    }
    val new_key = ctx.keys.metadata_key(MAIL_METADATA_CONTEXT)
    val current = gcm_open(new_key, ciphertext, nonce)
    if (current != null) {
        current.fill(0)
        ctx.counts.current += 1
        return null
    }
    val candidates = (ctx.keys.old_metadata_keys(MAIL_METADATA_CONTEXT) + ctx.keys.old_aes).toMutableList()
    val plaintext = open_with_any(candidates, ciphertext, nonce)
    if (plaintext == null) {
        ctx.counts.unreadable += 1
        return null
    }
    try {
        val sealed = seal_under_new(ctx.keys, plaintext, null, new_key)
        return json_object(
            "id" to id,
            "encrypted_metadata" to sealed.ciphertext,
            "metadata_nonce" to sealed.nonce,
            "expected_hash" to hash,
        )
    } finally {
        plaintext.fill(0)
    }
}

val mail_metadata_step: ResealStep = object : ResealStep {
    override val name = "mail_metadata"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "mail_metadata")
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val walk = walk_id_pages(ctx, MAIL_METADATA_PATH, "mail_metadata_list", progress.cursor, optional = true) { page, next_cursor ->
            val items = ArrayList<JsonObject>()
            for (row in page.objects("items")) {
                build_metadata_item(ctx, row)?.let(items::add)
            }
            for (batch in items.chunked(MAIL_METADATA_BATCH_SIZE)) {
                send_items_splitting(ctx, batch, "storage_rekey:mail_metadata") { entries ->
                    ctx.api.post(MAIL_METADATA_PATH, json_object("items" to JsonArray(entries)))
                }
                pause_between_batches(ctx)
            }
            if (next_cursor != null) {
                progress.cursor = next_cursor
                ctx.persist()
            }
        }
        if (walk == PageWalk.MISSING) mark_incomplete(ctx, "mail_metadata_list")
        return finish_step(ctx, progress, before)
    }
}
