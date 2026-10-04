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

import android.graphics.Bitmap
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class TrackingPixelHighlightScreenTest {

    @get:Rule
    val compose_rule = createAndroidComposeRule<org.astermail.android.MainActivity>()

    private val newsletter =
        """<div style="background-color:#ffffff;color:#111827;padding:16px">""" +
            """<h2 style="margin:0 0 8px">Autumn reading list</h2>""" +
            """<p>Five books we could not put down this month.<img src="https://open.mailmetrics.example/o/1.gif" width="1" height="1"></p>""" +
            """<p>Members get 20% off until Friday.<img src="https://open.mailmetrics.example/o/2.gif" width="1" height="1"></p>""" +
            """<p style="color:#6b7280;font-size:12px">You are receiving this because you subscribed.""" +
            """<img src="https://t.beacon.example/open?id=9" width="1" height="1"></p></div>"""

    private fun message() = ThreadMessage(
        id = "digest",
        sender_name = "Riverside Books",
        sender_email = "news@riverside-books.example",
        to_label = "me",
        to_addresses = listOf("me@astermail.org"),
        timestamp = 1_790_000_000_000L,
        body = "Autumn reading list",
        body_html = newsletter,
        is_encrypted = false,
        item_type = "received",
        spf_result = "pass",
        dkim_result = "pass",
        dmarc_result = "pass",
        is_external = true,
    )

    private fun allow_screenshots() {
        compose_rule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun show(dark: Boolean) {
        val activity = compose_rule.activity
        val body_ready = AtomicBoolean(false)
        activity.runOnUiThread {
            val view = ComposeView(activity)
            view.setContent {
                AsterTheme(use_dark_theme = dark) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AsterMaterial.colors.bg_primary)
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        expanded_message(
                            msg = message(),
                            is_last = true,
                            on_collapse = {},
                            on_reply = {},
                            on_reply_all = {},
                            on_forward = {},
                            on_more = {},
                            is_first_card = true,
                            is_last_card = true,
                            on_body_ready = { body_ready.set(true) },
                        )
                    }
                }
            }
            activity.setContentView(view)
            allow_screenshots()
        }
        val deadline = System.currentTimeMillis() + 60_000
        while (!body_ready.get() && System.currentTimeMillis() < deadline) {
            compose_rule.waitForIdle()
            Thread.sleep(200)
        }
        settle(rounds = 75)
    }

    private fun settle(rounds: Int = 10) {
        repeat(rounds) {
            compose_rule.waitForIdle()
            Thread.sleep(200)
        }
    }

    private fun save_screen(name: String) {
        compose_rule.activity.runOnUiThread { allow_screenshots() }
        compose_rule.waitForIdle()
        Thread.sleep(600)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: instrumentation.targetContext.getExternalFilesDir(null)?.absolutePath
                ?: return,
        )
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun open_and_close_the_tracker_list(theme: String) {
        val activity = compose_rule.activity
        val note = activity.getString(R.string.tracking_pixels_highlighted, 3)
        compose_rule.onNodeWithTag("tracking_pixel_highlight_note").assertDoesNotExist()
        save_screen("tracking_highlight_default_$theme")

        val announced = Collections.synchronizedList(mutableListOf<String>())
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT) announced.add(event.text.joinToString(""))
        }
        try {
            compose_rule.onNodeWithText(
                activity.resources.getQuantityString(R.plurals.n_tracking_pixels, 3, 3),
                useUnmergedTree = true,
            ).performClick()
            compose_rule.waitUntil(10_000) {
                runCatching { compose_rule.onNodeWithTag("tracking_pixel_highlight_note").assertExists() }.isSuccess
            }
            compose_rule.onNodeWithTag("tracking_pixel_highlight_note", useUnmergedTree = true)
                .assertExists()
            compose_rule.onNodeWithText(note, useUnmergedTree = true).assertTextEquals(note)
            settle(rounds = 40)
            save_screen("tracking_highlight_list_open_$theme")
            assertTrue("announced: $announced", announced.contains(note))
        } finally {
            automation.setOnAccessibilityEventListener(null)
        }

        compose_rule.onNodeWithText(activity.getString(R.string.close)).performClick()
        settle(rounds = 40)
        compose_rule.onNodeWithTag("tracking_pixel_highlight_note").assertDoesNotExist()
        save_screen("tracking_highlight_closed_$theme")
    }

    @Test
    fun markers_show_only_while_the_tracker_list_is_open_light() {
        show(dark = false)
        open_and_close_the_tracker_list("light")
    }

    @Test
    fun markers_show_only_while_the_tracker_list_is_open_dark() {
        show(dark = true)
        open_and_close_the_tracker_list("dark")
    }
}
