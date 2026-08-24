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

package org.astermail.android.ui.mail

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.*

import org.astermail.android.ui.icons.pin_icon
import org.astermail.android.ui.icons.pin_icon_filled
import org.astermail.android.design.components.aster_dropdown_divider
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.looks_encrypted
import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterColors
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.mail.AttachmentKeyUnavailableException
import org.astermail.android.mail.DecryptedReaction
import org.astermail.android.mail.MailViewModel
import org.astermail.android.mail.can_move_to_inbox
import org.astermail.android.mail.body_starts_with
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

private val placeholder_body_height = 140.dp

private val EXTERNAL_RESOURCE_PATTERN = Regex(
    """(?:src\s*=\s*["']https?://|background\s*=\s*["']https?://|url\s*\(\s*["']?https?://|@font-face)""",
    RegexOption.IGNORE_CASE,
)

private val IMG_TAG_PATTERN = Regex(
    """<img\b[^>]*\bsrc\s*=\s*["']https?://[^"']+["'][^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val FONT_FACE_PATTERN = Regex("""@font-face""", RegexOption.IGNORE_CASE)

private val LINK_STYLESHEET_PATTERN = Regex(
    """<link\b[^>]*rel\s*=\s*["']stylesheet["'][^>]*>""",
    RegexOption.IGNORE_CASE,
)

internal enum class ExternalContentType { image, tracker, font, stylesheet }

internal data class ExternalContentItem(
    val type: ExternalContentType,
    val url: String,
)

internal data class ExternalContentCounts(
    val image_count: Int,
    val tracker_count: Int,
    val font_count: Int,
    val css_count: Int,
    val items: List<ExternalContentItem> = emptyList(),
) {
    val total: Int get() = image_count + tracker_count + font_count + css_count
}

private const val EXTERNAL_ITEM_LIST_CAP = 60

private val SRC_URL_PATTERN = Regex("""(?<![-\w])src\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
private val HREF_URL_PATTERN = Regex("""(?<![-\w])href\s*=\s*["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE)
private val FONT_URL_PATTERN = Regex("""url\s*\(\s*["']?(https?://[^"')]+)""", RegexOption.IGNORE_CASE)

private fun external_display_url(url: String): String {
    val trimmed = url.trim()
    if (trimmed.length <= 60) return trimmed
    return try {
        val parsed = android.net.Uri.parse(trimmed)
        val host = parsed.host ?: return trimmed.take(60) + "…"
        val path = parsed.path.orEmpty()
        val remaining = 60 - host.length - 3
        if (remaining > 10) {
            host + if (path.length > remaining) path.take(remaining) + "…" else path
        } else {
            "$host/…"
        }
    } catch (_: Throwable) {
        trimmed.take(60) + "…"
    }
}

private val IMG_WIDTH_PATTERN = Regex("""width\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)

private val IMG_HEIGHT_PATTERN = Regex("""height\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)

private fun count_external_content(html: String): ExternalContentCounts {
    var images = 0
    var trackers = 0
    val items = mutableListOf<ExternalContentItem>()
    IMG_TAG_PATTERN.findAll(html).forEach { match ->
        val tag = match.value
        val width_match = IMG_WIDTH_PATTERN.find(tag)
        val height_match = IMG_HEIGHT_PATTERN.find(tag)
        val w = width_match?.groupValues?.get(1)?.toIntOrNull()
        val h = height_match?.groupValues?.get(1)?.toIntOrNull()
        val is_tracker = w != null && h != null && w <= 2 && h <= 2
        if (is_tracker) trackers++ else images++
        if (items.size < EXTERNAL_ITEM_LIST_CAP) {
            val url = SRC_URL_PATTERN.find(tag)?.groupValues?.get(1)
            if (!url.isNullOrBlank()) {
                items.add(
                    ExternalContentItem(
                        if (is_tracker) ExternalContentType.tracker else ExternalContentType.image,
                        url,
                    ),
                )
            }
        }
    }
    val fonts = FONT_FACE_PATTERN.findAll(html).count()
    val css = LINK_STYLESHEET_PATTERN.findAll(html).count()
    if (fonts > 0) {
        FONT_URL_PATTERN.findAll(html).take(EXTERNAL_ITEM_LIST_CAP).forEach { m ->
            if (items.size < EXTERNAL_ITEM_LIST_CAP) {
                items.add(ExternalContentItem(ExternalContentType.font, m.groupValues[1]))
            }
        }
    }
    LINK_STYLESHEET_PATTERN.findAll(html).forEach { m ->
        if (items.size < EXTERNAL_ITEM_LIST_CAP) {
            val url = HREF_URL_PATTERN.find(m.value)?.groupValues?.get(1)
            if (!url.isNullOrBlank()) {
                items.add(ExternalContentItem(ExternalContentType.stylesheet, url))
            }
        }
    }
    return ExternalContentCounts(images, trackers, fonts, css, items)
}

private val PROXY_SRC_PATTERN = Regex(
    """(src\s*=\s*["'])(https?://[^"']+)(["'])""",
    RegexOption.IGNORE_CASE,
)

private val PROXY_SRCSET_PATTERN = Regex(
    """(srcset\s*=\s*["'])([^"']+)(["'])""",
    RegexOption.IGNORE_CASE,
)

private val PROXY_PROTOCOL_RELATIVE_SRC_PATTERN = Regex(
    """(src\s*=\s*["'])(//[^"']+)(["'])""",
    RegexOption.IGNORE_CASE,
)

private val PROXY_CSS_URL_PATTERN = Regex(
    """(url\(\s*["']?)((?:https?:)?//[^"')\s]+)(["']?\s*\))""",
    RegexOption.IGNORE_CASE,
)

private val PROXY_BACKGROUND_ATTR_PATTERN = Regex(
    """(background\s*=\s*["'])((?:https?:)?//[^"']+)(["'])""",
    RegexOption.IGNORE_CASE,
)

private val PROXY_UNQUOTED_SRC_PATTERN = Regex(
    """((?:src|background)\s*=\s*)((?:https?:)?//[^\s"'>]+)""",
    RegexOption.IGNORE_CASE,
)

private val CID_SRC_PATTERN = Regex(
    """(src\s*=\s*["'])cid:([^"']+)(["'])""",
    RegexOption.IGNORE_CASE,
)

internal fun trim_email_runon(address: String): String {
    val at = address.lastIndexOf('@')
    if (at < 0) return address
    val domain = address.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    if (dot < 0) return address
    var tld = domain.substring(dot + 1)
    val seam = Regex("[a-z][A-Z]").find(tld)
    if (seam != null) tld = tld.substring(0, seam.range.first + 1)
    if (tld.length > 24) tld = tld.substring(0, 24)
    return address.substring(0, at + 1) + domain.substring(0, dot + 1) + tld
}

internal fun proxy_external_urls(html: String, proxy_base: String): String {
    fun to_proxied(raw_url: String): String {
        val url = raw_url
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&")
            .trim()
        val absolute = if (url.startsWith("//")) "https:$url" else url
        if (absolute.startsWith(proxy_base)) return absolute
        if (absolute.startsWith(INLINE_IMAGE_URL_PREFIX)) return absolute
        return proxy_base + java.net.URLEncoder.encode(absolute, "UTF-8")
    }
    val unquoted_replaced = PROXY_UNQUOTED_SRC_PATTERN.replace(html) { match ->
        "${match.groupValues[1]}\"${to_proxied(match.groupValues[2])}\""
    }
    val protocol_normalized = PROXY_PROTOCOL_RELATIVE_SRC_PATTERN.replace(unquoted_replaced) { match ->
        "${match.groupValues[1]}https:${match.groupValues[2]}${match.groupValues[3]}"
    }
    val src_replaced = PROXY_SRC_PATTERN.replace(protocol_normalized) { match ->
        "${match.groupValues[1]}${to_proxied(match.groupValues[2])}${match.groupValues[3]}"
    }
    val srcset_replaced = PROXY_SRCSET_PATTERN.replace(src_replaced) { match ->
        val proxied = match.groupValues[2].split(",").mapNotNull { entry ->
            val parts = entry.trim().split(Regex("\\s+"), 2)
            val url = parts[0]
            val descriptor = if (parts.size > 1) " ${parts[1]}" else ""
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")) {
                "${to_proxied(url)}$descriptor"
            } else {
                null
            }
        }.joinToString(", ")
        "${match.groupValues[1]}$proxied${match.groupValues[3]}"
    }
    val background_attr_replaced = PROXY_BACKGROUND_ATTR_PATTERN.replace(srcset_replaced) { match ->
        "${match.groupValues[1]}${to_proxied(match.groupValues[2])}${match.groupValues[3]}"
    }
    return PROXY_CSS_URL_PATTERN.replace(background_attr_replaced) { match ->
        "${match.groupValues[1]}${to_proxied(match.groupValues[2])}${match.groupValues[3]}"
    }
}

private const val INLINE_IMAGE_MAX_BYTES = 16 * 1024 * 1024
private const val INLINE_IMAGE_TOTAL_BUDGET_BYTES = 32 * 1024 * 1024
private const val TRANSPARENT_PIXEL_DATA_URI =
    "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="

internal fun normalized_content_id(raw: String?): String =
    raw?.trim()?.trim('<', '>').orEmpty()

internal fun resolve_inline_cids(html: String, inline_images: Map<String, String>): String =
    CID_SRC_PATTERN.replace(html) { match ->
        val cid = normalized_content_id(match.groupValues[2])
        val resolved = inline_images[cid] ?: TRANSPARENT_PIXEL_DATA_URI
        "${match.groupValues[1]}$resolved${match.groupValues[3]}"
    }

internal fun inline_image_sources(
    body_html: String,
    attachments: List<MessageAttachment>,
): Map<String, String> {
    if (body_html.isBlank() || attachments.isEmpty()) return emptyMap()
    val resolved = LinkedHashMap<String, String>()
    var budget = INLINE_IMAGE_TOTAL_BUDGET_BYTES
    for (att in attachments) {
        val cid = normalized_content_id(att.content_id)
        if (cid.isBlank() || resolved.containsKey(cid)) continue
        if (!body_html.contains("cid:$cid", ignoreCase = true)) continue
        if (!att.content_type.startsWith("image/", ignoreCase = true)) continue
        val data = att.encrypted_data
        val nonce = att.data_nonce
        if (data.isNullOrBlank() || nonce.isNullOrBlank()) continue
        val bytes = runCatching {
            org.astermail.android.mail.MailRepository.decrypt_attachment_bytes(
                data,
                nonce,
                att.session_key.orEmpty(),
                att.mail_item_id,
                att.seq_num,
            )
        }.getOrNull() ?: continue
        if (bytes.isEmpty() || bytes.size > INLINE_IMAGE_MAX_BYTES || bytes.size > budget) continue
        budget -= bytes.size
        val key = InlineImageStore.content_key(cid, bytes)
        InlineImageStore.put(key, att.content_type, bytes)
        resolved[cid] = InlineImageStore.url_for(key)
    }
    return resolved
}

private val GHOST_LOCAL_PATTERN = Regex("^[a-z]+\\.[a-z]+\\d{2}@", RegexOption.IGNORE_CASE)

@Composable
fun MailDetailScreen(
    email_id: String,
    on_back: () -> Unit,
    on_reply: (String, String?) -> Unit,
    on_reply_all: (String, String?) -> Unit,
    on_forward: (String, String?) -> Unit,
    on_archive: () -> Unit,
    on_delete: () -> Unit,
    on_next: (() -> Unit)? = null,
    on_previous: (() -> Unit)? = null,
    on_navigate: ((String) -> Unit)? = null,
    mail_vm: MailViewModel = hiltViewModel(),
    settings_vm: SettingsViewModel = shared_settings_view_model(),
) {
    val colors = AsterMaterial.colors
    val density = LocalDensity.current
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val swipe_threshold_px = with(density) { 200.dp.toPx() }
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val privacy_blocks_external = settings_state.preferences?.block_external_images ?: true
    val traffic_blocks_external = settings_state.preferences?.low_network_mode == true
    val block_external_images = privacy_blocks_external || traffic_blocks_external
    val blocked_for_traffic_only = traffic_blocks_external && !privacy_blocks_external
    val thread_state by mail_vm.thread_state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reactions_enabled = settings_state.preferences?.reactions_enabled != false
    val message_reactions by mail_vm.message_reactions.collectAsStateWithLifecycle()
    val decrypt_retry_active by mail_vm.decrypt_retry_active.collectAsStateWithLifecycle()
    val identity_changes by mail_vm.identity_changes.collectAsStateWithLifecycle()
    val identity_changed_senders = remember(identity_changes) {
        identity_changes.mapNotNull { it.sender_email.trim().lowercase().takeIf { e -> e.isNotBlank() } }.toSet()
    }
    var reaction_picker_open by remember { mutableStateOf(false) }

    LaunchedEffect(reactions_enabled) {
        mail_vm.set_reactions_enabled(reactions_enabled)
    }

    LaunchedEffect(email_id) {
        mail_vm.load_thread(email_id)
    }

    val unread_thread_ids = remember(thread_state.messages) {
        thread_state.messages.map { it.id }
    }
    LaunchedEffect(email_id, unread_thread_ids, settings_state.preferences?.mark_as_read) {
        val prefs = settings_state.preferences ?: return@LaunchedEffect
        val delay_ms = when (prefs.mark_as_read) {
            "immediate" -> 0L
            "3_seconds" -> 3000L
            "never" -> return@LaunchedEffect
            else -> 1000L
        }
        if (delay_ms > 0) kotlinx.coroutines.delay(delay_ms)
        mail_vm.mark_thread_read(email_id, unread_thread_ids)
    }

    LaunchedEffect(Unit) {
        settings_vm.load_preferences()
        settings_vm.load_tags(force = false)
        settings_vm.load_profile()
    }

    val thread_attachments = thread_state.attachments
    val api_messages = remember(thread_state.messages, thread_attachments) {
        thread_state.messages.map { msg ->
            val base = thread_message_to_mock(msg)
            val atts = thread_attachments[msg.id]
            if (!atts.isNullOrEmpty()) thread_message_with_attachments(base, atts) else base
        }
    }
    val api_item = thread_state.item
    val all_thread_attachments = remember(api_messages) {
        api_messages.flatMap { it.attachments }
    }

    val thread_ghost_email = remember(thread_state.messages) {
        val latest_sent = thread_state.messages
            .filter { it.raw_item.item_type == "sent" }
            .maxByOrNull { it.timestamp }
        val sender = latest_sent?.sender_email?.lowercase().orEmpty()
        if (sender.isNotBlank() && GHOST_LOCAL_PATTERN.containsMatchIn(sender)) sender else null
    }

    LaunchedEffect(reactions_enabled) {
        if (reactions_enabled) {
            settings_vm.load_aliases()
            settings_vm.load_custom_domain_addresses()
        }
    }

    LaunchedEffect(reactions_enabled, thread_ghost_email) {
        if (reactions_enabled && !thread_ghost_email.isNullOrBlank()) {
            settings_vm.load_ghost_aliases()
        }
    }

    val email = remember(email_id, api_item) {
        if (api_item != null) inbox_item_to_email(api_item) else null
    }
    var star_override by remember(email_id) { mutableStateOf<Boolean?>(null) }
    val is_starred = star_override ?: (api_item?.is_starred == true)
    var is_spam_override by remember(email_id) { mutableStateOf<Boolean?>(null) }
    val inbox_state_for_folder by mail_vm.inbox_state.collectAsStateWithLifecycle()
    val is_in_spam_folder = inbox_state_for_folder.current_folder == "spam"
    val is_spam = is_spam_override ?: ((api_item?.is_spam == true) || is_in_spam_folder)
    val current_account by settings_vm.account_store.current_account.collectAsStateWithLifecycle()
    val my_email = current_account?.email?.lowercase().orEmpty()
    val my_profile_pic = settings_state.user?.profile_picture?.takeIf { it.isNotBlank() }
        ?: current_account?.profile_picture?.takeIf { it.isNotBlank() }
    val reaction_sender_options = remember(
        my_email,
        settings_state.aliases,
        settings_state.custom_domain_addresses,
        settings_state.ghost_aliases,
    ) {
        val options = mutableListOf<String>()
        if (my_email.isNotBlank()) options.add(my_email)
        settings_state.aliases
            .filter { it.is_enabled && it.address.contains('@') }
            .forEach { if (it.address !in options) options.add(it.address) }
        settings_state.custom_domain_addresses
            .filter { it.is_enabled && it.address.contains('@') }
            .forEach { if (it.address !in options) options.add(it.address) }
        settings_state.ghost_aliases
            .filter { it.address.contains('@') }
            .forEach { if (it.address !in options) options.add(it.address) }
        options.toList()
    }
    val reaction_sender_hashes = remember(
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
    val reaction_identity_for: (String) -> org.astermail.android.ui.compose.ReactionSenderIdentity =
        { message_id ->
            val target = thread_state.messages.find { it.id == message_id }
            org.astermail.android.ui.compose.resolve_reaction_sender_identity(
                own_recipient_addresses = target?.let { it.to_addresses + it.cc_addresses }.orEmpty(),
                message_sender_email = target?.sender_email.orEmpty(),
                is_own_message = target?.raw_item?.item_type == "sent",
                alias_options = reaction_sender_options,
                alias_hash_map = reaction_sender_hashes,
                user_email = my_email,
            )
        }
    val reaction_own_addresses = remember(reaction_sender_options) {
        reaction_sender_options.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    }
    val is_in_trash_folder = inbox_state_for_folder.current_folder == "trash"
    val is_trashed = (api_item?.is_trashed == true) || is_in_trash_folder
    val reaction_restriction_for: (ThreadMessage) -> org.astermail.android.mail.ReactionRestriction? =
        { msg ->
            org.astermail.android.mail.reaction_restriction(
                item_type = msg.item_type,
                sender_email = msg.sender_email,
                to_addresses = msg.to_addresses,
                cc_addresses = msg.cc_addresses,
                raw_headers = msg.raw_headers,
                reactions = message_reactions[msg.id].orEmpty(),
                user_email = my_email,
                is_spam = is_spam,
                is_trashed = is_trashed,
                reactions_enabled = reactions_enabled,
                is_own_address = { address -> address in reaction_own_addresses },
            )
        }
    var show_action_sheet by remember { mutableStateOf(false) }
    var show_topbar_menu by remember { mutableStateOf(false) }
    var show_message_details by remember { mutableStateOf(false) }
    var show_raw_source_dialog by remember { mutableStateOf(false) }
    var show_encryption_info by remember { mutableStateOf(false) }
    var pending_block_sender by remember { mutableStateOf<String?>(null) }
    var profile_sender by remember { mutableStateOf<Pair<String, String>?>(null) }
    var show_snooze_sheet by remember { mutableStateOf(false) }
    var show_folder_sheet by remember { mutableStateOf(false) }
    var show_label_sheet by remember { mutableStateOf(false) }
    var action_target_id by remember { mutableStateOf<String?>(null) }

    var body_ready by remember(email_id) { mutableStateOf(false) }
    var show_encryption_dropdown by remember { mutableStateOf(false) }
    var hidden_group_revealed by remember(email_id) { mutableStateOf(false) }
    var allow_external_ids by remember(email_id) { mutableStateOf(emptySet<String>()) }
    var dismissed_unsub_ids by remember(email_id) { mutableStateOf(emptySet<String>()) }
    var pending_link by remember { mutableStateOf<String?>(null) }
    var lightbox_src by remember { mutableStateOf<String?>(null) }
    var preview_attachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var preview_bytes by remember { mutableStateOf<ByteArray?>(null) }
    var is_downloading_attachment by remember { mutableStateOf(false) }

    val messages = remember(email_id, api_messages) { api_messages.distinctBy { it.id } }
    val is_thread_encrypted = remember(messages) { thread_is_end_to_end_encrypted(messages) }
    val thread_trackers_blocked = remember(messages) { messages.sumOf { it.trackers_blocked } }

    var bottom_bar_height by remember { mutableStateOf(132.dp) }

    val visible_tail_count = 2
    val hidden_seed_ids = remember(email_id) { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(email_id, messages.size) {
        if (hidden_seed_ids.value != null || messages.size <= 1) return@LaunchedEffect
        hidden_seed_ids.value = if (messages.size <= visible_tail_count + 2) {
            emptySet()
        } else {
            messages.subList(1, messages.size - visible_tail_count).map { it.id }.toSet()
        }
    }
    val hidden_id_set = remember(messages, hidden_group_revealed, hidden_seed_ids.value) {
        if (hidden_group_revealed) emptySet()
        else {
            val seed = hidden_seed_ids.value ?: emptySet()
            messages.asSequence().map { it.id }.filter { it in seed }.toSet()
        }
    }
    val first_hidden_idx = remember(messages, hidden_id_set) {
        messages.indexOfFirst { it.id in hidden_id_set }
    }

    val expanded_ids = remember(email_id) {
        mutableStateOf(emptySet<String>())
    }
    val seeded_last_id = remember(email_id) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(email_id, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val last_id = messages.last().id
        if (seeded_last_id.value == last_id) return@LaunchedEffect
        seeded_last_id.value = last_id
        expanded_ids.value = expanded_ids.value + last_id
    }

    fun show_toast(msg: String) {
        org.astermail.android.ui.common.app_toast.show(msg)
    }

    val list_state = rememberLazyListState()
    val show_topbar_subject by remember {
        derivedStateOf { list_state.firstVisibleItemIndex > 0 || list_state.firstVisibleItemScrollOffset > 80 }
    }

    val pending_anchor = remember { mutableStateOf<Pair<String, Int>?>(null) }
    val toggle_tick = remember { mutableStateOf(0) }
    fun anchor_toggle(id: String) {
        val info = list_state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
        pending_anchor.value = if (info != null) id to info.offset else null
        toggle_tick.value += 1
    }
    LaunchedEffect(toggle_tick.value) {
        val anchor = pending_anchor.value ?: return@LaunchedEffect
        pending_anchor.value = null
        androidx.compose.runtime.withFrameNanos { }
        val info = list_state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchor.first }
            ?: return@LaunchedEffect
        val delta = (info.offset - anchor.second).toFloat()
        if (kotlin.math.abs(delta) > 1f) {
            list_state.scrollBy(delta)
        }
        androidx.compose.runtime.withFrameNanos { }
        val settled = list_state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == anchor.first }
            ?: return@LaunchedEffect
        if (kotlin.math.abs(settled.offset - anchor.second) > 24) {
            list_state.animateScrollToItem(settled.index)
        }
    }

    LaunchedEffect(mail_vm) {
        mail_vm.toast_events.collect { evt ->
            if (evt.on_undo != null) {
                org.astermail.android.ui.common.app_toast.show(
                    org.astermail.android.ui.common.TopToastState(
                        message = evt.message,
                        undo_label = evt.undo_label,
                        on_undo = evt.on_undo,
                        duration_ms = evt.duration_ms,
                        on_timeout = evt.on_timeout,
                    ),
                )
            } else {
                show_toast(evt.message)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg_primary).systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.bg_primary)
                    .padding(horizontal = AsterSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                AsterIconButton(
                    icon = TablerIcons.ArrowLeft,
                    content_description = stringResource(R.string.back),
                    onClick = on_back,
                    modifier = Modifier.align(Alignment.CenterStart).testTag("back"),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val topbar_subject = email?.subject?.ifBlank { stringResource(R.string.no_subject) }
                        ?: stringResource(R.string.no_subject)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = show_topbar_subject && email != null,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                    ) {
                        Text(
                            text = topbar_subject,
                            color = colors.text_primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !show_topbar_subject && email != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        val label = encryption_badge_label(is_thread_encrypted)
                        val tint = if (is_thread_encrypted) colors.accent_blue else colors.text_muted
                        Row(
                            modifier = Modifier
                                .clip(SquircleShape(999.dp))
                                .clickable { show_encryption_info = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("encryption_badge"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (is_thread_encrypted) TablerIcons.Lock else TablerIcons.LockOpen,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = label,
                                color = tint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val is_archived = api_item?.is_archived == true
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                    AsterIconButton(
                        icon = TablerIcons.DotsVertical,
                        content_description = stringResource(R.string.more),
                        onClick = { show_topbar_menu = true },
                        modifier = Modifier.testTag("more"),
                    )
                    aster_dropdown_menu(
                        expanded = show_topbar_menu,
                        on_dismiss = { show_topbar_menu = false },
                        offset = DpOffset(0.dp, 8.dp),
                        min_width = 260.dp,
                    ) {
                        val is_pinned = api_item?.raw_item?.metadata?.is_pinned == true
                        detail_menu_action(
                            icon = if (is_archived) TablerIcons.Inbox else TablerIcons.Archive,
                            text = if (is_archived) stringResource(R.string.swipe_move_to_inbox) else stringResource(R.string.archive_action),
                            tint = colors.text_primary,
                            test_tag = "archive",
                        ) {
                            show_topbar_menu = false
                            if (is_archived) {
                                mail_vm.unarchive(listOf(email_id))
                                on_archive()
                            } else {
                                mail_vm.archive(listOf(email_id))
                                on_archive()
                            }
                        }
                        detail_menu_action(
                            icon = TablerIcons.Mail,
                            text = stringResource(R.string.mark_as_unread),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            mail_vm.mark_unread(email_id)
                            show_toast(context.getString(R.string.marked_as_unread))
                            on_back()
                        }
                        detail_menu_action(
                            icon = if (is_pinned) pin_icon_filled else pin_icon,
                            text = if (is_pinned) stringResource(R.string.unpin) else stringResource(R.string.pin_to_top),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            mail_vm.toggle_pin(email_id)
                        }
                        detail_menu_action(
                            icon = TablerIcons.Moon,
                            text = stringResource(R.string.snooze),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            show_snooze_sheet = true
                        }
                        detail_menu_divider()
                        detail_menu_action(
                            icon = TablerIcons.Folder,
                            text = stringResource(R.string.move_to_folder),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            settings_vm.load_labels()
                            show_folder_sheet = true
                        }
                        detail_menu_action(
                            icon = TablerIcons.Tag,
                            text = stringResource(R.string.add_label),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            settings_vm.load_tags()
                            show_label_sheet = true
                        }
                        detail_menu_action(
                            icon = TablerIcons.Printer,
                            text = stringResource(R.string.print),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            val msg = messages.lastOrNull()
                            if (msg != null) {
                                print_email(context, msg, email?.subject.orEmpty())
                            } else {
                                show_toast(context.getString(R.string.nothing_to_print))
                            }
                        }
                        detail_menu_action(
                            icon = TablerIcons.InfoCircle,
                            text = stringResource(R.string.message_details),
                            tint = colors.text_primary,
                            test_tag = "message_details",
                        ) {
                            show_topbar_menu = false
                            show_message_details = true
                        }
                        detail_menu_action(
                            icon = TablerIcons.Code,
                            text = stringResource(R.string.detail_view_raw_source),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            show_raw_source_dialog = true
                        }
                        detail_menu_divider()
                        detail_menu_action(
                            icon = TablerIcons.AlertOctagon,
                            text = if (is_spam) stringResource(R.string.swipe_not_spam) else stringResource(R.string.report_spam),
                            tint = if (is_spam) colors.accent_blue else colors.danger,
                        ) {
                            show_topbar_menu = false
                            val spam_sender_hint =
                                listOfNotNull(messages.lastOrNull()?.sender_email)
                            if (is_spam) {
                                is_spam_override = false
                                mail_vm.unmark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                            } else {
                                is_spam_override = true
                                mail_vm.mark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                            }
                            on_back()
                        }
                        detail_menu_action(
                            icon = TablerIcons.Ban,
                            text = stringResource(R.string.block_sender),
                            tint = colors.danger,
                        ) {
                            show_topbar_menu = false
                            val sender = messages.lastOrNull()?.sender_email
                            if (!sender.isNullOrBlank()) {
                                pending_block_sender = sender
                            }
                        }
                        detail_menu_action(
                            icon = TablerIcons.Trash,
                            text = stringResource(R.string.delete_action),
                            tint = colors.danger,
                        ) {
                            show_topbar_menu = false
                            mail_vm.trash(listOf(email_id))
                            on_delete()
                        }
                    }
                    }
                }
            }

            val subject_text = email?.subject?.ifBlank { stringResource(R.string.no_subject) }
                ?: stringResource(R.string.no_subject)

            if (email == null) {
                if (thread_state.is_loading) {
                    detail_skeleton()
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(horizontal = AsterSpacing.lg),
                        ) {
                            Text(
                                text = thread_state.error
                                    ?: stringResource(R.string.message_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text_muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            org.astermail.android.design.components.AsterDialogPrimaryButton(
                                label = stringResource(R.string.error_try_again),
                                onClick = { mail_vm.load_thread(email_id) },
                            )
                        }
                    }
                }
                return@Column
            }

            Box(modifier = Modifier.weight(1f).clipToBounds()) {
            LazyColumn(
                state = list_state,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(on_next, on_previous) {
                        var cumulative_drag = 0f
                        var crossed_threshold = false
                        detectHorizontalDragGestures(
                            onDragStart = { cumulative_drag = 0f; crossed_threshold = false },
                            onHorizontalDrag = { _, drag_amount ->
                                cumulative_drag += drag_amount
                                if (!crossed_threshold) {
                                    val crossed_prev = cumulative_drag > swipe_threshold_px && on_previous != null
                                    val crossed_next = cumulative_drag < -swipe_threshold_px && on_next != null
                                    if (crossed_prev || crossed_next) {
                                        crossed_threshold = true
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (cumulative_drag > swipe_threshold_px && on_previous != null) {
                                    on_previous()
                                } else if (cumulative_drag < -swipe_threshold_px && on_next != null) {
                                    on_next()
                                }
                            },
                        )
                    },
            ) {
                item(key = "subject_header") {
                    var subject_expanded by remember(subject_text) { mutableStateOf(false) }
                    var subject_truncated by remember(subject_text) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AsterSpacing.lg, end = AsterSpacing.xs)
                            .padding(top = AsterSpacing.sm, bottom = AsterSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = subject_text,
                            color = colors.text_primary,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (subject_expanded) Int.MAX_VALUE else 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            onTextLayout = { layout ->
                                if (!subject_expanded && layout.hasVisualOverflow) subject_truncated = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (subject_truncated) {
                                        Modifier.clickable { subject_expanded = !subject_expanded }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        org.astermail.android.ui.common.star_toggle_icon(
                            is_starred = is_starred,
                            icon = if (is_starred) Icons.Filled.Star else TablerIcons.Star,
                            tint = if (is_starred) colors.accent_blue else colors.text_muted,
                            icon_size = 22.dp,
                            touch_size = 48.dp,
                            modifier = Modifier.testTag("detail_star"),
                            onClick = {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                mail_vm.toggle_star(email_id)
                                show_toast(
                                    if (!is_starred) {
                                        context.getString(R.string.starred)
                                    } else {
                                        context.getString(R.string.unstarred)
                                    },
                                )
                            },
                        )
                    }
                }
                item {
                    val applied_tag_tokens = api_item?.raw_item?.tag_tokens ?: emptyList()
                    val settings_state_now by settings_vm.state.collectAsStateWithLifecycle()
                    val applied_tags = remember(applied_tag_tokens, settings_state_now.tags) {
                        applied_tag_tokens.mapNotNull { token ->
                            settings_state_now.tags.find { it.tag_token == token }
                        }.filter { it.encrypted_name.isNotBlank() }
                    }
                    val folder_chip = detail_folder_chip_for(
                        item = api_item,
                        folders = settings_state_now.labels,
                        is_spam = is_spam,
                        is_trashed = is_trashed,
                    )
                    if (folder_chip != null || applied_tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AsterSpacing.lg)
                                .padding(bottom = AsterSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (folder_chip != null) {
                                detail_folder_chip(folder_chip)
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                applied_tags.forEach { tag ->
                                    detail_label_chip(tag)
                                }
                            }
                        }
                    }
                }

                items(messages.size, key = { messages[it].id }, contentType = { "thread_message" }) { idx ->
                    val msg = messages[idx]
                    val is_last = idx == messages.size - 1
                    val is_last_message = idx == messages.size - 1
                    val is_expanded = messages.size <= 1 ||
                        expanded_ids.value.contains(msg.id)

                    val is_hidden = msg.id in hidden_id_set
                    val is_after_indicator = hidden_id_set.isNotEmpty() &&
                        idx > 0 && messages[idx - 1].id in hidden_id_set

                    if (is_hidden) {
                        if (idx == first_hidden_idx) {
                            hidden_group_indicator(
                                count = hidden_id_set.size,
                                on_reveal = { hidden_group_revealed = true },
                            )
                        }
                        return@items
                    }

                    val is_system_sender = is_aster_system_sender(msg)

                    if (is_expanded) {
                        expanded_message(
                            msg = msg,
                            is_last = is_last,
                            message_index = idx,
                            thread_attachments = all_thread_attachments,
                            my_email = my_email,
                            my_profile_pic = my_profile_pic,
                            show_top_divider = !is_after_indicator,
                            allow_external = !block_external_images || msg.id in allow_external_ids || is_system_sender,
                            blocked_for_traffic = blocked_for_traffic_only,
                            on_retry_decrypt = { mail_vm.retry_decrypt_thread() },
                            retry_in_progress = decrypt_retry_active,
                            identity_changed = identity_changed_senders.contains(
                                msg.sender_email.trim().lowercase(),
                            ),
                            on_acknowledge_identity = {
                                mail_vm.acknowledge_identity_change(msg.sender_email)
                            },
                            on_load_external = {
                                allow_external_ids = allow_external_ids + msg.id
                            },
                            on_always_allow_external = {
                                allow_external_ids = allow_external_ids + msg.id
                                val base = settings_state.preferences
                                if (base != null) {
                                    settings_vm.save_preferences(base.copy(block_external_images = false))
                                }
                            },
                            on_disable_low_network = {
                                allow_external_ids = allow_external_ids + msg.id
                                val base = settings_state.preferences
                                if (base != null) {
                                    settings_vm.save_preferences(base.copy(low_network_mode = false))
                                }
                            },
                            show_unsub = msg.id !in dismissed_unsub_ids,
                            on_dismiss_unsub = {
                                dismissed_unsub_ids = dismissed_unsub_ids + msg.id
                            },
                            on_body_ready = { body_ready = true },
                            on_track = { _, _, _ -> },
                            access_token = settings_vm.get_access_token(),
                            on_link_click = { url ->
                                if (!url.startsWith("aster:") || is_system_sender) pending_link = url
                            },
                            on_image_click = { src -> lightbox_src = src },
                            on_unsubscribe = { url ->
                                dismissed_unsub_ids = dismissed_unsub_ids + msg.id
                                scope.launch {
                                    if (!is_safe_unsubscribe_url(url)) {
                                        show_toast(context.getString(R.string.could_not_unsubscribe))
                                        return@launch
                                    }
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        show_toast(context.getString(R.string.opening_unsubscribe))
                                    } catch (_: Throwable) {
                                        show_toast(context.getString(R.string.could_not_unsubscribe))
                                    }
                                }
                            },
                            on_collapse = {
                                anchor_toggle(msg.id)
                                expanded_ids.value = expanded_ids.value - msg.id
                            },
                            on_sender_tap = { email, name ->
                                profile_sender = email to name
                            },
                            on_reply = { on_reply(msg.id, thread_ghost_email) },
                            on_reply_all = { on_reply_all(msg.id, thread_ghost_email) },
                            on_forward = { on_forward(msg.id, thread_ghost_email) },
                            on_more = {
                                action_target_id = msg.id
                                show_action_sheet = true
                            },
                            on_attachment_tap = { att ->
                                is_downloading_attachment = true
                                mail_vm.download_attachment(att) { result ->
                                    result.onSuccess { (resolved_att, bytes) ->
                                        preview_attachment = resolved_att
                                        preview_bytes = bytes
                                    }.onFailure { error ->
                                        show_toast(
                                            if (error is AttachmentKeyUnavailableException) context.getString(R.string.attachment_locked)
                                            else context.getString(R.string.failed_to_load_preview),
                                        )
                                    }
                                    is_downloading_attachment = false
                                }
                            },
                            on_attachment_download = { att ->
                                show_toast(context.getString(R.string.downloading_file, att.filename))
                                mail_vm.download_attachment(att) { result ->
                                    result.onSuccess { (resolved_att, bytes) ->
                                        val saved = save_attachment_to_storage(context, resolved_att, bytes)
                                        show_toast(if (saved) context.getString(R.string.saved_file, resolved_att.filename) else context.getString(R.string.failed_to_save))
                                    }.onFailure { error ->
                                        show_toast(
                                            if (error is AttachmentKeyUnavailableException) context.getString(R.string.attachment_locked)
                                            else context.getString(R.string.failed_to_download, att.filename),
                                        )
                                    }
                                }
                            },
                            reactions = if (reactions_enabled) message_reactions[msg.id].orEmpty() else emptyList(),
                            on_react = { emoji ->
                                val blocked = reaction_restriction_for(msg)
                                if (blocked != null) {
                                    show_toast(
                                        context.getString(
                                            org.astermail.android.mail.reaction_restriction_string(blocked),
                                        ),
                                    )
                                    return@expanded_message
                                }
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                val identity = reaction_identity_for(msg.id)
                                mail_vm.send_reaction(
                                    message_id = msg.id,
                                    emoji = emoji,
                                    sender_email = identity.email,
                                    sender_alias_hash = identity.alias_hash,
                                    own_addresses = reaction_own_addresses,
                                ) { error ->
                                    if (error != null) show_toast(error)
                                }
                            },
                            is_system = is_system_sender,
                            can_collapse = messages.size > 1,
                        )
                    } else {
                        collapsed_message(
                            msg = msg,
                            show_top_divider = !is_after_indicator,
                            my_email = my_email,
                            my_profile_pic = my_profile_pic,
                            message_index = idx,
                            on_expand = {
                                anchor_toggle(msg.id)
                                expanded_ids.value = expanded_ids.value + msg.id
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(bottom_bar_height)) }
            }

            }
        }


        if (email != null && messages.isNotEmpty()) {
            val latest_msg = messages.last()
            val detail_prefs_state by settings_vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(detail_prefs_state.preferences?.toolbar_actions) {
                val raw = detail_prefs_state.preferences?.toolbar_actions
                if (raw != null) {
                    cache_toolbar_actions(context, parse_toolbar_actions(raw))
                }
            }
            val detail_toolbar_slots = load_toolbar_actions(context)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.bg_primary)
                    .pointerInput(Unit) {}
                    .navigationBarsPadding()
                    .onGloballyPositioned { coords ->
                        val measured = with(density) { coords.size.height.toDp() }
                        if (measured > 0.dp && measured != bottom_bar_height) bottom_bar_height = measured
                    },
            ) {
                AsterDivider(modifier = Modifier.fillMaxWidth())
                val latest_restriction = reaction_restriction_for(latest_msg)
                LaunchedEffect(latest_restriction) {
                    if (latest_restriction != null) reaction_picker_open = false
                }
                if (latest_restriction == null) {
                    reaction_quick_picker(
                        visible = reaction_picker_open,
                        on_pick = { emoji ->
                            reaction_picker_open = false
                            val blocked = reaction_restriction_for(latest_msg)
                            if (blocked != null) {
                                show_toast(
                                    context.getString(
                                        org.astermail.android.mail.reaction_restriction_string(blocked),
                                    ),
                                )
                                return@reaction_quick_picker
                            }
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val identity = reaction_identity_for(latest_msg.id)
                            mail_vm.send_reaction(
                                message_id = latest_msg.id,
                                emoji = emoji,
                                sender_email = identity.email,
                                sender_alias_hash = identity.alias_hash,
                                own_addresses = reaction_own_addresses,
                            ) { error ->
                                if (error != null) show_toast(error)
                            }
                        },
                    )
                }
                reply_action_row(
                    on_reply = { on_reply(latest_msg.id, thread_ghost_email) },
                    on_forward = { on_forward(latest_msg.id, thread_ghost_email) },
                    show_react = latest_restriction == null ||
                        (
                            latest_restriction != org.astermail.android.mail.ReactionRestriction.disabled &&
                                latest_restriction != org.astermail.android.mail.ReactionRestriction.own_message
                            ),
                    react_enabled = latest_restriction == null,
                    on_react = {
                        val blocked = reaction_restriction_for(latest_msg)
                        if (blocked != null) {
                            show_toast(
                                context.getString(
                                    org.astermail.android.mail.reaction_restriction_string(blocked),
                                ),
                            )
                        } else {
                            reaction_picker_open = !reaction_picker_open
                        }
                    },
                )
                val thread_draft_token = thread_state.item?.thread_token
                if (!thread_draft_token.isNullOrBlank()) {
                    val lifecycle_owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    var thread_draft by remember(thread_draft_token) {
                        mutableStateOf<org.astermail.android.mail.InboxItem?>(null)
                    }
                    var draft_probe_key by remember(thread_draft_token) { mutableStateOf(0) }
                    DisposableEffect(lifecycle_owner, thread_draft_token) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) draft_probe_key++
                        }
                        lifecycle_owner.lifecycle.addObserver(observer)
                        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
                    }
                    LaunchedEffect(thread_draft_token, draft_probe_key) {
                        thread_draft = mail_vm.load_thread_draft(thread_draft_token)
                    }
                    val draft = thread_draft
                    if (draft != null) {
                        val summary = draft.subject.takeIf {
                            it.isNotBlank() && it != stringResource(R.string.no_subject)
                        } ?: draft.preview
                        thread_draft_chip(
                            summary = summary,
                            on_edit = {
                                context.startActivity(
                                    org.astermail.android.ComposeActivity.intent_for(
                                        context,
                                        mode = "draft",
                                        draft_id = draft.id,
                                    ),
                                )
                            },
                            on_delete = {
                                mail_vm.delete_thread_draft(draft.id) { ok ->
                                    if (ok) thread_draft = null
                                }
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.md)
                        .padding(top = 2.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    detail_toolbar_slots.forEach { slot_id ->
                        when (slot_id) {
                            "read" -> {
                                val read_state = api_item?.is_read == true
                                if (read_state) {
                                    bottom_action(TablerIcons.Mail, stringResource(R.string.mark_as_unread), test_tag = "mark_read") {
                                        mail_vm.mark_unread(email_id)
                                        show_toast(context.getString(R.string.marked_as_unread))
                                        on_back()
                                    }
                                } else {
                                    bottom_action(TablerIcons.MailOpened, stringResource(R.string.mark_as_read), test_tag = "mark_read") {
                                        mail_vm.mark_read(email_id)
                                        show_toast(context.getString(R.string.mark_as_read))
                                    }
                                }
                            }
                            "trash" -> bottom_action(TablerIcons.Trash, stringResource(R.string.move_to_trash), test_tag = "delete") {
                                mail_vm.trash(listOf(email_id))
                                on_delete()
                            }
                            "archive" -> {
                                val archived = api_item?.is_archived == true
                                bottom_action(
                                    if (archived) TablerIcons.Inbox else TablerIcons.Archive,
                                    if (archived) stringResource(R.string.swipe_restore) else stringResource(R.string.swipe_archive),
                                    test_tag = "toolbar_archive",
                                ) {
                                    if (archived) {
                                        mail_vm.unarchive(listOf(email_id))
                                        on_archive()
                                    } else {
                                        mail_vm.archive(listOf(email_id))
                                        on_archive()
                                    }
                                }
                            }
                            "folder" -> bottom_action(TablerIcons.Folder, stringResource(R.string.move_to_folder)) {
                                settings_vm.load_labels(force = settings_state.labels.isEmpty())
                                show_folder_sheet = true
                            }
                            "label" -> bottom_action(TablerIcons.Tag, stringResource(R.string.label)) {
                                settings_vm.load_tags(force = settings_state.tags.isEmpty())
                                show_label_sheet = true
                            }
                            "star" -> bottom_action(
                                if (is_starred) TablerIcons.StarOff else TablerIcons.Star,
                                if (is_starred) stringResource(R.string.unstar) else stringResource(R.string.star),
                                test_tag = "toolbar_star",
                            ) {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                mail_vm.toggle_star(email_id)
                                show_toast(if (!is_starred) context.getString(R.string.starred) else context.getString(R.string.unstarred))
                            }
                            "snooze" -> bottom_action(TablerIcons.Clock, stringResource(R.string.snooze)) {
                                show_snooze_sheet = true
                            }
                            "spam" -> bottom_action(TablerIcons.Ban, stringResource(R.string.report_spam), test_tag = "toolbar_spam") {
                                val spam_sender_hint = listOfNotNull(messages.lastOrNull()?.sender_email)
                                if (is_spam) {
                                    is_spam_override = false
                                    mail_vm.unmark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                                } else {
                                    is_spam_override = true
                                    mail_vm.mark_spam(listOf(email_id), sender_emails_hint = spam_sender_hint)
                                    val sender = messages.lastOrNull()?.sender_email
                                    if (!sender.isNullOrBlank()) {
                                        settings_vm.block_sender(sender)
                                    }
                                }
                                on_back()
                            }
                            "reply" -> bottom_action(TablerIcons.ArrowBackUp, stringResource(R.string.reply), test_tag = "toolbar_reply") {
                                on_reply(latest_msg.id, thread_ghost_email)
                            }
                            "forward" -> bottom_action(TablerIcons.MailForward, stringResource(R.string.forward), test_tag = "toolbar_forward") {
                                on_forward(latest_msg.id, thread_ghost_email)
                            }
                        }
                    }
                    bottom_action(TablerIcons.Dots, stringResource(R.string.more)) {
                        action_target_id = null
                        show_action_sheet = true
                    }
                }
            }
        }
    }

    run {
        val target = action_target_id ?: messages.lastOrNull()?.id.orEmpty()
        val item_target = action_target_id ?: email_id
        val message_scope = action_target_id != null
        action_menu_sheet(
            expanded = show_action_sheet,
            on_close = { show_action_sheet = false },
            on_reply = { show_action_sheet = false; on_reply(target, thread_ghost_email) },
            on_reply_all = { show_action_sheet = false; on_reply_all(target, thread_ghost_email) },
            on_forward = { show_action_sheet = false; on_forward(target, thread_ghost_email) },
            on_star = {
                show_action_sheet = false
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                mail_vm.toggle_star(item_target)
                show_toast(if (!is_starred) context.getString(R.string.starred) else context.getString(R.string.unstarred))
            },
            is_starred = is_starred,
            on_mark_unread = {
                show_action_sheet = false
                mail_vm.mark_unread(item_target)
                show_toast(context.getString(R.string.marked_as_unread))
                on_back()
            },
            on_archive = {
                show_action_sheet = false
                if (api_item?.is_archived == true) {
                    mail_vm.unarchive(listOf(item_target))
                    on_archive()
                } else {
                    mail_vm.archive(listOf(item_target), message_scope = message_scope)
                    on_archive()
                }
            },
            on_trash = {
                show_action_sheet = false
                mail_vm.trash(listOf(item_target), message_scope = message_scope)
                on_delete()
            },
            on_spam = {
                show_action_sheet = false
                val target_message = messages.firstOrNull { it.id == item_target } ?: messages.lastOrNull()
                val spam_sender_hint = listOfNotNull(target_message?.sender_email)
                if (is_spam) {
                    is_spam_override = false
                    mail_vm.unmark_spam(listOf(item_target), sender_emails_hint = spam_sender_hint)
                } else {
                    is_spam_override = true
                    mail_vm.mark_spam(listOf(item_target), sender_emails_hint = spam_sender_hint)
                    val sender = target_message?.sender_email
                    if (!sender.isNullOrBlank()) {
                        settings_vm.block_sender(sender)
                    }
                }
                on_back()
            },
            is_spam = is_spam,
            on_snooze = {
                show_action_sheet = false
                show_snooze_sheet = true
            },
            on_label = {
                show_action_sheet = false
                show_label_sheet = true
            },
            on_customize_toolbar = {
                show_action_sheet = false
                on_navigate?.invoke("settings/customize_toolbar")
            },
        )
    }

    val show_preview = preview_attachment != null && preview_bytes != null
    AnimatedVisibility(
        visible = show_preview,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) +
            slideInVertically(
                animationSpec = androidx.compose.animation.core.tween(250),
                initialOffsetY = { it / 6 },
            ),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150)) +
            slideOutVertically(
                animationSpec = androidx.compose.animation.core.tween(200),
                targetOffsetY = { it / 6 },
            ),
    ) {
        val att = preview_attachment
        val byt = preview_bytes
        if (att != null && byt != null) {
            attachment_preview_dialog(
                attachment = att,
                bytes = byt,
                on_close = {
                    preview_attachment = null
                    preview_bytes = null
                },
                on_download = {
                    val saved = save_attachment_to_storage(context, att, byt)
                    Toast.makeText(
                        context,
                        if (saved) context.getString(R.string.saved_file, att.filename) else context.getString(R.string.failed_to_save),
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (saved) {
                        preview_attachment = null
                        preview_bytes = null
                    }
                },
            )
        }
    }

    lightbox_src?.let { src ->
        email_image_lightbox(
            src = src,
            auth_header = settings_vm.get_access_token()?.let { "Bearer $it" },
            on_dismiss = { lightbox_src = null },
        )
    }

    val current_link = pending_link
    if (current_link != null && current_link.startsWith("aster:")) {
        val aster_path = current_link.removePrefix("aster:")
        LaunchedEffect(aster_path) {
            pending_link = null
            on_navigate?.invoke(aster_path)
        }
    }

    if (current_link != null && !current_link.startsWith("aster:")) {
        val link = current_link
        val open_external_link = {
            pending_link = null
            if (!is_safe_external_url(link)) {
                show_toast(context.getString(R.string.could_not_open_link))
            } else {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                } catch (_: Throwable) {
                    show_toast(context.getString(R.string.could_not_open_link))
                }
            }
        }
        if (settings_state.preferences?.warn_suspicious_links == false) {
            LaunchedEffect(link) { open_external_link() }
        } else {
            org.astermail.android.design.components.AsterAlertDialog(
                on_dismiss = { pending_link = null },
                title = stringResource(R.string.open_external_link),
                message = stringResource(R.string.leaving_aster_warning),
                confirm_label = stringResource(R.string.open),
                cancel_label = stringResource(R.string.cancel),
                on_confirm = open_external_link,
                extra_content = {
                    Text(
                        text = link,
                        color = colors.accent_blue,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }

    val profile_target = profile_sender
    if (profile_target != null) {
        sender_profile_sheet(
            sender_email = profile_target.first,
            sender_name = profile_target.second,
            on_close = { profile_sender = null },
            on_copy = { address ->
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("email_address", address))
                show_toast(org.astermail.android.ui.common.copied_toast_text(context, address))
            },
            on_search_sender = { address -> on_navigate?.invoke("search:from:$address") },
            on_send_email = { address ->
                context.startActivity(
                    org.astermail.android.ComposeActivity.intent_for(context, prefill_to = address),
                )
            },
            on_block = { address -> pending_block_sender = address },
            on_result = { message -> show_toast(message) },
        )
    }

    val block_target = pending_block_sender
    if (block_target != null) {
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_block_sender = null },
            title = stringResource(R.string.block_sender),
            message = stringResource(R.string.block_sender_confirm_message),
            confirm_label = stringResource(R.string.block_sender),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
            on_confirm = {
                pending_block_sender = null
                settings_vm.block_sender(block_target) { ok ->
                    if (ok) {
                        show_toast(context.getString(R.string.sender_blocked_named, block_target))
                    } else {
                        show_toast(context.getString(R.string.failed_block_sender))
                    }
                }
                on_back()
            },
            extra_content = {
                Text(
                    text = block_target,
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }

    if (show_encryption_info) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_encryption_info = false },
            title = encryption_badge_label(is_thread_encrypted),
            body = { encryption_info_body(is_thread_encrypted) },
            footer = {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.done),
                    onClick = { show_encryption_info = false },
                )
            },
        )
    }

    if (show_snooze_sheet) {
        snooze_sheet(
            on_close = { show_snooze_sheet = false },
            on_pick = { iso, label ->
                show_snooze_sheet = false
                mail_vm.snooze_until(email_id, iso, label)
                on_back()
            },
        )
    }

    if (show_folder_sheet) {
        val source_folder = inbox_state_for_folder.current_folder
        val settings_state by settings_vm.state.collectAsStateWithLifecycle()
        val unnamed_folder_label = stringResource(R.string.unnamed_folder)
        val folder_decrypt_failed_label = stringResource(R.string.folder_decrypt_failed)
        val folder_items = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
            .map { node ->
                val label = node.label
                val readable = label.encrypted_name?.takeIf { it.isNotBlank() && !looks_encrypted(it) }
                label.copy(encrypted_name = readable ?: folder_decrypt_failed_label)
            }
        label_picker_sheet(
            title = stringResource(R.string.move_to_folder),
            empty_message = stringResource(R.string.no_folders_yet_create),
            items = folder_items,
            on_close = { show_folder_sheet = false },
            on_pick = { picked ->
                val display = picked.encrypted_name?.takeIf { it.isNotBlank() }
                    ?: unnamed_folder_label
                mail_vm.apply_label(email_id, picked.label_token, display)
                show_folder_sheet = false
            },
            on_move_to_inbox = if (can_move_to_inbox(source_folder)) {
                {
                    mail_vm.move_to_inbox(listOf(email_id), source_folder)
                    show_folder_sheet = false
                    on_back()
                }
            } else {
                null
            },
        )
    }

    if (show_label_sheet) {
        val settings_state by settings_vm.state.collectAsStateWithLifecycle()
        val tag_items = org.astermail.android.labels.tag_rows(settings_state.tags)
        val applied_tags = thread_state.item?.takeIf { it.id == email_id }?.tag_tokens?.toSet()
            ?: emptySet()
        tag_picker_sheet(
            title = stringResource(R.string.edit_labels),
            empty_message = stringResource(R.string.no_labels_yet_create),
            items = tag_items,
            on_close = { show_label_sheet = false },
            on_pick = { picked ->
                val display = picked.encrypted_name.takeIf { it.isNotBlank() } ?: picked.tag_token
                if (picked.tag_token in applied_tags) {
                    mail_vm.remove_tag(email_id, picked.tag_token, display)
                } else {
                    mail_vm.apply_tag(email_id, picked.tag_token, display)
                }
                show_label_sheet = false
            },
            applied_tokens = applied_tags,
        )
    }

    if (show_message_details) {
        message_details_dialog(
            message = messages.lastOrNull(),
            subject = email?.subject.orEmpty(),
            on_close = { show_message_details = false },
        )
    }

    if (show_raw_source_dialog) {
        val msg = messages.lastOrNull()
        raw_source_dialog(
            message = msg,
            subject = email?.subject.orEmpty(),
            on_close = { show_raw_source_dialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun expanded_message(
    msg: ThreadMessage,
    is_last: Boolean,
    message_index: Int = 0,
    thread_attachments: List<MessageAttachment> = emptyList(),
    show_top_divider: Boolean = true,
    my_email: String = "",
    my_profile_pic: String? = null,
    allow_external: Boolean = false,
    blocked_for_traffic: Boolean = false,
    on_load_external: () -> Unit = {},
    on_always_allow_external: () -> Unit = {},
    on_disable_low_network: () -> Unit = {},
    show_unsub: Boolean = true,
    on_dismiss_unsub: () -> Unit = {},
    on_unsubscribe: (String) -> Unit = {},
    on_body_ready: () -> Unit = {},
    on_retry_decrypt: () -> Unit = {},
    retry_in_progress: Boolean = false,
    on_track: (String, String, String?) -> Unit = { _, _, _ -> },
    access_token: String? = null,
    on_link_click: (String) -> Unit = {},
    on_image_click: (String) -> Unit = {},
    on_collapse: () -> Unit,
    on_sender_tap: (String, String) -> Unit = { _, _ -> },
    on_reply: () -> Unit,
    on_reply_all: () -> Unit,
    on_forward: () -> Unit,
    on_more: () -> Unit,
    on_attachment_tap: (MessageAttachment) -> Unit = {},
    on_attachment_download: (MessageAttachment) -> Unit = {},
    reactions: List<DecryptedReaction> = emptyList(),
    on_react: (String) -> Unit = {},
    is_system: Boolean = false,
    can_collapse: Boolean = true,
    show_header_reply: Boolean = true,
    identity_changed: Boolean = false,
    on_acknowledge_identity: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val copy_email = { email: String ->
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("email_address", email))
        org.astermail.android.ui.common.show_copied_toast(context, email)
    }
    var show_details by remember { mutableStateOf(false) }
    var addresses_expanded by remember(msg.id) { mutableStateOf(false) }
    var sender_name_truncated by remember(msg.id) { mutableStateOf(false) }
    var sender_email_truncated by remember(msg.id) { mutableStateOf(false) }
    var to_truncated by remember(msg.id) { mutableStateOf(false) }
    val tracker_report = remember(msg.body_html) { EmailHtmlSanitizer.analyze_trackers(msg.body_html) }
    val tracker_count = remember(tracker_report, msg.trackers_blocked) {
        maxOf(msg.trackers_blocked, tracker_report.total)
    }
    var show_tracker_details by remember(msg.id) { mutableStateOf(false) }
    if (show_tracker_details) {
        tracker_details_dialog(report = tracker_report, on_close = { show_tracker_details = false })
    }
    val chevron_rotation by animateFloatAsState(targetValue = if (show_details) 180f else 0f, label = "chevron")
    val auth_status = remember(msg.id, msg.item_type, msg.spf_result, msg.dkim_result, msg.dmarc_result) {
        sender_auth_status(msg)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_primary),
    ) {
        if (show_top_divider) {
            Spacer(Modifier.height(AsterSpacing.sm))
            AsterDivider()
        }
        Spacer(Modifier.height(AsterSpacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (can_collapse) Modifier.clickable(onClick = on_collapse) else Modifier)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
                .testTag("message_header_$message_index"),
            verticalAlignment = Alignment.Top,
        ) {
            val shown_sender_name = displayed_sender_name(msg.display_sender_name, msg.sender_name)
            val shown_sender_email = displayed_sender_email(msg.display_sender_email, msg.sender_email)
            SenderAvatar(
                email = shown_sender_email,
                name = shown_sender_name,
                profile_picture_url = if (msg.sender_email.lowercase() == my_email) my_profile_pic else null,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { on_sender_tap(shown_sender_email, shown_sender_name) },
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shown_sender_name,
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (addresses_expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!addresses_expanded) sender_name_truncated = it.hasVisualOverflow },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .combinedClickable(
                                onClick = {
                                    if (sender_name_truncated || addresses_expanded) {
                                        addresses_expanded = !addresses_expanded
                                    } else if (can_collapse) {
                                        on_collapse()
                                    }
                                },
                                onLongClick = { copy_email(shown_sender_email) },
                            ),
                    )
                }
                if (!shown_sender_name.equals(shown_sender_email, ignoreCase = true)) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = shown_sender_email,
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        maxLines = if (addresses_expanded) 6 else 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!addresses_expanded) sender_email_truncated = it.hasVisualOverflow },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (sender_email_truncated || addresses_expanded) {
                                    addresses_expanded = !addresses_expanded
                                } else if (can_collapse) {
                                    on_collapse()
                                }
                            },
                            onLongClick = { copy_email(shown_sender_email) },
                        ),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.to_label_prefix, msg.to_label),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    maxLines = if (addresses_expanded) 8 else 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!addresses_expanded) to_truncated = it.hasVisualOverflow },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (to_truncated || addresses_expanded) {
                                addresses_expanded = !addresses_expanded
                            } else if (can_collapse) {
                                on_collapse()
                            }
                        },
                        onLongClick = {
                            val recipient = msg.to_addresses.joinToString(", ").ifBlank { msg.to_label }
                            copy_email(recipient)
                        },
                    ),
                )
                val header_received_on = remember(msg) {
                    resolve_received_on_address(msg.raw_headers, msg.to_addresses + msg.cc_addresses, msg.sender_email)
                }
                val header_alias_label = alias_indicator_store.label_for(header_received_on)
                if (header_alias_label != null) {
                    Spacer(Modifier.height(4.dp))
                    alias_chip(header_alias_label, modifier = Modifier.widthIn(max = 200.dp))
                }
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = msg.timestamp.format_relative_time(
                        stringResource(R.string.yesterday),
                        android.text.format.DateFormat.is24HourFormat(LocalContext.current),
                    ),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (show_header_reply) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClick = on_reply),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TablerIcons.ArrowBackUp,
                                contentDescription = stringResource(R.string.reply),
                                tint = colors.text_secondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { show_details = !show_details },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TablerIcons.ChevronDown,
                            contentDescription = if (show_details)
                                stringResource(R.string.detail_hide_details)
                            else
                                stringResource(R.string.detail_show_details),
                            tint = colors.text_secondary,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer(rotationZ = chevron_rotation),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = show_details,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val received_on = remember(msg) {
                resolve_received_on_address(msg.raw_headers, msg.to_addresses + msg.cc_addresses, msg.sender_email)
            }
            val details_sender_name = displayed_sender_name(msg.display_sender_name, msg.sender_name)
            val details_sender_email = displayed_sender_email(msg.display_sender_email, msg.sender_email)
            val details_sender = if (details_sender_name.equals(details_sender_email, ignoreCase = true)) {
                details_sender_email
            } else {
                "$details_sender_name <$details_sender_email>"
            }
            val details_reply_to = remember(msg.raw_headers, details_sender_email) {
                msg.raw_headers
                    .firstOrNull { it.first.equals("reply-to", ignoreCase = true) }
                    ?.second
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.contains(details_sender_email, ignoreCase = true) }
            }
            message_details_panel(
                sender = details_sender,
                reply_to = details_reply_to,
                is_encrypted = msg.is_e2e_encrypted,
                tracker_count = tracker_count,
                date_text = msg.timestamp.format_full_datetime(
                    android.text.format.DateFormat.is24HourFormat(LocalContext.current),
                ),
                received_on = received_on,
                authentication = if (msg.item_type == "received" &&
                    (msg.spf_result != null || msg.dkim_result != null || msg.dmarc_result != null)
                ) {
                    stringResource(
                        R.string.auth_summary_format,
                        auth_result_label(msg.spf_result),
                        auth_result_label(msg.dkim_result),
                        auth_result_label(msg.dmarc_result),
                    )
                } else {
                    null
                },
                authentication_failed = auth_status == SenderAuthStatus.failed,
                on_show_trackers = if (tracker_report.total > 0) ({ show_tracker_details = true }) else null,
            )
        }

        val unsub_info = remember(msg.body_html, msg.body, msg.raw_headers) {
            detect_unsubscribe_info(
                html_content = msg.body_html,
                text_content = msg.body,
                list_unsubscribe = msg.raw_headers.firstOrNull {
                    it.first.equals("list-unsubscribe", ignoreCase = true)
                }?.second,
                list_unsubscribe_post = msg.raw_headers.firstOrNull {
                    it.first.equals("list-unsubscribe-post", ignoreCase = true)
                }?.second,
            )
        }

        val external_counts = remember(msg.body_html) {
            if (msg.body_html != null) count_external_content(msg.body_html) else ExternalContentCounts(0, 0, 0, 0)
        }

        val show_unsub_banner = show_unsub && unsub_info.has_unsubscribe && unsub_info.unsubscribe_link != null
        val show_external_banner = external_counts.total > 0 && !allow_external

        AnimatedVisibility(
            visible = show_unsub_banner,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            LaunchedEffect(msg.id) {
                on_track(msg.sender_email, msg.sender_name, unsub_info.unsubscribe_link)
            }
            unsubscribe_banner(
                on_unsubscribe = { unsub_info.unsubscribe_link?.let { on_unsubscribe(it) } },
            )
        }

        AnimatedVisibility(
            visible = show_external_banner,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (blocked_for_traffic) {
                traffic_saver_banner(
                    counts = external_counts,
                    on_load_once = on_load_external,
                    on_disable_traffic_saving = on_disable_low_network,
                )
            } else {
                external_content_banner(
                    counts = external_counts,
                    on_allow_once = on_load_external,
                    on_always_allow = on_always_allow_external,
                )
            }
        }

        val phishing_result by produceState<org.astermail.android.security.PhishingResult?>(
            initialValue = null,
            msg.body_html, msg.body, msg.sender_email, is_system, auth_status,
        ) {
            value = if (is_system) null else withContext(kotlinx.coroutines.Dispatchers.Default) {
                org.astermail.android.security.analyze_email(
                    html_content = msg.body_html.orEmpty(),
                    text_content = msg.body,
                    sender_name = msg.sender_name,
                    sender_email = msg.sender_email,
                    is_external = true,
                    spf_result = msg.spf_result,
                    dkim_result = msg.dkim_result,
                    dmarc_result = msg.dmarc_result,
                )
            }
        }
        val phishing_snapshot = phishing_result
        AnimatedVisibility(
            visible = phishing_snapshot != null && phishing_snapshot.level != org.astermail.android.security.PhishingLevel.safe,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (phishing_snapshot != null) {
                phishing_banner(result = phishing_snapshot)
            }
        }
        if (auth_status == SenderAuthStatus.failed &&
            (phishing_snapshot == null || phishing_snapshot.level == org.astermail.android.security.PhishingLevel.safe)
        ) {
            sender_unverified_banner(
                sender = displayed_sender_email(msg.display_sender_email, msg.sender_email),
            )
        }
        if (identity_changed) {
            identity_changed_banner(
                sender = displayed_sender_email(msg.display_sender_email, msg.sender_email),
                on_acknowledge = on_acknowledge_identity,
            )
        }

        val inline_images by produceState(
            initialValue = emptyMap<String, String>(),
            msg.body_html,
            msg.attachments,
            thread_attachments,
        ) {
            value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                inline_image_sources(
                    msg.body_html.orEmpty(),
                    msg.attachments + thread_attachments,
                )
            }
        }

        val body_settings_state by shared_settings_view_model().state.collectAsStateWithLifecycle()
        val plain_text_mode = body_settings_state.preferences?.html_rendering_mode == "plain_text"
        if (msg.is_body_pending) {
            email_body_skeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AsterSpacing.xs, bottom = AsterSpacing.sm)
                    .testTag("message_body"),
            )
        } else if (!plain_text_mode && !msg.body_html.isNullOrBlank() && !msg.is_undecryptable) {
            email_html_view(
                html = msg.body_html,
                allow_external = allow_external,
                inline_images = inline_images,
                access_token = access_token,
                on_ready = on_body_ready,
                on_link_click = on_link_click,
                on_image_click = on_image_click,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AsterSpacing.xs, bottom = if (is_last) 0.dp else AsterSpacing.sm)
                    .testTag("message_body"),
            )
        } else if (msg.is_undecryptable) {
            LaunchedEffect(Unit) { on_body_ready() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = AsterSpacing.md)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.bg_secondary)
                    .border(1.dp, colors.border_secondary, RoundedCornerShape(20.dp))
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = TablerIcons.LockOff,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.decrypt_failed_title),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.decrypt_failed_body),
                    color = colors.text_muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AsterSpacing.lg))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent_blue.copy(alpha = if (retry_in_progress) 0.5f else 1f))
                        .clickable(enabled = !retry_in_progress, onClick = on_retry_decrypt)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("retry_decrypt"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (retry_in_progress) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = TablerIcons.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (retry_in_progress) R.string.decrypt_retrying else R.string.retry,
                        ),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else if (msg.body.isBlank()) {
            LaunchedEffect(Unit) { on_body_ready() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Lock,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.e2e_encrypted_message),
                    color = colors.text_muted,
                    fontSize = 14.sp,
                )
            }
        } else {
            val e2e_no_key_text = stringResource(R.string.e2e_no_key_description)
            val no_body_text = stringResource(R.string.no_body)
            val plain_html by produceState(initialValue = "", msg.body, msg.is_encrypted, e2e_no_key_text, no_body_text) {
                value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                val body_source = msg.body.ifBlank {
                    if (msg.is_encrypted) {
                        e2e_no_key_text
                    } else {
                        no_body_text
                    }
                }
                org.astermail.android.mail.build_plain_text_html(body_source)
                }
            }
            email_html_view(
                html = plain_html,
                allow_external = false,
                access_token = access_token,
                on_ready = on_body_ready,
                on_link_click = on_link_click,
                on_image_click = on_image_click,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AsterSpacing.xs, bottom = if (is_last) 0.dp else AsterSpacing.sm)
                    .testTag("message_body"),
            )
        }

        val visible_attachments = remember(msg.attachments, msg.body_html, inline_images) {
            val body = msg.body_html.orEmpty()
            msg.attachments.filter { att ->
                val cid = normalized_content_id(att.content_id)
                if (cid.isBlank()) return@filter true
                if (!body.contains("cid:$cid", ignoreCase = true)) return@filter true
                !inline_images.containsKey(cid)
            }
        }
        if (visible_attachments.isNotEmpty()) {
            attachment_section(
                attachments = visible_attachments,
                on_tap = on_attachment_tap,
                on_download = on_attachment_download,
            )
        }

        reaction_chip_row(reactions = reactions, my_email = my_email)

        if (!is_last) Spacer(Modifier.height(AsterSpacing.md))
    }
}

@Composable
private fun reply_action_row(
    on_reply: () -> Unit,
    on_forward: () -> Unit,
    show_react: Boolean = false,
    react_enabled: Boolean = true,
    on_react: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val config = LocalConfiguration.current
    var label_size by remember(config) { mutableStateOf(REPLY_ACTION_LABEL_MAX) }
    val on_label_overflow: () -> Unit = {
        if (label_size.value > REPLY_ACTION_LABEL_MIN.value) {
            label_size = (label_size.value - 1f).sp
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reply_action_button(
            icon = TablerIcons.ArrowBackUp,
            label = stringResource(R.string.reply),
            bg = colors.accent_blue,
            fg = androidx.compose.ui.graphics.Color.White,
            label_size = label_size,
            on_label_overflow = on_label_overflow,
            on_click = on_reply,
            modifier = Modifier.weight(1f),
        )
        reply_action_button(
            icon = TablerIcons.MailForward,
            label = stringResource(R.string.forward),
            bg = androidx.compose.ui.graphics.Color.Transparent,
            fg = colors.text_primary,
            label_size = label_size,
            on_label_overflow = on_label_overflow,
            on_click = on_forward,
            modifier = Modifier.weight(1f),
        )
        if (show_react) {
            val react_alpha = if (react_enabled) 1f else 0.4f
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(SquircleShape(999.dp))
                    .border(
                        1.dp,
                        colors.text_secondary.copy(alpha = 0.35f * react_alpha),
                        SquircleShape(999.dp),
                    )
                    .clickable(onClick = on_react)
                    .testTag("detail_react"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.MoodSmile,
                    contentDescription = stringResource(R.string.add_reaction),
                    tint = colors.text_secondary.copy(alpha = react_alpha),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun thread_draft_chip(
    summary: String,
    on_edit: () -> Unit,
    on_delete: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(SquircleShape(14.dp))
            .background(colors.bg_secondary)
            .clickable(onClick = on_edit)
            .padding(horizontal = AsterSpacing.sm, vertical = 8.dp)
            .testTag("thread_draft_chip"),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Pencil,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.sender_draft),
            style = MaterialTheme.typography.labelMedium,
            color = colors.text_primary,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = colors.text_secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(SquircleShape(999.dp))
                .clickable(onClick = on_delete)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("thread_draft_delete"),
        ) {
            Text(
                text = stringResource(R.string.delete),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent_blue,
            )
        }
    }
}

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "🎉", "😮", "😢")

@Composable
private fun reaction_quick_picker(visible: Boolean, on_pick: (String) -> Unit) {
    val colors = AsterMaterial.colors
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(120)) + expandVertically(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(90)) + shrinkVertically(animationSpec = tween(120)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(SquircleShape(999.dp))
                        .background(colors.bg_tertiary)
                        .clickable { on_pick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun reaction_chip_row(
    reactions: List<DecryptedReaction>,
    my_email: String,
) {
    if (reactions.isEmpty()) return
    val colors = AsterMaterial.colors
    var info_emoji by remember { mutableStateOf<String?>(null) }
    val groups = remember(reactions, my_email) {
        reactions.groupBy { it.emoji }
            .map { (emoji, list) ->
                Triple(
                    emoji,
                    list.size,
                    list.any { it.is_own || it.reactor_email.equals(my_email, ignoreCase = true) },
                )
            }
            .sortedByDescending { it.second }
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
            .testTag("reaction_chip_row"),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
    ) {
        groups.forEach { (emoji, count, mine) ->
            val shape = SquircleShape(999.dp)
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(shape)
                    .background(if (mine) colors.accent_blue.copy(alpha = 0.16f) else colors.bg_tertiary)
                    .border(
                        width = 1.dp,
                        color = if (mine) colors.accent_blue.copy(alpha = 0.55f) else colors.border_primary,
                        shape = shape,
                    )
                    .clickable { info_emoji = emoji }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = emoji, fontSize = 15.sp)
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mine) colors.accent_blue else colors.text_secondary,
                )
            }
        }
    }

    info_emoji?.let { emoji ->
        val reactors = reactions.filter { it.emoji == emoji }
        val you_label = stringResource(R.string.reaction_info_you, emoji)
        val others = reactors
            .filterNot { it.is_own || it.reactor_email.equals(my_email, ignoreCase = true) }
            .map { it.reactor_email }
            .distinct()
        val mine = reactors.any { it.is_own || it.reactor_email.equals(my_email, ignoreCase = true) }
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { info_emoji = null },
            title = stringResource(R.string.reaction_info_title),
            confirm_label = stringResource(R.string.ok),
            on_confirm = { info_emoji = null },
            extra_content = {
                Column(modifier = Modifier.fillMaxWidth().testTag("reaction_info_dialog")) {
                    if (mine) {
                        Text(text = you_label, color = colors.text_primary, fontSize = 15.sp)
                        Spacer(Modifier.height(AsterSpacing.xs))
                    }
                    others.forEach { email ->
                        Text(
                            text = stringResource(R.string.reaction_info_other, email, emoji),
                            color = colors.text_secondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(AsterSpacing.xs))
                    }
                    Spacer(Modifier.height(AsterSpacing.xs))
                    Text(
                        text = stringResource(R.string.reaction_cannot_be_removed),
                        color = colors.text_muted,
                        fontSize = 13.sp,
                    )
                }
            },
        )
    }
}

private val REPLY_ACTION_LABEL_MAX = 14.sp
private val REPLY_ACTION_LABEL_MIN = 9.sp

@Composable
internal fun reply_action_button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    label_size: TextUnit,
    on_label_overflow: () -> Unit,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val border_color = if (bg == colors.accent_blue) {
        androidx.compose.ui.graphics.Color.Transparent
    } else {
        colors.text_secondary.copy(alpha = 0.35f)
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(SquircleShape(999.dp))
            .background(bg)
            .border(1.dp, border_color, SquircleShape(999.dp))
            .clickable(onClick = on_click)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = fg,
            fontSize = label_size,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            onTextLayout = { result -> if (result.hasVisualOverflow) on_label_overflow() },
        )
    }
}

@Composable
internal fun compact_banner_action(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Text(
        text = label,
        color = colors.accent_blue,
        fontSize = 13.sp,
        fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(SquircleShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
internal fun compact_banner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on_icon_click: (() -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = 3.dp)
            .clip(SquircleShape(10.dp))
            .background(colors.bg_secondary)
            .padding(start = AsterSpacing.md, end = AsterSpacing.sm, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (on_icon_click != null) colors.accent_blue else colors.text_secondary,
            modifier = Modifier
                .then(
                    if (on_icon_click != null) {
                        Modifier
                            .clip(SquircleShape(6.dp))
                            .clickable(onClick = on_icon_click)
                            .padding(2.dp)
                    } else {
                        Modifier
                    },
                )
                .size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = if (expanded) 6 else 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = !expanded },
        )
        Spacer(Modifier.width(6.dp))
        actions()
    }
}

@Composable
internal fun unsubscribe_banner(
    on_unsubscribe: () -> Unit,
) {
    compact_banner(
        icon = TablerIcons.Mail,
        label = stringResource(R.string.detail_unsubscribe_title),
    ) {
        compact_banner_action(
            label = stringResource(R.string.unsubscribe),
            primary = true,
            onClick = on_unsubscribe,
        )
    }
}

@Composable
private fun blocked_content_details_dialog(
    counts: ExternalContentCounts,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.blocked_content_details_title),
        message = null,
        body = ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.blocked_content_details_hint),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(AsterSpacing.sm))
                counts.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (item.type) {
                                ExternalContentType.image -> stringResource(R.string.blocked_type_image)
                                ExternalContentType.tracker -> stringResource(R.string.blocked_type_tracker)
                                ExternalContentType.font -> stringResource(R.string.blocked_type_font)
                                ExternalContentType.stylesheet -> stringResource(R.string.blocked_type_stylesheet)
                            },
                            color = colors.text_muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(SquircleShape(4.dp))
                                .background(colors.bg_tertiary)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = external_display_url(item.url),
                            color = colors.text_secondary,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.close),
                onClick = on_close,
            )
        },
    )
}

@Composable
private fun tracker_details_section_label(text: String) {
    Text(
        text = text,
        color = AsterMaterial.colors.text_muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
    )
}

@Composable
private fun tracker_details_dialog(
    report: EmailHtmlSanitizer.TrackerReport,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.tracker_protection),
        message = null,
        body = ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.tracker_details_hint),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
                if (report.pixel_domains.isNotEmpty()) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    tracker_details_section_label(stringResource(R.string.spy_pixels_blocked))
                    Spacer(Modifier.height(4.dp))
                    report.pixel_domains.forEach { (domain, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = domain,
                                color = colors.text_secondary,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (count > 1) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "x$count",
                                    color = colors.text_muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                if (report.param_counts.isNotEmpty()) {
                    Spacer(Modifier.height(AsterSpacing.md))
                    tracker_details_section_label(stringResource(R.string.links_cleaned))
                    Spacer(Modifier.height(4.dp))
                    report.param_counts.forEach { (param, count) ->
                        Text(
                            text = stringResource(R.string.param_removed_from_links, param, count),
                            color = colors.text_secondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.close),
                onClick = on_close,
            )
        },
    )
}

@Composable
internal fun external_content_banner(
    counts: ExternalContentCounts,
    on_allow_once: () -> Unit,
    on_always_allow: () -> Unit,
) {
    val summary_parts = mutableListOf<String>()
    if (counts.image_count > 0) {
        val n = counts.image_count
        summary_parts.add(if (n == 1) stringResource(R.string.one_image) else stringResource(R.string.n_images, n))
    }
    if (counts.tracker_count > 0) {
        val n = counts.tracker_count
        summary_parts.add(if (n == 1) stringResource(R.string.one_tracker) else stringResource(R.string.n_trackers, n))
    }
    if (counts.font_count > 0) {
        val n = counts.font_count
        summary_parts.add(if (n == 1) stringResource(R.string.one_font) else stringResource(R.string.n_fonts, n))
    }
    if (counts.css_count > 0) {
        val n = counts.css_count
        summary_parts.add(if (n == 1) stringResource(R.string.one_stylesheet) else stringResource(R.string.n_stylesheets, n))
    }
    val label = if (summary_parts.isNotEmpty()) summary_parts.joinToString(", ")
        else stringResource(R.string.detail_external_images_blocked)
    var show_details by remember { mutableStateOf(false) }
    if (show_details) {
        blocked_content_details_dialog(counts = counts, on_close = { show_details = false })
    }
    compact_banner(
        icon = TablerIcons.PhotoOff,
        label = label,
        on_icon_click = if (counts.items.isNotEmpty()) ({ show_details = true }) else null,
    ) {
        compact_banner_action(
            label = stringResource(R.string.detail_external_allow_once),
            primary = false,
            onClick = on_allow_once,
        )
        Text("·", color = AsterMaterial.colors.text_muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        compact_banner_action(
            label = stringResource(R.string.detail_external_always_allow),
            primary = true,
            onClick = on_always_allow,
        )
    }
}

@Composable
internal fun traffic_saver_banner(
    counts: ExternalContentCounts,
    on_load_once: () -> Unit,
    on_disable_traffic_saving: () -> Unit,
) {
    val summary_parts = mutableListOf<String>()
    if (counts.image_count > 0) {
        val n = counts.image_count
        summary_parts.add(if (n == 1) stringResource(R.string.one_image) else stringResource(R.string.n_images, n))
    }
    if (counts.font_count > 0) {
        val n = counts.font_count
        summary_parts.add(if (n == 1) stringResource(R.string.one_font) else stringResource(R.string.n_fonts, n))
    }
    val label = if (summary_parts.isNotEmpty()) summary_parts.joinToString(", ")
        else stringResource(R.string.detail_external_images_traffic_blocked)
    compact_banner(icon = TablerIcons.PhotoOff, label = label) {
        compact_banner_action(
            label = stringResource(R.string.detail_external_allow_once),
            primary = false,
            onClick = on_load_once,
        )
        Text("·", color = AsterMaterial.colors.text_muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        compact_banner_action(
            label = stringResource(R.string.detail_disable_traffic_saving),
            primary = true,
            onClick = on_disable_traffic_saving,
        )
    }
}

@Composable
private fun raw_source_dialog(
    message: ThreadMessage?,
    subject: String,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val headers = remember(message) {
        if (message == null) return@remember ""
        buildString {
            append("From: ").append(message.sender_name).append(" <").append(message.sender_email).append(">\n")
            append("To: ").append(message.to_label).append("\n")
            if (subject.isNotBlank()) append("Subject: ").append(subject).append("\n")
            append("Date: ").append(java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US).format(java.util.Date(message.timestamp))).append("\n")
            append("Message-Id: ").append(message.id).append("\n")
            append("X-Encrypted: ").append(if (message.is_e2e_encrypted) "end-to-end" else "in-transit").append("\n")
            if (message.trackers_blocked > 0) append("X-Aster-Trackers-Blocked: ").append(message.trackers_blocked).append("\n")
        }
    }
    val body_text = remember(message) {
        message?.body_html?.takeIf { it.isNotBlank() } ?: message?.body.orEmpty()
    }
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.detail_raw_source_title),
        message = if (message == null) stringResource(R.string.detail_raw_source_unavailable) else null,
        body = if (message == null) null else ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.detail_raw_source_headers),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = headers,
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.detail_raw_source_body),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body_text,
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.detail_raw_source_close),
                onClick = on_close,
            )
            if (message != null) {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.detail_raw_source_copy),
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("raw_source", headers + "\n" + body_text)
                        clip.description.extras = android.os.PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.detail_raw_source_copied), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        },
    )
}

@Composable
private fun message_detail_row(label: String, value: String) {
    val colors = AsterMaterial.colors
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(78.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = colors.text_primary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun message_details_dialog(
    message: ThreadMessage?,
    subject: String,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val headers_text = remember(message) {
        message?.raw_headers?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "${it.first}: ${it.second}" }
            .orEmpty()
    }
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.message_details),
        message = if (message == null) stringResource(R.string.detail_raw_source_unavailable) else null,
        body = if (message == null) null else ({
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                message_detail_row(
                    stringResource(R.string.from_label),
                    "${displayed_sender_name(message.display_sender_name, message.sender_name)} " +
                        "<${displayed_sender_email(message.display_sender_email, message.sender_email)}>",
                )
                if (message.to_label.isNotBlank()) {
                    message_detail_row(stringResource(R.string.to_label), message.to_label)
                }
                resolve_received_on_address(message.raw_headers, message.to_addresses + message.cc_addresses, message.sender_email)?.let {
                    message_detail_row(stringResource(R.string.received_on_label), it)
                }
                message_detail_row(
                    stringResource(R.string.date),
                    java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                        .format(java.util.Date(message.timestamp)),
                )
                if (subject.isNotBlank()) {
                    message_detail_row(stringResource(R.string.subject_label), subject)
                }
                message_detail_row(
                    stringResource(R.string.message_id_label),
                    "<${message.id}@astermail.org>",
                )
                message_detail_row(
                    stringResource(R.string.encryption),
                    if (message.is_e2e_encrypted) stringResource(R.string.encrypted_e2e) else stringResource(R.string.encrypted_in_transit),
                )
                Spacer(Modifier.height(AsterSpacing.md))
                Text(
                    text = stringResource(R.string.message_headers),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = headers_text.ifBlank { stringResource(R.string.no_raw_headers) },
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogOutlineButton(
                label = stringResource(R.string.detail_raw_source_close),
                onClick = on_close,
            )
            if (message != null && headers_text.isNotBlank()) {
                org.astermail.android.design.components.AsterDialogPrimaryButton(
                    label = stringResource(R.string.copy_headers),
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("headers", headers_text)
                        clip.description.extras = android.os.PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.headers_copied), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        },
    )
}

@Composable
internal fun message_details_panel(
    sender: String,
    reply_to: String?,
    date_text: String,
    is_encrypted: Boolean,
    tracker_count: Int,
    received_on: String?,
    authentication: String?,
    authentication_failed: Boolean,
    on_show_trackers: (() -> Unit)?,
) {
    val colors = AsterMaterial.colors
    var show_security by remember { mutableStateOf(false) }
    val encryption_value = if (is_encrypted) {
        stringResource(R.string.encrypted_e2e)
    } else {
        stringResource(R.string.encrypted_in_transit)
    }
    val encryption_tint = if (is_encrypted) AsterColors.accent_blue else colors.text_muted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(SquircleShape(14.dp))
            .background(colors.bg_secondary)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        detail_meta_row(label = stringResource(R.string.from), value = sender)
        if (reply_to != null) {
            detail_meta_row(label = stringResource(R.string.reply_to_label), value = reply_to)
        }
        detail_meta_row(label = stringResource(R.string.date), value = date_text)
        detail_meta_row(
            label = stringResource(R.string.encryption),
            value = encryption_value,
            icon = TablerIcons.Lock,
            value_tint = encryption_tint,
        )
        Text(
            text = stringResource(R.string.view_encryption_details),
            color = AsterColors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(SquircleShape(8.dp))
                .clickable { show_security = true }
                .padding(vertical = 5.dp),
        )
    }
    if (show_security) {
        security_details_dialog(
            is_encrypted = is_encrypted,
            tracker_count = tracker_count,
            received_on = received_on,
            authentication = authentication,
            authentication_failed = authentication_failed,
            on_show_trackers = on_show_trackers,
            on_close = { show_security = false },
        )
    }
}

@Composable
private fun security_details_dialog(
    is_encrypted: Boolean,
    tracker_count: Int,
    received_on: String?,
    authentication: String?,
    authentication_failed: Boolean,
    on_show_trackers: (() -> Unit)?,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    org.astermail.android.design.components.AsterDialog(
        on_dismiss = on_close,
        title = stringResource(R.string.security_details_title),
        message = null,
        body = ({
            Column(modifier = Modifier.fillMaxWidth()) {
                detail_meta_row(
                    label = stringResource(R.string.encryption),
                    value = if (is_encrypted) {
                        stringResource(R.string.encrypted_e2e)
                    } else {
                        stringResource(R.string.encrypted_in_transit)
                    },
                    icon = TablerIcons.Lock,
                    value_tint = if (is_encrypted) AsterColors.accent_blue else colors.text_muted,
                )
                detail_meta_row(
                    label = stringResource(R.string.tracker_protection),
                    value = if (tracker_count > 0) {
                        stringResource(R.string.trackers_blocked_count, tracker_count)
                    } else {
                        stringResource(R.string.no_trackers)
                    },
                    icon = TablerIcons.ShieldLock,
                    value_tint = if (tracker_count > 0) colors.warning else colors.success,
                    modifier = Modifier.testTag("tracker_badge"),
                    on_click = if (on_show_trackers != null) ({
                        on_close()
                        on_show_trackers()
                    }) else null,
                )
                if (received_on != null) {
                    detail_meta_row(
                        label = stringResource(R.string.received_on_label),
                        value = received_on,
                    )
                }
                if (authentication != null) {
                    detail_meta_row(
                        label = stringResource(R.string.sender_authentication),
                        value = authentication,
                        value_tint = if (authentication_failed) colors.danger else null,
                    )
                }
            }
        }),
        footer = {
            org.astermail.android.design.components.AsterDialogPrimaryButton(
                label = stringResource(R.string.close),
                onClick = on_close,
            )
        },
    )
}

@Composable
private fun detail_meta_row(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    value_tint: androidx.compose.ui.graphics.Color? = null,
    on_click: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (on_click != null) Modifier.clickable(onClick = on_click) else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.text_muted,
            fontSize = 13.sp,
            modifier = Modifier.width(116.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = value_tint ?: colors.text_muted,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = value,
                color = value_tint ?: colors.text_secondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (on_click != null) {
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun collapsed_message(
    msg: ThreadMessage,
    show_top_divider: Boolean = true,
    my_email: String = "",
    my_profile_pic: String? = null,
    message_index: Int = 0,
    on_expand: () -> Unit,
) {
    val colors = AsterMaterial.colors

    val is_undecryptable = msg.is_undecryptable || (msg.sender_email.isBlank() && msg.body.isBlank())

    Column(modifier = Modifier.fillMaxWidth()) {
        if (show_top_divider) {
            Spacer(Modifier.height(AsterSpacing.sm))
            AsterDivider()
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = on_expand)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
                .testTag("message_header_$message_index"),
            verticalAlignment = Alignment.Top,
        ) {
            if (is_undecryptable) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.bg_card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Lock,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                SenderAvatar(
                    email = displayed_sender_email(msg.display_sender_email, msg.sender_email),
                    name = displayed_sender_name(msg.display_sender_name, msg.sender_name),
                    size = 40.dp,
                    profile_picture_url = if (msg.sender_email.lowercase() == my_email) my_profile_pic else null,
                )
            }
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (is_undecryptable) stringResource(R.string.encrypted)
                            else displayed_sender_name(msg.display_sender_name, msg.sender_name),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = msg.timestamp.format_relative_time(
                            stringResource(R.string.yesterday),
                            android.text.format.DateFormat.is24HourFormat(LocalContext.current),
                        ),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    text = if (is_undecryptable) stringResource(R.string.e2e_encrypted_message)
                        else clean_preview_text(msg.preview, msg.body).ifBlank { stringResource(R.string.end_to_end_encrypted) },
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun clean_preview_text(preview: String, body: String): String {
    val source = preview.ifBlank {
        body.lineSequence().map { it.trim() }.firstOrNull { line ->
            line.isNotBlank() && line.any { it.isLetterOrDigit() }
        }.orEmpty()
    }
    val collapsed = source
        .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), "")
        .replace(Regex("[*_~`#=\\-]{2,}"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return collapsed
}

@Composable
private fun hidden_group_indicator(
    count: Int,
    on_reveal: () -> Unit,
) {
    val colors = AsterMaterial.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_reveal)
            .padding(vertical = AsterSpacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        AsterDivider(modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier
                .padding(start = AsterSpacing.md)
                .height(40.dp)
                .clip(CircleShape)
                .background(colors.bg_secondary)
                .border(1.dp, colors.border_secondary, CircleShape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = count.toString(),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun action_chip(
    label: String,
    icon: ImageVector,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    is_primary: Boolean = false,
    mirror_icon: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val bg = if (is_primary) colors.accent_blue else colors.bg_secondary
    val text_color = if (is_primary) Color.White else colors.text_primary
    val icon_color = if (is_primary) Color.White else colors.text_secondary

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(SquircleShape(18.dp))
            .background(bg)
            .clickable(onClick = on_click),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = icon_color,
            modifier = Modifier
                .size(16.dp)
                .then(
                    if (mirror_icon) Modifier.graphicsLayer(scaleX = -1f) else Modifier,
                ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = text_color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private object action_menu_position_provider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
internal fun action_menu_sheet(
    expanded: Boolean,
    on_close: () -> Unit,
    on_reply: () -> Unit,
    on_reply_all: () -> Unit,
    on_forward: () -> Unit,
    on_star: () -> Unit,
    is_starred: Boolean,
    on_mark_unread: () -> Unit,
    on_archive: () -> Unit,
    on_trash: () -> Unit,
    on_spam: () -> Unit,
    is_spam: Boolean = false,
    on_snooze: () -> Unit = {},
    on_label: () -> Unit = {},
    on_customize_toolbar: () -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val visible_state = remember { MutableTransitionState(false) }
    visible_state.targetState = expanded
    if (!visible_state.currentState && !visible_state.targetState) return

    val shape = SquircleShape(18.dp)
    val scrim_interaction = remember { MutableInteractionSource() }
    Popup(
        popupPositionProvider = action_menu_position_provider,
        onDismissRequest = on_close,
        properties = PopupProperties(focusable = true),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = visible_state,
                enter = fadeIn(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(120)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(
                            interactionSource = scrim_interaction,
                            indication = null,
                            onClick = on_close,
                        ),
                )
            }
            AnimatedVisibility(
                visibleState = visible_state,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = AsterSpacing.sm, bottom = AsterSpacing.sm),
                enter = fadeIn(animationSpec = tween(120, easing = LinearOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(190, easing = FastOutSlowInEasing),
                        initialScale = 0.88f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ),
                exit = fadeOut(animationSpec = tween(110)) +
                    scaleOut(
                        animationSpec = tween(130, easing = FastOutLinearInEasing),
                        targetScale = 0.94f,
                        transformOrigin = TransformOrigin(1f, 1f),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .testTag("action_menu")
                        .shadow(18.dp, shape, clip = false)
                        .clip(shape)
                        .background(colors.dropdown_bg)
                        .border(1.dp, colors.border_primary, shape)
                        .widthIn(min = 240.dp, max = 320.dp)
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                ) {
                    aster_dropdown_item(stringResource(R.string.reply), on_reply, icon = TablerIcons.ArrowBackUp)
                    aster_dropdown_item(stringResource(R.string.reply_all), on_reply_all, icon = TablerIcons.ArrowsLeft)
                    aster_dropdown_item(stringResource(R.string.forward), on_forward, icon = TablerIcons.MailForward)
                    aster_dropdown_divider()
                    aster_dropdown_item(
                        if (is_starred) stringResource(R.string.unstar) else stringResource(R.string.star),
                        on_star,
                        icon = TablerIcons.Star,
                    )
                    aster_dropdown_item(stringResource(R.string.mark_as_unread), on_mark_unread, icon = TablerIcons.Mail)
                    aster_dropdown_item(stringResource(R.string.label), on_label, icon = TablerIcons.Tag)
                    aster_dropdown_item(stringResource(R.string.snooze), on_snooze, icon = TablerIcons.Moon)
                    aster_dropdown_divider()
                    aster_dropdown_item(stringResource(R.string.swipe_archive), on_archive, icon = TablerIcons.Archive)
                    if (is_spam) {
                        aster_dropdown_item(
                            stringResource(R.string.swipe_not_spam),
                            on_spam,
                            icon = TablerIcons.ShieldCheck,
                            tint = colors.accent_blue,
                        )
                    } else {
                        aster_dropdown_item(
                            stringResource(R.string.report_spam),
                            on_spam,
                            icon = TablerIcons.AlertTriangle,
                            destructive = true,
                        )
                    }
                    aster_dropdown_item(
                        stringResource(R.string.move_to_trash),
                        on_trash,
                        icon = TablerIcons.Trash,
                        destructive = true,
                    )
                    aster_dropdown_divider()
                    aster_dropdown_item(
                        stringResource(R.string.customize_toolbar),
                        on_customize_toolbar,
                        icon = TablerIcons.Adjustments,
                        tint = colors.text_secondary,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun snooze_sheet(
    on_close: () -> Unit,
    on_pick: (iso: String, label: String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val state = rememberModalBottomSheetState()
    val later_today = stringResource(R.string.snooze_later_today)
    val tomorrow_morning = stringResource(R.string.snooze_tomorrow_morning)
    val this_weekend_label = stringResource(R.string.snooze_this_weekend)
    val next_week_label = stringResource(R.string.snooze_next_week)
    val options = remember(later_today, tomorrow_morning, this_weekend_label, next_week_label) {
        snooze_options(later_today, tomorrow_morning, this_weekend_label, next_week_label)
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
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.snooze_until),
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = AsterSpacing.xl,
                    end = AsterSpacing.xl,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.sm,
                ),
            )
            options.forEach { (label, iso) ->
                sheet_row(label, colors.text_primary) { on_pick(iso, label) }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun label_picker_sheet(
    title: String,
    empty_message: String,
    items: List<org.astermail.android.api.labels.LabelItem>,
    on_close: () -> Unit,
    on_pick: (org.astermail.android.api.labels.LabelItem) -> Unit,
    applied_tokens: Set<String> = emptySet(),
    on_move_to_inbox: (() -> Unit)? = null,
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
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = AsterSpacing.xl,
                    end = AsterSpacing.xl,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.sm,
                ),
            )
            if (on_move_to_inbox != null) {
                sheet_row(
                    label = stringResource(R.string.folder_inbox),
                    tint = colors.text_primary,
                    icon = TablerIcons.Inbox,
                    on_click = on_move_to_inbox,
                )
                AsterDivider(modifier = Modifier.padding(horizontal = AsterSpacing.xl))
            }
            if (items.isEmpty()) {
                Text(
                    text = empty_message,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.md),
                )
            } else {
                items.forEach { item ->
                    val display = item.encrypted_name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.unnamed_folder)
                    val applied = item.label_token in applied_tokens
                    sheet_row(
                        label = display,
                        tint = colors.text_primary,
                        icon = if (applied) TablerIcons.Check else null,
                    ) { on_pick(item) }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun tag_picker_sheet(
    title: String,
    empty_message: String,
    items: List<org.astermail.android.api.tags.TagItem>,
    on_close: () -> Unit,
    on_pick: (org.astermail.android.api.tags.TagItem) -> Unit,
    applied_tokens: Set<String> = emptySet(),
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
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    start = AsterSpacing.xl,
                    end = AsterSpacing.xl,
                    top = AsterSpacing.xs,
                    bottom = AsterSpacing.sm,
                ),
            )
            if (items.isEmpty()) {
                Text(
                    text = empty_message,
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.md),
                )
            } else {
                items.forEach { item ->
                    val display = item.encrypted_name.takeIf { it.isNotBlank() } ?: item.tag_token
                    val tag_color = try {
                        item.encrypted_color?.let { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) }
                    } catch (_: Throwable) { null }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { on_pick(item) }
                            .padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tag_color != null) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(tag_color, shape = CircleShape),
                            )
                            Spacer(Modifier.width(AsterSpacing.md))
                        }
                        Text(
                            text = display,
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.tag_token in applied_tokens) {
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.accent_blue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

private const val SNOOZE_MORNING_HOUR = 9

private const val SNOOZE_LATER_TODAY_OFFSET_HOURS = 4L

private fun at_snooze_morning(value: java.time.ZonedDateTime): java.time.ZonedDateTime =
    value.withHour(SNOOZE_MORNING_HOUR).withMinute(0).withSecond(0).withNano(0)

internal fun snooze_options(
    later_today_label: String,
    tomorrow_morning_label: String,
    this_weekend_label: String,
    next_week_label: String,
): List<Pair<String, String>> {
    val now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
    val later_today = now.plusHours(SNOOZE_LATER_TODAY_OFFSET_HOURS)
    val tomorrow = at_snooze_morning(now.plusDays(1))
    val days_until_saturday = if (now.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
        7L
    } else {
        ((java.time.DayOfWeek.SATURDAY.value - now.dayOfWeek.value + 7) % 7).toLong()
    }
    val this_weekend = at_snooze_morning(now.plusDays(days_until_saturday))
    val next_week = at_snooze_morning(now.plusDays(7))
    val fmt = java.time.format.DateTimeFormatter.ISO_INSTANT
    fun iso(z: java.time.ZonedDateTime) = fmt.format(z.toInstant())
    return listOf(
        later_today_label to iso(later_today),
        tomorrow_morning_label to iso(tomorrow),
        this_weekend_label to iso(this_weekend),
        next_week_label to iso(next_week),
    )
}

private fun print_email(context: android.content.Context, msg: ThreadMessage, subject: String) {
    val print_manager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
        ?: return
    val safe_subject = subject.ifBlank { context.getString(R.string.aster_email) }.take(80)
    val sender = "${displayed_sender_name(msg.display_sender_name, msg.sender_name)} " +
        "<${displayed_sender_email(msg.display_sender_email, msg.sender_email)}>"
    val timestamp_text = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(msg.timestamp))
    val body = msg.body_html?.takeIf { it.isNotBlank() }?.let { EmailHtmlSanitizer.sanitize(it) }
        ?: "<pre>${android.text.Html.escapeHtml(msg.body)}</pre>"
    val html = """
        <html><head><meta charset="utf-8">
        <style>
          body { font-family: -apple-system, sans-serif; color: #111; padding: 24px; }
          .meta { color: #666; font-size: 12px; margin-bottom: 16px; }
          h1 { font-size: 18px; margin: 0 0 8px 0; }
          hr { border: none; border-top: 1px solid #ddd; margin: 16px 0; }
        </style></head><body>
          <h1>${android.text.Html.escapeHtml(safe_subject)}</h1>
          <div class="meta">From: ${android.text.Html.escapeHtml(sender)}<br/>$timestamp_text</div>
          <hr/>
          $body
        </body></html>
    """.trimIndent()
    val web_view = android.webkit.WebView(context)
    web_view.settings.javaScriptEnabled = false
    web_view.settings.allowFileAccess = false
    web_view.settings.allowContentAccess = false
    @Suppress("DEPRECATION")
    web_view.settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    web_view.settings.allowUniversalAccessFromFileURLs = false
    web_view.settings.blockNetworkImage = true
    web_view.settings.blockNetworkLoads = true
    web_view.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    web_view.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView, url: String?) {
            val adapter = view.createPrintDocumentAdapter(safe_subject)
            print_manager.print(safe_subject, adapter, android.print.PrintAttributes.Builder().build())
            view.postDelayed({
                runCatching {
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view.destroy()
                }
            }, 1500)
        }
    }
    web_view.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
}

@Composable
internal fun sheet_row(
    label: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    on_click: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AsterSpacing.md))
        }
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun text_link_button(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .clip(SquircleShape(8.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = colors.accent_blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun info_banner(
    icon: ImageVector,
    label: String,
    button_label: String,
    on_action: () -> Unit,
    on_dismiss: (() -> Unit)? = null,
    secondary: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(SquircleShape(8.dp))
                .clickable(onClick = on_action)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = button_label,
                color = colors.accent_blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (on_dismiss != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(SquircleShape(8.dp))
                    .clickable(onClick = on_dismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = colors.text_muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private val safe_external_schemes = setOf("http", "https", "mailto", "tel")
private val safe_unsubscribe_schemes = setOf("https", "mailto")

private fun is_safe_external_url(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull() ?: return false
    return scheme in safe_external_schemes
}

private fun is_safe_unsubscribe_url(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull() ?: return false
    return scheme in safe_unsubscribe_schemes
}

private const val FALLBACK_MEASURE_JS =
    "(function(){var m=document.getElementById('m');if(!m)return 0;" +
        "var sy=window.pageYOffset||document.documentElement.scrollTop||0;" +
        "if(document.documentElement.getAttribute('data-nl'))return Math.ceil(m.getBoundingClientRect().bottom+sy);" +
        "var pb=parseFloat(window.getComputedStyle(document.body).paddingBottom)||0;" +
        "return Math.ceil(m.getBoundingClientRect().bottom+sy+pb)+4;})()"

private class height_channel(private val on_height: (Int, Boolean) -> Unit) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pending_height = 0
    private var pending_exact = false
    private val commit = Runnable {
        if (pending_height > 0) on_height(pending_height, pending_exact)
        pending_height = 0
        pending_exact = false
    }

    fun report(height: Int, exact: Boolean = false) {
        if (height <= 0) return
        if (exact) {
            pending_height = height
            pending_exact = true
        } else if (height > pending_height) {
            pending_height = height
        }
        handler.removeCallbacks(commit)
        handler.postDelayed(commit, if (exact) 0 else 40)
    }
}

private object body_height_cache {
    private const val max_entries = 128
    private val store = object : java.util.LinkedHashMap<Long, Float>(max_entries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Float>?): Boolean = size > max_entries
    }
    @Synchronized fun get(key: Long): Float? = store[key]
    @Synchronized fun put(key: Long, value: Float) { store[key] = value }
}

private object html_cache {
    private const val max_entries = 32
    private val store = object : java.util.LinkedHashMap<Long, String>(max_entries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean = size > max_entries
    }
    @Synchronized fun get(key: Long): String? = store[key]
    @Synchronized fun put(key: Long, value: String) { store[key] = value }
    fun height_key(html_hash: Int, allow_external: Boolean, screen_w: Int, text_zoom: Int): Long {
        var h = html_hash.toLong() and 0xFFFFFFFFL
        h = h * 31L + (if (allow_external) 1L else 0L)
        h = h * 31L + screen_w.toLong()
        h = h * 31L + text_zoom.toLong()
        return h
    }
    fun key(html_hash: Int, allow_external: Boolean, bg_hex: String, screen_w: Int = 0, force_dark: Boolean = false, translate: Boolean = false): Long {
        var h = html_hash.toLong() and 0xFFFFFFFFL
        h = h * 31L + (if (allow_external) 1L else 0L)
        h = h * 31L + bg_hex.hashCode().toLong()
        h = h * 31L + org.astermail.android.BuildConfig.VERSION_CODE.toLong()
        h = h * 31L + screen_w.toLong()
        h = h * 31L + (if (force_dark) 1L else 0L)
        h = h * 31L + (if (translate) 1L else 0L)
        return h
    }
}

@Volatile
private var email_image_client_instance: okhttp3.OkHttpClient? = null

internal const val EMAIL_FONT_PATH = "/__aster_font/opendyslexic.otf"

internal const val EMAIL_USER_FONT_PREFIX = "/__aster_email_font/"

private fun is_zoomable_image_src(src: String): Boolean {
    if (src.isBlank() || src.length > 8192) return false
    if (src.startsWith("data:image/", ignoreCase = true)) return true
    if (src.startsWith(INLINE_IMAGE_URL_PREFIX)) return true
    return try {
        val parsed = android.net.Uri.parse(src)
        parsed.scheme.equals("https", ignoreCase = true) && parsed.host == "app.astermail.org"
    } catch (_: Throwable) {
        false
    }
}

private fun email_user_font_response(
    context: android.content.Context,
    path: String,
): android.webkit.WebResourceResponse? = try {
    val name = path.removePrefix(EMAIL_USER_FONT_PREFIX).removeSuffix(".ttf")
    val parts = name.split("__")
    val resource = if (parts.size == 2) {
        org.astermail.android.design.email_font_resource_for(parts[0], parts[1])
    } else {
        null
    }
    if (resource == null) {
        null
    } else {
        android.webkit.WebResourceResponse(
            "font/ttf",
            null,
            200,
            "OK",
            mapOf("Cache-Control" to "max-age=86400"),
            context.resources.openRawResource(resource),
        )
    }
} catch (_: Throwable) {
    null
}

private fun email_font_response(context: android.content.Context): android.webkit.WebResourceResponse? = try {
    android.webkit.WebResourceResponse(
        "font/otf",
        null,
        200,
        "OK",
        mapOf("Cache-Control" to "max-age=86400"),
        context.resources.openRawResource(R.font.opendyslexic_regular),
    )
} catch (_: Throwable) {
    null
}

private fun email_image_client(context: android.content.Context): okhttp3.OkHttpClient {
    email_image_client_instance?.let { return it }
    return synchronized(mail_detail_image_lock) {
        email_image_client_instance ?: okhttp3.OkHttpClient.Builder()
            .dns(org.astermail.android.api.DualStackDns)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(false)
            .cache(
                okhttp3.Cache(
                    java.io.File(context.applicationContext.cacheDir, "email_img_cache"),
                    64L * 1024 * 1024,
                ),
            )
            .addNetworkInterceptor { chain ->
                val resp = chain.proceed(chain.request())
                if (resp.isSuccessful) {
                    resp.newBuilder()
                        .header("Cache-Control", "max-age=86400")
                        .removeHeader("Pragma")
                        .build()
                } else {
                    resp
                }
            }
            .build()
            .apply {
                dispatcher.maxRequests = 64
                dispatcher.maxRequestsPerHost = 16
            }
            .also { email_image_client_instance = it }
    }
}

private val mail_detail_image_lock = Any()

private sealed interface TranslationBannerState {
    object Hidden : TranslationBannerState
    data class Offer(val language: String) : TranslationBannerState
    object Translating : TranslationBannerState
    data class Translated(val language: String) : TranslationBannerState
    object Failed : TranslationBannerState
}

@Composable
private fun translation_banner(
    state: TranslationBannerState,
    on_translate: (String) -> Unit,
    on_show_original: () -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    if (state is TranslationBannerState.Hidden) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = AsterSpacing.sm)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.accent_blue.copy(alpha = 0.10f))
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is TranslationBannerState.Offer -> {
                val name = org.astermail.android.translation.language_display_name(state.language)
                Text(
                    text = stringResource(R.string.translation_offer_title, name),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.translation_offer_action),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { on_translate(state.language) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = stringResource(R.string.translation_offer_dismiss),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { on_dismiss() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            is TranslationBannerState.Translating -> {
                CircularProgressIndicator(
                    color = colors.accent_blue,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.translation_translating),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            is TranslationBannerState.Translated -> {
                val name = org.astermail.android.translation.language_display_name(state.language)
                Text(
                    text = stringResource(R.string.translation_translated, name),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.translation_show_original),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { on_show_original() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            is TranslationBannerState.Failed -> {
                Text(
                    text = stringResource(R.string.translation_failed),
                    color = colors.text_secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = null,
                    tint = colors.text_tertiary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { on_dismiss() },
                )
            }
            else -> Unit
        }
    }
}

@Composable
internal fun email_html_view(
    html: String,
    modifier: Modifier = Modifier,
    allow_external: Boolean = false,
    inline_images: Map<String, String> = emptyMap(),
    access_token: String? = null,
    force_light: Boolean = false,
    on_ready: () -> Unit = {},
    on_link_click: (String) -> Unit = {},
    on_image_click: (String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val is_dark = !force_light && colors.bg_primary.luminance() < colors.text_primary.luminance()
    val bg_hex = if (force_light) "#FFFFFF" else String.format(java.util.Locale.US, "#%06X", colors.bg_primary.toArgb() and 0xFFFFFF)
    val fg_hex = if (force_light) "#111827" else String.format(java.util.Locale.US, "#%06X", colors.text_primary.toArgb() and 0xFFFFFF)
    val link_hex = String.format(java.util.Locale.US, "#%06X", colors.accent_blue.toArgb() and 0xFFFFFF)

    val screen_width_dp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp

    val settings_vm: SettingsViewModel = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val text_zoom = when (settings_state.preferences?.font_size_scale) {
        "small" -> 85
        "large" -> 120
        "extra_large" -> 140
        else -> 100
    }
    val forwarded_label = stringResource(R.string.forwarded_message_label)
    val image_blocked_label = stringResource(R.string.image_blocked_placeholder)
    val image_failed_label = stringResource(R.string.image_failed_placeholder)
    val force_dark_emails = is_dark && settings_state.preferences?.force_dark_emails == true
    val tracking_protection_on = settings_state.preferences?.block_external_content != false
    val sanitize_options = EmailHtmlSanitizer.SanitizeOptions(
        clean_tracking_links = tracking_protection_on && settings_state.preferences?.block_tracking_links != false,
        remove_tracking_pixels = tracking_protection_on && settings_state.preferences?.block_tracking_pixels != false,
        block_remote_fonts = settings_state.preferences?.block_remote_fonts != false,
        block_remote_css = settings_state.preferences?.block_remote_css != false,
    )
    val dyslexia_font = settings_state.preferences?.dyslexia_font == true
    val email_font_id = org.astermail.android.design.resolve_email_font_id(
        settings_state.preferences?.email_font_choice,
        settings_state.preferences?.font_choice,
    )

    val translate_mode = if (org.astermail.android.translation.translation_supported) {
        settings_state.preferences?.translate_incoming ?: "off"
    } else {
        "off"
    }
    val translate_langs = settings_state.preferences?.translate_languages ?: emptyList()
    val translate_never_langs = settings_state.preferences?.translate_never_languages ?: emptyList()
    val ui_language = org.astermail.android.translation.normalize_language_code(
        java.util.Locale.getDefault().language,
    )
    val translate_target = org.astermail.android.translation.normalize_language_code(
        translate_langs.firstOrNull(),
    ) ?: ui_language ?: "en"
    val translate_accepted = remember(translate_langs, translate_never_langs, ui_language) {
        (translate_langs + translate_never_langs + listOfNotNull(ui_language))
            .mapNotNull { org.astermail.android.translation.normalize_language_code(it) }
            .distinct()
            .joinToString(",")
    }
    val web_ref = remember { arrayOfNulls<android.webkit.WebView>(1) }
    var translation_state by remember(html) {
        mutableStateOf<TranslationBannerState>(TranslationBannerState.Hidden)
    }
    val main_handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    fun run_translation(from: String) {
        val web = web_ref[0] ?: return
        translation_state = TranslationBannerState.Translating
        web.evaluateJavascript(
            "window.__aster_translate&&window.__aster_translate.run('$from','$translate_target')",
            null,
        )
    }

    fun show_original() {
        val web = web_ref[0] ?: return
        web.evaluateJavascript(
            "window.__aster_translate&&window.__aster_translate.show_original()",
            null,
        )
    }

    val translate_bridge = remember(translate_mode, translate_target) {
        object {
            @android.webkit.JavascriptInterface
            fun on_detect(json: String) {
                val language = Regex("\"language\"\\s*:\\s*\"([a-z]{2})\"").find(json)?.groupValues?.get(1)
                val detected = json.contains("\"detected\":true") || json.contains("\"detected\": true")
                if (!detected || language == null) return
                if (language == translate_target) return
                main_handler.post {
                    if (translate_mode == "always") {
                        run_translation(language)
                    } else if (translate_mode == "ask") {
                        translation_state = TranslationBannerState.Offer(language)
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun on_status(json: String) {
                val state = Regex("\"state\"\\s*:\\s*\"([a-z_]+)\"").find(json)?.groupValues?.get(1)
                val from = Regex("\"from\"\\s*:\\s*\"([a-z]{2})\"").find(json)?.groupValues?.get(1)
                main_handler.post {
                    when (state) {
                        "translating" -> translation_state = TranslationBannerState.Translating
                        "translated" -> translation_state =
                            TranslationBannerState.Translated(from ?: "")
                        "original" -> translation_state = TranslationBannerState.Hidden
                        "error" -> translation_state = TranslationBannerState.Failed
                        else -> Unit
                    }
                }
            }
        }
    }

    val inline_sig = remember(inline_images) { inline_images.keys.sorted().joinToString(",").hashCode() }
    val html_hash = remember(html, inline_sig) { html.hashCode() * 31 + inline_sig }
    val height_cache_key = remember(html_hash, allow_external, screen_width_dp, text_zoom, dyslexia_font, email_font_id) {
        (html_cache.height_key(html_hash, allow_external, screen_width_dp, text_zoom) * 31L + (if (dyslexia_font) 1L else 0L)) *
            31L + email_font_id.hashCode().toLong()
    }
    val cached_height = remember(height_cache_key) { body_height_cache.get(height_cache_key) }
    var content_height_dp by remember(height_cache_key) { mutableStateOf((cached_height ?: 0f).dp) }
    var has_measured by remember(height_cache_key) { mutableStateOf(cached_height != null) }
    val page_painted = remember { mutableStateOf(cached_height != null) }

    LaunchedEffect(height_cache_key, page_painted.value) {
        if (page_painted.value) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        page_painted.value = true
    }

    val measure_js = """(function(){
        var el=document.getElementById('m');
        if(el) console.log('ASTER_HEIGHT:'+Math.ceil(el.getBoundingClientRect().bottom+(window.pageYOffset||0)));
    })()"""

    fun build_html(body: String): String = build_email_html(
        body = body,
        is_dark = is_dark,
        fg_hex = fg_hex,
        link_hex = link_hex,
        forwarded_label = forwarded_label,
        image_failed_label = image_failed_label,
        force_dark_emails = force_dark_emails,
        dyslexia_font = dyslexia_font,
        translate_mode = translate_mode,
        email_font_id = email_font_id,
        text_zoom = text_zoom,
    )

    val translate_active = translate_mode != "off"
    val translate_active_ref = remember { booleanArrayOf(false) }
    translate_active_ref[0] = translate_active
    val cache_key = remember(html_hash, allow_external, bg_hex, screen_width_dp, force_dark_emails, translate_active, dyslexia_font, email_font_id, text_zoom, sanitize_options) { (((html_cache.key(html_hash, allow_external, bg_hex, screen_width_dp, force_dark_emails, translate_active) * 31L + (if (dyslexia_font) 1L else 0L)) * 31L + email_font_id.hashCode().toLong()) * 31L + text_zoom.toLong()) * 31L + sanitize_options.hashCode().toLong() }
    var prebuilt_html by remember(html_hash, allow_external, translate_active, dyslexia_font, email_font_id, text_zoom, sanitize_options) { mutableStateOf<String?>(html_cache.get(cache_key)) }
    var loaded_built by remember { mutableStateOf("") }
    var loaded_external by remember { mutableStateOf(false) }
    val scale_ref = remember { floatArrayOf(1f) }
    val nl_scale_ref = remember { floatArrayOf(1f) }
    val measured_dp_ref = remember { floatArrayOf(0f) }
    val measured_scale_ref = remember { floatArrayOf(1f) }
    val zoom_scale_ref = remember { floatArrayOf(1f) }
    val zoom_base_ref = remember { floatArrayOf(0f) }
    val zoom_last_ref = remember { floatArrayOf(0f) }
    val is_nl_ref = remember { booleanArrayOf(false) }
    val white_page_ref = remember { booleanArrayOf(false) }
    var long_pressed_link by remember { mutableStateOf<String?>(null) }

    val on_height_report by rememberUpdatedState<(Int, Boolean) -> Unit> { h, exact ->
        if (h > 0) {
            val visual_h = (h * scale_ref[0]).toInt()
            val new_dp = visual_h.dp
            val natural_scale = if (is_nl_ref[0]) nl_scale_ref[0] else 1f
            val is_natural = kotlin.math.abs(zoom_scale_ref[0] - natural_scale) < 0.01f
            if (has_measured && !is_natural) return@rememberUpdatedState
            if (!has_measured) {
                content_height_dp = new_dp
                measured_dp_ref[0] = new_dp.value
                measured_scale_ref[0] = scale_ref[0]
                if (zoom_last_ref[0] > 0f) zoom_base_ref[0] = zoom_last_ref[0]
                has_measured = true
                if (is_natural) body_height_cache.put(height_cache_key, new_dp.value)
                on_ready()
            } else if (exact || new_dp > content_height_dp) {
                val delta = kotlin.math.abs((new_dp - content_height_dp).value)
                if (delta >= 8f) {
                    content_height_dp = new_dp
                    measured_dp_ref[0] = new_dp.value
                    measured_scale_ref[0] = scale_ref[0]
                    if (zoom_last_ref[0] > 0f) zoom_base_ref[0] = zoom_last_ref[0]
                }
            }
        }
    }

    val height_sink = remember { height_channel { h, exact -> on_height_report(h, exact) } }

    LaunchedEffect(height_cache_key) {
        if (has_measured) on_ready()
    }

    val proxy_base = "https://app.astermail.org/api/images/v1/proxy?url="

    fun proxy_html(raw: String): String {
        val cid_normalized = resolve_inline_cids(raw, inline_images)
        if (!allow_external) {
            val imgs_blocked = EmailHtmlSanitizer.replace_blocked_images(cid_normalized, image_blocked_label)
            return EmailHtmlSanitizer.neutralize_blocked_backgrounds(imgs_blocked)
        }
        return proxy_external_urls(cid_normalized, proxy_base)
    }

    LaunchedEffect(html, inline_sig, allow_external, bg_hex, force_dark_emails, translate_active, dyslexia_font, email_font_id, text_zoom, sanitize_options) {
        scale_ref[0] = 1f
        zoom_scale_ref[0] = 1f
        measured_dp_ref[0] = 0f
        measured_scale_ref[0] = 1f
        zoom_base_ref[0] = 0f
        zoom_last_ref[0] = 0f
        val cached = html_cache.get(cache_key)
        if (cached != null) {
            prebuilt_html = cached
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.Default) {
            val sanitized = EmailHtmlSanitizer.sanitize(html, sanitize_options)
            build_html(proxy_html(sanitized))
        }
        html_cache.put(cache_key, result)
        prebuilt_html = result
    }

    LaunchedEffect(html, allow_external) {
        delay(600)
        if (!has_measured) {
            web_ref[0]?.evaluateJavascript(FALLBACK_MEASURE_JS) { result ->
                if (!has_measured) {
                    val parsed = result?.trim()?.removeSurrounding("\"")?.toIntOrNull() ?: 0
                    if (parsed > 0) content_height_dp = (parsed * scale_ref[0]).toInt().dp
                    has_measured = true
                    on_ready()
                }
            }
        }
        delay(400)
        if (!has_measured) {
            has_measured = true
            on_ready()
        }
    }

    LaunchedEffect(has_measured, translate_mode, translate_accepted, prebuilt_html) {
        if (!has_measured || translate_mode == "off") return@LaunchedEffect
        delay(150)
        val web = web_ref[0] ?: return@LaunchedEffect
        val accepted = translate_accepted
        val boot = "(function(){try{if(window.__aster_translate){window.__aster_translate.detect('$accepted');return;}" +
            "import('/bergamot/aster_translate.js').then(function(){window.__aster_translate&&window.__aster_translate.detect('$accepted');}).catch(function(e){});" +
            "}catch(e){}})()"
        web.evaluateJavascript(boot, null)
    }

    val webview_client = remember {
        object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                val scheme = request.url?.scheme?.lowercase() ?: return true
                when (scheme) {
                    "asterimg" -> {
                        val raw = url.substringAfter("asterimg:", "")
                        val decoded = try {
                            java.net.URLDecoder.decode(raw, "UTF-8")
                        } catch (_: Throwable) { "" }
                        if (is_zoomable_image_src(decoded)) on_image_click(decoded)
                        return true
                    }
                    "http", "https", "mailto", "tel", "sms", "aster" -> {
                        on_link_click(url)
                        return true
                    }
                    "about" -> return false
                    else -> return true
                }
            }

            override fun onScaleChanged(view: android.webkit.WebView?, oldScale: Float, newScale: Float) {
                if (newScale <= 0f) return
                zoom_last_ref[0] = newScale
                if (zoom_base_ref[0] <= 0f) zoom_base_ref[0] = newScale
                val ratio = newScale / zoom_base_ref[0]
                scale_ref[0] = if (is_nl_ref[0]) nl_scale_ref[0] else ratio
                zoom_scale_ref[0] = if (is_nl_ref[0]) nl_scale_ref[0] * ratio else ratio
                if (!has_measured) return
                val base_dp = measured_dp_ref[0]
                if (base_dp <= 0f) return
                if (kotlin.math.abs(ratio - 1f) < 0.01f) {
                    content_height_dp = base_dp.dp
                    return
                }
                content_height_dp = (base_dp * ratio).coerceIn(1f, 24000f).dp
            }

            override fun onPageCommitVisible(view: android.webkit.WebView?, url: String?) {
                if (url == "about:blank") return
                view?.setBackgroundColor(
                    if (white_page_ref[0]) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT,
                )
                page_painted.value = true
            }

            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                val bg_detect_js = """(function(){
  var m=document.getElementById('m');if(!m)return;
  if(!m.style.backgroundColor||m.style.backgroundColor==='transparent'||m.style.backgroundColor==='rgba(0, 0, 0, 0)'){
    var first=m.firstElementChild;
    if(first){
      var bg=first.getAttribute('bgcolor')||first.style.backgroundColor;
      if(!bg||bg==='transparent'||bg==='rgba(0, 0, 0, 0)'){
        var tag=first.tagName;
        if(tag==='TABLE'||tag==='DIV'||tag==='CENTER'){bg=window.getComputedStyle(first).backgroundColor}
      }
      if(bg&&bg!=='transparent'&&bg!=='rgba(0, 0, 0, 0)'){
        m.style.backgroundColor=bg;
      }
    }
  }
  var nl=document.documentElement.getAttribute('data-nl');
  var cur=window.getComputedStyle(m).backgroundColor;
  if(nl&&(!cur||cur==='transparent'||cur==='rgba(0, 0, 0, 0)')){
    m.style.backgroundColor='#ffffff';
  }
  var fin=window.getComputedStyle(m).backgroundColor;
  var fm=/rgba?\(([^)]+)\)/.exec(fin||'');
  if(fm){
    var fp=fm[1].split(',');
    var fa=fp.length>3?parseFloat(fp[3]):1;
    var fr=parseInt(fp[0],10),fg=parseInt(fp[1],10),fb=parseInt(fp[2],10);
    if(!isNaN(fr)&&!isNaN(fg)&&!isNaN(fb)&&fa>0.5){
      var fl=(0.2126*fr+0.7152*fg+0.0722*fb)/255;
      if(fl>0.55){
        document.documentElement.removeAttribute('data-dark');
        document.documentElement.setAttribute('data-white','1');
        document.body.style.setProperty('color','#111827','important');
        m.style.setProperty('color','#111827','important');
      }
    }
  }
})()"""
                val fit_and_measure_js = """(function(){window.__aster_collapse_images&&window.__aster_collapse_images();window.__aster_pad&&window.__aster_pad();window.__aster_relax&&window.__aster_relax();window.__aster_contrast&&window.__aster_contrast();window.__aster_fit&&window.__aster_fit();})()"""
                val email_prefs = settings_vm.state.value.preferences
                if (email_prefs?.underline_links == true) {
                    view?.evaluateJavascript("""(function(){var s=document.createElement('style');s.textContent='a{text-decoration:underline!important}';document.head.appendChild(s);})()""", null)
                }
                view?.evaluateJavascript(bg_detect_js, null)
                view?.setBackgroundColor(
                    if (white_page_ref[0]) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT,
                )
                view?.evaluateJavascript(fit_and_measure_js, null)
                page_painted.value = true
                view?.postDelayed({ view.evaluateJavascript(fit_and_measure_js, null) }, 300)
                view?.postDelayed({ view.evaluateJavascript(fit_and_measure_js, null) }, 1000)
            }

            override fun shouldInterceptRequest(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                val req_uri = request?.url ?: return null
                val url = req_uri.toString()
                if (req_uri.host == org.astermail.android.translation.TranslationAssets.CONTENT_HOST) {
                    if (InlineImageStore.key_for_path(req_uri.path) != null) {
                        return inline_image_response(req_uri.path)
                    }
                    val user_font_path = req_uri.path
                    if (user_font_path != null && user_font_path.startsWith(EMAIL_USER_FONT_PREFIX)) {
                        val user_font_ctx = view?.context
                        if (user_font_ctx != null) {
                            val user_font_response = email_user_font_response(user_font_ctx, user_font_path)
                            if (user_font_response != null) return user_font_response
                        }
                        return null
                    }
                    if (req_uri.path == EMAIL_FONT_PATH) {
                        val font_ctx = view?.context
                        if (font_ctx != null) {
                            val font_response = email_font_response(font_ctx)
                            if (font_response != null) return font_response
                        }
                        return null
                    }
                    val ctx0 = view?.context
                    if (ctx0 != null) {
                        val served = org.astermail.android.translation.TranslationAssets.serve(
                            ctx0,
                            req_uri.host,
                            req_uri.path,
                            translate_active_ref[0],
                        )
                        if (served != null) return served
                    }
                    return null
                }
                if (req_uri.scheme != "https") return null
                if (req_uri.host != "app.astermail.org") return null
                if (req_uri.path != "/api/images/v1/proxy") return null
                if (req_uri.getQueryParameter("url").isNullOrBlank()) return null
                fun image_response(
                    content_type: String,
                    stream: java.io.InputStream,
                ): android.webkit.WebResourceResponse =
                    android.webkit.WebResourceResponse(
                        content_type,
                        null,
                        200,
                        "OK",
                        mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
                        stream,
                    )
                fun transparent_pixel(): android.webkit.WebResourceResponse {
                    val gif = android.util.Base64.decode(
                        "R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==",
                        android.util.Base64.DEFAULT,
                    )
                    return image_response("image/gif", java.io.ByteArrayInputStream(gif))
                }
                val current_token = settings_vm.get_access_token()
                if (current_token.isNullOrBlank()) return transparent_pixel()
                val ctx = view?.context ?: return transparent_pixel()
                val client = email_image_client(ctx)
                return try {
                    fun fetch(bearer: String): okhttp3.Response {
                        val req = okhttp3.Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $bearer")
                            .build()
                        return client.newCall(req).execute()
                    }
                    var resp = fetch(current_token)
                    if (resp.code == 401) {
                        resp.close()
                        val refreshed = settings_vm.refresh_access_token_blocking()
                        if (refreshed.isNullOrBlank()) return transparent_pixel()
                        resp = fetch(refreshed)
                    }
                    if (!resp.isSuccessful) {
                        resp.close()
                        return transparent_pixel()
                    }
                    val body = resp.body
                    if (body == null) { resp.close(); return transparent_pixel() }
                    val content_type = resp.header("Content-Type")?.substringBefore(';')?.trim()
                        ?.takeIf { it.isNotBlank() } ?: "image/jpeg"
                    val stream = object : java.io.FilterInputStream(body.byteStream()) {
                        override fun close() {
                            try { super.close() } finally { resp.close() }
                        }
                    }
                    image_response(content_type, stream)
                } catch (_: Throwable) { transparent_pixel() }
            }
        }
    }

    Column(modifier = modifier) {
      translation_banner(
        state = translation_state,
        on_translate = { lang -> run_translation(lang) },
        on_show_original = { show_original() },
        on_dismiss = { translation_state = TranslationBannerState.Hidden },
      )
      val body_reveal by animateFloatAsState(
          targetValue = if (has_measured && page_painted.value) 1f else 0f,
          animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
          label = "web_reveal",
      )
      Box(
          modifier = Modifier
              .fillMaxWidth()
              .clipToBounds(),
          contentAlignment = Alignment.Center,
      ) {
        if (white_page_ref[0] && body_reveal > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(body_reveal)
                    .background(androidx.compose.ui.graphics.Color.White),
            )
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.textZoom = text_zoom
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.setSupportZoom(true)
                    settings.domStorageEnabled = false
                    settings.loadsImagesAutomatically = true
                    settings.blockNetworkImage = !allow_external
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setGeolocationEnabled(false)
                    @Suppress("DEPRECATION")
                    settings.saveFormData = false
                    @Suppress("DEPRECATION")
                    settings.savePassword = false
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = true
                    overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isNestedScrollingEnabled = false
                    var touch_down_x = 0f
                    var touch_down_y = 0f
                    setOnTouchListener { v, ev ->
                        when (ev.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                touch_down_x = ev.x
                                touch_down_y = ev.y
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (ev.pointerCount > 1) {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                } else {
                                    val dx = Math.abs(ev.x - touch_down_x)
                                    val dy = Math.abs(ev.y - touch_down_y)
                                    val can_scroll_horizontally =
                                        v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)
                                    if (can_scroll_horizontally && dx > dy && dx > 8f) {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    } else {
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                            }
                        }
                        false
                    }
                    setOnLongClickListener {
                        val hit = hitTestResult
                        fun is_web_link(href: String?): Boolean =
                            href != null && (href.startsWith("http://") || href.startsWith("https://"))
                        when (hit.type) {
                            android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                                val href = hit.extra
                                if (is_web_link(href)) {
                                    long_pressed_link = href
                                    true
                                } else {
                                    false
                                }
                            }
                            android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                val handler = android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                                    val href = msg.data.getString("url")
                                    if (is_web_link(href)) long_pressed_link = href
                                    true
                                }
                                requestFocusNodeHref(handler.obtainMessage())
                                true
                            }
                            else -> false
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                            val msg = message?.message() ?: return false
                            if (msg.startsWith("ASTER_HEIGHT_EXACT:")) {
                                val parsed = msg.substring("ASTER_HEIGHT_EXACT:".length).toIntOrNull()
                                if (parsed != null) height_sink.report(parsed, exact = true)
                                return true
                            }
                            if (msg.startsWith("ASTER_HEIGHT:")) {
                                val parsed = msg.substring("ASTER_HEIGHT:".length).toIntOrNull()
                                if (parsed != null) height_sink.report(parsed)
                                return true
                            }
                            return false
                        }
                    }
                    webViewClient = webview_client
                    addJavascriptInterface(translate_bridge, "AsterTranslateBridge")
                    web_ref[0] = this
                }
            },
            update = { web_view ->
                web_ref[0] = web_view
                val built = prebuilt_html ?: return@AndroidView
                val is_newsletter = built.contains("data-nl=\"1\"")
                val wants_white_page = built.contains("data-white=\"1\"")
                web_view.settings.textZoom = text_zoom
                web_view.settings.loadsImagesAutomatically = true
                web_view.settings.blockNetworkImage = !allow_external
                web_view.settings.mixedContentMode =
                    android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (loaded_built != built || loaded_external != allow_external) {
                    if (loaded_built != built && body_height_cache.get(height_cache_key) == null) has_measured = false
                    if (body_height_cache.get(height_cache_key) == null) page_painted.value = false
                    web_view.setBackgroundColor(
                        if (wants_white_page) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT,
                    )
                    loaded_built = built
                    loaded_external = allow_external
                    is_nl_ref[0] = is_newsletter
                    white_page_ref[0] = wants_white_page
                    nl_scale_ref[0] = if (is_newsletter) {
                        Regex("initial-scale=([0-9.]+)").find(built)
                            ?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0.1f, 1.0f) ?: 1f
                    } else {
                        1f
                    }
                    scale_ref[0] = nl_scale_ref[0]
                    zoom_scale_ref[0] = nl_scale_ref[0]
                    measured_scale_ref[0] = nl_scale_ref[0]
                    if (has_measured && measured_dp_ref[0] <= 0f) measured_dp_ref[0] = content_height_dp.value
                    web_view.loadDataWithBaseURL("https://mail-content.invalid/", built, "text/html", "UTF-8", null)
                    if (!has_measured) web_view.evaluateJavascript(measure_js, null)
                }
            },
            modifier = run {
                val target = if (has_measured && content_height_dp > 0.dp) content_height_dp else placeholder_body_height
                Modifier
                    .fillMaxWidth()
                    .height(target)
                    .clipToBounds()
                    .alpha(body_reveal)
            },
            onRelease = { web_view ->
                runCatching {
                    web_view.stopLoading()
                    web_view.loadUrl("about:blank")
                    web_view.removeAllViews()
                    web_view.destroy()
                }
            },
        )
        if (!has_measured || !page_painted.value) {
            email_body_skeleton(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.bg_primary)
                    .clipToBounds()
                    .align(Alignment.TopStart),
            )
        }
      }
    }

    long_pressed_link?.let { link ->
        link_options_sheet(url = link, on_close = { long_pressed_link = null })
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun link_options_sheet(
    url: String,
    on_close: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val copied_label = stringResource(R.string.link_copied)
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
                .navigationBarsPadding(),
        ) {
            Text(
                text = url,
                color = colors.text_secondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = AsterSpacing.xl, vertical = AsterSpacing.xs),
            )
            AsterDivider()
            sheet_row(stringResource(R.string.copy_link), colors.text_primary) {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
                Toast.makeText(context, copied_label, Toast.LENGTH_SHORT).show()
                on_close()
            }
            sheet_row(stringResource(R.string.share_link), colors.text_primary) {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                }
                context.startActivity(android.content.Intent.createChooser(send, null))
                on_close()
            }
            Spacer(Modifier.height(AsterSpacing.sm))
        }
    }
}

@Composable
private fun attachment_section(
    attachments: List<MessageAttachment>,
    on_tap: (MessageAttachment) -> Unit,
    on_download: (MessageAttachment) -> Unit,
) {
    val colors = AsterMaterial.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md),
    ) {
        Spacer(Modifier.height(AsterSpacing.sm))
        AsterDivider()
        Spacer(Modifier.height(AsterSpacing.md))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = TablerIcons.Paperclip,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.attachments_count, attachments.size),
                color = colors.text_secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(AsterSpacing.sm))

        val chunked = attachments.chunked(2)
        chunked.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { att ->
                    attachment_chip(
                        attachment = att,
                        on_tap = { on_tap(att) },
                        on_download = { on_download(att) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun attachment_chip(
    attachment: MessageAttachment,
    on_tap: () -> Unit,
    on_download: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val type_color = attachment_type_color(attachment.content_type)

    Row(
        modifier = modifier
            .clip(SquircleShape(18.dp))
            .border(1.dp, colors.border_secondary, SquircleShape(18.dp))
            .background(colors.bg_secondary)
            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = on_tap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = attachment_icon(attachment.content_type),
                contentDescription = null,
                tint = type_color,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = attachment.filename,
                    color = colors.text_primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (attachment.size_bytes > 0) {
                    val size_ctx = LocalContext.current
                    Text(
                        text = android.text.format.Formatter.formatShortFileSize(size_ctx, attachment.size_bytes),
                        color = colors.text_muted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(SquircleShape(8.dp))
                .background(colors.bg_tertiary)
                .clickable(onClick = on_download),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.Download,
                contentDescription = stringResource(R.string.download),
                tint = colors.text_primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun attachment_icon(content_type: String): ImageVector {
    return when {
        content_type.startsWith("image/") -> TablerIcons.Photo
        content_type.startsWith("video/") -> TablerIcons.Video
        content_type.startsWith("audio/") -> TablerIcons.Music
        content_type.contains("pdf") -> TablerIcons.FileText
        content_type.contains("zip") || content_type.contains("gzip") ||
            content_type.contains("tar") || content_type.contains("rar") ||
            content_type.contains("7z") -> TablerIcons.FileZip
        content_type.contains("html") || content_type.contains("xml") ||
            content_type.contains("json") || content_type.contains("javascript") -> TablerIcons.Code
        content_type.startsWith("text/") || content_type.contains("document") ||
            content_type.contains("msword") || content_type.contains("spreadsheet") ||
            content_type.contains("presentation") -> TablerIcons.FileText
        else -> TablerIcons.File
    }
}

private fun attachment_type_color(content_type: String): Color {
    return when {
        content_type == "application/pdf" -> Color(0xFFEA4335)
        content_type.startsWith("image/") -> Color(0xFFA855F7)
        content_type.startsWith("video/") -> Color(0xFFEC4899)
        content_type.startsWith("audio/") -> Color(0xFF0EA5E9)
        content_type.contains("spreadsheet") || content_type.contains("excel") ||
            content_type == "text/csv" -> Color(0xFF34A853)
        content_type.contains("presentation") || content_type.contains("powerpoint") -> Color(0xFFF97316)
        content_type.contains("word") || content_type.contains("document") -> Color(0xFF4285F4)
        content_type.contains("zip") || content_type.contains("gzip") ||
            content_type.contains("tar") -> Color(0xFF8B5CF6)
        content_type.startsWith("text/") -> Color(0xFF3B82F6)
        else -> Color(0xFF6B7280)
    }
}

private fun attachment_type_label(content_type: String, filename: String): String {
    val known = mapOf(
        "application/pdf" to "PDF",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "DOCX",
        "application/msword" to "DOC",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "XLSX",
        "application/vnd.ms-excel" to "XLS",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "PPTX",
        "application/vnd.ms-powerpoint" to "PPT",
        "application/json" to "JSON",
        "application/xml" to "XML",
    )
    known[content_type]?.let { return it }
    if (content_type.startsWith("text/")) return "TXT"
    if (content_type.contains("zip") || content_type.contains("compressed")) return "ZIP"
    val ext = filename.substringAfterLast('.', "")
    return if (ext.isNotBlank()) ext.uppercase() else "FILE"
}

internal fun format_file_size(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

private fun sanitize_filename(raw: String): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\')
    val cleaned = base.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").trim().trimStart('.')
    return cleaned.ifBlank { "attachment" }.take(200)
}

private fun safe_view_mime(filename: String, declared: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    val resolved = if (ext.isNotBlank()) {
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    } else {
        null
    }
    val mime = resolved ?: declared.ifBlank { "application/octet-stream" }
    val blocked = setOf(
        "application/vnd.android.package-archive",
        "application/x-msdownload",
        "application/x-executable",
        "application/x-elf",
        "application/x-sh",
    )
    return if (mime.lowercase() in blocked) "application/octet-stream" else mime
}

private fun save_attachment_to_storage(
    context: android.content.Context,
    attachment: MessageAttachment,
    bytes: ByteArray,
): Boolean {
    return try {
        val safe_name = sanitize_filename(attachment.filename)
        val mime = safe_view_mime(safe_name, attachment.content_type)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe_name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                show_download_notification(context, safe_name, uri, mime)
                true
            } else {
                false
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = java.io.File(dir, safe_name)
            if (!file.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator)) {
                return false
            }
            file.writeBytes(bytes)
            val uri = android.net.Uri.fromFile(file)
            show_download_notification(context, safe_name, uri, mime)
            true
        }
    } catch (_: Throwable) {
        false
    }
}

private fun show_download_notification(
    context: android.content.Context,
    filename: String,
    uri: android.net.Uri,
    mime: String,
) {
    try {
        val channel_id = "downloads"
        val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channel_id,
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        val open_intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = android.app.PendingIntent.getActivity(
            context, filename.hashCode(), open_intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = androidx.core.app.NotificationCompat.Builder(context, channel_id)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.download_complete))
            .setContentText(filename)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(filename.hashCode(), notification)
    } catch (_: Throwable) {
    }
}

@Composable
private fun attachment_preview_dialog(
    attachment: MessageAttachment,
    bytes: ByteArray,
    on_close: () -> Unit,
    on_download: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val ct = attachment.content_type.lowercase()
    val context = LocalContext.current

    BackHandler { on_close() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .systemBarsPadding()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterIconButton(
                    icon = TablerIcons.X,
                    content_description = stringResource(R.string.close),
                    onClick = on_close,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = attachment.filename,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                AsterIconButton(
                    icon = TablerIcons.Download,
                    content_description = stringResource(R.string.download),
                    onClick = on_download,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                AsterIconButton(
                    icon = TablerIcons.ExternalLink,
                    content_description = stringResource(R.string.open_with),
                    onClick = {
                        try {
                            val mime = safe_view_mime(attachment.filename, attachment.content_type)
                            val values = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, sanitize_filename(attachment.filename))
                                put(MediaStore.Downloads.MIME_TYPE, mime)
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                context.contentResolver.openOutputStream(uri)?.use {
                                    it.write(bytes)
                                    it.flush()
                                }
                                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                                context.contentResolver.update(uri, done, null, null)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        } catch (_: Throwable) {
                            Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
                        }
                    },
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    ct.startsWith("image/") -> {
                        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, bytes) {
                            value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                runCatching {
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                    val max_dim = 2048
                                    var sample = 1
                                    while (bounds.outWidth / sample > max_dim || bounds.outHeight / sample > max_dim) {
                                        sample *= 2
                                    }
                                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                }.getOrNull()
                            }
                        }
                        val bmp = bitmap
                        if (bmp != null) {
                            androidx.compose.runtime.DisposableEffect(bmp) {
                                onDispose { runCatching { bmp.recycle() } }
                            }
                            var scale by remember { mutableStateOf(1f) }
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = attachment.filename,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .graphicsLayer(scaleX = scale, scaleY = scale)
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(0.5f, 5f)
                                        }
                                    },
                            )
                        } else {
                            Text(stringResource(R.string.cannot_decode_image), color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    ct.startsWith("text/") -> {
                        val text = remember(bytes) { String(bytes, Charsets.UTF_8) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .clip(SquircleShape(8.dp))
                                .background(colors.bg_primary)
                                .padding(12.dp),
                        ) {
                            val scroll = rememberScrollState()
                            Text(
                                text = text,
                                color = colors.text_primary,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(scroll),
                            )
                        }
                    }
                    else -> {
                        val type_color = attachment_type_color(ct)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = attachment_icon(ct),
                                contentDescription = null,
                                tint = type_color,
                                modifier = Modifier.size(72.dp),
                            )
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = attachment.filename,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${attachment_type_label(ct, attachment.filename)} - ${format_file_size(bytes.size.toLong())}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.height(32.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(SquircleShape(18.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable(onClick = on_download)
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Text(stringResource(R.string.download), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(SquircleShape(18.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            try {
                                                val mime = attachment.content_type.ifBlank { "application/octet-stream" }
                                                val values = ContentValues().apply {
                                                    put(MediaStore.Downloads.DISPLAY_NAME, sanitize_filename(attachment.filename))
                                                    put(MediaStore.Downloads.MIME_TYPE, mime)
                                                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                                    put(MediaStore.Downloads.IS_PENDING, 1)
                                                }
                                                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                                if (uri != null) {
                                                    context.contentResolver.openOutputStream(uri)?.use {
                                                        it.write(bytes)
                                                        it.flush()
                                                    }
                                                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                                                    context.contentResolver.update(uri, done, null, null)
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, mime)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            } catch (_: Throwable) {
                                                Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Text(stringResource(R.string.open_with), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.bottom_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    test_tag: String? = null,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(SquircleShape(18.dp))
            .clickable(onClick = onClick)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.text_primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun detail_menu_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    test_tag: String? = null,
    onClick: () -> Unit,
) {
    aster_dropdown_item(
        label = text,
        icon = icon,
        tint = tint,
        test_tag = test_tag,
        on_click = onClick,
    )
}

@Composable
private fun encryption_badge(size: androidx.compose.ui.unit.Dp) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accent_blue),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TablerIcons.Lock,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun phishing_banner(result: org.astermail.android.security.PhishingResult) {
    val colors = AsterMaterial.colors
    val is_dangerous = result.level == org.astermail.android.security.PhishingLevel.dangerous
    val bg = if (is_dangerous) colors.danger else colors.warning.copy(alpha = 0.14f)
    val tint = if (is_dangerous) Color.White else colors.warning
    val text_color = if (is_dangerous) Color.White else colors.text_primary
    val sub_text_color = if (is_dangerous) Color.White.copy(alpha = 0.85f) else colors.text_secondary
    val title = if (is_dangerous) stringResource(R.string.phishing_dangerous_title) else stringResource(R.string.phishing_warning_title)
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(SquircleShape(18.dp))
            .background(bg)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = TablerIcons.AlertCircle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                color = text_color,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (expanded) R.string.phishing_hide_details else R.string.phishing_show_details),
                color = text_color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.phishing_privacy_note),
                color = sub_text_color,
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.phishing_signals_heading),
                color = text_color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            for (signal in result.signals) {
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(text = "•", color = text_color, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(signal.description_res, *signal.description_args.toTypedArray()),
                        color = sub_text_color,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun detail_menu_divider() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(1.dp)
            .background(colors.border_secondary.copy(alpha = 0.5f)),
    )
}

@Composable
internal fun encryption_badge_label(is_encrypted: Boolean): String =
    if (is_encrypted)
        stringResource(R.string.end_to_end_encrypted)
    else
        stringResource(R.string.protected_in_transit)

internal fun thread_is_end_to_end_encrypted(messages: List<ThreadMessage>): Boolean =
    messages.isNotEmpty() && messages.all { it.is_e2e_encrypted }

@Composable
internal fun encryption_info_body(is_encrypted: Boolean) {
    val colors = AsterMaterial.colors
    Text(
        text = if (is_encrypted)
            stringResource(R.string.e2e_recipient_description)
        else
            stringResource(R.string.transit_recipient_description),
        color = colors.text_secondary,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun auth_result_label(result: String?): String = when (result?.lowercase()) {
    "pass" -> stringResource(R.string.auth_result_pass)
    "fail", "softfail", "permerror", "temperror" -> stringResource(R.string.auth_result_fail)
    else -> stringResource(R.string.auth_result_missing)
}

@Composable
private fun identity_changed_banner(sender: String, on_acknowledge: () -> Unit) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(shape)
            .background(colors.danger.copy(alpha = 0.10f))
            .border(1.dp, colors.danger.copy(alpha = 0.35f), shape)
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.ShieldLock,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column {
            Text(
                text = stringResource(R.string.identity_changed_title),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.identity_changed_description, sender),
                color = colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.identity_changed_dismiss),
                color = colors.danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .clickable { on_acknowledge() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun sender_unverified_banner(sender: String) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md)
            .padding(bottom = AsterSpacing.sm)
            .clip(shape)
            .background(colors.danger.copy(alpha = 0.10f))
            .border(1.dp, colors.danger.copy(alpha = 0.35f), shape)
            .padding(AsterSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Column {
            Text(
                text = stringResource(R.string.sender_unverified_title),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.sender_unverified_description, sender),
                color = colors.text_secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}
