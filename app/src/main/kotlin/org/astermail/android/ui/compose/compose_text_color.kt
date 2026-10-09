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

package org.astermail.android.ui.compose

import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.Color
import org.astermail.android.ui.mail.contrast_ratio

internal const val compose_text_min_contrast = 4.5

internal fun compose_display_text_color(stored_argb: Int, background_argb: Int, readable_argb: Int): Int {
    val stored = Color(stored_argb or 0xFF000000.toInt())
    val background = Color(background_argb or 0xFF000000.toInt())
    return if (contrast_ratio(stored, background) >= compose_text_min_contrast) stored_argb else readable_argb
}

internal class compose_default_color_span(
    stored_argb: Int,
    private val display_argb: Int,
) : ForegroundColorSpan(stored_argb) {
    override fun updateDrawState(tp: TextPaint) {
        tp.color = display_argb
    }
}
