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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.components.AsterSwitch
import org.astermail.android.ui.theme.ThemeViewModel
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.translation.language_display_name
import org.astermail.android.translation.translation_language_codes
import org.astermail.android.translation.translation_supported
import org.astermail.android.settings.shared_settings_view_model

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
                modifier = Modifier.size(20.dp).background(colors.accent_blue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun behavior_toggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    info_title: String? = null,
    info_description: String? = null,
    on_change: (Boolean) -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = on_change, role = Role.Switch)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (info_title != null && info_description != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(5.dp))
                    info_dialog_button(info_title, info_description)
                }
            }
            if (subtitle != null) Text(subtitle, color = colors.text_tertiary, fontSize = 13.sp)
        }
        AsterSwitch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
fun BehaviorScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit,
) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val prefs = state.preferences
    val current_prefs = rememberUpdatedState(prefs)

    LaunchedEffect(Unit) { vm.load_preferences() }
    LaunchedEffect(Unit) { vm.load_spam_settings() }

    val prefs_loaded = prefs != null && state.preferences_authoritative
    var mark_read by remember(prefs_loaded) { mutableStateOf(prefs?.mark_as_read ?: "1_second") }
    var auto_advance by remember(prefs_loaded) { mutableStateOf(prefs?.auto_advance ?: "Go to next message") }
    var conversation_grouping by remember(prefs_loaded) { mutableStateOf(prefs?.conversation_grouping ?: true) }
    var inbox_categories by remember(prefs_loaded) { mutableStateOf(prefs?.inbox_categories_enabled ?: true) }
    var enabled_categories by remember(prefs_loaded) {
        mutableStateOf(prefs?.enabled_categories ?: emptyList())
    }
    var custom_categories by remember(prefs_loaded) {
        mutableStateOf(prefs?.custom_categories ?: emptyList())
    }
    val plan_vm: org.astermail.android.billing.PlanLimitsViewModel = hiltViewModel()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val custom_category_limit =
        plan_state.limits?.limits?.get("max_custom_categories")?.limit ?: -1
    var inbox_sort_oldest_first by remember(prefs_loaded) {
        mutableStateOf(org.astermail.android.api.preferences.resolve_inbox_sort_oldest_first(prefs))
    }
    var inbox_page_size by remember(prefs_loaded) { mutableIntStateOf(prefs?.inbox_page_size ?: 50) }
    var show_message_size by remember(prefs_loaded) { mutableStateOf(prefs?.show_message_size ?: false) }
    var show_alias_indicators by remember(prefs_loaded) { mutableStateOf(prefs?.show_alias_indicators ?: true) }
    var show_profile_pictures by remember(prefs_loaded) { mutableStateOf(prefs?.show_profile_pictures ?: true) }
    var show_email_preview by remember(prefs_loaded) { mutableStateOf(prefs?.show_email_preview != false) }
    var force_dark_emails by remember(prefs_loaded) { mutableStateOf(prefs?.force_dark_emails ?: false) }
    var default_reply by remember(prefs_loaded) { mutableStateOf(prefs?.default_reply_behavior ?: "reply") }
    var auto_save_recipients by remember(prefs_loaded) { mutableStateOf(prefs?.auto_save_recent_recipients ?: true) }
    var undo_send by remember(prefs_loaded) { mutableStateOf(prefs?.undo_send_enabled ?: true) }
    var undo_send_secs by remember(prefs_loaded) { mutableIntStateOf(prefs?.undo_send_seconds ?: 10) }
    var confirm_delete by remember(prefs_loaded) { mutableStateOf(prefs?.confirm_delete ?: false) }
    var confirm_archive by remember(prefs_loaded) { mutableStateOf(prefs?.confirm_archive ?: false) }
    var confirm_spam by remember(prefs_loaded) { mutableStateOf(prefs?.confirm_spam ?: false) }
    val spam_settings = state.spam_settings
    var spam_filter_enabled by remember(prefs_loaded, spam_settings != null) {
        mutableStateOf(spam_settings?.spam_filter_enabled ?: prefs?.spam_filter_enabled ?: true)
    }
    var spam_sensitivity by remember(prefs_loaded, spam_settings != null) {
        mutableStateOf(spam_settings?.spam_sensitivity ?: prefs?.spam_sensitivity ?: "medium")
    }
    var auto_delete_spam_days by remember(prefs_loaded, spam_settings != null) {
        mutableIntStateOf(spam_settings?.spam_retention_days ?: prefs?.auto_delete_spam_days ?: 30)
    }
    var folder_lock_mode by remember(prefs_loaded) { mutableStateOf(prefs?.folder_lock_mode ?: "session") }
    var purge_locked_folder_on_delete by remember(prefs_loaded) {
        mutableStateOf(prefs?.purge_locked_folder_on_delete ?: false)
    }
    val theme_vm: ThemeViewModel = hiltViewModel()
    var haptic by remember(prefs_loaded) { mutableStateOf(prefs?.haptic_enabled ?: true) }
    var dev_mode by remember(prefs_loaded) { mutableStateOf(prefs?.dev_mode ?: false) }
    var translate_incoming by remember(prefs_loaded) { mutableStateOf(prefs?.translate_incoming ?: "off") }
    var translate_languages by remember(prefs_loaded) { mutableStateOf((prefs?.translate_languages ?: emptyList()).toSet()) }
    var translate_never by remember(prefs_loaded) { mutableStateOf((prefs?.translate_never_languages ?: emptyList()).toSet()) }
    var save_trigger by remember { mutableIntStateOf(0) }
    var loaded_signature by remember { mutableStateOf<Int?>(null) }
    var reseed_token by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.save_status) {
        if (state.save_status != org.astermail.android.settings.SaveStatus.ERROR) return@LaunchedEffect
        if (prefs == null) return@LaunchedEffect
        save_trigger = 0
        loaded_signature = null
        reseed_token += 1
    }

    LaunchedEffect(prefs, reseed_token) {
        if (prefs != null) {
            val sig = prefs.hashCode()
            if (loaded_signature != sig && save_trigger == 0) {
                loaded_signature = sig
                mark_read = prefs.mark_as_read
                auto_advance = prefs.auto_advance
                conversation_grouping = prefs.conversation_grouping
                inbox_categories = prefs.inbox_categories_enabled
                enabled_categories = prefs.enabled_categories
                custom_categories = prefs.custom_categories
                inbox_sort_oldest_first =
                    org.astermail.android.api.preferences.resolve_inbox_sort_oldest_first(prefs)
                inbox_page_size = prefs.inbox_page_size
                show_message_size = prefs.show_message_size
                show_alias_indicators = prefs.show_alias_indicators
                show_profile_pictures = prefs.show_profile_pictures
                show_email_preview = prefs.show_email_preview
                force_dark_emails = prefs.force_dark_emails
                default_reply = prefs.default_reply_behavior
                auto_save_recipients = prefs.auto_save_recent_recipients
                undo_send = prefs.undo_send_enabled
                undo_send_secs = prefs.undo_send_seconds
                confirm_delete = prefs.confirm_delete
                confirm_archive = prefs.confirm_archive
                confirm_spam = prefs.confirm_spam
                if (spam_settings == null) {
                    spam_filter_enabled = prefs.spam_filter_enabled
                    spam_sensitivity = prefs.spam_sensitivity
                    auto_delete_spam_days = prefs.auto_delete_spam_days
                }
                folder_lock_mode = prefs.folder_lock_mode
                purge_locked_folder_on_delete = prefs.purge_locked_folder_on_delete
                haptic = prefs.haptic_enabled
                theme_vm.set_haptic_enabled(prefs.haptic_enabled)
                dev_mode = prefs.dev_mode
                translate_incoming = prefs.translate_incoming
                translate_languages = prefs.translate_languages.toSet()
                translate_never = prefs.translate_never_languages.toSet()
            }
        }
    }

    LaunchedEffect(spam_settings) {
        val server = spam_settings ?: return@LaunchedEffect
        spam_filter_enabled = server.spam_filter_enabled
        spam_sensitivity = server.spam_sensitivity
        auto_delete_spam_days = server.spam_retention_days
    }

    val toast_context = LocalContext.current
    LaunchedEffect(state.action_result) {
        val message = state.action_result ?: return@LaunchedEffect
        Toast.makeText(toast_context, message, Toast.LENGTH_LONG).show()
        vm.clear_action_result()
    }

    fun push_spam_settings() {
        vm.save_spam_settings(
            org.astermail.android.api.preferences.SpamSettings(
                spam_retention_days = auto_delete_spam_days,
                spam_sensitivity = spam_sensitivity,
                spam_filter_enabled = spam_filter_enabled,
            ),
        )
    }

    fun save(snap: UserPreferences? = null) {
        val base = snap ?: prefs ?: return
        vm.save_preferences(
            base.copy(
                mark_as_read = mark_read,
                auto_advance = auto_advance,
                conversation_grouping = conversation_grouping,
                inbox_categories_enabled = inbox_categories,
                enabled_categories = enabled_categories,
                custom_categories = org.astermail.android.mail.sanitize_custom_categories(custom_categories),
                inbox_sort_order =
                    org.astermail.android.api.preferences.inbox_sort_order_value(inbox_sort_oldest_first),
                inbox_page_size = inbox_page_size.coerceIn(10, 100),
                show_message_size = show_message_size,
                show_alias_indicators = show_alias_indicators,
                show_profile_pictures = show_profile_pictures,
                show_email_preview = show_email_preview,
                force_dark_emails = force_dark_emails,
                default_reply_behavior = default_reply,
                auto_save_recent_recipients = auto_save_recipients,
                undo_send_enabled = undo_send,
                undo_send_seconds = undo_send_secs,
                confirm_delete = confirm_delete,
                confirm_archive = confirm_archive,
                confirm_spam = confirm_spam,
                spam_filter_enabled = spam_filter_enabled,
                spam_sensitivity = spam_sensitivity,
                auto_delete_spam_days = auto_delete_spam_days,
                folder_lock_mode = folder_lock_mode,
                purge_locked_folder_on_delete = purge_locked_folder_on_delete,
                haptic_enabled = haptic,
                dev_mode = dev_mode,
                translate_incoming = translate_incoming,
                translate_languages = translate_languages.toList(),
                translate_never_languages = translate_never.toList(),
            ),
        )
    }

    LaunchedEffect(save_trigger) {
        if (save_trigger == 0) return@LaunchedEffect
        if (loaded_signature == null || prefs == null) return@LaunchedEffect
        delay(400)
        save()
        save_trigger = 0
    }

    val flush_on_exit: androidx.compose.runtime.State<() -> Unit> =
        rememberUpdatedState({
            if (save_trigger > 0 && loaded_signature != null) save(current_prefs.value)
        })

    DisposableEffect(Unit) {
        onDispose { flush_on_exit.value() }
    }

    detail_scaffold(title = stringResource(R.string.settings_behavior), on_back = on_back) {
        preferences_save_error_banner()
        if (!prefs_loaded) {
            preferences_load_placeholder()
        } else {

            // ── Reading & Conversations ──────────────────────────────────────────
            section_label(stringResource(R.string.section_reading_conversations))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.mark_as_read),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                )
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
            v_gap(AsterSpacing.md)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.auto_advance_label),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                )
                listOf(
                    "Go to next message" to stringResource(R.string.auto_advance_next),
                    "Go to previous message" to stringResource(R.string.auto_advance_previous),
                    "Go back to message list" to stringResource(R.string.auto_advance_back),
                ).forEachIndexed { i, (id, label) ->
                    behavior_option(label, auto_advance == id) { auto_advance = id; save_trigger++ }
                    if (i < 2) AsterDivider(modifier = Modifier)
                }
            }
            v_gap(AsterSpacing.md)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(
                    title = stringResource(R.string.conversation_grouping),
                    subtitle = stringResource(R.string.conversation_grouping_subtitle),
                    checked = conversation_grouping,
                    on_change = { conversation_grouping = it; save_trigger++ },
                )
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.inbox_categories),
                    subtitle = stringResource(R.string.inbox_categories_subtitle),
                    checked = inbox_categories,
                    on_change = { inbox_categories = it; save_trigger++ },
                )
                AsterDivider(modifier = Modifier)
                Text(
                    text = stringResource(R.string.sort_by),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                )
                listOf(
                    false to stringResource(R.string.sort_newest),
                    true to stringResource(R.string.sort_oldest),
                ).forEachIndexed { i, (oldest_first, label) ->
                    behavior_option(label, inbox_sort_oldest_first == oldest_first) {
                        inbox_sort_oldest_first = oldest_first
                        save_trigger++
                    }
                    if (i == 0) AsterDivider(modifier = Modifier)
                }
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.show_message_size),
                    subtitle = stringResource(R.string.show_message_size_subtitle),
                    checked = show_message_size,
                    on_change = { show_message_size = it; save_trigger++ },
                )
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.show_alias_indicators),
                    subtitle = stringResource(R.string.show_alias_indicators_subtitle),
                    checked = show_alias_indicators,
                    on_change = { show_alias_indicators = it; save_trigger++ },
                )
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.show_sender_pictures),
                    subtitle = stringResource(R.string.show_sender_pictures_subtitle),
                    checked = show_profile_pictures,
                    on_change = { show_profile_pictures = it; save_trigger++ },
                )
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.show_email_preview),
                    subtitle = stringResource(R.string.show_email_preview_subtitle),
                    checked = show_email_preview,
                    on_change = { show_email_preview = it; save_trigger++ },
                )
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.force_dark_emails),
                    subtitle = stringResource(R.string.force_dark_emails_subtitle),
                    checked = force_dark_emails,
                    on_change = { force_dark_emails = it; save_trigger++ },
                )
            }
            v_gap(AsterSpacing.md)
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.emails_per_page),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.emails_per_page_subtitle),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = 4.dp),
                )
                listOf(10, 20, 30, 50, 100).forEachIndexed { i, n ->
                    behavior_option("$n", inbox_page_size == n) { inbox_page_size = n; save_trigger++ }
                    if (i < 4) AsterDivider(modifier = Modifier)
                }
            }

            if (inbox_categories) {
                v_gap(AsterSpacing.lg)
                category_settings_section(
                    enabled_categories = enabled_categories,
                    custom_categories = custom_categories,
                    custom_category_limit = custom_category_limit,
                    on_enabled_change = { enabled_categories = it; save_trigger++ },
                    on_custom_change = { custom_categories = it; save_trigger++ },
                    on_upgrade = { on_open("billing") },
                )
            }

            if (translation_supported) {
                v_gap(AsterSpacing.lg)

                section_label(stringResource(R.string.section_translation))
                AsterCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.translate_incoming_label),
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.translate_incoming_subtitle),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = 4.dp),
                    )
                    listOf(
                        "off" to stringResource(R.string.translate_mode_off),
                        "ask" to stringResource(R.string.translate_mode_ask),
                        "always" to stringResource(R.string.translate_mode_always),
                    ).forEachIndexed { i, (id, label) ->
                        behavior_option(label, translate_incoming == id) { translate_incoming = id; save_trigger++ }
                        if (i < 2) AsterDivider(modifier = Modifier)
                    }
                }
                if (translate_incoming != "off") {
                    v_gap(AsterSpacing.md)
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.translate_my_languages_label),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.translate_my_languages_subtitle),
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = 4.dp),
                        )
                        translation_language_codes.forEachIndexed { i, code ->
                            behavior_option(language_display_name(code), translate_languages.contains(code)) {
                                translate_languages = if (translate_languages.contains(code)) {
                                    translate_languages - code
                                } else {
                                    translate_languages + code
                                }
                                save_trigger++
                            }
                            if (i < translation_language_codes.size - 1) AsterDivider(modifier = Modifier)
                        }
                    }
                    v_gap(AsterSpacing.md)
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.translate_never_languages_label),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.translate_never_languages_subtitle),
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = 4.dp),
                        )
                        translation_language_codes.forEachIndexed { i, code ->
                            behavior_option(language_display_name(code), translate_never.contains(code)) {
                                translate_never = if (translate_never.contains(code)) {
                                    translate_never - code
                                } else {
                                    translate_never + code
                                }
                                save_trigger++
                            }
                            if (i < translation_language_codes.size - 1) AsterDivider(modifier = Modifier)
                        }
                    }
                }
            }

            v_gap(AsterSpacing.lg)

            // ── Composing & Replies ──────────────────────────────────────────────
            section_label(stringResource(R.string.section_composing_replies))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.default_reply),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                )
                behavior_option(stringResource(R.string.reply_to_sender), default_reply == "reply") { default_reply = "reply"; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_option(stringResource(R.string.reply_to_all), default_reply == "reply_all") { default_reply = "reply_all"; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.auto_save_recipients),
                    subtitle = stringResource(R.string.auto_save_recipients_subtitle),
                    checked = auto_save_recipients,
                    on_change = { auto_save_recipients = it; save_trigger++ },
                )
            }

            v_gap(AsterSpacing.lg)

            // ── Undo Send ────────────────────────────────────────────────────────
            section_label(stringResource(R.string.undo_send))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(
                    title = stringResource(R.string.enable_undo_send),
                    subtitle = if (undo_send) stringResource(R.string.undo_send_cancel_window, undo_send_secs) else stringResource(R.string.enable_undo_send_subtitle),
                    checked = undo_send,
                    on_change = { undo_send = it; save_trigger++ },
                )
                AnimatedVisibility(
                    visible = undo_send,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(140)),
                ) {
                    Column {
                        AsterDivider(modifier = Modifier)
                        Text(
                            text = stringResource(R.string.cancellation_period),
                            color = colors.text_tertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.sm, bottom = 2.dp),
                        )
                        listOf(3, 5, 10, 15, 20, 30).forEachIndexed { i, secs ->
                            behavior_option(stringResource(R.string.undo_send_delay_seconds, secs), undo_send_secs == secs) { undo_send_secs = secs; save_trigger++ }
                            if (i < 5) AsterDivider(modifier = Modifier)
                        }
                    }
                }
            }

            v_gap(AsterSpacing.lg)

            // ── Confirmations ────────────────────────────────────────────────────
            section_label(stringResource(R.string.confirmations))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(stringResource(R.string.confirm_before_delete), null, confirm_delete) { confirm_delete = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(stringResource(R.string.confirm_before_archive), null, confirm_archive) { confirm_archive = it; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(stringResource(R.string.confirm_before_spam), null, confirm_spam) { confirm_spam = it; save_trigger++ }
            }

            v_gap(AsterSpacing.lg)

            // ── Spam Filtering ───────────────────────────────────────────────────
            section_label(stringResource(R.string.section_spam_filtering))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(
                    title = stringResource(R.string.enable_spam_filtering),
                    subtitle = stringResource(R.string.enable_spam_filtering_subtitle),
                    checked = spam_filter_enabled,
                    on_change = { spam_filter_enabled = it; push_spam_settings(); save_trigger++ },
                )
                AnimatedVisibility(
                    visible = spam_filter_enabled,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(140)),
                ) {
                    Column {
                        AsterDivider(modifier = Modifier)
                        Text(
                            text = stringResource(R.string.spam_sensitivity),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                        )
                        listOf(
                            "low" to stringResource(R.string.spam_sensitivity_low),
                            "medium" to stringResource(R.string.spam_sensitivity_medium),
                            "high" to stringResource(R.string.spam_sensitivity_high),
                        ).forEachIndexed { i, (id, label) ->
                            behavior_option(label, spam_sensitivity == id) { spam_sensitivity = id; push_spam_settings(); save_trigger++ }
                            if (i < 2) AsterDivider(modifier = Modifier)
                        }
                        AsterDivider(modifier = Modifier)
                        Text(
                            text = stringResource(R.string.auto_delete_spam),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                        )
                        listOf(
                            7 to stringResource(R.string.auto_delete_spam_7),
                            14 to stringResource(R.string.auto_delete_spam_14),
                            30 to stringResource(R.string.auto_delete_spam_30),
                            0 to stringResource(R.string.auto_delete_spam_never),
                        ).forEachIndexed { i, (days, label) ->
                            behavior_option(label, auto_delete_spam_days == days) { auto_delete_spam_days = days; push_spam_settings(); save_trigger++ }
                            if (i < 3) AsterDivider(modifier = Modifier)
                        }
                    }
                }
            }

            v_gap(AsterSpacing.lg)

            // ── Protected Folders ────────────────────────────────────────────────
            section_label(stringResource(R.string.section_protected_folders))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.folder_lock_mode),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = AsterSpacing.lg, top = AsterSpacing.md, bottom = 4.dp),
                )
                behavior_option(
                    label = stringResource(R.string.folder_lock_session),
                    selected = folder_lock_mode == "session",
                ) { folder_lock_mode = "session"; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_option(
                    label = stringResource(R.string.folder_lock_on_leave),
                    selected = folder_lock_mode == "on_leave",
                ) { folder_lock_mode = "on_leave"; save_trigger++ }
                AsterDivider(modifier = Modifier)
                behavior_toggle(
                    title = stringResource(R.string.purge_locked_folder_on_delete),
                    subtitle = stringResource(R.string.purge_locked_folder_on_delete_subtitle),
                    checked = purge_locked_folder_on_delete,
                    on_change = { purge_locked_folder_on_delete = it; save_trigger++ },
                )
            }
            v_gap(AsterSpacing.xs)
            Text(
                text = stringResource(R.string.folder_lock_explanation),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = AsterSpacing.xs),
            )

            v_gap(AsterSpacing.lg)

            // ── Advanced ─────────────────────────────────────────────────────────
            section_label(stringResource(R.string.section_advanced))
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                behavior_toggle(
                    title = stringResource(R.string.haptic_feedback),
                    subtitle = stringResource(R.string.haptic_feedback_subtitle),
                    checked = haptic,
                    on_change = { haptic = it; theme_vm.set_haptic_enabled(it); save_trigger++ },
                )
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
