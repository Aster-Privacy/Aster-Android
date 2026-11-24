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

package org.astermail.android.ui.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterExpandVisibility
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.aster_linear_progress
import org.astermail.android.mail.IndexProgress

internal enum class SearchStatusKind { indexing_mail, indexing_text, waiting_for_wifi, paused, partial }

internal enum class SearchStatusAction { pause, resume, retry }

internal data class SearchStatus(
    val kind: SearchStatusKind,
    val done: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float?
        get() = if (total > 0) body_index_fraction(done, total) else null

    val action: SearchStatusAction?
        get() = when (kind) {
            SearchStatusKind.indexing_mail, SearchStatusKind.indexing_text -> SearchStatusAction.pause
            SearchStatusKind.paused -> SearchStatusAction.resume
            SearchStatusKind.partial -> SearchStatusAction.retry
            SearchStatusKind.waiting_for_wifi -> null
        }
}

internal fun search_status_of(
    has_query: Boolean,
    metadata_indexing: Boolean,
    paused: Boolean,
    metadata_progress: IndexProgress?,
    body_progress: IndexProgress?,
    partial: Boolean,
): SearchStatus? {
    val metadata = metadata_progress?.takeIf { it.total > 0 }
    val body = body_progress?.takeIf { it.total > 0 && it.indexed < it.total }
    return when {
        paused -> {
            val shown = if (metadata_indexing || body == null) metadata else body
            SearchStatus(SearchStatusKind.paused, shown?.indexed ?: 0, shown?.total ?: 0)
        }
        metadata_indexing -> SearchStatus(SearchStatusKind.indexing_mail, metadata?.indexed ?: 0, metadata?.total ?: 0)
        body != null -> SearchStatus(
            if (body.waiting) SearchStatusKind.waiting_for_wifi else SearchStatusKind.indexing_text,
            body.indexed,
            body.total,
        )
        partial && has_query -> SearchStatus(SearchStatusKind.partial)
        else -> null
    }
}

private fun search_status_label_res(kind: SearchStatusKind): Int = when (kind) {
    SearchStatusKind.indexing_mail -> R.string.search_status_indexing_mail
    SearchStatusKind.indexing_text -> R.string.search_status_indexing_text
    SearchStatusKind.waiting_for_wifi -> R.string.search_status_waiting_wifi
    SearchStatusKind.paused -> R.string.search_status_paused
    SearchStatusKind.partial -> R.string.search_status_partial
}

private fun search_status_action_res(action: SearchStatusAction): Int = when (action) {
    SearchStatusAction.pause -> R.string.pause
    SearchStatusAction.resume -> R.string.resume
    SearchStatusAction.retry -> R.string.retry
}

@Composable
internal fun search_status_strip(
    status: SearchStatus?,
    on_action: (SearchStatusAction) -> Unit,
) {
    val steady = org.astermail.android.design.remember_steady_value(status)
    val retained = remember { arrayOfNulls<SearchStatus>(1) }
    if (steady != null) retained[0] = steady
    AsterExpandVisibility(visible = steady != null) {
        val shown = steady ?: retained[0]
        if (shown != null) search_status_content(shown, on_action)
    }
}

@Composable
private fun search_status_content(
    status: SearchStatus,
    on_action: (SearchStatusAction) -> Unit,
) {
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    val number_format = remember { java.text.NumberFormat.getIntegerInstance() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_status_strip"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(start = AsterSpacing.lg, end = AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            Crossfade(
                targetState = status.kind,
                animationSpec = androidx.compose.animation.core.tween(if (reduce_motion) 0 else 180),
                modifier = Modifier.weight(1f),
                label = "search_status_label",
            ) { kind ->
                Text(
                    text = stringResource(search_status_label_res(kind)),
                    color = colors.text_secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status.total > 0) {
                Text(
                    text = stringResource(
                        R.string.search_status_count,
                        number_format.format(status.done.coerceAtMost(status.total)),
                        number_format.format(status.total),
                    ),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
            }
            val action = status.action
            if (action != null) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .clip(SquircleShape(8.dp))
                        .clickable(role = Role.Button) { on_action(action) }
                        .padding(horizontal = AsterSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(search_status_action_res(action)),
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg)
                .height(2.dp)
                .clip(SquircleShape(1.dp)),
        ) {
            val fraction = status.fraction
            val track = colors.text_primary.copy(alpha = 0.10f)
            when {
                status.kind == SearchStatusKind.partial -> Unit
                fraction == null && status.kind == SearchStatusKind.indexing_mail -> aster_linear_progress(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colors.accent_blue,
                    track_color = track,
                )
                else -> {
                    val animated by animateFloatAsState(
                        targetValue = fraction ?: 0f,
                        animationSpec = androidx.compose.animation.core.tween(if (reduce_motion) 0 else 400),
                        label = "search_status_progress",
                    )
                    val active = status.kind == SearchStatusKind.indexing_mail ||
                        status.kind == SearchStatusKind.indexing_text
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = if (active) colors.accent_blue else colors.text_muted,
                        trackColor = track,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
        Spacer(Modifier.height(AsterSpacing.sm))
    }
}
