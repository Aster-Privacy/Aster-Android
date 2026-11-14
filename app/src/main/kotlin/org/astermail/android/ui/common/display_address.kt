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

package org.astermail.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.ui.compose.compose_seed_store

fun resolve_display_address(
    default_sender_id: String?,
    default_sender_loaded: Boolean,
    resolved_sender: String,
    cached_sender: String,
    user_email: String,
): String {
    val points_elsewhere = !default_sender_id.isNullOrBlank() && default_sender_id != "primary"
    val unresolved = points_elsewhere && resolved_sender.equals(user_email, ignoreCase = true)
    if (default_sender_loaded && !unresolved) return resolved_sender.ifBlank { user_email }
    return cached_sender.ifBlank { resolved_sender.ifBlank { user_email } }
}

@Composable
fun remember_display_address(state: SettingsUiState, user_email: String): String {
    val context = LocalContext.current
    val cached_sender by produceState(initialValue = "", user_email) {
        value = if (user_email.isBlank()) {
            ""
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    compose_seed_store.read_identity(context, user_email).primary_sender_email
                }.getOrDefault("")
            }
        }
    }
    val resolved_sender = resolve_primary_sender_email(
        state.default_sender_id,
        user_email,
        state.aliases,
        state.ghost_aliases,
        state.custom_domain_addresses,
    )
    return resolve_display_address(
        default_sender_id = state.default_sender_id,
        default_sender_loaded = state.default_sender_loaded,
        resolved_sender = resolved_sender,
        cached_sender = cached_sender,
        user_email = user_email,
    )
}
