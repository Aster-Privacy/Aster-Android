// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream

private const val LOG_TAG = "aster_shot"
private const val CHUNK_SIZE = 2500
private const val TARGET_WIDTH = 520

internal fun capture_screenshot(name: String, node: SemanticsNodeInteraction) {
    capture_safely(name) { node.captureToImage().asAndroidBitmap() }
}

internal fun capture_device_screenshot(name: String) {
    capture_safely(name) { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() }
}

private fun capture_safely(name: String, grab: () -> Bitmap?) {
    val bitmap = runCatching(grab).getOrNull()
    if (bitmap == null) {
        Log.w(LOG_TAG, "SKIP $name")

        return
    }
    runCatching { dump_screenshot(name, bitmap) }
        .onFailure { Log.w(LOG_TAG, "SKIP $name") }
}

private fun dump_screenshot(name: String, bitmap: Bitmap) {
    val scaled = if (bitmap.width > TARGET_WIDTH) {
        val height = bitmap.height * TARGET_WIDTH / bitmap.width
        Bitmap.createScaledBitmap(bitmap, TARGET_WIDTH, height.coerceAtLeast(1), true)
    } else {
        bitmap
    }
    val bytes = ByteArrayOutputStream().also {
        scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()
    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
    Log.i(LOG_TAG, "BEGIN $name parts=${(encoded.length + CHUNK_SIZE - 1) / CHUNK_SIZE}")
    var index = 0
    var part = 0
    while (index < encoded.length) {
        val end = minOf(index + CHUNK_SIZE, encoded.length)
        Log.i(LOG_TAG, "$name:$part:${encoded.substring(index, end)}")
        index = end
        part++
    }
    Log.i(LOG_TAG, "END $name")
}
