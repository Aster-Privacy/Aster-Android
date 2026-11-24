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

package org.astermail.android.mail

import org.astermail.android.api.network.TransientFailureSignal
import org.astermail.android.storage.SessionKeyStore

data class DecryptReadiness(
    val keys_settled: Boolean,
    val key_epoch: Long,
    val failure_epoch: Long,
)

enum class DecryptVerdict { failed, pending }

fun capture_decrypt_readiness(store: SessionKeyStore): DecryptReadiness = DecryptReadiness(
    keys_settled = store.keys_settled(),
    key_epoch = store.key_readiness_epoch(),
    failure_epoch = TransientFailureSignal.epoch(),
)

fun classify_decrypt_failure(
    before: DecryptReadiness,
    after: DecryptReadiness,
    transient_error: Boolean = false,
): DecryptVerdict = if (
    !transient_error &&
    before.keys_settled &&
    after.keys_settled &&
    before.key_epoch == after.key_epoch &&
    before.failure_epoch == after.failure_epoch
) {
    DecryptVerdict.failed
} else {
    DecryptVerdict.pending
}

fun is_transient_decrypt_error(error: Throwable?): Boolean {
    var current = error
    var depth = 0
    while (current != null && depth < 8) {
        if (current is kotlinx.coroutines.CancellationException) return true
        if (current is java.io.IOException) return true
        if (current is java.security.KeyStoreException) return true
        if (current is java.security.ProviderException) return true
        if (current is android.security.keystore.UserNotAuthenticatedException) return true
        if (current is io.ktor.client.plugins.ResponseException &&
            TransientFailureSignal.is_transient_status(current.response.status.value)
        ) {
            return true
        }
        current = current.cause
        depth += 1
    }
    return false
}

fun is_envelope_pending_row(item: InboxItem): Boolean =
    item.is_decrypt_pending && item.is_undecryptable && !item.is_envelope_missing

fun settle_ratchet_result(
    result: String,
    before: DecryptReadiness,
    after: DecryptReadiness,
): String? = when {
    result != org.astermail.android.mail.ratchet.RATCHET_UNDECRYPTABLE_SENTINEL -> result
    classify_decrypt_failure(before, after) == DecryptVerdict.failed -> result
    else -> null
}

val decrypt_settle_delays_ms = listOf(400L, 2_000L, 6_000L, 15_000L, 30_000L, 60_000L)

fun envelope_pending_ids(items: List<InboxItem>): Set<String> =
    items.asSequence().filter(::is_envelope_pending_row).map { it.id }.toCollection(LinkedHashSet())

fun replace_settled_items(items: List<InboxItem>, settled: Map<String, InboxItem>): List<InboxItem> {
    if (settled.isEmpty()) return items
    return items.map { item ->
        val replacement = settled[item.id]
        if (replacement != null && is_envelope_pending_row(item) && !is_envelope_pending_row(replacement) &&
            !replacement.is_envelope_missing
        ) {
            replacement.copy(
                is_read = item.is_read,
                is_starred = item.is_starred,
                is_trashed = item.is_trashed,
                is_archived = item.is_archived,
                is_spam = item.is_spam,
                labels = item.labels,
                tag_tokens = item.tag_tokens,
                thread_token = item.thread_token,
                thread_message_count = item.thread_message_count,
            )
        } else {
            item
        }
    }
}
