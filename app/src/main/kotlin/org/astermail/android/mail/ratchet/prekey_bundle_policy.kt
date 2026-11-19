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

package org.astermail.android.mail.ratchet

object PrekeyBundlePolicy {
    const val require_verified_prekey_bundle: Boolean = true
}

class UnverifiedPrekeyBundleException(
    recipient: String?,
) : RatchetEncryptionException(recipient, "recipient prekey bundle is not verified")

class RecipientPublicKeyMissingException(
    val recipients: List<String>,
) : Exception("no public key for ${recipients.joinToString(", ")}")

class PgpFallbackSealException(message: String) : Exception(message)

const val OWNER_KEY_SEND_PIN_WINDOW_MS = 10L * 60L * 1000L

fun owner_key_pinned_during_send(pinned_at: Long?, now_ms: Long): Boolean =
    pinned_at != null && now_ms >= pinned_at && now_ms - pinned_at < OWNER_KEY_SEND_PIN_WINDOW_MS

fun prekey_bundle_needs_pgp_fallback(
    require_verified: Boolean,
    result: PrekeyBindingResult,
    pinned_owner: String?,
    served_owner: String?,
): Boolean {
    if (!require_verified) return false
    return when (result) {
        PrekeyBindingResult.UNSIGNED_LEGACY -> owner_key_trusted(pinned_owner, served_owner)
        PrekeyBindingResult.UNVERIFIABLE -> pinned_owner.isNullOrBlank()
        else -> false
    }
}

fun owner_key_trusted(pinned_owner: String?, served_owner: String?): Boolean {
    if (pinned_owner.isNullOrBlank()) return true
    return !served_owner.isNullOrBlank() && served_owner.equals(pinned_owner, ignoreCase = true)
}

const val ONE_TIME_PREKEY_WITHHELD_WARNING =
    "a recipient server sent no one-time prekey, so the message was sealed with the signed prekey alone and has downgraded forward secrecy for its first reply"

suspend fun seal_internal_body(
    recipients: List<String>,
    allow_non_post_quantum: Boolean,
    seal_ratchet: suspend (without_one_time: Boolean) -> String?,
    seal_pgp: suspend () -> String,
    warn: (String) -> Unit = {},
): String? {
    return try {
        try {
            try {
                seal_ratchet(false)
            } catch (withheld: OneTimePrekeyWithheldException) {
                seal_ratchet(false)
            }
        } catch (withheld: OneTimePrekeyWithheldException) {
            warn(ONE_TIME_PREKEY_WITHHELD_WARNING)
            seal_ratchet(true)
        }
    } catch (e: RatchetEncryptionException) {
        if (
            e !is UnverifiedPrekeyBundleException &&
            e !is RecoveryLaneDowngradeException
        ) {
            throw e
        }
        if (!allow_non_post_quantum) throw PostQuantumUnavailableException(recipients)
        seal_pgp()
    }
}

suspend fun seal_pgp_for_recipients(
    recipients: List<String>,
    own_public_key: String?,
    fetch_public_key: suspend (String) -> String?,
    is_owner_key_trusted: (String, String) -> Boolean,
    can_encrypt_to: (String) -> Boolean,
    encrypt_and_sign: (List<String>) -> String?,
): String {
    val keys = ArrayList<String>()
    val missing = ArrayList<String>()
    val seen = HashSet<String>()
    for (recipient in recipients) {
        if (!seen.add(recipient.trim().lowercase(java.util.Locale.ROOT))) continue
        val key = fetch_public_key(recipient)?.takeIf { it.isNotBlank() && can_encrypt_to(it) }
        if (key == null) {
            missing.add(recipient)
            continue
        }
        if (!is_owner_key_trusted(recipient, key)) {
            throw RecipientKeyUntrustedException(recipient, "recipient key is not trusted")
        }
        if (key !in keys) keys.add(key)
    }
    if (missing.isNotEmpty()) throw RecipientPublicKeyMissingException(missing)
    if (keys.isEmpty()) throw RecipientPublicKeyMissingException(recipients)
    val own = own_public_key?.takeIf { it.isNotBlank() && can_encrypt_to(it) }
        ?: throw PgpFallbackSealException("sender public key unavailable")
    if (own !in keys) keys.add(own)
    return encrypt_and_sign(keys) ?: throw PgpFallbackSealException("pgp encryption failed")
}
