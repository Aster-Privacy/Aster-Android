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

package org.astermail.android.ui.mail

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class TrackerBannerTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun save(name: String, bitmap: Bitmap?) {
        bitmap ?: return
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun device_screenshot(name: String) {
        save(name, runCatching { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() }.getOrNull())
    }

    @Test
    fun tapping_the_banner_tracker_count_opens_the_tracker_dialog() {
        val report = EmailHtmlSanitizer.analyze_trackers(
            """<img src="https://open.mailmetrics.example/o/1.gif" width="1" height="1">""" +
                """<img src="https://open.mailmetrics.example/o/2.gif" width="1" height="1">""" +
                """<img src="https://t.beacon.example/open?id=9" width="1" height="1">""",
        )
        var opened = 0
        compose_rule.setContent {
            AsterTheme(use_dark_theme = false) {
                var show by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AsterMaterial.colors.bg_primary)
                        .padding(vertical = 24.dp),
                ) {
                    external_content_banner(
                        counts = ExternalContentCounts(image_count = 4, tracker_count = 3, font_count = 0, css_count = 0),
                        on_allow_once = {},
                        on_always_allow = {},
                        on_show_trackers = {
                            opened++
                            show = true
                        },
                    )
                    external_content_banner(
                        counts = ExternalContentCounts(image_count = 0, tracker_count = 1, font_count = 0, css_count = 0),
                        on_allow_once = {},
                        on_always_allow = null,
                        on_show_trackers = {},
                    )
                }
                if (show) tracker_details_dialog(report = report, on_close = { show = false })
            }
        }
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("4 images", useUnmergedTree = true).assertIsDisplayed()
        compose_rule.onNodeWithText("1 tracking pixel", useUnmergedTree = true).assertIsDisplayed()
        save("tracking_banner", runCatching { compose_rule.onRoot().captureToImage().asAndroidBitmap() }.getOrNull())

        compose_rule.onNodeWithText("3 tracking pixels", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose_rule.waitForIdle()

        assertEquals(1, opened)
        compose_rule.onNodeWithText("open.mailmetrics.example").assertIsDisplayed()
        compose_rule.onNodeWithText("t.beacon.example").assertIsDisplayed()
        compose_rule.onNodeWithText("x2").assertIsDisplayed()
        Thread.sleep(500)
        device_screenshot("tracking_banner_dialog")
    }

    @Test
    fun the_details_sheet_breaks_the_tracker_total_into_pixels_and_links() {
        compose_rule.setContent {
            AsterTheme(use_dark_theme = false) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AsterMaterial.colors.bg_primary),
                ) {
                    message_details_panel(
                        sender = "Weekly digest <news@example.com>",
                        reply_to = null,
                        is_encrypted = false,
                        tracking_pixel_count = 3,
                        tracking_link_count = 2,
                        date_text = "3 October 2026 at 09:14",
                        received_on = null,
                        authentication = null,
                        authentication_failed = false,
                        on_show_trackers = {},
                    )
                }
            }
        }
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("View encryption details", useUnmergedTree = true).performClick()
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("5 trackers blocked\n3 tracking pixels · 2 tracking links", useUnmergedTree = true)
            .assertIsDisplayed()
        Thread.sleep(500)
        device_screenshot("tracking_details_sheet")
    }

    @Test
    fun the_details_sheet_omits_an_empty_part_of_the_breakdown() {
        compose_rule.setContent {
            AsterTheme(use_dark_theme = false) {
                message_details_panel(
                    sender = "Weekly digest <news@example.com>",
                    reply_to = null,
                    is_encrypted = false,
                    tracking_pixel_count = 0,
                    tracking_link_count = 1,
                    date_text = "3 October 2026 at 09:14",
                    received_on = null,
                    authentication = null,
                    authentication_failed = false,
                    on_show_trackers = {},
                )
            }
        }
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("View encryption details", useUnmergedTree = true).performClick()
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("1 tracker blocked\n1 tracking link", useUnmergedTree = true).assertIsDisplayed()
    }
}
