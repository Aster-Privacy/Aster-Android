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

package org.astermail.android.api.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCodeApiTest {

    @Test
    fun normalize_strips_separators_and_uppercases() {
        assertEquals("QEME77ET", normalize_device_code("qeme-77et"))
        assertEquals("QEME77ET", normalize_device_code("  QEME 77ET  "))
        assertEquals("QEME77ET", normalize_device_code("qeme-77et-extra"))
        assertEquals("", normalize_device_code("----"))
    }

    @Test
    fun normalize_keeps_ascii_only_so_it_matches_the_server() {
        assertEquals("QEME77E", normalize_device_code("qeme-77eé"))
        assertEquals("QEME77E", normalize_device_code("qeme-77e٠"))
        assertEquals("", normalize_device_code("فقك"))
        assertEquals("TITLE123", normalize_device_code("title123"))
    }

    @Test
    fun format_groups_the_code_in_two_blocks() {
        assertEquals("", format_device_code(""))
        assertEquals("QEME", format_device_code("qeme"))
        assertEquals("QEME-7", format_device_code("qeme7"))
        assertEquals("QEME-77ET", format_device_code("qeme77et"))
        assertEquals("QEME-77ET", format_device_code("QEME-77ET"))
    }

    @Test
    fun expired_or_unknown_code_maps_to_code_not_found() {
        assertEquals(
            DeviceLinkError.CodeNotFound,
            map_device_link_error(404, """{"error":"invalid or expired code","code":"NOT_FOUND"}"""),
        )
        assertEquals(DeviceLinkError.CodeNotFound, map_device_link_error(400, """{"code":"NOT_FOUND"}"""))
    }

    @Test
    fun enrolled_device_maps_to_already_linked() {
        assertEquals(
            DeviceLinkError.AlreadyLinked,
            map_device_link_error(
                409,
                """{"error":"device key already enrolled to another account","code":"CONFLICT"}""",
            ),
        )
    }

    @Test
    fun bridge_plan_gate_maps_to_plan_upgrade_required() {
        assertEquals(
            DeviceLinkError.PlanUpgradeRequired,
            map_device_link_error(403, """{"error":"plan_upgrade_required","required_tier":"star"}"""),
        )
    }

    @Test
    fun redis_outage_maps_to_service_unavailable() {
        assertEquals(DeviceLinkError.ServiceUnavailable, map_device_link_error(503, ""))
    }

    @Test
    fun other_failures_are_left_to_the_shared_mapper() {
        assertNull(map_device_link_error(401, """{"error":"unauthorized","code":"UNAUTHORIZED"}"""))
        assertNull(map_device_link_error(429, """{"error":"slow down"}"""))
        assertNull(map_device_link_error(500, "not json at all"))
    }
}
