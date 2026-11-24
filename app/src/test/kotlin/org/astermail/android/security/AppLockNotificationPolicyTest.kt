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

package org.astermail.android.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockNotificationPolicyTest {

    @Test
    fun content_is_hidden_only_when_the_lock_and_the_setting_are_on() {
        assertTrue(AppLockNotificationPolicy.hides_content(app_lock_configured = true, hide_setting = true))
        assertFalse(AppLockNotificationPolicy.hides_content(app_lock_configured = true, hide_setting = false))
        assertFalse(AppLockNotificationPolicy.hides_content(app_lock_configured = false, hide_setting = true))
    }

    @Test
    fun the_lock_screen_version_is_redacted_whenever_the_app_is_locked() {
        assertTrue(AppLockNotificationPolicy.redacts_public_version(app_lock_configured = true, private_mode = false))
        assertTrue(AppLockNotificationPolicy.redacts_public_version(app_lock_configured = false, private_mode = true))
        assertFalse(AppLockNotificationPolicy.redacts_public_version(app_lock_configured = false, private_mode = false))
    }

    @Test
    fun copy_code_action_never_appears_while_the_app_is_locked() {
        assertFalse(AppLockNotificationPolicy.offers_copy_code(app_lock_configured = true, has_code = true))
        assertTrue(AppLockNotificationPolicy.offers_copy_code(app_lock_configured = false, has_code = true))
        assertFalse(AppLockNotificationPolicy.offers_copy_code(app_lock_configured = false, has_code = false))
    }
}

class DeviceCredentialPolicyTest {

    @Test
    fun android_11_and_later_use_the_crypto_bound_prompt() {
        assertEquals(DeviceCredentialRoute.BIOMETRIC_PROMPT, DeviceCredentialPolicy.route(30))
        assertEquals(DeviceCredentialRoute.BIOMETRIC_PROMPT, DeviceCredentialPolicy.route(36))
        assertTrue(DeviceCredentialPolicy.prepares_before_prompt(DeviceCredentialRoute.BIOMETRIC_PROMPT))
    }

    @Test
    fun older_versions_use_the_keyguard_confirmation() {
        assertEquals(DeviceCredentialRoute.KEYGUARD_CONFIRM, DeviceCredentialPolicy.route(26))
        assertEquals(DeviceCredentialRoute.KEYGUARD_CONFIRM, DeviceCredentialPolicy.route(29))
        assertFalse(DeviceCredentialPolicy.prepares_before_prompt(DeviceCredentialRoute.KEYGUARD_CONFIRM))
    }
}
