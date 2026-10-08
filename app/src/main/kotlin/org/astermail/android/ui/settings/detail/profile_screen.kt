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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import compose.icons.tablericons.At
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Rocket
import org.astermail.android.design.components.AsterPlanTag
import org.astermail.android.design.components.aster_plan_kind
import org.astermail.android.ui.upgrade.UpgradeStore
import org.astermail.android.design.components.aster_plan_kind_of
import org.astermail.android.R
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Pencil
import org.astermail.android.api.user.Badge
import org.astermail.android.api.user.UpdateBadgePreferencesRequest
import org.astermail.android.ui.common.current_user_avatar
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.design.components.aster_menu
import org.astermail.android.settings.SaveStatus
import org.astermail.android.settings.PrimaryAddressViewModel
import org.astermail.android.settings.primary_address_reason_plan
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

@Composable
fun ProfileScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.load_profile()
        vm.load_aliases()
        vm.load_badges()
        vm.load_subscription(force = false)
    }

    LaunchedEffect(state.action_result) {
        val msg = state.action_result ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        vm.clear_action_result()
    }

    val live_account by vm.account_store.current_account.collectAsStateWithLifecycle(
        initialValue = vm.account_store.get_current()
    )
    val user = state.user
    val email = user?.email ?: live_account?.email ?: ""
    var display_name by remember { mutableStateOf(live_account?.display_name ?: user?.display_name ?: "") }
    LaunchedEffect(live_account?.display_name, user?.display_name) {
        val incoming = user?.display_name ?: live_account?.display_name ?: return@LaunchedEffect
        if (incoming != display_name) display_name = incoming
    }

    val address_vm: PrimaryAddressViewModel = hiltViewModel()
    val address_state by address_vm.state.collectAsStateWithLifecycle()
    var show_address_dialog by remember { mutableStateOf(false) }
    var show_address_locked by remember { mutableStateOf(false) }
    var show_address_upsell by remember { mutableStateOf(false) }
    var address_menu_open by remember { mutableStateOf(false) }
    val copy_address_label = stringResource(R.string.copy_address)
    LaunchedEffect(Unit) { address_vm.load_eligibility() }

    var photo_uploading by remember { mutableStateOf(false) }
    var photo_removing by remember { mutableStateOf(false) }
    var photo_failed by remember { mutableStateOf(false) }
    var show_photo_sheet by rememberSaveable { mutableStateOf(false) }
    val photo_busy = photo_uploading || photo_removing

    val image_picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            photo_uploading = true
            photo_failed = false
            val data_uri = withContext(Dispatchers.IO) { read_image_as_data_uri(context, uri) }
            val success = if (data_uri != null) vm.update_profile_picture(data_uri) else false
            photo_uploading = false
            photo_failed = !success
        }
    }

    LaunchedEffect(state.save_status) {
        if (state.save_status == SaveStatus.SAVED) {
            kotlinx.coroutines.delay(1500)
            vm.reset_save_status()
        }
    }

    val name_save_label = when (state.save_status) {
        SaveStatus.SAVING -> stringResource(R.string.saving)
        SaveStatus.SAVED -> stringResource(R.string.saved)
        SaveStatus.ERROR -> stringResource(R.string.error_try_again)
        else -> stringResource(R.string.save)
    }
    val is_name_saving = state.save_status == SaveStatus.SAVING

    val plan_code = state.subscription?.plan?.code
    val plan_kind = remember(plan_code) { aster_plan_kind_of(plan_code) }
    val plan_label = state.subscription?.let {
        it.effective_plan_name?.takeIf { name -> name.isNotBlank() } ?: stringResource(R.string.plan_free)
    } ?: ""
    val member_since = remember(user?.created_at) { format_settings_date(user?.created_at) }
    val is_supernova = plan_kind == aster_plan_kind.supernova
    val resolved_name = listOfNotNull(
        display_name.takeIf { it.isNotBlank() },
        live_account?.display_name?.takeIf { it.isNotBlank() },
        user?.display_name?.takeIf { it.isNotBlank() },
        user?.username?.takeIf { it.isNotBlank() },
        live_account?.email?.substringBefore("@")?.takeIf { it.isNotBlank() },
    ).firstOrNull() ?: stringResource(R.string.your_name)

    if (show_photo_sheet) {
        profile_picture_sheet(
            account_store = vm.account_store,
            picture = user?.profile_picture,
            display_name = resolved_name,
            email = email,
            has_saved_picture = !user?.profile_picture.isNullOrBlank(),
            uploading = photo_uploading,
            removing = photo_removing,
            failed = photo_failed,
            on_dismiss = { show_photo_sheet = false },
            on_upload = {
                photo_failed = false
                image_picker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            on_remove = {
                if (!photo_busy) {
                    scope.launch {
                        photo_removing = true
                        photo_failed = false
                        val success = vm.update_profile_picture(null)
                        photo_removing = false
                        photo_failed = !success
                    }
                }
            },
            on_choose_image = { bytes ->
                scope.async {
                    photo_uploading = true
                    photo_failed = false
                    val data_uri = withContext(Dispatchers.Default) { image_bytes_as_data_uri(bytes) }
                    val success = if (data_uri != null) vm.update_profile_picture(data_uri) else false
                    photo_uploading = false
                    photo_failed = !success
                    success
                }.await()
            },
        )
    }

    val profile_loaded = user != null &&
        (state.subscription != null || state.subscription_load_failed) &&
        state.badge_preferences != null &&
        state.badges_loaded
    val load_settled = remember_load_settled(!profile_loaded)

    detail_scaffold(title = stringResource(R.string.profile), on_back = on_back) {
        if (!profile_loaded && !load_settled) {
            profile_pulse_skeleton()
            return@detail_scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val change_photo_label = stringResource(R.string.change_photo)
            plan_ring(size = 112.dp, enabled = remember_has_paid_plan()) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = change_photo_label,
                        ) {
                            photo_failed = false
                            show_photo_sheet = true
                        }
                        .testTag("profile_avatar"),
                    contentAlignment = Alignment.Center,
                ) {
                    current_user_avatar(
                        account_store = vm.account_store,
                        size = 112.dp,
                        profile_picture_url = user?.profile_picture,
                    )
                    if (photo_busy) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .background(colors.bg_primary.copy(alpha = 0.60f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent_blue,
                            )
                        }
                    }
                }
            }
            v_gap(AsterSpacing.lg)
            Text(
                text = resolved_name,
                color = colors.text_primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            v_gap(AsterSpacing.xs)
            Text(
                text = email,
                color = colors.text_secondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { copy_address(context, email) },
                        onLongClickLabel = copy_address_label,
                    )
                    .padding(horizontal = AsterSpacing.sm, vertical = 2.dp),
            )
            v_gap(AsterSpacing.md)
            Box(
                modifier = Modifier.height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (plan_kind != null) {
                    AsterPlanTag(
                        text = plan_label,
                        plan = plan_kind,
                        font_size = 12.sp,
                        horizontal_padding = 12.dp,
                        vertical_padding = 5.dp,
                    )
                } else if (state.subscription != null) {
                    org.astermail.android.design.components.AsterCompactButton(
                        label = stringResource(R.string.upgrade),
                        onClick = { UpgradeStore.show_feature(null, null) },
                        modifier = Modifier.widthIn(min = 160.dp),
                    )
                }
            }
            if (photo_failed) {
                v_gap(AsterSpacing.sm)
                Text(
                    text = stringResource(R.string.error_try_again),
                    color = colors.danger,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.account))
        val lock_message = address_change_lock_message(
            reason = address_state.lock_reason,
            eligibility_failed = address_state.eligibility_failed,
            next_change_available_at = address_state.next_change_available_at,
        )
        val address_plan_locked = address_state.eligibility_loaded &&
            address_state.lock_reason == primary_address_reason_plan
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Box {
                val primary_address_label = stringResource(R.string.primary_address)
                val address_cadence_label = stringResource(R.string.address_change_once_title)
                detail_row(
                    title = email,
                    subtitle = if (address_state.eligibility_loaded && address_state.eligible) {
                        "$primary_address_label · $address_cadence_label"
                    } else {
                        primary_address_label
                    },
                    icon = TablerIcons.At,
                    icon_tint = if (address_plan_locked) colors.text_tertiary else null,
                    muted = address_plan_locked,
                    trailing = if (address_plan_locked) {
                        {
                            Icon(
                                imageVector = TablerIcons.Lock,
                                contentDescription = null,
                                tint = colors.text_tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    on_click = { address_menu_open = true },
                )
                aster_menu(
                    expanded = address_menu_open,
                    on_dismiss = { address_menu_open = false },
                ) {
                    aster_menu_item(
                        label = copy_address_label,
                        icon = TablerIcons.Copy,
                        test_tag = "primary_address_copy",
                        on_click = {
                            address_menu_open = false
                            copy_address(context, email)
                        },
                    )
                    aster_menu_item(
                        label = stringResource(R.string.change_address),
                        icon = if (address_plan_locked) TablerIcons.Lock else TablerIcons.Pencil,
                        test_tag = "primary_address_change",
                        on_click = {
                            address_menu_open = false
                            when {
                                address_state.eligibility_failed -> {
                                    address_vm.load_eligibility()
                                    show_address_locked = true
                                }
                                !address_state.eligibility_loaded -> address_vm.load_eligibility()
                                address_state.lock_reason == primary_address_reason_plan ->
                                    show_address_upsell = true
                                !address_state.eligible -> show_address_locked = true
                                else -> show_address_dialog = true
                            }
                        },
                    )
                }
            }
            settings_row_gap()
            detail_row(
                title = stringResource(R.string.current_plan),
                subtitle = plan_label,
                icon = TablerIcons.Rocket,
                on_click = { on_open("billing") },
            )
            if (member_since.isNotEmpty()) {
                settings_row_gap()
                detail_row(
                    title = stringResource(R.string.member_since),
                    subtitle = member_since,
                    icon = TablerIcons.Calendar,
                )
            }
        }
        val address_upsell_message = stringResource(R.string.address_change_locked_plan)
        if (show_address_upsell) {
            AsterAlertDialog(
                on_dismiss = { show_address_upsell = false },
                title = stringResource(R.string.address_change_title),
                message = stringResource(R.string.address_change_locked_plan),
                confirm_label = stringResource(R.string.upgrade),
                cancel_label = stringResource(R.string.cancel),
                on_confirm = {
                    show_address_upsell = false
                    UpgradeStore.show_feature("supernova", address_upsell_message)
                },
            )
        }
        if (show_address_locked) {
            AsterAlertDialog(
                on_dismiss = { show_address_locked = false },
                title = stringResource(R.string.address_change_title),
                message = lock_message ?: stringResource(R.string.address_change_not_available),
                confirm_label = stringResource(R.string.got_it),
                on_confirm = { show_address_locked = false },
            )
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.display_name))
        AsterTextField(
            value = display_name,
            onValueChange = {
                display_name = sanitize_display_name(it)
                vm.reset_save_status()
            },
            placeholder = stringResource(R.string.your_name),
            enabled = !is_name_saving,
        )
        v_gap(AsterSpacing.sm)
        AsterButton(
            label = name_save_label,
            onClick = { vm.update_display_name(display_name) },
            enabled = display_name.isNotBlank() && !is_name_saving && state.save_status != SaveStatus.SAVED,
            is_loading = is_name_saving,
        )
        if (show_address_dialog) {
            change_primary_address_dialog(
                current_address = email,
                display_name = live_account?.display_name ?: user?.display_name ?: "",
                alias_addresses = state.aliases
                    .filter { it.is_enabled && !it.decryption_failed && !it.is_retained_primary }
                    .map { it.address },
                on_dismiss = {
                    show_address_dialog = false
                    address_vm.reset()
                },
                on_changed = {
                    vm.load_profile(force = true)
                    vm.load_aliases(force = true)
                    address_vm.load_eligibility()
                },
                vm = address_vm,
            )
        }
        if (state.badges.isNotEmpty()) {
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.badges))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                items(state.badges) { badge ->
                    badge_chip(badge)
                }
            }
            val badge_prefs = state.badge_preferences
            if (badge_prefs != null) {
                v_gap(AsterSpacing.md)
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    active_badge_row(
                        badges = state.badges,
                        active_slug = badge_prefs.active_badge_slug,
                        on_select = { slug ->
                            vm.update_badge_preferences(
                                UpdateBadgePreferencesRequest(
                                    active_badge_slug = slug,
                                    clear_active_badge = if (slug == null) true else null,
                                ),
                            )
                        },
                    )
                    if (badge_prefs.active_badge_slug != null) {
                        badge_toggle_row(
                            title = stringResource(R.string.badge_show_on_profile),
                            subtitle = stringResource(R.string.badge_show_on_profile_description),
                            checked = badge_prefs.show_badge_profile,
                            on_change = {
                                vm.update_badge_preferences(
                                    UpdateBadgePreferencesRequest(show_badge_profile = it),
                                )
                            },
                        )
                        badge_toggle_row(
                            title = stringResource(R.string.badge_show_in_signature),
                            subtitle = stringResource(R.string.badge_show_in_signature_description),
                            checked = badge_prefs.show_badge_signature,
                            on_change = {
                                vm.update_badge_preferences(
                                    UpdateBadgePreferencesRequest(show_badge_signature = it),
                                )
                            },
                        )
                    }
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun profile_pulse_skeleton() {
    val tone = shimmer_state()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(112.dp).shimmer(tone, CircleShape))
            v_gap(AsterSpacing.lg)
            skeleton_block(tone, 184.dp, 26.dp, corner = 8.dp)
            v_gap(AsterSpacing.xs)
            skeleton_block(tone, 212.dp, 14.dp)
            v_gap(AsterSpacing.md)
            Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                skeleton_block(tone, 132.dp, 26.dp, corner = 13.dp)
            }
        }
        v_gap(AsterSpacing.lg)
        skeleton_section_label()
        skeleton_card_list(rows = 3, leading_circle = true)
        v_gap(AsterSpacing.lg)
        skeleton_section_label()
        skeleton_block_fill(tone, 52.dp, corner = 12.dp)
        v_gap(AsterSpacing.sm)
        skeleton_block_fill(tone, 48.dp, corner = 12.dp)
    }
}

@Composable
private fun badge_chip(badge: Badge) {
    val colors = AsterMaterial.colors
    val visual = remember(badge.slug) { badge_visual_for(badge.slug) }
    val shape = remember { RoundedCornerShape(AsterRadius.field) }
    val background = org.astermail.android.ui.mail.chip_background(
        visual.color,
        colors.bg_primary,
        colors.is_dark,
    )
    val content = org.astermail.android.ui.mail.chip_content(visual.color, background, colors.is_dark)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(background, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = badge.display_name,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        val order = badge.find_order
        if (order != null && order >= 1) {
            Text(
                text = format_find_order(order),
                color = content.copy(alpha = 0.70f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun format_find_order(order: Int): String =
    "#" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.getDefault()).format(order)

@Composable
private fun active_badge_row(
    badges: List<Badge>,
    active_slug: String?,
    on_select: (String?) -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    val active = remember(active_slug, badges) { badges.firstOrNull { it.slug == active_slug } }
    val none_label = stringResource(R.string.badge_none)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.badge_active),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.badges_description),
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Box {
            Row(
                modifier = Modifier
                    .clip(AsterShapes.control)
                    .background(field_surface_color(colors), AsterShapes.control)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (active != null) {
                    val visual = badge_visual_for(active.slug)
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.color,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = active?.display_name ?: none_label,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(15.dp),
                )
            }
            aster_menu(expanded = expanded, on_dismiss = { expanded = false }) {
                aster_menu_item(
                    label = none_label,
                    selected = active_slug == null,
                    on_click = {
                        expanded = false
                        on_select(null)
                    },
                )
                badges.forEach { badge ->
                    val visual = badge_visual_for(badge.slug)
                    val order = badge.find_order
                    aster_menu_item(
                        label = if (order != null && order >= 1) {
                            badge.display_name + "  " + format_find_order(order)
                        } else {
                            badge.display_name
                        },
                        icon = visual.icon,
                        selected = badge.slug == active_slug,
                        on_click = {
                            expanded = false
                            on_select(badge.slug)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun badge_toggle_row(
    title: String,
    subtitle: String,
    checked: Boolean,
    on_change: (Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = colors.text_tertiary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(AsterSpacing.md))
        AsterSwitch(checked = checked, onCheckedChange = on_change)
    }
}

private const val MAX_DISPLAY_NAME_LENGTH = 100

private fun sanitize_display_name(value: String): String =
    value.filter { it != '<' && it != '>' && it != '\u0000' }.take(MAX_DISPLAY_NAME_LENGTH)

internal const val AVATAR_OUTPUT_SIZE = 512
internal const val AVATAR_QUALITY = 88
internal const val AVATAR_MIN_QUALITY = 48
private const val AVATAR_QUALITY_STEP = 10
internal const val AVATAR_TARGET_DATA_URI_CHARS = 400_000
internal const val AVATAR_MAX_DATA_URI_CHARS = 512_000
private const val AVATAR_MAX_SOURCE_BYTES = 64L * 1024 * 1024
private const val AVATAR_DATA_URI_PREFIX = "data:image/webp;base64,"

internal fun avatar_sample_size(width: Int, height: Int, target: Int = AVATAR_OUTPUT_SIZE): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    val shortest = minOf(width, height)
    var sample = 1
    while (shortest / (sample * 2) >= target) {
        sample *= 2
    }
    return sample
}

internal fun avatar_decode_size(width: Int, height: Int, target: Int = AVATAR_OUTPUT_SIZE): IntArray {
    val shortest = minOf(width, height)
    if (width <= 0 || height <= 0 || shortest <= target) return intArrayOf(width, height)
    val scale = target.toDouble() / shortest
    return intArrayOf(
        kotlin.math.ceil(width * scale).toInt().coerceIn(1, width),
        kotlin.math.ceil(height * scale).toInt().coerceIn(1, height),
    )
}

internal fun avatar_square_crop(width: Int, height: Int): IntArray {
    val side = minOf(width, height).coerceAtLeast(1)
    return intArrayOf(((width - side) / 2).coerceAtLeast(0), ((height - side) / 2).coerceAtLeast(0), side)
}

internal fun avatar_output_size(side: Int, target: Int = AVATAR_OUTPUT_SIZE): Int =
    side.coerceIn(1, target)

internal fun avatar_next_quality(quality: Int): Int? {
    if (quality <= AVATAR_MIN_QUALITY) return null
    return (quality - AVATAR_QUALITY_STEP).coerceAtLeast(AVATAR_MIN_QUALITY)
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
private fun decode_avatar_source(source: ImageDecoder.Source): Bitmap =
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val (target_width, target_height) = avatar_decode_size(width, height)
        if (target_width != width || target_height != height) {
            decoder.setTargetSize(target_width, target_height)
        }
    }

private fun exif_orientation(bytes: ByteArray): Int = try {
    android.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(
        android.media.ExifInterface.TAG_ORIENTATION,
        android.media.ExifInterface.ORIENTATION_NORMAL,
    )
} catch (_: Throwable) {
    android.media.ExifInterface.ORIENTATION_NORMAL
}

private fun apply_exif_orientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = android.graphics.Matrix()
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private fun decode_avatar_bytes_legacy(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = avatar_sample_size(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return apply_exif_orientation(decoded, exif_orientation(bytes))
}

private fun decode_avatar_bitmap(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return decode_avatar_source(ImageDecoder.createSource(context.contentResolver, uri))
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > AVATAR_MAX_SOURCE_BYTES) return null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: return null
    return decode_avatar_bytes_legacy(bytes)
}

private fun decode_avatar_bytes(bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty()) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return decode_avatar_source(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
    }
    return decode_avatar_bytes_legacy(bytes)
}

private fun square_avatar_bitmap(source: Bitmap): Bitmap {
    val (left, top, side) = avatar_square_crop(source.width, source.height)
    val size = avatar_output_size(side)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint(
        android.graphics.Paint.FILTER_BITMAP_FLAG or
            android.graphics.Paint.ANTI_ALIAS_FLAG or
            android.graphics.Paint.DITHER_FLAG,
    )
    canvas.drawBitmap(
        source,
        android.graphics.Rect(left, top, left + side, top + side),
        android.graphics.Rect(0, 0, size, size),
        paint,
    )
    return output
}

private fun encode_avatar_data_uri(bitmap: Bitmap): String? {
    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    }
    var quality: Int? = AVATAR_QUALITY
    var smallest: String? = null
    while (quality != null) {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(format, quality, out)) return null
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        val data_uri = AVATAR_DATA_URI_PREFIX + b64
        if (data_uri.length <= AVATAR_TARGET_DATA_URI_CHARS) return data_uri
        smallest = data_uri
        quality = avatar_next_quality(quality)
    }
    return smallest?.takeIf { it.length <= AVATAR_MAX_DATA_URI_CHARS }
}

private fun avatar_data_uri_from(decoded: Bitmap?): String? {
    decoded ?: return null
    var square: Bitmap? = null
    return try {
        square = square_avatar_bitmap(decoded)
        encode_avatar_data_uri(square)
    } finally {
        square?.recycle()
        decoded.recycle()
    }
}

internal fun read_image_as_data_uri(context: Context, uri: Uri): String? {
    return try {
        avatar_data_uri_from(decode_avatar_bitmap(context, uri))
    } catch (_: Throwable) {
        null
    }
}

internal fun image_bytes_as_data_uri(bytes: ByteArray): String? {
    return try {
        avatar_data_uri_from(decode_avatar_bytes(bytes))
    } catch (_: Throwable) {
        null
    }
}
