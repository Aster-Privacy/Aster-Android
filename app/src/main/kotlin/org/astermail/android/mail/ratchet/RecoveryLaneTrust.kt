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

enum class RecoveryLaneBinding { TRANSCRIPT, LEGACY, NONE }

internal data class OpenedRecoveryLane(
    val message_key: String,
    val binding: RecoveryLaneBinding,
    val snonce: String,
    val sent_at: Long,
)

internal data class RecoveredLanePlaintext(
    val plaintext: String,
    val binding: RecoveryLaneBinding,
    val snonce: String,
    val sent_at: Long,
)

enum class LaneSenderPin { PINNED, UNPINNED, UNREADABLE }

enum class LaneFreshness { FRESH, REPLAYED, UNKNOWN }

enum class RecoveryLaneVerdict { CONFIRMED, UNCONFIRMED, REFUSED }

object RecoveryLaneTrust {
    fun verdict(
        binding: RecoveryLaneBinding,
        freshness: LaneFreshness,
        continuity: SenderIdentityContinuity,
        sender_pin: LaneSenderPin,
    ): RecoveryLaneVerdict = when {
        binding == RecoveryLaneBinding.NONE && sender_pin == LaneSenderPin.PINNED -> RecoveryLaneVerdict.REFUSED
        freshness != LaneFreshness.FRESH -> RecoveryLaneVerdict.UNCONFIRMED
        continuity != SenderIdentityContinuity.KNOWN -> RecoveryLaneVerdict.UNCONFIRMED
        binding == RecoveryLaneBinding.TRANSCRIPT -> RecoveryLaneVerdict.CONFIRMED
        binding == RecoveryLaneBinding.LEGACY && sender_pin == LaneSenderPin.UNPINNED -> RecoveryLaneVerdict.CONFIRMED
        else -> RecoveryLaneVerdict.UNCONFIRMED
    }

    fun pins(binding: RecoveryLaneBinding, verdict: RecoveryLaneVerdict): Boolean =
        binding == RecoveryLaneBinding.TRANSCRIPT && verdict == RecoveryLaneVerdict.CONFIRMED
}
