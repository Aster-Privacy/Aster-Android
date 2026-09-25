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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.At
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.Database
import compose.icons.tablericons.Lifebuoy
import compose.icons.tablericons.ListCheck
import compose.icons.tablericons.Paperclip
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Shield
import compose.icons.tablericons.Users
import compose.icons.tablericons.World
import org.astermail.android.R
import org.astermail.android.billing.plan_comparison_feed
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard

internal enum class billing_hero_status { free, active, ending, attention }

internal data class billing_hero_action(
    val label: String,
    val icon: ImageVector,
    val on_click: () -> Unit,
    val subtitle: String? = null,
    val color: Color? = null,
    val loading: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
internal fun billing_hero_card(
    plan_name: String,
    status: billing_hero_status,
    status_text: String,
    member_since: String?,
    thanks_title: String?,
    thanks_body: String?,
    price_text: String?,
    interval_short: String?,
    schedule_text: String?,
    discount: String?,
    storage_used_bytes: Long,
    storage_limit_bytes: Long,
    storage_over_limit: Boolean,
    storage_action_label: String?,
    on_storage_action: (() -> Unit)?,
    usage: List<billing_usage_item>,
    on_usage_upgrade: (() -> Unit)?,
    keep_title: String?,
    keep_body: String?,
    keep_action: String?,
    keep_loading: Boolean,
    on_keep: (() -> Unit)?,
    primary_action: String?,
    primary_note: String?,
    on_primary: (() -> Unit)?,
    actions: List<billing_hero_action>,
    danger_action: billing_hero_action?,
) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AsterSpacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                billing_plan_title(plan_name = plan_name, modifier = Modifier.weight(1f, fill = false))
                if (status != billing_hero_status.free) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = status_text,
                        color = when (status) {
                            billing_hero_status.active -> colors.success
                            billing_hero_status.ending -> colors.warning
                            else -> colors.danger
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (member_since != null) {
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = member_since,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
            if (thanks_title != null && thanks_body != null) {
                Spacer(Modifier.height(AsterSpacing.lg))
                Text(
                    text = thanks_title,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = thanks_body,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            if (price_text != null || schedule_text != null) {
                Spacer(Modifier.height(AsterSpacing.lg))
                Row(verticalAlignment = Alignment.Bottom) {
                    if (price_text != null) {
                        Text(
                            text = price_text,
                            color = colors.text_primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp,
                            maxLines = 1,
                        )
                        if (interval_short != null) {
                            Text(
                                text = interval_short,
                                color = colors.text_secondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                        Spacer(Modifier.width(AsterSpacing.md))
                    }
                    if (schedule_text != null) {
                        Text(
                            text = schedule_text,
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 3.dp),
                        )
                    }
                }
                if (discount != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = discount,
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (storage_limit_bytes > 0) {
                val storage_fraction = storage_used_bytes.toFloat() / storage_limit_bytes.toFloat()
                val storage_status = when {
                    storage_over_limit || storage_fraction >= 1f -> billing_meter_status.full
                    storage_fraction >= 0.8f -> billing_meter_status.near
                    else -> billing_meter_status.ok
                }
                Spacer(Modifier.height(AsterSpacing.lg))
                billing_meter(
                    label = stringResource(R.string.storage),
                    value_text = stringResource(
                        R.string.storage_used_format,
                        format_storage_short(storage_used_bytes),
                        format_storage_short(storage_limit_bytes),
                    ),
                    fraction = storage_fraction,
                    status = storage_status,
                    status_text = stringResource(
                        when (storage_status) {
                            billing_meter_status.full -> R.string.billing_status_action_required
                            billing_meter_status.near -> R.string.billing_status_almost_full
                            else -> R.string.billing_status_ok
                        },
                    ),
                    trailing_action = if (storage_action_label == null || on_storage_action == null) {
                        null
                    } else {
                        { billing_action_text(label = storage_action_label, on_click = on_storage_action) }
                    },
                )
            }
            usage.forEach { item ->
                Spacer(Modifier.height(AsterSpacing.lg))
                billing_usage_meter(item = item, on_upgrade = on_usage_upgrade)
            }
        }
        if (keep_title != null && keep_action != null && on_keep != null) {
            settings_row_gap()
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = keep_title,
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (keep_body != null) {
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = keep_body,
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterButton(
                    label = keep_action,
                    onClick = on_keep,
                    enabled = !keep_loading,
                    is_loading = keep_loading,
                )
            }
        }
        if (primary_action != null && on_primary != null) {
            settings_row_gap()
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                AsterButton(label = primary_action, onClick = on_primary)
                if (primary_note != null) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = primary_note,
                        color = colors.text_tertiary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        actions.forEach { action ->
            settings_row_gap()
            billing_hero_row(action)
        }
        if (danger_action != null) {
            settings_row_gap()
            billing_hero_row(danger_action)
        }
    }
}

@Composable
private fun billing_hero_row(action: billing_hero_action) {
    val colors = AsterMaterial.colors
    val tint = action.color ?: colors.text_primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height)
            .clickable(enabled = action.enabled && !action.loading, role = Role.Button, onClick = action.on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = if (action.color != null) tint else colors.text_secondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                color = if (action.enabled) tint else colors.text_muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (action.subtitle != null) {
                Text(
                    text = action.subtitle,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        if (action.loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = colors.accent_blue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun billing_yearly_nudge_card(
    monthly_equivalent: String,
    yearly_total: String,
    save_text: String,
    on_switch: () -> Unit,
    enabled: Boolean,
) {
    val colors = AsterMaterial.colors
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        detail_row(
            title = stringResource(R.string.billing_yearly_nudge_title),
            subtitle = stringResource(R.string.billing_yearly_nudge_body, monthly_equivalent, yearly_total),
            icon = TablerIcons.Calendar,
            on_click = if (enabled) on_switch else null,
            trailing = {
                Text(
                    text = save_text,
                    color = colors.success,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Icon(
                    imageVector = TablerIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

internal data class billing_advantage_row(
    val label: String,
    val free_value: String?,
    val paid_value: String?,
    val icon: ImageVector = TablerIcons.Check,
)

internal data class billing_usage_item(
    val label: String,
    val current: Int,
    val limit: Int?,
    val loaded: Boolean,
)

@Composable
internal fun billing_usage_meter(item: billing_usage_item, on_upgrade: (() -> Unit)?) {
    val limit = item.limit
    if (!item.loaded) {
        billing_meter(
            label = item.label,
            value_text = stringResource(R.string.billing_usage_in_use, item.current),
            fraction = 0f,
            status = billing_meter_status.none,
            status_text = null,
        )
        return
    }
    if (limit == null || limit <= 0) {
        billing_meter(
            label = item.label,
            value_text = stringResource(R.string.billing_usage_in_use, item.current),
            fraction = 0f,
            status = billing_meter_status.ok,
            status_text = stringResource(R.string.usage_unlimited),
        )
        return
    }
    val fraction = item.current.toFloat() / limit.toFloat()
    val status = when {
        item.current >= limit -> billing_meter_status.full
        fraction >= 0.8f -> billing_meter_status.near
        else -> billing_meter_status.ok
    }
    billing_meter(
        label = item.label,
        value_text = stringResource(R.string.billing_usage_of, item.current, limit),
        fraction = fraction,
        status = status,
        status_text = stringResource(
            when (status) {
                billing_meter_status.full -> R.string.billing_status_limit_reached
                billing_meter_status.near -> R.string.billing_status_near_limit
                else -> R.string.billing_status_ok
            },
        ),
        trailing_action = if (on_upgrade == null || status == billing_meter_status.ok) {
            null
        } else {
            { billing_action_text(label = stringResource(R.string.billing_usage_upgrade_hint), on_click = on_upgrade) }
        },
    )
}

@Composable
internal fun billing_advantages_card(
    title: String,
    rows: List<billing_advantage_row>,
    more_label: String?,
    on_more: (() -> Unit)?,
    show_free_values: Boolean = false,
) {
    val colors = AsterMaterial.colors
    section_label(title)
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        rows.filter { it.paid_value != null && it.paid_value != billing_included_marker }.forEachIndexed { index, row ->
            if (index > 0) settings_row_gap()
            detail_row(
                title = row.label,
                icon = row.icon,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val free_value = row.free_value
                        if (show_free_values && free_value != null && free_value != row.paid_value) {
                            Text(
                                text = free_value,
                                color = colors.text_muted,
                                fontSize = 13.sp,
                                textDecoration = TextDecoration.LineThrough,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(AsterSpacing.sm))
                        }
                        Text(
                            text = row.paid_value.orEmpty(),
                            color = colors.accent_blue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                },
            )
        }
        if (more_label != null && on_more != null) {
            settings_row_gap()
            detail_row(
                title = more_label,
                icon = TablerIcons.ListCheck,
                on_click = on_more,
                trailing = {
                    Icon(
                        imageVector = TablerIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

private const val billing_wordmark_id = "aster_wordmark"
private const val billing_wordmark_aspect = 4f
private const val billing_title_size = 24f
private const val billing_wordmark_cap_ratio = 0.72f

@Composable
internal fun billing_plan_title(plan_name: String, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val cap_height = billing_title_size * billing_wordmark_cap_ratio
    val wordmark = InlineTextContent(
        Placeholder(
            width = (cap_height * billing_wordmark_aspect).sp,
            height = cap_height.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
        ),
    ) {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Text(
        text = buildAnnotatedString {
            appendInlineContent(billing_wordmark_id, "Aster")
            append(" ")
            append(plan_name)
        },
        inlineContent = mapOf(billing_wordmark_id to wordmark),
        color = colors.text_primary,
        fontSize = billing_title_size.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

internal const val billing_included_marker = "✓"

@Composable
internal fun billing_expander_row(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    expanded: Boolean,
    on_toggle: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height)
            .clickable(role = Role.Button, onClick = on_toggle)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Icon(
            imageVector = if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

internal val billing_icon_payment: ImageVector = TablerIcons.CreditCard
internal val billing_icon_family: ImageVector = TablerIcons.Users
internal val billing_icon_renew: ImageVector = TablerIcons.Refresh
internal val billing_icon_storage: ImageVector = TablerIcons.Database
internal val billing_icon_yearly: ImageVector = TablerIcons.Calendar

private val billing_advantage_highlights = listOf(
    "storage" to TablerIcons.Database,
    "aliases" to TablerIcons.At,
    "domains" to TablerIcons.World,
    "attachment_size" to TablerIcons.Paperclip,
    "external_accounts" to TablerIcons.Refresh,
)

internal fun billing_advantage_rows_from_feed(plan_code: String, feed: plan_comparison_feed): List<billing_advantage_row> =
    billing_advantage_highlights.mapNotNull { (id, icon) ->
        val row = feed.row(id) ?: return@mapNotNull null
        val paid = row.value_for(plan_code).text ?: return@mapNotNull null
        billing_advantage_row(
            label = row.label,
            free_value = row.value_for("free").text,
            paid_value = paid,
            icon = icon,
        )
    }

@Composable
internal fun billing_advantage_rows(plan_code: String, feed: plan_comparison_feed?): List<billing_advantage_row> {
    val from_feed = feed?.let { billing_advantage_rows_from_feed(plan_code, it) }.orEmpty()
    if (from_feed.isNotEmpty()) return from_feed
    return billing_advantage_fallback_rows(plan_code)
}

@Composable
private fun billing_advantage_fallback_rows(plan_code: String): List<billing_advantage_row> {
    val unlimited = stringResource(R.string.usage_unlimited)
    val storage = when (plan_code) {
        "star" -> "50 GB"
        "supernova" -> "5 TB"
        else -> "500 GB"
    }
    val aliases = if (plan_code == "star") "15" else unlimited
    val domains = when (plan_code) {
        "star" -> "5"
        "supernova" -> unlimited
        else -> "30"
    }
    val attachments = when (plan_code) {
        "star" -> "50 MB"
        "supernova" -> "250 MB"
        else -> "100 MB"
    }
    return listOf(
        billing_advantage_row(stringResource(R.string.special_offer_compare_storage), "10 GB", storage, TablerIcons.Database),
        billing_advantage_row(stringResource(R.string.special_offer_compare_aliases), "5", aliases, TablerIcons.At),
        billing_advantage_row(stringResource(R.string.special_offer_compare_domains), "1", domains, TablerIcons.World),
        billing_advantage_row(stringResource(R.string.special_offer_compare_attachments), "25 MB", attachments, TablerIcons.Paperclip),
        billing_advantage_row(stringResource(R.string.settings_plan_bullet_tracker_protection), null, billing_included_marker, TablerIcons.Shield),
        billing_advantage_row(stringResource(R.string.billing_compare_external_accounts), null, billing_included_marker, TablerIcons.Refresh),
        billing_advantage_row(stringResource(R.string.settings_plan_bullet_priority_support), null, billing_included_marker, TablerIcons.Lifebuoy),
    )
}
