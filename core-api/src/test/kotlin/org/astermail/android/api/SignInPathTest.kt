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

package org.astermail.android.api

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class SignInPathTest {
    @Test
    fun second_factor_sign_in_paths_never_carry_another_accounts_bearer() {
        listOf(
            "/api/core/v1/auth/login",
            "/api/core/v1/auth/salt",
            "/api/core/v1/auth/register",
            "/api/core/v1/auth/refresh",
            "/api/core/v1/auth/totp/verify",
            "/api/core/v1/auth/totp/backup-code",
            "/api/core/v1/auth/hardware-keys/assert/initiate",
            "/api/core/v1/auth/hardware-keys/assert/verify",
            "/api/core/v1/auth/recovery/start",
            "/api/core/v1/guard/sign-in/status",
            "/api/core/v1/guard/sign-in/rescue/status",
            "/api/core/v1/guard/veto",
            "/api/core/v1/recovery/initiate",
            "/api/core/v1/recovery/verifier-salts",
            "/api/core/v1/recovery/complete",
            "/api/core/v1/recovery/forgot-password",
            "/api/core/v1/recovery/reset-password",
            "/api/core/v1/recovery/reset-password/vault",
            "/api/core/v1/recovery/reset-password/second-factor/status",
            "/api/core/v1/recovery/phrase/initiate",
            "/api/core/v1/recovery/escrow/fetch",
            "/api/core/v1/recovery/email/verify-token",
            "/api/core/v1/recovery/email/link-status",
            "/api/core/v1/recovery/email/resend-pending",
        ).forEach { assertTrue(it, is_sign_in_path(it)) }
    }

    @Test
    fun signed_in_paths_keep_the_bearer() {
        listOf(
            "/api/core/v1/auth/me",
            "/api/core/v1/auth/profiles",
            "/api/core/v1/auth/vault",
            "/api/core/v1/auth/hardware-keys",
            "/api/core/v1/auth/hardware-keys/register/initiate",
            "/api/core/v1/auth/hardware-keys/step-up/options",
            "/api/core/v1/auth/totp/enable",
            "/api/mail/v1/messages",
            "/api/core/v1/guard/requests",
            "/api/core/v1/guard",
        ).forEach { assertFalse(it, is_sign_in_path(it)) }
    }

    @Test
    fun signed_in_recovery_paths_keep_the_bearer() {
        listOf(
            "/api/core/v1/recovery/methods",
            "/api/core/v1/recovery/inactive",
            "/api/core/v1/recovery/inactive/fetch",
            "/api/core/v1/recovery/inactive/consume",
            "/api/core/v1/recovery/inactive/unlock-with-code",
            "/api/core/v1/recovery/codes/status",
            "/api/core/v1/recovery/codes/verify-step-up",
            "/api/core/v1/recovery/backup",
            "/api/core/v1/recovery/phrase",
            "/api/core/v1/recovery/escrow",
            "/api/core/v1/recovery/escrow/key",
            "/api/core/v1/recovery/device-secrets",
            "/api/core/v1/recovery/device-secrets/fetch",
            "/api/core/v1/recovery/device-secrets/delete",
            "/api/core/v1/recovery/options",
            "/api/core/v1/recovery/email",
            "/api/core/v1/recovery/email/resend",
            "/api/core/v1/recovery/email/server-enc",
        ).forEach { assertFalse(it, is_sign_in_path(it)) }
    }
}
