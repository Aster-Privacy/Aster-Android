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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import compose.icons.tablericons.At
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Rocket
import org.astermail.android.design.components.AsterPlanTag
import org.astermail.android.design.components.aster_plan_kind
import org.astermail.android.ui.upgrade.UpgradeStore
import org.astermail.android.design.components.aster_plan_kind_of
import org.astermail.android.R
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import org.astermail.android.api.user.Badge
import org.astermail.android.api.user.UpdateBadgePreferencesRequest
import org.astermail.android.ui.common.current_user_avatar
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
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
    LaunchedEffect(Unit) { address_vm.load_eligibility() }

    var photo_uploading by remember { mutableStateOf(false) }
    var photo_failed by remember { mutableStateOf(false) }

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
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !photo_uploading) {
                            photo_failed = false
                            image_picker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                ) {
                    plan_ring(size = 112.dp, enabled = remember_has_paid_plan()) {
                        current_user_avatar(
                            account_store = vm.account_store,
                            size = 112.dp,
                            profile_picture_url = user?.profile_picture,
                        )
                    }
                }
                if (photo_uploading) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
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
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = email,
                subtitle = stringResource(R.string.primary_address),
                icon = TablerIcons.At,
                on_click = {
                    when {
                        address_state.eligibility_failed -> {
                            address_vm.load_eligibility()
                            show_address_locked = true
                        }
                        !address_state.eligibility_loaded -> address_vm.load_eligibility()
                        address_state.lock_reason == primary_address_reason_plan -> show_address_upsell = true
                        !address_state.eligible -> show_address_locked = true
                        else -> show_address_dialog = true
                    }
                },
            )
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
                    vm.load_profile()
                    vm.load_aliases()
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
    val shape = remember { RoundedCornerShape(10.dp) }
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.input_bg)
                    .border(1.dp, colors.input_border, RoundedCornerShape(12.dp))
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

private const val MAX_AVATAR_DIMENSION = 256

private fun decode_avatar_bitmap(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > MAX_AVATAR_DIMENSION) {
                val scale = MAX_AVATAR_DIMENSION.toFloat() / largest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_AVATAR_DIMENSION) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val largest = maxOf(decoded.width, decoded.height)
    if (largest <= MAX_AVATAR_DIMENSION) return decoded
    val scale = MAX_AVATAR_DIMENSION.toFloat() / largest
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
}

internal fun read_image_as_data_uri(context: Context, uri: Uri): String? {
    return try {
        val bitmap = decode_avatar_bitmap(context, uri) ?: return null
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val out = ByteArrayOutputStream()
        val compressed = bitmap.compress(format, 80, out)
        bitmap.recycle()
        if (!compressed) return null
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        "data:image/webp;base64,$b64"
    } catch (_: Throwable) {
        null
    }
}
