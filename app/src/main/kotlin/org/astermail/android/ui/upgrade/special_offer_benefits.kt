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

import org.astermail.android.api.billing.AvailablePlan
import java.util.Locale

internal val VANGUARD_PLAN_CODES = setOf("nova", "supernova", "duo", "family", "family_duo", "family_full")

internal sealed interface special_offer_benefit {
    data object unlimited_aliases : special_offer_benefit
    data class aliases(val count: Int) : special_offer_benefit
    data class storage(val label: String) : special_offer_benefit
    data object unlimited_domains : special_offer_benefit
    data class domains(val count: Int) : special_offer_benefit
    data object vanguard : special_offer_benefit
}

internal fun special_offer_benefits_for(plan_code: String, plan: AvailablePlan?): List<special_offer_benefit> {
    val code = plan_code.lowercase(Locale.ROOT)
    val benefits = mutableListOf<special_offer_benefit>()
    if (plan != null) {
        when {
            plan.max_email_aliases < 0 -> benefits += special_offer_benefit.unlimited_aliases
            plan.max_email_aliases > 0 -> benefits += special_offer_benefit.aliases(plan.max_email_aliases)
        }
        if (plan.storage_limit_bytes > 0) {
            benefits += special_offer_benefit.storage(special_offer_storage_label(plan.storage_limit_bytes))
        }
        when {
            plan.max_custom_domains < 0 -> benefits += special_offer_benefit.unlimited_domains
            plan.max_custom_domains > 0 -> benefits += special_offer_benefit.domains(plan.max_custom_domains)
        }
    }
    if (code in VANGUARD_PLAN_CODES) benefits += special_offer_benefit.vanguard
    return benefits
}

internal fun special_offer_storage_label(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    val rounded = if (value >= 10 || value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        "%.1f".format(Locale.getDefault(), value)
    }
    return "$rounded ${units[unit]}"
}
