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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing

internal val compose_field_min_height = 48.dp

internal val compose_caret_touch_size = 48.dp

private val compose_caret_icon_size = 22.dp

private val compose_field_label_width = 56.dp

private val compose_caret_inset = (compose_caret_touch_size - compose_caret_icon_size) / 2

internal const val ime_hide_wait_ms = 600L

internal suspend fun wait_for_ime_hidden(
    ime_insets: WindowInsets,
    density: Density,
    timeout_ms: Long = ime_hide_wait_ms,
) {
    withTimeoutOrNull(timeout_ms) {
        snapshotFlow { ime_insets.getBottom(density) }.first { it == 0 }
    }
}

@Composable
internal fun compose_field_row(
    label: String,
    modifier: Modifier = Modifier,
    on_label_click: (() -> Unit)? = null,
    label_click_description: String? = null,
    label_test_tag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = compose_field_min_height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(AsterSpacing.lg + compose_field_label_width)
                .heightIn(min = compose_field_min_height)
                .then(
                    if (on_label_click != null) {
                        Modifier.clickable(
                            onClickLabel = label_click_description,
                            onClick = on_label_click,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(if (label_test_tag != null) Modifier.testTag(label_test_tag) else Modifier)
                .padding(start = AsterSpacing.lg),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.text_tertiary,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = AsterSpacing.md),
        ) {
            content()
        }
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(AsterSpacing.lg - compose_caret_inset))
        } else {
            Spacer(Modifier.width(AsterSpacing.lg))
        }
    }
}

@Composable
internal fun compose_cc_caret(expanded: Boolean, on_toggle: () -> Unit) {
    val colors = AsterMaterial.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "caret_rotation",
    )
    Box(
        modifier = Modifier
            .size(compose_caret_touch_size)
            .clip(CircleShape)
            .clickable(onClick = on_toggle)
            .testTag("cc_toggle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TablerIcons.ChevronDown,
            contentDescription = stringResource(R.string.toggle_cc_bcc),
            tint = colors.text_tertiary,
            modifier = Modifier
                .size(compose_caret_icon_size)
                .rotate(rotation),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun compose_from_row(
    address: String,
    on_open: () -> Unit,
    on_long_press: () -> Unit,
    avatar: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    compose_field_row(
        label = stringResource(R.string.from),
        modifier = Modifier
            .combinedClickable(
                hapticFeedbackEnabled = false,
                onClickLabel = stringResource(R.string.send_from),
                onClick = on_open,
                onLongClick = on_long_press,
            )
            .testTag("from_field"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (address.isBlank()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.text_tertiary.copy(alpha = 0.16f)),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.text_tertiary.copy(alpha = 0.16f)),
                )
                Spacer(Modifier.weight(1f))
            } else {
                avatar()
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text_primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = true),
                )
            }
            Spacer(Modifier.width(AsterSpacing.xs))
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = stringResource(R.string.send_from),
                tint = colors.text_tertiary,
                modifier = Modifier
                    .size(20.dp)
                    .testTag("from_chevron"),
            )
        }
    }
}
