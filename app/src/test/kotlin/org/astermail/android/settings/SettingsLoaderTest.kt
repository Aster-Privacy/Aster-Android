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

package org.astermail.android.settings

import android.content.Context
import android.net.ConnectivityManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLoaderTest {

    @Test
    fun `retries transient errors and returns the first success`() = runTest {
        var calls = 0
        val result = load_settings_resource(await_session = {}) {
            calls++
            when (calls) {
                1 -> throw IOException("reset")
                2 -> throw ApiError.UnauthorizedError
                else -> "ok"
            }
        }
        assertEquals("ok", result.getOrNull())
        assertEquals(3, calls)
    }

    @Test
    fun `stops at once on a non transient error`() = runTest {
        var calls = 0
        val result = load_settings_resource(await_session = {}) {
            calls++
            throw ApiError.NotFoundError
        }
        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `gives up after the last backoff step`() = runTest {
        var calls = 0
        val result = load_settings_resource(await_session = {}, backoff_ms = listOf(10L, 20L)) {
            calls++
            throw ApiError.ServerError(500)
        }
        assertTrue(result.exceptionOrNull() is ApiError.ServerError)
        assertEquals(3, calls)
    }

    @Test
    fun `session wait holds until signed in and not expired`() = runTest {
        val signed_in = MutableStateFlow(false)
        val expired = MutableStateFlow(true)
        var done = false
        launch {
            await_settings_session(signed_in, expired)
            done = true
        }
        runCurrent()
        assertFalse(done)
        signed_in.value = true
        runCurrent()
        assertFalse(done)
        expired.value = false
        runCurrent()
        assertTrue(done)
    }

    @Test
    fun `session wait gives up after its timeout`() = runTest {
        var done = false
        launch {
            await_settings_session(MutableStateFlow(false), MutableStateFlow(false), wait_ms = 1_000L)
            done = true
        }
        advanceUntilIdle()
        assertTrue(done)
    }

    @Test
    fun `waits for the network instead of spending retries while offline`() = runTest {
        var online = false
        val callback = slot<ConnectivityManager.NetworkCallback>()
        val manager = mockk<ConnectivityManager>(relaxed = true)
        every { manager.activeNetwork } answers { if (online) mockk(relaxed = true) else null }
        every { manager.registerDefaultNetworkCallback(capture(callback)) } returns Unit
        val context = mockk<Context>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        var calls = 0
        var result: Result<String>? = null
        launch {
            result = load_settings_resource(await_session = { await_settings_network(context) }) {
                calls++
                if (!online) throw IOException("offline")
                "ok"
            }
        }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, calls)
        assertEquals(null, result)
        online = true
        callback.captured.onAvailable(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals("ok", result?.getOrNull())
    }

    @Test
    fun `network wait returns at once without a connectivity service`() = runTest {
        val context = mockk<Context>(relaxed = true)
        var calls = 0
        val result = load_settings_resource(await_session = { await_settings_network(context) }) {
            calls++
            "ok"
        }
        assertEquals("ok", result.getOrNull())
        assertEquals(1, calls)
    }
}
