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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel

@Composable
private fun behavior_option(label: String, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.text_primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Box(
                modifier = Modifier.size(18.dp).background(colors.accent_blue, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("\u2713", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun behavior_toggle(title: String, subtitle: String?, checked: Boolean, on_change: (Boolean) -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = on_change,
                role = androidx.compose.ui.semantics.Role.Switch,
            )
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = colors.text_tertiary, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent_blue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.border_primary,
            ),
        )
    }
}

@Composable
fun BehaviorScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences

    LaunchedEffect(Unit) { vm.load_preferences() }

    var mark_read by remember { mutableStateOf("1_second") }
    var default_reply by remember { mutableStateOf("reply") }
    var block_remote_images by remember { mutableStateOf(true) }
    var block_tracking by remember { mutableStateOf(true) }
    var block_spy_pixels by remember { mutableStateOf(true) }
    var auto_save_recipients by remember { mutableStateOf(true) }
    var undo_send by remember { mutableStateOf(true) }
    var undo_send_secs by remember { mutableIntStateOf(10) }
    var confirm_delete by remember { mutableStateOf(false) }
    var confirm_archive by remember { mutableStateOf(false) }
    var confirm_spam by remember { mutableStateOf(false) }
    var haptic by remember { mutableStateOf(true) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var loaded_signature by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(prefs) {
        if (prefs != null) {
            val sig = prefs.hashCode()
            if (loaded_signature != sig && save_trigger == 0) {
                loaded_signature = sig
                mark_read = prefs.mark_as_read
                default_reply = prefs.default_reply_behavior
                block_remote_images = prefs.block_external_images
                block_tracking = prefs.block_tracking_links
                block_spy_pixels = prefs.block_tracking_pixels
                auto_save_recipients = prefs.auto_save_recent_recipients
                undo_send = prefs.undo_send_enabled
                undo_send_secs = prefs.undo_send_seconds
                confirm_delete = prefs.confirm_delete
                confirm_archive = prefs.confirm_archive
                confirm_spam = prefs.confirm_spam
                haptic = prefs.haptic_feedback
            }
        }
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                mark_as_read = mark_read,
                default_reply_behavior = default_reply,
                block_external_images = block_remote_images,
                block_tracking_links = block_tracking,
                block_tracking_pixels = block_spy_pixels,
                auto_save_recent_recipients = auto_save_recipients,
                undo_send_enabled = undo_send,
                undo_send_seconds = undo_send_secs,
                confirm_delete = confirm_delete,
                confirm_archive = confirm_archive,
                confirm_spam = confirm_spam,
                haptic_feedback = haptic,
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (loaded_signature == null || prefs == null) return@LaunchedEffect
        delay(500)
        save()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (save_trigger > 0 && prefs != null && loaded_signature != null) {
                save()
            }
        }
    }

    detail_scaffold(
        title = stringResource(R.string.settings_behavior),
        on_back = on_back,
    ) {
        if (state.is_loading && prefs == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            section_label(stringResource(R.string.mark_as_read))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "immediate" to stringResource(R.string.immediately),
                    "1_second" to stringResource(R.string.after_1_second),
                    "3_seconds" to stringResource(R.string.after_3_seconds),
                    "never" to stringResource(R.string.never_manual),
                ).forEachIndexed { i, (id, label) ->
                    behavior_option(label, mark_read == id) { mark_read = id; save_trigger++ }
                    if (i < 3) AsterDivider(modifier = Modifier)
                }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.default_reply))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_option(stringResource(R.string.reply_to_sender), default_reply == "reply") { default_reply = "reply"; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_option(stringResource(R.string.reply_to_all), default_reply == "reply_all") { default_reply = "reply_all"; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.images_tracking))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(stringResource(R.string.block_remote_images), stringResource(R.string.block_remote_images_subtitle), block_remote_images) { block_remote_images = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(stringResource(R.string.block_tracking_pixels), null, block_spy_pixels) { block_spy_pixels = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(stringResource(R.string.block_tracking_links), null, block_tracking) { block_tracking = it; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.sending))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(stringResource(R.string.auto_save_recipients), null, auto_save_recipients) { auto_save_recipients = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(stringResource(R.string.undo_send), stringResource(R.string.undo_send_subtitle, undo_send_secs), undo_send) { undo_send = it; save_trigger++ }
                androidx.compose.animation.AnimatedVisibility(
                    visible = undo_send,
                    enter = androidx.compose.animation.expandVertically(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)),
                    exit = androidx.compose.animation.shrinkVertically(
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                    ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 140)),
                ) {
                    androidx.compose.foundation.layout.Column {
                        AsterDivider(modifier = Modifier)
                        listOf(3, 5, 10, 15, 20, 30).forEachIndexed { i, secs ->
                            behavior_option(stringResource(R.string.undo_send_delay_seconds, secs), undo_send_secs == secs) { undo_send_secs = secs; save_trigger++ }
                            if (i < 5) AsterDivider(modifier = Modifier)
                        }
                    }
                }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.confirmations))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(stringResource(R.string.confirm_before_delete), null, confirm_delete) { confirm_delete = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(stringResource(R.string.confirm_before_archive), null, confirm_archive) { confirm_archive = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(stringResource(R.string.confirm_before_spam), null, confirm_spam) { confirm_spam = it; save_trigger++ }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.haptics))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(stringResource(R.string.haptic_feedback), stringResource(R.string.haptic_feedback_subtitle), haptic) { haptic = it; save_trigger++ }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
