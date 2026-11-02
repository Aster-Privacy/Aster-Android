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
import androidx.compose.ui.platform.LocalContext
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

internal data class offer_countdown_formats(
    val days_hours: (Int, Int) -> String = { d, h -> "${d}d ${h}h" },
    val hours_minutes: (Int, Int) -> String = { h, m -> "${h}h ${m}m" },
    val minutes: (Int) -> String = { m -> "${m}m" },
)

internal fun offer_countdown_text(
    remaining_ms: Long,
    formats: offer_countdown_formats = offer_countdown_formats(),
): String {
    if (remaining_ms <= 0L) return ""
    val days = remaining_ms / DAY_MS
    val hours = (remaining_ms % DAY_MS) / HOUR_MS
    val minutes = (remaining_ms % HOUR_MS) / MINUTE_MS
    return when {
        days > 0L -> formats.days_hours(days.toInt(), hours.toInt())
        hours > 0L -> formats.hours_minutes(hours.toInt(), minutes.toInt())
        else -> formats.minutes(minutes.coerceAtLeast(1L).toInt())
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
    val resources = LocalContext.current.resources
    val formats = remember(resources) {
        offer_countdown_formats(
            days_hours = { d, h -> resources.getString(R.string.special_offer_countdown_days_hours, d, h) },
            hours_minutes = { h, m -> resources.getString(R.string.special_offer_countdown_hours_minutes, h, m) },
            minutes = { m -> resources.getString(R.string.special_offer_countdown_minutes, m) },
        )
    }
    val text = offer_countdown_text(ends_at.toEpochMilli() - now_ms, formats)
    if (text.isEmpty()) return
    Text(
        text = stringResource(R.string.special_offer_ends_in, text),
        color = AsterMaterial.colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}
