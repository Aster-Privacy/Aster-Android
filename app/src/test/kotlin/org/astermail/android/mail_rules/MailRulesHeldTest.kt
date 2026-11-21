//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.mail_rules

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.api.mail_rules.MailRulesApi
import org.astermail.android.api.mail_rules.MatchMode
import org.astermail.android.guard.guard_hold_kind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MailRulesHeldTest {
    private val dispatcher = StandardTestDispatcher()
    private val held = ApiError.GuardHeld("c1", "mail_rule_change", "2026-11-14T11:00:00Z")
    private lateinit var api: MailRulesApi

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a held new rule shows the hold and is not saved`() = runTest(dispatcher) {
        coEvery { api.create(any()) } throws held
        val vm = MailRulesViewModel(api)
        var result: String? = "unset"

        vm.create_rule("Fwd", "#000000", true, MatchMode.ALL, emptyList(), emptyList()) { result = it }
        advanceUntilIdle()

        assertNull(result)
        assertEquals(guard_hold_kind.mail_rule_change, vm.state.value.held?.kind)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a held rule edit reports not saved with the hold`() = runTest(dispatcher) {
        coEvery { api.update(any(), any()) } throws held
        val vm = MailRulesViewModel(api)
        var ok = true

        vm.update_rule("r1", name = "Fwd") { ok = it }
        advanceUntilIdle()

        assertTrue(!ok)
        assertEquals("c1", vm.state.value.held?.id)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a held delete keeps the rule list and shows the hold`() = runTest(dispatcher) {
        coEvery { api.delete(any()) } throws held
        val vm = MailRulesViewModel(api)

        vm.delete_rule("r1")
        advanceUntilIdle()

        assertEquals("c1", vm.state.value.held?.id)
        assertNull(vm.state.value.error)
    }
}
