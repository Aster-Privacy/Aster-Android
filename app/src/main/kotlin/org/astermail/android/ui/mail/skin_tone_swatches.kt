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

package org.astermail.android.ui.mail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.preferences.apply_emoji_skin_tone
import org.astermail.android.api.preferences.emoji_skin_tones
import org.astermail.android.api.preferences.normalize_emoji_skin_tone
import org.astermail.android.design.AsterMaterial

private const val skin_tone_sample_emoji = "👋"

@StringRes
internal fun skin_tone_label(tone: String): Int = when (tone) {
    "light" -> R.string.skin_tone_light
    "medium_light" -> R.string.skin_tone_medium_light
    "medium" -> R.string.skin_tone_medium
    "medium_dark" -> R.string.skin_tone_medium_dark
    "dark" -> R.string.skin_tone_dark
    else -> R.string.skin_tone_default
}

@Composable
internal fun skin_tone_swatch_row(
    selected: String,
    on_select: (String) -> Unit,
    modifier: Modifier = Modifier,
    swatch_size: Dp = 40.dp,
) {
    val colors = AsterMaterial.colors
    val current = normalize_emoji_skin_tone(selected)
    Row(
        modifier = modifier
            .selectableGroup()
            .testTag("skin_tone_swatches"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emoji_skin_tones.forEach { tone ->
            val is_selected = tone == current
            val label = stringResource(skin_tone_label(tone))
            Box(
                modifier = Modifier
                    .size(swatch_size)
                    .clip(CircleShape)
                    .background(if (is_selected) colors.bg_tertiary else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (is_selected) colors.accent_blue else Color.Transparent,
                        shape = CircleShape,
                    )
                    .selectable(
                        selected = is_selected,
                        role = Role.RadioButton,
                        onClick = { if (!is_selected) on_select(tone) },
                    )
                    .semantics { contentDescription = label }
                    .testTag("skin_tone_$tone"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = apply_emoji_skin_tone(skin_tone_sample_emoji, tone),
                    fontSize = (swatch_size.value * 0.5f).sp,
                    maxLines = 1,
                )
            }
        }
    }
}
