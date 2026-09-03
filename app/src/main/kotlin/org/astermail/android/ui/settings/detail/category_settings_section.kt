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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Bell
import compose.icons.tablericons.BellOff
import compose.icons.tablericons.Briefcase
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.Discount
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Gift
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Home
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.MessageDots
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Plane
import compose.icons.tablericons.Plus
import compose.icons.tablericons.School
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Speakerphone
import compose.icons.tablericons.Star
import compose.icons.tablericons.Tag
import compose.icons.tablericons.Trash
import compose.icons.tablericons.Users
import compose.icons.tablericons.Wand
import compose.icons.tablericons.World
import org.astermail.android.R
import org.astermail.android.api.preferences.CustomCategoryRule
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogDestructiveButton
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.mail.BUILTIN_CATEGORIES
import org.astermail.android.mail.CUSTOM_CATEGORY_ICON_CHOICES
import org.astermail.android.mail.MAX_CUSTOM_CATEGORY_NAME
import org.astermail.android.mail.MAX_CUSTOM_CATEGORY_RULES
import org.astermail.android.mail.allowed_custom_categories
import org.astermail.android.mail.is_valid_match_domain
import org.astermail.android.mail.is_valid_match_keyword
import org.astermail.android.mail.sanitize_custom_category

private val category_setting_icons: Map<String, ImageVector> = mapOf(
    "inbox" to TablerIcons.Inbox,
    "tag" to TablerIcons.Discount,
    "users" to TablerIcons.Users,
    "bell" to TablerIcons.Bell,
    "chat" to TablerIcons.MessageDots,
    "credit_card" to TablerIcons.CreditCard,
    "plane" to TablerIcons.Plane,
    "shopping_bag" to TablerIcons.ShoppingCart,
    "star" to TablerIcons.Star,
    "heart" to TablerIcons.Heart,
    "briefcase" to TablerIcons.Briefcase,
    "home" to TablerIcons.Home,
    "globe" to TablerIcons.World,
    "academic_cap" to TablerIcons.School,
    "megaphone" to TablerIcons.Speakerphone,
    "gift" to TablerIcons.Gift,
    "folder" to TablerIcons.Folder,
    "sparkles" to TablerIcons.Wand,
)

private fun category_setting_icon(icon: String): ImageVector =
    category_setting_icons[icon] ?: TablerIcons.Tag

private fun builtin_info_res(id: String): Int = when (id) {
    "promotions" -> R.string.category_info_promotions
    "social" -> R.string.category_info_social
    "updates" -> R.string.category_info_updates
    "forums" -> R.string.category_info_forums
    "finance" -> R.string.category_info_finance
    "travel" -> R.string.category_info_travel
    "shopping" -> R.string.category_info_shopping
    else -> R.string.category_info_primary
}

private fun split_terms(raw: String): List<String> =
    raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun category_mute_button(
    label: String,
    is_enabled: Boolean,
    is_muted: Boolean,
    on_toggle: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val description = stringResource(
        if (is_muted) R.string.unmute_category_notifications else R.string.mute_category_notifications,
        label,
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .then(if (is_enabled) Modifier.clickable(onClick = on_toggle) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (is_muted) TablerIcons.BellOff else TablerIcons.Bell,
            contentDescription = description,
            tint = when {
                !is_enabled -> colors.text_tertiary.copy(alpha = 0.4f)
                is_muted -> colors.text_primary
                else -> colors.text_tertiary
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun category_settings_section(
    enabled_categories: List<String>,
    custom_categories: List<CustomCategoryRule>,
    custom_category_limit: Int,
    muted_categories: List<String>,
    on_enabled_change: (List<String>) -> Unit,
    on_custom_change: (List<CustomCategoryRule>) -> Unit,
    on_toggle_muted: (String) -> Unit,
    on_upgrade: () -> Unit,
) {
    val colors = AsterMaterial.colors
    var editing by remember { mutableStateOf<CustomCategoryRule?>(null) }
    var editor_open by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CustomCategoryRule?>(null) }

    val enabled_ids = enabled_categories.toSet()
    val muted_ids = muted_categories.toSet()
    val is_unlimited = custom_category_limit < 0
    val at_limit = !is_unlimited && custom_categories.size >= custom_category_limit
    val can_add_custom = is_unlimited || custom_category_limit > 0
    val permitted_ids = allowed_custom_categories(custom_categories, custom_category_limit)
        .map { it.id }
        .toSet()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.categories_title),
            color = colors.text_primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = AsterSpacing.xs, bottom = 2.dp),
        )
        Text(
            text = stringResource(R.string.categories_description),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = AsterSpacing.xs, bottom = AsterSpacing.sm),
        )
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            val removable = BUILTIN_CATEGORIES.filter { it.removable }
            removable.forEachIndexed { index, cat ->
                val is_enabled = enabled_ids.contains(cat.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = if (is_enabled) {
                                enabled_categories.filter { it != cat.id }
                            } else {
                                enabled_categories + cat.id
                            }
                            on_enabled_change(next)
                        }
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = category_setting_icon(cat.icon),
                        contentDescription = null,
                        tint = colors.text_tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(cat.label_res),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(builtin_info_res(cat.id)),
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                    category_mute_button(
                        label = stringResource(cat.label_res),
                        is_enabled = is_enabled,
                        is_muted = muted_ids.contains(cat.id),
                        on_toggle = { on_toggle_muted(cat.id) },
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    AsterSwitch(checked = is_enabled, onCheckedChange = null)
                }
                if (index < removable.size - 1) AsterDivider(modifier = Modifier)
            }
        }

        Spacer(Modifier.height(AsterSpacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.xs, bottom = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.custom_categories_title),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.custom_categories_tutorial),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            Row(
                modifier = Modifier
                    .border(1.dp, colors.border_primary, SquircleShape(14.dp))
                    .clickable {
                        if (!can_add_custom || at_limit ||
                            custom_categories.size >= MAX_CUSTOM_CATEGORY_RULES
                        ) {
                            on_upgrade()
                        } else {
                            editing = null
                            editor_open = true
                        }
                    }
                    .padding(horizontal = AsterSpacing.md, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = null,
                    tint = colors.text_primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add_category),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (!can_add_custom) {
            Text(
                text = stringResource(R.string.custom_categories_locked),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = AsterSpacing.xs, top = 4.dp),
            )
        } else if (custom_categories.isEmpty()) {
            Text(
                text = stringResource(R.string.no_custom_categories),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = AsterSpacing.xs, top = 4.dp),
            )
        } else {
            if (at_limit) {
                Text(
                    text = stringResource(R.string.custom_categories_limit_reached),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = AsterSpacing.xs, bottom = 4.dp),
                )
            }
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                custom_categories.forEachIndexed { index, rule ->
                    val is_locked = !permitted_ids.contains(rule.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = category_setting_icon(rule.icon),
                            contentDescription = null,
                            tint = colors.text_tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AsterSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (is_locked) {
                                    rule.name + " " + stringResource(R.string.custom_category_locked_badge)
                                } else {
                                    rule.name
                                },
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            val terms = (rule.match_domains + rule.match_keywords).take(4)
                            if (terms.isNotEmpty()) {
                                Text(
                                    text = terms.joinToString(", "),
                                    color = colors.text_tertiary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        category_mute_button(
                            label = rule.name,
                            is_enabled = rule.enabled && !is_locked,
                            is_muted = muted_ids.contains(rule.id),
                            on_toggle = { on_toggle_muted(rule.id) },
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    editing = rule
                                    editor_open = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TablerIcons.Pencil,
                                contentDescription = stringResource(R.string.edit),
                                tint = colors.text_tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable { deleting = rule },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TablerIcons.Trash,
                                contentDescription = stringResource(R.string.delete),
                                tint = colors.danger,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        AsterSwitch(
                            checked = rule.enabled && !is_locked,
                            onCheckedChange = {
                                if (is_locked) {
                                    on_upgrade()
                                } else {
                                    on_custom_change(
                                        custom_categories.map { entry ->
                                            if (entry.id == rule.id) {
                                                entry.copy(enabled = !entry.enabled)
                                            } else {
                                                entry
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                    if (index < custom_categories.size - 1) AsterDivider(modifier = Modifier)
                }
            }
        }

        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.category_tutorial_text),
            color = colors.text_tertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = AsterSpacing.xs),
        )
    }

    if (editor_open) {
        custom_category_editor(
            existing = editing,
            on_dismiss = { editor_open = false },
            on_save = { saved ->
                val exists = custom_categories.any { it.id == saved.id }
                val next = if (exists) {
                    custom_categories.map { if (it.id == saved.id) saved else it }
                } else {
                    custom_categories + saved
                }
                on_custom_change(next.take(MAX_CUSTOM_CATEGORY_RULES))
                editor_open = false
            },
        )
    }

    val pending_delete = deleting
    if (pending_delete != null) {
        AsterDialog(
            on_dismiss = { deleting = null },
            title = stringResource(R.string.delete_category_title),
            message = stringResource(
                R.string.delete_category_description,
                pending_delete.name,
            ),
            footer = {
                AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { deleting = null },
                )
                AsterDialogDestructiveButton(
                    label = stringResource(R.string.delete),
                    onClick = {
                        on_custom_change(custom_categories.filter { it.id != pending_delete.id })
                        deleting = null
                    },
                )
            },
        )
    }
}

@Composable
private fun custom_category_editor(
    existing: CustomCategoryRule?,
    on_dismiss: () -> Unit,
    on_save: (CustomCategoryRule) -> Unit,
) {
    val colors = AsterMaterial.colors
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "tag") }
    var domains_text by remember {
        mutableStateOf(existing?.match_domains?.joinToString(", ") ?: "")
    }
    var keywords_text by remember {
        mutableStateOf(existing?.match_keywords?.joinToString(", ") ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    val name_required = stringResource(R.string.category_name_required)
    val rule_required = stringResource(R.string.category_rule_required)
    val domains_invalid_template = stringResource(R.string.category_domains_invalid, "%s")
    val keywords_invalid_template = stringResource(R.string.category_keywords_invalid, "%s")

    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(
            if (existing == null) R.string.new_custom_category else R.string.edit_custom_category,
        ),
        body = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AsterTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_CUSTOM_CATEGORY_NAME) name = it },
                    label = stringResource(R.string.category_name),
                    placeholder = stringResource(R.string.category_name_placeholder),
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.category_icon),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CUSTOM_CATEGORY_ICON_CHOICES.size) { index ->
                        val choice = CUSTOM_CATEGORY_ICON_CHOICES[index]
                        val selected = choice == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (selected) colors.accent_blue else colors.input_bg,
                                    CircleShape,
                                )
                                .clickable { icon = choice },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = category_setting_icon(choice),
                                contentDescription = null,
                                tint = if (selected) {
                                    androidx.compose.ui.graphics.Color.White
                                } else {
                                    colors.text_secondary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterTextField(
                    value = domains_text,
                    onValueChange = { domains_text = it },
                    label = stringResource(R.string.category_match_domains),
                    placeholder = stringResource(R.string.category_match_domains_placeholder),
                    helper_text = stringResource(R.string.category_match_domains_help),
                    singleLine = false,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                AsterTextField(
                    value = keywords_text,
                    onValueChange = { keywords_text = it },
                    label = stringResource(R.string.category_match_keywords),
                    placeholder = stringResource(R.string.category_match_keywords_placeholder),
                    helper_text = stringResource(R.string.category_match_keywords_help),
                    singleLine = false,
                )
                val message = error
                if (message != null) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(text = message, color = colors.danger, fontSize = 13.sp)
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.save),
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = name_required
                        return@AsterDialogPrimaryButton
                    }
                    val domains = split_terms(domains_text)
                    val keywords = split_terms(keywords_text)
                    if (domains.isEmpty() && keywords.isEmpty()) {
                        error = rule_required
                        return@AsterDialogPrimaryButton
                    }
                    val bad_domains = domains.filter { !is_valid_match_domain(it) }
                    if (bad_domains.isNotEmpty()) {
                        error = domains_invalid_template.replace(
                            "%s",
                            bad_domains.joinToString(", "),
                        )
                        return@AsterDialogPrimaryButton
                    }
                    val bad_keywords = keywords.filter { !is_valid_match_keyword(it) }
                    if (bad_keywords.isNotEmpty()) {
                        error = keywords_invalid_template.replace(
                            "%s",
                            bad_keywords.joinToString(", "),
                        )
                        return@AsterDialogPrimaryButton
                    }
                    val sanitized = sanitize_custom_category(
                        CustomCategoryRule(
                            id = existing?.id ?: "",
                            name = trimmed,
                            icon = icon,
                            match_domains = domains,
                            match_keywords = keywords,
                            enabled = existing?.enabled ?: true,
                        ),
                    )
                    if (sanitized == null) {
                        error = name_required
                        return@AsterDialogPrimaryButton
                    }
                    on_save(sanitized)
                },
            )
        },
    )
}
