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

package org.astermail.android.ui.settings.detail

val INDIVIDUAL_PLAN_ORDER = listOf("free", "star", "nova", "supernova")

val FAMILY_PLAN_ORDER = listOf("duo", "family")

const val STORAGE_TIGHT_PERCENT = 80.0

const val DEFAULT_RECOMMENDED_PLAN = "nova"

enum class plan_ladder_kind { INDIVIDUAL, FAMILY }

data class plan_recommendation(
    val ladder: plan_ladder_kind,
    val current_plan_code: String?,
    val is_paid: Boolean,
    val is_top_tier: Boolean,
    val storage_is_tight: Boolean,
    val storage_percent: Double,
    val recommended_plan_code: String?,
    val suggest_storage_addon: Boolean,
)

fun plan_ladder_of(plan_code: String?): plan_ladder_kind =
    if (plan_code != null && FAMILY_PLAN_ORDER.contains(plan_code)) {
        plan_ladder_kind.FAMILY
    } else {
        plan_ladder_kind.INDIVIDUAL
    }

fun plan_rank_of(plan_code: String?): Int {
    if (plan_code == null) return -1
    val family_index = FAMILY_PLAN_ORDER.indexOf(plan_code)

    if (family_index > -1) return family_index

    return INDIVIDUAL_PLAN_ORDER.indexOf(plan_code)
}

fun storage_percent_of(used_bytes: Long, limit_bytes: Long): Double {
    if (limit_bytes <= 0L) return 0.0

    return minOf(100.0, used_bytes.toDouble() / limit_bytes.toDouble() * 100.0)
}

fun compute_plan_recommendation(
    current_plan_code: String?,
    storage_used_bytes: Long,
    storage_limit_bytes: Long,
): plan_recommendation {
    val code = current_plan_code?.takeIf { it.isNotBlank() }
    val ladder = plan_ladder_of(code)
    val order = if (ladder == plan_ladder_kind.FAMILY) FAMILY_PLAN_ORDER else INDIVIDUAL_PLAN_ORDER
    val rank = plan_rank_of(code)
    val is_paid = code != null && code != "free" && rank > -1
    val percent = storage_percent_of(storage_used_bytes, storage_limit_bytes)
    val storage_is_tight = percent >= STORAGE_TIGHT_PERCENT
    val is_top_tier = is_paid && rank == order.size - 1

    if (!is_paid) {
        return plan_recommendation(
            ladder = ladder,
            current_plan_code = code,
            is_paid = false,
            is_top_tier = false,
            storage_is_tight = storage_is_tight,
            storage_percent = percent,
            recommended_plan_code = DEFAULT_RECOMMENDED_PLAN,
            suggest_storage_addon = false,
        )
    }

    return plan_recommendation(
        ladder = ladder,
        current_plan_code = code,
        is_paid = true,
        is_top_tier = is_top_tier,
        storage_is_tight = storage_is_tight,
        storage_percent = percent,
        recommended_plan_code = if (!is_top_tier && storage_is_tight) order.getOrNull(rank + 1) else null,
        suggest_storage_addon = is_top_tier,
    )
}
