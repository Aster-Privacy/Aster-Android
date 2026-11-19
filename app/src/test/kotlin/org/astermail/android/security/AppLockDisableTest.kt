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

package org.astermail.android.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockDisableTest {

    private class FakeLock(
        private val unseal_results: MutableList<Boolean> = mutableListOf(true, true),
        private val marker_commits: Boolean = true,
        private val pin_commits: Boolean = true,
    ) {
        var key_open = true
        var stores_sealed = true
        var marker = true
        var pin_saved = true

        fun requires_pin(): Boolean = AppLockPresenceRule.requires_lock(AppLockPresenceRule.resolve(pin_saved, marker))

        fun fully_enabled(): Boolean = key_open && stores_sealed && pin_saved && requires_pin()

        fun fully_disabled(): Boolean = !key_open && !stores_sealed && !pin_saved && !marker

        val steps = AppLockDisableSteps(
            unseal = {
                val ok = unseal_results.removeFirstOrNull() ?: true
                if (ok) stores_sealed = false
                ok
            },
            reseal = { if (key_open) stores_sealed = true },
            close_key = { key_open = false },
            reopen_key = {
                key_open = true
                stores_sealed = true
            },
            write_marker = { present ->
                if (marker_commits || present) marker = present
                marker_commits || present
            },
            clear_pin = {
                if (pin_commits) pin_saved = false
                pin_commits
            },
        )
    }

    @Test
    fun `turning the lock off clears every part of it`() {
        val lock = FakeLock()
        assertTrue(run_app_lock_disable(lock.steps))
        assertTrue(lock.fully_disabled())
        assertFalse(lock.requires_pin())
    }

    @Test
    fun `a pin settings write that fails leaves the lock fully on`() {
        val lock = FakeLock(pin_commits = false)
        assertFalse(run_app_lock_disable(lock.steps))
        assertTrue(lock.fully_enabled())
        assertTrue(lock.marker)
    }

    @Test
    fun `a marker write that fails leaves the lock fully on`() {
        val lock = FakeLock(marker_commits = false)
        assertFalse(run_app_lock_disable(lock.steps))
        assertTrue(lock.fully_enabled())
    }

    @Test
    fun `stores that cannot be unsealed keep the lock on`() {
        val lock = FakeLock(unseal_results = mutableListOf(false))
        assertFalse(run_app_lock_disable(lock.steps))
        assertTrue(lock.fully_enabled())
    }

    @Test
    fun `stores that cannot be unsealed after the key closes reopen the key`() {
        val lock = FakeLock(unseal_results = mutableListOf(true, false))
        assertFalse(run_app_lock_disable(lock.steps))
        assertTrue(lock.fully_enabled())
    }
}
