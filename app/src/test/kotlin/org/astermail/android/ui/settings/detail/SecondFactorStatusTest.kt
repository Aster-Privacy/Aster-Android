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

import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.ui.guard.guard_criterion_met
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondFactorStatusTest {

    @Test
    fun no_method_set_up_reports_none() {
        assertEquals(second_factor_method.none, second_factor_method_for(totp_enabled = false, security_key_count = 0))
        assertFalse(has_second_factor(totp_enabled = false, security_key_count = 0))
    }

    @Test
    fun a_security_key_alone_counts_as_the_second_factor() {
        assertEquals(second_factor_method.security_key, second_factor_method_for(totp_enabled = false, security_key_count = 2))
        assertTrue(has_second_factor(totp_enabled = false, security_key_count = 1))
    }

    @Test
    fun an_authenticator_app_alone_counts_as_the_second_factor() {
        assertEquals(second_factor_method.authenticator, second_factor_method_for(totp_enabled = true, security_key_count = 0))
    }

    @Test
    fun both_methods_are_reported_together() {
        assertEquals(second_factor_method.both, second_factor_method_for(totp_enabled = true, security_key_count = 1))
    }

    @Test
    fun score_counts_a_key_for_both_the_second_factor_and_the_key_check() {
        val key_only = security_score(security_score_input(security_key_count = 1))

        assertEquals(2, key_only)
    }

    @Test
    fun score_counts_the_second_factor_once_when_both_methods_are_set_up() {
        val both = security_score(security_score_input(totp_enabled = true, security_key_count = 1))
        val key_only = security_score(security_score_input(security_key_count = 1))

        assertEquals(key_only, both)
    }

    @Test
    fun a_fully_protected_account_with_only_a_key_reaches_the_maximum() {
        val all_with_key = security_score_input(
            security_key_count = 1,
            recovery_email_verified = true,
            login_alerts_enabled = true,
            block_tracking_pixels = true,
            block_remote_images = true,
            strip_exif_on_compose = true,
        )

        assertEquals(security_score_max, security_score(all_with_key))
        assertEquals(security_score_max, security_score(all_with_key.copy(totp_enabled = true)))
    }

    @Test
    fun removing_the_last_key_drops_the_second_factor() {
        val with_key = security_score_input(security_key_count = 1, recovery_email_verified = true)
        val without_key = with_key.copy(security_key_count = 0)

        assertEquals(2, security_score(with_key) - security_score(without_key))
        assertFalse(has_second_factor(totp_enabled = false, security_key_count = 0))
    }

    @Test
    fun aster_guard_is_left_out_of_the_score_when_hidden() {
        val input = security_score_input(totp_enabled = true)
        assertEquals(security_score_max, security_score_max_for(input))
        assertEquals(1, security_score(input))
    }

    @Test
    fun aster_guard_weighs_the_same_as_two_factor() {
        val off = security_score_input(guard_met = false)
        val on = off.copy(guard_met = true)
        val with_two_factor = off.copy(totp_enabled = true)
        assertEquals(security_score_max + 1, security_score_max_for(off))
        assertEquals(security_score(with_two_factor) - security_score(off), security_score(on) - security_score(off))
        assertEquals(1, security_score(on))
    }

    @Test
    fun aster_guard_counts_when_new_device_approval_is_on() {
        assertTrue(guard_criterion_met(GuardStatus(switches = GuardSwitch.NEW_DEVICE_APPROVAL)))
        assertTrue(guard_criterion_met(GuardStatus(switches = GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES)))
        assertFalse(guard_criterion_met(GuardStatus(switches = GuardSwitch.HOLD_CHANGES)))
        assertFalse(guard_criterion_met(GuardStatus()))
    }
}
