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

package org.astermail.android.security

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SenderTrustMessage(
    val sender_name: String? = null,
    val sender_email: String? = null,
    val display_sender_name: String? = null,
    val display_sender_email: String? = null,
    val reply_to: String? = null,
    val spf_result: String? = null,
    val dkim_result: String? = null,
    val dmarc_result: String? = null,
    val sender_aligned: Boolean? = null,
    val sender_verified: Boolean? = null,
    val sender_verified_domain: String? = null,
    val is_external: Boolean? = null,
    val sender_verification: String? = null,
)

data class SenderTrustContext(
    val own_addresses: List<String> = emptyList(),
    val contacts: List<SenderTrustContact> = emptyList(),
)

private fun folded_address(value: String): String = value.trim().lowercase(Locale.ROOT)

private fun recorded_status(raw: String?): String {
    val value = (raw ?: "").trim().lowercase(Locale.ROOT)
    return if (value == "missing") "" else value
}

fun sender_trust_input(
    message: SenderTrustMessage,
    context: SenderTrustContext,
    shown_name: String? = null,
    shown_email: String? = null,
): SenderTrustInput {
    val actual_email = message.sender_email ?: ""
    val email = shown_email ?: message.display_sender_email ?: actual_email
    val name = shown_name ?: message.display_sender_name ?: message.sender_name ?: ""
    val is_rewritten = folded_address(email) != folded_address(actual_email)
    val aligned = message.sender_aligned
    val dmarc = recorded_status(message.dmarc_result)
    val unrecorded = recorded_status(message.spf_result).isEmpty() &&
        recorded_status(message.dkim_result).isEmpty() &&
        dmarc.isEmpty()
    val carrier_trusted = aligned == true ||
        (aligned == null && (unrecorded || dmarc == "pass"))
    val drop_auth = is_rewritten && carrier_trusted
    return SenderTrustInput(
        name = name,
        email = email,
        reply_to = message.reply_to,
        spf_result = if (drop_auth) null else message.spf_result,
        dkim_result = if (drop_auth) null else message.dkim_result,
        dmarc_result = if (drop_auth) null else message.dmarc_result,
        sender_aligned = if (drop_auth) null else if (is_rewritten) false else aligned,
        sender_verified = if (is_rewritten) {
            false
        } else {
            message.sender_verified ?: !message.sender_verified_domain.isNullOrEmpty()
        },
        sender_verified_domain = if (is_rewritten) null else message.sender_verified_domain,
        is_internal = !is_rewritten &&
            message.is_external == false &&
            message.sender_verification != "invalid",
        own_addresses = context.own_addresses,
        contacts = context.contacts,
    )
}

internal data class SenderTrustCacheKey(
    val name: String?,
    val email: String?,
    val reply_to: String?,
    val spf_result: String?,
    val dkim_result: String?,
    val dmarc_result: String?,
    val sender_aligned: Boolean?,
    val sender_verified: Boolean?,
    val sender_verified_domain: String?,
    val is_internal: Boolean,
)

internal class SenderTrustResultCache(
    private val max_entries: Int = 512,
    private val max_key_length: Int = 2048,
    private val evaluate: (SenderTrustInput) -> SenderTrust = ::evaluate_sender_trust,
) {
    private var context: SenderTrustContext? = null
    private val entries = object : LinkedHashMap<SenderTrustCacheKey, SenderTrust>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SenderTrustCacheKey, SenderTrust>?): Boolean =
            size > max_entries
    }

    private fun key_of(input: SenderTrustInput): SenderTrustCacheKey? {
        val length = listOf(
            input.name,
            input.email,
            input.reply_to,
            input.spf_result,
            input.dkim_result,
            input.dmarc_result,
            input.sender_verified_domain,
        ).sumOf { it?.length ?: 0 }
        if (length > max_key_length) return null
        return SenderTrustCacheKey(
            name = input.name,
            email = input.email,
            reply_to = input.reply_to,
            spf_result = input.spf_result,
            dkim_result = input.dkim_result,
            dmarc_result = input.dmarc_result,
            sender_aligned = input.sender_aligned,
            sender_verified = input.sender_verified,
            sender_verified_domain = input.sender_verified_domain,
            is_internal = input.is_internal,
        )
    }

    fun trust_for(input: SenderTrustInput, context: SenderTrustContext): SenderTrust {
        if (input.own_addresses !== context.own_addresses || input.contacts !== context.contacts) {
            return evaluate(input)
        }
        val key = key_of(input) ?: return evaluate(input)
        synchronized(this) {
            if (this.context !== context) {
                entries.clear()
                this.context = context
            }
            val cached = entries[key]
            if (cached != null) return cached
        }
        val result = evaluate(input)
        synchronized(this) {
            if (this.context === context) entries[key] = result
        }
        return result
    }

    @Synchronized
    fun clear() {
        entries.clear()
        context = null
    }
}

private val sender_trust_results = SenderTrustResultCache()

private fun cached_sender_trust(input: SenderTrustInput, context: SenderTrustContext): SenderTrust =
    if (context === SenderTrustStore.context.value) {
        sender_trust_results.trust_for(input, context)
    } else {
        evaluate_sender_trust(input)
    }

fun sender_trust_for(
    message: SenderTrustMessage?,
    context: SenderTrustContext,
    shown_name: String? = null,
    shown_email: String? = null,
): SenderTrust? {
    if (message == null) return null
    return cached_sender_trust(sender_trust_input(message, context, shown_name, shown_email), context)
}

fun trusted_sender_name(trust: SenderTrust?, raw_name: String, placeholder: String): String {
    if (trust == null) return raw_name.ifEmpty { placeholder }
    return trust.name.ifEmpty { trust.address.ifEmpty { placeholder } }
}

fun trusted_sender_name(message: SenderTrustMessage?, raw_name: String, placeholder: String): String =
    trusted_sender_name(sender_trust_for(message, SenderTrustStore.context.value), raw_name, placeholder)

fun safe_sender_name(name: String?, email: String?): String {
    if (name.isNullOrEmpty()) return ""
    val context = SenderTrustStore.context.value
    return cached_sender_trust(
        SenderTrustInput(
            name = name,
            email = email ?: "",
            own_addresses = context.own_addresses,
            contacts = context.contacts,
        ),
        context,
    ).name
}

fun reply_to_header_value(raw_headers: List<Pair<String, String>>): String? {
    val values = raw_headers
        .filter { it.first.equals("reply-to", ignoreCase = true) }
        .map { it.second }
        .filter { it.isNotBlank() }
    return if (values.isEmpty()) null else values.joinToString(", ")
}

object SenderTrustStore {
    private const val max_own_addresses = 2000
    private const val max_contacts = 10000

    private val state = MutableStateFlow(SenderTrustContext())
    val context: StateFlow<SenderTrustContext> = state.asStateFlow()

    private var primary_address: String? = null
    private val extra_addresses = LinkedHashMap<String, List<String>>()
    private var generation = 0L

    @Synchronized
    fun current_generation(): Long = generation

    @Synchronized
    fun set_primary_address(address: String?) {
        val next = address?.takeIf { it.isNotBlank() }
        if (next == primary_address) return
        primary_address = next
        publish_own_addresses()
    }

    @Synchronized
    fun set_own_addresses(source: String, addresses: Collection<String>) {
        val next = addresses.toList()
        if (extra_addresses[source] == next) return
        extra_addresses[source] = next
        publish_own_addresses()
    }

    @Synchronized
    fun set_contacts(contacts: List<SenderTrustContact>, expected_generation: Long = generation) {
        if (expected_generation != generation) return
        val next = ArrayList<SenderTrustContact>()
        for (contact in contacts) {
            if (next.size >= max_contacts) break
            val emails = contact.emails.filter { it.contains('@') }
            if (emails.isEmpty()) continue
            next.add(SenderTrustContact(contact.name, emails.toList()))
        }
        val current = state.value
        if (current.contacts == next) return
        state.value = current.copy(contacts = java.util.Collections.unmodifiableList(next))
    }

    @Synchronized
    fun clear_contacts() {
        generation += 1
        sender_trust_results.clear()
        val current = state.value
        if (current.contacts.isEmpty()) return
        state.value = current.copy(contacts = emptyList())
    }

    @Synchronized
    fun clear() {
        generation += 1
        sender_trust_results.clear()
        primary_address = null
        extra_addresses.clear()
        if (state.value.own_addresses.isEmpty() && state.value.contacts.isEmpty()) return
        state.value = SenderTrustContext()
    }

    private fun publish_own_addresses() {
        val seen = java.util.TreeSet<String>()
        val candidates = ArrayList<String>()
        primary_address?.let { candidates.add(it) }
        for (addresses in extra_addresses.values) candidates.addAll(addresses)
        for (candidate in candidates) {
            val normalized = folded_address(candidate)
            if (normalized.contains('@')) seen.add(normalized)
        }
        val next = seen.take(max_own_addresses)
        val current = state.value
        if (current.own_addresses == next) return
        state.value = current.copy(own_addresses = java.util.Collections.unmodifiableList(next))
    }
}
