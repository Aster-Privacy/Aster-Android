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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.astermail.android.R
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.notifications.MailPollingWorker
import org.astermail.android.ui.common.picker_theme_res
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.design.components.UpgradeGate
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

@Composable
private fun switch_row(title: String, subtitle: String?, checked: Boolean, on_change: (Boolean) -> Unit) {
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
            Text(text = title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, color = colors.text_tertiary, fontSize = 13.sp)
            }
        }
        AsterSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
fun NotificationsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences
    val context = LocalContext.current
    val time_picker_theme = picker_theme_res()
    val quiet_hours_locked = plan_vm.is_feature_locked("has_quiet_hours") && !plan_state.is_loading

    LaunchedEffect(Unit) { vm.load_preferences() }
    LaunchedEffect(Unit) { vm.load_product_updates() }

    val prefs_seeded = prefs != null && state.preferences_authoritative
    var push by remember(prefs_seeded) { mutableStateOf(prefs?.push_notifications ?: true) }
    var sound by remember(prefs_seeded) { mutableStateOf(prefs?.sound ?: true) }
    var vibrate by remember(prefs_seeded) { mutableStateOf(prefs?.vibrate ?: true) }
    var new_email by remember(prefs_seeded) { mutableStateOf(prefs?.notify_new_email ?: true) }
    var replies by remember(prefs_seeded) { mutableStateOf(prefs?.notify_replies ?: true) }
    var mentions by remember(prefs_seeded) { mutableStateOf(prefs?.notify_mentions ?: true) }
    var quiet_hours by remember(prefs_seeded) { mutableStateOf(prefs?.quiet_hours_enabled ?: false) }
    var quiet_hours_start by remember(prefs_seeded) { mutableStateOf(prefs?.quiet_hours_start?.takeIf { it.isNotBlank() } ?: "22:00") }
    var quiet_hours_end by remember(prefs_seeded) { mutableStateOf(prefs?.quiet_hours_end?.takeIf { it.isNotBlank() } ?: "07:00") }
    var save_trigger by remember { mutableIntStateOf(0) }
    var prefs_loaded by remember { mutableStateOf(false) }

    val is_battery_exempt = remember { mutableStateOf(false) }
    fun refresh_battery_exempt() {
        is_battery_exempt.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }
    LaunchedEffect(Unit) { refresh_battery_exempt() }
    val lifecycle_owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh_battery_exempt()
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(prefs, state.preferences_authoritative) {
        if (prefs != null && state.preferences_authoritative && !prefs_loaded) {
            prefs_loaded = true
            push = prefs.push_notifications
            sound = prefs.sound
            vibrate = prefs.vibrate
            new_email = prefs.notify_new_email
            replies = prefs.notify_replies
            mentions = prefs.notify_mentions
            quiet_hours = prefs.quiet_hours_enabled
            quiet_hours_start = prefs.quiet_hours_start.takeIf { it.isNotBlank() } ?: "22:00"
            quiet_hours_end = prefs.quiet_hours_end.takeIf { it.isNotBlank() } ?: "07:00"
            MailPollingWorker.set_quiet_hours(context, quiet_hours, quiet_hours_start, quiet_hours_end)
            MailPollingWorker.set_notify_new_email(context, new_email)
        }
    }

    fun save() {
        val base = prefs ?: return
        vm.save_preferences(
            base.copy(
                push_notifications = push,
                sound = sound,
                vibrate = vibrate,
                notify_new_email = new_email,
                notify_replies = replies,
                notify_mentions = mentions,
                quiet_hours_enabled = quiet_hours,
                quiet_hours_start = quiet_hours_start,
                quiet_hours_end = quiet_hours_end,
            ),
        )
        MailPollingWorker.set_quiet_hours(context, quiet_hours, quiet_hours_start, quiet_hours_end)
    }

    fun show_time_picker(initial: String, on_pick: (String) -> Unit) {
        val parts = initial.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 22
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        android.app.TimePickerDialog(
            context,
            time_picker_theme,
            { _, hour, minute -> on_pick(String.format(java.util.Locale.US, "%02d:%02d", hour, minute)) },
            h, m, android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (!prefs_loaded || prefs == null) return@LaunchedEffect
        delay(500)
        save()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (save_trigger > 0 && prefs != null && prefs_loaded) {
                save()
            }
        }
    }

    detail_scaffold(
        title = stringResource(R.string.notifications),
        on_back = on_back,
    ) {
        if (prefs == null || !state.preferences_authoritative) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
            }
        } else {
            section_label(stringResource(R.string.channels))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                switch_row(stringResource(R.string.push_notifications), stringResource(R.string.push_notifications_subtitle), push) { push = it; save_trigger++; MailPollingWorker.set_push_enabled(context, it) }
                AsterDivider(modifier = Modifier)
                switch_row(stringResource(R.string.sound), stringResource(R.string.sound_subtitle), sound) { sound = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                switch_row(stringResource(R.string.vibrate), null, vibrate) { vibrate = it; save_trigger++ }
            }
            if (!is_battery_exempt.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                v_gap(AsterSpacing.lg)
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    detail_row(
                        title = stringResource(R.string.notif_battery_opt_title),
                        subtitle = stringResource(R.string.notif_battery_opt_subtitle),
                        on_click = {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(intent)
                        },
                    )
                }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.events))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                switch_row(stringResource(R.string.new_emails), null, new_email) { new_email = it; save_trigger++; MailPollingWorker.set_notify_new_email(context, it) }
                AsterDivider(modifier = Modifier)
                switch_row(stringResource(R.string.replies), null, replies) { replies = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                switch_row(stringResource(R.string.mentions), null, mentions) { mentions = it; save_trigger++ }
                if (state.product_updates_available) {
                    AsterDivider(modifier = Modifier)
                    switch_row(
                        stringResource(R.string.product_updates),
                        stringResource(R.string.product_updates_subtitle),
                        state.product_updates,
                    ) { vm.set_product_updates(it) }
                }
            }
            if (prefs.inbox_categories_enabled) {
                val muted_categories = prefs.muted_notification_categories
                val rows = buildList {
                    org.astermail.android.mail.BUILTIN_CATEGORIES
                        .filter { it.removable && it.id in prefs.enabled_categories }
                        .forEach { add(it.id to context.getString(it.label_res)) }
                    prefs.custom_categories
                        .filter { it.enabled && it.id.isNotBlank() && it.name.isNotBlank() }
                        .forEach { add(it.id to it.name) }
                }
                v_gap(AsterSpacing.lg)
                section_label(stringResource(R.string.muted_categories))
                Text(
                    text = stringResource(R.string.muted_categories_description),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(
                        start = AsterSpacing.lg,
                        end = AsterSpacing.lg,
                        bottom = AsterSpacing.sm,
                    ),
                )
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.muted_categories_empty),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = AsterSpacing.lg),
                    )
                } else {
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        rows.forEachIndexed { index, (id, label) ->
                            switch_row(label, null, id in muted_categories) {
                                vm.toggle_category_notifications(id)
                            }
                            if (index < rows.size - 1) AsterDivider(modifier = Modifier)
                        }
                    }
                }
            }
            v_gap(AsterSpacing.lg)
            section_label(stringResource(R.string.quiet_hours))
            if (quiet_hours_locked) {
                UpgradeGate(
                    title = stringResource(R.string.quiet_hours),
                    description = stringResource(R.string.quiet_hours_paywall_description),
                    plan_name = "Star",
                    on_upgrade = { on_open("billing") },
                    requires_label = stringResource(R.string.requires_plan, "Star"),
                    button_label = stringResource(R.string.upgrade),
                )
            } else {
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    switch_row(stringResource(R.string.quiet_hours), stringResource(R.string.quiet_hours_subtitle_range, quiet_hours_start, quiet_hours_end), quiet_hours) { quiet_hours = it; save_trigger++ }
                    if (quiet_hours) {
                        AsterDivider(modifier = Modifier)
                        detail_row(
                            title = stringResource(R.string.quiet_hours_start),
                            on_click = { show_time_picker(quiet_hours_start) { quiet_hours_start = it; save_trigger++ } },
                            trailing = { Text(text = quiet_hours_start, color = colors.text_secondary, fontSize = 15.sp) },
                        )
                        AsterDivider(modifier = Modifier)
                        detail_row(
                            title = stringResource(R.string.quiet_hours_end),
                            on_click = { show_time_picker(quiet_hours_end) { quiet_hours_end = it; save_trigger++ } },
                            trailing = { Text(text = quiet_hours_end, color = colors.text_secondary, fontSize = 15.sp) },
                        )
                    }
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
