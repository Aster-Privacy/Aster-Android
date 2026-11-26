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

package org.astermail.android.ui.common

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import java.util.concurrent.ConcurrentHashMap

object raster_painter_cache {
    private val bitmaps = ConcurrentHashMap<Int, ImageBitmap>()

    fun bitmap(resources: Resources, @DrawableRes id: Int): ImageBitmap =
        bitmaps[id] ?: ImageBitmap.imageResource(resources, id).also { bitmaps.putIfAbsent(id, it) }

    fun prewarm(resources: Resources, ids: IntArray) {
        for (id in ids) {
            if (bitmaps.containsKey(id)) continue
            runCatching { bitmap(resources, id).prepareToDraw() }
        }
    }
}

@Composable
fun cached_raster_painter(@DrawableRes id: Int): Painter {
    val resources = LocalContext.current.resources
    return remember(id) { BitmapPainter(raster_painter_cache.bitmap(resources, id)) }
}
