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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityCenterLinksTest {

    private val retired_routes = setOf("privacy", "encryption")

    private fun main_activity_source(): String {
        val relative = "src/main/kotlin/org/astermail/android/MainActivity.kt"
        val candidate = File(relative).takeIf { it.exists() } ?: File("app/$relative")
        return candidate.readText()
    }

    @Test
    fun two_factor_opens_the_two_factor_screen() {
        assertEquals(
            security_check_target.screen("two_factor"),
            security_check_target_for(security_check.two_factor),
        )
    }

    @Test
    fun passkey_scrolls_to_the_passkeys_row_instead_of_encryption() {
        assertEquals(
            security_check_target.section(security_anchor.passkeys),
            security_check_target_for(security_check.passkey),
        )
    }

    @Test
    fun recovery_email_opens_the_recovery_email_screen() {
        assertEquals(
            security_check_target.screen("recovery_email"),
            security_check_target_for(security_check.recovery_email),
        )
    }

    @Test
    fun login_alerts_scrolls_to_the_login_alerts_row() {
        assertEquals(
            security_check_target.section(security_anchor.login_alerts),
            security_check_target_for(security_check.login_alerts),
        )
    }

    @Test
    fun content_protection_checks_stay_on_the_security_screen() {
        assertEquals(
            security_check_target.section(security_anchor.tracking_pixels),
            security_check_target_for(security_check.tracking_pixels),
        )
        assertEquals(
            security_check_target.section(security_anchor.remote_images),
            security_check_target_for(security_check.remote_images),
        )
        assertEquals(
            security_check_target.section(security_anchor.strip_exif),
            security_check_target_for(security_check.strip_exif),
        )
    }

    @Test
    fun no_check_points_at_a_retired_settings_screen() {
        security_check.entries.forEach { check ->
            val target = security_check_target_for(check)
            if (target is security_check_target.screen) {
                assertTrue(check.name, target.route_id !in retired_routes)
            }
        }
    }

    @Test
    fun every_screen_target_is_registered_in_the_nav_graph() {
        val source = main_activity_source()
        security_check.entries.forEach { check ->
            val target = security_check_target_for(check)
            if (target is security_check_target.screen) {
                assertTrue(
                    check.name,
                    source.contains("composable(routes.settings_detail(\"${target.route_id}\"))"),
                )
            }
        }
    }

    @Test
    fun hidden_tracking_pixels_row_falls_back_to_the_tracking_toggle() {
        val placed = setOf(security_anchor.tracking_protection)
        assertEquals(
            security_anchor.tracking_protection,
            resolve_security_anchor(security_anchor.tracking_pixels) { it in placed },
        )
    }

    @Test
    fun placed_anchor_resolves_to_itself() {
        assertEquals(
            security_anchor.tracking_pixels,
            resolve_security_anchor(security_anchor.tracking_pixels) { true },
        )
    }

    @Test
    fun unplaced_anchor_without_fallback_resolves_to_nothing() {
        assertNull(resolve_security_anchor(security_anchor.passkeys) { false })
    }
}
