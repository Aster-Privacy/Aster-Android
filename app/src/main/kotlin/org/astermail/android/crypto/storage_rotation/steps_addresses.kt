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

import java.util.IdentityHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

const val ALIAS_HMAC_INFO = "astermail-alias-hmac-v1"
const val DOMAIN_ADDRESS_HMAC_INFO = "astermail-domain-address-hmac-v1"

private const val ALIAS_PAGE_SIZE = 1000
private const val ALIAS_MAX_PAGES = 100
private const val ADDRESS_BATCH_SIZE = 25
private const val MAX_MAIL_TOKEN_PAGES = 10_000
private const val GHOST_LIST_PATH = "/addresses/v1/aliases/ghost"
private const val DELETED_LIST_PATH = "/addresses/v1/aliases/deleted"

private data class AddressEntry(val item: JsonObject, val move: HashMove?)

private data class FieldPlan(val send: SealedValue?, val changed: Boolean)

private data class OpenedLocalPart(val local_part: String, val ciphertext: Any?, val nonce: Any?, val changed: Boolean)

private fun addr_decode_text(bytes: ByteArray): String {
    val text = decode_text(bytes)
    return if (text.startsWith('﻿')) text.substring(1) else text
}

private fun addr_truthy(value: JsonElement?): Boolean = when (value) {
    null, JsonNull -> false
    is JsonObject, is JsonArray -> true
    is JsonPrimitive -> when {
        value.isString -> value.content.isNotEmpty()
        value.booleanOrNull != null -> value.booleanOrNull == true
        else -> value.content.toDoubleOrNull()?.let { it != 0.0 && !it.isNaN() } ?: true
    }
}

private fun addr_template(value: JsonElement?): String = when (value) {
    null -> "undefined"
    JsonNull -> "null"
    is JsonPrimitive -> value.content
    is JsonObject -> "[object Object]"
    is JsonArray -> value.joinToString(",") { if (it is JsonNull) "" else addr_template(it) }
}

private fun addr_uri_component(value: String): String = url_component(value)
    .replace("%21", "!")
    .replace("%7E", "~")
    .replace("%27", "'")
    .replace("%28", "(")
    .replace("%29", ")")

fun normalize_address(local_part: String): String = local_part.lowercase().replace(".", "")

private fun plan_optional_field(ctx: ResealContext, ciphertext: String?, nonce: String?): FieldPlan {
    if (ciphertext.isNullOrEmpty() || nonce.isNullOrEmpty()) return FieldPlan(null, false)
    return when (val result = reseal_field(ctx.keys, ciphertext, nonce)) {
        ResealResult.Absent -> FieldPlan(null, false)
        ResealResult.Current -> FieldPlan(SealedValue(ciphertext, nonce), false)
        ResealResult.Unreadable -> {
            ctx.counts.unreadable += 1
            FieldPlan(SealedValue(ciphertext, nonce), false)
        }
        is ResealResult.Sealed -> FieldPlan(result.value, true)
    }
}

private fun same_hash(a: String?, b: String?): Boolean {
    val left = decode_b64(a)
    val right = decode_b64(b)
    if (left == null || right == null || left.size != right.size) return false
    var diff = 0
    for (i in left.indices) diff = diff or (left[i].toInt() xor right[i].toInt())
    return diff == 0
}

private fun open_local_part(ctx: ResealContext, ciphertext: String?, nonce: String?): OpenedLocalPart? {
    val ct = decode_b64(ciphertext)
    val iv = decode_b64(nonce)
    if (ct != null && iv != null) {
        val current = gcm_open(ctx.keys.new_aes, ct, iv)
        if (current != null) {
            val local_part = addr_decode_text(current)
            current.fill(0)
            return OpenedLocalPart(local_part, ciphertext, nonce, false)
        }
    }
    val sealed = classify_sealed(ctx.keys, ciphertext, nonce)
    if (sealed !is SealedState.Stale) {
        ctx.counts.unreadable += 1
        return null
    }
    try {
        val resealed = seal_under_new(ctx.keys, sealed.plaintext)
        return OpenedLocalPart(addr_decode_text(sealed.plaintext), resealed.ciphertext, resealed.nonce, true)
    } finally {
        sealed.plaintext.fill(0)
    }
}

private fun build_alias_entry(
    ctx: ResealContext,
    alias: JsonObject,
    is_random: Boolean,
    is_retained_primary: Boolean,
): AddressEntry? {
    val encrypted_local_part = alias.str("encrypted_local_part")
    val local_part_nonce = alias.str("local_part_nonce")
    val opened: OpenedLocalPart?
    if (is_random) {
        val raw = decode_b64(encrypted_local_part)
        opened = if (raw != null) {
            OpenedLocalPart(addr_decode_text(raw), alias["encrypted_local_part"], alias["local_part_nonce"], false)
        } else {
            null
        }
        if (opened == null) ctx.counts.unreadable += 1
    } else {
        opened = open_local_part(ctx, encrypted_local_part, local_part_nonce)
    }
    if (opened == null) return null

    val display = plan_optional_field(ctx, alias.str("encrypted_display_name"), alias.str("display_name_nonce"))
    val note = plan_optional_field(ctx, alias.str("encrypted_note"), alias.str("note_nonce"))
    val websites = plan_optional_field(ctx, alias.str("encrypted_websites"), alias.str("websites_nonce"))
    val old_hash = alias.str("alias_address_hash")
    val new_hash = if (is_retained_primary) {
        old_hash
    } else {
        sign_under_new(
            ctx.keys,
            ALIAS_HMAC_INFO,
            "${normalize_address(opened.local_part)}@${addr_template(alias["domain"])}",
        )
    }
    val hash_changed = !same_hash(old_hash, new_hash)

    if (!opened.changed && !display.changed && !note.changed && !websites.changed && !hash_changed) {
        ctx.counts.current += 1
        return null
    }

    val pairs = mutableListOf<Pair<String, Any?>>(
        "id" to alias["id"],
        "encrypted_local_part" to opened.ciphertext,
        "local_part_nonce" to opened.nonce,
        "alias_address_hash" to new_hash,
    )
    val display_send = display.send
    if (display.changed && display_send != null) {
        pairs.add("encrypted_display_name" to display_send.ciphertext)
        pairs.add("display_name_nonce" to display_send.nonce)
    }
    val note_send = note.send
    if (note.changed && note_send != null) {
        pairs.add("encrypted_note" to note_send.ciphertext)
        pairs.add("note_nonce" to note_send.nonce)
    }
    val websites_send = websites.send
    if (websites.changed && websites_send != null) {
        pairs.add("encrypted_websites" to websites_send.ciphertext)
        pairs.add("websites_nonce" to websites_send.nonce)
    }

    val move = if (hash_changed && old_hash != null && new_hash != null) {
        HashMove(from_hash = old_hash, to_hash = new_hash, cursor = null)
    } else {
        null
    }
    return AddressEntry(json_object(*pairs.toTypedArray()), move)
}

private fun build_domain_address_entry(
    ctx: ResealContext,
    address: JsonObject,
    domain_name: String,
): AddressEntry? {
    val opened = open_local_part(ctx, address.str("encrypted_local_part"), address.str("local_part_nonce"))
        ?: return null
    val display = plan_optional_field(ctx, address.str("encrypted_display_name"), address.str("display_name_nonce"))
    val note = plan_optional_field(ctx, address.str("encrypted_note"), address.str("note_nonce"))
    val new_hash = sign_under_new(
        ctx.keys,
        DOMAIN_ADDRESS_HMAC_INFO,
        "${normalize_address(opened.local_part)}@${domain_name.lowercase()}",
    )
    val old_hash = address.str("local_part_hash")
    val hash_changed = !same_hash(old_hash, new_hash)

    if (!opened.changed && !display.changed && !note.changed && !hash_changed) {
        ctx.counts.current += 1
        return null
    }

    val pairs = mutableListOf<Pair<String, Any?>>(
        "id" to address["id"],
        "encrypted_local_part" to opened.ciphertext,
        "local_part_nonce" to opened.nonce,
        "local_part_hash" to new_hash,
    )
    val display_send = display.send
    if (display_send != null) {
        pairs.add("encrypted_display_name" to display_send.ciphertext)
        pairs.add("display_name_nonce" to display_send.nonce)
    }
    val note_send = note.send
    if (note.changed && note_send != null) {
        pairs.add("encrypted_note" to note_send.ciphertext)
        pairs.add("note_nonce" to note_send.nonce)
    }

    val move = if (hash_changed && !old_hash.isNullOrEmpty()) {
        HashMove(from_hash = old_hash, to_hash = new_hash, cursor = null)
    } else {
        null
    }
    return AddressEntry(json_object(*pairs.toTypedArray()), move)
}

private fun same_move(a: HashMove, b: HashMove): Boolean = a.from_hash == b.from_hash && a.to_hash == b.to_hash

private fun drop_move(ctx: ResealContext, move: HashMove) {
    ctx.progress.hash_moves.removeAll { same_move(it, move) }
    ctx.persist()
}

private fun is_permanent_rejection(status: Int?): Boolean =
    status != null && status >= 400 && status < 500 && status != 401 && status != 403 && status != 429

suspend fun drain_hash_moves(ctx: ResealContext) {
    while (ctx.progress.hash_moves.isNotEmpty()) {
        val move = ctx.progress.hash_moves[0]
        var finished = false
        for (page in 0 until MAX_MAIL_TOKEN_PAGES) {
            val move_cursor = move.cursor
            val body = json_object(
                "from_hash" to move.from_hash,
                "to_hash" to move.to_hash,
                "cursor" to if (move_cursor.isNullOrEmpty()) null else move_cursor,
            )
            val response = request_with_backoff(ctx) { ctx.api.post(MAIL_TOKENS_PATH, body) }
            if (response.error) {
                if (is_permanent_rejection(response.status)) {
                    finished = true
                    break
                }
                throw ResealInterrupted("mail_tokens:${response.status ?: "network"}")
            }
            val data = response.data.as_object()
            val next_cursor = data.str("next_cursor")
            if (addr_truthy(data?.get("done")) || next_cursor.isNullOrEmpty()) {
                finished = true
                break
            }
            move.cursor = next_cursor
            ctx.persist()
            pause_between_batches(ctx)
        }
        if (!finished) throw ResealInterrupted("mail_tokens:page_limit")
        drop_move(ctx, move)
    }
}

private suspend fun send_address_entries(ctx: ResealContext, entries: List<AddressEntry>, field: String) {
    send_address_entries_with(ctx, entries, "rekey:$field", null) { items ->
        ctx.api.post(USER_REKEY_PATH, json_object(field to JsonArray(items)))
    }
}

private suspend fun send_address_entries_with(
    ctx: ResealContext,
    entries: List<AddressEntry>,
    what: String,
    on_conflict: ((AddressEntry) -> Unit)?,
    send: suspend (List<JsonObject>) -> ApiReply,
) {
    for (batch in entries.chunked(ADDRESS_BATCH_SIZE)) {
        for (entry in batch) {
            val move = entry.move ?: continue
            if (ctx.progress.hash_moves.none { same_move(it, move) }) ctx.progress.hash_moves.add(move)
        }
        ctx.persist()

        val by_item = IdentityHashMap<JsonObject, AddressEntry>()
        for (entry in batch) by_item[entry.item] = entry
        send_items_splitting(
            ctx,
            batch.map { it.item },
            what,
            { item, outcome ->
                val entry = by_item[item]
                if (entry != null) {
                    entry.move?.let { drop_move(ctx, it) }
                    if (outcome == ItemOutcome.CONFLICT) on_conflict?.invoke(entry)
                }
            },
            { item -> item.str("id") },
        ) { items -> send(items) }
        drain_hash_moves(ctx)
        pause_between_batches(ctx)
    }
}

suspend fun list_all_aliases(ctx: ResealContext): List<JsonObject> {
    val seen = LinkedHashMap<String?, JsonObject>()
    for (page in 0 until ALIAS_MAX_PAGES) {
        val path = "/addresses/v1/aliases?limit=$ALIAS_PAGE_SIZE&offset=${page * ALIAS_PAGE_SIZE}"
        val data = require_ok(request_with_backoff(ctx) { ctx.api.get(path) }, "aliases_list").as_object()
        val raw_rows = data?.get("aliases").as_array()
        for (row in raw_rows) {
            val record = row as? JsonObject ?: continue
            seen[record.str("id")] = record
        }
        if (!addr_truthy(data?.get("has_more")) || raw_rows.size < ALIAS_PAGE_SIZE) break
    }
    return seen.values.toList()
}

val aliases_step: ResealStep = object : ResealStep {
    override val name = "aliases"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "aliases")
        drain_hash_moves(ctx)
        if (progress.done) return StepResult.DONE
        val entries = ArrayList<AddressEntry>()
        for (alias in list_all_aliases(ctx)) {
            val entry = build_alias_entry(
                ctx,
                alias,
                addr_truthy(alias["is_random"]),
                addr_truthy(alias["is_retained_primary"]),
            )
            if (entry != null) entries.add(entry)
        }
        send_address_entries(ctx, entries, "re_encrypted_aliases")
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

val domain_addresses_step: ResealStep = object : ResealStep {
    override val name = "domain_addresses"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "domain_addresses")
        drain_hash_moves(ctx)
        if (progress.done) return StepResult.DONE
        val domains = require_ok(
            request_with_backoff(ctx) { ctx.api.get("/addresses/v1/domains") },
            "domains_list",
        ).as_object()
        for (domain in domains.objects("domains")) {
            val path = "/addresses/v1/domains/${addr_uri_component(addr_template(domain["id"]))}/addresses"
            val data = require_ok(
                request_with_backoff(ctx) { ctx.api.get(path) },
                "domain_addresses_list",
            ).as_object()
            val domain_name = addr_template(domain["domain_name"])
            val entries = ArrayList<AddressEntry>()
            for (address in data.objects("addresses")) {
                val entry = build_domain_address_entry(ctx, address, domain_name)
                if (entry != null) entries.add(entry)
            }
            send_address_entries(ctx, entries, "re_encrypted_domain_addresses")
        }
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

private fun keep_old_hash(entry: AddressEntry, old_hash: String): AddressEntry =
    AddressEntry(entry.item.plus_fields("alias_address_hash" to old_hash), null)

val deleted_aliases_step: ResealStep = object : ResealStep {
    override val name = "deleted_aliases"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "deleted_aliases")
        drain_hash_moves(ctx)
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val rows = collect_id_pages(ctx, DELETED_LIST_PATH, "aliases", "deleted_aliases_list")
        val entries = ArrayList<AddressEntry>()
        val old_hashes = HashMap<String, String?>()
        for (alias in rows) {
            val entry = build_alias_entry(ctx, alias, addr_truthy(alias["is_random"]), false) ?: continue
            val id = alias.str("id")
            if (id != null) old_hashes[id] = alias.str("alias_address_hash")
            entries.add(entry)
        }
        val retry = ArrayList<AddressEntry>()
        val send: suspend (List<JsonObject>) -> ApiReply = { items ->
            ctx.api.post(STORAGE_REKEY_PATH, json_object("deleted_aliases" to JsonArray(items)))
        }
        send_address_entries_with(ctx, entries, "storage_rekey:deleted_aliases", { entry ->
            val old_hash = entry.item.str("id")?.let { old_hashes[it] }
            if (!old_hash.isNullOrEmpty()) retry.add(keep_old_hash(entry, old_hash))
        }, send)
        if (retry.isNotEmpty()) {
            ctx.counts.conflicts -= retry.size
            send_address_entries_with(ctx, retry, "storage_rekey:deleted_aliases", null, send)
        }
        if (incomplete_count(ctx) > before) return StepResult.INCOMPLETE
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

val ghost_aliases_step: ResealStep = object : ResealStep {
    override val name = "ghost_aliases"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "ghost_aliases")
        drain_hash_moves(ctx)
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val rows = collect_id_pages(ctx, GHOST_LIST_PATH, "aliases", "ghost_aliases_list")
        val entries = ArrayList<AddressEntry>()
        for (alias in rows) {
            val entry = build_alias_entry(ctx, alias, false, false)
            if (entry != null) entries.add(entry)
        }
        send_address_entries(ctx, entries, "re_encrypted_aliases")
        if (incomplete_count(ctx) > before) return StepResult.INCOMPLETE
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

suspend fun list_ghost_alias_ids(ctx: ResealContext): List<String> =
    collect_id_pages(ctx, GHOST_LIST_PATH, "aliases", "ghost_aliases_list").mapNotNull { it.str("id") }
