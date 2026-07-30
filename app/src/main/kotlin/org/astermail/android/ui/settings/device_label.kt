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

package org.astermail.android.ui.settings

private val device_type_words = setOf("mobile", "desktop", "tablet", "phone", "unknown")

fun device_display_name(browser: String, device_type: String): String {
    val from_browser = browser.trim()
    if (from_browser.isNotEmpty() && !from_browser.equals("unknown", ignoreCase = true)) {
        return from_browser.replaceFirstChar { it.uppercase() }
    }
    val from_type = device_type.trim()
    if (from_type.isNotEmpty()) return from_type.replaceFirstChar { it.uppercase() }
    return ""
}

fun device_display_platform(name: String, os: String): String {
    val trimmed = os.trim()
    if (trimmed.isEmpty() || trimmed.equals("unknown", ignoreCase = true)) return ""
    if (name.contains(trimmed, ignoreCase = true)) return ""
    return trimmed
}

fun clean_trusted_device_label(raw: String): String {
    var label = raw.trim()
    if (label.isEmpty()) return ""

    val paren = Regex("\\s*\\(([^()]*)\\)\\s*$").find(label)
    if (paren != null && device_type_words.contains(paren.groupValues[1].trim().lowercase())) {
        label = label.removeRange(paren.range).trim()
    }

    val on_split = Regex("^(.*?)\\s+on\\s+(.*)$").find(label)
    if (on_split != null) {
        val client = on_split.groupValues[1].trim()
        val platform = on_split.groupValues[2].trim()
        if (platform.isEmpty() ||
            platform.equals("unknown", ignoreCase = true) ||
            client.contains(platform, ignoreCase = true)
        ) {
            label = client
        }
    }

    return label.trim()
}
