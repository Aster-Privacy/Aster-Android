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

package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostAliasExpiryTest {

    private val day_millis = 24L * 60L * 60L * 1000L

    @Test
    fun `expire becomes eligible thirty days after creation`() {
        val created = java.time.Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
        assertEquals(created + 30L * day_millis, ghost_expire_eligible_at("2026-10-01T12:00:00Z"))
    }

    @Test
    fun `expire eligibility parses offset and fractional timestamps`() {
        val expected = java.time.Instant.parse("2026-10-31T12:00:00Z").toEpochMilli()
        assertEquals(expected, ghost_expire_eligible_at("2026-10-01T12:00:00.123456+00:00")?.let { it - it % 1000L })
    }

    @Test
    fun `expire eligibility is unknown for a malformed timestamp`() {
        assertNull(ghost_expire_eligible_at("not a date"))
    }

    @Test
    fun `alias at the ninety day cap is at max lifetime`() {
        assertTrue(ghost_at_max_lifetime("2026-10-01T00:00:00Z", "2026-12-30T00:00:00Z"))
    }

    @Test
    fun `alias within a minute of the cap is at max lifetime`() {
        assertTrue(ghost_at_max_lifetime("2026-10-01T00:00:00Z", "2026-12-29T23:59:30Z"))
    }

    @Test
    fun `alias short of the cap can be extended`() {
        assertFalse(ghost_at_max_lifetime("2026-10-01T00:00:00Z", "2026-10-31T00:00:00Z"))
    }

    @Test
    fun `missing timestamps never block an extend`() {
        assertFalse(ghost_at_max_lifetime(null, "2026-12-30T00:00:00Z"))
        assertFalse(ghost_at_max_lifetime("2026-10-01T00:00:00Z", null))
        assertFalse(ghost_at_max_lifetime("bad", "bad"))
    }
}
