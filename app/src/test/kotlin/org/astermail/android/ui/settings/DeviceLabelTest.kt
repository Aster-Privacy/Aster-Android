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

package org.astermail.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLabelTest {

    @Test
    fun android_session_drops_the_mobile_prefix() {
        assertEquals("Aster Mail Android", device_display_name("Aster Mail Android", "mobile"))
    }

    @Test
    fun browser_session_keeps_the_browser_name() {
        assertEquals("Chrome", device_display_name("Chrome", "desktop"))
    }

    @Test
    fun unknown_browser_falls_back_to_device_type() {
        assertEquals("Desktop", device_display_name("Unknown", "desktop"))
    }

    @Test
    fun no_browser_and_no_device_type_yields_empty() {
        assertEquals("", device_display_name("", ""))
    }

    @Test
    fun platform_is_hidden_when_the_name_already_says_it() {
        assertEquals("", device_display_platform("Aster Mail Android", "Android"))
    }

    @Test
    fun platform_is_shown_when_it_adds_information() {
        assertEquals("Windows", device_display_platform("Chrome", "Windows"))
    }

    @Test
    fun unknown_platform_is_hidden() {
        assertEquals("", device_display_platform("Chrome", "Unknown"))
    }

    @Test
    fun trusted_label_drops_device_type_parenthetical() {
        assertEquals("Aster Mail Android", clean_trusted_device_label("Aster Mail Android (mobile)"))
    }

    @Test
    fun trusted_label_drops_redundant_platform() {
        assertEquals(
            "Aster Mail Android",
            clean_trusted_device_label("Aster Mail Android on Android (mobile)"),
        )
    }

    @Test
    fun trusted_label_keeps_informative_platform() {
        assertEquals("Chrome on Windows", clean_trusted_device_label("Chrome on Windows (desktop)"))
    }

    @Test
    fun trusted_label_drops_unknown_platform() {
        assertEquals("Chrome", clean_trusted_device_label("Chrome on Unknown (desktop)"))
    }

    @Test
    fun trusted_label_keeps_a_user_supplied_name_untouched() {
        assertEquals("Work laptop", clean_trusted_device_label("Work laptop"))
    }

    @Test
    fun trusted_label_keeps_a_meaningful_parenthetical() {
        assertEquals("Chrome (work)", clean_trusted_device_label("Chrome (work)"))
    }

    @Test
    fun blank_trusted_label_stays_blank() {
        assertEquals("", clean_trusted_device_label("   "))
    }
}
