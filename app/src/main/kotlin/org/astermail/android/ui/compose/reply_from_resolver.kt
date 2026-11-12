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

import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.crypto.same_address_ignoring_dots
import org.astermail.android.mail.is_sendable_address
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

fun compute_received_on_alias(
    recipient_addresses: List<String>,
    alias_options: List<String>,
    user_email: String,
): String? {
    val options_by_lower = alias_options.associateBy { it.lowercase() }
    val matches = recipient_addresses
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { options_by_lower[it.lowercase()] }
    return matches.firstOrNull { !same_address_ignoring_dots(it, user_email) }
        ?: matches.firstOrNull()
}

data class ReactionSenderIdentity(
    val email: String,
    val alias_hash: String?,
)

private fun reaction_alias_identity(
    address: String?,
    alias_options: List<String>,
    alias_hash_map: Map<String, String>,
    user_email: String,
): ReactionSenderIdentity? {
    val target = address?.trim().orEmpty()
    if (target.isEmpty()) return null
    val option = alias_options.firstOrNull { it.trim().equals(target, ignoreCase = true) } ?: return null
    if (same_address_ignoring_dots(option, user_email)) return null
    val hash = alias_hash_map.entries
        .firstOrNull { it.key.trim().equals(option.trim(), ignoreCase = true) }
        ?.value
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return ReactionSenderIdentity(option, hash)
}

fun resolve_reaction_sender_identity(
    own_recipient_addresses: List<String>,
    message_sender_email: String,
    is_own_message: Boolean,
    alias_options: List<String>,
    alias_hash_map: Map<String, String>,
    user_email: String,
    delivered_to: String? = null,
    routing_token: String? = null,
    routing_token_map: Map<String, String> = emptyMap(),
): ReactionSenderIdentity? {
    if (user_email.isBlank()) return null
    val primary = ReactionSenderIdentity(user_email, null)
    if (is_own_message) {
        val sender = message_sender_email.trim()
        if (sender.isEmpty()) return null
        if (same_address_ignoring_dots(sender, user_email)) return primary
        return reaction_alias_identity(sender, alias_options, alias_hash_map, user_email)
    }
    val token = routing_token?.trim().orEmpty()
    if (token.isNotEmpty()) {
        reaction_alias_identity(routing_token_map[token], alias_options, alias_hash_map, user_email)
            ?.let { return it }
    }
    val delivered = delivered_to?.trim().orEmpty()
    val delivered_to_primary = delivered.isNotEmpty() && same_address_ignoring_dots(delivered, user_email)
    if (delivered.isNotEmpty() && !delivered_to_primary) {
        reaction_alias_identity(delivered, alias_options, alias_hash_map, user_email)?.let { return it }
    }
    val visible = own_recipient_addresses.map { it.trim() }.filter { it.isNotEmpty() }
    if (visible.any { same_address_ignoring_dots(it, user_email) }) return primary
    visible.firstNotNullOfOrNull {
        reaction_alias_identity(it, alias_options, alias_hash_map, user_email)
    }?.let { return it }
    if (delivered_to_primary) return primary
    return null
}

fun domain_address_routing_token(local_part: String, domain: String): String {
    val normalized_local_part = local_part.trim().lowercase(Locale.ROOT).replace(".", "")
    val address = "$normalized_local_part@${domain.trim().lowercase(Locale.ROOT)}"
    val digest = MessageDigest.getInstance("SHA-256").digest(address.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(digest)
}

fun reaction_routing_token_map(
    aliases: List<AliasInfo>,
    custom_domain_addresses: List<CustomDomainAddressInfo>,
    ghost_aliases: List<GhostAlias>,
): Map<String, String> {
    val tokens = mutableMapOf<String, String>()
    aliases
        .filter { it.is_enabled && !it.decryption_failed && it.address.contains('@') }
        .filter { it.alias_address_hash.isNotBlank() }
        .forEach { tokens[it.alias_address_hash] = it.address }
    ghost_aliases
        .filter { it.is_enabled && !it.decryption_failed && it.decrypted_address.contains('@') }
        .filter { it.alias_address_hash.isNotBlank() }
        .forEach { tokens[it.alias_address_hash] = it.decrypted_address }
    custom_domain_addresses
        .filter { it.is_enabled && !it.decryption_failed && it.address.contains('@') }
        .forEach {
            val local_part = it.address.substringBeforeLast('@')
            val domain = it.address.substringAfterLast('@')
            if (local_part.isNotBlank() && domain.isNotBlank()) {
                tokens[domain_address_routing_token(local_part, domain)] = it.address
            }
        }
    return tokens.toMap()
}

fun reply_from_mismatch(
    mode: String?,
    received_on_alias: String?,
    from_alias: String,
): Boolean {
    if (mode != "reply" && mode != "reply_all") return false
    val received = received_on_alias?.trim().orEmpty()
    if (received.isEmpty()) return false
    return !received.equals(from_alias.trim(), ignoreCase = true)
}

const val from_tier_thread = 0

const val from_tier_ghost = 1

const val from_tier_selected_alias = 2

const val from_tier_pinned = 3

const val from_tier_fallback = 4

data class resolved_from_alias(
    val address: String,
    val tier: Int,
)

fun resolve_from_alias_tiered(
    received_on_alias: String?,
    thread_ghost_match: String?,
    primary_sender_email: String,
    alias_options: List<String>,
    selected_alias: String? = null,
): resolved_from_alias {
    received_on_alias?.takeIf { it.isNotBlank() }?.let {
        return resolved_from_alias(it, from_tier_thread)
    }
    matching_alias_option(thread_ghost_match, alias_options)?.let {
        return resolved_from_alias(it, from_tier_ghost)
    }
    matching_alias_option(selected_alias, alias_options)?.let {
        return resolved_from_alias(it, from_tier_selected_alias)
    }
    matching_alias_option(primary_sender_email, alias_options)?.let {
        return resolved_from_alias(it, from_tier_pinned)
    }
    return resolved_from_alias(alias_options.firstOrNull().orEmpty(), from_tier_fallback)
}

fun next_from_alias(
    current: String,
    current_tier: Int,
    resolved: resolved_from_alias,
    alias_options: List<String>,
    manually_selected: Boolean,
): resolved_from_alias? {
    if (manually_selected) return null
    if (alias_options.isEmpty()) return null
    if (resolved.address.isBlank()) return null
    val current_is_option = current.isNotBlank() &&
        alias_options.any { it.equals(current, ignoreCase = true) }
    if (current_is_option && resolved.tier > current_tier) return null
    if (resolved.address == current && resolved.tier == current_tier) return null
    return resolved
}

fun resolve_reply_from_alias(
    received_on_alias: String?,
    thread_ghost_match: String?,
    primary_sender_email: String,
    alias_options: List<String>,
): String = resolve_from_alias_tiered(
    received_on_alias,
    thread_ghost_match,
    primary_sender_email,
    alias_options,
).address

fun owned_sender_addresses(
    user_email: String,
    aliases: List<AliasInfo>,
    custom_domain_addresses: List<CustomDomainAddressInfo>,
): List<String> {
    val owned = mutableListOf<String>()
    if (is_sendable_address(user_email)) owned.add(user_email)
    aliases
        .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
        .forEach { owned.add(it.address) }
    custom_domain_addresses
        .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
        .forEach { owned.add(it.address) }
    return owned
}

fun selected_alias_for_new_compose(
    enabled: Boolean,
    selected_alias: String?,
    reply_to: String?,
    mode: String?,
    draft_id: String?,
    owned_senders: List<String>,
): String? {
    if (!enabled) return null
    if (!reply_to.isNullOrBlank() || !draft_id.isNullOrBlank()) return null
    if (mode != null && mode != "new") return null
    return matching_alias_option(selected_alias, owned_senders)
}

private fun matching_alias_option(value: String?, alias_options: List<String>): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return alias_options.firstOrNull { it.trim().equals(trimmed, ignoreCase = true) }
}
