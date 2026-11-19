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

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.astermail.android.api.ApiError
import org.astermail.android.mail.ratchet.PostQuantumCoverage
import org.astermail.android.mail.ratchet.PostQuantumUnavailableException
import org.astermail.android.mail.ratchet.RatchetEncryptionException
import org.astermail.android.mail.ratchet.RatchetIdentityPinException
import org.astermail.android.mail.ratchet.RecipientPublicKeyMissingException

typealias RecipientKeyLookup = suspend (username: String, email: String) -> Unit

class RecipientNotOnAsterException(
    val recipients: List<String>,
    message: String,
) : E2eEncryptionException(message)

sealed interface PostQuantumSendGate {
    data object Proceed : PostQuantumSendGate
    data class NotOnAster(val recipients: List<String>) : PostQuantumSendGate
    data class Prompt(val coverage: PostQuantumCoverage) : PostQuantumSendGate
}

fun is_existence_checkable(email: String): Boolean {
    val normalized = email.trim().lowercase(Locale.ROOT)
    return is_aster_domain_recipient(normalized) && !normalized.endsWith("@$ASTER_GHOST_ALIAS_DOMAIN")
}

fun post_quantum_candidates(recipients: List<String>): List<String> =
    recipients.filter { is_internal_recipient(it) }

suspend fun is_unregistered_aster_address(email: String, lookup: RecipientKeyLookup): Boolean {
    if (!is_existence_checkable(email)) return false
    val address = email.trim()
    val username = address.substringBefore('@').trim()
    if (username.isEmpty()) return false
    return try {
        lookup(username, address)
        false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (missing: ApiError.NotFoundError) {
        true
    } catch (other: Throwable) {
        false
    }
}

suspend fun collect_unregistered_aster_addresses(
    recipients: List<String>,
    lookup: RecipientKeyLookup,
): List<String> = coroutineScope {
    recipients
        .filter { is_existence_checkable(it) }
        .distinctBy { it.trim().lowercase(Locale.ROOT) }
        .map { recipient -> async { recipient.takeIf { is_unregistered_aster_address(it, lookup) } } }
        .awaitAll()
        .filterNotNull()
}

suspend fun post_quantum_send_gate(
    coverage: PostQuantumCoverage,
    find_unregistered: suspend (List<String>) -> List<String>,
): PostQuantumSendGate {
    if (coverage.missing.isEmpty()) return PostQuantumSendGate.Proceed
    val unregistered = find_unregistered(coverage.missing)
    if (unregistered.isNotEmpty()) return PostQuantumSendGate.NotOnAster(unregistered)
    return PostQuantumSendGate.Prompt(coverage)
}

suspend fun recipients_not_on_aster_for_failure(
    error: Throwable,
    recipients: List<String>,
    find_unregistered: suspend (List<String>) -> List<String>,
): List<String> {
    val applies = when (error) {
        is RatchetIdentityPinException -> false
        is PostQuantumUnavailableException,
        is RatchetEncryptionException,
        is RecipientPublicKeyMissingException,
        -> true
        else -> false
    }
    if (!applies) return emptyList()
    return find_unregistered(recipients)
}
