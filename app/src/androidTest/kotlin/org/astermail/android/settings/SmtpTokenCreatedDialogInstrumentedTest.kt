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

package org.astermail.android.settings

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.api.settings.SmtpTokenSettings
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.smtp_token_created_dialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmtpTokenCreatedDialogInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private val context = instrumentation.targetContext

    private val settings = SmtpTokenSettings(
        host = "smtp.example-under-test.com",
        port = 587,
        security = "STARTTLS",
        username = "token-user",
        password = "token-secret-under-test",
    )

    private fun show_dialog() {
        compose.setContent {
            AsterTheme {
                smtp_token_created_dialog(settings = settings, on_done = {})
            }
        }
        compose.waitForIdle()
    }

    private fun dialog_window_flags(): Int {
        val node = compose.onNode(isDialog()).fetchSemanticsNode()
        val root = node.root as? ViewRootForTest
        assertNotNull("the dialog has no compose root", root)
        var cursor: View? = root!!.view
        while (cursor != null && cursor !is DialogWindowProvider) {
            cursor = cursor.parent as? View
        }
        val provider = cursor as? DialogWindowProvider
        assertNotNull("the dialog root has no window provider", provider)
        var flags = 0
        instrumentation.runOnMainSync { flags = provider!!.window.attributes.flags }
        return flags
    }

    @Test
    fun the_created_token_dialog_window_blocks_screen_capture() {
        show_dialog()

        val flags = dialog_window_flags()

        assertEquals(
            "the token dialog window must carry FLAG_SECURE",
            WindowManager.LayoutParams.FLAG_SECURE,
            flags and WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    @Test
    fun copy_all_marks_the_clip_as_sensitive() {
        show_dialog()

        compose.onNodeWithText(context.getString(R.string.smtp_token_copy_all)).performClick()
        compose.waitForIdle()

        val sensitive_key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        var sensitive = false
        var copied = ""
        instrumentation.runOnMainSync {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            sensitive = clipboard.primaryClipDescription?.extras?.getBoolean(sensitive_key, false) == true
            copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        }

        assertTrue("the copied token must be marked sensitive", sensitive)
        assertTrue("the copied text must hold the token", copied.contains(settings.password))
    }
}
