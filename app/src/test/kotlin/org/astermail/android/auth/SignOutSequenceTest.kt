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

package org.astermail.android.auth

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignOutSequenceTest {

    @Test
    fun wipe_finishes_when_the_caller_is_cancelled() = runTest {
        val gate = CompletableDeferred<Unit>()
        val wiped = AtomicBoolean(false)
        val notified = AtomicBoolean(false)
        val job = launch {
            run_sign_out(
                wipe = {
                    gate.await()
                    wiped.set(true)
                    "tokens"
                },
                notify_server = { notified.set(true) },
            )
        }
        advanceUntilIdle()
        job.cancel()
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(wiped.get())
        assertTrue(notified.get())
    }

    @Test
    fun wipe_runs_before_any_server_request() = runTest {
        val order = mutableListOf<String>()
        val result = run_sign_out(
            wipe = {
                order.add("wipe")
                "tokens"
            },
            notify_server = { order.add("notify:$it") },
        )
        assertTrue(result.isSuccess)
        assertEquals(listOf("wipe", "notify:tokens"), order)
    }

    @Test
    fun a_slow_server_request_never_holds_back_the_wipe() = runTest {
        val wiped = AtomicBoolean(false)
        val notified = AtomicBoolean(false)
        val call = async(start = CoroutineStart.UNDISPATCHED) {
            run_sign_out(
                wipe = {
                    wiped.set(true)
                    "tokens"
                },
                notify_server = {
                    delay(60_000L)
                    notified.set(true)
                },
            )
        }
        assertTrue(wiped.get())
        assertFalse(notified.get())
        call.cancel()
        advanceUntilIdle()
        assertTrue(notified.get())
    }

    @Test
    fun a_failed_server_request_still_reports_success() = runTest {
        val result = run_sign_out(
            wipe = { "tokens" },
            notify_server = { throw IllegalStateException("offline") },
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun nothing_is_sent_when_there_was_no_session_to_end() = runTest {
        val notified = AtomicBoolean(false)
        val result = run_sign_out<String>(
            wipe = { null },
            notify_server = { notified.set(true) },
        )
        assertTrue(result.isSuccess)
        assertFalse(notified.get())
    }

    @Test
    fun a_failed_wipe_is_reported_and_sends_nothing() = runTest {
        val notified = AtomicBoolean(false)
        val result = run_sign_out<String>(
            wipe = { throw IllegalStateException("disk") },
            notify_server = { notified.set(true) },
        )
        assertTrue(result.isFailure)
        assertFalse(notified.get())
    }

    private val source = File("src/main/kotlin/org/astermail/android/auth/AuthRepository.kt")
        .readText()
        .replace("\r\n", "\n")

    private fun body_of(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("missing $signature", start >= 0)
        val next = Regex("""\n    (private |internal |)(suspend )?fun """).find(source, start + signature.length)
        return source.substring(start, next?.range?.first ?: source.length)
    }

    @Test
    fun the_local_wipe_makes_no_server_request() {
        val body = body_of("private suspend fun sign_out_internal(")
        assertFalse("auth_api.logout" in body)
        assertFalse("unlink_account_device" in body)
        assertTrue("token_store.clear()" in body)
        assertTrue("session_key_store.clear()" in body)
    }

    @Test
    fun every_sign_out_entry_point_goes_through_the_sequence() {
        assertTrue("run_sign_out(" in body_of("private suspend fun sign_out("))
        assertTrue("sign_out(remove_account = true)" in body_of("suspend fun logout(): Result<Unit>"))
        assertTrue("withContext(NonCancellable)" in body_of("suspend fun logout_all(): Result<Unit>"))
        assertTrue("sign_out(remove_account = false" in body_of("suspend fun force_sign_out("))
        assertFalse(Regex("""\bsign_out_internal\(remove_account""").containsMatchIn(
            source.replace(body_of("private suspend fun sign_out("), ""),
        ))
    }

    @Test
    fun account_deletion_wipes_without_honoring_cancellation() {
        val body = body_of("suspend fun delete_account(")
        val wipe_start = body.indexOf("withContext(NonCancellable)")
        assertTrue(wipe_start >= 0)
        assertTrue(body.indexOf("session_key_store.clear()") > wipe_start)
        assertTrue(body.indexOf("auth_api.delete_account(") < wipe_start)
    }

    @Test
    fun sign_out_and_deletion_forget_deleted_subscriptions() {
        val call = "forget_deleted_subscriptions(context, current_email)"
        assertTrue(call in body_of("private suspend fun sign_out_internal("))
        assertTrue(call in body_of("suspend fun delete_account("))
    }
}
