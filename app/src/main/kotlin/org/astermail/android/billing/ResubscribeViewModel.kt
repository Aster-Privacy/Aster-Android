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

package org.astermail.android.billing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.BillingHistoryItem
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.ui.upgrade.UpgradeStore

enum class ResubscribeKind { Reactivate, Choose }

private val win_back_window: Duration = Duration.ofDays(60)

internal fun resubscribe_kind(
    subscription: SubscriptionResponse,
    history: List<BillingHistoryItem>,
    now: Instant,
): ResubscribeKind? {
    if (subscription.cancel_at_period_end && subscription.has_stripe_subscription == true) {
        return ResubscribeKind.Reactivate
    }
    if (subscription.plan.code != "free") return null
    val cutoff = now.minus(win_back_window)
    val churned_recently = history.any { item ->
        if (item.status != "paid") return@any false
        val ended_at = parse_instant(item.period_end ?: item.created_at) ?: return@any false
        minOf(ended_at, now) >= cutoff
    }
    return if (churned_recently) ResubscribeKind.Choose else null
}

private fun parse_instant(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

@HiltViewModel
class ResubscribeViewModel @Inject constructor(
    private val billing_api: BillingApi,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    private val _kind = MutableStateFlow<ResubscribeKind?>(null)
    val kind: StateFlow<ResubscribeKind?> = _kind.asStateFlow()
    private var is_busy = false
    private var loaded_account_id: String? = null
    private var load_job: Job? = null

    fun load(account_id: String?) {
        if (account_id != loaded_account_id) {
            load_job?.cancel()
            loaded_account_id = account_id
            _kind.value = null
        }
        if (account_id == null || load_job?.isActive == true) return
        load_job = viewModelScope.launch {
            try {
                val subscription = billing_api.get_subscription()
                val history = if (subscription.plan.code == "free") {
                    billing_api.get_billing_history(page = 1, per_page = 20).items
                } else {
                    emptyList()
                }
                val next = resubscribe_kind(subscription, history, Instant.now())
                if (loaded_account_id == account_id) _kind.value = next
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                return@launch
            }
        }
    }

    fun resubscribe(on_message: (String) -> Unit) {
        when (_kind.value) {
            ResubscribeKind.Choose -> UpgradeStore.show_plan_limit(null, null)
            ResubscribeKind.Reactivate -> reactivate(on_message)
            null -> Unit
        }
    }

    private fun reactivate(on_message: (String) -> Unit) {
        if (is_busy) return
        is_busy = true
        viewModelScope.launch {
            try {
                billing_api.reactivate_subscription()
                _kind.value = null
                on_message(ctx.getString(R.string.subscription_reactivated))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                on_message(localized_api_error(ctx, t, ctx.getString(R.string.reactivate_failed)))
            } finally {
                is_busy = false
            }
        }
    }
}
