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
)

data class DecryptedAliasContact(
    val id: String,
    val contact: String,
    val is_blocked: Boolean,
)

data class AliasDetailState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
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
)

fun is_alias_run_active(run: AliasRun?): Boolean =
    run != null && (run.status == "pending" || run.status == "running")
