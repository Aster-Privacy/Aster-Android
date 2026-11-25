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

package org.astermail.android.ui.compose

import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.settings.sending_block_kind_alias
import org.astermail.android.api.settings.sending_block_kind_domain_address
import org.astermail.android.api.settings.sending_block_kind_primary

private val aster_dotless_domains = setOf("astermail.org", "aster.cx")

const val queued_send_timeout_ms = 15_000L

fun canonical_sender_key(address: String): String {
    val lowered = address.trim().lowercase()
    val at = lowered.lastIndexOf('@')
    if (at <= 0) return lowered
    val local = lowered.substring(0, at)
    val domain = lowered.substring(at + 1)
    return if (domain in aster_dotless_domains) "${local.replace(".", "")}@$domain" else lowered
}

fun same_sender_address(left: String?, right: String?): Boolean {
    if (left.isNullOrBlank() || right.isNullOrBlank()) return false
    return canonical_sender_key(left) == canonical_sender_key(right)
}

private fun strip_plus_tag(address: String): String? {
    val trimmed = address.trim()
    val at = trimmed.lastIndexOf('@')
    if (at <= 0) return null
    val local = trimmed.substring(0, at)
    val plus = local.indexOf('+')
    if (plus <= 0) return null
    return local.substring(0, plus) + trimmed.substring(at)
}

private fun extract_bare_address(value: String): String {
    val trimmed = value.trim()
    val open = trimmed.lastIndexOf('<')
    val close = trimmed.lastIndexOf('>')
    return if (open >= 0 && close > open) trimmed.substring(open + 1, close).trim() else trimmed
}

fun match_sender_option(candidate: String, alias_options: List<String>): String? {
    val address = extract_bare_address(candidate)
    if (address.isEmpty()) return null
    alias_options.firstOrNull { it.trim().equals(address, ignoreCase = true) }?.let { return it }
    alias_options.firstOrNull { same_sender_address(it, address) }?.let { return it }
    val base = strip_plus_tag(address) ?: return null
    alias_options.firstOrNull { it.trim().equals(base, ignoreCase = true) }?.let { return it }
    return alias_options.firstOrNull { same_sender_address(it, base) }
}

fun expected_sender_candidates(message: compose_thread_message?): List<String> {
    if (message == null) return emptyList()
    val raw = if (message.is_sent) {
        listOf(message.sender_email)
    } else {
        listOfNotNull(message.delivered_to) + message.to_addresses + message.cc_addresses
    }
    return raw.map { it.trim() }.filter { it.isNotEmpty() }
}

fun expected_sender_address(
    message: compose_thread_message?,
    alias_options: List<String>,
    user_email: String,
): String? {
    val matches = expected_sender_candidates(message).mapNotNull { match_sender_option(it, alias_options) }
    return matches.firstOrNull { !same_sender_address(it, user_email) } ?: matches.firstOrNull()
}

fun target_thread_message(
    reply_to: String?,
    thread: compose_thread_snapshot,
): compose_thread_message? =
    thread.messages.firstOrNull { it.id == reply_to }
        ?: thread.messages.filterNot { it.is_sent }.maxByOrNull { it.timestamp }
        ?: thread.messages.lastOrNull()

data class sender_resolution_state(
    val is_thread_compose: Boolean,
    val is_draft: Boolean,
    val manually_selected: Boolean,
    val from_address: String,
    val thread_settled: Boolean,
    val identities_settled: Boolean,
    val draft_settled: Boolean,
    val has_pending_change: Boolean,
)

fun sender_resolution_pending(state: sender_resolution_state): Boolean {
    if (state.manually_selected && state.from_address.isNotBlank()) return false
    if (state.is_draft) return !state.draft_settled || state.from_address.isBlank()
    if (state.from_address.isBlank()) return true
    if (!state.is_thread_compose) return false
    if (!state.thread_settled || !state.identities_settled) return true
    return state.has_pending_change
}

enum class queued_send_action { idle, wait, fire, drop }

fun queued_send_step(
    queued_at_ms: Long?,
    now_ms: Long,
    pending: Boolean,
    timeout_ms: Long = queued_send_timeout_ms,
): queued_send_action {
    if (queued_at_ms == null) return queued_send_action.idle
    if (!pending) return queued_send_action.fire
    if (now_ms - queued_at_ms >= timeout_ms) return queued_send_action.drop
    return queued_send_action.wait
}

enum class send_gate_action { queue, blocked, confirm_mismatch, proceed }

fun send_gate(
    sender_pending: Boolean,
    skip_from_guard: Boolean,
    from_blocked: Boolean,
    mismatch_unacknowledged: Boolean,
): send_gate_action = when {
    sender_pending && !skip_from_guard -> send_gate_action.queue
    from_blocked -> send_gate_action.blocked
    mismatch_unacknowledged -> send_gate_action.confirm_mismatch
    else -> send_gate_action.proceed
}

fun sender_mismatch(
    is_thread_compose: Boolean,
    resolved: Boolean,
    expected_address: String?,
    from_address: String,
): Boolean {
    if (!is_thread_compose || !resolved) return false
    val expected = expected_address?.trim().orEmpty()
    if (expected.isEmpty() || from_address.isBlank()) return false
    return !same_sender_address(expected, from_address)
}

const val primary_sending_block_id = "primary"

data class sending_block_row(
    val kind: String,
    val id: String,
    val address: String,
    val display_name: String,
    val sending_disabled: Boolean,
)

fun sending_block_key(address: String): String {
    val bare = extract_bare_address(address).lowercase()
    val at = bare.lastIndexOf('@')
    if (at <= 0) return bare
    val local = bare.substring(0, at).substringBefore('+').replace(".", "")
    return "$local@${bare.substring(at + 1)}"
}

private fun is_listable_address(address: String): Boolean {
    val at = address.lastIndexOf('@')
    return at > 0 && at < address.length - 1
}

fun sending_block_rows(
    primary_address: String?,
    primary_blocked: Boolean,
    aliases: List<AliasInfo>,
    ghost_aliases: List<GhostAlias>,
    domain_addresses: List<CustomDomainAddressInfo>,
    blocked_alias_ids: Set<String>,
    blocked_domain_address_ids: Set<String>,
    query: String = "",
): List<sending_block_row> {
    val primary_row = primary_address?.trim()?.takeIf { is_listable_address(it) }?.let {
        sending_block_row(
            kind = sending_block_kind_primary,
            id = primary_sending_block_id,
            address = it,
            display_name = "",
            sending_disabled = primary_blocked,
        )
    }
    val alias_rows = aliases
        .filter { !it.decryption_failed && is_listable_address(it.address) }
        .map {
            sending_block_row(
                kind = sending_block_kind_alias,
                id = it.id,
                address = it.address,
                display_name = it.encrypted_display_name?.trim().orEmpty(),
                sending_disabled = it.id in blocked_alias_ids,
            )
        }
    val alias_ids = alias_rows.mapTo(mutableSetOf()) { it.id }
    val ghost_rows = ghost_aliases
        .filter { !it.decryption_failed && it.id !in alias_ids && is_listable_address(it.decrypted_address) }
        .map {
            sending_block_row(
                kind = sending_block_kind_alias,
                id = it.id,
                address = it.decrypted_address,
                display_name = "",
                sending_disabled = it.id in blocked_alias_ids,
            )
        }
    val domain_rows = domain_addresses
        .filter { !it.decryption_failed && is_listable_address(it.address) }
        .map {
            sending_block_row(
                kind = sending_block_kind_domain_address,
                id = it.id,
                address = it.address,
                display_name = it.encrypted_display_name?.trim().orEmpty(),
                sending_disabled = it.id in blocked_domain_address_ids,
            )
        }
    val others = (alias_rows + ghost_rows + domain_rows).sortedBy { it.address.lowercase() }
    return filter_sending_block_rows(listOfNotNull(primary_row) + others, query)
}

fun filter_sending_block_rows(rows: List<sending_block_row>, query: String): List<sending_block_row> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return rows
    return rows.filter { it.address.lowercase().contains(needle) || it.display_name.lowercase().contains(needle) }
}

fun every_sending_address_disabled(rows: List<sending_block_row>): Boolean =
    rows.isNotEmpty() && rows.all { it.sending_disabled }

fun sending_disabled_addresses(
    primary_address: String?,
    primary_blocked: Boolean,
    aliases: List<AliasInfo>,
    ghost_aliases: List<GhostAlias>,
    domain_addresses: List<CustomDomainAddressInfo>,
    blocked_alias_ids: Set<String>,
    blocked_domain_address_ids: Set<String>,
): Set<String> {
    val keys = mutableSetOf<String>()
    if (primary_blocked && !primary_address.isNullOrBlank()) keys.add(sending_block_key(primary_address))
    aliases.filter { it.id in blocked_alias_ids && !it.decryption_failed && is_listable_address(it.address) }
        .forEach { keys.add(sending_block_key(it.address)) }
    ghost_aliases.filter { it.id in blocked_alias_ids && !it.decryption_failed && is_listable_address(it.decrypted_address) }
        .forEach { keys.add(sending_block_key(it.decrypted_address)) }
    domain_addresses.filter { it.id in blocked_domain_address_ids && !it.decryption_failed && is_listable_address(it.address) }
        .forEach { keys.add(sending_block_key(it.address)) }
    return keys
}

fun is_sending_disabled_sender(address: String, disabled_keys: Set<String>): Boolean =
    address.isNotBlank() && sending_block_key(address) in disabled_keys
