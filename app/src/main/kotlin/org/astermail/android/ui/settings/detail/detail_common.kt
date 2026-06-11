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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterTopBar

@Composable
internal fun detail_scaffold(
    title: String,
    on_back: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        AsterTopBar(title = title, on_back = on_back, trailing = trailing)
        AsterDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AsterSpacing.lg),
            content = content,
        )
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
internal fun detail_row(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    on_click: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
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
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
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
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                fontSize = (size_dp / 2.4).sp,
                fontWeight = FontWeight.SemiBold,
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
