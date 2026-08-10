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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class EmailBannerScreenshotTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun save_screenshot(name: String) {
        val bitmap = runCatching { compose_rule.onRoot().captureToImage().asAndroidBitmap() }
            .getOrNull() ?: return
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun capture_stacked_banners() {
        compose_rule.setContent {
            AsterTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AsterMaterial.colors.bg_primary)
                        .padding(vertical = 8.dp),
                ) {
                    unsubscribe_banner(on_unsubscribe = {})
                    external_content_banner(
                        counts = ExternalContentCounts(image_count = 12, tracker_count = 3, font_count = 2, css_count = 1),
                        on_allow_once = {},
                        on_always_allow = {},
                    )
                    traffic_saver_banner(
                        counts = ExternalContentCounts(image_count = 12, tracker_count = 0, font_count = 2, css_count = 0),
                        on_load_once = {},
                        on_disable_traffic_saving = {},
                    )
                }
            }
        }
        compose_rule.waitForIdle()

        save_screenshot("email_banners_stacked")

        val root_height = compose_rule.onRoot().fetchSemanticsNode().size.height
        assertTrue("stacked banners should stay compact, was $root_height px", root_height < 500)
    }
}
