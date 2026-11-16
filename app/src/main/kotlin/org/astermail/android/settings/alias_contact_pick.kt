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

package org.astermail.android.settings

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.astermail.android.ui.contacts.Contact
import java.util.Locale

const val MAX_CONTACT_PICK = 200
const val CONTACT_PICK_CONCURRENCY = 4

enum class PickEmailStatus { AVAILABLE, ADDED, BLOCKED }

data class PickEmail(
    val address: String,
    val type: String,
    val label: String,
    val status: PickEmailStatus,
)

data class PickCandidate(
    val contact_id: String,
    val name: String,
    val avatar_url: String,
    val profile_color: String,
    val emails: List<PickEmail>,
) {
    val open_emails: List<PickEmail> get() = emails.filter { it.status == PickEmailStatus.AVAILABLE }
}

data class BulkAddResult(val added: List<String>, val failed: List<String>)

fun normalize_pick_address(value: String): String = value.trim().lowercase(Locale.ROOT)

fun is_pickable_address(address: String): Boolean {
    if (address.length > 254 || address.any { it.isWhitespace() }) return false
    val at = address.indexOf('@')
    if (at <= 0 || at != address.lastIndexOf('@') || at == address.length - 1) return false
    val domain = address.substring(at + 1)
    return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}

fun contact_pick_addresses(contacts: List<Contact>): List<String> {
    val seen = LinkedHashSet<String>()
    for (contact in contacts) {
        if (contact.deleted_at.isNotBlank()) continue
        for (entry in contact.email_entries()) {
            val address = normalize_pick_address(entry.value)
            if (is_pickable_address(address)) seen.add(address)
        }
    }
    return seen.toList()
}

fun resolve_pick_statuses(
    addresses: List<String>,
    pins: List<DecryptedAliasPin>,
    hash: (String) -> String,
): Map<String, PickEmailStatus> {
    val statuses = HashMap<String, PickEmailStatus>()
    val by_hash = HashMap<String, PickEmailStatus>()
    fun merge(target: MutableMap<String, PickEmailStatus>, key: String, blocked: Boolean) {
        if (blocked) {
            target[key] = PickEmailStatus.BLOCKED
        } else if (target[key] != PickEmailStatus.BLOCKED) {
            target[key] = PickEmailStatus.ADDED
        }
    }
    for (pin in pins) {
        val sender = normalize_pick_address(pin.sender)
        if (is_pickable_address(sender)) merge(statuses, sender, pin.is_blocked)
        if (pin.sender_hash.isNotBlank()) merge(by_hash, pin.sender_hash, pin.is_blocked)
    }
    if (by_hash.isNotEmpty()) {
        for (address in addresses) {
            val status = by_hash[hash(address)] ?: continue
            if (status == PickEmailStatus.BLOCKED || statuses[address] == null) statuses[address] = status
        }
    }
    return statuses
}

fun build_pick_candidates(
    contacts: List<Contact>,
    statuses: Map<String, PickEmailStatus>,
): List<PickCandidate> {
    val built = contacts.asSequence()
        .filter { it.deleted_at.isBlank() }
        .mapNotNull { contact ->
            val seen = HashSet<String>()
            val emails = contact.email_entries().mapNotNull { entry ->
                val address = normalize_pick_address(entry.value)
                if (!is_pickable_address(address) || !seen.add(address)) return@mapNotNull null
                PickEmail(address, entry.type, entry.label, statuses[address] ?: PickEmailStatus.AVAILABLE)
            }
            if (emails.isEmpty()) return@mapNotNull null
            PickCandidate(
                contact_id = contact.id,
                name = contact.name.trim(),
                avatar_url = contact.avatar_url,
                profile_color = contact.profile_color,
                emails = emails,
            )
        }
        .toList()
    return built.sortedWith(
        compareBy<PickCandidate> { it.open_emails.isEmpty() }
            .thenBy { (it.name.ifBlank { it.emails.first().address }).lowercase(Locale.ROOT) },
    )
}

fun filter_pick_candidates(candidates: List<PickCandidate>, query: String): List<PickCandidate> {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return candidates
    return candidates.filter { candidate ->
        candidate.name.lowercase(Locale.ROOT).contains(needle) ||
            candidate.emails.any { it.address.contains(needle) }
    }
}

fun toggle_pick(
    selected: Set<String>,
    address: String,
    checked: Boolean,
    limit: Int = MAX_CONTACT_PICK,
): Set<String> {
    val key = normalize_pick_address(address)
    if (!checked) return selected - key
    if (key in selected || selected.size >= limit) return selected
    return selected + key
}

fun select_all_visible(
    selected: Set<String>,
    visible: List<PickCandidate>,
    limit: Int = MAX_CONTACT_PICK,
): Set<String> {
    val next = LinkedHashSet(selected)
    for (candidate in visible) {
        if (next.size >= limit) break
        val open = candidate.open_emails
        if (open.isEmpty() || open.any { it.address in next }) continue
        next.add(open.first().address)
    }
    return next
}

fun clear_visible(selected: Set<String>, visible: List<PickCandidate>): Set<String> {
    val visible_addresses = visible.flatMapTo(HashSet()) { candidate -> candidate.emails.map { it.address } }
    return selected.filterTo(LinkedHashSet()) { it !in visible_addresses }
}

fun select_all_saturated(
    selected: Set<String>,
    visible: List<PickCandidate>,
    limit: Int = MAX_CONTACT_PICK,
): Boolean {
    val touches_visible = visible.any { candidate -> candidate.emails.any { it.address in selected } }
    return touches_visible && select_all_visible(selected, visible, limit).size == selected.size
}

fun finalize_picks(
    selected: Set<String>,
    statuses: Map<String, PickEmailStatus>,
    limit: Int = MAX_CONTACT_PICK,
): List<String> =
    selected.asSequence()
        .map(::normalize_pick_address)
        .filter { is_pickable_address(it) && statuses[it] == null }
        .distinct()
        .take(limit)
        .toList()

suspend fun add_senders_in_batches(
    addresses: List<String>,
    concurrency: Int = CONTACT_PICK_CONCURRENCY,
    add: suspend (String) -> Boolean,
): BulkAddResult {
    if (addresses.isEmpty()) return BulkAddResult(emptyList(), emptyList())
    val gate = Semaphore(concurrency.coerceAtLeast(1))
    val outcomes = coroutineScope {
        addresses.map { address ->
            async {
                gate.withPermit {
                    val ok = try {
                        add(address)
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        false
                    }
                    address to ok
                }
            }
        }.awaitAll()
    }
    return BulkAddResult(
        added = outcomes.filter { it.second }.map { it.first },
        failed = outcomes.filterNot { it.second }.map { it.first },
    )
}
