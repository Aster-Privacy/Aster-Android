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

package org.astermail.android.ui.upgrade

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60L * MINUTE_MS
private const val DAY_MS = 24L * HOUR_MS

internal fun offer_countdown_text(remaining_ms: Long): String {
    if (remaining_ms <= 0L) return ""
    val days = remaining_ms / DAY_MS
    val hours = (remaining_ms % DAY_MS) / HOUR_MS
    val minutes = (remaining_ms % HOUR_MS) / MINUTE_MS
    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes.coerceAtLeast(1L)}m"
    }
}

internal fun parse_offer_end(ends_at: String?): Instant? =
    ends_at?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

@Composable
fun offer_countdown(ends_at: Instant, modifier: Modifier = Modifier) {
    var now_ms by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ends_at) {
        while (true) {
            now_ms = System.currentTimeMillis()
            if (now_ms >= ends_at.toEpochMilli()) break
            delay(MINUTE_MS)
        }
    }
    val text = offer_countdown_text(ends_at.toEpochMilli() - now_ms)
    if (text.isEmpty()) return
    Text(
        text = stringResource(R.string.special_offer_ends_in, text),
        color = AsterMaterial.colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}
