//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import org.astermail.android.ui.mail.AsterTimePreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsterTimePreferencesTest {

    @After
    fun reset() {
        AsterTimePreferences.set_account_time_format(null)
        AsterTimePreferences.set_account_time_zone(null)
        AsterTimePreferences.set_use_24h(false)
    }

    @Test
    fun device_setting_applies_when_no_account_preference() {
        AsterTimePreferences.set_account_time_format(null)
        AsterTimePreferences.set_use_24h(true)
        assertTrue(AsterTimePreferences.use_24h)

        AsterTimePreferences.set_use_24h(false)
        assertFalse(AsterTimePreferences.use_24h)
    }

    @Test
    fun account_preference_overrides_device_setting() {
        AsterTimePreferences.set_use_24h(false)
        AsterTimePreferences.set_account_time_format("24h")
        assertTrue(AsterTimePreferences.use_24h)

        AsterTimePreferences.set_use_24h(true)
        assertTrue(AsterTimePreferences.use_24h)

        AsterTimePreferences.set_account_time_format("12h")
        assertFalse(AsterTimePreferences.use_24h)
    }

    @Test
    fun clearing_account_preference_falls_back_to_device_setting() {
        AsterTimePreferences.set_use_24h(true)
        AsterTimePreferences.set_account_time_format("12h")
        assertFalse(AsterTimePreferences.use_24h)

        AsterTimePreferences.set_account_time_format(null)
        assertTrue(AsterTimePreferences.use_24h)
    }

    @Test
    fun unknown_account_preference_is_ignored() {
        AsterTimePreferences.set_use_24h(true)
        AsterTimePreferences.set_account_time_format("auto")
        assertTrue(AsterTimePreferences.use_24h)
    }

    @Test
    fun generation_changes_only_when_resolved_value_changes() {
        AsterTimePreferences.set_use_24h(false)
        AsterTimePreferences.set_account_time_format(null)
        val start = AsterTimePreferences.generation

        AsterTimePreferences.set_account_time_format("12h")
        assertEquals(start, AsterTimePreferences.generation)

        AsterTimePreferences.set_account_time_format("24h")
        assertEquals(start + 1, AsterTimePreferences.generation)
    }

    @Test
    fun valid_time_zone_is_adopted_and_auto_clears_it() {
        AsterTimePreferences.set_account_time_zone("Europe/Berlin")
        assertEquals("Europe/Berlin", AsterTimePreferences.time_zone?.id)

        AsterTimePreferences.set_account_time_zone("auto")
        assertNull(AsterTimePreferences.time_zone)
    }

    @Test
    fun invalid_time_zone_is_ignored() {
        AsterTimePreferences.set_account_time_zone("Not/AZone")
        assertNull(AsterTimePreferences.time_zone)
    }
}
