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

package org.astermail.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutedNotificationFallbackTest {

    private val deals = setOf("promotions")
    private val no_folders = emptySet<String>()

    @Test
    fun `failed fetch with muted deals posts silently`() {
        val mute_active = MailPollingWorker.has_mute_filters(deals, no_folders)
        assertEquals(
            FallbackAlert.Silent,
            MailPollingWorker.fallback_alert(fetched_any_page = false, protected_pending = false, mute_filters_active = mute_active),
        )
    }

    @Test
    fun `failed fetch with muted folder posts silently`() {
        val mute_active = MailPollingWorker.has_mute_filters(emptySet(), setOf("folder_token"))
        assertEquals(
            FallbackAlert.Silent,
            MailPollingWorker.fallback_alert(fetched_any_page = false, protected_pending = false, mute_filters_active = mute_active),
        )
    }

    @Test
    fun `failed fetch without mute filters keeps the audible fallback`() {
        assertEquals(
            FallbackAlert.Audible,
            MailPollingWorker.fallback_alert(fetched_any_page = false, protected_pending = false, mute_filters_active = false),
        )
    }

    @Test
    fun `fetched page with only muted mail posts nothing`() {
        assertEquals(
            FallbackAlert.None,
            MailPollingWorker.fallback_alert(fetched_any_page = true, protected_pending = false, mute_filters_active = true),
        )
    }

    @Test
    fun `protected folder mail still alerts`() {
        assertEquals(
            FallbackAlert.Audible,
            MailPollingWorker.fallback_alert(fetched_any_page = true, protected_pending = true, mute_filters_active = true),
        )
    }

    @Test
    fun `undecryptable mail is silent while a category is muted`() {
        assertTrue(MailPollingWorker.is_candidate_silent(is_undecryptable = true, muted_categories = deals))
    }

    @Test
    fun `undecryptable mail alerts when no category is muted`() {
        assertFalse(MailPollingWorker.is_candidate_silent(is_undecryptable = true, muted_categories = emptySet()))
    }

    @Test
    fun `readable mail that passed the mute filter alerts`() {
        assertFalse(MailPollingWorker.is_candidate_silent(is_undecryptable = false, muted_categories = deals))
    }

    @Test
    fun `locked push defers to the worker when a category is muted`() {
        assertTrue(MailPollingWorker.locked_push_needs_fetch(deals, no_folders))
    }

    @Test
    fun `locked push keeps the instant generic alert without mute filters`() {
        assertFalse(MailPollingWorker.locked_push_needs_fetch(emptySet(), no_folders))
    }
}
