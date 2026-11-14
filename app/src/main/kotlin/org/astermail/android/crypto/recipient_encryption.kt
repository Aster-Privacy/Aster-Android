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


package org.astermail.android.crypto

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.astermail.android.api.keys.ExternalKeyInfo

enum class RecipientEncryption { ENCRYPT, SERVER, PLAIN }

enum class RecipientLockStatus { CHECKING, ENCRYPTED, AVAILABLE, KEY_INVALID, TRANSIT, UNKNOWN }

object RecipientEncryptionRules {
    const val DISCOVERY_BATCH_SIZE = 20
    const val LOOKUP_ATTEMPTS = 3
    const val LOOKUP_RETRY_STEP_MS = 2000L
    private val OPPORTUNISTIC_KEY_SOURCES = setOf("aster", "autocrypt", "dane", "manual", "wkd")

    fun normalize(address: String): String = address.trim().lowercase()

    fun has_key(info: ExternalKeyInfo?): Boolean =
        info != null && info.found && !info.public_key.isNullOrBlank()

    fun is_listed(address: String, pgp_recipients: List<String>): Boolean {
        val target = normalize(address)
        if (target.isEmpty()) return false
        return pgp_recipients.any { normalize(it) == target }
    }

    fun is_explicit(force_pgp: Boolean?, require_encryption: Boolean, listed: Boolean): Boolean =
        listed || force_pgp == true || require_encryption

    fun wants_copies(
        force_pgp: Boolean?,
        require_encryption: Boolean,
        pgp_recipients: List<String>,
    ): Boolean = !(force_pgp == false && !require_encryption && pgp_recipients.none { it.isNotBlank() })

    fun is_recipient_controlled_key(info: ExternalKeyInfo?, now_ms: Long): Boolean {
        if (!has_key(info)) return false
        if (!info!!.expires_at.isNullOrBlank() && !is_unexpired(info.expires_at, now_ms)) return false
        return info.source?.trim()?.lowercase() in OPPORTUNISTIC_KEY_SOURCES
    }

    fun trusts_key_for_opportunistic(info: ExternalKeyInfo?, encrypt_by_default: Boolean, now_ms: Long): Boolean =
        encrypt_by_default && is_recipient_controlled_key(info, now_ms)

    fun will_encrypt_opportunistically(info: ExternalKeyInfo?, encrypt_by_default: Boolean, now_ms: Long): Boolean =
        info?.will_encrypt == true || trusts_key_for_opportunistic(info, encrypt_by_default, now_ms)

    fun decide(
        info: ExternalKeyInfo?,
        force_pgp: Boolean?,
        require_encryption: Boolean,
        listed: Boolean,
        encrypt_by_default: Boolean = false,
        now_ms: Long = System.currentTimeMillis(),
    ): RecipientEncryption {
        val found = has_key(info)
        if (is_explicit(force_pgp, require_encryption, listed)) {
            return if (found) RecipientEncryption.ENCRYPT else RecipientEncryption.SERVER
        }
        if (force_pgp == false || !found) return RecipientEncryption.PLAIN
        return if (will_encrypt_opportunistically(info, encrypt_by_default, now_ms)) {
            RecipientEncryption.ENCRYPT
        } else {
            RecipientEncryption.PLAIN
        }
    }

    fun recipients_failing_required_encryption(
        recipients: List<String>,
        discovered: List<ExternalKeyInfo>,
        force_pgp: Boolean?,
        pgp_recipients: List<String>,
    ): List<String> {
        val by_address = discovered.associateBy { normalize(it.email) }
        return recipients.filter { recipient ->
            decide(
                by_address[normalize(recipient)],
                force_pgp,
                require_encryption = true,
                listed = is_listed(recipient, pgp_recipients),
            ) != RecipientEncryption.ENCRYPT
        }
    }

    fun copy_failure_throws(
        explicit: Boolean,
        listed: Boolean,
        too_large: Boolean,
        require_encryption: Boolean,
    ): Boolean = require_encryption || if (too_large) listed else explicit

    fun skipped_copies_throw(listed: Boolean, require_encryption: Boolean): Boolean =
        listed || require_encryption

    private fun expiry_millis(expires_at: String?): Long? {
        val raw = expires_at?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching { ZonedDateTime.parse(raw).toInstant().toEpochMilli() }
            .getOrNull()
    }

    fun is_expired(expires_at: String?, now_ms: Long): Boolean {
        val millis = expiry_millis(expires_at) ?: return false
        return millis <= now_ms
    }

    private fun is_unexpired(expires_at: String?, now_ms: Long): Boolean {
        val millis = expiry_millis(expires_at) ?: return false
        return millis > now_ms
    }

    fun lock_status_for(
        info: ExternalKeyInfo,
        now_ms: Long,
        encrypt_by_default: Boolean = false,
    ): RecipientLockStatus {
        if (will_encrypt_opportunistically(info, encrypt_by_default, now_ms)) return RecipientLockStatus.ENCRYPTED
        val key_present = info.found && info.public_key != null
        if (key_present && is_expired(info.expires_at, now_ms)) return RecipientLockStatus.KEY_INVALID
        if (key_present) return RecipientLockStatus.AVAILABLE
        return RecipientLockStatus.TRANSIT
    }

    fun shows_encrypted_lock(status: RecipientLockStatus, pgp_override: Boolean?): Boolean {
        if (pgp_override == false) return false
        if (status == RecipientLockStatus.ENCRYPTED) return true
        return status == RecipientLockStatus.AVAILABLE && pgp_override == true
    }

    fun effective_status(
        status: RecipientLockStatus,
        pgp_override: Boolean?,
        toggleable: Boolean,
    ): RecipientLockStatus {
        if (status != RecipientLockStatus.AVAILABLE && status != RecipientLockStatus.ENCRYPTED) return status
        if (shows_encrypted_lock(status, pgp_override)) return RecipientLockStatus.ENCRYPTED
        if (toggleable && status == RecipientLockStatus.AVAILABLE) return RecipientLockStatus.AVAILABLE
        return RecipientLockStatus.TRANSIT
    }

    fun displayed_internal_status(has_outside_recipient: Boolean): RecipientLockStatus =
        if (has_outside_recipient) RecipientLockStatus.TRANSIT else RecipientLockStatus.ENCRYPTED

    fun toggle_override(pgp_override: Boolean?): Boolean? = if (pgp_override == true) null else true

    fun statuses_from_discovery(
        outside_recipients: List<String>,
        discovered: List<ExternalKeyInfo>,
        now_ms: Long,
        encrypt_by_default: Boolean = false,
    ): Map<String, RecipientLockStatus> {
        val by_address = discovered.associateBy { normalize(it.email) }
        return outside_recipients
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .associateWith { address ->
                by_address[address]?.let { lock_status_for(it, now_ms, encrypt_by_default) }
                    ?: RecipientLockStatus.TRANSIT
            }
    }

    suspend fun lookup_lock_statuses(
        outside_recipients: List<String>,
        now_ms: () -> Long,
        wait: suspend (Long) -> Unit,
        discover: suspend (List<String>) -> List<ExternalKeyInfo>,
        encrypt_by_default: Boolean = false,
    ): Map<String, RecipientLockStatus> {
        val targets = outside_recipients.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
        if (targets.isEmpty()) return emptyMap()
        var failed = false
        for (attempt in 0 until LOOKUP_ATTEMPTS) {
            if (attempt > 0) wait(LOOKUP_RETRY_STEP_MS * attempt)
            val result = try {
                discover(targets)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            failed = result == null
            if (!result.isNullOrEmpty()) {
                return statuses_from_discovery(targets, result, now_ms(), encrypt_by_default)
            }
        }
        val fallback = if (failed) RecipientLockStatus.UNKNOWN else RecipientLockStatus.TRANSIT
        return targets.associateWith { fallback }
    }

    fun collect_pgp_recipients(
        outside_recipients: List<String>,
        statuses: Map<String, RecipientLockStatus>,
        pgp_override: Boolean?,
    ): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (recipient in outside_recipients) {
            val normalized = normalize(recipient)
            if (normalized.isEmpty() || !seen.add(normalized)) continue
            val status = statuses[normalized] ?: continue
            if (shows_encrypted_lock(status, pgp_override)) out.add(normalized)
        }
        return out
    }
}
