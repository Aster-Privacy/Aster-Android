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

package org.astermail.android.vacation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.vacation.VacationReplyApi
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VacationReplyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: VacationReplyApi
    private lateinit var context: android.content.Context
    private lateinit var vm: VacationReplyViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = true)
        context = mockk(relaxed = true)
        vm = VacationReplyViewModel(api, context)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `failed load marks load_failed and does not fabricate a reply`() = runTest {
        coEvery { api.get() } throws RuntimeException("boom")

        vm.load()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.load_failed)
        assertFalse(state.is_loading)
        assertFalse(state.exists)
    }

    @Test
    fun `save is a no-op after a failed load`() = runTest {
        coEvery { api.get() } throws RuntimeException("boom")

        vm.load()
        advanceUntilIdle()

        vm.update_subject("Out of office")
        vm.update_body("Back next week")
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { api.upsert(any()) }
    }

    @Test
    fun `set_enabled after a failed load reverts and never writes`() = runTest {
        coEvery { api.get() } throws RuntimeException("boom")

        vm.load()
        advanceUntilIdle()

        vm.set_enabled(true)
        advanceUntilIdle()

        assertFalse(vm.state.value.is_enabled)
        coVerify(exactly = 0) { api.upsert(any()) }
    }

    @Test
    fun `successful load clears load_failed`() = runTest {
        coEvery { api.get() } returns null

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.state.value.load_failed)
    }
}
