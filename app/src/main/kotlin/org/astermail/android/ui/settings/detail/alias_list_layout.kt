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

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.WifiOff
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard

data class AliasListGroups<T>(val pinned: List<T>, val others: List<T>)

internal fun <T> group_aliases(items: List<T>, pinned: (T) -> Boolean): AliasListGroups<T> {
    val (pinned_items, other_items) = items.partition(pinned)
    return AliasListGroups(pinned_items, other_items)
}

enum class AliasUsageLevel { Unlimited, Normal, Near, Full }

internal fun alias_usage_level(used: Int, limit: Int?): AliasUsageLevel = when {
    limit == null || limit <= 0 -> AliasUsageLevel.Unlimited
    used >= limit -> AliasUsageLevel.Full
    used * 100 >= limit * 80 -> AliasUsageLevel.Near
    else -> AliasUsageLevel.Normal
}

internal fun alias_usage_fraction(used: Int, limit: Int?): Float {
    if (limit == null || limit <= 0) return 0f
    return (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
}

@Composable
internal fun alias_group_header(title: String, count: Int, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.xs, end = AsterSpacing.xs, top = AsterSpacing.md, bottom = AsterSpacing.sm)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = count.toString(),
            color = colors.text_tertiary,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun alias_plan_usage_card(
    used: Int,
    limit: Int?,
    on_create: () -> Unit,
    on_upgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val level = alias_usage_level(used, limit)
    val fraction = alias_usage_fraction(used, limit)
    val fill = when (level) {
        AliasUsageLevel.Full -> colors.danger
        AliasUsageLevel.Near -> colors.warning
        else -> colors.accent_blue
    }
    AsterCard(modifier = modifier.fillMaxWidth().testTag("alias_plan_usage")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AsterSpacing.lg),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.aliases),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (limit != null && limit > 0) {
                        stringResource(R.string.storage_used_of_total, used.toString(), limit.toString())
                    } else {
                        androidx.compose.ui.res.pluralStringResource(R.plurals.aliases_count, used, used)
                    },
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("alias_plan_usage_value"),
                )
            }
            if (level != AliasUsageLevel.Unlimited) {
                Spacer(Modifier.height(AsterSpacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.bg_tertiary),
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(fill),
                        )
                    }
                }
            }
            if (level == AliasUsageLevel.Near || level == AliasUsageLevel.Full) {
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = if (level == AliasUsageLevel.Full) {
                        stringResource(R.string.alias_usage_full)
                    } else {
                        stringResource(R.string.alias_usage_near)
                    },
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(AsterSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterButton(
                    label = stringResource(R.string.alias_create_action),
                    onClick = on_create,
                    enabled = level != AliasUsageLevel.Full,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("alias_create_button"),
                )
                if (level == AliasUsageLevel.Near || level == AliasUsageLevel.Full) {
                    alias_text_action(
                        label = stringResource(R.string.upgrade),
                        on_click = on_upgrade,
                        modifier = Modifier.testTag("alias_usage_upgrade"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun alias_offline_banner(modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bg_tertiary)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md)
            .semantics(mergeDescendants = true) {}
            .testTag("alias_offline_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.WifiOff,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = stringResource(R.string.alias_offline_banner),
            color = colors.text_secondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun remember_is_online(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var online by remember(manager) { mutableStateOf(manager?.let { current_network_online(it) } ?: true) }
    DisposableEffect(manager) {
        val cm = manager
        if (cm == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    online = true
                }

                override fun onLost(network: Network) {
                    online = current_network_online(cm)
                }
            }
            val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
            onDispose {
                if (registered) runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }
    return online
}

private fun current_network_online(manager: ConnectivityManager): Boolean = runCatching {
    val network = manager.activeNetwork ?: return@runCatching false
    val caps = manager.getNetworkCapabilities(network) ?: return@runCatching false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(true)
