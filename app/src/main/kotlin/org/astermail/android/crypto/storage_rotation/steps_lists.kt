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

import java.math.BigDecimal
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

const val CONTACTS_HMAC_INFO = "contacts-hmac-v2"
const val CONTACTS_SEARCH_INFO = "contacts-search-v2"
const val BLOCKED_SENDERS_INFO = "blocked-senders-hmac-v1"
const val ALLOWED_SENDERS_INFO = "allowed-senders-hmac-v1"
const val RECENT_RECIPIENTS_INFO = "recent-recipients-hmac-v1"
const val RECENT_RECIPIENTS_PATH = "/contacts/v1/recent_recipients"
const val EXTERNAL_ACCOUNTS_INFO = "external-accounts-hmac-v1"

private const val CONTACT_PAGE_SIZE = 100
private const val CONTACT_BATCH_SIZE = 50
private const val LIST_BATCH_SIZE = 100
private const val ALLOWED_PAGE_SIZE = 500
private const val RECENT_RECIPIENT_LIMIT = 100
private const val LISTS_MAX_PAGES_PER_RUN = 1000
private const val GCM_TAG_LENGTH = 16

typealias ExternalAccountOpen = SealedState

private val lists_js_whitespace: Set<Char> = setOf(
    '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', ' ', ' ', ' ',
    ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
    ' ', ' ', ' ', ' ', ' ', ' ', ' ', '　', '﻿',
)

private fun lists_trim(value: String): String = value.trim { it in lists_js_whitespace }

private fun lists_search_value(value: String): String = lists_trim(value.lowercase())

private fun lists_decode_text(bytes: ByteArray): String {
    val text = decode_text(bytes)
    return if (text.startsWith('﻿')) text.substring(1) else text
}

private fun lists_parse_object(bytes: ByteArray): JsonObject? = parse_json_text(lists_decode_text(bytes)) as? JsonObject

private fun lists_truthy(value: JsonElement?): Boolean = when (value) {
    null, JsonNull -> false
    is JsonObject, is JsonArray -> true
    is JsonPrimitive -> when {
        value.isString -> value.content.isNotEmpty()
        value.booleanOrNull != null -> value.booleanOrNull == true
        else -> value.content.toDoubleOrNull()?.let { it != 0.0 && !it.isNaN() } ?: true
    }
}

private fun lists_number_text(value: JsonPrimitive): String {
    value.booleanOrNull?.let { return it.toString() }
    val number = value.content.toDoubleOrNull() ?: return value.content
    if (number.isNaN()) return "NaN"
    if (number == floor(number) && abs(number) < 1e21) return BigDecimal(number).toPlainString()
    return value.content
}

private fun lists_js_string(value: JsonElement?): String = when (value) {
    null, JsonNull -> ""
    is JsonObject -> "[object Object]"
    is JsonArray -> value.joinToString(",") { if (it is JsonNull) "" else lists_js_string(it) }
    is JsonPrimitive -> if (value.isString) value.content else lists_number_text(value)
}

private fun lists_template(value: JsonElement?): String = when (value) {
    null -> "undefined"
    JsonNull -> "null"
    else -> lists_js_string(value)
}

private fun lists_uri_component(value: String): String = url_component(value)
    .replace("%21", "!")
    .replace("%7E", "~")
    .replace("%27", "'")
    .replace("%28", "(")
    .replace("%29", ")")

private fun lists_form_encode(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun lists_open_stale(ctx: ResealContext, ciphertext: String?, nonce: String?): ByteArray? =
    when (val sealed = classify_sealed(ctx.keys, ciphertext, nonce)) {
        is SealedState.Stale -> sealed.plaintext
        SealedState.Current -> {
            ctx.counts.current += 1
            null
        }
        SealedState.Unreadable -> {
            ctx.counts.unreadable += 1
            null
        }
    }

private fun build_contact_item(ctx: ResealContext, row: JsonObject): JsonObject? {
    val keys = ctx.keys
    val plaintext = lists_open_stale(ctx, row.str("encrypted_data"), row.str("data_nonce")) ?: return null
    try {
        val record = lists_parse_object(plaintext)
        if (record == null) {
            ctx.counts.unreadable += 1
            return null
        }
        val emails = record.strings("emails")
        val first_name = lists_js_string(record["first_name"])
        val last_name = lists_js_string(record["last_name"])
        val company = record.str("company")
        val resealed = seal_under_new(keys, plaintext)
        val contact_token = sign_under_new(
            keys,
            CONTACTS_HMAC_INFO,
            "$first_name $last_name ${emails.joinToString(" ")}".lowercase(),
        )
        val full_name = lists_trim("$first_name $last_name")
        val pairs = mutableListOf<Pair<String, Any?>>(
            "id" to row["id"],
            "encrypted_contact_data" to resealed.ciphertext,
            "contact_nonce" to resealed.nonce,
            "contact_token" to contact_token,
        )
        if (full_name.isNotEmpty()) {
            pairs.add("name_search_token" to sign_under_new(keys, CONTACTS_SEARCH_INFO, lists_search_value(full_name)))
        }
        val first_email = emails.firstOrNull()
        if (!first_email.isNullOrEmpty()) {
            pairs.add("email_search_token" to sign_under_new(keys, CONTACTS_SEARCH_INFO, lists_search_value(first_email)))
        }
        if (!company.isNullOrEmpty()) {
            pairs.add("company_search_token" to sign_under_new(keys, CONTACTS_SEARCH_INFO, lists_search_value(company)))
        }
        val data_version = row["data_version"]
        if (lists_truthy(data_version)) {
            pairs.add(
                "integrity_hash" to sign_under_new(
                    keys,
                    CONTACTS_HMAC_INFO,
                    "${resealed.ciphertext}:${resealed.nonce}:${lists_template(data_version)}",
                ),
            )
        }
        return json_object(*pairs.toTypedArray())
    } finally {
        plaintext.fill(0)
    }
}

val contacts_step: ResealStep = object : ResealStep {
    override val name = "contacts"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "contacts")
        if (progress.done) return StepResult.DONE
        var cursor: String? = progress.cursor
        for (page in 0 until LISTS_MAX_PAGES_PER_RUN) {
            val query = StringBuilder("limit=").append(lists_form_encode(CONTACT_PAGE_SIZE.toString()))
            val current_cursor = cursor
            if (!current_cursor.isNullOrEmpty()) query.append("&cursor=").append(lists_form_encode(current_cursor))
            val path = "/contacts/v1?$query"
            val data = require_ok(request_with_backoff(ctx) { ctx.api.get(path) }, "contacts_list").as_object()
            val items = ArrayList<JsonObject>()
            for (row in data.objects("items")) {
                val item = build_contact_item(ctx, row)
                if (item != null) items.add(item)
            }
            for (batch in items.chunked(CONTACT_BATCH_SIZE)) {
                post_storage_rekey(ctx, "contacts", batch)
                pause_between_batches(ctx)
            }
            cursor = data.str("next_cursor")
            if (!lists_truthy(data?.get("has_more")) || cursor.isNullOrEmpty()) {
                progress.done = true
                progress.cursor = null
                ctx.persist()
                return StepResult.DONE
            }
            progress.cursor = cursor
            ctx.persist()
        }
        return StepResult.INTERRUPTED
    }
}

private fun parse_group_name(value: String): String {
    if (!value.startsWith("{")) return value
    val parsed = parse_json_text(value) ?: return value
    return (parsed as? JsonObject).str("name") ?: value
}

val contact_groups_step: ResealStep = object : ResealStep {
    override val name = "contact_groups"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "contact_groups")
        if (progress.done) return StepResult.DONE
        val data = require_ok(
            request_with_backoff(ctx) { ctx.api.get("/contacts/v1/groups") },
            "groups_list",
        ).as_object()
        for (group in data.objects("groups")) {
            val plaintext = lists_open_stale(ctx, group.str("encrypted_name"), group.str("name_nonce")) ?: continue
            try {
                val name = parse_group_name(lists_decode_text(plaintext))
                val resealed = seal_under_new(ctx.keys, plaintext)
                val body = json_object(
                    "group_token" to sign_under_new(ctx.keys, CONTACTS_SEARCH_INFO, lists_search_value(name)),
                    "encrypted_name" to resealed.ciphertext,
                    "name_nonce" to resealed.nonce,
                )
                val path = "/contacts/v1/groups/${lists_uri_component(lists_template(group["id"]))}"
                send_items_splitting(ctx, listOf(body), "groups_update") { batch -> ctx.api.put(path, batch[0]) }
            } finally {
                plaintext.fill(0)
            }
            pause_between_batches(ctx)
        }
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

private fun build_sender_item(
    ctx: ResealContext,
    row: JsonObject,
    info: String,
    email_prefix: String,
    integrity_suffix: String,
): JsonObject? {
    val plaintext = lists_open_stale(ctx, row.str("encrypted_sender_data"), row.str("sender_data_nonce")) ?: return null
    try {
        val record = lists_parse_object(plaintext)
        val email = record.str("email")
        if (record == null || email.isNullOrEmpty()) {
            ctx.counts.unreadable += 1
            return null
        }
        val is_domain = row.bool("is_domain") ?: (record.bool("is_domain") == true)
        val token_input = if (is_domain) {
            "domain:${lists_trim(email.lowercase())}"
        } else {
            "$email_prefix${lists_trim(email.lowercase())}"
        }
        val resealed = seal_under_new(ctx.keys, plaintext)
        return json_object(
            "id" to row["id"],
            "sender_token" to sign_under_new(ctx.keys, info, token_input),
            "encrypted_sender_data" to resealed.ciphertext,
            "sender_data_nonce" to resealed.nonce,
            "integrity_hash" to sign_under_new(
                ctx.keys,
                info,
                "${resealed.ciphertext}:${resealed.nonce}:$integrity_suffix",
            ),
        )
    } finally {
        plaintext.fill(0)
    }
}

private suspend fun reseal_sender_rows(ctx: ResealContext, rows: List<JsonObject>, category: String) {
    val blocked = category == "blocked_senders"
    val items = ArrayList<JsonObject>()
    for (row in rows) {
        val item = build_sender_item(
            ctx,
            row,
            if (blocked) BLOCKED_SENDERS_INFO else ALLOWED_SENDERS_INFO,
            if (blocked) "" else "email:",
            if (blocked) "blocked-senders-v1" else "allowed-senders-v1",
        )
        if (item != null) items.add(item)
    }
    for (batch in items.chunked(LIST_BATCH_SIZE)) {
        post_storage_rekey(ctx, category, batch)
        pause_between_batches(ctx)
    }
}

val blocked_senders_step: ResealStep = object : ResealStep {
    override val name = "blocked_senders"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "blocked_senders")
        if (progress.done) return StepResult.DONE
        val data = require_ok(
            request_with_backoff(ctx) { ctx.api.get("/contacts/v1/blocked_senders") },
            "blocked_list",
        ).as_object()
        reseal_sender_rows(ctx, data.objects("blocked_senders"), "blocked_senders")
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

val allowed_senders_step: ResealStep = object : ResealStep {
    override val name = "allowed_senders"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "allowed_senders")
        if (progress.done) return StepResult.DONE
        var offset = progress.cursor?.toLongOrNull() ?: 0L
        for (page in 0 until LISTS_MAX_PAGES_PER_RUN) {
            val path = "/contacts/v1/allowed_senders?limit=$ALLOWED_PAGE_SIZE&offset=$offset"
            val data = require_ok(request_with_backoff(ctx) { ctx.api.get(path) }, "allowed_list").as_object()
            val raw_rows = data?.get("allowed_senders").as_array()
            reseal_sender_rows(ctx, raw_rows.mapNotNull { it as? JsonObject }, "allowed_senders")
            if (raw_rows.size < ALLOWED_PAGE_SIZE) {
                progress.done = true
                progress.cursor = null
                ctx.persist()
                return StepResult.DONE
            }
            offset += raw_rows.size
            progress.cursor = offset.toString()
            ctx.persist()
        }
        return StepResult.INTERRUPTED
    }
}

private fun build_recent_recipient_item(ctx: ResealContext, row: JsonObject): JsonObject? {
    val plaintext = lists_open_stale(ctx, row.str("encrypted_email"), row.str("email_nonce")) ?: return null
    try {
        val email = lists_decode_text(plaintext)
        val resealed = seal_under_new(ctx.keys, plaintext)
        return json_object(
            "id" to row["id"],
            "email_token" to sign_under_new(ctx.keys, RECENT_RECIPIENTS_INFO, lists_trim(email.lowercase())),
            "encrypted_email" to resealed.ciphertext,
            "email_nonce" to resealed.nonce,
        )
    } finally {
        plaintext.fill(0)
    }
}

suspend fun list_recent_recipients(ctx: ResealContext): List<JsonObject> {
    val seen = LinkedHashMap<String?, JsonObject>()
    val walk = walk_id_pages(ctx, RECENT_RECIPIENTS_PATH, "recent_list", null) { data, _ ->
        for (row in data.objects("items")) seen[row.str("id")] = row
    }
    if (walk == PageWalk.DONE) return seen.values.toList()
    seen.clear()
    for (page in 0 until LISTS_MAX_PAGES_PER_RUN) {
        val path = "$RECENT_RECIPIENTS_PATH?limit=$RECENT_RECIPIENT_LIMIT&offset=${seen.size}"
        val data = require_ok(request_with_backoff(ctx) { ctx.api.get(path) }, "recent_list").as_object()
        val raw_rows = data?.get("items").as_array()
        val before = seen.size
        for (row in raw_rows) {
            val record = row as? JsonObject ?: continue
            seen[record.str("id")] = record
        }
        if (raw_rows.size < RECENT_RECIPIENT_LIMIT || seen.size == before) break
    }
    return seen.values.toList()
}

val recent_recipients_step: ResealStep = object : ResealStep {
    override val name = "recent_recipients"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "recent_recipients")
        if (progress.done) return StepResult.DONE
        val before = incomplete_count(ctx)
        val items = ArrayList<JsonObject>()
        for (row in list_recent_recipients(ctx)) {
            val item = build_recent_recipient_item(ctx, row)
            if (item != null) items.add(item)
        }
        for (batch in items.chunked(LIST_BATCH_SIZE)) {
            post_storage_rekey(ctx, "recent_recipients", batch)
            pause_between_batches(ctx)
        }
        if (incomplete_count(ctx) > before) return StepResult.INCOMPLETE
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}

private fun split_combined(ciphertext: ByteArray, nonce: ByteArray?): Pair<ByteArray, ByteArray>? {
    if (ciphertext.size <= RESEAL_NONCE_LENGTH + GCM_TAG_LENGTH) return null
    val embedded = ciphertext.copyOfRange(0, RESEAL_NONCE_LENGTH)
    if (nonce != null && nonce.isNotEmpty()) {
        if (nonce.size != RESEAL_NONCE_LENGTH) return null
        for (i in 0 until RESEAL_NONCE_LENGTH) {
            if (nonce[i] != embedded[i]) return null
        }
    }
    return ciphertext.copyOfRange(RESEAL_NONCE_LENGTH, ciphertext.size) to embedded
}

fun open_external_account(keys: ResealKeys, ciphertext_b64: String?, nonce_b64: String?): ExternalAccountOpen {
    val ciphertext = decode_b64(ciphertext_b64)
    val nonce = decode_b64(nonce_b64)
    if (ciphertext == null) return SealedState.Unreadable
    if (nonce != null && nonce.isNotEmpty()) {
        val current = gcm_open(keys.new_aes, ciphertext, nonce)
        if (current != null) {
            current.fill(0)
            return SealedState.Current
        }
        val stale = open_with_any(keys.old_aes, ciphertext, nonce)
        if (stale != null) return SealedState.Stale(stale)
    }
    val combined = split_combined(ciphertext, nonce) ?: return SealedState.Unreadable
    val opened = gcm_open(keys.new_aes, combined.first, combined.second)
        ?: open_with_any(keys.old_aes, combined.first, combined.second)
    return if (opened != null) SealedState.Stale(opened) else SealedState.Unreadable
}

val external_accounts_step: ResealStep = object : ResealStep {
    override val name = "external_accounts"

    override suspend fun run(ctx: ResealContext): StepResult {
        val progress = step_progress(ctx, "external_accounts")
        if (progress.done) return StepResult.DONE
        val data = require_ok(
            request_with_backoff(ctx) { ctx.api.get("/mail/v1/external_accounts") },
            "external_list",
        ).as_object()
        val items = ArrayList<JsonObject>()
        for (row in data.objects("accounts")) {
            val opened = open_external_account(ctx.keys, row.str("encrypted_account_data"), row.str("account_data_nonce"))
            val plaintext = when (opened) {
                is SealedState.Stale -> opened.plaintext
                SealedState.Current -> {
                    ctx.counts.current += 1
                    continue
                }
                SealedState.Unreadable -> {
                    ctx.counts.unreadable += 1
                    continue
                }
            }
            try {
                val email = lists_parse_object(plaintext).str("email")
                if (email.isNullOrEmpty()) {
                    ctx.counts.unreadable += 1
                    continue
                }
                val resealed = seal_under_new(ctx.keys, plaintext)
                items.add(
                    json_object(
                        "id" to row["id"],
                        "account_token" to sign_under_new(ctx.keys, EXTERNAL_ACCOUNTS_INFO, lists_trim(email.lowercase())),
                        "encrypted_account_data" to resealed.ciphertext,
                        "account_data_nonce" to resealed.nonce,
                        "integrity_hash" to sign_under_new(
                            ctx.keys,
                            EXTERNAL_ACCOUNTS_INFO,
                            "${resealed.ciphertext}:${resealed.nonce}:external-accounts-v1",
                        ),
                    ),
                )
            } finally {
                plaintext.fill(0)
            }
        }
        for (batch in items.chunked(LIST_BATCH_SIZE)) {
            post_storage_rekey(ctx, "external_accounts", batch)
            pause_between_batches(ctx)
        }
        progress.done = true
        ctx.persist()
        return StepResult.DONE
    }
}
