/*
 * Aster Mail Android
 * Copyright (C) 2026 Aster Privacy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.astermail.android.ui.settings.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

internal val billing_plan_shape: Shape = SquircleShape(16.dp)

private val quantity_prefix = Regex("^(\\d[\\d.,]*(?:\\s?[GMT]B)?)\\s+(.+)$", RegexOption.IGNORE_CASE)

@Composable
internal fun plan_feature_label(@StringRes feature_res: Int): AnnotatedString {
    val colors = AsterMaterial.colors
    val raw = stringResource(feature_res)
    val match = quantity_prefix.find(raw)
    return buildAnnotatedString {
        if (match == null) {
            append(raw)
        } else {
            withStyle(SpanStyle(color = colors.text_primary, fontWeight = FontWeight.SemiBold)) {
                append(match.groupValues[1])
            }
            append(" ")
            append(match.groupValues[2])
        }
    }
}

@Composable
internal fun plan_feature_line(@StringRes feature_res: Int) {
    val colors = AsterMaterial.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = plan_feature_icon(feature_res),
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(17.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = plan_feature_label(feature_res),
            color = colors.text_secondary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
}
