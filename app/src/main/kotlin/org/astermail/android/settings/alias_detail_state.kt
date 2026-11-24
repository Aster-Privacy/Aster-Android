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

import org.astermail.android.api.aliases.AliasDeliveryEvent
import org.astermail.android.api.aliases.AliasRule
import org.astermail.android.api.aliases.AliasStatsResponse
import org.astermail.android.api.aliases.SENDER_PIN_MODE_OFF
import org.astermail.android.api.settings.AliasRun

data class DecryptedAliasPin(
    val id: String,
    val sender: String,
    val is_blocked: Boolean,
    val sender_hash: String = "",
)

data class DecryptedAliasContact(
    val id: String,
    val contact: String,
    val is_blocked: Boolean,
)

data class AliasDetailState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val load_failed: Boolean = false,
    val busy: Boolean = false,
    val stats: AliasStatsResponse? = null,
    val stats_locked: Boolean = false,
    val pin_mode: Int = SENDER_PIN_MODE_OFF,
    val pins: List<DecryptedAliasPin> = emptyList(),
    val pins_locked: Boolean = false,
    val contacts: List<DecryptedAliasContact> = emptyList(),
    val contacts_locked: Boolean = false,
    val blocked_events: List<AliasDeliveryEvent> = emptyList(),
    val blocked_locked: Boolean = false,
    val rules: List<AliasRule> = emptyList(),
    val rules_locked: Boolean = false,
    val apply_run: AliasRun? = null,
    val apply_busy: Boolean = false,
    val apply_unsupported: Boolean = false,
)

fun is_alias_run_active(run: AliasRun?): Boolean =
    run != null && (run.status == "pending" || run.status == "running")

enum class AliasRunConfirmKind { FOLDER_LABEL, FOLDER, LABEL }

fun alias_run_confirm_kind(
    folder_token: String?,
    never_inbox: Boolean,
    label_token: String?,
): AliasRunConfirmKind? {
    val has_folder = folder_token != null || never_inbox
    val has_label = label_token != null
    return when {
        has_folder && has_label -> AliasRunConfirmKind.FOLDER_LABEL
        has_folder -> AliasRunConfirmKind.FOLDER
        has_label -> AliasRunConfirmKind.LABEL
        else -> null
    }
}

fun can_start_alias_run(
    kind: AliasRunConfirmKind?,
    folder_is_spam: Boolean,
    run: AliasRun?,
    busy: Boolean,
): Boolean = kind != null && !folder_is_spam && !busy && !is_alias_run_active(run)

fun alias_run_finished(previous: AliasRun?, next: AliasRun?): AliasRun? =
    if (is_alias_run_active(previous) && next != null && !is_alias_run_active(next)) next else null
