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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertOctagon
import compose.icons.tablericons.Archive
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.Send
import compose.icons.tablericons.Trash
import org.astermail.android.R
import org.astermail.android.api.mail.MailUserStatsResponse
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.mirror_in_rtl

private val segment_inbox = Color(0xFF3B82F6)
private val segment_archived = Color(0xFF22C55E)
private val segment_sent = Color(0xFF8B5CF6)
private val segment_drafts = Color(0xFFF59E0B)
private val segment_spam = Color(0xFFF97316)
private val segment_trash = Color(0xFFEF4444)

internal data class storage_segment(
    val label: String,
    val count: Int,
    val color: Color,
    val folder_id: String = "",
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

internal fun format_count(value: Int): String =
    java.text.NumberFormat.getIntegerInstance().format(value.toLong())

internal data class mail_distribution(
    val inbox: Int,
    val archived: Int,
    val sent: Int,
    val drafts: Int,
    val spam: Int,
    val trash: Int,
    val total: Int,
)

internal fun compute_distribution(stats: MailUserStatsResponse): mail_distribution {
    val inbox = (stats.notifiable ?: stats.inbox).coerceAtLeast(0)
    val spam = stats.spam.coerceAtLeast(0)
    val archived = stats.archived.coerceAtLeast(0)
    val trash = stats.trash.coerceAtLeast(0)
    val drafts = stats.drafts.coerceAtLeast(0)
    val reported_sent = stats.sent.coerceAtLeast(0)
    val untrashed = stats.total_items.coerceAtLeast(0)
    val sent = if (untrashed > 0) {
        (untrashed - inbox - spam - archived).coerceIn(0, reported_sent)
    } else {
        reported_sent
    }
    return mail_distribution(
        inbox = inbox,
        archived = archived,
        sent = sent,
        drafts = drafts,
        spam = spam,
        trash = trash,
        total = inbox + archived + sent + drafts + spam + trash,
    )
}

@Composable
internal fun stat_row(label: String, value: String, emphasis: Boolean = false) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = colors.text_primary,
            fontSize = 14.sp,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
internal fun legend_row(segment: storage_segment, total: Int, on_open: () -> Unit) {
    val colors = AsterMaterial.colors
    val share = if (total > 0) segment.count * 100f / total else 0f
    val share_label = when {
        segment.count == 0 -> "0%"
        share < 1f -> "<1%"
        else -> "${share.toInt()}%"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_open)
            .padding(horizontal = AsterSpacing.md, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            val icon = segment.icon
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = segment.color,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(SquircleShape(5.dp))
                        .background(segment.color),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = segment.label,
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.storage_share_of_mail, share_label),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
        Text(
            text = format_count(segment.count),
            color = colors.text_secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(AsterSpacing.xs))
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(16.dp).mirror_in_rtl(),
        )
    }
}

@Composable
internal fun distribution_donut(segments: List<storage_segment>, total: Int) {
    val colors = AsterMaterial.colors
    val track_color = colors.bg_secondary
    Box(
        modifier = Modifier.size(172.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke_px = 18.dp.toPx()
            val top_left = Offset(stroke_px / 2f, stroke_px / 2f)
            val arc_size = Size(size.width - stroke_px, size.height - stroke_px)
            val stroke = Stroke(width = stroke_px, cap = StrokeCap.Butt)
            drawArc(
                color = track_color,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = top_left,
                size = arc_size,
                style = stroke,
            )
            if (total <= 0) return@Canvas
            val visible = segments.filter { it.count > 0 }
            val gap = if (visible.size > 1) 2.5f else 0f
            var cursor = -90f
            visible.forEach { segment ->
                val sweep = segment.count * 360f / total
                drawArc(
                    color = segment.color,
                    startAngle = cursor + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(1.5f),
                    useCenter = false,
                    topLeft = top_left,
                    size = arc_size,
                    style = stroke,
                )
                cursor += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = format_count(total),
                color = colors.text_primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.storage_donut_caption),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun storage_plan_section(
    plan_name: String?,
    total_bytes: Long,
    addon_bytes: Long,
    free_bytes: Long,
) {
    val included = (total_bytes - addon_bytes).coerceAtLeast(0L)
    section_label(stringResource(R.string.storage_plan_section))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        stat_row(
            label = stringResource(R.string.storage_plan_row),
            value = plan_name ?: stringResource(R.string.plan_free),
            emphasis = true,
        )
        AsterDivider()
        stat_row(
            label = stringResource(R.string.storage_included_row),
            value = format_bytes(included),
        )
        if (addon_bytes > 0) {
            AsterDivider()
            stat_row(
                label = stringResource(R.string.storage_addons_row),
                value = format_bytes(addon_bytes),
            )
        }
        AsterDivider()
        stat_row(
            label = stringResource(R.string.storage_total_row),
            value = format_bytes(total_bytes),
        )
        AsterDivider()
        stat_row(
            label = stringResource(R.string.storage_available_row),
            value = format_bytes(free_bytes),
        )
    }
    v_gap(AsterSpacing.lg)
}

@Composable
internal fun storage_distribution_section(
    stats: MailUserStatsResponse?,
    on_open_folder: (String, String) -> Unit,
) {
    if (stats == null) return
    val colors = AsterMaterial.colors
    val distribution = compute_distribution(stats)
    val segments = listOf(
        storage_segment(
            stringResource(R.string.folder_inbox), distribution.inbox, segment_inbox,
            "inbox", TablerIcons.Inbox,
        ),
        storage_segment(
            stringResource(R.string.archived), distribution.archived, segment_archived,
            "archive", TablerIcons.Archive,
        ),
        storage_segment(
            stringResource(R.string.sent), distribution.sent, segment_sent,
            "sent", TablerIcons.Send,
        ),
        storage_segment(
            stringResource(R.string.folder_drafts), distribution.drafts, segment_drafts,
            "drafts", TablerIcons.FileText,
        ),
        storage_segment(
            stringResource(R.string.folder_spam), distribution.spam, segment_spam,
            "spam", TablerIcons.AlertOctagon,
        ),
        storage_segment(
            stringResource(R.string.folder_trash), distribution.trash, segment_trash,
            "trash", TablerIcons.Trash,
        ),
    )
    val total = distribution.total
    if (total <= 0) return
    section_label(stringResource(R.string.storage_where_section))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AsterSpacing.md,
                    end = AsterSpacing.md,
                    top = AsterSpacing.lg,
                    bottom = AsterSpacing.md,
                ),
            contentAlignment = Alignment.Center,
        ) {
            distribution_donut(segments, total)
        }
        segments.filter { it.count > 0 }.forEach { segment ->
            AsterDivider()
            legend_row(segment, total) { on_open_folder(segment.folder_id, segment.label) }
        }
    }
    v_gap(AsterSpacing.sm)
    Text(
        text = stringResource(R.string.storage_where_note),
        color = colors.text_muted,
        fontSize = 12.sp,
    )
    v_gap(AsterSpacing.lg)
}

@Composable
internal fun storage_mailbox_section(stats: MailUserStatsResponse?, used_bytes: Long) {
    if (stats == null) return
    val total_messages = compute_distribution(stats).total
    section_label(stringResource(R.string.storage_mailbox_section))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        stat_row(
            label = stringResource(R.string.storage_total_messages),
            value = format_count(total_messages),
            emphasis = true,
        )
        AsterDivider()
        stat_row(
            label = stringResource(R.string.storage_unread_messages),
            value = format_count(stats.unread),
        )
        AsterDivider()
        stat_row(
            label = stringResource(R.string.storage_starred_messages),
            value = format_count(stats.starred),
        )
        if (stats.scheduled > 0) {
            AsterDivider()
            stat_row(
                label = stringResource(R.string.storage_scheduled_messages),
                value = format_count(stats.scheduled),
            )
        }
        if (total_messages > 0 && used_bytes > 0) {
            AsterDivider()
            stat_row(
                label = stringResource(R.string.storage_average_size),
                value = format_bytes(used_bytes / total_messages),
            )
        }
    }
    v_gap(AsterSpacing.lg)
}

@Composable
internal fun storage_cleanup_section(
    trash_count: Int,
    spam_count: Int,
    is_emptying_spam: Boolean,
    is_emptying_trash: Boolean,
    on_empty_trash: () -> Unit,
    on_empty_spam: () -> Unit,
    on_open_folder: (String, String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val trash_label = stringResource(R.string.folder_trash)
    val spam_label = stringResource(R.string.folder_spam)
    section_label(stringResource(R.string.storage_free_up_section))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        if (trash_count <= 0 && spam_count <= 0) {
            detail_row(title = stringResource(R.string.storage_nothing_to_clean))
        }
        if (trash_count > 0) {
            cleanup_row(
                icon = TablerIcons.Trash,
                tint = segment_trash,
                title = trash_label,
                subtitle = stringResource(R.string.storage_trash_hint),
                count = trash_count,
                action_label = stringResource(R.string.storage_empty_action),
                is_busy = is_emptying_trash,
                on_action = on_empty_trash,
                on_open = { on_open_folder("trash", trash_label) },
            )
        }
        if (trash_count > 0 && spam_count > 0) AsterDivider()
        if (spam_count > 0) {
            cleanup_row(
                icon = TablerIcons.AlertOctagon,
                tint = segment_spam,
                title = spam_label,
                subtitle = stringResource(R.string.storage_spam_hint),
                count = spam_count,
                action_label = stringResource(R.string.storage_empty_action),
                is_busy = is_emptying_spam,
                on_action = on_empty_spam,
                on_open = { on_open_folder("spam", spam_label) },
            )
        }
    }
    v_gap(AsterSpacing.sm)
    Text(
        text = stringResource(R.string.storage_free_up_note),
        color = colors.text_muted,
        fontSize = 12.sp,
    )
    v_gap(AsterSpacing.lg)
}

@Composable
private fun cleanup_row(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    count: Int,
    action_label: String,
    is_busy: Boolean,
    on_action: () -> Unit,
    on_open: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_open)
            .padding(horizontal = AsterSpacing.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.storage_cleanup_title,
                    title,
                    format_count(count),
                ),
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        if (is_busy) {
            CircularProgressIndicator(
                color = colors.accent_blue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            AsterGhostButton(label = action_label, onClick = on_action)
        }
    }
}
