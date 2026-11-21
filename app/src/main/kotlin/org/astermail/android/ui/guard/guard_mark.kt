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

package org.astermail.android.ui.guard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.guard.GuardDeviceInfo
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.components.AsterCompactButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.ui.settings.detail.detail_row

internal val GUARD_MARK_PATHS: List<String> = listOf(
    "M12 2.714A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.18 3.28 9.6 7.9 11.29",
    "M12 2.714A11.959 11.959 0 0 0 20.25 6h.152c.388 1.18.598 2.441.598 3.75 0 .37-.02.74-.05 1.1",
    "M14.625 16.5h5.25a1.125 1.125 0 0 1 1.125 1.125v3a1.125 1.125 0 0 1-1.125 1.125h-5.25a1.125 1.125 0 0 1-1.125-1.125v-3a1.125 1.125 0 0 1 1.125-1.125Z",
    "M15 16.5v-1.5a2.25 2.25 0 0 1 4.5 0v1.5",
)

private fun guard_stroked_icon(name: String, paths: List<String>): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach {
        builder.addPath(
            pathData = addPathNodes(it),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

val guard_mark_icon: ImageVector by lazy { guard_stroked_icon("guard_mark", GUARD_MARK_PATHS) }

val guard_phone_check_icon: ImageVector by lazy {
    guard_stroked_icon(
        "guard_phone_check",
        listOf(
            "M9 2.75h6a2.25 2.25 0 0 1 2.25 2.25v14a2.25 2.25 0 0 1-2.25 2.25H9a2.25 2.25 0 0 1-2.25-2.25V5A2.25 2.25 0 0 1 9 2.75Z",
            "M10.5 5.75h3",
            "M9.75 13l1.6 1.6 3-3.2",
        ),
    )
}

val guard_clock_icon: ImageVector by lazy {
    guard_stroked_icon(
        "guard_clock",
        listOf("M3.25 12a8.75 8.75 0 1 0 17.5 0a8.75 8.75 0 1 0 -17.5 0Z", "M12 7.5V12l3 2"),
    )
}

val guard_key_icon: ImageVector by lazy {
    guard_stroked_icon(
        "guard_key",
        listOf("M3.75 15.5a4.25 4.25 0 1 0 8.5 0a4.25 4.25 0 1 0 -8.5 0Z", "M11 12.5l8.5-8.5M16.5 7l2.5 2.5M14.5 9l1.75 1.75"),
    )
}

@Composable
fun guard_mark(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Icon(imageVector = guard_mark_icon, contentDescription = null, tint = color, modifier = modifier.size(size))
}

@Composable
private fun guard_intro_feature(icon: ImageVector, title: Int, body: Int) {
    val colors = AsterMaterial.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                color = colors.text_primary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(body),
                color = colors.text_secondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
internal fun guard_intro_features() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("guard_setup_intro"),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        guard_intro_feature(guard_phone_check_icon, R.string.guard_intro_approve_title, R.string.guard_intro_approve_body)
        guard_intro_feature(guard_clock_icon, R.string.guard_intro_hold_title, R.string.guard_intro_hold_body)
        guard_intro_feature(guard_key_icon, R.string.guard_intro_rescue_title, R.string.guard_intro_rescue_body)
    }
}

@Composable
fun guard_status_row(on: Boolean, devices: List<GuardDeviceInfo>, on_turn_on: (() -> Unit)? = null) {
    val subtitle = when {
        !on -> stringResource(R.string.guard_status_off_summary)
        devices.isNotEmpty() -> pluralStringResource(R.plurals.guard_status_devices, devices.size, devices.size)
        else -> stringResource(R.string.guard_status_on_body)
    }
    AsterCard(modifier = Modifier.fillMaxWidth().testTag("guard_status_header")) {
        detail_row(
            title = stringResource(if (on) R.string.guard_switches_status_on else R.string.guard_setup_title),
            subtitle = subtitle,
            icon = guard_mark_icon,
            trailing = if (on_turn_on != null) {
                {
                    AsterCompactButton(
                        label = stringResource(R.string.guard_status_turn_on),
                        onClick = on_turn_on,
                        modifier = Modifier.testTag("guard_setup_start"),
                    )
                }
            } else {
                null
            },
        )
    }
}
