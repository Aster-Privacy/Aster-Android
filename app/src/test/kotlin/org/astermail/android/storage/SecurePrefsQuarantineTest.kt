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

package org.astermail.android.storage

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecurePrefsQuarantineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val prefs_name = "aster_ratchet_state_v1"
    private val payload = "<map><string name=\"ratchet_state_conv\">opaque</string></map>"

    private fun mock_context(shared_prefs_dir: File): Context {
        val files_dir = File(shared_prefs_dir.parentFile, "files").apply { mkdirs() }
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns files_dir
        every { context.packageManager } throws RuntimeException("keystore unavailable")
        every { context.getSharedPreferences(eq(prefs_name), any()) } throws
            RuntimeException("keystore unavailable")
        every { context.deleteSharedPreferences(any()) } answers {
            File(shared_prefs_dir, firstArg<String>() + ".xml").delete()
        }
        return context
    }

    @Test
    fun `keystore failure never deletes the encrypted prefs file`() {
        val root = temp.newFolder("data")
        val shared_prefs_dir = File(root, "shared_prefs").apply { mkdirs() }
        File(shared_prefs_dir, "$prefs_name.xml").writeText(payload)

        SecurePrefs.forget_for_test(prefs_name)
        SecurePrefs.open(mock_context(shared_prefs_dir), prefs_name)

        val recoverable = shared_prefs_dir.listFiles().orEmpty().filter {
            it.name.startsWith(prefs_name)
        }
        assertTrue(
            "expected the payload to survive, found ${shared_prefs_dir.list()?.toList()}",
            recoverable.any { it.readText() == payload },
        )
    }
}
