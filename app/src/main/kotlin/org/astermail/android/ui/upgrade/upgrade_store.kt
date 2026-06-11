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

package org.astermail.android.ui.upgrade

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UpgradeReason { PlanLimit, StorageFull }

enum class UpgradeLimitKey {
    MaxEmailAliases,
    MaxCustomDomains,
    MaxContacts,
    MaxEmailTemplates,
    MaxHtmlSignatures,
    MaxCustomFilters,
    Generic,
}

data class UpgradeState(
    val is_open: Boolean = false,
    val reason: UpgradeReason = UpgradeReason.PlanLimit,
    val limit_key: UpgradeLimitKey = UpgradeLimitKey.Generic,
    val resource_label: String? = null,
    val server_message: String? = null,
)

object UpgradeStore {
    private val _state = MutableStateFlow(UpgradeState())
    val state: StateFlow<UpgradeState> = _state.asStateFlow()

    private val resource_to_key = mapOf(
        "aliases" to UpgradeLimitKey.MaxEmailAliases,
        "alias" to UpgradeLimitKey.MaxEmailAliases,
        "email aliases" to UpgradeLimitKey.MaxEmailAliases,
        "domains" to UpgradeLimitKey.MaxCustomDomains,
        "custom domains" to UpgradeLimitKey.MaxCustomDomains,
        "contacts" to UpgradeLimitKey.MaxContacts,
        "templates" to UpgradeLimitKey.MaxEmailTemplates,
        "email templates" to UpgradeLimitKey.MaxEmailTemplates,
        "signatures" to UpgradeLimitKey.MaxHtmlSignatures,
        "html signatures" to UpgradeLimitKey.MaxHtmlSignatures,
        "filters" to UpgradeLimitKey.MaxCustomFilters,
        "custom filters" to UpgradeLimitKey.MaxCustomFilters,
    )

    private fun resolve_key(resource: String?): UpgradeLimitKey {
        if (resource.isNullOrBlank()) return UpgradeLimitKey.Generic
        return resource_to_key[resource.trim().lowercase()] ?: UpgradeLimitKey.Generic
    }

    fun show_plan_limit(resource: String?, message: String?) {
        _state.value = UpgradeState(
            is_open = true,
            reason = UpgradeReason.PlanLimit,
            limit_key = resolve_key(resource),
            resource_label = resource,
            server_message = message,
        )
    }

    fun show_plan_limit_for(key: UpgradeLimitKey, resource_label: String?) {
        _state.value = UpgradeState(
            is_open = true,
            reason = UpgradeReason.PlanLimit,
            limit_key = key,
            resource_label = resource_label,
            server_message = null,
        )
    }

    fun show_storage_full(message: String?) {
        _state.value = UpgradeState(
            is_open = true,
            reason = UpgradeReason.StorageFull,
            limit_key = UpgradeLimitKey.Generic,
            resource_label = null,
            server_message = message,
        )
    }

    fun close() {
        if (!_state.value.is_open) return
        _state.value = _state.value.copy(is_open = false)
    }
}
