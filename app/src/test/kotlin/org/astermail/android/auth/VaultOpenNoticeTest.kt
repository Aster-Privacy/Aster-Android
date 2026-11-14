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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultOpenNoticeTest {
    @Test
    fun `a vault that cannot be opened raises the notice until it is shown`() {
        val notice = VaultOpenNotice()
        assertFalse(notice.failed.value)
        notice.report_failed()
        assertTrue(notice.failed.value)
        notice.consume()
        assertFalse(notice.failed.value)
    }

    @Test
    fun `a later sign-in that opens the vault clears a pending notice`() {
        val notice = VaultOpenNotice()
        notice.report_failed()
        notice.report_opened()
        assertFalse(notice.failed.value)
    }

    @Test
    fun `sign-in reports both outcomes of opening the vault`() {
        val source = java.io.File("src/main/kotlin/org/astermail/android/auth/AuthRepository.kt").readText()
        val failure = source.indexOf("vault_open_notice.report_failed()")
        assertTrue(failure > 0)
        assertTrue(source.indexOf("vault decryption failed", failure) in failure..(failure + 300))
        assertTrue(source.contains("vault_open_notice.report_opened()"))
    }
}
