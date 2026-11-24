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

package org.astermail.android.accounts

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.storage.PreferencesCacheStore

private val unpaid_subscription_states = setOf(
    "canceled",
    "cancelled",
    "incomplete_expired",
    "unpaid",
)

private val account_plan_json = Json { ignoreUnknownKeys = true }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface account_plan_cache_entry_point {
    fun preferences_cache(): PreferencesCacheStore
}

fun subscription_is_paid(subscription: SubscriptionInfo): Boolean =
    subscription.effective_price_cents > 0 &&
        subscription.status !in unpaid_subscription_states

fun cached_account_paid(cache: PreferencesCacheStore, account_id: String): Boolean? {
    if (account_id.isBlank()) return null
    val raw = cache.read_subscription(account_id) ?: return null
    val subscription = runCatching {
        account_plan_json.decodeFromString(SubscriptionInfo.serializer(), raw)
    }.getOrNull() ?: return null
    return subscription_is_paid(subscription)
}

fun cached_paid_account_ids(context: Context, account_ids: List<String>): Set<String> {
    val cache = runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            account_plan_cache_entry_point::class.java,
        ).preferences_cache()
    }.getOrNull() ?: return emptySet()
    return account_ids.filter { cached_account_paid(cache, it) == true }.toSet()
}
