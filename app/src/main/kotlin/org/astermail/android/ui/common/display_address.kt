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
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.ui.compose.compose_seed_store

fun display_sender_email(
    default_sender_id: String?,
    user_email: String,
    aliases: List<AliasInfo>,
    custom_domain_addresses: List<CustomDomainAddressInfo>,
): String = resolve_primary_sender_email(
    default_sender_id,
    user_email,
    aliases.filter { it.is_enabled && !it.decryption_failed },
    emptyList(),
    custom_domain_addresses.filter { it.is_enabled && !it.decryption_failed },
)

fun resolve_display_address(
    default_sender_id: String?,
    default_sender_loaded: Boolean,
    resolved_sender: String,
    cached_sender: String,
    user_email: String,
    senders_loaded: Boolean = false,
): String {
    val points_elsewhere = !default_sender_id.isNullOrBlank() &&
        default_sender_id != "primary" &&
        DISPLAY_EXCLUDED_PREFIXES.none { default_sender_id.startsWith(it) }
    if (default_sender_loaded && !points_elsewhere) return user_email
    val unresolved = points_elsewhere && !senders_loaded && resolved_sender.equals(user_email, ignoreCase = true)
    if (default_sender_loaded && !unresolved) return resolved_sender.ifBlank { user_email }
    return cached_sender.ifBlank { resolved_sender.ifBlank { user_email } }
}

private val DISPLAY_EXCLUDED_PREFIXES = listOf("ghost-", "external-")

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
    val resolved_sender = display_sender_email(
        state.default_sender_id,
        user_email,
        state.aliases,
        state.custom_domain_addresses,
    )
    return resolve_display_address(
        default_sender_id = state.default_sender_id,
        default_sender_loaded = state.default_sender_loaded,
        resolved_sender = resolved_sender,
        cached_sender = cached_sender,
        user_email = user_email,
        senders_loaded = state.aliases_loaded && state.custom_domain_addresses_loaded,
    )
}
