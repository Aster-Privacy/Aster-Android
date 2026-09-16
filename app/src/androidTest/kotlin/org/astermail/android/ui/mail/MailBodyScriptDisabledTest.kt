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

package org.astermail.android.ui.mail

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MailBodyScriptDisabledTest {

    private data class BodyViewSettings(
        val javascript_enabled: Boolean,
        val javascript_opens_windows: Boolean,
        val file_access: Boolean,
        val content_access: Boolean,
        val dom_storage: Boolean,
    )

    private fun mail_body_view_settings(allow_external: Boolean): BodyViewSettings {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val holder = arrayOfNulls<BodyViewSettings>(1)
        instrumentation.runOnMainSync {
            val view = WebView(context)
            configure_mail_body_web_view(view, 100, allow_external)
            holder[0] = BodyViewSettings(
                javascript_enabled = view.settings.javaScriptEnabled,
                javascript_opens_windows = view.settings.javaScriptCanOpenWindowsAutomatically,
                file_access = view.settings.allowFileAccess,
                content_access = view.settings.allowContentAccess,
                dom_storage = view.settings.domStorageEnabled,
            )
            view.destroy()
        }
        return holder[0]!!
    }

    @Test
    fun the_mail_body_view_runs_no_script() {
        val settings = mail_body_view_settings(allow_external = false)
        assertFalse("the mail body WebView must never run script", settings.javascript_enabled)
    }

    @Test
    fun allowing_remote_images_does_not_bring_script_back() {
        val settings = mail_body_view_settings(allow_external = true)
        assertFalse("remote images must not re-enable script", settings.javascript_enabled)
        assertFalse("remote images must not open windows", settings.javascript_opens_windows)
    }

    @Test
    fun the_mail_body_view_reaches_no_local_storage() {
        val settings = mail_body_view_settings(allow_external = false)
        assertFalse("the mail body must not read local files", settings.file_access)
        assertFalse("the mail body must not read content providers", settings.content_access)
        assertFalse("the mail body must not keep dom storage", settings.dom_storage)
    }

    @Test
    fun the_document_the_view_loads_denies_script() {
        val document = build_email_html(
            body = "<p>Hello</p>",
            is_dark = false,
            fg_hex = "#111827",
            link_hex = "#2563eb",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image could not be loaded",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "auto",
        )
        assertTrue("the body document must deny script outright", document.contains("script-src 'none'"))
        assertFalse("the body template must add no script of its own", document.contains("<script"))
    }
}
