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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsubscribeBatchTest {
    private fun prompt(id: String) = ManualUnsubscribePrompt(
        subscription_id = id,
        sender_email = "$id@b.example",
        sender_name = id,
        url = "https://b.example/$id",
    )

    @Test
    fun single_success_reads_as_one_line() {
        val lines = summarize_unsubscribe_tally(UnsubscribeTally(succeeded = 1))
        assertEquals(listOf(UnsubscribeSummaryLine.unsubscribed_one), lines)
    }

    @Test
    fun single_failure_reads_as_one_line() {
        val lines = summarize_unsubscribe_tally(UnsubscribeTally(failed = 1))
        assertEquals(listOf(UnsubscribeSummaryLine.failed_one), lines)
    }

    @Test
    fun mixed_batch_lists_every_outcome_once() {
        val lines = summarize_unsubscribe_tally(
            UnsubscribeTally(succeeded = 5, needs_website = 2, failed = 1, rate_limited = 3),
        )
        assertEquals(
            listOf(
                UnsubscribeSummaryLine.unsubscribed_many(5),
                UnsubscribeSummaryLine.needs_website(2),
                UnsubscribeSummaryLine.failed_many(1),
                UnsubscribeSummaryLine.rate_limited,
            ),
            lines,
        )
    }

    @Test
    fun empty_tally_has_no_lines() {
        assertTrue(summarize_unsubscribe_tally(UnsubscribeTally()).isEmpty())
    }

    @Test
    fun batch_waits_until_every_request_finishes() {
        val batch = UnsubscribeBatch()
        batch.begin(3)
        batch.finish(UnsubscribeResultKind.succeeded)
        batch.finish(UnsubscribeResultKind.failed)
        assertFalse(batch.is_idle)
        assertNull(batch.drain())
        batch.finish(UnsubscribeResultKind.succeeded)
        assertTrue(batch.is_idle)
        val result = require_result(batch.drain())
        assertEquals(UnsubscribeTally(succeeded = 2, failed = 1), result.tally)
        assertNull(result.single_prompt)
        assertNull(batch.drain())
    }

    @Test
    fun taps_during_a_batch_merge_into_one_summary() {
        val batch = UnsubscribeBatch()
        batch.begin(1)
        batch.begin(1)
        batch.finish(UnsubscribeResultKind.succeeded)
        assertNull(batch.drain())
        batch.finish(UnsubscribeResultKind.succeeded)
        assertEquals(2, require_result(batch.drain()).tally.succeeded)
    }

    @Test
    fun single_website_sender_keeps_its_prompt() {
        val batch = UnsubscribeBatch()
        batch.begin(1)
        batch.finish(UnsubscribeResultKind.needs_website, prompt("a"))
        assertEquals("a", require_result(batch.drain()).single_prompt?.subscription_id)
    }

    @Test
    fun website_prompt_is_dropped_when_the_batch_has_several_senders() {
        val batch = UnsubscribeBatch()
        batch.begin(2)
        batch.finish(UnsubscribeResultKind.needs_website, prompt("a"))
        batch.finish(UnsubscribeResultKind.succeeded)
        val result = require_result(batch.drain())
        assertNull(result.single_prompt)
        assertEquals(UnsubscribeTally(succeeded = 1, needs_website = 1), result.tally)
    }

    @Test
    fun many_senders_finish_into_one_result() {
        val batch = UnsubscribeBatch()
        batch.begin(500)
        repeat(500) { index ->
            batch.finish(if (index % 2 == 0) UnsubscribeResultKind.succeeded else UnsubscribeResultKind.failed)
        }
        val result = require_result(batch.drain())
        assertEquals(250, result.tally.succeeded)
        assertEquals(250, result.tally.failed)
    }

    @Test
    fun only_rate_limit_errors_classify_as_rate_limited() {
        assertEquals(ProxyUnsubscribeResult.rate_limited, classify_unsubscribe_error(ApiError.RateLimited()))
        assertEquals(ProxyUnsubscribeResult.rejected, classify_unsubscribe_error(ApiError.NetworkError))
        assertEquals(ProxyUnsubscribeResult.rejected, classify_unsubscribe_error(ApiError.ServerError(500)))
        assertEquals(ProxyUnsubscribeResult.rejected, classify_unsubscribe_error(IllegalStateException()))
    }

    private fun require_result(result: UnsubscribeBatchResult?): UnsubscribeBatchResult {
        assertNotNull(result)
        return result!!
    }
}
