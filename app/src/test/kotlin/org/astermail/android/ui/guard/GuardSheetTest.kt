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
package org.astermail.android.ui.guard

import androidx.compose.ui.window.SecureFlagPolicy
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.Json
import org.astermail.android.R
import org.astermail.android.guard.guard_context_view
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GuardSheetTest {

    @Test
    fun approve_dialog_window_blocks_capture() {
        assertEquals(SecureFlagPolicy.SecureOn, guard_approve_dialog_properties.securePolicy)
        assertFalse(guard_approve_dialog_properties.dismissOnClickOutside)
    }

    @Test
    fun flag_from_region_code() {
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", guard_flag_emoji("DE"))
        assertEquals("\uD83C\uDDF5\uD83C\uDDF9", guard_flag_emoji("pt"))
    }

    @Test
    fun flag_rejects_non_codes() {
        assertNull(guard_flag_emoji(null))
        assertNull(guard_flag_emoji(""))
        assertNull(guard_flag_emoji("Germany"))
        assertNull(guard_flag_emoji("D1"))
    }

    @Test
    fun country_name_from_code() {
        assertEquals("Germany", guard_country_name("DE", Locale.US))
        assertEquals("Deutschland", guard_country_name("DE", Locale.GERMANY))
        assertEquals("Atlantis", guard_country_name("Atlantis", Locale.US))
        assertNull(guard_country_name(" ", Locale.US))
    }

    @Test
    fun place_text_joins_city_and_country() {
        assertEquals("Berlin, Germany", guard_place_text("Berlin", "DE", Locale.US))
        assertEquals("Germany", guard_place_text(null, "DE", Locale.US))
        assertEquals("", guard_place_text(null, null, Locale.US))
    }

    @Test
    fun countdown_formats_minutes_and_seconds() {
        assertEquals("4:32", guard_countdown_text(272, Locale.US))
        assertEquals("0:05", guard_countdown_text(5, Locale.US))
        assertEquals("0:00", guard_countdown_text(-3, Locale.US))
    }

    @Test
    fun os_kind_matches_names() {
        assertEquals(guard_os_kind.windows, guard_os_kind_for("Windows 11", "desktop"))
        assertEquals(guard_os_kind.apple, guard_os_kind_for("macOS", "desktop"))
        assertEquals(guard_os_kind.apple, guard_os_kind_for("iOS", "mobile"))
        assertEquals(guard_os_kind.android, guard_os_kind_for("Android", "mobile"))
        assertEquals(guard_os_kind.chromeos, guard_os_kind_for("ChromeOS", "desktop"))
        assertEquals(guard_os_kind.linux, guard_os_kind_for("Linux", "desktop"))
        assertEquals(guard_os_kind.phone, guard_os_kind_for("Unknown", "mobile"))
        assertEquals(guard_os_kind.unknown, guard_os_kind_for(null, ""))
    }

    @Test
    fun epoch_parses_iso_or_returns_null() {
        assertEquals(0L, guard_epoch_millis("1970-01-01T00:00:00Z"))
        assertNull(guard_epoch_millis(""))
        assertNull(guard_epoch_millis("soon"))
    }

    @Test
    fun region_code_prefers_country_code() {
        val json = Json { ignoreUnknownKeys = true }
        val view = json.decodeFromString(
            guard_context_view.serializer(),
            """{"device_type":"desktop","city":"Porto","country":"PT","country_code":"PT","requested_at":"2026-11-15T10:00:00Z"}""",
        )
        assertEquals("PT", view.region_code())
        assertEquals("DE", guard_context_view(country = "DE").region_code())
        assertNull(guard_context_view(country = "Germany").region_code())
    }

    @Test
    fun change_progress_tracks_elapsed_share() {
        val start = Instant.parse("2026-11-10T00:00:00Z")
        val end = start.plus(Duration.ofDays(4))
        assertEquals(0.25f, guard_change_progress(start, end, start.plus(Duration.ofDays(1)))!!, 0.001f)
        assertEquals(1f, guard_change_progress(start, end, end.plus(Duration.ofDays(1)))!!, 0.001f)
        assertEquals(0f, guard_change_progress(start, end, start.minusSeconds(60))!!, 0.001f)
        assertNull(guard_change_progress(null, end, start))
    }

    @Test
    fun wait_amount_picks_a_readable_unit() {
        assertEquals(guard_wait_amount(R.plurals.guard_wait_days, 5), guard_wait_amount_for(Duration.ofHours(5 * 24 + 7)))
        assertEquals(guard_wait_amount(R.plurals.guard_wait_hours, 22), guard_wait_amount_for(Duration.ofHours(22)))
        assertEquals(guard_wait_amount(R.plurals.guard_wait_hours, 47), guard_wait_amount_for(Duration.ofHours(47)))
        assertEquals(guard_wait_amount(R.plurals.guard_wait_minutes, 90), guard_wait_amount_for(Duration.ofMinutes(90)))
        assertEquals(guard_wait_amount(R.plurals.guard_wait_minutes, 1), guard_wait_amount_for(Duration.ofSeconds(10)))
    }
}
