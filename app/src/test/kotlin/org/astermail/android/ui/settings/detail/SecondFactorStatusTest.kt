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
}
