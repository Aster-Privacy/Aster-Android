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

package org.astermail.android.subscriptions

import org.astermail.android.api.ApiError
import org.astermail.android.ui.mail.ProxyUnsubscribeResult

internal enum class UnsubscribeResultKind {
    succeeded,
    needs_website,
    failed,
    rate_limited,
}

internal data class UnsubscribeTally(
    val succeeded: Int = 0,
    val needs_website: Int = 0,
    val failed: Int = 0,
    val rate_limited: Int = 0,
) {
    val total: Int get() = succeeded + needs_website + failed + rate_limited

    operator fun plus(kind: UnsubscribeResultKind): UnsubscribeTally = when (kind) {
        UnsubscribeResultKind.succeeded -> copy(succeeded = succeeded + 1)
        UnsubscribeResultKind.needs_website -> copy(needs_website = needs_website + 1)
        UnsubscribeResultKind.failed -> copy(failed = failed + 1)
        UnsubscribeResultKind.rate_limited -> copy(rate_limited = rate_limited + 1)
    }
}

internal sealed interface UnsubscribeSummaryLine {
    data object unsubscribed_one : UnsubscribeSummaryLine
    data class unsubscribed_many(val count: Int) : UnsubscribeSummaryLine
    data class needs_website(val count: Int) : UnsubscribeSummaryLine
    data object failed_one : UnsubscribeSummaryLine
    data class failed_many(val count: Int) : UnsubscribeSummaryLine
    data object rate_limited : UnsubscribeSummaryLine
}

internal fun summarize_unsubscribe_tally(tally: UnsubscribeTally): List<UnsubscribeSummaryLine> {
    val lines = mutableListOf<UnsubscribeSummaryLine>()
    val single = tally.total == 1
    if (tally.succeeded > 0) {
        lines += if (single) UnsubscribeSummaryLine.unsubscribed_one else UnsubscribeSummaryLine.unsubscribed_many(tally.succeeded)
    }
    if (tally.needs_website > 0) lines += UnsubscribeSummaryLine.needs_website(tally.needs_website)
    if (tally.failed > 0) {
        lines += if (single) UnsubscribeSummaryLine.failed_one else UnsubscribeSummaryLine.failed_many(tally.failed)
    }
    if (tally.rate_limited > 0) lines += UnsubscribeSummaryLine.rate_limited
    return lines
}

internal data class UnsubscribeBatchResult(
    val tally: UnsubscribeTally,
    val single_prompt: ManualUnsubscribePrompt?,
)

internal class UnsubscribeBatch {
    private var in_flight = 0
    private var tally = UnsubscribeTally()
    private val prompts = mutableListOf<ManualUnsubscribePrompt>()

    val is_idle: Boolean get() = in_flight == 0

    fun begin(count: Int) {
        in_flight += count.coerceAtLeast(0)
    }

    fun finish(kind: UnsubscribeResultKind, prompt: ManualUnsubscribePrompt? = null) {
        in_flight = (in_flight - 1).coerceAtLeast(0)
        tally += kind
        if (prompt != null) prompts += prompt
    }

    fun drain(): UnsubscribeBatchResult? {
        if (!is_idle || tally.total == 0) return null
        val result = UnsubscribeBatchResult(
            tally = tally,
            single_prompt = prompts.singleOrNull()?.takeIf { tally.total == 1 },
        )
        tally = UnsubscribeTally()
        prompts.clear()
        return result
    }
}

internal fun classify_unsubscribe_error(error: Throwable): ProxyUnsubscribeResult =
    if (error is ApiError.RateLimited) ProxyUnsubscribeResult.rate_limited else ProxyUnsubscribeResult.rejected
