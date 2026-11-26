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
import org.junit.Assert.assertNull
import org.junit.Test

class PushDistributorChoiceTest {

    private val own = "org.astermail.android"
    private val ntfy = "io.heckel.ntfy"
    private val other = "org.example.distributor"

    @Test
    fun built_in_is_the_default_when_nothing_is_chosen() {
        val result = resolve_push_distributor(own, null, null, listOf(own, ntfy))
        assertEquals(own, result.target)
        assertNull(result.missing_choice)
    }

    @Test
    fun existing_registration_is_kept_when_nothing_is_chosen() {
        val result = resolve_push_distributor(own, null, ntfy, listOf(own, ntfy))
        assertEquals(ntfy, result.target)
    }

    @Test
    fun chosen_distributor_wins_over_the_acknowledged_one() {
        val result = resolve_push_distributor(own, ntfy, own, listOf(own, ntfy))
        assertEquals(ntfy, result.target)
        assertNull(result.missing_choice)
    }

    @Test
    fun uninstalled_choice_falls_back_to_built_in() {
        val result = resolve_push_distributor(own, ntfy, ntfy, listOf(own, other))
        assertEquals(own, result.target)
        assertEquals(ntfy, result.missing_choice)
    }

    @Test
    fun uninstalled_choice_without_play_services_uses_another_distributor() {
        val result = resolve_push_distributor(own, ntfy, null, listOf(other))
        assertEquals(other, result.target)
        assertEquals(ntfy, result.missing_choice)
    }

    @Test
    fun built_in_choice_is_never_reported_missing() {
        val result = resolve_push_distributor(own, own, null, emptyList())
        assertNull(result.missing_choice)
        assertNull(result.target)
        assertEquals(own, selected_push_distributor(own, own, null, emptyList()))
    }

    @Test
    fun picker_lists_only_external_distributors_once() {
        assertEquals(listOf(ntfy, other), external_push_distributors(own, listOf(own, ntfy, "", ntfy, other)))
    }
}
