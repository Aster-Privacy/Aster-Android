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

package org.astermail.android.auth

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRecoverySignOutSourceTest {

    private val source = File("src/main/kotlin/org/astermail/android/auth/AuthRepository.kt")
        .readText()
        .replace("\r\n", "\n")

    private fun body_of(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("missing $signature", start >= 0)
        val next = Regex("""\n    (private |internal |)(suspend )?fun """).find(source, start + signature.length)
        return source.substring(start, next?.range?.first ?: source.length)
    }

    @Test
    fun explicit_sign_out_forgets_device_recovery_snapshots() {
        val body = body_of("private suspend fun sign_out_internal(")
        val branch = Regex("""if \(remove_account\) \{\n(.*?)\n        \}""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "sign_out_internal must forget device recovery when it removes the account",
            branch.any { "forget_deleted_account_device_recovery(context," in it },
        )
    }

    @Test
    fun forced_sign_out_keeps_device_recovery_for_password_reset() {
        val body = body_of("private suspend fun sign_out_internal(")
        val outside = Regex("""if \(remove_account\) \{\n.*?\n        \}""", RegexOption.DOT_MATCHES_ALL)
            .replace(body, "")
        assertTrue(
            "device recovery must only be forgotten on an explicit sign-out",
            "forget_deleted_account_device_recovery" !in outside,
        )
    }

    @Test
    fun account_deletion_still_forgets_device_recovery() {
        val body = body_of("suspend fun delete_account(")
        assertTrue("forget_deleted_account_device_recovery(context, current_id)" in body)
    }
}
