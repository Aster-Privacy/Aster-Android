// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.astermail.android.R
import org.astermail.android.design.components.aster_linear_progress

@Composable
internal fun aster_boot_placeholder(background_argb: Int) {
    val background = Color(background_argb)
    val ink = if (background.luminance() < 0.5f) Color.White else Color.Black
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = org.astermail.android.ui.common.cached_raster_painter(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(22.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        aster_linear_progress(
            color = ink.copy(alpha = 0.55f),
            track_color = ink.copy(alpha = 0.14f),
            modifier = Modifier
                .width(160.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}
