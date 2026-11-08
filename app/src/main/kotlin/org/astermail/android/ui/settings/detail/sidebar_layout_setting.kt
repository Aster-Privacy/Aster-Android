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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.Refresh
import org.astermail.android.R
import org.astermail.android.api.preferences.SIDEBAR_HIDEABLE_MAIL_ITEM_IDS
import org.astermail.android.api.preferences.SIDEBAR_SECTION_ALIASES
import org.astermail.android.api.preferences.SIDEBAR_SECTION_FOLDERS
import org.astermail.android.api.preferences.SIDEBAR_SECTION_LABELS
import org.astermail.android.api.preferences.SIDEBAR_SECTION_MAIL
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.preferences.can_move_sidebar_section
import org.astermail.android.api.preferences.is_default_sidebar_layout
import org.astermail.android.api.preferences.is_sidebar_item_hidden
import org.astermail.android.api.preferences.resolve_sidebar_section_order
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.ui.settings.settings_search_anchor

internal fun sidebar_section_label_res(id: String): Int = when (id) {
    SIDEBAR_SECTION_FOLDERS -> R.string.drawer_folders
    SIDEBAR_SECTION_LABELS -> R.string.drawer_labels
    SIDEBAR_SECTION_ALIASES -> R.string.drawer_aliases
    else -> R.string.mail
}

internal fun sidebar_mail_item_label_res(id: String): Int = when (id) {
    "sent" -> R.string.folder_sent
    "scheduled" -> R.string.folder_scheduled
    "snoozed" -> R.string.folder_snoozed
    "drafts" -> R.string.folder_drafts
    "contacts" -> R.string.folder_contacts
    "starred" -> R.string.folder_starred
    "all" -> R.string.folder_all_mail
    "archive" -> R.string.folder_archive
    "spam" -> R.string.folder_spam
    "trash" -> R.string.folder_trash
    else -> R.string.folder_subscriptions
}

@Composable
internal fun sidebar_layout_settings(
    preferences: UserPreferences,
    on_move: (id: String, direction: Int) -> Unit,
    on_set_hidden: (id: String, hidden: Boolean) -> Unit,
    on_reset: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val order = preferences.sidebar_section_order
    val hidden = preferences.sidebar_hidden_items
    val sections = resolve_sidebar_section_order(order)

    section_label(stringResource(R.string.sidebar_layout))
    AsterCard(modifier = Modifier.fillMaxWidth().testTag("sidebar_layout_card")) {
        sections.forEachIndexed { index, id ->
            val name = stringResource(sidebar_section_label_res(id))
            val is_mail = id == SIDEBAR_SECTION_MAIL
            sidebar_section_row(
                id = id,
                name = name,
                subtitle = if (is_mail) stringResource(R.string.sidebar_mail_section_subtitle) else null,
                visible = if (is_mail) null else !is_sidebar_item_hidden(hidden, id),
                can_move_up = can_move_sidebar_section(order, id, -1),
                can_move_down = can_move_sidebar_section(order, id, 1),
                on_move = { direction -> on_move(id, direction) },
                on_visible_change = { visible -> on_set_hidden(id, !visible) },
            )
            if (index < sections.lastIndex) settings_row_gap(modifier = Modifier)
        }
    }
    v_gap(AsterSpacing.xs)
    Text(
        text = stringResource(R.string.sidebar_layout_subtitle),
        color = colors.text_tertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = AsterSpacing.xs),
    )

    v_gap(AsterSpacing.lg)

    section_label(stringResource(R.string.sidebar_mail_items))
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        SIDEBAR_HIDEABLE_MAIL_ITEM_IDS.forEachIndexed { index, id ->
            settings_toggle_row(
                title = stringResource(sidebar_mail_item_label_res(id)),
                checked = !is_sidebar_item_hidden(hidden, id),
                test_tag = "sidebar_item_$id",
                on_change = { visible -> on_set_hidden(id, !visible) },
            )
            if (index < SIDEBAR_HIDEABLE_MAIL_ITEM_IDS.lastIndex) settings_row_gap(modifier = Modifier)
        }
    }
    v_gap(AsterSpacing.xs)
    Text(
        text = stringResource(R.string.sidebar_mail_items_subtitle),
        color = colors.text_tertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = AsterSpacing.xs),
    )

    v_gap(AsterSpacing.md)
    AsterCard(modifier = Modifier.fillMaxWidth()) {
        sidebar_reset_row(
            enabled = !is_default_sidebar_layout(order, hidden),
            on_click = on_reset,
        )
    }
}

@Composable
private fun sidebar_section_row(
    id: String,
    name: String,
    subtitle: String?,
    visible: Boolean?,
    can_move_up: Boolean,
    can_move_down: Boolean,
    on_move: (Int) -> Unit,
    on_visible_change: (Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    val toggle_modifier = if (visible != null) {
        Modifier.toggleable(
            value = visible,
            role = Role.Switch,
            indication = androidx.compose.material3.ripple(),
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onValueChange = on_visible_change,
        )
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .settings_search_anchor(name, subtitle)
            .fillMaxWidth()
            .then(toggle_modifier)
            .testTag("sidebar_section_$id")
            .heightIn(min = settings_row_min_height)
            .padding(start = AsterSpacing.lg, end = AsterSpacing.lg, top = AsterSpacing.xs, bottom = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.text_primary,
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
        sidebar_move_button(
            icon = TablerIcons.ChevronUp,
            description = stringResource(R.string.sidebar_move_section_up, name),
            enabled = can_move_up,
            test_tag = "sidebar_move_up_$id",
            on_click = { on_move(-1) },
        )
        sidebar_move_button(
            icon = TablerIcons.ChevronDown,
            description = stringResource(R.string.sidebar_move_section_down, name),
            enabled = can_move_down,
            test_tag = "sidebar_move_down_$id",
            on_click = { on_move(1) },
        )
        if (visible != null) {
            Spacer(Modifier.width(AsterSpacing.sm))
            AsterSwitch(checked = visible, onCheckedChange = null)
        }
    }
}

@Composable
private fun sidebar_move_button(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    test_tag: String,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    IconButton(
        onClick = on_click,
        enabled = enabled,
        modifier = Modifier.size(40.dp).testTag(test_tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) colors.text_secondary else colors.text_muted.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun sidebar_reset_row(enabled: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val tint = if (enabled) colors.accent_blue else colors.text_muted
    val label = stringResource(R.string.reset_sidebar_layout)
    Row(
        modifier = Modifier
            .settings_search_anchor(label)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = on_click)
            .testTag("sidebar_layout_reset")
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = TablerIcons.Refresh,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}
