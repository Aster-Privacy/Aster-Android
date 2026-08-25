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

package org.astermail.android.ui.compose

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.BuildConfig
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import org.astermail.android.ui.icons.pin_icon
import org.astermail.android.ui.icons.pin_icon_filled
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.billing.AttachmentLimits
import org.astermail.android.billing.PlanLimitsViewModel
import org.astermail.android.mail.ASTER_INTERNAL_DOMAINS
import org.astermail.android.mail.is_sendable_address
import org.astermail.android.mail.MailViewModel
import org.astermail.android.settings.DecryptedSignature
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.common.picker_theme_res
import org.astermail.android.ui.common.resolve_primary_sender_email
import org.astermail.android.ui.common.sender_id_for_email
import org.astermail.android.contacts.ContactsViewModel
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.mail.ComposePrefill
import org.astermail.android.ui.mail.build_quoted_body
import org.astermail.android.ui.mail.subject_prefix
import org.astermail.android.ui.mail.thread_message_to_mock
import org.astermail.android.util.strip_metadata
import org.astermail.android.util.strip_status

data class AttachmentItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime_type: String,
)

private class AttachmentEncodeException(val filename: String, cause: Throwable?) : Exception(cause)

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun ComposeScreen(
    on_back: () -> Unit,
    on_sent: () -> Unit,
    reply_to: String? = null,
    mode: String? = null,
    draft_id: String? = null,
    prefill_to: String? = null,
    thread_ghost_email: String? = null,
    shared_mail_vm: MailViewModel? = null,
    shared_settings_vm: SettingsViewModel? = null,
    share_payload: org.astermail.android.share.SharePayload? = null,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mail_vm: MailViewModel = shared_mail_vm ?: hiltViewModel()
    val settings_vm: SettingsViewModel = shared_settings_vm ?: hiltViewModel()
    val contacts_vm: ContactsViewModel = hiltViewModel()
    val plan_vm: PlanLimitsViewModel = hiltViewModel()
    val templates_vm: org.astermail.android.templates.TemplatesViewModel = hiltViewModel()
    val templates_state by templates_vm.state.collectAsStateWithLifecycle()
    val plan_state by plan_vm.state.collectAsStateWithLifecycle()
    val thread_state by mail_vm.thread_state.collectAsStateWithLifecycle()
    val current_user_email = remember { mail_vm.get_user_email().orEmpty() }
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val contacts_state by contacts_vm.state.collectAsStateWithLifecycle()
    val all_contacts = contacts_state.contacts
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus_manager = androidx.compose.ui.platform.LocalFocusManager.current
    val dismiss_keyboard = {
        focus_manager.clearFocus(force = true)
        keyboard?.hide()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }
    val copy_from_address = { address: String ->
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("email_address", address))
        org.astermail.android.ui.common.show_copied_toast(context, address)
    }

    LaunchedEffect(Unit) {
        settings_vm.load_profile()
        settings_vm.load_aliases()
        settings_vm.load_custom_domain_addresses()
        settings_vm.load_ghost_aliases()
        settings_vm.load_preferences()
        settings_vm.load_signature()
        contacts_vm.load_contacts()
    }
    LaunchedEffect(draft_id, reply_to, mode) {
        if (!draft_id.isNullOrBlank() && mode == "draft") {
            mail_vm.load_draft(draft_id)
        } else if (!draft_id.isNullOrBlank()) {
            mail_vm.load_thread(draft_id)
        } else if (!reply_to.isNullOrBlank()) {
            val already_loaded = mail_vm.thread_state.value.messages.any { it.id == reply_to }
            if (!already_loaded) {
                mail_vm.load_thread(reply_to)
            }
        }
    }

    val user_email = settings_state.user?.email.orEmpty()
    val thread_ghost_match = remember(thread_ghost_email, settings_state.ghost_aliases) {
        val target = thread_ghost_email?.lowercase()?.takeIf { it.isNotBlank() }
        if (target != null) {
            settings_state.ghost_aliases.firstOrNull { it.address.lowercase() == target }?.address
                ?: target
        } else null
    }
    val alias_options = remember(
        user_email,
        settings_state.aliases,
        settings_state.custom_domain_addresses,
        settings_state.ghost_aliases,
        thread_ghost_match,
    ) {
        val options = mutableListOf<String>()
        if (user_email.isNotBlank()) options.add(user_email)
        settings_state.aliases
            .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
            .forEach { alias ->
                val addr = alias.address
                if (addr.isNotBlank() && addr !in options) options.add(addr)
            }
        settings_state.custom_domain_addresses
            .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
            .forEach { addr_info ->
                val addr = addr_info.address
                if (addr.isNotBlank() && addr !in options) options.add(addr)
            }
        settings_state.ghost_aliases
            .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
            .forEach { ghost ->
                val addr = ghost.address
                if (addr.isNotBlank() && addr !in options) options.add(addr)
            }
        if (thread_ghost_match != null && thread_ghost_match !in options) options.add(thread_ghost_match)
        if (options.isEmpty()) options.add("you@astermail.org")
        options.toList()
    }

    val alias_hash_map = remember(
        settings_state.aliases,
        settings_state.custom_domain_addresses,
        settings_state.ghost_aliases,
    ) {
        val map = mutableMapOf<String, String>()
        settings_state.aliases.forEach { map[it.address] = it.alias_address_hash }
        settings_state.custom_domain_addresses.forEach { map[it.address] = it.local_part_hash }
        settings_state.ghost_aliases.forEach { ghost ->
            if (ghost.address.isNotBlank() && ghost.alias_address_hash.isNotBlank()) {
                map[ghost.address] = ghost.alias_address_hash
            }
        }
        map.toMap()
    }

    val alias_display_name_map = remember(
        settings_state.aliases,
        settings_state.custom_domain_addresses,
    ) {
        val map = mutableMapOf<String, String>()
        settings_state.aliases.forEach { alias ->
            val name = alias.encrypted_display_name?.trim().orEmpty()
            if (alias.address.isNotBlank() && name.isNotBlank()) {
                map[alias.address] = name
            }
        }
        settings_state.custom_domain_addresses.forEach { addr ->
            val name = addr.encrypted_display_name?.trim().orEmpty()
            if (addr.address.isNotBlank() && name.isNotBlank()) {
                map[addr.address] = name
            }
        }
        map.toMap()
    }

    val resolve_sender_display_name: (String) -> String? = { from ->
        if (from == user_email) {
            settings_state.user?.display_name?.trim()?.takeIf { it.isNotBlank() }
        } else {
            alias_display_name_map[from]
        }
    }

    val primary_sender_email = remember(
        settings_state.default_sender_id,
        user_email,
        settings_state.aliases,
        settings_state.ghost_aliases,
        settings_state.custom_domain_addresses,
    ) {
        resolve_primary_sender_email(
            settings_state.default_sender_id,
            user_email,
            settings_state.aliases,
            settings_state.ghost_aliases,
            settings_state.custom_domain_addresses,
        )
    }

    val received_on_alias = remember(reply_to, thread_state.messages, alias_options, mode, user_email) {
        if (mode == "new" || mode == "draft") {
            null
        } else {
            val msg = thread_state.messages.firstOrNull { it.id == reply_to }
                ?: thread_state.messages.filter { it.raw_item.item_type != "sent" }.maxByOrNull { it.timestamp }
                ?: thread_state.messages.lastOrNull()
            val delivered_to = msg?.raw_headers?.let {
                org.astermail.android.ui.mail.extract_delivered_to(it)
            }
            val recipients = listOfNotNull(delivered_to) +
                (msg?.to_addresses ?: emptyList()) +
                (msg?.cc_addresses ?: emptyList())
            compute_received_on_alias(recipients, alias_options, user_email)
        }
    }

    var from_alias by rememberSaveable {
        mutableStateOf(
            resolve_reply_from_alias(received_on_alias, thread_ghost_match, primary_sender_email, alias_options),
        )
    }
    var from_manually_selected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(alias_options, received_on_alias, thread_ghost_match, primary_sender_email) {
        if (from_manually_selected) return@LaunchedEffect
        val resolved = resolve_reply_from_alias(received_on_alias, thread_ghost_match, primary_sender_email, alias_options)
        if (resolved.isNotBlank() && resolved != from_alias) {
            from_alias = resolved
        }
    }

    var mode_override by rememberSaveable { mutableStateOf<String?>(null) }
    var mode_menu_open by remember { mutableStateOf(false) }
    val effective_mode = mode_override
        ?: if (mode == "reply" && settings_state.preferences?.default_reply_behavior == "reply_all") "reply_all" else mode

    val prefill = remember(reply_to, effective_mode, thread_state) {
        if (reply_to.isNullOrBlank() || effective_mode.isNullOrBlank()) {
            ComposePrefill(emptyList(), "", "", emptyList())
        } else {
            val msg = thread_state.messages.firstOrNull { it.id == reply_to }
                ?: thread_state.messages.lastOrNull()
            if (msg != null) {
                val item = thread_state.item
                val original_subject = sequenceOf(
                    msg.subject,
                    item?.subject.orEmpty(),
                    thread_state.messages.firstNotNullOfOrNull { m ->
                        m.subject.takeIf { it.isNotBlank() }
                    }.orEmpty(),
                ).firstOrNull { it.isNotBlank() }.orEmpty()
                val me = current_user_email.lowercase()
                val to_chips = when (effective_mode) {
                    "forward" -> emptyList()
                    "reply_all" -> {
                        val all = mutableListOf(msg.sender_email)
                        msg.to_addresses.filter { it.lowercase() != me && it !in all }.forEach { all.add(it) }
                        all
                    }
                    else -> listOf(msg.sender_email)
                }.filter { it.isNotBlank() }
                val cc = when (effective_mode) {
                    "reply_all" -> msg.cc_addresses.filter { it.lowercase() != me && it !in to_chips }
                    else -> emptyList()
                }
                val seed_body = ""
                ComposePrefill(to_chips, subject_prefix(original_subject, effective_mode), seed_body, cc)
            } else {
                val item = thread_state.item
                if (item != null) {
                    val subject = subject_prefix(item.subject, effective_mode)
                    val to = if (effective_mode != "forward") listOf(item.sender_email).filter { it.isNotBlank() } else emptyList()
                    ComposePrefill(to, subject, "", emptyList())
                } else {
                    ComposePrefill(emptyList(), "", "", emptyList())
                }
            }
        }
    }

    var show_attach_sheet by remember { mutableStateOf(false) }
    var show_from_sheet by remember { mutableStateOf(false) }
    var show_overflow_sheet by remember { mutableStateOf(false) }
    val open_from_sheet = {
        if (!show_from_sheet) {
            dismiss_keyboard()
            scope.launch {
                kotlinx.coroutines.delay(90)
                show_from_sheet = true
            }
        }
        Unit
    }
    var show_ghost_alias_sheet by remember { mutableStateOf(false) }
    var show_template_sheet by remember { mutableStateOf(false) }
    var show_signature_sheet by remember { mutableStateOf(false) }
    var manual_signature_id by remember { mutableStateOf<String?>("auto") }
    var signature_placement_override by remember { mutableStateOf<Int?>(null) }
    var scheduled_send by remember { mutableStateOf(false) }
    var show_schedule_picker by remember { mutableStateOf(false) }
    var scheduled_at_iso by remember { mutableStateOf<String?>(null) }
    var expiring by remember { mutableStateOf(false) }
    var expires_at_iso by remember { mutableStateOf<String?>(null) }
    var expiry_password by remember { mutableStateOf<String?>(null) }
    var show_expiring_sheet by remember { mutableStateOf(false) }
    var to_chips_set by remember { mutableStateOf(false) }
    var subject_set by remember { mutableStateOf(false) }
    var body_set by remember { mutableStateOf(false) }
    var to_chips by remember {
        val initial = when {
            !share_payload?.to.isNullOrEmpty() -> share_payload!!.to
            !prefill_to.isNullOrBlank() -> listOf(prefill_to)
            else -> prefill.to_chips
        }
        mutableStateOf(initial)
    }
    var to_input by remember { mutableStateOf("") }
    val to_focus_requester = remember { androidx.compose.ui.focus.FocusRequester() }
    val is_blank_new_message = remember {
        reply_to.isNullOrBlank() && mode.isNullOrBlank() && draft_id.isNullOrBlank() &&
            prefill_to.isNullOrBlank() && to_chips.isEmpty()
    }
    LaunchedEffect(Unit) {
        if (is_blank_new_message) {
            kotlinx.coroutines.delay(250)
            runCatching { to_focus_requester.requestFocus() }
        }
    }
    var cc_expanded by remember {
        mutableStateOf(!share_payload?.cc.isNullOrEmpty() || !share_payload?.bcc.isNullOrEmpty())
    }
    var cc_chips by remember { mutableStateOf(share_payload?.cc.orEmpty()) }
    var cc_input by remember { mutableStateOf("") }
    var bcc_chips by remember { mutableStateOf(share_payload?.bcc.orEmpty()) }
    var bcc_input by remember { mutableStateOf("") }
    var subject by remember {
        mutableStateOf(share_payload?.subject?.takeIf { it.isNotBlank() } ?: prefill.subject)
    }
    val share_body_prefix = remember(share_payload) {
        share_payload?.body?.takeIf { it.isNotBlank() }?.let { it + "\n\n" }.orEmpty()
    }
    val initial_watermark = remember {
        if (mode != "draft" && prefill.body.isBlank() &&
            settings_state.preferences?.show_aster_branding == true
        ) {
            "\n\n${context.getString(R.string.compose_footer_secured_by_plain)}"
        } else {
            ""
        }
    }
    val preloaded_signature_obj = remember {
        if (mode == "draft" || prefill.body.isNotBlank()) {
            null
        } else {
            settings_vm.ensure_signatures_hydrated()
            settings_vm.signature_for(null)
        }
    }
    val preloaded_signature = remember {
        preloaded_signature_obj?.takeIf { !it.is_html }?.content.orEmpty()
    }
    var signature_html by remember {
        mutableStateOf(preloaded_signature_obj?.takeIf { it.is_html }?.content.orEmpty())
    }
    var body by remember {
        mutableStateOf(
            when {
                prefill.body.isNotBlank() -> prefill.body
                preloaded_signature.isNotBlank() ->
                    share_body_prefix + "\n\n" + preloaded_signature + initial_watermark
                else -> share_body_prefix + initial_watermark
            },
        )
    }
    fun draft_body_with_signature(): String =
        if (signature_html.isNotBlank()) {
            val separator = if (settings_state.preferences?.show_signature_separator != false) "--<br>" else ""
            body + "<br><br><div class=\"aster_signature\">" + separator + signature_html + "</div>"
        } else {
            body
        }
    var initial_to_chips by remember { mutableStateOf<List<String>>(emptyList()) }
    var initial_subject by remember { mutableStateOf("") }
    var initial_body by remember { mutableStateOf(if (prefill.body.isNotBlank()) "" else body) }
    var initial_cc_chips by remember { mutableStateOf<List<String>>(emptyList()) }
    var initial_bcc_chips by remember { mutableStateOf<List<String>>(emptyList()) }
    val signature_loaded by settings_vm.signature_loaded.collectAsStateWithLifecycle()
    val signatures_list by settings_vm.signatures.collectAsStateWithLifecycle()
    var signature_applied by remember { mutableStateOf(false) }
    var applied_signature by remember { mutableStateOf(preloaded_signature) }
    val current_alias_id = remember(from_alias, settings_state.aliases, settings_state.custom_domain_addresses) {
        settings_state.aliases.firstOrNull { it.address == from_alias }?.id
            ?: settings_state.custom_domain_addresses.firstOrNull { it.address == from_alias }?.id
    }
    val signature_auto_enabled = (settings_state.preferences?.signature_mode ?: "auto") == "auto"
    val signature_separator_enabled = settings_state.preferences?.show_signature_separator != false
    LaunchedEffect(signature_loaded, mode, prefill) {
        if (!signature_loaded || signature_applied) return@LaunchedEffect
        if (mode == "draft") { signature_applied = true; return@LaunchedEffect }
        if (prefill.body.isNotBlank()) { signature_applied = true; return@LaunchedEffect }
        val resolved_sig = if (signature_auto_enabled) settings_vm.signature_for(current_alias_id) else null
        val resolved = decorate_plain_signature(
            resolved_sig?.takeIf { !it.is_html }?.content.orEmpty(),
            signature_separator_enabled,
        )
        signature_placement_override = resolved_sig?.placement
        val show_branding = settings_state.preferences?.show_aster_branding == true
        val watermark = if (show_branding) "\n\n${context.getString(R.string.compose_footer_secured_by_plain)}" else ""
        val new_body = share_body_prefix +
            if (resolved.isNotBlank()) "\n\n${resolved}${watermark}" else watermark
        val seeded_body = if (preloaded_signature.isNotBlank()) {
            share_body_prefix + "\n\n" + preloaded_signature + initial_watermark
        } else {
            share_body_prefix + initial_watermark
        }
        if (body == seeded_body || body == share_body_prefix + initial_watermark || body.isBlank()) {
            body = new_body
            initial_body = new_body
        }
        signature_html = resolved_sig?.takeIf { it.is_html }?.content.orEmpty()
        applied_signature = resolved
        signature_applied = true
    }
    LaunchedEffect(current_alias_id, signature_applied, signature_auto_enabled) {
        if (!signature_applied) return@LaunchedEffect
        if (mode == "draft") return@LaunchedEffect
        if (manual_signature_id != "auto") return@LaunchedEffect
        val resolved_sig = if (signature_auto_enabled) settings_vm.signature_for(current_alias_id) else null
        val resolved = decorate_plain_signature(
            resolved_sig?.takeIf { !it.is_html }?.content.orEmpty(),
            signature_separator_enabled,
        )
        val resolved_html = resolved_sig?.takeIf { it.is_html }?.content.orEmpty()
        signature_placement_override = resolved_sig?.placement
        if (resolved == applied_signature && resolved_html == signature_html) return@LaunchedEffect
        signature_html = resolved_html
        val watermark = context.getString(R.string.compose_footer_secured_by_plain)
        val watermark_suffix = "\n\n${watermark}"
        val kept_suffix = if (body.endsWith(watermark_suffix)) watermark_suffix else ""
        val core = body.substring(0, body.length - kept_suffix.length)
        val new_core = if (applied_signature.isNotBlank() && core.endsWith(applied_signature)) {
            val before = core.substring(0, core.length - applied_signature.length)
            if (resolved.isNotBlank()) before + resolved else before.trimEnd('\n')
        } else if (applied_signature.isBlank() && resolved.isNotBlank()) {
            "${core}\n\n${resolved}"
        } else core
        body = new_core + kept_suffix
        applied_signature = resolved
    }
    LaunchedEffect(settings_state.preferences?.show_aster_branding, signature_applied) {
        if (!signature_applied) return@LaunchedEffect
        val show_branding = settings_state.preferences?.show_aster_branding ?: return@LaunchedEffect
        if (show_branding && mode == "draft") return@LaunchedEffect
        val watermark = context.getString(R.string.compose_footer_secured_by_plain)
        val watermark_suffix = "\n\n${watermark}"
        if (!show_branding) {
            if (body.endsWith(watermark_suffix)) {
                val trimmed = body.substring(0, body.length - watermark_suffix.length)
                if (initial_body == body) initial_body = trimmed
                body = trimmed
            }
        } else if (prefill.body.isBlank() && body == initial_body && !body.contains(watermark)) {
            val restored = body + watermark_suffix
            initial_body = restored
            body = restored
        }
    }
    var show_discard_dialog by remember { mutableStateOf(false) }
    var show_from_mismatch_dialog by remember { mutableStateOf(false) }
    var post_quantum_missing by remember { mutableStateOf<List<String>>(emptyList()) }
    val quoted_source = remember(reply_to, mode, thread_state) {
        if (reply_to.isNullOrBlank() || mode.isNullOrBlank()) {
            null
        } else {
            val msg = thread_state.messages.firstOrNull { it.id == reply_to }
                ?: thread_state.messages.lastOrNull()
            if (msg == null) {
                null
            } else {
                val item = thread_state.item
                val original_html = msg.body_html?.takeIf { it.isNotBlank() }
                    ?: msg.body_text.replace("\n", "<br>")
                original_html to Triple(
                    msg.display_sender_email ?: msg.sender_email,
                    msg.timestamp,
                    item?.subject.orEmpty(),
                )
            }
        }
    }
    val quoted_html = quoted_source?.first
    val quoted_meta = quoted_source?.second
    var quoted_expanded by remember { mutableStateOf(false) }
    var is_sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var send_error by remember { mutableStateOf<String?>(null) }
    var attachments by remember { mutableStateOf(listOf<AttachmentItem>()) }
    var inline_images by remember { mutableStateOf(listOf<AttachmentItem>()) }
    val body_editor_ref = remember { androidx.compose.runtime.mutableStateOf<RichBodyEditText?>(null) }
    val format_bold = remember { mutableStateOf(false) }
    val format_italic = remember { mutableStateOf(false) }
    val format_underline = remember { mutableStateOf(false) }
    val format_strike = remember { mutableStateOf(false) }
    val format_quote = remember { mutableStateOf(false) }
    val show_format_bar = remember { mutableStateOf(false) }
    var link_dialog_text by remember { mutableStateOf<String?>(null) }

    val compose_default_size_label = org.astermail.android.api.preferences
        .effective_compose_font_size(settings_state.preferences)
    val compose_default_size_px = org.astermail.android.api.preferences
        .compose_font_size_px(compose_default_size_label)
        .takeIf { compose_default_size_label != org.astermail.android.api.preferences.compose_font_size_default }
    val compose_default_color_argb = org.astermail.android.api.preferences
        .compose_font_color_argb(
            org.astermail.android.api.preferences.effective_compose_font_color(settings_state.preferences),
        )
    val compose_defaults_allowed = mode != "draft"

    val apply_compose_defaults: (android.text.Editable) -> Unit = apply_defaults@{ editable ->
        if (!compose_defaults_allowed) return@apply_defaults
        editable.getSpans(0, editable.length, android.text.style.AbsoluteSizeSpan::class.java)
            .forEach { editable.removeSpan(it) }
        editable.getSpans(0, editable.length, android.text.style.ForegroundColorSpan::class.java)
            .forEach { editable.removeSpan(it) }
        compose_default_size_px?.let {
            editable.setSpan(
                android.text.style.AbsoluteSizeSpan(it, true),
                0,
                editable.length,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
        compose_default_color_argb?.let {
            editable.setSpan(
                android.text.style.ForegroundColorSpan(it),
                0,
                editable.length,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
    }

    LaunchedEffect(compose_default_size_px, compose_default_color_argb, body_editor_ref.value) {
        val editable = body_editor_ref.value?.text ?: return@LaunchedEffect
        apply_compose_defaults(editable)
    }

    val insert_image_inline: (Uri) -> Boolean = insert@{ uri ->
        val img = build_attachment_from_uri(context, uri) ?: return@insert false
        val et = body_editor_ref.value ?: return@insert false
        val editable = et.text ?: return@insert false
        val raw_pos = et.selectionStart.coerceIn(0, editable.length)
        val needs_leading_newline = raw_pos > 0 && editable[raw_pos - 1] != '\n'
        val prefix = if (needs_leading_newline) "\n" else ""
        val to_insert = "$prefix$IMG_MARKER\n"
        val k = (0 until raw_pos).count { editable[it] == IMG_MARKER }
        et.suspend_text_watcher = true
        editable.insert(raw_pos, to_insert)
        et.suspend_text_watcher = false
        val marker_pos = raw_pos + prefix.length
        inline_images = inline_images.toMutableList().apply { add(k.coerceAtMost(size), img) }
        et.setSelection((marker_pos + 2).coerceAtMost(editable.length))
        body = editable.toString()
        apply_image_span_placeholder(et, marker_pos, uri)
        load_image_span_async(et, uri)
        true
    }

    val try_paste_clipboard_image: () -> Boolean = paste@{
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return@paste false
        val clip = cm.primaryClip ?: return@paste false
        var inserted = false
        for (i in 0 until clip.itemCount) {
            val u = clip.getItemAt(i).uri ?: continue
            val mime = context.contentResolver.getType(u) ?: continue
            if (!mime.startsWith("image/")) continue
            if (insert_image_inline(u)) inserted = true
        }
        inserted
    }
    var draft_status by remember { mutableStateOf("") }
    var draft_save_job by remember { mutableStateOf<Job?>(null) }
    var draft_loaded by remember { mutableStateOf(false) }
    var current_draft_id by rememberSaveable { mutableStateOf(if (mode == "draft") draft_id.orEmpty() else "") }
    val draft_session_id = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val prefs = settings_state.preferences
    val undo_send_enabled = prefs?.undo_send_enabled ?: true
    val undo_send_seconds = prefs?.undo_send_seconds ?: 10

    LaunchedEffect(mode, draft_id, thread_state) {
        if (mode == "draft" && !draft_id.isNullOrBlank() && !draft_loaded) {
            val msg = thread_state.messages.firstOrNull()
            val item = thread_state.item
            if (msg != null && item != null) {
                subject = item.subject
                val raw = msg.body_html ?: msg.body_text
                body = if (raw.contains("<") && raw.contains(">")) {
                    android.text.Html.fromHtml(STYLE_SCRIPT_TAG_RE.replace(raw, ""), android.text.Html.FROM_HTML_MODE_LEGACY)
                        .toString().trimEnd()
                } else raw
                to_chips = msg.to_addresses.filter { it.isNotBlank() }
                cc_chips = msg.cc_addresses.filter { it.isNotBlank() }
                if (cc_chips.isNotEmpty()) cc_expanded = true
                initial_to_chips = to_chips
                initial_subject = subject
                initial_body = body
                initial_cc_chips = cc_chips
                initial_bcc_chips = bcc_chips
                draft_loaded = true
            }
        }
    }

    LaunchedEffect(prefill) {
        if (mode in listOf("reply", "reply_all", "forward")) {
            if (!to_chips_set && prefill.to_chips.isNotEmpty()) {
                to_chips = prefill.to_chips
                to_chips_set = true
            }
            val bare_prefixes = setOf("re:", "fwd:")
            val subject_is_placeholder = subject.isBlank() ||
                subject.trim().lowercase() in bare_prefixes
            val prefill_is_placeholder = prefill.subject.trim().lowercase() in bare_prefixes
            if (!subject_set && prefill.subject.isNotBlank() && subject_is_placeholder) {
                subject = prefill.subject
                if (!prefill_is_placeholder) subject_set = true
            }
            if (!body_set && prefill.body.isNotBlank()) {
                body = prefill.body
                body_set = true
            }
            if (prefill.cc_chips.isNotEmpty() && cc_chips.isEmpty()) {
                cc_chips = prefill.cc_chips
                cc_expanded = true
            }
            initial_to_chips = to_chips
            initial_subject = subject
            initial_cc_chips = cc_chips
            initial_bcc_chips = bcc_chips
        }
    }

    suspend fun attach_uris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        send_error = null
        val existing_names = attachments.map { it.name }.toMutableSet()
        var running_total = attachments.sumOf { it.size } + inline_images.sumOf { it.size }
        val accepted = mutableListOf<AttachmentItem>()
        var error: String? = null
        for (uri in uris) {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                org.astermail.android.share.import_shared_attachment(context, uri)
            }
            when (result) {
                is org.astermail.android.share.AttachmentImport.TooLarge -> {
                    error = oversized_attachment_message(context, result.name)
                    if (AttachmentLimits.can_upgrade()) {
                        org.astermail.android.ui.upgrade.UpgradeStore.show_plan_limit("attachments", null)
                    }
                }
                is org.astermail.android.share.AttachmentImport.Failed ->
                    error = context.getString(R.string.attachment_read_failed, result.name)
                is org.astermail.android.share.AttachmentImport.Imported -> {
                    val item = result.attachment
                    when {
                        existing_names.contains(item.name) ->
                            error = context.getString(R.string.attachment_already_attached, item.name)
                        running_total + item.size > AttachmentLimits.total_max_bytes() ->
                            error = context.getString(
                                R.string.attachment_total_too_large,
                                format_file_size(AttachmentLimits.total_max_bytes()),
                            )
                        else -> {
                            existing_names.add(item.name)
                            running_total += item.size
                            accepted.add(AttachmentItem(item.uri, item.name, item.size, item.mime_type))
                        }
                    }
                }
            }
        }
        if (accepted.isNotEmpty()) attachments = attachments + accepted
        if (error != null) send_error = error
    }

    LaunchedEffect(share_payload) {
        attach_uris(share_payload?.stream_uris.orEmpty())
    }

    val file_picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        scope.launch { attach_uris(uris) }
    }

    val image_picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        scope.launch { attach_uris(uris) }
    }

    val has_any_content = to_chips.isNotEmpty() ||
        to_input.isNotBlank() ||
        subject.isNotBlank() ||
        body.isNotBlank() ||
        attachments.isNotEmpty() ||
        inline_images.isNotEmpty()

    val has_unsaved_changes = to_chips != initial_to_chips ||
        cc_chips != initial_cc_chips ||
        bcc_chips != initial_bcc_chips ||
        subject != initial_subject ||
        body != initial_body ||
        to_input.isNotBlank() ||
        cc_input.isNotBlank() ||
        bcc_input.isNotBlank() ||
        attachments.isNotEmpty() ||
        inline_images.isNotEmpty()

    val has_recipient = to_chips.isNotEmpty() || cc_chips.isNotEmpty() || bcc_chips.isNotEmpty() ||
        to_input.isNotBlank() || cc_input.isNotBlank() || bcc_input.isNotBlank()
    val can_send = has_recipient && !is_sending

    val try_back: () -> Unit = {
        dismiss_keyboard()
        if (has_unsaved_changes) show_discard_dialog = true else on_back()
    }

    val draft_save_thread_token = if (mode == "draft") {
        thread_state.item?.raw_item?.thread_token?.takeIf { it.isNotBlank() }
    } else {
        thread_state.item?.thread_token?.takeIf { it.isNotBlank() }
    }
    val draft_save_reply_to = if (mode == "draft") null else reply_to?.takeIf { it.isNotBlank() }
    val draft_save_type = when {
        mode == "draft" -> if (draft_save_thread_token != null) "reply" else "new"
        mode == "reply" || mode == "reply_all" -> "reply"
        mode == "forward" -> "forward"
        else -> "new"
    }

    fun schedule_draft_save() {
        draft_save_job?.cancel()
        draft_save_job = scope.launch {
            delay(3000)
            if (sent || is_sending) return@launch
            if (subject.isBlank() && body.isBlank() && to_chips.isEmpty()) return@launch
            draft_status = context.getString(R.string.saving)
            val result = mail_vm.save_draft(
                subject = subject,
                body_html = draft_body_with_signature(),
                sender_email = from_alias,
                to = to_chips,
                cc = cc_chips,
                existing_draft_id = current_draft_id.takeIf { it.isNotBlank() },
                draft_type = draft_save_type,
                reply_to_id = draft_save_reply_to,
                thread_token = draft_save_thread_token,
                session_id = draft_session_id,
                on_id_assigned = { assigned -> current_draft_id = assigned },
            )
            if (result.isSuccess) {
                current_draft_id = result.getOrNull().orEmpty()
                draft_status = context.getString(R.string.saved)
            } else {
                draft_status = ""
            }
        }
    }

    LaunchedEffect(draft_status) {
        if (draft_status == context.getString(R.string.saved)) {
            delay(2000)
            draft_status = ""
        }
    }

    fun update_format_state() {
        val et = body_editor_ref.value ?: return
        val editable = et.text ?: return
        val s = et.selectionStart.coerceAtLeast(0)
        val e = et.selectionEnd.coerceAtLeast(s)
        val lo = if (s == e && s > 0) s - 1 else s
        val hi = if (s == e) (lo + 1).coerceAtMost(editable.length) else e
        format_bold.value = editable.getSpans(lo, hi, android.text.style.StyleSpan::class.java).any { it.style == android.graphics.Typeface.BOLD }
        format_italic.value = editable.getSpans(lo, hi, android.text.style.StyleSpan::class.java).any { it.style == android.graphics.Typeface.ITALIC }
        format_underline.value = editable.getSpans(lo, hi, android.text.style.UnderlineSpan::class.java).isNotEmpty()
        format_strike.value = editable.getSpans(lo, hi, android.text.style.StrikethroughSpan::class.java).isNotEmpty()
        format_quote.value = editable.getSpans(lo, hi, android.text.style.QuoteSpan::class.java).isNotEmpty()
    }

    fun apply_inline_span(make_span: () -> Any, is_active: Boolean) {
        val et = body_editor_ref.value ?: return
        val editable = et.text ?: return
        val s = minOf(et.selectionStart, et.selectionEnd).coerceAtLeast(0)
        val e = maxOf(et.selectionStart, et.selectionEnd).coerceAtMost(editable.length)
        if (s >= e) return
        val span_class = make_span()::class.java
        if (is_active) {
            editable.getSpans(s, e, span_class).forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(make_span(), s, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        update_format_state()
        schedule_draft_save()
    }

    fun apply_bullet_list() {
        val et = body_editor_ref.value ?: return
        val editable = et.text ?: return
        val s = et.selectionStart.coerceAtLeast(0)
        val line_start = editable.substring(0, s).lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        val line_end = editable.indexOf('\n', s).let { if (it < 0) editable.length else it }
        val existing = editable.getSpans(line_start, line_end, android.text.style.BulletSpan::class.java)
        if (existing.isNotEmpty()) {
            existing.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(android.text.style.BulletSpan(24), line_start, line_end, android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        schedule_draft_save()
    }

    fun apply_number_list() {
        val et = body_editor_ref.value ?: return
        val editable = et.text ?: return
        val s = et.selectionStart.coerceAtLeast(0)
        val line_start = editable.substring(0, s).lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        val text_at_line = editable.substring(line_start, minOf(line_start + 4, editable.length))
        if (text_at_line.matches(Regex("\\d+\\. .*"))) {
            val dot = text_at_line.indexOf(". ")
            editable.delete(line_start, line_start + dot + 2)
        } else {
            val existing_numbers = editable.substring(0, line_start).split("\n").count { it.matches(Regex("\\d+\\. .*")) }
            editable.insert(line_start, "${existing_numbers + 1}. ")
        }
        schedule_draft_save()
    }

    fun apply_blockquote() {
        val et = body_editor_ref.value ?: return
        val editable = et.text ?: return
        val s = minOf(et.selectionStart, et.selectionEnd).coerceAtLeast(0)
        val e = maxOf(et.selectionStart, et.selectionEnd).coerceAtMost(editable.length)
        val line_start = editable.substring(0, s).lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        val line_end = editable.indexOf('\n', e).let { if (it < 0) editable.length else it }
        val existing = editable.getSpans(line_start, line_end, android.text.style.QuoteSpan::class.java)
        if (existing.isNotEmpty()) {
            existing.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(
                android.text.style.QuoteSpan(),
                line_start,
                line_end,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE,
            )
        }
        update_format_state()
        schedule_draft_save()
    }

    val rule_line_color = colors.border_secondary.toArgb()

    fun apply_horizontal_rule() {
        val et = body_editor_ref.value ?: return
        val editable = et.text ?: return
        val at = et.selectionEnd.coerceIn(0, editable.length)
        val prefix = if (at > 0 && editable[at - 1] != '\n') "\n" else ""
        val inserted = "$prefix \n"
        editable.insert(at, inserted)
        val rule_start = at + prefix.length
        editable.setSpan(
            android.text.Annotation("aster", "hr"),
            rule_start,
            rule_start + 1,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        editable.setSpan(
            horizontal_rule_span(rule_line_color),
            rule_start,
            rule_start + 1,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        et.setSelection((rule_start + 2).coerceAtMost(editable.length))
        schedule_draft_save()
    }

    fun apply_link(url: String) {
        val et = body_editor_ref.value ?: return
        val editable = et.text ?: return
        val normalized = url.trim().let {
            when {
                it.startsWith("http://", true) || it.startsWith("https://", true) || it.startsWith("mailto:", true) -> it
                it.contains('@') && !it.contains(' ') -> "mailto:$it"
                else -> "https://$it"
            }
        }
        if (normalized.length < 8 || normalized.contains(' ')) return
        val s = minOf(et.selectionStart, et.selectionEnd).coerceAtLeast(0)
        val e = maxOf(et.selectionStart, et.selectionEnd).coerceAtMost(editable.length)
        if (s >= e) {
            editable.insert(s, normalized)
            editable.setSpan(
                android.text.style.URLSpan(normalized),
                s,
                s + normalized.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            et.setSelection((s + normalized.length).coerceAtMost(editable.length))
        } else {
            editable.getSpans(s, e, android.text.style.URLSpan::class.java).forEach { editable.removeSpan(it) }
            editable.setSpan(
                android.text.style.URLSpan(normalized),
                s,
                e,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        schedule_draft_save()
    }

    val lifecycle_owner_for_draft = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle_owner_for_draft) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                if (!sent && !is_sending && (subject.isNotBlank() || body.isNotBlank() || to_chips.isNotEmpty())) {
                    draft_save_job?.cancel()
                    mail_vm.save_draft_and_finish(
                        subject = subject,
                        body_html = draft_body_with_signature(),
                        sender_email = from_alias,
                        to = to_chips,
                        cc = cc_chips,
                        existing_draft_id = current_draft_id.takeIf { it.isNotBlank() },
                        draft_type = draft_save_type,
                        reply_to_id = draft_save_reply_to,
                        thread_token = draft_save_thread_token,
                        session_id = draft_session_id,
                    ) { _ -> }
                }
            }
        }
        lifecycle_owner_for_draft.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner_for_draft.lifecycle.removeObserver(observer) }
    }

    val footer_secured_by_plain = stringResource(R.string.compose_footer_secured_by_plain)
    val quote_forwarded_label = stringResource(R.string.compose_quote_forwarded_message)
    val quote_original_label = stringResource(R.string.compose_quote_original_message)
    val quote_show_template = stringResource(R.string.compose_quote_show_label)
    val quote_header_from = stringResource(R.string.compose_quote_header_from)
    val quote_header_date = stringResource(R.string.compose_quote_header_date)
    val quote_header_subject = stringResource(R.string.compose_quote_header_subject)

    fun get_body_with_formatting(): String {
        val et = body_editor_ref.value ?: return body
        val editable = et.text ?: return body
        val has_spans = editable.getSpans(0, editable.length, android.text.style.AbsoluteSizeSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.style.ForegroundColorSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.style.StyleSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.style.UnderlineSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.style.StrikethroughSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.style.BulletSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.style.QuoteSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.style.URLSpan::class.java).isNotEmpty() ||
            editable.getSpans(0, editable.length, android.text.Annotation::class.java).any { it.key == "aster" && it.value == "hr" }
        if (!has_spans) return body
        return render_spanned_html(editable)
    }

    suspend fun prepare_send_data(): Triple<String, List<org.astermail.android.api.send.ExternalAttachmentPayload>, Boolean> {
        val raw_formatted_body = get_body_with_formatting()
        val strip_branding = settings_state.preferences?.show_aster_branding == false
        val branding_footer_kept = !strip_branding && raw_formatted_body.contains(footer_secured_by_plain)
        val body_without_footer = (
            if (strip_branding) raw_formatted_body.replace(footer_secured_by_plain, "")
            else raw_formatted_body.removeSuffix(footer_secured_by_plain)
        ).trimEnd('\n', ' ')
        val signature_below = quoted_html != null &&
            signature_below_quote(
                signature_placement_override,
                settings_state.preferences?.signature_placement,
            )
        val moved_plain_signature = if (
            signature_below &&
            applied_signature.isNotBlank() &&
            body_without_footer.endsWith(applied_signature)
        ) {
            applied_signature
        } else {
            ""
        }
        val body_for_html = if (moved_plain_signature.isBlank()) {
            body_without_footer
        } else {
            body_without_footer.dropLast(moved_plain_signature.length).trimEnd('\n', ' ')
        }
        val strip_metadata_enabled = settings_state.preferences?.strip_exif_on_compose != false
        val unstripped_names = mutableListOf<String>()

        fun apply_metadata_strip(bytes: ByteArray, item: AttachmentItem): ByteArray {
            if (!strip_metadata_enabled) return bytes
            if (!item.mime_type.startsWith("image/")) return bytes
            val result = strip_metadata(bytes)
            if (result.status != strip_status.stripped) unstripped_names.add(item.name)
            return result.data
        }

        val image_html_for = withContext(Dispatchers.IO) {
            val encoded = mutableMapOf<Int, String>()
            inline_images.forEachIndexed { idx, img ->
                val raw_bytes = try {
                    context.contentResolver.openInputStream(img.uri)?.use { it.readBytes() }
                } catch (_: Throwable) {
                    null
                } ?: return@forEachIndexed
                val bytes = apply_metadata_strip(raw_bytes, img)
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                encoded[idx] = "<img src=\"data:${img.mime_type};base64,$b64\" alt=\"${img.name}\" style=\"max-width:100%;height:auto;\" />"
            }
            encoded
        }
        val tokenized = StringBuilder()
        var marker_idx = 0
        for (ch in body_for_html) {
            if (ch == IMG_MARKER) {
                tokenized.append("[[ASTER_IMG_${marker_idx}]]")
                marker_idx++
            } else {
                tokenized.append(ch)
            }
        }
        val raw_text = tokenized.toString()
        val raw_html = if (raw_text.contains("<") && raw_text.contains(">")) {
            raw_text
        } else {
            raw_text.split("\n\n")
                .joinToString("") { paragraph ->
                    val inner = paragraph.replace("\n", "<br>")
                    "<p>$inner</p>"
                }
        }
        var with_images = raw_html
        image_html_for.forEach { (idx, html) ->
            with_images = with_images.replace("[[ASTER_IMG_${idx}]]", html)
        }
        with_images = with_images.replace(Regex("\\[\\[ASTER_IMG_\\d+]]"), "")

        val attachment_payloads = withContext(Dispatchers.IO) {
            attachments.map { att ->
                val raw_bytes = try {
                    context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() }
                } catch (t: Throwable) {
                    throw AttachmentEncodeException(att.name, t)
                } ?: throw AttachmentEncodeException(att.name, null)
                val bytes = apply_metadata_strip(raw_bytes, att)
                val encoded = try {
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } catch (t: OutOfMemoryError) {
                    throw AttachmentEncodeException(att.name, t)
                }
                org.astermail.android.api.send.ExternalAttachmentPayload(
                    data = encoded,
                    filename = att.name,
                    content_type = att.mime_type,
                    size_bytes = bytes.size.toLong(),
                )
            }
        }

        if (unstripped_names.isNotEmpty()) {
            Toast.makeText(
                context,
                context.getString(
                    R.string.compose_metadata_strip_failed,
                    unstripped_names.joinToString(", "),
                ),
                Toast.LENGTH_LONG,
            ).show()
        }

        val quote_block = quoted_html?.let { qh ->
            val (from_addr, ts, subj) = quoted_meta ?: Triple("", "", "")
            "<br><div class=\"aster_quote gmail_quote\">" +
                "<div class=\"aster_quote_attr gmail_attr\" style=\"color:#555;font-size:13px\">" +
                "<div><b>${escape_html(quote_header_from)}</b> ${escape_html(from_addr)}</div>" +
                "<div><b>${escape_html(quote_header_date)}</b> ${escape_html(format_quote_timestamp(ts))}</div>" +
                "<div><b>${escape_html(quote_header_subject)}</b> ${escape_html(subj)}</div>" +
                "</div>" +
                "<blockquote class=\"gmail_quote\" style=\"margin:8px 0 0;padding-left:12px;border-left:2px solid #ccc;color:#555;font-size:13px\">" +
                qh +
                "</blockquote></div>"
        }.orEmpty()
        val html_separator = if (signature_separator_enabled) "--<br>" else ""
        val signature_block = if (signature_html.isNotBlank()) {
            "<br><br><div class=\"aster_signature\">" + html_separator + signature_html + "</div>"
        } else {
            ""
        }
        val moved_signature_block = if (moved_plain_signature.isBlank()) {
            ""
        } else {
            "<br><div class=\"aster_signature\">" +
                moved_plain_signature.split("\n").joinToString("<br>") { escape_html(it) } +
                "</div>"
        }
        val body_html = if (signature_below) {
            with_images + quote_block + signature_block + moved_signature_block
        } else {
            with_images + signature_block + quote_block
        }

        return Triple(body_html, attachment_payloads, !branding_footer_kept)
    }

    val send_lock = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    fun execute_send(
        body_html: String,
        attachment_payloads: List<org.astermail.android.api.send.ExternalAttachmentPayload>,
        snapshot_to: List<String> = to_chips.toList(),
        snapshot_cc: List<String> = cc_chips.toList(),
        snapshot_bcc: List<String> = bcc_chips.toList(),
        snapshot_subject: String = subject,
        snapshot_from: String = from_alias,
        suppress_branding: Boolean = false,
        allow_non_post_quantum: Boolean = false,
    ) {
        if (snapshot_to.isEmpty()) {
            is_sending = false
            send_lock.set(false)
            send_error = context.getString(R.string.recipients_required)
            return
        }
        scope.launch {
            runCatching { draft_save_job?.cancelAndJoin() }
            val display_name = resolve_sender_display_name(snapshot_from)
            val resolved_thread_token = if (!reply_to.isNullOrBlank() && (mode == "reply" || mode == "reply_all")) {
                mail_vm.get_or_create_thread_token(reply_to, thread_state.item?.thread_token)
            } else {
                null
            }
            val result = mail_vm.send_email(
                to = snapshot_to,
                cc = snapshot_cc,
                bcc = snapshot_bcc,
                subject = snapshot_subject,
                body_html = body_html,
                sender_email = snapshot_from,
                sender_display_name = display_name,
                thread_token = resolved_thread_token,
                expires_at = expires_at_iso,
                expiry_password = expiry_password,
                attachments = attachment_payloads,
                sender_alias_hash = if (snapshot_from != user_email) alias_hash_map[snapshot_from]?.takeIf { it.isNotBlank() } else null,
                suppress_branding = suppress_branding,
                allow_non_post_quantum = allow_non_post_quantum,
            )
            result.fold(
                onSuccess = { resp ->
                    is_sending = false
                    send_lock.set(false)
                    if (resp.success) {
                        if (resolved_thread_token != null) {
                            mail_vm.refresh_current_thread()
                        }
                        sent = true
                        mail_vm.discard_sent_draft(current_draft_id, draft_session_id)
                        current_draft_id = ""
                        on_sent()
                    } else {
                        send_error = resp.message ?: context.getString(R.string.save_failed)
                    }
                },
                onFailure = { t ->
                    is_sending = false
                    send_lock.set(false)
                    send_error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.save_failed))
                },
            )
        }
    }

    fun do_send(skip_from_guard: Boolean = false, allow_non_post_quantum: Boolean = false) {
        if (!skip_from_guard && reply_from_mismatch(mode, received_on_alias, from_alias)) {
            show_from_mismatch_dialog = true
            return
        }
        if (!send_lock.compareAndSet(false, true)) return
        to_input.trim().let { if (it.isNotEmpty() && is_valid_email_chip(it)) { to_chips = to_chips + it; to_input = "" } }
        cc_input.trim().let { if (it.isNotEmpty() && is_valid_email_chip(it)) { cc_chips = cc_chips + it; cc_input = "" } }
        bcc_input.trim().let { if (it.isNotEmpty() && is_valid_email_chip(it)) { bcc_chips = bcc_chips + it; bcc_input = "" } }
        if (to_chips.isEmpty() && cc_chips.isEmpty() && bcc_chips.isEmpty()) { send_lock.set(false); return }
        if (is_sending) { send_lock.set(false); return }
        dismiss_keyboard()
        is_sending = true
        send_error = null

        val snap_to = to_chips.toList()
        val snap_cc = cc_chips.toList()
        val snap_bcc = bcc_chips.toList()
        val snap_subject = subject
        val snap_from = from_alias

        if (settings_state.preferences?.auto_save_recent_recipients != false) {
            contacts_vm.auto_save_recipients(
                recipients = snap_to + snap_cc + snap_bcc,
                own_addresses = buildSet {
                    if (user_email.isNotBlank()) add(user_email)
                    addAll(alias_hash_map.keys)
                },
            )
        }

        scope.launch {
            runCatching { draft_save_job?.cancelAndJoin() }
            val prepared = try {
                prepare_send_data()
            } catch (e: AttachmentEncodeException) {
                is_sending = false
                send_lock.set(false)
                send_error = context.getString(R.string.compose_attachment_read_failed, e.filename)
                return@launch
            } catch (e: OutOfMemoryError) {
                is_sending = false
                send_lock.set(false)
                send_error = context.getString(
                    R.string.attachment_total_too_large,
                    format_file_size(AttachmentLimits.total_max_bytes()),
                )
                return@launch
            }
            val (body_html, attachment_payloads, suppress_branding) = prepared

            if (!allow_non_post_quantum && !scheduled_send) {
                val missing = mail_vm.check_post_quantum_coverage(
                    recipients = snap_to + snap_cc + snap_bcc,
                    sender_email = snap_from,
                )
                if (missing.isNotEmpty()) {
                    is_sending = false
                    send_lock.set(false)
                    post_quantum_missing = missing
                    return@launch
                }
            }

            if (scheduled_send) {
                if (attachment_payloads.isNotEmpty()) {
                    is_sending = false
                    send_lock.set(false)
                    send_error = context.getString(R.string.scheduled_send_no_attachments)
                    return@launch
                }
                val scheduled_at = scheduled_at_iso ?: java.time.Instant.now().plus(java.time.Duration.ofHours(1)).toString()
                val result = mail_vm.schedule_email(
                    to = snap_to,
                    cc = snap_cc,
                    bcc = snap_bcc,
                    subject = snap_subject,
                    body_html = body_html,
                    sender_email = snap_from,
                    sender_display_name = resolve_sender_display_name(snap_from),
                    scheduled_at = scheduled_at,
                    sender_alias_hash = if (snap_from != user_email) alias_hash_map[snap_from]?.takeIf { it.isNotBlank() } else null,
                )
                is_sending = false
                send_lock.set(false)
                result.fold(
                    onSuccess = {
                        sent = true
                        mail_vm.discard_sent_draft(current_draft_id, draft_session_id)
                        current_draft_id = ""
                        on_sent()
                    },
                    onFailure = { t ->
                        send_error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.save_failed))
                    },
                )
                return@launch
            }

            if (undo_send_enabled) {
                val resolved_thread_token = if (!reply_to.isNullOrBlank() && (mode == "reply" || mode == "reply_all")) {
                    runCatching { mail_vm.get_or_create_thread_token(reply_to, thread_state.item?.thread_token) }.getOrNull()
                } else {
                    null
                }
                val result = mail_vm.schedule_send_with_undo(
                    to = snap_to,
                    cc = snap_cc,
                    bcc = snap_bcc,
                    subject = snap_subject,
                    body_html = body_html,
                    sender_email = snap_from,
                    sender_display_name = resolve_sender_display_name(snap_from),
                    thread_token = resolved_thread_token,
                    expires_at = expires_at_iso,
                    expiry_password = expiry_password,
                    attachments = attachment_payloads,
                    sender_alias_hash = if (snap_from != user_email) alias_hash_map[snap_from]?.takeIf { it.isNotBlank() } else null,
                    suppress_branding = suppress_branding,
                    undo_seconds = undo_send_seconds,
                    draft_id = current_draft_id.takeIf { it.isNotBlank() },
                    allow_non_post_quantum = allow_non_post_quantum,
                )
                is_sending = false
                send_lock.set(false)
                result.fold(
                    onSuccess = {
                        sent = true
                        mail_vm.release_draft_session(draft_session_id)
                        on_sent()
                    },
                    onFailure = { t ->
                        send_error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.save_failed))
                    },
                )
            } else {
                execute_send(
                    body_html,
                    attachment_payloads,
                    snap_to,
                    snap_cc,
                    snap_bcc,
                    snap_subject,
                    snap_from,
                    suppress_branding,
                    allow_non_post_quantum,
                )
            }
        }
    }

    BackHandler { try_back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = TablerIcons.ArrowLeft,
                content_description = stringResource(R.string.back),
                onClick = try_back,
                tint = colors.text_primary,
                modifier = Modifier.testTag("back"),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            val mode_selectable = effective_mode in listOf("reply", "reply_all", "forward")
            Column(modifier = Modifier.weight(1f)) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (mode_selectable) {
                                    Modifier.clickable { mode_menu_open = true }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = if (mode_selectable) 6.dp else 0.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (effective_mode) {
                                "reply", "reply_all" -> stringResource(R.string.compose_reply)
                                "forward" -> stringResource(R.string.compose_forward)
                                else -> stringResource(R.string.new_message)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text_primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (mode_selectable) {
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = TablerIcons.ChevronDown,
                                contentDescription = stringResource(R.string.more_options),
                                tint = colors.text_tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    aster_dropdown_menu(
                        expanded = mode_menu_open,
                        on_dismiss = { mode_menu_open = false },
                    ) {
                        compose_mode_options.forEach { option ->
                            aster_dropdown_item(
                                label = stringResource(option.second.second),
                                icon = option.second.first,
                                selected = option.first == effective_mode,
                                on_click = {
                                    mode_menu_open = false
                                    if (option.first != effective_mode) {
                                        mode_override = option.first
                                        to_chips_set = false
                                        subject_set = false
                                        to_chips = emptyList()
                                        cc_chips = emptyList()
                                        cc_expanded = false
                                        subject = ""
                                    }
                                },
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = draft_status.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = draft_status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draft_status == stringResource(R.string.save_failed)) colors.danger else colors.text_muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AsterIconButton(
                icon = TablerIcons.Paperclip,
                content_description = stringResource(R.string.attach),
                onClick = { show_attach_sheet = true },
                modifier = Modifier.testTag("attach"),
            )
            AsterIconButton(
                icon = TablerIcons.DotsVertical,
                content_description = stringResource(R.string.more_options),
                onClick = { show_overflow_sheet = true },
            )
            Spacer(Modifier.width(AsterSpacing.xs))
            androidx.compose.animation.Crossfade(targetState = is_sending, label = "send_state") { sending ->
                if (sending) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.accent_blue),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    send_fab(enabled = can_send, on_click = { do_send() })
                }
            }
            Spacer(Modifier.width(AsterSpacing.sm))
        }

        AsterDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            field_row(label = stringResource(R.string.from)) {
                var from_expanded by remember(from_alias) { mutableStateOf(false) }
                var from_truncated by remember(from_alias) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open_from_sheet() }
                        .testTag("from_field"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    org.astermail.android.ui.mail.SenderAvatar(
                        email = from_alias,
                        name = settings_state.user?.display_name.orEmpty(),
                        size = 24.dp,
                        profile_picture_url = settings_state.user?.profile_picture,
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = from_alias,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.text_primary,
                        maxLines = if (from_expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!from_expanded) from_truncated = it.hasVisualOverflow },
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = {
                                    if (from_truncated || from_expanded) {
                                        from_expanded = !from_expanded
                                    } else {
                                        open_from_sheet()
                                    }
                                },
                                onLongClick = { copy_from_address(from_alias) },
                            ),
                    )
                    Icon(
                        imageVector = TablerIcons.ChevronDown,
                        contentDescription = stringResource(R.string.send_from),
                        tint = colors.text_tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AsterDivider()

            field_row(label = stringResource(R.string.to)) {
                chip_input(
                    chips = to_chips,
                    input = to_input,
                    focus_requester = to_focus_requester,
                    on_input_change = { value ->
                        val result = parse_chips(value)
                        to_chips = to_chips + result.new_chips
                        to_input = result.remaining
                        schedule_draft_save()
                    },
                    on_commit = {
                        val trimmed = to_input.trim()
                        if (trimmed.isNotEmpty() && is_valid_email_chip(trimmed)) {
                            to_chips = to_chips + trimmed
                            to_input = ""
                            schedule_draft_save()
                        }
                    },
                    on_remove = { idx ->
                        to_chips = to_chips.filterIndexed { i, _ -> i != idx }
                        schedule_draft_save()
                    },
                    trailing = {
                        caret_toggle(
                            expanded = cc_expanded,
                            on_toggle = { cc_expanded = !cc_expanded },
                        )
                    },
                    suggestions = all_contacts.filter { it.email.isNotBlank() },
                    on_suggestion_pick = { email ->
                        to_chips = to_chips + email
                        to_input = ""
                        schedule_draft_save()
                    },
                )
            }
            AsterDivider()

            AnimatedVisibility(
                visible = cc_expanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 250, easing = org.astermail.android.design.AsterEasing.emphasized_enter),
                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = org.astermail.android.design.AsterEasing.emphasized_exit),
                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)),
            ) {
            Column {
            field_row(label = stringResource(R.string.cc)) {
                    chip_input(
                        chips = cc_chips,
                        input = cc_input,
                        on_input_change = { value ->
                            val result = parse_chips(value)
                            cc_chips = cc_chips + result.new_chips
                            cc_input = result.remaining
                            if (result.new_chips.isNotEmpty()) schedule_draft_save()
                        },
                        on_commit = {
                            val trimmed = cc_input.trim()
                            if (trimmed.isNotEmpty() && is_valid_email_chip(trimmed)) {
                                cc_chips = cc_chips + trimmed
                                cc_input = ""
                                schedule_draft_save()
                            }
                        },
                        on_remove = { idx ->
                            cc_chips = cc_chips.filterIndexed { i, _ -> i != idx }
                            schedule_draft_save()
                        },
                        suggestions = all_contacts.filter { it.email.isNotBlank() },
                        on_suggestion_pick = { email ->
                            cc_chips = cc_chips + email
                            cc_input = ""
                            schedule_draft_save()
                        },
                    )
                }
                AsterDivider()
                field_row(label = stringResource(R.string.bcc)) {
                    chip_input(
                        chips = bcc_chips,
                        input = bcc_input,
                        on_input_change = { value ->
                            val result = parse_chips(value)
                            bcc_chips = bcc_chips + result.new_chips
                            bcc_input = result.remaining
                            if (result.new_chips.isNotEmpty()) schedule_draft_save()
                        },
                        on_commit = {
                            val trimmed = bcc_input.trim()
                            if (trimmed.isNotEmpty() && is_valid_email_chip(trimmed)) {
                                bcc_chips = bcc_chips + trimmed
                                bcc_input = ""
                                schedule_draft_save()
                            }
                        },
                        on_remove = { idx ->
                            bcc_chips = bcc_chips.filterIndexed { i, _ -> i != idx }
                            schedule_draft_save()
                        },
                        suggestions = all_contacts.filter { it.email.isNotBlank() },
                        on_suggestion_pick = { email ->
                            bcc_chips = bcc_chips + email
                            bcc_input = ""
                            schedule_draft_save()
                        },
                    )
                }
                AsterDivider()
            }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
            ) {
                BasicTextField(
                    value = subject,
                    onValueChange = {
                        subject = it
                        schedule_draft_save()
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        color = colors.text_primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = SolidColor(colors.accent_blue),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (subject.isEmpty()) {
                            Text(
                                text = stringResource(R.string.subject),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.text_muted,
                            )
                        }
                        inner()
                    },
                )
            }
            AsterDivider()

            AnimatedVisibility(
                visible = send_error != null,
                enter = androidx.compose.animation.expandVertically() + fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.danger.copy(alpha = 0.12f))
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = send_error ?: "",
                        color = colors.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { send_error = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TablerIcons.X,
                            contentDescription = stringResource(R.string.dismiss),
                            tint = colors.danger,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
            ) {
                val text_color_argb = colors.text_primary.toArgb()
                val muted_color_argb = colors.text_muted.toArgb()
                val cursor_color_argb = colors.accent_blue.toArgb()
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        RichBodyEditText(ctx).apply {
                            background = null
                            setPadding(0, 0, 0, 0)
                            compoundDrawablePadding = 0
                            gravity = android.view.Gravity.TOP or android.view.Gravity.START
                            setTextColor(text_color_argb)
                            setHintTextColor(muted_color_argb)
                            hint = ctx.getString(R.string.compose_email_hint)
                            textSize = 16f
                            setLineSpacing(0f, 1f)
                            includeFontPadding = false
                            isSingleLine = false
                            setHorizontallyScrolling(false)
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
                            minHeight = (200 * resources.displayMetrics.density).toInt()
                            try {
                                val cursor_drawable = android.graphics.drawable.GradientDrawable().apply {
                                    setSize((2 * resources.displayMetrics.density).toInt(), 0)
                                    setColor(cursor_color_argb)
                                }
                                textCursorDrawable = cursor_drawable
                            } catch (_: Throwable) {}
                            highlightColor = (cursor_color_argb and 0x00FFFFFF) or 0x55000000.toInt()
                            try {
                                val handle_drawable = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(cursor_color_argb)
                                    cornerRadius = 8f * resources.displayMetrics.density
                                    setSize((20 * resources.displayMetrics.density).toInt(), (20 * resources.displayMetrics.density).toInt())
                                }
                                setTextSelectHandle(handle_drawable)
                                setTextSelectHandleLeft(handle_drawable)
                                setTextSelectHandleRight(handle_drawable)
                            } catch (_: Throwable) {}
                            on_image_received = { uri ->
                                if (insert_image_inline(uri)) schedule_draft_save()
                            }
                            on_paste_clipboard = {
                                if (try_paste_clipboard_image()) {
                                    schedule_draft_save()
                                    true
                                } else false
                            }
                            on_selection_changed = { _, _ -> update_format_state() }
                            on_format_requested = { show_format_bar.value = !show_format_bar.value }
                            addTextChangedListener(object : android.text.TextWatcher {
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    if (suspend_text_watcher) return
                                    val new_text = s?.toString().orEmpty()
                                    val new_marker_count = new_text.count { it == IMG_MARKER }
                                    if (new_marker_count < inline_images.size && s != null) {
                                        val survivors = mutableListOf<Uri>()
                                        for (i in new_text.indices) {
                                            if (new_text[i] != IMG_MARKER) continue
                                            val spans = s.getSpans(i, i + 1, AsterImageSpan::class.java)
                                            spans.firstOrNull()?.image_uri?.let { survivors.add(it) }
                                        }
                                        inline_images = inline_images.filter { it.uri in survivors }
                                    }
                                    if (new_text != body) {
                                        body = new_text
                                        schedule_draft_save()
                                    }
                                }
                                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                            })
                            setText(body)
                            text?.let { apply_compose_defaults(it) }
                            val has_signature = body.startsWith("\n\n") && body.length > 2
                            setSelection(if (has_signature) 0 else text?.length ?: 0)
                            body_editor_ref.value = this
                        }
                    },
                    update = { et ->
                        val target_min = ((if (quoted_html != null) 72 else 200) * et.resources.displayMetrics.density).toInt()
                        if (et.minHeight != target_min) et.minHeight = target_min
                        val current = et.text?.toString().orEmpty()
                        if (current != body) {
                            val sel = et.selectionStart.coerceIn(0, body.length)
                            et.suspend_text_watcher = true
                            et.setText(body)
                            et.suspend_text_watcher = false
                            et.text?.let { apply_compose_defaults(it) }
                            et.setSelection(sel)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = if (quoted_html != null) 72.dp else 200.dp),
                )

            }

            if (signature_html.isNotBlank()) {
                signature_preview_card(html = signature_html)
            }

            if (quoted_html != null) {
                val quote_toggle_label = quote_show_template.format(
                    if (mode == "forward") quote_forwarded_label else quote_original_label,
                )
                val quote_text_argb = colors.text_muted.toArgb()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(SquircleShape(16.dp))
                            .background(colors.bg_secondary)
                            .clickable { quoted_expanded = !quoted_expanded }
                            .semantics { contentDescription = quote_toggle_label }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                            .testTag("compose_quote_toggle"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "•••",
                            color = colors.text_muted,
                            fontSize = 15.sp,
                        )
                    }
                    AnimatedVisibility(
                        visible = quoted_expanded,
                        enter = androidx.compose.animation.expandVertically() + fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
                    ) {
                        val (quoted_from, quoted_ts, quoted_subject) = quoted_meta
                            ?: Triple("", "", "")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.sm),
                        ) {
                            Text(
                                text = "$quote_header_from $quoted_from",
                                color = colors.text_muted,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = "$quote_header_date ${format_quote_timestamp(quoted_ts)}",
                                color = colors.text_muted,
                                fontSize = 12.sp,
                            )
                            if (quoted_subject.isNotBlank()) {
                                Text(
                                    text = "$quote_header_subject $quoted_subject",
                                    color = colors.text_muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Spacer(Modifier.height(AsterSpacing.sm))
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.TextView(ctx).apply {
                                        setTextColor(quote_text_argb)
                                        textSize = 14f
                                        setPadding(
                                            (12 * resources.displayMetrics.density).toInt(),
                                            0,
                                            0,
                                            0,
                                        )
                                        setTextIsSelectable(true)
                                    }
                                },
                                update = { tv ->
                                    tv.text = android.text.Html.fromHtml(
                                        STYLE_SCRIPT_TAG_RE.replace(
                                            org.astermail.android.ui.mail.EmailHtmlSanitizer
                                                .repair_comment_markup(quoted_html),
                                            "",
                                        ),
                                        android.text.Html.FROM_HTML_MODE_COMPACT,
                                    ).toString().trim()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clickable(
                            interactionSource = remember {
                                androidx.compose.foundation.interaction.MutableInteractionSource()
                            },
                            indication = null,
                        ) {
                            body_editor_ref.value?.let { et ->
                                et.requestFocus()
                                et.setSelection(et.text?.length ?: 0)
                            }
                        },
                )
            }

            Spacer(Modifier.height(AsterSpacing.lg))

            AnimatedVisibility(
                visible = attachments.isNotEmpty(),
                enter = androidx.compose.animation.expandVertically() + fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
            ) {
                Column {
                AsterDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    attachments.forEachIndexed { idx, att ->
                        val att_desc = "${att.name}, ${format_file_size(att.size)}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bg_secondary, SquircleShape(18.dp))
                                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
                                .semantics(mergeDescendants = true) { contentDescription = att_desc }
                                .testTag(if (idx == 0) "attachment_chip" else "attachment_chip_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = TablerIcons.Paperclip,
                                contentDescription = null,
                                tint = colors.text_muted,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = att.name,
                                color = colors.text_primary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = format_file_size(att.size),
                                color = colors.text_muted,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        attachments = attachments.filterIndexed { i, _ -> i != idx }
                                    }
                                    .testTag("remove_attachment_$idx"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = TablerIcons.X,
                                    contentDescription = stringResource(R.string.remove),
                                    tint = colors.text_muted,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                }
            }
        }

        AnimatedVisibility(
            visible = show_format_bar.value,
            enter = androidx.compose.animation.expandVertically() + fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
        ) {
            Column {
                AsterDivider()
                compose_format_row(
                    bold = format_bold.value,
                    italic = format_italic.value,
                    underline = format_underline.value,
                    strike = format_strike.value,
                    quote = format_quote.value,
                    on_bold = { apply_inline_span({ android.text.style.StyleSpan(android.graphics.Typeface.BOLD) }, format_bold.value) },
                    on_italic = { apply_inline_span({ android.text.style.StyleSpan(android.graphics.Typeface.ITALIC) }, format_italic.value) },
                    on_underline = { apply_inline_span({ android.text.style.UnderlineSpan() }, format_underline.value) },
                    on_strike = { apply_inline_span({ android.text.style.StrikethroughSpan() }, format_strike.value) },
                    on_bullet = { apply_bullet_list() },
                    on_number = { apply_number_list() },
                    on_quote = { apply_blockquote() },
                    on_link = { link_dialog_text = "" },
                    on_rule = { apply_horizontal_rule() },
                    on_close = { show_format_bar.value = false },
                )
            }
        }
    }

    if (show_attach_sheet) {
        AttachSheet(
            on_close = { show_attach_sheet = false },
            on_pick_file = {
                show_attach_sheet = false
                file_picker.launch(arrayOf("*/*"))
            },
            on_pick_photo = {
                show_attach_sheet = false
                image_picker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
        )
    }
    if (show_from_sheet) {
        FromAliasSheet(
            current = from_alias,
            primary = primary_sender_email,
            options = alias_options,
            custom_domain_set = settings_state.custom_domain_addresses.map { it.address }.toSet(),
            on_close = { show_from_sheet = false },
            on_select = { selected ->
                from_alias = selected
                from_manually_selected = true
                show_from_sheet = false
            },
            on_set_primary = { selected ->
                val next = if (selected == primary_sender_email) {
                    null
                } else {
                    sender_id_for_email(
                        selected,
                        user_email,
                        settings_state.aliases,
                        settings_state.ghost_aliases,
                        settings_state.custom_domain_addresses,
                    )
                }
                settings_vm.set_default_sender(next)
            },
            on_create_ghost_alias = {
                show_from_sheet = false
                show_ghost_alias_sheet = true
            },
        )
    }
    if (show_overflow_sheet) {
        OverflowSheet(
            scheduled_send = scheduled_send,
            expiring = expiring,
            on_close = { show_overflow_sheet = false },
            on_toggle_scheduled = {
                if (scheduled_send) {
                    scheduled_send = false
                    scheduled_at_iso = null
                } else {
                    show_overflow_sheet = false
                    show_schedule_picker = true
                }
            },
            on_toggle_expiring = {
                if (expiring) {
                    expiring = false
                    expires_at_iso = null
                    expiry_password = null
                } else {
                    show_overflow_sheet = false
                    show_expiring_sheet = true
                }
            },
            on_open_templates = {
                show_overflow_sheet = false
                templates_vm.load()
                show_template_sheet = true
            },
            on_open_signature = {
                show_overflow_sheet = false
                show_signature_sheet = true
            },
        )
    }

    if (show_template_sheet) {
        TemplatePickerSheet(
            items = templates_state.items,
            is_loading = templates_state.is_loading,
            on_close = { show_template_sheet = false },
            on_pick = { tpl ->
                show_template_sheet = false
                body = insert_template_body(
                    body = body,
                    template = tpl.content,
                    signature = applied_signature,
                    watermark = context.getString(R.string.compose_footer_secured_by_plain),
                )
                Toast.makeText(context, context.getString(R.string.template_inserted), Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (show_signature_sheet) {
        SignaturePickerSheet(
            signatures = signatures_list,
            current_id = manual_signature_id,
            on_close = { show_signature_sheet = false },
            on_pick = { picked_id ->
                show_signature_sheet = false
                val picked = if (picked_id == null) null
                    else signatures_list.firstOrNull { it.id == picked_id }
                val new_content = decorate_plain_signature(
                    picked?.takeIf { !it.is_html }?.content.orEmpty(),
                    signature_separator_enabled,
                )
                signature_html = picked?.takeIf { it.is_html }?.content.orEmpty()
                signature_placement_override = picked?.placement
                val watermark = context.getString(R.string.compose_footer_secured_by_plain)
                val watermark_suffix = "\n\n${watermark}"
                val kept_suffix = if (body.endsWith(watermark_suffix)) watermark_suffix else ""
                val core = body.substring(0, body.length - kept_suffix.length)
                val new_core = if (applied_signature.isNotBlank() && core.endsWith(applied_signature)) {
                    val before = core.substring(0, core.length - applied_signature.length)
                    if (new_content.isNotBlank()) before + new_content else before.trimEnd('\n')
                } else if (applied_signature.isBlank() && new_content.isNotBlank()) {
                    "${core}\n\n${new_content}"
                } else core
                body = new_core + kept_suffix
                applied_signature = new_content
                manual_signature_id = picked_id
            },
        )
    }

    if (show_ghost_alias_sheet) {
        GhostAliasSheet(
            on_close = { show_ghost_alias_sheet = false },
            on_pick = { days ->
                show_ghost_alias_sheet = false
                scope.launch {
                    when (val result = settings_vm.create_ghost_alias_now(note = "${days}d")) {
                        is SettingsViewModel.GhostAliasResult.Success -> {
                            from_alias = result.address
                            from_manually_selected = true
                            settings_vm.load_aliases()
                            Toast.makeText(context, context.getString(R.string.ghost_alias_created, result.address), Toast.LENGTH_LONG).show()
                        }
                        is SettingsViewModel.GhostAliasResult.Failure -> {
                            send_error = result.message
                        }
                    }
                }
            },
        )
    }

    if (show_expiring_sheet) {
        ExpiringSheet(
            on_close = { show_expiring_sheet = false },
            on_pick = { expires_epoch_ms, label, password ->
                show_expiring_sheet = false
                expires_at_iso = java.time.Instant.ofEpochMilli(expires_epoch_ms).toString()
                expiry_password = password
                expiring = true
                Toast.makeText(context, context.getString(R.string.message_expires_in, label), Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (show_schedule_picker) {
        val picker_context = LocalContext.current
        val picker_theme = picker_theme_res()
        LaunchedEffect(Unit) {
            val calendar = java.util.Calendar.getInstance()
            val date_picker = android.app.DatePickerDialog(
                picker_context,
                picker_theme,
                { _, year, month, day ->
                    val time_picker = android.app.TimePickerDialog(
                        picker_context,
                        picker_theme,
                        { _, hour, minute ->
                            val cal = java.util.Calendar.getInstance()
                            cal.set(year, month, day, hour, minute, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            if (cal.timeInMillis <= System.currentTimeMillis()) {
                                show_schedule_picker = false
                                send_error = context.getString(R.string.schedule_time_in_past)
                            } else {
                                scheduled_at_iso = java.time.Instant.ofEpochMilli(cal.timeInMillis).toString()
                                scheduled_send = true
                                show_schedule_picker = false
                            }
                        },
                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                        calendar.get(java.util.Calendar.MINUTE),
                        true,
                    )
                    time_picker.setOnCancelListener { show_schedule_picker = false }
                    time_picker.show()
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH),
            )
            date_picker.datePicker.minDate = System.currentTimeMillis()
            date_picker.setOnCancelListener {
                show_schedule_picker = false
                scheduled_send = false
            }
            date_picker.show()
        }
    }

    link_dialog_text?.let { pending_url ->
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { link_dialog_text = null },
            title = stringResource(R.string.insert_link),
            body = {
                org.astermail.android.design.components.AsterTextField(
                    value = pending_url,
                    onValueChange = { link_dialog_text = it },
                    label = stringResource(R.string.link_url_label),
                    placeholder = stringResource(R.string.link_url_placeholder),
                    keyboard_options = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    ),
                )
            },
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { link_dialog_text = null },
                )
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.insert),
                    enabled = pending_url.isNotBlank(),
                    onClick = {
                        apply_link(pending_url)
                        link_dialog_text = null
                    },
                )
            },
        )
    }

    if (post_quantum_missing.isNotEmpty()) {
        val missing_list = post_quantum_missing.joinToString(", ")
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { post_quantum_missing = emptyList() },
            title = stringResource(R.string.post_quantum_unavailable_title),
            message = stringResource(R.string.post_quantum_unavailable_message, missing_list),
            footer = {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                        4.dp,
                        Alignment.End,
                    ),
                ) {
                    androidx.compose.material3.TextButton(
                        modifier = androidx.compose.ui.Modifier.testTag("post_quantum_cancel"),
                        onClick = { post_quantum_missing = emptyList() },
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                    }
                    androidx.compose.material3.TextButton(
                        modifier = androidx.compose.ui.Modifier.testTag("post_quantum_send_anyway"),
                        onClick = {
                            post_quantum_missing = emptyList()
                            do_send(skip_from_guard = true, allow_non_post_quantum = true)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.post_quantum_send_anyway),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                        )
                    }
                }
            },
        )
    }

    if (show_from_mismatch_dialog) {
        val received_address = received_on_alias.orEmpty()
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_from_mismatch_dialog = false },
            title = stringResource(R.string.reply_from_mismatch_title),
            message = stringResource(R.string.reply_from_mismatch_message, received_address, from_alias),
            footer = {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                        4.dp,
                        Alignment.End,
                    ),
                ) {
                    androidx.compose.material3.TextButton(
                        modifier = androidx.compose.ui.Modifier.testTag("mismatch_cancel"),
                        onClick = { show_from_mismatch_dialog = false },
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                    }
                    androidx.compose.material3.TextButton(
                        modifier = androidx.compose.ui.Modifier.testTag("mismatch_send_anyway"),
                        onClick = {
                            show_from_mismatch_dialog = false
                            do_send(skip_from_guard = true)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.reply_from_mismatch_send_anyway),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                    }
                    androidx.compose.material3.TextButton(
                        modifier = androidx.compose.ui.Modifier.testTag("mismatch_use_received"),
                        onClick = {
                            show_from_mismatch_dialog = false
                            if (received_address.isNotBlank()) {
                                from_alias = received_address
                                from_manually_selected = true
                            }
                            do_send(skip_from_guard = true)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.reply_from_mismatch_use_received),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
        )
    }

    if (show_discard_dialog) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_discard_dialog = false },
            title = stringResource(R.string.discard_draft),
            message = stringResource(R.string.discard_draft_description),
            footer = {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    org.astermail.android.design.components.AsterDialogDestructiveButton(
                        label = stringResource(R.string.discard),
                        onClick = {
                            show_discard_dialog = false
                            on_back()
                        },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        org.astermail.android.design.components.AsterDialogOutlineButton(
                            label = stringResource(R.string.cancel),
                            onClick = { show_discard_dialog = false },
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                        )
                        org.astermail.android.design.components.AsterDialogPrimaryButton(
                            label = stringResource(R.string.save_draft),
                            onClick = {
                                show_discard_dialog = false
                                draft_save_job?.cancel()
                                mail_vm.save_draft_and_finish(
                                    subject = subject,
                                    body_html = draft_body_with_signature(),
                                    sender_email = from_alias,
                                    to = to_chips,
                                    cc = cc_chips,
                                    existing_draft_id = current_draft_id.takeIf { it.isNotBlank() },
                                    draft_type = draft_save_type,
                                    reply_to_id = draft_save_reply_to,
                                    thread_token = draft_save_thread_token,
                                    session_id = draft_session_id,
                                ) { ok ->
                                    if (ok) on_back()
                                }
                            },
                            modifier = androidx.compose.ui.Modifier.weight(1f),
                        )
                    }
                }
            },
        )
    }
}

private fun oversized_attachment_message(
    context: android.content.Context,
    file_name: String,
): String {
    val max_size = format_file_size(AttachmentLimits.max_bytes())
    return if (AttachmentLimits.can_upgrade()) {
        context.getString(
            R.string.attachment_too_large_upgradable,
            file_name,
            max_size,
            format_file_size(AttachmentLimits.paid_max_bytes),
        )
    } else {
        context.getString(R.string.attachment_too_large, file_name, max_size)
    }
}

private fun format_file_size(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
private fun send_fab(enabled: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (enabled) colors.accent_blue else colors.bg_hover
    val tint = if (enabled) Color.White else colors.text_muted
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TablerIcons.Send,
            contentDescription = stringResource(R.string.send),
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun caret_toggle(expanded: Boolean, on_toggle: () -> Unit) {
    val colors = AsterMaterial.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "caret_rotation",
    )
    Icon(
        imageVector = TablerIcons.ChevronDown,
        contentDescription = stringResource(R.string.toggle_cc_bcc),
        tint = colors.text_tertiary,
        modifier = Modifier
            .size(22.dp)
            .rotate(rotation)
            .clip(CircleShape)
            .clickable(onClick = on_toggle),
    )
}

private data class chip_parse_result(val new_chips: List<String>, val remaining: String)

private val compose_mode_options = listOf(
    "reply" to Pair(TablerIcons.ArrowBackUp, R.string.reply),
    "reply_all" to Pair(TablerIcons.ArrowsLeft, R.string.reply_all),
    "forward" to Pair(TablerIcons.MailForward, R.string.forward),
)

private val email_chip_regex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

private fun is_valid_email_chip(value: String): Boolean = email_chip_regex.matches(value.trim())

private fun parse_chips(value: String): chip_parse_result {
    if (value.isEmpty()) return chip_parse_result(emptyList(), "")
    val last = value.last()
    if (last != ',' && last != ' ' && last != '\n') {
        return chip_parse_result(emptyList(), value)
    }
    val trimmed = value.trim().trimEnd(',', ' ', '\n')
    if (trimmed.isEmpty()) return chip_parse_result(emptyList(), "")
    val parts = trimmed.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    val (valid, invalid) = parts.partition { is_valid_email_chip(it) }
    val remaining = invalid.joinToString(" ")
    return chip_parse_result(valid, remaining)
}

@Composable
private fun field_row(label: String, content: @Composable () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.text_tertiary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(56.dp),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun chip_input(
    chips: List<String>,
    input: String,
    on_input_change: (String) -> Unit,
    on_commit: () -> Unit,
    on_remove: (Int) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    suggestions: List<Contact> = emptyList(),
    on_suggestion_pick: ((String) -> Unit)? = null,
    focus_requester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val colors = AsterMaterial.colors
    val scroll_state = rememberScrollState()
    val query = input.trim().lowercase()
    val filtered_suggestions = remember(query, suggestions, chips) {
        if (query.length < 2 || suggestions.isEmpty()) emptyList()
        else suggestions
            .filter { contact ->
                val email = contact.email.lowercase()
                val name = contact.name.lowercase()
                (email.contains(query) || name.contains(query)) && email !in chips.map { it.lowercase() }
            }
            .take(5)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll_state),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
            ) {
                chips.forEachIndexed { idx, chip ->
                    recipient_chip(chip) { on_remove(idx) }
                }
                BasicTextField(
                    value = input,
                    onValueChange = on_input_change,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text_primary),
                    cursorBrush = SolidColor(colors.accent_blue),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { on_commit() },
                        onDone = { on_commit() },
                        onSend = { on_commit() },
                    ),
                    modifier = Modifier
                        .widthIn(min = 120.dp)
                        .let { m ->
                            if (focus_requester != null) {
                                m.focusRequester(focus_requester)
                            } else m
                        }
                        .onFocusChanged { focus ->
                            if (!focus.isFocused) on_commit()
                        },
                    decorationBox = { inner ->
                        if (chips.isEmpty() && input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.add_recipient),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text_muted,
                            )
                        }
                        inner()
                    },
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(AsterSpacing.sm))
                trailing()
            }
        }
        if (filtered_suggestions.isNotEmpty() && on_suggestion_pick != null) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 120),
                properties = PopupProperties(focusable = false),
            ) {
                var popup_mounted by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { popup_mounted = true }
                val popup_alpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (popup_mounted) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
                    label = "popup_alpha",
                )
                val popup_scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (popup_mounted) 1f else 0.96f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
                    label = "popup_scale",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(popup_alpha)
                        .scale(popup_scale)
                        .shadow(8.dp, SquircleShape(18.dp))
                        .background(colors.bg_card, SquircleShape(18.dp))
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    filtered_suggestions.forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { on_suggestion_pick(contact.email) }
                                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (contact.name.isNotBlank() && contact.name != contact.email) {
                                    Text(
                                        text = contact.name,
                                        color = colors.text_primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = contact.email,
                                    color = colors.text_secondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val internal_domains = ASTER_INTERNAL_DOMAINS

private val pgp_provider_domains = listOf(
    "protonmail.com",
    "protonmail.ch",
    "proton.me",
    "pm.me",
    "tutanota.com",
    "tutanota.de",
    "tutamail.com",
    "tuta.io",
    "mailfence.com",
    "posteo.net",
    "posteo.de",
    "mailbox.org",
    "disroot.org",
    "riseup.net",
    "runbox.com",
    "kolabnow.com",
    "ctemplar.com",
    "hushmail.com",
)

private fun derive_contact_name(email: String): String {
    val local_part = email.substringBefore('@')
    val derived = local_part.split(".", "_", "-")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { ch -> ch.uppercase() } }
    return derived.ifBlank { local_part }
}

private fun is_internal_email(email: String): Boolean {
    val lower = email.lowercase()
    return internal_domains.any { lower.endsWith("@$it") }
}

private fun email_domain(email: String): String {
    val at = email.lastIndexOf('@')
    return if (at >= 0 && at < email.length - 1) email.substring(at + 1).lowercase() else ""
}

private enum class EncryptionLevel { END_TO_END, IN_TRANSIT }

private fun encryption_level_for(email: String): EncryptionLevel {
    if (is_internal_email(email)) return EncryptionLevel.END_TO_END
    val domain = email_domain(email)
    if (domain.isBlank()) return EncryptionLevel.IN_TRANSIT
    if (pgp_provider_domains.any { it == domain || domain.endsWith(".$it") }) {
        return EncryptionLevel.END_TO_END
    }
    return EncryptionLevel.IN_TRANSIT
}

@Composable
private fun recipient_chip(text: String, on_remove: () -> Unit) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val contacts_vm: ContactsViewModel = hiltViewModel()
    val contacts_state by contacts_vm.state.collectAsStateWithLifecycle()
    val copy_action = org.astermail.android.ui.common.remember_copy_action()
    val normalized = remember(text) { text.trim() }
    val saved_contact = remember(normalized, contacts_state.contacts) {
        contacts_state.contacts.firstOrNull { it.email.equals(normalized, ignoreCase = true) }
    }
    val level = encryption_level_for(text)
    val is_encrypted = level == EncryptionLevel.END_TO_END
    val accent = if (is_encrypted) colors.accent_blue else colors.text_muted
    var show_tooltip by remember { mutableStateOf(false) }
    var menu_open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(SquircleShape(AsterRadius.pill))
                .background(colors.bg_hover)
                .clickable { menu_open = true }
                .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Lock,
                contentDescription = if (is_encrypted) stringResource(R.string.end_to_end_encrypted) else stringResource(R.string.protected_in_transit),
                tint = accent,
                modifier = Modifier
                    .size(12.dp)
                    .clickable { show_tooltip = !show_tooltip },
            )
            Spacer(Modifier.width(6.dp))
            org.astermail.android.ui.mail.SenderAvatar(
                email = text,
                size = 20.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = colors.text_primary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = on_remove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = "${stringResource(R.string.remove)} $text",
                    tint = colors.text_tertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        val copied_label = stringResource(R.string.copied_value, normalized)
        aster_dropdown_menu(
            expanded = menu_open,
            on_dismiss = { menu_open = false },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md + 8.dp, vertical = AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                org.astermail.android.ui.mail.SenderAvatar(email = normalized, size = 24.dp)
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = normalized,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.text_secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            org.astermail.android.design.components.aster_dropdown_divider()
            aster_dropdown_item(
                label = stringResource(R.string.copy),
                icon = TablerIcons.Copy,
                on_click = {
                    menu_open = false
                    copy_action("email_address", normalized, copied_label)
                },
            )
            if (saved_contact == null) {
                aster_dropdown_item(
                    label = stringResource(R.string.add_to_contacts),
                    icon = TablerIcons.UserPlus,
                    on_click = {
                        menu_open = false
                        contacts_vm.save_contact(
                            Contact(
                                id = "",
                                name = derive_contact_name(normalized),
                                email = normalized,
                            ),
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.contact_added_named, normalized),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
            aster_dropdown_item(
                label = stringResource(R.string.remove),
                icon = TablerIcons.Trash,
                destructive = true,
                on_click = {
                    menu_open = false
                    on_remove()
                },
            )
        }
    }

    if (show_tooltip) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_tooltip = false },
            title = if (is_encrypted) stringResource(R.string.end_to_end_encrypted) else stringResource(R.string.protected_in_transit),
            message = if (is_encrypted)
                stringResource(R.string.e2e_recipient_description)
            else
                stringResource(R.string.transit_recipient_description),
            footer = {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.done),
                    onClick = { show_tooltip = false },
                )
            },
        )
    }
}

@Composable
private fun compose_format_row(
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strike: Boolean,
    quote: Boolean,
    on_bold: () -> Unit,
    on_italic: () -> Unit,
    on_underline: () -> Unit,
    on_strike: () -> Unit,
    on_bullet: () -> Unit,
    on_number: () -> Unit,
    on_quote: () -> Unit,
    on_link: () -> Unit,
    on_rule: () -> Unit,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_secondary)
            .padding(horizontal = AsterSpacing.xs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            format_icon_btn(TablerIcons.Bold, stringResource(R.string.bold), bold, on_bold)
            format_icon_btn(TablerIcons.Italic, stringResource(R.string.italic), italic, on_italic)
            format_icon_btn(TablerIcons.Underline, stringResource(R.string.underline), underline, on_underline)
            format_icon_btn(TablerIcons.Strikethrough, stringResource(R.string.strikethrough), strike, on_strike)
            format_divider()
            format_icon_btn(TablerIcons.List, stringResource(R.string.bullet_list), false, on_bullet)
            format_icon_btn(numbered_list_icon, stringResource(R.string.numbered_list), false, on_number)
            format_icon_btn(TablerIcons.Blockquote, stringResource(R.string.blockquote), quote, on_quote)
            format_divider()
            format_icon_btn(TablerIcons.Link, stringResource(R.string.insert_link), false, on_link)
            format_icon_btn(TablerIcons.Separator, stringResource(R.string.horizontal_rule), false, on_rule)
        }
        format_divider()
        format_icon_btn(TablerIcons.X, stringResource(R.string.close), false, on_close)
    }
}

@Composable
private fun format_divider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 5.dp)
            .width(1.dp)
            .height(20.dp)
            .background(AsterMaterial.colors.border_secondary),
    )
}

@Composable
private fun format_icon_btn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.accent_blue.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) colors.accent_blue else colors.text_secondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun build_attachment_from_uri(
    context: android.content.Context,
    uri: android.net.Uri,
): AttachmentItem? {
    val mime = context.contentResolver.getType(uri) ?: "image/*"
    if (!mime.startsWith("image/")) return null
    var name = "pasted_image"
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val ni = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val si = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (ni >= 0) name = cursor.getString(ni) ?: name
            if (si >= 0) size = cursor.getLong(si)
        }
    }
    if (size == 0L) {
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                size = afd.length.takeIf { it > 0 } ?: 0L
            }
        }
    }
    if (size > AttachmentLimits.max_bytes()) return null
    return AttachmentItem(uri, name, size, mime)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    on_close: () -> Unit,
    on_pick_file: () -> Unit,
    on_pick_photo: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.attach),
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    top = AsterSpacing.sm,
                    bottom = AsterSpacing.sm,
                ),
            )
            sheet_row(TablerIcons.Folder, stringResource(R.string.device_file), colors.text_primary, on_pick_file)
            sheet_row(TablerIcons.Stack, stringResource(R.string.photo), colors.text_primary, on_pick_photo)
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FromAliasSheet(
    current: String,
    primary: String,
    options: List<String>,
    custom_domain_set: Set<String> = emptySet(),
    on_close: () -> Unit,
    on_select: (String) -> Unit,
    on_set_primary: (String) -> Unit,
    on_create_ghost_alias: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    val normalized_query = query.trim().lowercase()
    val first_option = options.firstOrNull()
    val visible_options = remember(options, normalized_query) {
        if (normalized_query.isEmpty()) {
            options
        } else {
            options.filter { it.lowercase().contains(normalized_query) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.send_from),
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    top = AsterSpacing.sm,
                    bottom = AsterSpacing.sm,
                ),
            )
            if (options.size >= 8) {
                org.astermail.android.design.components.AsterTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_aliases),
                    leading_icon = {
                        Icon(
                            imageVector = TablerIcons.Search,
                            contentDescription = null,
                            tint = colors.text_muted,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AsterSpacing.sm)
                        .testTag("from_sheet_search"),
                )
            }
            if (normalized_query.isNotEmpty() && visible_options.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_results_found),
                    color = colors.text_muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AsterSpacing.lg),
                    textAlign = TextAlign.Center,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                items(visible_options, key = { it }) { opt ->
                    val label = when {
                        opt == first_option -> stringResource(R.string.primary_account)
                        custom_domain_set.contains(opt) -> stringResource(R.string.custom_domain)
                        else -> stringResource(R.string.alias)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(8.dp))
                            .clickable { on_select(opt) }
                            .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opt,
                                color = colors.text_primary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (opt == primary) stringResource(R.string.primary_badge) else label,
                                color = if (opt == primary) colors.accent_blue else colors.text_muted,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(
                            imageVector = if (opt == primary) pin_icon_filled else pin_icon,
                            contentDescription = stringResource(R.string.set_as_primary),
                            tint = if (opt == primary) colors.accent_blue else colors.text_muted,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(SquircleShape(8.dp))
                                .clickable { on_set_primary(opt) }
                                .padding(8.dp)
                                .rotate(if (opt == primary) -38f else 0f),
                        )
                        if (opt == current) {
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            if (normalized_query.isEmpty()) {
                androidx.compose.material3.HorizontalDivider(
                    color = colors.border_primary,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = AsterSpacing.xs),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SquircleShape(8.dp))
                        .clickable(onClick = on_create_ghost_alias)
                        .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TablerIcons.At,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.generate_ghost_alias),
                            color = colors.accent_blue,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.ghost_alias_subtitle),
                            color = colors.text_muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowSheet(
    scheduled_send: Boolean,
    expiring: Boolean,
    on_close: () -> Unit,
    on_toggle_scheduled: () -> Unit,
    on_toggle_expiring: () -> Unit,
    on_open_templates: () -> Unit,
    on_open_signature: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.more_options),
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.xs,
                ),
            )
            toggle_sheet_row(
                TablerIcons.Clock,
                stringResource(R.string.schedule_send),
                scheduled_send,
                on_toggle_scheduled,
            )
            toggle_sheet_row(
                TablerIcons.ShieldLock,
                stringResource(R.string.expiring_email),
                expiring,
                on_toggle_expiring,
            )
            sheet_row(
                TablerIcons.FileText,
                stringResource(R.string.use_template),
                colors.text_primary,
                on_open_templates,
            )
            sheet_row(
                TablerIcons.Signature,
                stringResource(R.string.signature_select),
                colors.text_primary,
                on_open_signature,
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePickerSheet(
    items: List<org.astermail.android.templates.DecryptedTemplate>,
    is_loading: Boolean,
    on_close: () -> Unit,
    on_pick: (org.astermail.android.templates.DecryptedTemplate) -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.pick_template),
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    top = AsterSpacing.sm,
                    bottom = AsterSpacing.xs,
                ),
            )
            when {
                is_loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AsterSpacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = colors.accent_blue,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                items.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_templates),
                        color = colors.text_muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(AsterSpacing.md),
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                    items.forEach { tpl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { on_pick(tpl) }
                                .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = TablerIcons.FileText,
                                contentDescription = null,
                                tint = colors.text_secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(AsterSpacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tpl.name.ifBlank { stringResource(R.string.unnamed_template) },
                                    color = colors.text_primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                                if (tpl.category.isNotBlank() || tpl.content.isNotBlank()) {
                                    val subtitle = if (tpl.category.isNotBlank()) {
                                        tpl.category
                                    } else {
                                        tpl.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                                    }
                                    Text(
                                        text = subtitle,
                                        color = colors.text_muted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@Composable
internal fun signature_preview_card(html: String) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.signature),
            color = colors.text_muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        signature_html_web_preview(
            html = html,
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(16.dp))
                .height(160.dp),
        )
    }
}

@Composable
internal fun signature_html_web_preview(html: String, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = true
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.WHITE)
            }
        },
        update = { wv ->
            val safe_html = org.astermail.android.ui.mail.EmailHtmlSanitizer.sanitize(
                html,
                org.astermail.android.ui.mail.EmailHtmlSanitizer.SanitizeOptions(
                    clean_tracking_links = false,
                    remove_tracking_pixels = false,
                    block_remote_fonts = false,
                    block_remote_css = false,
                ),
            )
            val doc = "<!DOCTYPE html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>body{margin:10px;font-family:sans-serif;font-size:14px;color:#222222;background:#ffffff;}img{max-width:100%;height:auto;}</style>" +
                "</head><body>" + safe_html + "</body></html>"
            if (wv.tag != html) {
                wv.tag = html
                wv.loadDataWithBaseURL(null, doc, "text/html", "utf-8", null)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignaturePickerSheet(
    signatures: List<DecryptedSignature>,
    current_id: String?,
    on_close: () -> Unit,
    on_pick: (String?) -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.signature_select),
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    top = AsterSpacing.sm,
                    bottom = AsterSpacing.xs,
                ),
            )
            val is_none_selected = current_id == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { on_pick(null) }
                    .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = null,
                    tint = if (is_none_selected) colors.accent_blue else colors.text_secondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.signature_none),
                    color = if (is_none_selected) colors.accent_blue else colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = if (is_none_selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
            }
            signatures.forEach { sig ->
                val is_selected = current_id == sig.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { on_pick(sig.id) }
                        .padding(horizontal = AsterSpacing.sm, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = TablerIcons.FileText,
                        contentDescription = null,
                        tint = if (is_selected) colors.accent_blue else colors.text_secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sig.name.ifBlank { stringResource(R.string.unnamed_template) },
                            color = if (is_selected) colors.accent_blue else colors.text_primary,
                            fontSize = 15.sp,
                            fontWeight = if (is_selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                        val preview_line = remember(sig.id, sig.content, sig.is_html) {
                            if (sig.is_html) {
                                org.astermail.android.mail.strip_body_html(sig.content)
                            } else {
                                sig.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                            }
                        }
                        if (preview_line.isNotBlank()) {
                            Text(
                                text = preview_line,
                                color = colors.text_muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (is_selected) {
                        Icon(
                            imageVector = TablerIcons.Check,
                            contentDescription = null,
                            tint = colors.accent_blue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GhostAliasSheet(
    on_close: () -> Unit,
    on_pick: (Int) -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.generate_ghost_alias),
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    top = AsterSpacing.sm,
                    bottom = AsterSpacing.xs,
                ),
            )
            Text(
                text = stringResource(R.string.ghost_alias_subtitle),
                color = colors.text_muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    end = AsterSpacing.sm,
                    bottom = AsterSpacing.sm,
                ),
            )
            sheet_row(TablerIcons.At, stringResource(R.string.valid_for_days, 30), colors.text_primary) { on_pick(30) }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpiringSheet(
    on_close: () -> Unit,
    on_pick: (expires_epoch_ms: Long, label: String, password: String?) -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    val sheet_context = LocalContext.current
    val sheet_picker_theme = picker_theme_res()
    var password by remember { mutableStateOf("") }
    var selected_hours by remember { mutableStateOf<Int?>(null) }
    var custom_epoch_ms by remember { mutableStateOf<Long?>(null) }
    val password_arg = password.trim().ifBlank { null }
    val one_hour_label_top = stringResource(R.string.duration_one_hour)
    val one_day_label_top = stringResource(R.string.duration_one_day)
    val seven_days_label_top = stringResource(R.string.duration_n_days, 7)
    val format_custom_label: (Long) -> String = { epoch_ms ->
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(java.util.Date(epoch_ms))
    }
    val open_custom_picker: () -> Unit = {
        val calendar = java.util.Calendar.getInstance().apply {
            custom_epoch_ms?.let { timeInMillis = it }
        }
        val date_picker = android.app.DatePickerDialog(
            sheet_context,
            sheet_picker_theme,
            { _, year, month, day ->
                val time_picker = android.app.TimePickerDialog(
                    sheet_context,
                    sheet_picker_theme,
                    { _, hour, minute ->
                        val cal = java.util.Calendar.getInstance()
                        cal.set(year, month, day, hour, minute, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        if (cal.timeInMillis <= System.currentTimeMillis()) {
                            Toast.makeText(sheet_context, sheet_context.getString(R.string.expiry_time_in_past), Toast.LENGTH_SHORT).show()
                        } else {
                            custom_epoch_ms = cal.timeInMillis
                            selected_hours = null
                        }
                    },
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(sheet_context),
                )
                time_picker.show()
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        )
        date_picker.datePicker.minDate = System.currentTimeMillis()
        date_picker.show()
    }
    val commit_or_close: () -> Unit = commit@{
        val hours = selected_hours
        val custom = custom_epoch_ms
        if (hours == null && custom == null) {
            on_close()
            return@commit
        }
        if (hours != null) {
            val label = when (hours) {
                1 -> one_hour_label_top
                24 -> one_day_label_top
                else -> seven_days_label_top
            }
            on_pick(System.currentTimeMillis() + hours * 3_600_000L, label, password_arg)
            return@commit
        }
        if (custom != null) {
            if (custom <= System.currentTimeMillis()) {
                Toast.makeText(sheet_context, sheet_context.getString(R.string.expiry_time_in_past), Toast.LENGTH_SHORT).show()
                return@commit
            }
            on_pick(custom, format_custom_label(custom), password_arg)
        }
    }
    ModalBottomSheet(
        onDismissRequest = commit_or_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.set_message_expiry),
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    top = AsterSpacing.sm,
                    bottom = AsterSpacing.xs,
                ),
            )
            Text(
                text = stringResource(R.string.message_expiry_subtitle),
                color = colors.text_muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(
                    start = AsterSpacing.sm,
                    end = AsterSpacing.sm,
                    bottom = AsterSpacing.sm,
                ),
            )
            toggle_sheet_row(TablerIcons.ShieldLock, stringResource(R.string.expires_in_hour), selected_hours == 1) { selected_hours = 1; custom_epoch_ms = null }
            toggle_sheet_row(TablerIcons.ShieldLock, stringResource(R.string.expires_in_day), selected_hours == 24) { selected_hours = 24; custom_epoch_ms = null }
            toggle_sheet_row(TablerIcons.ShieldLock, stringResource(R.string.expires_in_days, 7), selected_hours == 24 * 7) { selected_hours = 24 * 7; custom_epoch_ms = null }
            toggle_sheet_row(
                TablerIcons.Clock,
                custom_epoch_ms?.let { stringResource(R.string.expires_custom_at, format_custom_label(it)) } ?: stringResource(R.string.expires_custom),
                custom_epoch_ms != null,
            ) { open_custom_picker() }
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = stringResource(R.string.expiry_password_label),
                color = colors.text_primary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = AsterSpacing.sm),
            )
            Text(
                text = stringResource(R.string.expiry_password_subtitle),
                color = colors.text_muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = AsterSpacing.sm, end = AsterSpacing.sm, top = 2.dp, bottom = AsterSpacing.xs),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.sm)
                    .clip(SquircleShape(10.dp))
                    .border(1.dp, colors.border_secondary, SquircleShape(10.dp))
                    .background(colors.bg_secondary)
                    .padding(horizontal = AsterSpacing.md, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text_primary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent_blue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expiry_password_field"),
                    decorationBox = { inner ->
                        if (password.isEmpty()) {
                            Text(
                                text = stringResource(R.string.expiry_password_label),
                                color = colors.text_muted,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(AsterSpacing.md))
            org.astermail.android.design.components.AsterButton(
                label = stringResource(R.string.accept),
                onClick = commit_or_close,
                enabled = selected_hours != null || custom_epoch_ms != null,
                modifier = Modifier.padding(horizontal = AsterSpacing.sm),
            )
            Spacer(Modifier.height(AsterSpacing.lg))
        }
    }
}

@Composable
private fun sheet_row(
    icon: ImageVector,
    label: String,
    tint: Color,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(8.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun toggle_sheet_row(
    icon: ImageVector,
    label: String,
    active: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(8.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) colors.accent_blue else colors.text_muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = colors.text_primary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun signature_below_quote(placement: Int?, preference: String?): Boolean {
    if (placement == 1) return false
    if (placement == 0) return true
    return preference != "above"
}

private fun decorate_plain_signature(content: String, separator_enabled: Boolean): String =
    if (content.isBlank() || !separator_enabled) content else "--\n" + content

private fun escape_html(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun format_quote_timestamp(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val instant = runCatching { java.time.OffsetDateTime.parse(trimmed).toInstant() }
        .recoverCatching { java.time.Instant.parse(trimmed) }
        .getOrNull() ?: return trimmed
    return runCatching {
        java.time.format.DateTimeFormatter
            .ofPattern("EEE, MMM d, yyyy 'at' h:mm a")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(trimmed)
}

private fun safe_href(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.length > 2048) return null
    if (trimmed.any { it.isISOControl() }) return null
    val lower = trimmed.lowercase()
    val allowed = lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:")
    if (!allowed) return null
    return escape_html(trimmed)
}

private fun inline_style_at(editable: android.text.Editable, index: Int): inline_style_state {
    val styles = editable.getSpans(index, index + 1, android.text.style.StyleSpan::class.java)
    return inline_style_state(
        font_size_px = editable
            .getSpans(index, index + 1, android.text.style.AbsoluteSizeSpan::class.java)
            .lastOrNull()
            ?.size,
        font_color = editable
            .getSpans(index, index + 1, android.text.style.ForegroundColorSpan::class.java)
            .lastOrNull()
            ?.let { serializable_font_color(it.foregroundColor) },
        href = editable
            .getSpans(index, index + 1, android.text.style.URLSpan::class.java)
            .firstNotNullOfOrNull { safe_href(it.url ?: "") },
        bold = styles.any {
            it.style == android.graphics.Typeface.BOLD || it.style == android.graphics.Typeface.BOLD_ITALIC
        },
        italic = styles.any {
            it.style == android.graphics.Typeface.ITALIC || it.style == android.graphics.Typeface.BOLD_ITALIC
        },
        underline = editable
            .getSpans(index, index + 1, android.text.style.UnderlineSpan::class.java)
            .isNotEmpty(),
        strike = editable
            .getSpans(index, index + 1, android.text.style.StrikethroughSpan::class.java)
            .isNotEmpty(),
    )
}

private fun render_inline_html(editable: android.text.Editable, start: Int, end: Int): String {
    if (start >= end) return ""
    return render_inline_style_html(
        length = end - start,
        style_at = { offset -> inline_style_at(editable, start + offset) },
        char_at = { offset ->
            val ch = editable[start + offset]
            if (ch == IMG_MARKER) ch.toString() else escape_html(ch.toString())
        },
    )
}

private fun render_spanned_html(editable: android.text.Editable): String {
    val text = editable.toString()
    val line_bounds = mutableListOf<Pair<Int, Int>>()
    var cursor = 0
    while (cursor <= text.length) {
        val nl = text.indexOf('\n', cursor)
        val stop = if (nl < 0) text.length else nl
        line_bounds.add(cursor to stop)
        if (nl < 0) break
        cursor = nl + 1
    }
    val out = StringBuilder()
    var index = 0
    while (index < line_bounds.size) {
        val (line_start, line_end) = line_bounds[index]
        val probe_end = if (line_end > line_start) line_end else minOf(line_start + 1, editable.length)
        val is_hr = line_start < probe_end && editable
            .getSpans(line_start, probe_end, android.text.Annotation::class.java)
            .any { it.key == "aster" && it.value == "hr" }
        if (is_hr) {
            out.append("<hr>")
            index++
            continue
        }
        val is_bullet = line_start < probe_end &&
            editable.getSpans(line_start, probe_end, android.text.style.BulletSpan::class.java).isNotEmpty()
        val is_quote = line_start < probe_end &&
            editable.getSpans(line_start, probe_end, android.text.style.QuoteSpan::class.java).isNotEmpty()
        if (is_bullet) {
            out.append("<ul>")
            while (index < line_bounds.size) {
                val (s, e) = line_bounds[index]
                val pe = if (e > s) e else minOf(s + 1, editable.length)
                if (s >= pe || editable.getSpans(s, pe, android.text.style.BulletSpan::class.java).isEmpty()) break
                out.append("<li>").append(render_inline_html(editable, s, e)).append("</li>")
                index++
            }
            out.append("</ul>")
            continue
        }
        if (is_quote) {
            out.append("<blockquote>")
            var first = true
            while (index < line_bounds.size) {
                val (s, e) = line_bounds[index]
                val pe = if (e > s) e else minOf(s + 1, editable.length)
                if (s >= pe || editable.getSpans(s, pe, android.text.style.QuoteSpan::class.java).isEmpty()) break
                if (!first) out.append("<br>")
                out.append(render_inline_html(editable, s, e))
                first = false
                index++
            }
            out.append("</blockquote>")
            continue
        }
        out.append(render_inline_html(editable, line_start, line_end))
        val next = line_bounds.getOrNull(index + 1)
        if (next != null) {
            val (ns, ne) = next
            val npe = if (ne > ns) ne else minOf(ns + 1, editable.length)
            val next_block = ns < npe && (
                editable.getSpans(ns, npe, android.text.style.BulletSpan::class.java).isNotEmpty() ||
                    editable.getSpans(ns, npe, android.text.style.QuoteSpan::class.java).isNotEmpty() ||
                    editable.getSpans(ns, npe, android.text.Annotation::class.java).any { it.key == "aster" && it.value == "hr" }
                )
            if (!next_block) out.append("<br>")
        }
        index++
    }
    return out.toString()
}

private val WATERMARK_LINE_RE = Regex("(?i)\\bSecured by Aster Mail\\b\\s*")

private fun strip_watermarks(text: String): String {
    val cleaned = WATERMARK_LINE_RE.replace(text, "\n")
    return cleaned.replace(Regex("\\n{3,}"), "\n\n").trim()
}

internal fun insert_template_body(
    body: String,
    template: String,
    signature: String,
    watermark: String,
): String {
    if (template.isBlank()) return body
    val watermark_suffix = "\n\n$watermark"
    val has_watermark = body.endsWith(watermark_suffix)
    val without_watermark = if (has_watermark) body.dropLast(watermark_suffix.length) else body
    val has_signature = signature.isNotBlank() && without_watermark.endsWith(signature)
    val core = if (has_signature) without_watermark.dropLast(signature.length) else without_watermark
    val trimmed_core = core.trimEnd('\n', ' ')
    val separator = if (trimmed_core.isBlank()) "" else "\n\n"
    val signature_block = if (has_signature) "\n\n$signature" else ""
    val watermark_block = if (has_watermark) watermark_suffix else ""
    return trimmed_core + separator + template.trim() + signature_block + watermark_block
}

private val STYLE_SCRIPT_TAG_RE = Regex("(?is)<(style|script)\\b[^>]*>.*?</\\1>")

private fun build_quoted_body(
    msg: org.astermail.android.mail.ThreadMessageDecrypted,
    item: org.astermail.android.mail.InboxItem?,
    forwarded: Boolean,
): String {
    val raw = msg.body_html ?: msg.body_text
    val plain_raw = if (raw.contains("<") && raw.contains(">")) {
        val repaired = org.astermail.android.ui.mail.EmailHtmlSanitizer.repair_comment_markup(raw)
        val without_style_script = STYLE_SCRIPT_TAG_RE.replace(repaired, "")
        android.text.Html.fromHtml(without_style_script, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trimEnd()
    } else raw
    val plain = strip_watermarks(plain_raw)
    val from_line = msg.display_sender_email ?: msg.sender_email
    val to_line = msg.to_addresses.joinToString(", ")
    val subject_line = item?.subject.orEmpty()
    val date_line = msg.timestamp
    val header = if (forwarded) {
        "\n\n\n---------- Forwarded message ----------\nFrom: $from_line\nDate: $date_line\nSubject: $subject_line\nTo: $to_line\n\n"
    } else {
        "\n\nOn $date_line, $from_line wrote:\n"
    }
    val quoted = plain.lines().joinToString("\n") { if (forwarded) it else "> $it" }
    return header + quoted
}

private fun find_quote_start(text: String): Int {
    val patterns = listOf("\nOn ", "\n---------- Forwarded")
    for (pattern in patterns) {
        val idx = text.indexOf(pattern)
        if (idx >= 0) return idx
    }
    return -1
}

private const val IMG_MARKER = '￼'

private class AsterImageSpan(d: android.graphics.drawable.Drawable, val image_uri: Uri) :
    android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BOTTOM)

private fun max_image_dims(et: android.widget.EditText, intrinsic_w: Int = -1): Pair<Int, Int> {
    val available = (et.width - et.paddingLeft - et.paddingRight).takeIf { it > 0 }
        ?: (et.context.resources.displayMetrics.widthPixels - 96)
    val density = et.context.resources.displayMetrics.density
    val display_h = et.context.resources.displayMetrics.heightPixels
    val natural = if (intrinsic_w > 0) intrinsic_w else available
    val max_w = natural.coerceAtMost(available).coerceAtLeast(1)
    val max_h = (display_h * 0.6f).toInt().coerceAtLeast((160 * density).toInt())
    return max_w to max_h
}

private fun fit(intrinsic_w: Int, intrinsic_h: Int, max_w: Int, max_h: Int): Pair<Int, Int> {
    if (intrinsic_w <= 0 || intrinsic_h <= 0) return max_w to (max_w / 2).coerceAtLeast(1)
    val ratio = intrinsic_h.toFloat() / intrinsic_w
    var w = max_w
    var h = (w * ratio).toInt()
    if (h > max_h) {
        h = max_h
        w = (h / ratio).toInt().coerceAtLeast(1)
    }
    return w to h.coerceAtLeast(1)
}

private fun apply_image_span_placeholder(et: android.widget.EditText, marker_pos: Int, uri: Uri) {
    val text = et.text ?: return
    if (marker_pos < 0 || marker_pos >= text.length) return
    if (text[marker_pos] != IMG_MARKER) return
    val (max_w, max_h) = max_image_dims(et)
    val (w, h) = fit(2, 1, max_w, max_h)
    val placeholder = android.graphics.drawable.ColorDrawable(0x22888888)
    placeholder.setBounds(0, 0, w, h)
    val span = AsterImageSpan(placeholder, uri)
    text.setSpan(span, marker_pos, marker_pos + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    et.invalidate()
}

private fun load_image_span_async(et: android.widget.EditText, uri: Uri) {
    val ctx = et.context
    val request = coil.request.ImageRequest.Builder(ctx)
        .data(uri)
        .target(
            onSuccess = { drawable ->
                val text = et.text ?: return@target
                val spans = text.getSpans(0, text.length, AsterImageSpan::class.java)
                val match = spans.firstOrNull { it.image_uri == uri } ?: return@target
                val start = text.getSpanStart(match)
                val end = text.getSpanEnd(match)
                if (start < 0 || end < 0 || end > text.length) return@target
                val (max_w, max_h) = max_image_dims(et, drawable.intrinsicWidth)
                val (w, h) = fit(drawable.intrinsicWidth, drawable.intrinsicHeight, max_w, max_h)
                drawable.setBounds(0, 0, w, h)
                text.removeSpan(match)
                val new_span = AsterImageSpan(drawable, uri)
                text.setSpan(new_span, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val owner = et as? RichBodyEditText
                owner?.suspend_text_watcher = true
                val sel = et.selectionStart
                et.setText(text as CharSequence, android.widget.TextView.BufferType.EDITABLE)
                et.setSelection(sel.coerceIn(0, et.text?.length ?: 0))
                owner?.suspend_text_watcher = false
                et.invalidate()
            },
        )
        .build()
    coil.Coil.imageLoader(ctx).enqueue(request)
}

private const val format_menu_item_id = 0x00A57E01

private class RichBodyEditText(context: android.content.Context) : android.widget.EditText(context) {
    var on_image_received: ((Uri) -> Unit)? = null
    var on_paste_clipboard: (() -> Boolean)? = null
    var on_selection_changed: ((Int, Int) -> Unit)? = null
    var on_format_requested: (() -> Unit)? = null
    var suspend_text_watcher: Boolean = false

    init {
        customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                menu.add(
                    android.view.Menu.NONE,
                    format_menu_item_id,
                    0,
                    context.getString(R.string.format),
                ).setShowAsAction(
                    android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or
                        android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT,
                )
                return true
            }

            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean = false

            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                if (item.itemId != format_menu_item_id) return false
                on_format_requested?.invoke()
                mode.finish()
                return true
            }

            override fun onDestroyActionMode(mode: android.view.ActionMode) {}
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        on_selection_changed?.invoke(selStart, selEnd)
    }

    private fun maybe_delete_selected_image(): Boolean {
        val text = this.text ?: return false
        val s = selectionStart
        val e = selectionEnd
        if (s == e || s !in 0 until text.length || e != s + 1) return false
        if (text[s] != IMG_MARKER) return false
        val spans = text.getSpans(s, s + 1, AsterImageSpan::class.java)
        if (spans.isEmpty()) return false
        val ds = if (s > 0 && text[s - 1] == '\n') s - 1 else s
        val de = if (e < text.length && text[e] == '\n') e + 1 else e
        text.delete(ds, de)
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        EditorInfoCompat.setContentMimeTypes(
            outAttrs,
            arrayOf("image/*", "image/png", "image/jpeg", "image/gif", "image/webp"),
        )
        val callback = InputConnectionCompat.OnCommitContentListener { info, flags, _ ->
            val grant = (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
            if (grant) {
                runCatching { info.requestPermission() }.getOrElse { return@OnCommitContentListener false }
            }
            on_image_received?.invoke(info.contentUri)
            true
        }
        val wrapped = object : android.view.inputmethod.InputConnectionWrapper(ic, true) {
            override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    event.keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                    maybe_delete_selected_image()
                ) return true
                return super.sendKeyEvent(event)
            }
            override fun deleteSurroundingText(before: Int, after: Int): Boolean {
                if (maybe_delete_selected_image()) return true
                return super.deleteSurroundingText(before, after)
            }
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text.isNullOrEmpty() && maybe_delete_selected_image()) return true
                return super.commitText(text, newCursorPosition)
            }
        }
        @Suppress("DEPRECATION")
        return InputConnectionCompat.createWrapper(wrapped, outAttrs, callback)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            if (on_paste_clipboard?.invoke() == true) return true
        }
        return super.onTextContextMenuItem(id)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP) {
            val text = this.text
            val layout = this.layout
            if (text != null && layout != null) {
                val x = event.x.toInt() - totalPaddingLeft + scrollX
                val y = event.y.toInt() - totalPaddingTop + scrollY
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                val candidates = intArrayOf(offset, offset - 1).filter { it in 0 until text.length }
                for (cand in candidates) {
                    if (text[cand] != IMG_MARKER) continue
                    val spans = text.getSpans(cand, cand + 1, AsterImageSpan::class.java)
                    if (spans.isEmpty()) continue
                    requestFocus()
                    setSelection(cand, cand + 1)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
