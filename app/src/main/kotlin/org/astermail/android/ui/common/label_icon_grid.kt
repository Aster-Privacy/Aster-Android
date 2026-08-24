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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun label_icon_grid(
    selected_icon: String?,
    accent_color: Color,
    on_select: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors

    Column(modifier = modifier.fillMaxWidth()) {
        label_icon_groups.forEachIndexed { index, group ->
            if (index > 0) {
                Spacer(Modifier.height(AsterSpacing.sm))
            }
            Text(
                text = stringResource(group.title),
                color = colors.text_muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(AsterSpacing.xs))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.icons.forEach { key ->
                    val vector = label_icon_or_null(key) ?: return@forEach
                    val is_selected = key == selected_icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(SquircleShape(8.dp))
                            .background(
                                if (is_selected) accent_color.copy(alpha = 0.15f) else colors.bg_hover
                            )
                            .then(
                                if (is_selected)
                                    Modifier.border(1.dp, accent_color, SquircleShape(8.dp))
                                else
                                    Modifier
                            )
                            .clickable {
                                on_select(if (is_selected) null else key)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = vector,
                            contentDescription = key,
                            tint = if (is_selected) accent_color else colors.text_secondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
