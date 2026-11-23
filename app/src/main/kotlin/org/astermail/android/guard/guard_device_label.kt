//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.guard

import java.util.Locale

const val GUARD_DEVICE_LABEL_MAX = 64
const val GUARD_DEVICE_LABEL_FALLBACK = "Aster for Android"

fun guard_device_label_of(manufacturer: String?, model: String?): String {
    val maker = manufacturer.orEmpty().trim()
    val name = model.orEmpty().trim()
    val label = when {
        name.isEmpty() -> maker
        maker.isEmpty() || name.startsWith(maker, ignoreCase = true) -> name
        else -> "${maker.replaceFirstChar { it.titlecase(Locale.ROOT) }} $name"
    }
    return guard_known_label(label)?.take(GUARD_DEVICE_LABEL_MAX)?.trim() ?: GUARD_DEVICE_LABEL_FALLBACK
}

fun guard_device_label(): String = guard_device_label_of(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
