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

package org.astermail.android.ui.drawer

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.BuildConfig
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.ui.mail.SenderAvatar
import org.astermail.android.ui.mail.avatar_colors_for
import org.astermail.android.ui.mail.initial_for
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.parse_hex_color_safe
import androidx.compose.animation.core.tween
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.storage.StoredAccount

data class drawer_folder_item(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val count: Int = 0,
    val depth: Int = 0,
    val trail: List<Boolean> = emptyList(),
    val has_next: Boolean = false,
)

data class folder_parent_option(
    val token: String,
    val label: String,
    val depth: Int,
    val path_label: String,
)

data class drawer_label_item(
    val id: String,
    val label: String,
    val color: Color,
    val icon: String? = null,
)

fun resolve_label_icon(key: String?): ImageVector =
    key?.let { name -> label_icon_presets.firstOrNull { it.first == name }?.second }
        ?: TablerIcons.Tag

data class drawer_alias_item(
    val id: String,
    val address: String,
    val routing_token: String? = null,
)

private val default_folder_items = emptyList<drawer_folder_item>()

private val default_label_items = emptyList<drawer_label_item>()

private val default_alias_items = emptyList<drawer_alias_item>()

private val label_palette = listOf(
    Color(0xFF3B82F6),
    Color(0xFF22C55E),
    Color(0xFFF59E0B),
    Color(0xFFA855F7),
    Color(0xFFEC4899),
    Color(0xFF14B8A6),
    Color(0xFFF97316),
    Color(0xFF6366F1),
)

private val label_color_presets = listOf(
    "#ef4444",
    "#f97316",
    "#f59e0b",
    "#eab308",
    "#84cc16",
    "#22c55e",
    "#10b981",
    "#14b8a6",
    "#06b6d4",
    "#0ea5e9",
    "#3b82f6",
    "#6366f1",
    "#8b5cf6",
    "#a855f7",
    "#d946ef",
    "#ec4899",
    "#f43f5e",
)

private const val default_label_color = "#3b82f6"

private val label_icon_presets: List<Pair<String, ImageVector>> = listOf(
    "clock" to TablerIcons.Clock,
    "archive" to TablerIcons.Archive,
    "trash" to TablerIcons.Trash,
    "send" to TablerIcons.Send,
    "draft" to TablerIcons.FileText,
    "star" to TablerIcons.Star,
    "flag" to TablerIcons.Flag,
    "bolt" to TablerIcons.Bolt,
    "shield" to TablerIcons.Shield,
    "warning" to TablerIcons.AlertTriangle,
    "check" to TablerIcons.CircleCheck,
    "tag" to TablerIcons.Tag,
    "folder" to TablerIcons.Folder,
    "envelope" to TablerIcons.Mail,
    "lock" to TablerIcons.Lock,
    "bell" to TablerIcons.Bell,
    "sparkles" to TablerIcons.Wand,
    "fire" to TablerIcons.Flame,
    "heart" to TablerIcons.Heart,
    "bookmark" to TablerIcons.Bookmark,
    "chat" to TablerIcons.MessageDots,
    "document" to TablerIcons.FileText,
    "currency" to TablerIcons.CurrencyDollar,
    "cart" to TablerIcons.ShoppingCart,
    "code" to TablerIcons.Code,
    "user" to TablerIcons.User,
    "building" to TablerIcons.Building,
    "globe" to TablerIcons.World,
    "info" to TablerIcons.InfoCircle,
    "eye-slash" to TablerIcons.EyeOff,
)

private val category_icons: Map<String, androidx.compose.ui.graphics.vector.ImageVector> = mapOf(
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

private fun category_icon(icon: String): androidx.compose.ui.graphics.vector.ImageVector =
    category_icons[icon] ?: TablerIcons.Tag

private fun parse_hex_color(hex: String): Color =
    parse_hex_color_safe(hex) ?: Color(0xFF3B82F6)

private const val sidebar_prefs_name = "aster_sidebar"
private const val key_more_collapsed = "more_collapsed"
private const val key_folders_collapsed = "folders_collapsed"
private const val key_labels_collapsed = "labels_collapsed"
private const val key_aliases_collapsed = "aliases_collapsed"

@Composable
fun DrawerContent(
    selected_id: String,
    on_select: (String) -> Unit,
    on_close: () -> Unit,
    on_navigate_folder: (String, String) -> Unit = { _, _ -> },
    on_navigate_label: (String, String) -> Unit = { _, _ -> },
    on_navigate_alias: (String, String, String?) -> Unit = { _, _, _ -> },
    inbox_unread: Int = 0,
    drafts_count: Int = 0,
    spam_count: Int = 0,
    trash_count: Int = 0,
    categories_enabled: Boolean = false,
    category_entries: List<org.astermail.android.mail.CategoryEntry> = emptyList(),
    category_unread: Map<String, Int> = emptyMap(),
    selected_category: String = "primary",
    on_select_category: (String) -> Unit = {},
    storage_used_fraction: Float = 0f,
    storage_label: String = "",
    user_email: String = "",
    api_folder_items: List<drawer_folder_item> = emptyList(),
    api_label_items: List<drawer_label_item> = emptyList(),
    api_alias_items: List<drawer_alias_item> = emptyList(),
    accounts: List<StoredAccount> = emptyList(),
    current_account_id: String? = null,
    can_add_account: Boolean = true,
    on_switch_account: (StoredAccount) -> Unit = {},
    on_add_account: () -> Unit = {},
    on_open_workspace_sheet: () -> Unit = {},
    on_create_label: (name: String, color: String, icon: String?) -> Unit = { _, _, _ -> },
    on_create_folder: (name: String, parent_token: String?) -> Unit = { _, _ -> },
    folder_parent_options: List<folder_parent_option> = emptyList(),
    on_logout: () -> Unit = {},
    initial_more_collapsed: Boolean = false,
    initial_folders_collapsed: Boolean = false,
    initial_labels_collapsed: Boolean = false,
    initial_aliases_collapsed: Boolean = false,
    preferences_loaded: Boolean = false,
    on_sidebar_toggle: (String, Boolean) -> Unit = { _, _ -> },
) {
    val colors = AsterMaterial.colors
    var show_workspace_sheet by remember { mutableStateOf(false) }
    var show_logout_confirm by remember { mutableStateOf(false) }
    val current_workspace = user_email

    val sidebar_prefs_context = LocalContext.current
    val sidebar_prefs = remember { sidebar_prefs_context.getSharedPreferences(sidebar_prefs_name, Context.MODE_PRIVATE) }

    var more_expanded by rememberSaveable {
        mutableStateOf(!sidebar_prefs.getBoolean(key_more_collapsed, false))
    }
    var folders_expanded by rememberSaveable {
        mutableStateOf(!sidebar_prefs.getBoolean(key_folders_collapsed, false))
    }
    var labels_expanded by rememberSaveable {
        mutableStateOf(!sidebar_prefs.getBoolean(key_labels_collapsed, false))
    }
    var aliases_expanded by rememberSaveable {
        mutableStateOf(!sidebar_prefs.getBoolean(key_aliases_collapsed, false))
    }
    var aliases_show_all by remember { mutableStateOf(false) }
    val aliases_collapsed_count = 5
    var prefs_synced by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(preferences_loaded) {
        if (preferences_loaded && !prefs_synced) {
            if (!sidebar_prefs.contains(key_more_collapsed)) more_expanded = !initial_more_collapsed
            if (!sidebar_prefs.contains(key_folders_collapsed)) folders_expanded = !initial_folders_collapsed
            if (!sidebar_prefs.contains(key_labels_collapsed)) labels_expanded = !initial_labels_collapsed
            if (!sidebar_prefs.contains(key_aliases_collapsed)) aliases_expanded = !initial_aliases_collapsed
            sidebar_prefs.edit()
                .putBoolean(key_more_collapsed, !more_expanded)
                .putBoolean(key_folders_collapsed, !folders_expanded)
                .putBoolean(key_labels_collapsed, !labels_expanded)
                .putBoolean(key_aliases_collapsed, !aliases_expanded)
                .apply()
            prefs_synced = true
        }
    }

    var show_create_folder by remember { mutableStateOf(false) }
    var show_create_label by remember { mutableStateOf(false) }

    val folder_items = api_folder_items.ifEmpty { default_folder_items }
    val label_items = api_label_items.ifEmpty { default_label_items }
    val alias_items = api_alias_items.ifEmpty { default_alias_items }

    val label_inbox = stringResource(R.string.folder_inbox)
    val label_sent = stringResource(R.string.folder_sent)
    val label_scheduled = stringResource(R.string.folder_scheduled)
    val label_snoozed = stringResource(R.string.folder_snoozed)
    val label_drafts = stringResource(R.string.folder_drafts)
    val label_starred = stringResource(R.string.folder_starred)
    val label_all_mail = stringResource(R.string.folder_all_mail)
    val label_archive = stringResource(R.string.folder_archive)
    val label_spam = stringResource(R.string.folder_spam)
    val label_trash = stringResource(R.string.folder_trash)
    val label_contacts = stringResource(R.string.folder_contacts)
    val label_subscriptions = stringResource(R.string.folder_subscriptions)

    val core_items = remember(categories_enabled, inbox_unread, drafts_count, spam_count, trash_count, label_inbox, label_sent, label_drafts, label_starred, label_archive, label_spam, label_trash) {
        listOfNotNull(
            if (categories_enabled) null else drawer_folder_item("inbox", label_inbox, TablerIcons.Inbox, inbox_unread),
            drawer_folder_item("sent", label_sent, TablerIcons.Send),
            drawer_folder_item("drafts", label_drafts, TablerIcons.FileText, drafts_count),
            drawer_folder_item("starred", label_starred, TablerIcons.Star),
            drawer_folder_item("archive", label_archive, TablerIcons.Archive),
            drawer_folder_item("spam", label_spam, TablerIcons.AlertTriangle, spam_count),
            drawer_folder_item("trash", label_trash, TablerIcons.Trash, trash_count),
        )
    }

    val more_secondary = remember(label_scheduled, label_snoozed, label_all_mail, label_subscriptions) {
        listOf(
            drawer_folder_item("scheduled", label_scheduled, TablerIcons.Clock),
            drawer_folder_item("snoozed", label_snoozed, TablerIcons.BellMinus),
            drawer_folder_item("all", label_all_mail, TablerIcons.Mail),
            drawer_folder_item("subscriptions", label_subscriptions, TablerIcons.News),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(colors.bg_primary)
            .navigationBarsPadding(),
    ) {
        val current_account = accounts.firstOrNull { it.id == current_account_id }
        workspace_header(
            current_address = current_workspace,
            account_email = current_account?.email ?: user_email,
            account_name = current_account?.display_name.orEmpty(),
            profile_picture = current_account?.profile_picture,
            on_click = {
                on_open_workspace_sheet()
                show_workspace_sheet = true
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(AsterSpacing.sm))

            if (categories_enabled) {
                category_entries.forEach { entry ->
                    drawer_row(
                        icon = category_icon(entry.icon),
                        label = entry.label,
                        count = category_unread[entry.id] ?: 0,
                        is_unread_count = true,
                        selected = selected_id == "inbox" && entry.id == selected_category,
                        on_click = {
                            on_select_category(entry.id)
                            on_close()
                        },
                    )
                }
            }

            core_items.forEach { item ->
                drawer_row(
                    icon = item.icon,
                    label = item.label,
                    count = item.count,
                    is_unread_count = item.id == "inbox",
                    selected = item.id == selected_id,
                    on_click = {
                        on_select(item.id)
                        on_close()
                    },
                )
            }

            collapsible_section_header(
                text = stringResource(R.string.drawer_more),
                expanded = more_expanded,
                on_toggle = {
                    more_expanded = !more_expanded
                    sidebar_prefs.edit().putBoolean(key_more_collapsed, !more_expanded).apply()
                    on_sidebar_toggle("sidebar_more_collapsed", !more_expanded)
                },
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = more_expanded,
                enter = section_expand_enter(),
                exit = section_expand_exit(),
            ) {
                androidx.compose.foundation.layout.Column {
                    more_secondary.forEach { item ->
                        drawer_row(
                            icon = item.icon,
                            label = item.label,
                            count = item.count,
                            is_unread_count = false,
                            selected = item.id == selected_id,
                            on_click = {
                                on_select(item.id)
                                on_close()
                            },
                        )
                    }
                    drawer_row(
                        icon = TablerIcons.MailOpened,
                        label = stringResource(R.string.refer_a_friend),
                        count = 0,
                        is_unread_count = false,
                        selected = false,
                        on_click = {
                            on_select("referral")
                            on_close()
                        },
                    )
                }
            }

            collapsible_section_header(
                text = stringResource(R.string.drawer_folders),
                expanded = folders_expanded,
                on_toggle = {
                    folders_expanded = !folders_expanded
                    sidebar_prefs.edit().putBoolean(key_folders_collapsed, !folders_expanded).apply()
                    on_sidebar_toggle("sidebar_folders_collapsed", !folders_expanded)
                },
                show_add = true,
                on_add = { show_create_folder = true },
                add_test_tag = "create_folder",
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = folders_expanded,
                enter = section_expand_enter(),
                exit = section_expand_exit(),
            ) {
                androidx.compose.foundation.layout.Column {
                    if (folder_items.isEmpty()) {
                        empty_section_hint(stringResource(R.string.no_folders_yet))
                    } else {
                        folder_items.forEach { item ->
                            drawer_row(
                                icon = item.icon,
                                label = item.label,
                                count = item.count,
                                is_unread_count = true,
                                selected = item.id == selected_id,
                                on_click = {
                                    on_select(item.id)
                                    on_navigate_folder(item.id, item.label)
                                    on_close()
                                },
                                depth = item.depth,
                                trail = item.trail,
                                has_next = item.has_next,
                            )
                        }
                    }
                }
            }

            collapsible_section_header(
                text = stringResource(R.string.drawer_labels),
                expanded = labels_expanded,
                on_toggle = {
                    labels_expanded = !labels_expanded
                    sidebar_prefs.edit().putBoolean(key_labels_collapsed, !labels_expanded).apply()
                    on_sidebar_toggle("sidebar_labels_collapsed", !labels_expanded)
                },
                show_add = true,
                on_add = { show_create_label = true },
                add_test_tag = "create_label",
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = labels_expanded,
                enter = section_expand_enter(),
                exit = section_expand_exit(),
            ) {
                androidx.compose.foundation.layout.Column {
                    if (label_items.isEmpty()) {
                        empty_section_hint(stringResource(R.string.no_labels_yet))
                    } else {
                        label_items.forEach { item ->
                            drawer_label_row(
                                color = item.color,
                                label = item.label,
                                icon = resolve_label_icon(item.icon),
                                selected = item.id == selected_id,
                                on_click = {
                                    on_select(item.id)
                                    on_navigate_label(item.id, item.label)
                                    on_close()
                                },
                            )
                        }
                    }
                }
            }

            collapsible_section_header(
                text = stringResource(R.string.drawer_aliases),
                expanded = aliases_expanded,
                on_toggle = {
                    aliases_expanded = !aliases_expanded
                    sidebar_prefs.edit().putBoolean(key_aliases_collapsed, !aliases_expanded).apply()
                    on_sidebar_toggle("sidebar_aliases_collapsed", !aliases_expanded)
                },
                show_add = true,
                on_add = {
                    on_select("aliases_create")
                    on_close()
                },
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = aliases_expanded,
                enter = section_expand_enter(),
                exit = section_expand_exit(),
            ) {
                androidx.compose.foundation.layout.Column {
                    if (alias_items.isEmpty()) {
                        empty_section_hint(stringResource(R.string.no_aliases_yet))
                    } else {
                        val collapsed_aliases = alias_items.take(aliases_collapsed_count)
                        val extra_aliases = if (alias_items.size > aliases_collapsed_count) {
                            alias_items.drop(aliases_collapsed_count)
                        } else emptyList()
                        collapsed_aliases.forEach { item ->
                            drawer_alias_row(
                                address = item.address,
                                selected = item.id == selected_id,
                                on_click = {
                                    on_select(item.id)
                                    on_navigate_alias(item.id, item.address, item.routing_token)
                                    on_close()
                                },
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = aliases_show_all,
                            enter = section_expand_enter(),
                            exit = section_expand_exit(),
                        ) {
                            androidx.compose.foundation.layout.Column {
                                extra_aliases.forEach { item ->
                                    drawer_alias_row(
                                        address = item.address,
                                        selected = item.id == selected_id,
                                        on_click = {
                                            on_select(item.id)
                                            on_navigate_alias(item.id, item.address, item.routing_token)
                                            on_close()
                                        },
                                    )
                                }
                            }
                        }
                        if (alias_items.size > aliases_collapsed_count) {
                            val remaining = alias_items.size - aliases_collapsed_count
                            show_more_row(
                                text = if (aliases_show_all) {
                                    stringResource(R.string.show_less)
                                } else {
                                    stringResource(R.string.show_n_more_aliases, remaining)
                                },
                                expanded = aliases_show_all,
                                on_click = { aliases_show_all = !aliases_show_all },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(AsterSpacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg)
                    .height(1.dp)
                    .background(colors.border_secondary.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.height(AsterSpacing.xs))
            drawer_row(
                icon = TablerIcons.Users,
                label = stringResource(R.string.folder_contacts),
                count = 0,
                is_unread_count = false,
                selected = selected_id == "contacts",
                on_click = {
                    on_select("contacts")
                    on_close()
                },
            )
            drawer_row(
                icon = TablerIcons.Settings,
                label = stringResource(R.string.settings),
                count = 0,
                is_unread_count = false,
                selected = false,
                on_click = {
                    on_select("settings")
                    on_close()
                },
                test_tag = "settings",
            )
            if (storage_label.isNotBlank()) {
                drawer_footer(
                    used_fraction = storage_used_fraction,
                    storage_label = storage_label,
                    on_feedback = {
                        on_select("feedback")
                        on_close()
                    },
                )
            }
            Spacer(Modifier.height(AsterSpacing.lg))
        }
    }

    if (show_workspace_sheet) {
        workspace_switcher_sheet(
            accounts = accounts,
            current_account_id = current_account_id,
            current_email = current_workspace,
            can_add = can_add_account,
            on_dismiss = { show_workspace_sheet = false },
            on_switch = { account ->
                show_workspace_sheet = false
                on_switch_account(account)
            },
            on_add = {
                show_workspace_sheet = false
                on_add_account()
            },
            on_logout = {
                show_workspace_sheet = false
                show_logout_confirm = true
            },
        )
    }

    if (show_logout_confirm) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_logout_confirm = false },
            title = stringResource(R.string.log_out_confirm_title),
            message = stringResource(R.string.log_out_confirm_message, current_workspace),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { show_logout_confirm = false },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.log_out),
                    onClick = {
                        show_logout_confirm = false
                        on_logout()
                    },
                )
            },
        )
    }

    if (show_create_folder) {
        create_folder_dialog(
            title = stringResource(R.string.create_folder),
            placeholder = stringResource(R.string.folder_name),
            parent_options = folder_parent_options,
            on_dismiss = { show_create_folder = false },
            on_create = { name, parent_token ->
                on_create_folder(name, parent_token)
                show_create_folder = false
            },
        )
    }

    if (show_create_label) {
        create_label_dialog(
            on_dismiss = { show_create_label = false },
            on_create = { name, color, icon ->
                on_create_label(name, color, icon)
                show_create_label = false
            },
        )
    }

}

@Composable
internal fun create_folder_dialog(
    title: String,
    placeholder: String,
    parent_options: List<folder_parent_option>,
    on_dismiss: () -> Unit,
    on_create: (name: String, parent_token: String?) -> Unit,
) {
    val colors = AsterMaterial.colors
    var text_value by remember { mutableStateOf("") }
    var selected_parent by remember { mutableStateOf<folder_parent_option?>(null) }
    var parent_menu_open by remember { mutableStateOf(false) }
    val none_label = stringResource(R.string.parent_folder_none)

    org.astermail.android.design.components.AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = title,
        confirm_label = stringResource(R.string.save),
        cancel_label = stringResource(R.string.cancel),
        on_confirm = { if (text_value.isNotBlank()) on_create(text_value.trim(), selected_parent?.token) },
        confirm_enabled = text_value.isNotBlank(),
        extra_content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                org.astermail.android.design.components.AsterTextField(
                    value = text_value,
                    onValueChange = { text_value = it },
                    placeholder = placeholder,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.parent_folder),
                    color = colors.text_muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border_secondary, RoundedCornerShape(8.dp))
                            .clickable { parent_menu_open = true }
                            .testTag("parent_folder_selector")
                            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selected_parent?.label ?: none_label,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = TablerIcons.ChevronDown,
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    aster_dropdown_menu(
                        expanded = parent_menu_open,
                        on_dismiss = { parent_menu_open = false },
                    ) {
                        aster_dropdown_item(
                            label = none_label,
                            selected = selected_parent == null,
                            on_click = {
                                selected_parent = null
                                parent_menu_open = false
                            },
                        )
                        parent_options.forEach { option ->
                            aster_dropdown_item(
                                label = " ".repeat(option.depth) + option.label,
                                icon = TablerIcons.Folder,
                                selected = selected_parent == option,
                                on_click = {
                                    selected_parent = option
                                    parent_menu_open = false
                                },
                            )
                        }
                    }
                }
                val path_label = selected_parent?.path_label
                if (!path_label.isNullOrBlank()) {
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = path_label,
                        color = colors.text_muted,
                        fontSize = 12.sp,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun create_label_dialog(
    on_dismiss: () -> Unit,
    on_create: (name: String, color: String, icon: String?) -> Unit,
) {
    val colors = AsterMaterial.colors
    var name_value by remember { mutableStateOf("") }
    var selected_color by remember { mutableStateOf(default_label_color) }
    var selected_icon by remember { mutableStateOf<String?>(null) }
    val accent = parse_hex_color(selected_color)

    org.astermail.android.design.components.AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.create_label),
        confirm_label = stringResource(R.string.create),
        cancel_label = stringResource(R.string.cancel),
        on_confirm = {
            if (name_value.isNotBlank()) {
                on_create(name_value.trim(), selected_color, selected_icon)
            }
        },
        confirm_enabled = name_value.isNotBlank(),
        extra_content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon_vector = selected_icon
                        ?.let { key -> label_icon_presets.firstOrNull { it.first == key }?.second }
                        ?: TablerIcons.Tag
                    Icon(
                        imageVector = icon_vector,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = name_value.ifBlank { stringResource(R.string.preview) },
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                org.astermail.android.design.components.AsterTextField(
                    value = name_value,
                    onValueChange = { name_value = it },
                    placeholder = stringResource(R.string.label_name),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.color_label),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    label_color_presets.forEach { hex ->
                        val swatch = parse_hex_color(hex)
                        val is_selected = hex.equals(selected_color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (is_selected)
                                        Modifier.border(2.dp, colors.text_primary, CircleShape)
                                    else
                                        Modifier.border(1.dp, colors.border_secondary, CircleShape)
                                )
                                .clickable { selected_color = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (is_selected) {
                                Icon(
                                    imageVector = TablerIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.icon_label),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    label_icon_presets.forEach { (key, vector) ->
                        val is_selected = key == selected_icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SquircleShape(8.dp))
                                .background(
                                    if (is_selected) accent.copy(alpha = 0.15f) else colors.bg_hover
                                )
                                .then(
                                    if (is_selected)
                                        Modifier.border(1.dp, accent, SquircleShape(8.dp))
                                    else
                                        Modifier
                                )
                                .clickable {
                                    selected_icon = if (is_selected) null else key
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = key,
                                tint = if (is_selected) accent else colors.text_secondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun workspace_switcher_sheet(
    accounts: List<StoredAccount>,
    current_account_id: String?,
    current_email: String,
    can_add: Boolean,
    on_dismiss: () -> Unit,
    on_switch: (StoredAccount) -> Unit,
    on_add: () -> Unit,
    on_logout: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState()
    val copy_action = org.astermail.android.ui.common.remember_copy_action()
    val name_copied = stringResource(R.string.name_copied)
    val copy_toast_context = androidx.compose.ui.platform.LocalContext.current
    val name_clip_label = stringResource(R.string.display_name)
    val email_clip_label = stringResource(R.string.email)

    val ordered = if (accounts.isNotEmpty()) {
        val current = accounts.firstOrNull { it.id == current_account_id }
        val rest = accounts.filter { it.id != current_account_id }
        listOfNotNull(current) + rest
    } else {
        emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md)
                .heightIn(min = 120.dp),
        ) {
            Text(
                text = stringResource(R.string.accounts),
                color = colors.text_muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
            )
            if (ordered.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (av_bg, av_fg) = avatar_colors_for(current_email)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(av_bg, SquircleShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initial_for("", current_email),
                            color = av_fg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(AsterSpacing.md))
                    Text(
                        text = current_email,
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = stringResource(R.string.current),
                        tint = colors.accent_blue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            ordered.forEach { account ->
                val is_current = account.id == current_account_id
                val display = account.display_name?.takeIf { it.isNotBlank() } ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !is_current) { on_switch(account) }
                        .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SenderAvatar(
                        email = account.email,
                        name = display,
                        size = 32.dp,
                        profile_picture_url = account.profile_picture,
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        if (display.isNotBlank()) {
                            Text(
                                text = display,
                                color = colors.text_primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { if (!is_current) on_switch(account) },
                                        onLongClick = { copy_action(name_clip_label, display, name_copied) },
                                    )
                                    .padding(vertical = 2.dp),
                            )
                        }
                        Text(
                            text = account.email,
                            color = colors.text_muted,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { if (!is_current) on_switch(account) },
                                    onLongClick = {
                                        copy_action(
                                            email_clip_label,
                                            account.email,
                                            org.astermail.android.ui.common.copied_toast_text(
                                                copy_toast_context,
                                                account.email,
                                            ),
                                        )
                                    },
                                )
                                .padding(vertical = 2.dp),
                        )
                    }
                    if (is_current) {
                        Icon(
                            imageVector = TablerIcons.Check,
                            contentDescription = stringResource(R.string.current),
                            tint = colors.accent_blue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = can_add, onClick = on_add)
                    .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = null,
                    tint = if (can_add) colors.text_muted else colors.text_muted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Text(
                    text = if (can_add) stringResource(R.string.add_account) else stringResource(R.string.account_limit_reached),
                    color = if (can_add) colors.text_primary else colors.text_muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = on_logout)
                    .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TablerIcons.Logout,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.log_out),
                    color = colors.danger,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@Composable
private fun workspace_header(
    current_address: String,
    account_email: String,
    account_name: String,
    profile_picture: String?,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = on_click)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("workspace_switcher"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SenderAvatar(
                email = account_email,
                name = account_name,
                size = 40.dp,
                profile_picture_url = profile_picture,
                modifier = Modifier.testTag("account_avatar"),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account_name.ifBlank { current_address.substringBefore('@') },
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.1).sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = current_address,
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = stringResource(R.string.switch_workspace),
                tint = colors.text_muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun collapsible_section_header(
    text: String,
    expanded: Boolean,
    on_toggle: () -> Unit,
    show_add: Boolean = false,
    on_add: () -> Unit = {},
    add_test_tag: String? = null,
) {
    val colors = AsterMaterial.colors
    val chevron_rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "section_chevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_toggle)
            .padding(
                start = 24.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            color = colors.text_muted.copy(alpha = 0.75f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = TablerIcons.ChevronDown,
            contentDescription = null,
            tint = colors.text_muted.copy(alpha = 0.7f),
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = chevron_rotation },
        )
        Spacer(Modifier.width(if (show_add) 8.dp else 0.dp))
        if (show_add) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = on_add)
                    .then(if (add_test_tag != null) Modifier.testTag(add_test_tag) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = stringResource(R.string.add),
                    tint = colors.text_muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun tree_indent_guides(
    depth: Int,
    trail: List<Boolean> = emptyList(),
    has_next: Boolean = false,
) {
    if (depth <= 0) return
    val guide_color = AsterMaterial.colors.border_secondary
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .width((depth * 18).dp)
            .height(46.dp),
    ) {
        val stroke_width = 1.5.dp.toPx()
        val slot = 18.dp.toPx()
        val line_offset = 8.dp.toPx()
        val cy = size.height / 2f
        val curve_start = 10.dp.toPx()
        val curve_reach = 9.dp.toPx()
        val stroke_style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke_width,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        for (level in 0 until depth - 1) {
            if (trail.getOrNull(level + 1) == true) {
                val x = level * slot + line_offset
                drawLine(
                    color = guide_color,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = stroke_width,
                )
            }
        }
        val branch_x = (depth - 1) * slot + line_offset
        if (has_next) {
            drawLine(
                color = guide_color,
                start = androidx.compose.ui.geometry.Offset(branch_x, 0f),
                end = androidx.compose.ui.geometry.Offset(branch_x, size.height),
                strokeWidth = stroke_width,
            )
            val branch = androidx.compose.ui.graphics.Path().apply {
                moveTo(branch_x, cy - curve_start)
                quadraticTo(branch_x, cy, branch_x + curve_reach, cy)
            }
            drawPath(path = branch, color = guide_color, style = stroke_style)
        } else {
            val elbow = androidx.compose.ui.graphics.Path().apply {
                moveTo(branch_x, 0f)
                lineTo(branch_x, cy - curve_start)
                quadraticTo(branch_x, cy, branch_x + curve_reach, cy)
            }
            drawPath(path = elbow, color = guide_color, style = stroke_style)
        }
    }
}

@Composable
private fun drawer_row(
    icon: ImageVector,
    label: String,
    count: Int,
    is_unread_count: Boolean,
    selected: Boolean,
    on_click: () -> Unit,
    test_tag: String? = null,
    depth: Int = 0,
    trail: List<Boolean> = emptyList(),
    has_next: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val bg by animateColorAsState(
        targetValue = if (selected) colors.accent_blue.copy(alpha = if (colors.is_dark) 0.22f else 0.14f) else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "row_bg",
    )
    val text_color by animateColorAsState(
        targetValue = if (selected) colors.text_primary else colors.text_secondary,
        animationSpec = tween(durationMillis = 150),
        label = "row_text",
    )
    val icon_color by animateColorAsState(
        targetValue = if (selected) colors.text_primary else colors.text_muted,
        animationSpec = tween(durationMillis = 150),
        label = "row_icon",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = on_click)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tree_indent_guides(depth, trail, has_next)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = icon_color,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                color = text_color,
                fontSize = 15.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = (-0.15).sp,
                modifier = Modifier.weight(1f),
            )
            if (count > 0) {
                count_badge(
                    value = count,
                    emphasized = is_unread_count,
                    selected = selected,
                )
            }
        }
    }
}

@Composable
private fun count_badge(value: Int, emphasized: Boolean, selected: Boolean) {
    val colors = AsterMaterial.colors
    val text_color = when {
        emphasized && selected -> colors.text_primary
        emphasized -> colors.text_secondary
        else -> colors.text_muted
    }
    Text(
        text = value.toString(),
        color = text_color,
        fontSize = 13.sp,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
    )
}

@Composable
private fun drawer_label_row(
    color: Color,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val bg by animateColorAsState(
        targetValue = if (selected) {
            colors.accent_blue.copy(alpha = if (colors.is_dark) 0.22f else 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "row_bg",
    )
    val text_color by animateColorAsState(
        targetValue = if (selected) colors.text_primary else colors.text_secondary,
        animationSpec = tween(durationMillis = 150),
        label = "row_text",
    )
    val row_modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(bg)
        .clickable(onClick = on_click)
        .padding(horizontal = 15.dp)
        .height(48.dp)
    Row(
        modifier = row_modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = text_color,
            fontSize = 15.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = (-0.15).sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun drawer_alias_row(
    address: String,
    selected: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val bg by animateColorAsState(
        targetValue = if (selected) {
            colors.accent_blue.copy(alpha = if (colors.is_dark) 0.22f else 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 150),
        label = "row_bg",
    )
    val icon_color by animateColorAsState(
        targetValue = if (selected) colors.text_primary else colors.text_muted,
        animationSpec = tween(durationMillis = 150),
        label = "row_icon",
    )
    val row_modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(bg)
        .clickable(onClick = on_click)
        .padding(horizontal = 15.dp)
        .height(48.dp)
    Row(
        modifier = row_modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.At,
            contentDescription = null,
            tint = icon_color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = address,
            color = if (selected) colors.text_primary else colors.text_secondary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = (-0.15).sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val aster_wordmark_ratio = 800f / 199f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun drawer_footer(
    used_fraction: Float = 0f,
    storage_label: String = "",
    on_feedback: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_primary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_secondary),
        )
        storage_meter(
            used_percent = used_fraction,
            label = storage_label,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_secondary),
        )
        val copy_action = org.astermail.android.ui.common.remember_copy_action()
        val version_copied = stringResource(R.string.version_copied)
        val clip_label = stringResource(R.string.app_name)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.aster_wordmark),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .height(22.dp)
                    .aspectRatio(aster_wordmark_ratio)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = {
                            copy_action(
                                clip_label,
                                "v${org.astermail.android.BuildConfig.VERSION_NAME}",
                                version_copied,
                            )
                        },
                    ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.leave_us_feedback),
                color = colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .offset(y = 3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = on_feedback)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun storage_meter(used_percent: Float, label: String) {
    val colors = AsterMaterial.colors
    val clamped = used_percent.coerceIn(0f, 1f)
    val display_fraction = if (clamped < 0.005f) 0f else clamped
    val pct = clamped * 100
    val pct_label = when {
        clamped <= 0f -> "0%"
        pct < 0.1f -> "<0.1%"
        pct < 1f -> "%.1f%%".format(pct)
        else -> "${pct.toInt()}%"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.storage_used),
                color = colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pct_label,
                color = colors.text_muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        val animated_fraction by animateFloatAsState(
            targetValue = display_fraction,
            animationSpec = tween(durationMillis = 420),
            label = "storage_bar",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.bg_hover),
        ) {
            if (animated_fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animated_fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent_blue),
                )
            }
        }
        if (label.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
    }
}

private fun section_expand_enter(): androidx.compose.animation.EnterTransition =
    androidx.compose.animation.expandVertically(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 220,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
    ) + androidx.compose.animation.fadeIn(
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
    )

private fun section_expand_exit(): androidx.compose.animation.ExitTransition =
    androidx.compose.animation.shrinkVertically(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 200,
            easing = androidx.compose.animation.core.FastOutLinearInEasing,
        ),
    ) + androidx.compose.animation.fadeOut(
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
    )

@Composable
private fun show_more_row(text: String, expanded: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val chevron_rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "chevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(start = 30.dp, end = AsterSpacing.lg, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.ChevronDown,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = chevron_rotation },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = colors.text_muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun empty_section_hint(text: String) {
    val colors = AsterMaterial.colors
    Text(
        text = text,
        color = colors.text_muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(
            start = AsterSpacing.xxl,
            top = AsterSpacing.xs,
            bottom = AsterSpacing.xs,
        ),
    )
}
