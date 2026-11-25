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

package org.astermail.android.ui.contacts

import org.astermail.android.ui.common.vertical_scroll_with_indicator
import org.astermail.android.ui.common.lazy_top_fade
import org.astermail.android.ui.common.top_bar_account_button
import org.astermail.android.ui.settings.detail.aster_segmented
import org.astermail.android.ui.settings.detail.switcher_option
import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.contacts.ContactGroup
import org.astermail.android.contacts.ContactsTab
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.contacts.DuplicateCluster
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterExpandVisibility
import org.astermail.android.design.AsterMotionSpec
import org.astermail.android.design.aster_fade_through
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.tonal_surface_color
import org.astermail.android.design.island_surface_color
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.acrylic
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.auto_mirrored
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import org.astermail.android.ui.common.page_surface
import org.astermail.android.ui.mail.inbox_card_content_padding
import org.astermail.android.ui.mail.inbox_card_horizontal_margin
import org.astermail.android.ui.mail.inbox_card_read_color
import org.astermail.android.ui.mail.inbox_card_selected_color
import org.astermail.android.ui.mail.inbox_group_shape
import org.astermail.android.ui.mail.inbox_group_split

private val group_default_colors: List<String> =
    org.astermail.android.ui.common.display_color_presets

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    on_back: () -> Unit = {},
    on_open_contact: (String) -> Unit,
    on_create_contact: () -> Unit,
    on_open_group: (String) -> Unit = {},
    on_open_drawer: (() -> Unit)? = null,
    on_compose_to: ((List<String>) -> Unit)? = null,
    search_back_enabled: Boolean = true,
    on_open_account: (() -> Unit)? = null,
    stored_account: org.astermail.android.storage.StoredAccount? = null,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val ui_state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter_favorites by rememberSaveable { mutableStateOf(false) }
    var filter_recent by rememberSaveable { mutableStateOf(false) }
    var show_sync_confirm by remember { mutableStateOf(false) }
    var show_bulk_menu by remember { mutableStateOf(false) }
    var show_overflow_menu by remember { mutableStateOf(false) }
    var pending_export_selected by remember { mutableStateOf(false) }
    var pending_export_format by remember {
        mutableStateOf(org.astermail.android.contacts.ContactExportFormat.VCARD)
    }
    val import_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.import_contacts_from_file(context, uri)
    }
    val on_export_target: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null) {
            vm.export_contacts_to_file(context, uri, pending_export_format, pending_export_selected)
        }
    }
    val vcard_export_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard"),
        on_export_target,
    )
    val csv_export_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
        on_export_target,
    )
    val start_export: (Boolean, org.astermail.android.contacts.ContactExportFormat) -> Unit =
        { selected, format ->
            pending_export_selected = selected
            pending_export_format = format
            val stamp = java.time.LocalDate.now().toString()
            if (format == org.astermail.android.contacts.ContactExportFormat.VCARD) {
                vcard_export_launcher.launch("aster-contacts-$stamp.vcf")
            } else {
                csv_export_launcher.launch("aster-contacts-$stamp.csv")
            }
        }
    var show_delete_confirm by remember { mutableStateOf(false) }
    var show_empty_trash_confirm by remember { mutableStateOf(false) }
    var pending_forever_delete by remember { mutableStateOf<Contact?>(null) }
    var show_new_group by remember { mutableStateOf(false) }
    var new_group_for_selection by remember { mutableStateOf(false) }
    var editing_group by remember { mutableStateOf<ContactGroup?>(null) }
    var show_group_picker by remember { mutableStateOf(false) }
    var merge_cluster by remember { mutableStateOf<DuplicateCluster?>(null) }
    var pending_group_delete by remember { mutableStateOf<ContactGroup?>(null) }

    val permission_launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.sync_device_contacts(context)
        } else {
            org.astermail.android.ui.common.app_toast.show(context.getString(R.string.contacts_permission_required).toString())
        }
    }

    LaunchedEffect(Unit) {
        if (ui_state.contacts.isEmpty()) vm.load_contacts()
        vm.load_groups()
    }

    LaunchedEffect(ui_state.sync_message) {
        ui_state.sync_message?.let { msg ->
            org.astermail.android.ui.common.app_toast.show(msg.toString())
            vm.clear_sync_message()
        }
    }

    LaunchedEffect(ui_state.error) {
        ui_state.error?.let { err ->
            org.astermail.android.ui.common.app_toast.show(err.toString())
            vm.clear_flags()
        }
    }

    val search_focus_requester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focus_manager = androidx.compose.ui.platform.LocalFocusManager.current
    var search_focused by remember { mutableStateOf(false) }
    val exit_search: () -> Unit = {
        query = ""
        search_focused = false
        focus_manager.clearFocus()
    }
    LaunchedEffect(ui_state.is_selecting) {
        if (ui_state.is_selecting) search_focused = false
    }

    BackHandler(
        enabled = search_back_enabled && !ui_state.is_selecting && (query.isNotEmpty() || search_focused),
    ) { exit_search() }
    BackHandler(enabled = ui_state.is_selecting) { vm.clear_selection() }

    val relation_names = relation_kind_names()
    val relation_name: (org.astermail.android.contacts.ContactRelation) -> String = { relation ->
        relation_display_name(relation, relation_names)
    }
    val show_recent_filter = remember(ui_state.contacts) {
        org.astermail.android.contacts.has_recently_added(ui_state.contacts)
    }
    LaunchedEffect(show_recent_filter) {
        if (!show_recent_filter) filter_recent = false
    }
    val recent_mode = filter_recent && show_recent_filter
    val filtered = remember(query, filter_favorites, recent_mode, ui_state.contacts, relation_names) {
        val matched = ui_state.contacts
            .filter { if (filter_favorites) it.is_favorite else true }
            .filter { contact_matches_query(it, query, relation_name) }
        if (recent_mode) {
            org.astermail.android.contacts.recently_added_sorted(matched)
        } else {
            matched.sortedBy { it.name.uppercase() }
        }
    }

    val today_label = stringResource(R.string.contacts_today)
    val yesterday_label = stringResource(R.string.yesterday)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val grouped = remember(filtered, recent_mode, today_label, yesterday_label, locale) {
        if (recent_mode) {
            val formatter = java.time.format.DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(locale)
            org.astermail.android.contacts.group_recently_added(
                filtered,
                java.time.LocalDate.now(),
                java.time.ZoneId.systemDefault(),
            ).associate { group ->
                val label = when (group.bucket) {
                    org.astermail.android.contacts.RecentBucket.TODAY -> today_label
                    org.astermail.android.contacts.RecentBucket.YESTERDAY -> yesterday_label
                    org.astermail.android.contacts.RecentBucket.DATE -> group.date.format(formatter)
                }
                label to group.contacts
            }
        } else {
            filtered.groupBy { letter_of(it.name) }.toSortedMap()
        }
    }

    val filtered_groups = remember(query, ui_state.groups) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) ui_state.groups else ui_state.groups.filter { it.name.lowercase().contains(q) }
    }

    val filtered_trash = remember(query, ui_state.trashed_contacts, relation_names) {
        ui_state.trashed_contacts.filter { contact_matches_query(it, query, relation_name) }
    }

    val on_compose_selection: () -> Unit = {
        val addresses = ui_state.selected_contacts
            .map { it.email.ifBlank { it.work_email } }
            .filter { it.isNotBlank() }
            .distinct()
        if (addresses.isNotEmpty()) on_compose_to?.invoke(addresses)
        vm.clear_selection()
    }

    val ime_bottom = if (search_focused) {
        androidx.compose.foundation.layout.WindowInsets.ime
            .exclude(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .asPaddingValues()
            .calculateBottomPadding()
    } else {
        0.dp
    }
    val nav_bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val contacts_list_state = androidx.compose.foundation.lazy.rememberLazyListState()
    val drag_order = remember(grouped) { grouped.values.flatMap { group -> group.map { it.id } } }
    val drag_index = remember(drag_order) {
        HashMap<String, Int>(drag_order.size * 2).also { map ->
            drag_order.forEachIndexed { i, id -> map[id] = i }
        }
    }
    val latest_drag_order = rememberUpdatedState(drag_order)
    val latest_drag_index = rememberUpdatedState(drag_index)
    val latest_selected_ids = rememberUpdatedState(ui_state.selected_ids)
    val contact_haptics_enabled = org.astermail.android.ui.theme.local_accessibility.current.haptic_enabled
    val contact_haptics = LocalHapticFeedback.current
    val contact_density = LocalDensity.current
    val drag_anchor_index = remember { mutableIntStateOf(-1) }
    val drag_last_index = remember { mutableIntStateOf(-1) }
    val drag_pre_selected = remember { mutableStateListOf<String>() }
    val drag_pointer_y = remember { mutableFloatStateOf(-1f) }
    val drag_viewport_height = remember { mutableFloatStateOf(0f) }
    var drag_selecting by remember { mutableStateOf(false) }

    fun contact_index_at_offset(y: Float): Int? {
        val info = contacts_list_state.layoutInfo
        val item_y = y + info.viewportStartOffset
        val entry = info.visibleItemsInfo.firstOrNull { entry ->
            item_y >= entry.offset && item_y < entry.offset + entry.size
        } ?: return null
        val key = entry.key as? String ?: return null
        return latest_drag_index.value[key]
    }

    fun apply_contact_drag_selection(y: Float) {
        val anchor = drag_anchor_index.intValue
        if (anchor < 0) return
        val idx = contact_index_at_offset(y) ?: return
        if (idx == drag_last_index.intValue) return
        drag_last_index.intValue = idx
        val order = latest_drag_order.value
        val target = LinkedHashSet<String>(drag_pre_selected)
        for (i in minOf(anchor, idx)..maxOf(anchor, idx)) {
            order.getOrNull(i)?.let { target.add(it) }
        }
        val current = latest_selected_ids.value
        if (target.size != current.size || !target.containsAll(current)) {
            if (contact_haptics_enabled) contact_haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            vm.set_selection(target)
        }
    }

    val drag_edge_px = with(contact_density) { 72.dp.toPx() }
    LaunchedEffect(drag_selecting) {
        if (!drag_selecting) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val y = drag_pointer_y.floatValue
            val viewport = drag_viewport_height.floatValue
            if (y < 0f || viewport <= 0f) continue
            val info = contacts_list_state.layoutInfo
            val content_top = info.beforeContentPadding.toFloat()
            val content_bottom = viewport - info.afterContentPadding.toFloat()
            val usable = content_bottom - content_top
            if (usable <= 0f) continue
            val edge = minOf(drag_edge_px, usable / 3f)
            if (edge <= 0f) continue
            val top_bound = content_top + edge
            val bottom_bound = content_bottom - edge
            val ratio = when {
                y < top_bound -> -((top_bound - y) / edge)
                y > bottom_bound -> (y - bottom_bound) / edge
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (ratio != 0f) {
                contacts_list_state.scrollBy(ratio * 26f)
                apply_contact_drag_selection(y.coerceIn(content_top, content_bottom - 1f))
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .statusBarsPadding()
            .windowInsetsPadding(
                androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal),
            )
            .padding(bottom = ime_bottom),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (ui_state.is_bulk_working && ui_state.bulk_total > 1) {
                bulk_progress_banner(
                    done = ui_state.bulk_done,
                    total = ui_state.bulk_total,
                )
            } else if ((ui_state.is_transferring || ui_state.is_syncing) && ui_state.import_total > 0) {
                bulk_progress_banner(
                    done = ui_state.import_done,
                    total = ui_state.import_total,
                    label_res = R.plurals.contacts_import_progress,
                )
            }
            val search_placeholder = stringResource(
                when (ui_state.tab) {
                    ContactsTab.GROUPS -> R.string.search_groups
                    ContactsTab.TRASH -> R.string.search_trash
                    else -> R.string.search_contacts
                },
            )
            val top_bar_reduce_motion = aster_reduce_motion()
            val last_selected_count = remember { intArrayOf(0) }
            if (ui_state.selected_ids.isNotEmpty()) last_selected_count[0] = ui_state.selected_ids.size
            AnimatedContent(
                targetState = ui_state.is_selecting,
                transitionSpec = { aster_fade_through(top_bar_reduce_motion) },
                label = "contacts_top_bar",
            ) { selecting ->
            if (selecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.xs)
                        .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs)
                        .height(contacts_top_bar_height),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsterIconButton(
                        icon = TablerIcons.X,
                        content_description = stringResource(R.string.clear_selection),
                        onClick = { vm.clear_selection() },
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = pluralStringResource(R.plurals.count_selected, last_selected_count[0], last_selected_count[0]),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text_primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    val all_selected = filtered.isNotEmpty() &&
                        filtered.all { ui_state.selected_ids.contains(it.id) }
                    AsterIconButton(
                        icon = if (all_selected) TablerIcons.SquareCheck else TablerIcons.Square,
                        content_description = if (all_selected) {
                            stringResource(R.string.deselect_all)
                        } else {
                            stringResource(R.string.select_all)
                        },
                        onClick = {
                            if (all_selected) {
                                vm.clear_selection()
                            } else {
                                vm.set_selection(filtered.map { it.id }.toSet())
                            }
                        },
                    )
                    Box {
                        AsterIconButton(
                            icon = TablerIcons.DotsVertical,
                            content_description = stringResource(R.string.more_options),
                            onClick = { show_bulk_menu = true },
                            modifier = Modifier.testTag("contacts_bulk_menu"),
                        )
                        aster_menu(
                            expanded = show_bulk_menu,
                            on_dismiss = { show_bulk_menu = false },
                        ) {
                            aster_menu_item(
                                label = stringResource(R.string.compose),
                                icon = TablerIcons.Mail,
                                test_tag = "bulk_compose",
                                on_click = {
                                    show_bulk_menu = false
                                    on_compose_selection()
                                },
                            )
                            val favorite_target = org.astermail.android.contacts.favorite_target(
                                ui_state.selected_contacts,
                            )
                            if (favorite_target != null) {
                                aster_menu_item(
                                    label = stringResource(
                                        if (favorite_target) {
                                            R.string.contacts_add_to_favorites
                                        } else {
                                            R.string.contacts_remove_from_favorites
                                        },
                                    ),
                                    icon = TablerIcons.Star,
                                    test_tag = "bulk_favorite",
                                    on_click = {
                                        show_bulk_menu = false
                                        vm.set_selection_favorite { ok ->
                                            if (!ok) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.contacts_favorites_failed),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                )
                            }
                            aster_menu_item(
                                label = stringResource(R.string.add_to_group),
                                icon = TablerIcons.Users,
                                test_tag = "bulk_add_to_group",
                                on_click = {
                                    show_bulk_menu = false
                                    vm.load_groups()
                                    show_group_picker = true
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_vcard),
                                icon = TablerIcons.Download,
                                test_tag = "bulk_export_vcard",
                                on_click = {
                                    show_bulk_menu = false
                                    start_export(
                                        true,
                                        org.astermail.android.contacts.ContactExportFormat.VCARD,
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_csv),
                                icon = TablerIcons.Download,
                                test_tag = "bulk_export_csv",
                                on_click = {
                                    show_bulk_menu = false
                                    start_export(
                                        true,
                                        org.astermail.android.contacts.ContactExportFormat.CSV,
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.delete),
                                icon = TablerIcons.Trash,
                                destructive = true,
                                test_tag = "bulk_delete",
                                on_click = {
                                    show_bulk_menu = false
                                    show_delete_confirm = true
                                },
                            )
                        }
                    }
                }
            } else {
                contacts_search_top_bar(
                    query = query,
                    on_query_change = { query = it },
                    placeholder = search_placeholder,
                    is_syncing = ui_state.is_syncing,
                    on_open_drawer = on_open_drawer,
                    on_back = on_back,
                    focus_requester = search_focus_requester,
                    is_focused = search_focused,
                    on_focus_change = { search_focused = it },
                    on_exit_search = exit_search,
                ) {
                    if (on_open_account != null) {
                        top_bar_account_button(
                            stored_account = stored_account,
                            on_click = on_open_account,
                        )
                    }
                }
            }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AsterSpacing.lg, end = AsterSpacing.xs)
                    .padding(top = AsterSpacing.xs, bottom = AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    aster_segmented(
                        value = ui_state.tab.name,
                        options = listOf(
                            switcher_option(
                                id = ContactsTab.CONTACTS.name,
                                label = stringResource(R.string.contacts),
                                badge = ui_state.contacts.size.takeIf { it > 0 }?.toString(),
                            ),
                            switcher_option(
                                id = ContactsTab.GROUPS.name,
                                label = stringResource(R.string.contact_groups),
                                badge = ui_state.groups.size.takeIf { it > 0 }?.toString(),
                            ),
                            switcher_option(
                                id = ContactsTab.TRASH.name,
                                label = stringResource(R.string.trash),
                                badge = ui_state.trashed_contacts.size.takeIf { it > 0 }?.toString(),
                            ),
                        ),
                        on_change = { id -> vm.select_tab(ContactsTab.valueOf(id)) },
                        modifier = Modifier.testTag("contacts_tabs"),
                    )
                }
                if (!ui_state.is_selecting) {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Box {
                        AsterIconButton(
                            icon = TablerIcons.DotsVertical,
                            content_description = stringResource(R.string.more_options),
                            onClick = { show_overflow_menu = true },
                            modifier = Modifier.testTag("contacts_overflow_menu"),
                        )
                        aster_menu(
                            expanded = show_overflow_menu,
                            on_dismiss = { show_overflow_menu = false },
                        ) {
                            if (ui_state.tab == ContactsTab.CONTACTS) {
                                org.astermail.android.design.components.aster_menu_toggle_item(
                                    label = stringResource(R.string.contacts_show_favorites_only),
                                    checked = filter_favorites,
                                    icon = TablerIcons.Star,
                                    test_tag = "contacts_favorites_filter",
                                    on_click = {
                                        show_overflow_menu = false
                                        filter_favorites = !filter_favorites
                                        if (filter_favorites) filter_recent = false
                                    },
                                )
                                if (show_recent_filter) {
                                    org.astermail.android.design.components.aster_menu_toggle_item(
                                        label = stringResource(R.string.contacts_recently_added),
                                        checked = recent_mode,
                                        icon = TablerIcons.Clock,
                                        test_tag = "contacts_recent_filter",
                                        on_click = {
                                            show_overflow_menu = false
                                            filter_recent = !filter_recent
                                            if (filter_recent) filter_favorites = false
                                        },
                                    )
                                }
                            }
                            aster_menu_item(
                                label = stringResource(
                                    if (ui_state.is_syncing) R.string.syncing else R.string.sync_contacts,
                                ),
                                icon = TablerIcons.Refresh,
                                enabled = !ui_state.is_syncing,
                                test_tag = "contacts_sync",
                                on_click = {
                                    show_overflow_menu = false
                                    show_sync_confirm = true
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.import_contacts_file),
                                icon = TablerIcons.Upload,
                                test_tag = "contacts_import_file",
                                on_click = {
                                    show_overflow_menu = false
                                    import_launcher.launch(
                                        arrayOf(
                                            "text/x-vcard",
                                            "text/vcard",
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/vnd.ms-excel",
                                            "application/octet-stream",
                                            "text/*",
                                        ),
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_vcard),
                                icon = TablerIcons.Download,
                                enabled = ui_state.contacts.isNotEmpty(),
                                test_tag = "contacts_export_vcard",
                                on_click = {
                                    show_overflow_menu = false
                                    start_export(
                                        false,
                                        org.astermail.android.contacts.ContactExportFormat.VCARD,
                                    )
                                },
                            )
                            aster_menu_item(
                                label = stringResource(R.string.export_as_csv),
                                icon = TablerIcons.Download,
                                enabled = ui_state.contacts.isNotEmpty(),
                                test_tag = "contacts_export_csv",
                                on_click = {
                                    show_overflow_menu = false
                                    start_export(
                                        false,
                                        org.astermail.android.contacts.ContactExportFormat.CSV,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            val contacts_pending = ui_state.contacts.isEmpty() &&
                (ui_state.is_loading || (!ui_state.contacts_settled && ui_state.error == null))
            if (ui_state.tab == ContactsTab.GROUPS) {
                groups_pane(
                    groups = filtered_groups,
                    query = query.trim(),
                    bottom_inset = nav_bottom,
                    is_loading = !ui_state.groups_loaded && ui_state.groups.isEmpty(),
                    count_of = { ui_state.group_member_count(it) },
                    modifier = Modifier.weight(1f),
                    on_open = { on_open_group(it.id) },
                    on_edit = { editing_group = it },
                    on_delete = { pending_group_delete = it },
                )
            } else if (ui_state.tab == ContactsTab.TRASH && contacts_pending) {
                Spacer(Modifier.weight(1f).fillMaxWidth())
            } else if (ui_state.tab == ContactsTab.TRASH) {
                trash_pane(
                    contacts = filtered_trash,
                    query = query.trim(),
                    bottom_inset = nav_bottom,
                    is_busy = ui_state.is_bulk_working,
                    modifier = Modifier.weight(1f),
                    on_restore = { contact ->
                        vm.restore_contact(contact) { ok ->
                            if (ok) {
                                org.astermail.android.ui.common.app_toast.show(context.getString(R.string.contact_restored).toString())
                            }
                        }
                    },
                    on_delete_forever = { pending_forever_delete = it },
                    on_empty_trash = { show_empty_trash_confirm = true },
                )
            } else {
                AsterExpandVisibility(
                    visible = !ui_state.duplicates_dismissed &&
                        ui_state.duplicate_clusters.isNotEmpty() &&
                        !ui_state.is_selecting,
                ) {
                    duplicate_banner(
                        count = ui_state.duplicate_count,
                        on_review = {
                            ui_state.duplicate_clusters.firstOrNull()?.let { merge_cluster = it }
                        },
                        on_dismiss = { vm.dismiss_duplicates() },
                    )
                }

                if (contacts_pending) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = nav_bottom),
                        contentAlignment = Alignment.Center,
                    ) {
                        org.astermail.android.design.components.aster_spinner(color = colors.accent_blue)
                    }
                } else if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(bottom = nav_bottom)
                            .padding(AsterSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        val empty_message = ui_state.error
                            ?: when (contacts_empty_reason(query, filter_favorites)) {
                                ContactsEmptyReason.no_contacts -> stringResource(R.string.no_contacts_yet)
                                ContactsEmptyReason.no_match ->
                                    stringResource(R.string.no_contacts_match, query.trim())
                                ContactsEmptyReason.no_favorites -> stringResource(R.string.no_favorite_contacts)
                                ContactsEmptyReason.no_favorites_match ->
                                    stringResource(R.string.no_favorite_contacts_match, query.trim())
                            }
                        Text(
                            text = empty_message,
                            color = colors.text_muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = contacts_list_state,
                            modifier = Modifier
                                .fillMaxSize()
                                .lazy_top_fade(contacts_list_state)
                                .pointerInput(Unit) {
                                    val press_slop = viewConfiguration.touchSlop
                                    val long_press_ms = viewConfiguration.longPressTimeoutMillis
                                    val drag_start_slop = 24.dp.toPx()
                                    awaitEachGesture {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                        val anchor_index = contact_index_at_offset(down.position.y)
                                        if (anchor_index == null) return@awaitEachGesture
                                        var long_pressed = false
                                        var aborted = false
                                        try {
                                            withTimeout(long_press_ms) {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull()
                                                    if (change == null || !change.pressed) {
                                                        aborted = true
                                                        break
                                                    }
                                                    if ((change.position - down.position).getDistance() > press_slop) {
                                                        aborted = true
                                                        break
                                                    }
                                                }
                                            }
                                        } catch (_: PointerEventTimeoutCancellationException) {
                                            long_pressed = true
                                        }
                                        if (!long_pressed || aborted) return@awaitEachGesture
                                        var drag_started = false
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull() ?: break
                                                val has_selection = latest_selected_ids.value.isNotEmpty()
                                                if (drag_started || has_selection) change.consume()
                                                if (!change.pressed) break
                                                if (!has_selection) continue
                                                if ((change.position - down.position).getDistance() < drag_start_slop) continue
                                                if (!drag_started) {
                                                    drag_anchor_index.intValue = anchor_index
                                                    drag_last_index.intValue = anchor_index
                                                    drag_pre_selected.clear()
                                                    drag_pre_selected.addAll(latest_selected_ids.value)
                                                    drag_viewport_height.floatValue = size.height.toFloat()
                                                    drag_started = true
                                                    drag_selecting = true
                                                }
                                                drag_pointer_y.floatValue = change.position.y
                                                apply_contact_drag_selection(change.position.y)
                                            }
                                        } finally {
                                            drag_selecting = false
                                            drag_pointer_y.floatValue = -1f
                                            drag_anchor_index.intValue = -1
                                            drag_last_index.intValue = -1
                                            drag_pre_selected.clear()
                                        }
                                    }
                                },
                            contentPadding = PaddingValues(bottom = 96.dp + nav_bottom),
                        ) {
                            if (!filter_favorites && !recent_mode && query.isBlank()) {
                                val favorites = filtered.filter { it.is_favorite }
                                if (favorites.isNotEmpty()) {
                                    item(key = "favorites_strip") {
                                        Box(modifier = if (query.isBlank()) Modifier.animateItem() else Modifier) {
                                            favorites_strip(
                                                favorites = favorites,
                                                on_open_contact = on_open_contact,
                                            )
                                        }
                                    }
                                }
                            }
                            grouped.entries.forEachIndexed { group_index, (letter, group) ->
                                item(key = if (recent_mode) "recent_$letter" else "letter_$letter", contentType = "letter_header") {
                                    Box(modifier = if (query.isBlank()) Modifier.animateItem() else Modifier) {
                                        section_letter_header(letter = letter, is_first_group = group_index == 0)
                                    }
                                }
                                itemsIndexed(group, key = { _, c -> c.id }) { index, c ->
                                    Box(modifier = if (query.isBlank()) Modifier.animateItem() else Modifier) {
                                    ContactRow(
                                        contact = c,
                                        is_selected = c.id in ui_state.selected_ids,
                                        is_selecting = ui_state.is_selecting,
                                        is_pending = c.id in ui_state.pending_ids,
                                        is_first = index == 0,
                                        is_last = index == group.size - 1,
                                        on_click = {
                                            if (ui_state.is_selecting) {
                                                vm.toggle_selection(c.id)
                                            } else {
                                                on_open_contact(c.id)
                                            }
                                        },
                                        on_toggle_selection = { vm.toggle_selection(c.id) },
                                    )
                                    }
                                }
                            }
                        }
                        val contacts_drag = remember { org.astermail.android.ui.common.fast_scroll_drag_signal() }
                        val letter_by_key = remember(grouped, recent_mode) {
                            buildMap<Any, String> {
                                grouped.forEach { (letter, group) ->
                                    put(if (recent_mode) "recent_$letter" else "letter_$letter", letter)
                                    group.forEach { put(it.id, letter) }
                                }
                            }
                        }
                        val scrub_letter by remember(contacts_list_state, letter_by_key) {
                            derivedStateOf {
                                val first = contacts_list_state.layoutInfo.visibleItemsInfo
                                    .firstOrNull { letter_by_key.containsKey(it.key) }
                                first?.let { letter_by_key[it.key] }
                            }
                        }
                        org.astermail.android.ui.common.vertical_scroll_bar(
                            state = contacts_list_state,
                            modifier = Modifier.align(Alignment.TopEnd),
                            bottom_inset = 96.dp + nav_bottom,
                            drag_signal = contacts_drag,
                        )
                        contacts_scrub_bubble(
                            letter = scrub_letter,
                            visible = contacts_drag.dragging,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 40.dp, bottom = 96.dp + nav_bottom),
                        )
                    }
                }
            }
        }

        if (!ui_state.is_selecting && ui_state.tab != ContactsTab.TRASH) {
            val fab_label = stringResource(
                if (ui_state.tab == ContactsTab.GROUPS) R.string.new_group else R.string.new_contact,
            )
            ExtendedFloatingActionButton(
                onClick = {
                    if (ui_state.tab == ContactsTab.GROUPS) show_new_group = true else on_create_contact()
                },
                containerColor = colors.accent_blue,
                contentColor = colors.on_accent,
                shape = SquircleShape(AsterRadius.pill),
                icon = {
                    Icon(
                        imageVector = TablerIcons.Plus,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = fab_label,
                        fontWeight = FontWeight.Medium,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = nav_bottom)
                    .padding(AsterSpacing.lg)
                    .semantics { contentDescription = fab_label }
                    .testTag("fab_add_contact"),
            )
        }
    }

    if (show_sync_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_sync_confirm = false },
            title = stringResource(R.string.sync_contacts),
            message = stringResource(R.string.sync_contacts_description),
            confirm_label = stringResource(R.string.sync),
            cancel_label = stringResource(R.string.cancel),
            on_confirm = {
                show_sync_confirm = false
                permission_launcher.launch(Manifest.permission.READ_CONTACTS)
            },
        )
    }

    if (show_delete_confirm) {
        val count = ui_state.selected_ids.size
        val deleted_message = context.getString(R.string.contact_moved_to_trash)
        AsterAlertDialog(
            on_dismiss = { show_delete_confirm = false },
            title = context.resources.getQuantityString(R.plurals.delete_contacts_title, count, count),
            message = stringResource(R.string.contact_trash_confirm_message),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            is_busy = ui_state.is_bulk_working,
            on_confirm = {
                show_delete_confirm = false
                vm.delete_selection { ok ->
                    if (ok) org.astermail.android.ui.common.app_toast.show(deleted_message.toString())
                }
            },
        )
    }

    if (show_empty_trash_confirm) {
        AsterAlertDialog(
            on_dismiss = { show_empty_trash_confirm = false },
            title = stringResource(R.string.empty_trash),
            message = stringResource(R.string.empty_contacts_trash_confirm_message),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            is_busy = ui_state.is_bulk_working,
            on_confirm = {
                show_empty_trash_confirm = false
                vm.empty_trash()
            },
        )
    }

    pending_forever_delete?.let { target ->
        AsterAlertDialog(
            on_dismiss = { pending_forever_delete = null },
            title = stringResource(R.string.swipe_delete_forever),
            message = stringResource(
                R.string.alias_delete_confirm_message,
                target.name.ifBlank { target.email },
            ),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            is_busy = ui_state.is_bulk_working,
            on_confirm = {
                pending_forever_delete = null
                vm.delete_contact_forever(target.id)
            },
        )
    }

    if (show_new_group) {
        new_group_dialog(
            existing_count = ui_state.groups.size,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = {
                show_new_group = false
                new_group_for_selection = false
            },
            on_create = { name, color, icon ->
                val add_selection = new_group_for_selection
                show_new_group = false
                new_group_for_selection = false
                vm.create_group(name, color, icon, add_selection = add_selection) { ok ->
                    if (ok) {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.group_created).toString())
                    }
                }
            },
        )
    }

    editing_group?.let { group ->
        new_group_dialog(
            existing_count = ui_state.groups.size,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = { editing_group = null },
            on_create = { name, color, icon ->
                editing_group = null
                vm.update_group(group.id, name, color, icon) { ok ->
                    if (ok) {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.saved).toString())
                    }
                }
            },
            existing = group,
        )
    }

    if (show_group_picker) {
        group_picker_dialog(
            groups = ui_state.groups,
            count_of = { ui_state.group_member_count(it) },
            on_dismiss = { show_group_picker = false },
            on_pick = { group ->
                show_group_picker = false
                vm.add_selection_to_group(group.id) { added ->
                    if (added > 0) {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.added_to_group).toString())
                    }
                }
            },
            on_new_group = {
                show_group_picker = false
                new_group_for_selection = true
                show_new_group = true
            },
        )
    }

    merge_cluster?.let { cluster ->
        merge_dialog(
            cluster = cluster,
            is_busy = ui_state.is_bulk_working,
            on_dismiss = { merge_cluster = null },
            on_merge = { ordered ->
                merge_cluster = null
                vm.merge_duplicates(ordered) { ok ->
                    if (ok) {
                        org.astermail.android.ui.common.app_toast.show(context.getString(R.string.contacts_merged).toString())
                    }
                }
            },
        )
    }

    pending_group_delete?.let { group ->
        AsterAlertDialog(
            on_dismiss = { pending_group_delete = null },
            title = stringResource(R.string.delete_group),
            message = stringResource(R.string.delete_group_message),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                pending_group_delete = null
                vm.delete_group(group.id)
            },
        )
    }
}

private fun letter_of(name: String): String {
    val t = name.trim()
    if (t.isEmpty()) return "#"
    val first = t.first().uppercaseChar()
    return if (first.isLetter()) first.toString() else "#"
}

internal fun parse_group_color(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)

@Composable
internal fun group_glyph(
    color: String,
    icon: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val tint = parse_group_color(color, colors.accent_blue)
    val vector = org.astermail.android.ui.common.label_icon_or_null(icon)
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = modifier.size(16.dp),
        )
    } else {
        Box(
            modifier = modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(tint),
        )
    }
}

internal data class GroupAvatarColors(val background: Color, val icon: Color)

internal fun group_avatar_colors(tint: Color): GroupAvatarColors =
    GroupAvatarColors(background = tint, icon = Color.White)

@Composable
internal fun group_avatar(
    color: String,
    icon: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val tint = parse_group_color(color, colors.accent_blue)
    val avatar_colors = group_avatar_colors(tint)
    val vector = org.astermail.android.ui.common.label_icon_or_null(icon) ?: TablerIcons.Users
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatar_colors.background)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = avatar_colors.icon,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun duplicate_banner(count: Int, on_review: () -> Unit, on_dismiss: () -> Unit) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = inbox_card_horizontal_margin, vertical = AsterSpacing.sm)
            .clip(AsterShapes.island)
            .acrylic(colors, AsterShapes.island, island_surface_color(colors))
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Users,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.merge_contacts),
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = context.resources.getQuantityString(R.plurals.duplicate_contacts_found, count, count),
                color = colors.text_secondary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.review_duplicates),
            color = colors.on_accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(SquircleShape(AsterRadius.pill))
                .background(colors.accent_blue)
                .clickable(onClick = on_review)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
                .testTag("review_duplicates"),
        )
        AsterIconButton(
            icon = TablerIcons.X,
            content_description = stringResource(R.string.close),
            icon_size = 16,
            onClick = on_dismiss,
        )
    }
}

@Composable
private fun contacts_empty_state(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.xl)
            .padding(bottom = contacts_empty_state_bottom_offset),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(AsterSpacing.sm))
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = body,
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private val contacts_empty_state_bottom_offset = 72.dp

@Composable
private fun trash_pane(
    contacts: List<Contact>,
    query: String,
    bottom_inset: Dp,
    is_busy: Boolean,
    modifier: Modifier = Modifier,
    on_restore: (Contact) -> Unit,
    on_delete_forever: (Contact) -> Unit,
    on_empty_trash: () -> Unit,
) {
    val colors = AsterMaterial.colors
    if (contacts.isEmpty()) {
        contacts_empty_state(
            icon = if (query.isBlank()) TablerIcons.Trash else TablerIcons.Search,
            title = if (query.isBlank()) {
                stringResource(R.string.no_contacts_in_trash)
            } else {
                stringResource(R.string.no_contacts_match, query)
            },
            body = if (query.isBlank()) stringResource(R.string.contacts_in_trash_notice) else null,
            modifier = modifier.padding(bottom = bottom_inset),
        )
        return
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.contacts_in_trash_notice),
                color = colors.text_muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.empty_trash),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .clickable(enabled = !is_busy, onClick = on_empty_trash)
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
            )
        }
        AsterDivider()
        val trash_list_state = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(
            state = trash_list_state,
            modifier = Modifier.lazy_top_fade(trash_list_state),
            contentPadding = PaddingValues(top = AsterSpacing.sm, bottom = 88.dp + bottom_inset),
        ) {
            itemsIndexed(contacts, key = { _, c -> c.id }) { index, contact ->
                Row(
                    modifier = contact_card_modifier(
                        is_first = index == 0,
                        is_last = index == contacts.size - 1,
                    )
                        .defaultMinSize(minHeight = contact_row_min_height)
                        .padding(
                            horizontal = inbox_card_content_padding,
                            vertical = AsterSpacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(
                        avatar_url = contact.avatar_url,
                        email = contact.email,
                        name = contact.name,
                        profile_color = contact.profile_color,
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name.ifBlank { contact.email },
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.contact_trash_days_left,
                                org.astermail.android.contacts.contact_trash_days_left(contact.deleted_at),
                                org.astermail.android.contacts.contact_trash_days_left(contact.deleted_at),
                            ),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AsterIconButton(
                        icon = TablerIcons.ArrowBackUp.auto_mirrored(),
                        content_description = stringResource(R.string.restore),
                        onClick = { on_restore(contact) },
                        enabled = !is_busy,
                        tint = colors.text_muted,
                        icon_size = 20,
                    )
                    AsterIconButton(
                        icon = TablerIcons.Trash,
                        content_description = stringResource(R.string.swipe_delete_forever),
                        onClick = { on_delete_forever(contact) },
                        enabled = !is_busy,
                        tint = colors.danger,
                        icon_size = 20,
                    )
                }
            }
        }
    }
}

@Composable
private fun groups_pane(
    groups: List<ContactGroup>,
    query: String,
    bottom_inset: Dp,
    is_loading: Boolean,
    count_of: (ContactGroup) -> Int,
    modifier: Modifier = Modifier,
    on_open: (ContactGroup) -> Unit,
    on_edit: (ContactGroup) -> Unit,
    on_delete: (ContactGroup) -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    if (groups.isEmpty() && is_loading) {
        Box(modifier = modifier.fillMaxWidth().padding(bottom = bottom_inset), contentAlignment = Alignment.Center) {
            org.astermail.android.design.components.aster_spinner(color = colors.accent_blue)
        }
        return
    }
    if (groups.isEmpty()) {
        contacts_empty_state(
            icon = if (query.isBlank()) TablerIcons.Users else TablerIcons.Search,
            title = if (query.isBlank()) {
                stringResource(R.string.no_groups_yet)
            } else {
                stringResource(R.string.no_groups_match, query)
            },
            body = if (query.isBlank()) stringResource(R.string.no_groups_description) else null,
            modifier = modifier.padding(bottom = bottom_inset),
        )
        return
    }
    val groups_list_state = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = groups_list_state,
        modifier = modifier.lazy_top_fade(groups_list_state),
        contentPadding = PaddingValues(top = AsterSpacing.sm, bottom = 88.dp + bottom_inset),
    ) {
        itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
            val count = count_of(group)
            val members_label = context.resources.getQuantityString(
                R.plurals.group_members_plural,
                count,
                count,
            )
            val open_label = stringResource(R.string.open_group_description, group.name, members_label)
            Row(
                modifier = contact_card_modifier(
                    is_first = index == 0,
                    is_last = index == groups.size - 1,
                )
                    .clickable(role = Role.Button) { on_open(group) }
                    .semantics { contentDescription = open_label }
                    .testTag("contact_group_row_" + group.id)
                    .defaultMinSize(minHeight = contact_row_min_height)
                    .padding(
                        horizontal = inbox_card_content_padding,
                        vertical = AsterSpacing.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                group_avatar(
                    color = group.color,
                    icon = group.icon,
                    size = contact_row_avatar_size,
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { },
                ) {
                    Text(
                        text = group.name,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = members_label,
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
                AsterIconButton(
                    icon = TablerIcons.Pencil,
                    content_description = stringResource(R.string.edit_group),
                    icon_size = 18,
                    onClick = { on_edit(group) },
                )
                AsterIconButton(
                    icon = TablerIcons.Trash,
                    content_description = stringResource(R.string.delete_group),
                    icon_size = 18,
                    onClick = { on_delete(group) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun new_group_dialog(
    existing_count: Int,
    is_busy: Boolean,
    on_dismiss: () -> Unit,
    on_create: (String, String, String?) -> Unit,
    existing: ContactGroup? = null,
) {
    val colors = AsterMaterial.colors
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var color by remember(existing?.id) {
        mutableStateOf(
            existing?.color?.takeIf { it.isNotBlank() }
                ?: group_default_colors[existing_count % group_default_colors.size],
        )
    }
    var icon by remember(existing?.id) { mutableStateOf(existing?.icon) }
    val accent = parse_group_color(color, colors.accent_blue)
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(
            if (existing == null) R.string.new_group else R.string.edit_group,
        ),
        message = stringResource(R.string.no_groups_description),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .vertical_scroll_with_indicator(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    group_glyph(color = color, icon = icon)
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = name.trim().ifBlank { stringResource(R.string.group_name) },
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
                AsterTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.group_name),
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
                org.astermail.android.ui.common.color_swatch_row(
                    value = color,
                    on_pick = { color = it },
                    test_tag_prefix = "group_color_swatch",
                    custom_test_tag = "group_color_custom",
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.icon_label),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AsterSpacing.xs))
                org.astermail.android.ui.common.label_icon_grid(
                    selected_icon = icon,
                    accent_color = accent,
                    on_select = { icon = it },
                )
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(
                    if (existing == null) R.string.create else R.string.save,
                ),
                enabled = name.isNotBlank(),
                is_loading = is_busy,
                onClick = { on_create(name, color, icon) },
            )
        },
    )
}

@Composable
private fun group_picker_dialog(
    groups: List<ContactGroup>,
    count_of: (ContactGroup) -> Int,
    on_dismiss: () -> Unit,
    on_pick: (ContactGroup) -> Unit,
    on_new_group: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.add_to_group),
        message = if (groups.isEmpty()) stringResource(R.string.no_groups_description) else null,
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .vertical_scroll_with_indicator(),
            ) {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(AsterRadius.md))
                            .clickable { on_pick(group) }
                            .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group_glyph(color = group.color, icon = group.icon)
                        Spacer(Modifier.width(AsterSpacing.md))
                        Text(
                            text = group.name,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val count = count_of(group)
                        Text(
                            text = context.resources.getQuantityString(
                                R.plurals.group_members_plural,
                                count,
                                count,
                            ),
                            color = colors.text_muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.new_group),
                onClick = on_new_group,
            )
        },
    )
}

@Composable
private fun merge_dialog(
    cluster: DuplicateCluster,
    is_busy: Boolean,
    on_dismiss: () -> Unit,
    on_merge: (List<Contact>) -> Unit,
) {
    val colors = AsterMaterial.colors
    var primary_id by remember(cluster.key) { mutableStateOf(cluster.contacts.first().id) }
    AsterDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.merge_contacts),
        message = stringResource(R.string.merge_contacts_description),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .vertical_scroll_with_indicator(),
            ) {
                cluster.contacts.forEachIndexed { index, contact ->
                    val is_primary = contact.id == primary_id
                    if (index > 0) Spacer(Modifier.height(AsterSpacing.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(AsterRadius.lg))
                            .background(
                                if (is_primary) tonal_surface_color(colors, colors.accent_blue) else colors.bg_secondary,
                            )
                            .border(
                                1.5.dp,
                                if (is_primary) colors.accent_blue else Color.Transparent,
                                SquircleShape(AsterRadius.lg),
                            )
                            .clickable { primary_id = contact.id }
                            .padding(AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        merge_radio_dot(selected = is_primary)
                        Spacer(Modifier.width(AsterSpacing.md))
                        ContactAvatar(
                            avatar_url = contact.avatar_url,
                            email = contact.email,
                            name = contact.name,
                            profile_color = contact.profile_color,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(AsterSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.name.ifBlank { contact.email },
                                    color = colors.text_primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (is_primary) {
                                    Spacer(Modifier.width(AsterSpacing.sm))
                                    Text(
                                        text = stringResource(R.string.merge_keep_this),
                                        color = colors.accent_blue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(SquircleShape(AsterRadius.pill))
                                            .background(colors.accent_blue.copy(alpha = 0.14f))
                                            .padding(horizontal = AsterSpacing.sm, vertical = 2.dp),
                                    )
                                }
                            }
                            if (contact.email.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = contact.email,
                                    color = colors.text_secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            val details = listOf(contact.phone, contact.company)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            if (details.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = details,
                                    color = colors.text_muted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        footer = {
            AsterDialogOutlineButton(
                label = stringResource(R.string.cancel),
                onClick = on_dismiss,
            )
            AsterDialogPrimaryButton(
                label = stringResource(R.string.merge),
                is_loading = is_busy,
                onClick = {
                    val primary = cluster.contacts.first { it.id == primary_id }
                    on_merge(listOf(primary) + cluster.contacts.filter { it.id != primary_id })
                },
            )
        },
    )
}

@Composable
private fun merge_radio_dot(selected: Boolean) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent_blue else Color.Transparent)
            .border(1.5.dp, if (selected) colors.accent_blue else colors.text_tertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.on_accent),
            )
        }
    }
}

@Composable
private fun favorites_strip(
    favorites: List<Contact>,
    on_open_contact: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val side_inset = inbox_card_horizontal_margin + inbox_card_content_padding
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tab_favorites),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = side_inset,
                end = AsterSpacing.lg,
                top = AsterSpacing.md,
                bottom = AsterSpacing.xs,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = inbox_card_horizontal_margin, vertical = AsterSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
        ) {
            favorites.forEach { contact ->
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(SquircleShape(AsterRadius.xl))
                        .clickable { on_open_contact(contact.id) }
                        .padding(vertical = AsterSpacing.sm, horizontal = AsterSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ContactAvatar(
                        avatar_url = contact.avatar_url,
                        email = contact.email,
                        name = contact.name,
                        size = 56.dp,
                        profile_color = contact.profile_color,
                    )
                    Spacer(Modifier.height(AsterSpacing.sm))
                    Text(
                        text = contact.name.ifBlank { contact.email }.substringBefore(" "),
                        color = colors.text_secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal val contact_row_avatar_size = 44.dp
internal val contact_row_min_height = 72.dp

@Composable
internal fun section_letter_header(letter: String, is_first_group: Boolean) {
    val colors = AsterMaterial.colors
    Text(
        text = letter,
        color = colors.text_secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin + inbox_card_content_padding,
                end = AsterSpacing.lg,
                top = if (is_first_group) AsterSpacing.sm else AsterSpacing.lg,
                bottom = AsterSpacing.sm,
            ),
    )
}

@Composable
internal fun contact_card_modifier(
    is_first: Boolean,
    is_last: Boolean,
    is_selected: Boolean = false,
): Modifier {
    val colors = AsterMaterial.colors
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val surface = if (is_selected) inbox_card_selected_color(colors) else inbox_card_read_color(colors)
    return Modifier
        .fillMaxWidth()
        .padding(
            start = inbox_card_horizontal_margin,
            end = inbox_card_horizontal_margin,
            bottom = if (is_last) 0.dp else inbox_group_split,
        )
        .clip(shape)
        .acrylic_backdrop(colors)
        .drawBehind { drawRect(surface) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactRow(
    contact: Contact,
    is_selected: Boolean,
    is_selecting: Boolean,
    is_pending: Boolean,
    is_first: Boolean,
    is_last: Boolean,
    on_click: () -> Unit,
    on_toggle_selection: () -> Unit,
    avatar_toggles_selection: Boolean = true,
    long_click_label: String? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = contact_card_modifier(is_first, is_last, is_selected)
            .combinedClickable(
                onClick = on_click,
                onLongClick = on_toggle_selection,
                onLongClickLabel = long_click_label,
            )
            .defaultMinSize(minHeight = contact_row_min_height)
            .padding(
                horizontal = inbox_card_content_padding,
                vertical = AsterSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .then(
                    if (avatar_toggles_selection) {
                        Modifier
                            .clickable(onClick = on_toggle_selection)
                            .semantics { contentDescription = contact.name }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val avatar_reduce_motion = aster_reduce_motion()
            val flip_state = animateFloatAsState(
                targetValue = if (is_selected) 180f else 0f,
                animationSpec = if (avatar_reduce_motion) {
                    snap()
                } else {
                    tween(AsterMotionSpec.icon_ms, easing = AsterEasing.emphasized)
                },
                label = "contact_avatar_flip",
            )
            val check_state = animateFloatAsState(
                targetValue = if (is_selected) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (avatar_reduce_motion) AsterMotionSpec.reduced_fade_ms else AsterMotionSpec.icon_ms,
                    easing = if (avatar_reduce_motion) LinearEasing else AsterEasing.emphasized_decelerate,
                ),
                label = "contact_avatar_check",
            )
            val show_check by remember { derivedStateOf { flip_state.value > 90f } }
            Box(
                modifier = Modifier
                    .size(contact_row_avatar_size)
                    .graphicsLayer {
                        rotationY = flip_state.value
                        cameraDistance = 12f * density
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (show_check) {
                    Box(
                        modifier = Modifier
                            .size(contact_row_avatar_size)
                            .graphicsLayer { rotationY = 180f }
                            .clip(CircleShape)
                            .background(colors.accent_blue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TablerIcons.Check,
                            contentDescription = stringResource(R.string.selected),
                            tint = colors.on_accent,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    val t = check_state.value
                                    alpha = t
                                    val s = AsterMotionSpec.pop_from + (1f - AsterMotionSpec.pop_from) * t
                                    scaleX = s
                                    scaleY = s
                                },
                        )
                    }
                } else {
                    ContactAvatar(
                        avatar_url = contact.avatar_url,
                        email = contact.email,
                        name = contact.name,
                        size = contact_row_avatar_size,
                        profile_color = contact.profile_color,
                    )
                }
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name.ifBlank { contact.email },
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (contact.is_favorite) {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.tab_favorites),
                        tint = colors.star,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            val subtitle = listOf(contact.company, contact.email)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (is_pending) {
                Spacer(Modifier.height(2.dp))
                contact_pending_sync_label()
            }
        }
    }
}

@Composable
internal fun contact_pending_sync_label(modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = TablerIcons.Clock,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(AsterSpacing.xs))
        Text(
            text = stringResource(R.string.contacts_pending_sync),
            color = colors.text_secondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun bulk_progress_banner(
    done: Int,
    total: Int,
    label_res: Int = R.plurals.contacts_bulk_progress,
) {
    val colors = AsterMaterial.colors
    val safe_total = total.coerceAtLeast(1)
    val target = (done.toFloat() / safe_total).coerceIn(0f, 1f)
    val progress by animateFloatAsState(targetValue = target, label = "bulk_progress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        Text(
            text = pluralStringResource(label_res, total, done.coerceAtMost(total), total),
            color = colors.text_secondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(AsterSpacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.accent_blue.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(colors.accent_blue),
            )
        }
    }
}

@Composable
private fun contacts_scrub_bubble(letter: String?, visible: Boolean, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val reduce_motion = aster_reduce_motion()
    val shown = visible && letter != null
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduce_motion) snap() else tween(if (shown) 140 else 200),
        label = "contacts_scrub_bubble",
    )
    if (progress <= 0f) return
    var last_letter by remember { mutableStateOf(letter ?: "") }
    if (letter != null) last_letter = letter
    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                val scale = 0.9f + 0.1f * progress
                scaleX = scale
                scaleY = scale
            }
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(colors.accent_blue)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = last_letter,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
