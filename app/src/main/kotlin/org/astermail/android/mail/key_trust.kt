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
import org.astermail.android.api.keys.ExternalKeyInfo

data class RecipientKeyChange(
    val email: String,
    val prior_fingerprint: String,
    val new_fingerprint: String,
)

fun external_key_trust_candidates(recipients: List<String>): List<String> =
    recipients
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.contains('@') && !is_internal_recipient(it) }
        .distinct()

fun key_changes_from_discovery(keys: List<ExternalKeyInfo>): List<RecipientKeyChange> =
    keys.mapNotNull { info ->
        val change = info.fingerprint_change ?: return@mapNotNull null
        if (change.prior_fingerprint.isBlank() || change.new_fingerprint.isBlank()) {
            return@mapNotNull null
        }
        RecipientKeyChange(
            email = info.email.trim().lowercase(Locale.ROOT),
            prior_fingerprint = change.prior_fingerprint,
            new_fingerprint = change.new_fingerprint,
        )
    }
