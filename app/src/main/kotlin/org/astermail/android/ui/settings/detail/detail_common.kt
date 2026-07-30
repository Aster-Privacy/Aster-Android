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

package org.astermail.android.ui.settings.detail

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.ui.mail.avatar_initial_style

@Composable
internal fun relative_time_label(iso: String?): String {
    val unknown = stringResource(R.string.unknown)
    if (iso.isNullOrBlank()) return unknown
    val instant = try {
        java.time.OffsetDateTime.parse(iso).toInstant()
    } catch (_: Throwable) {
        try {
            java.time.Instant.parse(iso)
        } catch (_: Throwable) {
            null
        }
    } ?: return iso.take(10)
    val minutes = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
    val absolute = java.time.format.DateTimeFormatter
        .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)
    return when {
        minutes < 0 -> absolute
        minutes < 1 -> stringResource(R.string.active_now)
        minutes < 60 -> stringResource(R.string.minutes_ago, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.hours_ago, (minutes / 60).toInt())
        minutes < 60 * 24 * 7 -> stringResource(R.string.days_ago, (minutes / (60 * 24)).toInt())
        else -> absolute
    }
}

@Composable
internal fun detail_scaffold(
    title: String,
    on_back: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    scroll_state: ScrollState? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val effective_scroll = scroll_state ?: rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        AsterTopBar(
            title = title,
            on_back = on_back,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing?.invoke()
                    org.astermail.android.ui.settings.settings_search_action()
                }
            },
        )
        AsterDivider()
        if (scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(effective_scroll)
                    .padding(AsterSpacing.lg),
                content = content,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }
    }
}

@Composable
internal fun section_label(text: String) {
    val colors = AsterMaterial.colors
    Text(
        text = text.uppercase(),
        color = colors.text_tertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = AsterSpacing.md, bottom = AsterSpacing.xs),
    )
}

@Composable
internal fun info_dialog_button(title: String, description: String) {
    val colors = AsterMaterial.colors
    var show by remember { mutableStateOf(false) }
    Icon(
        imageVector = TablerIcons.InfoCircle,
        contentDescription = stringResource(R.string.info),
        tint = colors.text_muted,
        modifier = Modifier
            .size(15.dp)
            .clickable { show = true },
    )
    if (show) {
        AsterAlertDialog(
            on_dismiss = { show = false },
            title = title,
            message = description,
            confirm_label = stringResource(R.string.got_it),
            on_confirm = { show = false },
        )
    }
}

@Composable
internal fun detail_row(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    on_click: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    info_title: String? = null,
    info_description: String? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (on_click != null) Modifier.clickable(onClick = on_click) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (info_title != null && info_description != null) {
                    Spacer(Modifier.width(5.dp))
                    info_dialog_button(title = info_title, description = info_description)
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (on_click != null) {
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun avatar_circle(seed: String, size_dp: Int = 60, image_url: String? = null) {
    val colors = AsterMaterial.colors
    val initials_fallback = @Composable {
        val initial = seed.firstOrNull()?.uppercase() ?: "A"
        Box(
            modifier = Modifier
                .size(size_dp.dp)
                .background(colors.avatar_bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                color = colors.avatar_text,
                style = avatar_initial_style((size_dp / 2.4).sp),
            )
        }
    }
    if (!image_url.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = image_url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size_dp.dp)
                .clip(CircleShape),
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                is AsyncImagePainter.State.Error -> initials_fallback()
                else -> initials_fallback()
            }
        }
    } else {
        initials_fallback()
    }
}

@Composable
internal fun verified_badge(text: String = "Verified") {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .background(colors.success.copy(alpha = 0.15f), SquircleShape(8.dp))
            .padding(horizontal = AsterSpacing.sm, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = colors.success,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun v_gap(height: androidx.compose.ui.unit.Dp = AsterSpacing.md) {
    Spacer(Modifier.height(height))
}

@Composable
internal fun error_banner(message: String) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.danger.copy(alpha = 0.12f), SquircleShape(18.dp))
            .padding(AsterSpacing.md),
    ) {
        Text(text = message, color = colors.danger, fontSize = 13.sp)
    }
}
