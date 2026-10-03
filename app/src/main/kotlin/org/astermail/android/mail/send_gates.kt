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

const val MAX_SEALED_SCHEDULE_DAYS = 28L

private const val DAY_MS = 24L * 60L * 60L * 1000L

enum class ScheduledSendBlock { TOO_FAR_AHEAD, MIXED_RECIPIENTS, REQUIRES_ENCRYPTION }

private fun address_key(address: String): String = address.trim().lowercase(Locale.ROOT)

fun latest_sealed_schedule_ms(now_ms: Long): Long = now_ms + MAX_SEALED_SCHEDULE_DAYS * DAY_MS

fun exceeds_sealed_schedule_window(scheduled_at_ms: Long, now_ms: Long): Boolean =
    scheduled_at_ms > latest_sealed_schedule_ms(now_ms)

fun scheduled_send_block(
    recipients: List<String>,
    scheduled_at_ms: Long,
    now_ms: Long,
    require_encryption: Boolean,
): ScheduledSendBlock? {
    val addresses = recipients.filter { it.isNotBlank() }
    return when {
        exceeds_sealed_schedule_window(scheduled_at_ms, now_ms) -> ScheduledSendBlock.TOO_FAR_AHEAD
        has_mixed_recipients(addresses) -> ScheduledSendBlock.MIXED_RECIPIENTS
        require_encryption && addresses.any { !is_internal_recipient(it) } ->
            ScheduledSendBlock.REQUIRES_ENCRYPTION
        else -> null
    }
}

fun hidden_internal_bcc(to: List<String>, cc: List<String>, bcc: List<String>): List<String> {
    val visible = (to + cc).map { address_key(it) }.toSet()
    val seen = HashSet<String>()
    return bcc
        .map { it.trim() }
        .filter { it.isNotEmpty() && is_internal_recipient(it) }
        .filter { address_key(it) !in visible && seen.add(address_key(it)) }
}

fun shared_targets(to: List<String>, cc: List<String>, bcc: List<String>): List<String> {
    val hidden = hidden_internal_bcc(to, cc, bcc).map { address_key(it) }.toSet()
    return (to + cc + bcc).filter { it.isNotBlank() && address_key(it) !in hidden }
}

fun replay_blocked_by_key_change(recipients: List<String>, changes: List<RecipientKeyChange>): Boolean {
    val external = recipients.filter { it.isNotBlank() && !is_internal_recipient(it) }.map { address_key(it) }.toSet()
    return changes.any { address_key(it.email) in external }
}

fun identity_change_pending_for(recipients: List<String>, changed_senders: List<String>): Boolean {
    val wanted = recipients.filter { it.isNotBlank() }.map { address_key(it) }.toSet()
    return changed_senders.any { it.isNotBlank() && address_key(it) in wanted }
}

const val MIN_MESSAGE_PASSWORD_LENGTH = 12

fun message_password_strength_tier(password: String): Int {
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it in 'A'..'Z' } && password.any { it in 'a'..'z' }) score++
    if (password.any { it in '0'..'9' }) score++
    if (password.any { !(it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9') }) score++
    return when {
        score <= 1 -> 1
        score == 2 -> 2
        score == 3 -> 3
        else -> 4
    }
}

fun is_strong_message_password(password: String): Boolean =
    password.length >= MIN_MESSAGE_PASSWORD_LENGTH && message_password_strength_tier(password) >= 3
