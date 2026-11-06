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
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val PROFILE_NOTES_HMAC_INFO = "profile-notes-hmac-v1"
const val RATCHET_STATES_PATH = "/crypto/v1/ratchet/states"
const val PROFILE_NOTES_PATH = "/settings/v1/profile_notes/all"
const val SYNC_SOURCES_PATH = "/contacts/v1/sync/sources"
const val DELETED_DIRECTORIES_PATH = "/addresses/v1/aliases/directories/deleted"
const val SEALED_BATCH_SIZE = 100
const val PROFILE_NOTE_BATCH_SIZE = 100
const val RATCHET_BATCH_SIZE = 20
const val ATTACHMENT_BATCH_SIZE = 4

private const val PROFILE_NOTES_INTEGRITY_SUFFIX = "profile-notes-v1"
private const val RATCHET_BOUND_AAD_PREFIX = "aster-ratchet-state-v2:"
private const val CATEGORIES_CONTACT_PAGE_SIZE = 100
private const val CATEGORIES_ALLOWED_PAGE_SIZE = 500
private const val CATEGORIES_MAX_PAGES_PER_RUN = 1000
private const val CATEGORIES_ALIAS_PAGE_SIZE = 1000
private const val CATEGORIES_ALIAS_MAX_PAGES = 100
private const val CATEGORIES_GHOST_LIST_PATH = "/addresses/v1/aliases/ghost"
private const val NOTES_RECENT_RECIPIENTS_PATH = "/contacts/v1/recent_recipients"
private const val NOTES_RECENT_RECIPIENT_LIMIT = 100
private const val MAX_SAFE_INTEGER = 9007199254740991.0

private class RatchetConflict(val id: String, val state_version: Double)

private class RatchetOpened(val plaintext: ByteArray, val bound: Boolean, val derived: Boolean)

private class AddressOwner(val key: String, val base: String)

private class SealedPair(val ciphertext_field: String, val nonce_field: String)

private class StaleFieldValue(val row: JsonObject, val sealed: SealedValue)

private val NAME_PAIR = SealedPair("encrypted_name", "name_nonce")
private val CONTENT_PAIR = SealedPair("encrypted_content", "content_nonce")
private val CATEGORY_PAIR = SealedPair("encrypted_category", "category_nonce")

private val EMPTY_OBJECT = JsonObject(emptyMap())

private fun json_number(element: JsonElement?): Double? {
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    return primitive.content.toDoubleOrNull()?.takeIf { it.isFinite() }
}

private fun is_safe_integer(value: Double): Boolean =
    value.isFinite() && floor(value) == value && abs(value) <= MAX_SAFE_INTEGER

private fun js_string_length(element: JsonElement?): Int = when (element) {
    null -> "undefined".length
    is JsonPrimitive -> element.content.length
    else -> element.toString().length
}

private fun nullish(element: JsonElement?): JsonElement? = if (element is JsonNull) null else element

private fun web_decode_text(bytes: ByteArray): String = decode_text(bytes).removePrefix("﻿")

private fun query_component(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun slice(items: List<JsonObject>, start: Int, size: Int): List<JsonObject> =
    items.subList(start, minOf(start + size, items.size))

private fun JsonObject?.items_count(): Int = (this?.get("items") as? JsonArray)?.size ?: 0

private fun sealed_item(ctx: ResealContext, id: String?, ciphertext: String?, nonce: String?): JsonObject? {
    val result = reseal_field(ctx.keys, ciphertext, nonce)
    if (result !is ResealResult.Sealed) {
        if (result != ResealResult.Absent) tally_untouched(ctx, listOf(result))
        return null
    }
    return json_object("id" to id, "ciphertext" to result.value.ciphertext, "nonce" to result.value.nonce)
}

private suspend fun post_sealed_items(ctx: ResealContext, category: String, items: List<JsonObject>) {
    var i = 0
    while (i < items.size) {
        post_storage_rekey(ctx, category, slice(items, i, SEALED_BATCH_SIZE))
        pause_between_batches(ctx)
        i += SEALED_BATCH_SIZE
    }
}

private fun open_new_or_old(keys: ResealKeys, ciphertext: String?, nonce: String?): ByteArray? {
    val ct = decode_b64(ciphertext) ?: return null
    val iv = decode_b64(nonce) ?: return null
    return gcm_open(keys.new_aes, ct, iv) ?: open_with_any(keys.old_aes, ct, iv)
}

private fun conversation_text(value: String?): String? {
    val bytes = decode_b64(value) ?: return null
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()?.removePrefix("﻿")
}

private fun ratchet_state_bound_aad(conversation_id: String): ByteArray =
    "$RATCHET_BOUND_AAD_PREFIX$conversation_id".toByteArray(Charsets.UTF_8)

private fun open_ratchet_under_old(
    keys: ResealKeys,
    ciphertext: ByteArray,
    nonce: ByteArray,
    bound_aad: ByteArray,
): RatchetOpened? {
    val bound = open_with_any(keys.old_ratchet, ciphertext, nonce, bound_aad)
    if (bound != null) return RatchetOpened(bound, bound = true, derived = false)
    val legacy = open_with_any(keys.old_aes, ciphertext, nonce)
    if (legacy != null) return RatchetOpened(legacy, bound = false, derived = false)
    val derived = open_with_any(keys.old_ratchet, ciphertext, nonce)
    return if (derived != null) RatchetOpened(derived, bound = false, derived = true) else null
}

private fun decode_ratchet_state_kind(
    plaintext_json: String,
    conversation_id: String,
    floor_version: Double,
    opened_bound: Boolean,
    established: Boolean = floor_version > 0,
    allow_unversioned: Boolean = false,
): String {
    val parsed = parse_json_text(plaintext_json) ?: return "malformed"
    val record = parsed as? JsonObject ?: return "malformed"
    val state = record["state"] as? JsonObject ?: return "malformed"
    val inner_id = state.str("conversation_id")
    val bound_id = record.str("conversation_id")
    if (inner_id != null && inner_id != conversation_id) return "wrong_conversation"
    if (bound_id != null && bound_id != conversation_id) return "wrong_conversation"
    val raw_version = json_number(record["sync_version"])
    val sync_version = if (raw_version != null && is_safe_integer(raw_version)) raw_version else null
    if (opened_bound && (bound_id == null || sync_version == null)) return "unbound"
    if (sync_version != null && sync_version < floor_version) return "rolled_back"
    if ((established || floor_version > 0) && (bound_id == null || (sync_version == null && !allow_unversioned))) {
        return "unbound"
    }
    return "accepted"
}

private fun ratchet_state_accepted(opened: RatchetOpened, conversation_id: String): Boolean {
    val text = web_decode_text(opened.plaintext)
    val kind = if (opened.derived) {
        decode_ratchet_state_kind(text, conversation_id, 0.0, false, true, false)
    } else {
        decode_ratchet_state_kind(text, conversation_id, 0.0, opened.bound)
    }
    return kind == "accepted"
}

private fun build_ratchet_item(ctx: ResealContext, row: JsonObject): JsonObject? {
    val conversation_id = conversation_text(row.str("conversation_id"))
    val ciphertext = decode_b64(row.str("encrypted_state"))
    val nonce = decode_b64(row.str("state_nonce"))
    if (conversation_id.isNullOrEmpty() || ciphertext == null || nonce == null) {
        ctx.counts.unreadable += 1
        return null
    }
    val bound_aad = ratchet_state_bound_aad(conversation_id)
    for (aad in listOf(bound_aad, null)) {
        val current = gcm_open(ctx.keys.new_ratchet, ciphertext, nonce, aad)
        if (current != null) {
            current.fill(0)
            ctx.counts.current += 1
            return null
        }
    }
    val opened = open_ratchet_under_old(ctx.keys, ciphertext, nonce, bound_aad)
    if (opened == null) {
        ctx.counts.unreadable += 1
        return null
    }
    try {
        if (!ratchet_state_accepted(opened, conversation_id)) {
            ctx.counts.unreadable += 1
            return null
        }
        val sealed = seal_under_new(
            ctx.keys,
            opened.plaintext,
            if (opened.bound) bound_aad else null,
            ctx.keys.new_ratchet,
        )
        return json_object(
            "id" to row["id"],
            "encrypted_state" to sealed.ciphertext,
            "state_nonce" to sealed.nonce,
            "expected_version" to row["state_version"],
        )
    } finally {
        opened.plaintext.fill(0)
    }
}

private fun ratchet_conflicts_of(data: JsonElement?): List<RatchetConflict> {
    val record = data.as_object() ?: return emptyList()
    val list = record["ratchet_conflicts"] as? JsonArray ?: return emptyList()
    val out = ArrayList<RatchetConflict>()
    for (entry in list) {
        val entry_object = entry as? JsonObject ?: continue
        val id = entry_object.str("id") ?: continue
        val version = json_number(entry_object["state_version"]) ?: continue
        out.add(RatchetConflict(id, version))
    }
    return out
}

private suspend fun list_ratchet_rows(ctx: ResealContext): List<JsonObject> {
    val rows = require_ok(request_with_backoff(ctx) { ctx.api.get(RATCHET_STATES_PATH) }, "ratchet_states_list")
    return (rows as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
}

private suspend fun post_ratchet_items(ctx: ResealContext, items: List<JsonObject>): List<RatchetConflict> {
    val conflicts = ArrayList<RatchetConflict>()
    for (batch in chunk_by_chars(items, RATCHET_BATCH_SIZE) { js_string_length(it["encrypted_state"]) }) {
        send_items_splitting(ctx, batch, "storage_rekey:ratchet_states") { entries ->
            val response = ctx.api.post(STORAGE_REKEY_PATH, json_object("ratchet_states" to JsonArray(entries)))
            if (!response.error) conflicts.addAll(ratchet_conflicts_of(response.data))
            response
        }
        pause_between_batches(ctx)
    }
    return conflicts
}

private suspend fun retry_ratchet_conflicts(ctx: ResealContext, conflicts: List<RatchetConflict>) {
    if (conflicts.isEmpty()) return
    val wanted = LinkedHashMap<String, RatchetConflict>()
    for (entry in conflicts) wanted[entry.id] = entry
    val retry = ArrayList<JsonObject>()
    ctx.counts.skipped -= wanted.size
    for (row in list_ratchet_rows(ctx)) {
        val row_id = row.str("id") ?: continue
        val conflict = wanted[row_id] ?: continue
        wanted.remove(row_id)
        val row_version = json_number(row["state_version"])
        if (row_version == null || row_version != conflict.state_version) {
            ctx.counts.conflicts += 1
            continue
        }
        val item = build_ratchet_item(ctx, row)
        if (item != null) retry.add(item)
    }
    ctx.counts.skipped += wanted.size
    if (retry.isEmpty()) return
    val again = post_ratchet_items(ctx, retry)
    ctx.counts.skipped -= again.size
    ctx.counts.conflicts += again.size
}

val ratchet_states_step: ResealStep = object : ResealStep {
    override val name: String = "ratchet_states"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "ratchet_states")
        if (progress.done) return StepResult.DONE
        val items = ArrayList<JsonObject>()
        for (row in list_ratchet_rows(ctx)) {
            val item = build_ratchet_item(ctx, row)
            if (item != null) items.add(item)
        }
        retry_ratchet_conflicts(ctx, post_ratchet_items(ctx, items))
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

private suspend fun each_contact_page(
    ctx: ResealContext,
    start_cursor: String?,
    visit: suspend (JsonObject, String?) -> Unit,
): Boolean {
    var cursor = start_cursor
    for (page in 0 until CATEGORIES_MAX_PAGES_PER_RUN) {
        val current_cursor = cursor
        val query = StringBuilder("limit=$CATEGORIES_CONTACT_PAGE_SIZE")
        if (!current_cursor.isNullOrEmpty()) query.append("&cursor=").append(query_component(current_cursor))
        val target = "/contacts/v1?$query"
        val data = require_ok(request_with_backoff(ctx) { ctx.api.get(target) }, "contacts_list").as_object()
            ?: EMPTY_OBJECT
        val next_cursor = data.str("next_cursor")
        val next = if (data.bool("has_more") == true && !next_cursor.isNullOrEmpty()) next_cursor else null
        visit(data, next)
        if (next == null) return true
        cursor = next
    }
    return false
}

private suspend fun categories_list_recent_recipients(ctx: ResealContext): List<JsonObject> {
    val seen = LinkedHashMap<String?, JsonObject>()
    val walk = walk_id_pages(ctx, NOTES_RECENT_RECIPIENTS_PATH, "recent_list", null) { data, _ ->
        for (row in data.objects("items")) seen[row.str("id")] = row
    }
    if (walk == PageWalk.DONE) return seen.values.toList()
    seen.clear()
    for (page in 0 until CATEGORIES_MAX_PAGES_PER_RUN) {
        val target = "$NOTES_RECENT_RECIPIENTS_PATH?limit=$NOTES_RECENT_RECIPIENT_LIMIT&offset=${seen.size}"
        val data = require_ok(request_with_backoff(ctx) { ctx.api.get(target) }, "recent_list").as_object()
        val rows = data.objects("items")
        val before = seen.size
        for (row in rows) seen[row.str("id")] = row
        if (rows.size < NOTES_RECENT_RECIPIENT_LIMIT || seen.size == before) break
    }
    return seen.values.toList()
}

private fun add_candidate(emails: MutableSet<String>, value: String?) {
    if (value != null && value.trim().isNotEmpty()) emails.add(normalize_email(value))
}

private fun add_sender_email(ctx: ResealContext, emails: MutableSet<String>, row: JsonObject) {
    val plain = open_new_or_old(ctx.keys, row.str("encrypted_sender_data"), row.str("sender_data_nonce")) ?: return
    try {
        val record = parse_json_object(plain)
        if (record != null) add_candidate(emails, record.str("email"))
    } finally {
        plain.fill(0)
    }
}

private suspend fun collect_candidate_emails(ctx: ResealContext): Set<String> {
    val emails = LinkedHashSet<String>()
    each_contact_page(ctx, null) { page, _ ->
        for (row in page.objects("items")) {
            val plain = open_new_or_old(ctx.keys, row.str("encrypted_data"), row.str("data_nonce")) ?: continue
            try {
                val record = parse_json_object(plain)
                val list = record?.get("emails") as? JsonArray
                if (list != null) {
                    for (entry in list) {
                        val primitive = entry as? JsonPrimitive
                        add_candidate(emails, if (primitive != null && primitive.isString) primitive.content else null)
                    }
                }
            } finally {
                plain.fill(0)
            }
        }
    }
    val blocked = get_optional(ctx, "/contacts/v1/blocked_senders", "blocked_list").as_object()
    for (row in blocked.objects("blocked_senders")) add_sender_email(ctx, emails, row)
    for (page in 0 until CATEGORIES_MAX_PAGES_PER_RUN) {
        val allowed = get_optional(
            ctx,
            "/contacts/v1/allowed_senders?limit=$CATEGORIES_ALLOWED_PAGE_SIZE&offset=${page * CATEGORIES_ALLOWED_PAGE_SIZE}",
            "allowed_list",
        ).as_object()
        val rows = allowed.objects("allowed_senders")
        for (row in rows) add_sender_email(ctx, emails, row)
        if (rows.size < CATEGORIES_ALLOWED_PAGE_SIZE) break
    }
    for (row in categories_list_recent_recipients(ctx)) {
        val plain = open_new_or_old(ctx.keys, row.str("encrypted_email"), row.str("email_nonce")) ?: continue
        try {
            add_candidate(emails, decode_text(plain))
        } finally {
            plain.fill(0)
        }
    }
    return emails
}

private suspend fun profile_note_tokens(ctx: ResealContext): Map<String, String> {
    val tokens = HashMap<String, String>()
    val new_key = ctx.keys.hmac(PROFILE_NOTES_HMAC_INFO)
    val old_keys = ctx.keys.old_hmacs(PROFILE_NOTES_HMAC_INFO)
    for (email in collect_candidate_emails(ctx)) {
        val new_token = sign_with(new_key, email)
        tokens[new_token] = new_token
        for (old_key in old_keys) {
            val old_token = sign_with(old_key, email)
            if (!tokens.containsKey(old_token)) tokens[old_token] = new_token
        }
    }
    return tokens
}

private fun build_profile_note_item(ctx: ResealContext, row: JsonObject, tokens: Map<String, String>): JsonObject? {
    val encrypted_note = row.str("encrypted_note")
    val note_nonce = row.str("note_nonce")
    val body = reseal_field(ctx.keys, encrypted_note, note_nonce)
    val row_token = row.str("email_token")
    val email_token = row_token?.let { tokens[it] ?: it }
    val token_changed = email_token != row_token
    if (body == ResealResult.Unreadable || body == ResealResult.Absent) {
        ctx.counts.unreadable += 1
        return null
    }
    if (body == ResealResult.Current && !token_changed) {
        ctx.counts.current += 1
        return null
    }
    val sealed = if (body is ResealResult.Sealed) {
        body.value
    } else {
        SealedValue(encrypted_note.orEmpty(), note_nonce.orEmpty())
    }
    return json_object(
        "id" to row["id"],
        "email_token" to email_token,
        "encrypted_note" to sealed.ciphertext,
        "note_nonce" to sealed.nonce,
        "integrity_hash" to sign_under_new(
            ctx.keys,
            PROFILE_NOTES_HMAC_INFO,
            "${sealed.ciphertext}:${sealed.nonce}:$PROFILE_NOTES_INTEGRITY_SUFFIX",
        ),
    )
}

val profile_notes_step: ResealStep = object : ResealStep {
    override val name: String = "profile_notes"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "profile_notes")
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val notes = collect_id_pages(ctx, PROFILE_NOTES_PATH, "notes", "profile_notes_list")
        if (notes.isNotEmpty()) {
            val tokens = profile_note_tokens(ctx)
            val originals = HashMap<String, String?>()
            for (note in notes) originals[note.str("id") ?: "undefined"] = note.str("email_token")
            val items = ArrayList<JsonObject>()
            val retry = ArrayList<JsonObject>()
            for (note in notes) {
                val item = build_profile_note_item(ctx, note, tokens)
                if (item != null) items.add(item)
            }
            var i = 0
            while (i < items.size) {
                send_items_splitting(
                    ctx,
                    slice(items, i, PROFILE_NOTE_BATCH_SIZE),
                    "storage_rekey:profile_notes",
                    on_failed_item = { item, outcome ->
                        val original = originals[item.str("id") ?: "undefined"]
                        if (outcome == ItemOutcome.CONFLICT && !original.isNullOrEmpty() &&
                            original != item.str("email_token")
                        ) {
                            ctx.counts.conflicts -= 1
                            retry.add(item.plus_fields("email_token" to original))
                        }
                    },
                ) { batch ->
                    ctx.api.post(STORAGE_REKEY_PATH, json_object("profile_notes" to JsonArray(batch)))
                }
                pause_between_batches(ctx)
                i += PROFILE_NOTE_BATCH_SIZE
            }
            if (retry.isNotEmpty()) post_storage_rekey(ctx, "profile_notes", retry)
        }
        return finish_step(ctx, progress, before)
    }
}

private suspend fun categories_list_aliases(ctx: ResealContext): List<JsonObject> {
    val seen = LinkedHashMap<String?, JsonObject>()
    for (page in 0 until CATEGORIES_ALIAS_MAX_PAGES) {
        val target = "/addresses/v1/aliases?limit=$CATEGORIES_ALIAS_PAGE_SIZE&offset=${page * CATEGORIES_ALIAS_PAGE_SIZE}"
        val data = require_ok(request_with_backoff(ctx) { ctx.api.get(target) }, "aliases_list").as_object()
        val rows = data.objects("aliases")
        for (row in rows) seen[row.str("id")] = row
        if (data.bool("has_more") != true || rows.size < CATEGORIES_ALIAS_PAGE_SIZE) break
    }
    return seen.values.toList()
}

private suspend fun categories_list_ghost_ids(ctx: ResealContext): List<String> =
    collect_id_pages(ctx, CATEGORIES_GHOST_LIST_PATH, "aliases", "ghost_aliases_list").mapNotNull { it.str("id") }

private suspend fun list_address_owners(ctx: ResealContext): List<AddressOwner> {
    val owners = LinkedHashMap<String, AddressOwner>()
    fun add(kind: String, id: String, base: String) {
        owners["$kind:$id"] = AddressOwner("$kind:$id", base)
    }
    for (alias in categories_list_aliases(ctx)) {
        val id = alias.str("id") ?: continue
        add("a", id, "/addresses/v1/aliases/${url_component(id)}")
    }
    for (id in categories_list_ghost_ids(ctx)) {
        if (owners.containsKey("a:$id")) continue
        add("g", id, "/addresses/v1/aliases/${url_component(id)}")
    }
    val domains = require_ok(
        request_with_backoff(ctx) { ctx.api.get("/addresses/v1/domains") },
        "domains_list",
    ).as_object()
    for (domain in domains.objects("domains")) {
        val domain_id = domain.str("id") ?: continue
        val target = "/addresses/v1/domains/${url_component(domain_id)}/addresses"
        val data = require_ok(request_with_backoff(ctx) { ctx.api.get(target) }, "domain_addresses_list").as_object()
        for (address in data.objects("addresses")) {
            val address_id = address.str("id") ?: continue
            add("d", address_id, "/addresses/v1/aliases/domain-addresses/${url_component(address_id)}")
        }
    }
    return owners.values.sortedWith { a, b -> a.key.compareTo(b.key) }
}

private suspend fun reseal_owner_items(ctx: ResealContext, owner: AddressOwner) {
    val pins = get_optional(ctx, "${owner.base}/pins", "alias_pins_list").as_object()
    val contacts = collect_id_pages(ctx, "${owner.base}/contacts", "contacts", "alias_contacts_list", true)
    val destinations = get_optional(ctx, "${owner.base}/destinations", "alias_destinations_list").as_object()
    val pin_items = ArrayList<JsonObject>()
    val contact_items = ArrayList<JsonObject>()
    val destination_items = ArrayList<JsonObject>()
    for (row in pins.objects("pins")) {
        val item = sealed_item(ctx, row.str("id"), row.str("encrypted_sender"), row.str("sender_nonce"))
        if (item != null) pin_items.add(item)
    }
    for (row in contacts) {
        val item = sealed_item(ctx, row.str("id"), row.str("encrypted_contact"), row.str("contact_nonce"))
        if (item != null) contact_items.add(item)
    }
    for (row in destinations.objects("destinations")) {
        val item = sealed_item(ctx, row.str("id"), row.str("encrypted_destination"), row.str("destination_nonce"))
        if (item != null) destination_items.add(item)
    }
    post_sealed_items(ctx, "alias_pins", pin_items)
    post_sealed_items(ctx, "alias_contacts", contact_items)
    post_sealed_items(ctx, "alias_destinations", destination_items)
}

val alias_items_step: ResealStep = object : ResealStep {
    override val name: String = "alias_items"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "alias_items")
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val after = progress.cursor
        for (owner in list_address_owners(ctx)) {
            if (!after.isNullOrEmpty() && owner.key <= after) continue
            reseal_owner_items(ctx, owner)
            progress.cursor = owner.key
            ctx.persist()
            if (ctx.is_cancelled()) throw ResealInterrupted("cancelled")
        }
        return finish_step(ctx, progress, before)
    }
}

private suspend fun list_rows(
    ctx: ResealContext,
    path: String,
    list_key: String,
    what: String,
    paged: Boolean,
): List<JsonObject> {
    if (paged) return collect_id_pages(ctx, path, list_key, what, true)
    val data = get_optional(ctx, path, what).as_object()
    val rows = data?.get(list_key) as? JsonArray ?: return emptyList()
    return rows.mapNotNull { it as? JsonObject }
}

private fun sealed_list_step(
    step_name: String,
    path: String,
    list_key: String,
    ciphertext_field: String,
    nonce_field: String,
    paged: Boolean = false,
): ResealStep = object : ResealStep {
    override val name: String = step_name

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, step_name)
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val rows = list_rows(ctx, path, list_key, "${step_name}_list", paged)
        val items = ArrayList<JsonObject>()
        for (row in rows) {
            val item = sealed_item(ctx, row.str("id"), row.str(ciphertext_field), row.str(nonce_field))
            if (item != null) items.add(item)
        }
        post_sealed_items(ctx, step_name, items)
        return finish_step(ctx, progress, before)
    }
}

val alias_directories_step: ResealStep = sealed_list_step(
    "alias_directories",
    "/addresses/v1/aliases/directories",
    "directories",
    "encrypted_label",
    "label_nonce",
)

val deleted_alias_directories_step: ResealStep = sealed_list_step(
    "deleted_alias_directories",
    DELETED_DIRECTORIES_PATH,
    "directories",
    "encrypted_label",
    "label_nonce",
    true,
)

val contact_field_definitions_step: ResealStep = sealed_list_step(
    "contact_field_definitions",
    "/contacts/v1/fields",
    "items",
    "encrypted_name",
    "name_nonce",
)

private suspend fun reseal_field_values(ctx: ResealContext, contact_id: String) {
    val path = "/contacts/v1/${url_component(contact_id)}/fields"
    val data = get_optional(ctx, path, "contact_field_values_list").as_object()
    val stale = ArrayList<StaleFieldValue>()
    for (row in data.objects("items")) {
        val result = reseal_field(ctx.keys, row.str("encrypted_value"), row.str("value_nonce"))
        if (result is ResealResult.Sealed) {
            stale.add(StaleFieldValue(row, result.value))
        } else if (result != ResealResult.Absent) {
            tally_untouched(ctx, listOf(result))
        }
    }
    if (stale.isEmpty()) return
    val fresh = get_optional(ctx, path, "contact_field_values_list").as_object()
    val latest = HashMap<String?, String?>()
    for (row in fresh.objects("items")) latest[row.str("id")] = row.str("encrypted_value")
    for (entry in stale) {
        val row = entry.row
        val target = "$path/${url_component(row.str("field_definition_id") ?: "undefined")}"
        write_if_unchanged(
            ctx,
            "contact_field_value",
            { latest[row.str("id")] == row.str("encrypted_value") },
        ) {
            ctx.api.put(
                target,
                json_object("encrypted_value" to entry.sealed.ciphertext, "value_nonce" to entry.sealed.nonce),
            )
        }
    }
    pause_between_batches(ctx)
}

private suspend fun reseal_contact_attachments(ctx: ResealContext, contact_id: String) {
    val base = "/contacts/v1/${url_component(contact_id)}/attachments"
    val list = get_optional(ctx, base, "contact_attachments_list").as_object()
    val items = ArrayList<JsonObject>()
    for (entry in list.objects("items")) {
        val meta_state = classify_sealed(ctx.keys, entry.str("encrypted_meta"), entry.str("meta_nonce"))
        if (meta_state is SealedState.Current) {
            ctx.counts.current += 1
            continue
        }
        if (meta_state is SealedState.Stale) meta_state.plaintext.fill(0)
        val entry_target = "$base/${url_component(entry.str("id") ?: "undefined")}"
        val raw = get_optional(ctx, entry_target, "contact_attachment_get") ?: continue
        val row = raw.as_object() ?: EMPTY_OBJECT
        val data = reseal_field(ctx.keys, row.str("encrypted_data"), row.str("data_nonce"))
        val meta = reseal_field(ctx.keys, row.str("encrypted_meta"), row.str("meta_nonce"))
        tally_untouched(ctx, listOf(data, meta))
        if (data !is ResealResult.Sealed && meta !is ResealResult.Sealed) continue
        items.add(
            json_object(
                "id" to row["id"],
                "encrypted_data" to if (data is ResealResult.Sealed) data.value.ciphertext else row["encrypted_data"],
                "data_nonce" to if (data is ResealResult.Sealed) data.value.nonce else row["data_nonce"],
                "encrypted_meta" to if (meta is ResealResult.Sealed) meta.value.ciphertext else row["encrypted_meta"],
                "meta_nonce" to if (meta is ResealResult.Sealed) meta.value.nonce else row["meta_nonce"],
            ),
        )
    }
    for (batch in chunk_by_chars(items, ATTACHMENT_BATCH_SIZE) { js_string_length(it["encrypted_data"]) }) {
        post_storage_rekey(ctx, "contact_attachments", batch)
        pause_between_batches(ctx)
    }
}

private suspend fun reseal_contact_photo(ctx: ResealContext, contact_id: String) {
    val path = "/contacts/v1/${url_component(contact_id)}/photo"
    val raw = get_optional(ctx, path, "contact_photo_get") ?: return
    val photo = raw.as_object() ?: EMPTY_OBJECT
    val data = reseal_field(ctx.keys, photo.str("encrypted_data"), photo.str("data_nonce"))
    val meta = reseal_field(ctx.keys, photo.str("encrypted_meta"), photo.str("meta_nonce"))
    tally_untouched(ctx, listOf(data, meta))
    if (data !is ResealResult.Sealed && meta !is ResealResult.Sealed) return
    write_if_unchanged(
        ctx,
        "contact_photo",
        {
            val fresh_raw = get_optional(ctx, path, "contact_photo_get")
            if (fresh_raw == null) {
                false
            } else {
                val fresh = fresh_raw.as_object()
                fresh?.get("data_nonce") == photo["data_nonce"] && fresh?.get("meta_nonce") == photo["meta_nonce"]
            }
        },
    ) {
        ctx.api.post(
            path,
            json_object(
                "encrypted_data" to if (data is ResealResult.Sealed) data.value.ciphertext else photo["encrypted_data"],
                "data_nonce" to if (data is ResealResult.Sealed) data.value.nonce else photo["data_nonce"],
                "encrypted_meta" to if (meta is ResealResult.Sealed) meta.value.ciphertext else photo["encrypted_meta"],
                "meta_nonce" to if (meta is ResealResult.Sealed) meta.value.nonce else photo["meta_nonce"],
                "size_bytes" to photo["size_bytes"],
            ),
        )
    }
    pause_between_batches(ctx)
}

val contact_extras_step: ResealStep = object : ResealStep {
    override val name: String = "contact_extras"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "contact_extras")
        if (progress.done) return StepResult.DONE
        val definitions = get_optional(ctx, "/contacts/v1/fields", "contact_fields_list").as_object()
        val has_fields = definitions.items_count() > 0
        val start = progress.cursor
        val finished = each_contact_page(ctx, start) { page, next ->
            for (row in page.objects("items")) {
                val contact_id = row.str("id") ?: "undefined"
                if (has_fields) reseal_field_values(ctx, contact_id)
                reseal_contact_attachments(ctx, contact_id)
                reseal_contact_photo(ctx, contact_id)
            }
            progress.cursor = next
            ctx.persist()
            pause_between_batches(ctx)
        }
        if (!finished) return StepResult.INTERRUPTED
        progress.done = true
        progress.cursor = null
        ctx.persist()
        return StepResult.DONE
    }
}

private fun plan_pairs(ctx: ResealContext, row: JsonObject, pairs: List<SealedPair>): JsonObject {
    val body = LinkedHashMap<String, JsonElement>()
    val results = ArrayList<ResealResult>()
    for (pair in pairs) {
        val result = reseal_field(ctx.keys, row.str(pair.ciphertext_field), row.str(pair.nonce_field))
        results.add(result)
        if (result is ResealResult.Sealed) {
            body[pair.ciphertext_field] = JsonPrimitive(result.value.ciphertext)
            body[pair.nonce_field] = JsonPrimitive(result.value.nonce)
        }
    }
    tally_untouched(ctx, results)
    return JsonObject(body)
}

private fun same_pairs(a_raw: JsonElement?, b: JsonObject, pairs: List<SealedPair>): Boolean {
    if (a_raw == null) return false
    val a = a_raw.as_object()
    return pairs.all { pair ->
        nullish(a?.get(pair.ciphertext_field)) == nullish(b[pair.ciphertext_field]) &&
            nullish(a?.get(pair.nonce_field)) == nullish(b[pair.nonce_field])
    }
}

private fun editable_rows_step(
    step_name: String,
    list_path: String,
    list_key: String,
    pairs: List<SealedPair>,
): ResealStep = object : ResealStep {
    override val name: String = step_name

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, step_name)
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val rows = collect_id_pages(ctx, list_path, list_key, "${step_name}_list", true)
        for (row in rows) {
            val body = plan_pairs(ctx, row, pairs)
            if (body.isEmpty()) continue
            val item_path = "$list_path/${url_component(row.str("id") ?: "undefined")}"
            write_if_unchanged(
                ctx,
                step_name,
                { same_pairs(get_optional(ctx, item_path, "${step_name}_get"), row, pairs) },
            ) {
                ctx.api.put(item_path, body)
            }
            pause_between_batches(ctx)
        }
        return finish_step(ctx, progress, before)
    }
}

val signatures_step: ResealStep = editable_rows_step(
    "signatures",
    "/mail/v1/signatures",
    "signatures",
    listOf(NAME_PAIR, CONTENT_PAIR),
)

val templates_step: ResealStep = editable_rows_step(
    "templates",
    "/mail/v1/templates",
    "templates",
    listOf(NAME_PAIR, CATEGORY_PAIR, CONTENT_PAIR),
)

val sync_sources_step: ResealStep = object : ResealStep {
    override val name: String = "sync_sources"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "sync_sources")
        if (progress.done) return StepResult.DONE
        val data = get_optional(ctx, SYNC_SOURCES_PATH, "sync_sources_list").as_object()
        for (row in data.objects("items")) {
            val result = reseal_field(ctx.keys, row.str("encrypted_config"), row.str("config_nonce"))
            if (result !is ResealResult.Sealed) {
                if (result != ResealResult.Absent) tally_untouched(ctx, listOf(result))
                continue
            }
            val sealed = result.value
            val target = "$SYNC_SOURCES_PATH/${url_component(row.str("id") ?: "undefined")}"
            write_if_unchanged(
                ctx,
                "sync_sources",
                {
                    val fresh = get_optional(ctx, SYNC_SOURCES_PATH, "sync_sources_list").as_object()
                    val match = fresh.objects("items").firstOrNull { it["id"] == row["id"] }
                    match != null && match["encrypted_config"] == row["encrypted_config"]
                },
            ) {
                ctx.api.put(
                    target,
                    json_object(
                        "source_type" to row["source_type"],
                        "encrypted_config" to sealed.ciphertext,
                        "config_nonce" to sealed.nonce,
                    ),
                )
            }
            pause_between_batches(ctx)
        }
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}
