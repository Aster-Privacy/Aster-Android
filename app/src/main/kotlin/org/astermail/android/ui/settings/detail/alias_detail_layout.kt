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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.design.components.skeleton_reveal

data class AliasAddressParts(val local: String, val domain: String)

internal fun split_alias_address(address: String): AliasAddressParts {
    val trimmed = address.trim()
    val at = trimmed.lastIndexOf('@')
    if (at <= 0 || at == trimmed.length - 1) return AliasAddressParts(trimmed, "")
    return AliasAddressParts(trimmed.substring(0, at), trimmed.substring(at))
}

internal const val alias_domain_max_fraction = 0.6f

@Composable
internal fun alias_address_text(
    address: String,
    modifier: Modifier = Modifier,
    color: Color = AsterMaterial.colors.text_primary,
    font_size: TextUnit = 15.sp,
    font_weight: FontWeight = FontWeight.Medium,
    test_tag: String? = null,
) {
    val parts = remember(address) { split_alias_address(address) }
    BoxWithConstraints(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = address
                text = AnnotatedString(address)
            }
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
    ) {
        val domain_max = maxWidth * alias_domain_max_fraction
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = parts.local,
                color = color,
                fontSize = font_size,
                fontWeight = font_weight,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (parts.domain.isNotEmpty()) {
                Text(
                    text = parts.domain,
                    color = color,
                    fontSize = font_size,
                    fontWeight = font_weight,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier.widthIn(max = domain_max),
                )
            }
        }
    }
}

@Composable
internal fun alias_card(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    header_action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AsterCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = settings_group_inset),
        ) {
            if (title != null) {
                alias_card_heading(title = title, description = description, action = header_action)
            }
            content()
        }
    }
}

@Composable
internal fun alias_card_heading(
    title: String,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height)
            .padding(start = AsterSpacing.lg, end = if (action != null) AsterSpacing.sm else AsterSpacing.lg)
            .padding(vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (description != null) {
                Text(
                    text = description,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(AsterSpacing.sm))
            action()
        }
    }
}

@Composable
internal fun alias_card_body(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        content = content,
    )
}

@Composable
internal fun alias_card_hint(text: String, color: Color = AsterMaterial.colors.text_tertiary) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
    )
}

@Composable
internal fun alias_text_action(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AsterMaterial.colors.accent_blue,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = on_click)
            .padding(horizontal = AsterSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) color else AsterMaterial.colors.text_tertiary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun alias_select_row(
    title: String,
    value: String,
    test_tag: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    menu: @Composable (expanded: Boolean, dismiss: () -> Unit) -> Unit,
) {
    val colors = AsterMaterial.colors
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = settings_row_min_height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = settings_row_min_height)
                    .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                    .padding(start = AsterSpacing.lg, end = if (trailing != null) AsterSpacing.xs else AsterSpacing.lg)
                    .padding(vertical = AsterSpacing.sm)
                    .testTag(test_tag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (enabled) colors.text_primary else colors.text_tertiary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(AsterSpacing.md))
                Text(
                    text = value,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 148.dp),
                )
                Spacer(Modifier.width(AsterSpacing.xs))
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.text_tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            menu(open) { open = false }
        }
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(AsterSpacing.xs))
        }
    }
}

@Composable
internal fun alias_detail_sections_skeleton(cards: Int = 3) {
    val state = shimmer_state()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .testTag("alias_detail_skeleton"),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
    ) {
        repeat(cards) { index ->
            Box(modifier = Modifier.skeleton_reveal()) {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = settings_group_inset)) {
                        val rows = if (index == 0) 3 else 2
                        repeat(rows) { row ->
                            skeleton_list_row(
                                state = state,
                                title_fraction = if (row % 2 == 0) 0.5f else 0.38f,
                                subtitle_fraction = if (row % 2 == 0) 0.3f else 0.44f,
                                trailing_width = if (row == 0) 44.dp else 0.dp,
                            )
                            if (row < rows - 1) settings_row_gap()
                        }
                    }
                }
            }
        }
    }
}
