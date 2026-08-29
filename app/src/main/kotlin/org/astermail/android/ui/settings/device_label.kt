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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.DeviceDesktop
import compose.icons.tablericons.DeviceLaptop
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.DeviceTablet
import compose.icons.tablericons.Devices
import compose.icons.tablericons.Key
import compose.icons.tablericons.Plug
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.World
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

private val device_type_words = setOf("mobile", "desktop", "tablet", "phone", "unknown")

private val bridge_hints = listOf("bridge", "imap", "smtp", "thunderbird", "mail client")

private val tablet_hints = listOf("tablet", "ipad")

private val phone_hints = listOf("android", "iphone", "ipod", "mobile", "phone", "ios")

private val desktop_app_hints = listOf("aster mail", "astermail", "tauri", "electron", "desktop app", "webview")

private val browser_hints = listOf(
    "chrome",
    "chromium",
    "firefox",
    "safari",
    "edge",
    "opera",
    "brave",
    "vivaldi",
    "samsung internet",
    "browser",
)

enum class DeviceClientKind { bridge, tablet, phone, desktop_app, browser, desktop }

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

fun device_client_kind(browser: String, device_type: String, os: String = ""): DeviceClientKind {
    val haystack = listOf(browser, device_type, os).joinToString(" ").lowercase()
    return when {
        bridge_hints.any { haystack.contains(it) } -> DeviceClientKind.bridge
        tablet_hints.any { haystack.contains(it) } -> DeviceClientKind.tablet
        phone_hints.any { haystack.contains(it) } -> DeviceClientKind.phone
        desktop_app_hints.any { haystack.contains(it) } -> DeviceClientKind.desktop_app
        browser_hints.any { haystack.contains(it) } -> DeviceClientKind.browser
        else -> DeviceClientKind.desktop
    }
}

fun device_client_icon(kind: DeviceClientKind): ImageVector = when (kind) {
    DeviceClientKind.bridge -> TablerIcons.Plug
    DeviceClientKind.tablet -> TablerIcons.DeviceTablet
    DeviceClientKind.phone -> TablerIcons.DeviceMobile
    DeviceClientKind.desktop_app -> TablerIcons.DeviceDesktop
    DeviceClientKind.browser -> TablerIcons.World
    DeviceClientKind.desktop -> TablerIcons.DeviceLaptop
}

fun device_client_label_res(kind: DeviceClientKind): Int = when (kind) {
    DeviceClientKind.bridge -> R.string.session_client_bridge
    DeviceClientKind.tablet -> R.string.session_client_tablet
    DeviceClientKind.phone -> R.string.session_client_phone
    DeviceClientKind.desktop_app -> R.string.session_client_desktop_app
    DeviceClientKind.browser -> R.string.session_client_browser
    DeviceClientKind.desktop -> R.string.session_client_desktop
}

fun link_device_icon(device_type: String): ImageVector = when (device_type.lowercase()) {
    "bridge" -> TablerIcons.Plug
    "desktop" -> TablerIcons.DeviceDesktop
    else -> TablerIcons.Devices
}

fun link_device_step_icon(index: Int): ImageVector = when (index) {
    0 -> TablerIcons.Key
    1 -> TablerIcons.ShieldCheck
    else -> TablerIcons.Check
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

@Composable
fun device_badge(label: String, color: Color = AsterMaterial.colors.accent_blue) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), SquircleShape(8.dp))
            .padding(horizontal = AsterSpacing.sm, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun this_device_badge() {
    device_badge(label = stringResource(R.string.this_device))
}

@Composable
fun device_client_avatar(kind: DeviceClientKind, size_dp: Int = 40) {
    val colors = AsterMaterial.colors
    Icon(
        imageVector = device_client_icon(kind),
        contentDescription = null,
        tint = colors.text_tertiary,
        modifier = Modifier
            .size(size_dp.dp)
            .padding((size_dp * 0.22f).dp),
    )
}
