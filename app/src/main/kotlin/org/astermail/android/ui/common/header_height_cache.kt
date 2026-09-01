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

package org.astermail.android.ui.common

import android.content.Context

private const val header_prefs_name = "aster_ui_metrics"

private fun header_key(id: String, width_dp: Int, orientation: Int): String =
    "header_" + id + "_" + width_dp + "_" + orientation

fun cached_header_height_px(
    context: Context,
    id: String,
    width_dp: Int,
    orientation: Int,
): Int = context
    .getSharedPreferences(header_prefs_name, Context.MODE_PRIVATE)
    .getInt(header_key(id, width_dp, orientation), 0)

fun store_header_height_px(
    context: Context,
    id: String,
    width_dp: Int,
    orientation: Int,
    height_px: Int,
) {
    if (height_px <= 0) return
    val prefs = context.getSharedPreferences(header_prefs_name, Context.MODE_PRIVATE)
    val key = header_key(id, width_dp, orientation)
    if (prefs.getInt(key, 0) == height_px) return
    prefs.edit().putInt(key, height_px).apply()
}
