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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing

@Composable
fun detail_skeleton(modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val brush = shimmer_brush()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .padding(AsterSpacing.lg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
        )
        Spacer(Modifier.height(AsterSpacing.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(brush),
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
                )
            }
        }
        Spacer(Modifier.height(AsterSpacing.xl))

        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (it == 5) 0.4f else 1f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun email_body_skeleton(modifier: Modifier = Modifier) {
    val brush = shimmer_brush()

    Column(modifier = modifier.padding(horizontal = 8.dp, vertical = AsterSpacing.md)) {
        val widths = listOf(1f, 0.95f, 1f, 0.85f, 1f, 0.9f, 1f, 0.7f, 1f, 0.5f)
        widths.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(13.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
