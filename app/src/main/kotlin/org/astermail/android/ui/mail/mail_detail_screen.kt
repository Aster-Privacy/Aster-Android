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

import org.astermail.android.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Unsubscribe
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.looks_encrypted
import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterDragHandle
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.mail.MailViewModel
import org.astermail.android.settings.SettingsViewModel

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

private data class ExternalContentCounts(
    val image_count: Int,
    val tracker_count: Int,
    val font_count: Int,
    val css_count: Int,
) {
    val total: Int get() = image_count + tracker_count + font_count + css_count
}

private fun count_external_content(html: String): ExternalContentCounts {
    var images = 0
    var trackers = 0
    IMG_TAG_PATTERN.findAll(html).forEach { match ->
        val tag = match.value
        val width_match = Regex("""width\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(tag)
        val height_match = Regex("""height\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(tag)
        val w = width_match?.groupValues?.get(1)?.toIntOrNull()
        val h = height_match?.groupValues?.get(1)?.toIntOrNull()
        if (w != null && h != null && w <= 2 && h <= 2) trackers++ else images++
    }
    val fonts = FONT_FACE_PATTERN.findAll(html).count()
    val css = LINK_STYLESHEET_PATTERN.findAll(html).count()
    return ExternalContentCounts(images, trackers, fonts, css)
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
    """(url\(\s*["']?)(https?://[^"')]+)(["']?\s*\))""",
    RegexOption.IGNORE_CASE,
)

private val CID_SRC_PATTERN = Regex(
    """(src\s*=\s*["'])cid:([^"']+)(["'])""",
    RegexOption.IGNORE_CASE,
)

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
    settings_vm: SettingsViewModel = hiltViewModel(),
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

    LaunchedEffect(email_id) {
        mail_vm.load_thread(email_id)
        val delay_ms = when (settings_state.preferences?.mark_as_read) {
            "immediate" -> 0L
            "3_seconds" -> 3000L
            "never" -> return@LaunchedEffect
            else -> 1000L
        }
        if (delay_ms > 0) kotlinx.coroutines.delay(delay_ms)
        mail_vm.mark_read(email_id)
    }

    LaunchedEffect(Unit) {
        settings_vm.load_preferences()
        settings_vm.load_tags()
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

    val thread_ghost_email = remember(thread_state.messages) {
        val latest_sent = thread_state.messages
            .filter { it.raw_item.item_type == "sent" }
            .maxByOrNull { it.timestamp }
        val sender = latest_sent?.sender_email?.lowercase().orEmpty()
        if (sender.isNotBlank() && GHOST_LOCAL_PATTERN.containsMatchIn(sender)) sender else null
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
    var show_action_sheet by remember { mutableStateOf(false) }
    var show_topbar_menu by remember { mutableStateOf(false) }
    var show_raw_source_dialog by remember { mutableStateOf(false) }
    var show_encryption_info by remember { mutableStateOf(false) }
    var show_snooze_sheet by remember { mutableStateOf(false) }
    var show_folder_sheet by remember { mutableStateOf(false) }
    var show_label_sheet by remember { mutableStateOf(false) }
    var action_target_id by remember { mutableStateOf<String?>(null) }
    var toast_message by remember { mutableStateOf<String?>(null) }
    var top_toast_state by remember { mutableStateOf<org.astermail.android.ui.common.TopToastState?>(null) }

    var body_ready by remember(email_id) { mutableStateOf(false) }
    var show_encryption_dropdown by remember { mutableStateOf(false) }
    var hidden_group_revealed by remember(email_id) { mutableStateOf(false) }
    var allow_external_ids by remember(email_id) { mutableStateOf(emptySet<String>()) }
    var dismissed_unsub_ids by remember(email_id) { mutableStateOf(emptySet<String>()) }
    var pending_link by remember { mutableStateOf<String?>(null) }
    var preview_attachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var preview_bytes by remember { mutableStateOf<ByteArray?>(null) }
    var is_downloading_attachment by remember { mutableStateOf(false) }

    val messages = remember(email_id, api_messages) { api_messages.distinctBy { it.id } }
    val is_thread_encrypted = remember(messages) { messages.any { it.is_encrypted } }
    val thread_trackers_blocked = remember(messages) { messages.sumOf { it.trackers_blocked } }

    val visible_tail_count = 2
    val hidden_count = remember(messages.size, hidden_group_revealed) {
        if (hidden_group_revealed || messages.size <= visible_tail_count + 2) 0
        else messages.size - 1 - visible_tail_count
    }

    val expanded_ids = remember(email_id) {
        mutableStateOf(emptySet<String>())
    }
    val collapsed_ids = remember(email_id) {
        mutableStateOf(emptySet<String>())
    }
    LaunchedEffect(email_id, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (expanded_ids.value.isNotEmpty()) return@LaunchedEffect
        val initial = mutableSetOf(messages.last().id)
        if (messages.size <= 4) {
            messages.filter { !it.is_read }.takeLast(5).forEach { initial.add(it.id) }
        }
        expanded_ids.value = initial.toSet()
    }

    fun show_toast(msg: String) {
        toast_message = msg
    }

    val list_state = rememberLazyListState()
    val show_topbar_subject by remember {
        derivedStateOf { list_state.firstVisibleItemIndex > 0 || list_state.firstVisibleItemScrollOffset > 80 }
    }

    LaunchedEffect(mail_vm) {
        mail_vm.toast_events.collect { evt ->
            if (evt.on_undo != null) {
                top_toast_state = org.astermail.android.ui.common.TopToastState(
                    message = evt.message,
                    undo_label = evt.undo_label,
                    on_undo = evt.on_undo,
                    duration_ms = evt.duration_ms,
                    on_timeout = evt.on_timeout,
                )
            } else {
                toast_message = evt.message
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg_primary).systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.bg_primary)
                    .padding(horizontal = AsterSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsterIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    content_description = stringResource(R.string.back),
                    onClick = on_back,
                    modifier = Modifier.testTag("back"),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                        val label = if (is_thread_encrypted)
                            stringResource(R.string.end_to_end_encrypted)
                        else
                            stringResource(R.string.standard)
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
                                imageVector = if (is_thread_encrypted) Icons.Filled.Lock else Icons.Outlined.LockOpen,
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
                Box {
                    AsterIconButton(
                        icon = Icons.Filled.MoreVert,
                        content_description = stringResource(R.string.more),
                        onClick = { show_topbar_menu = true },
                        modifier = Modifier.testTag("more"),
                    )
                    DropdownMenu(
                        expanded = show_topbar_menu,
                        onDismissRequest = { show_topbar_menu = false },
                        shadowElevation = 16.dp,
                        offset = DpOffset(0.dp, 8.dp),
                        modifier = Modifier
                            .clip(SquircleShape(18.dp))
                            .background(colors.dropdown_bg)
                            .border(1.dp, colors.border_secondary, SquircleShape(18.dp))
                            .width(260.dp)
                            .padding(6.dp),
                    ) {
                        val is_pinned = api_item?.raw_item?.metadata?.is_pinned == true
                        detail_menu_action(
                            icon = if (is_starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            text = if (is_starred) stringResource(R.string.unstar) else stringResource(R.string.star),
                            tint = if (is_starred) colors.accent_blue else colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            star_override = !is_starred
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            mail_vm.toggle_star(email_id)
                            show_toast(if (!is_starred) context.getString(R.string.starred) else context.getString(R.string.unstarred))
                        }
                        detail_menu_action(
                            icon = if (is_archived) Icons.Outlined.MoveToInbox else Icons.Outlined.Archive,
                            text = if (is_archived) stringResource(R.string.swipe_move_to_inbox) else stringResource(R.string.archive_action),
                            tint = colors.text_primary,
                            test_tag = "archive",
                        ) {
                            show_topbar_menu = false
                            if (is_archived) {
                                mail_vm.unarchive(listOf(email_id))
                                on_archive()
                                show_toast(context.getString(R.string.moved_to_inbox))
                            } else {
                                mail_vm.archive(listOf(email_id))
                                on_archive()
                                show_toast(context.getString(R.string.swipe_archive))
                            }
                        }
                        detail_menu_action(
                            icon = Icons.Outlined.MarkEmailUnread,
                            text = stringResource(R.string.mark_as_unread),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            mail_vm.mark_unread(email_id)
                            show_toast(context.getString(R.string.marked_as_unread))
                            on_back()
                        }
                        detail_menu_action(
                            icon = Icons.Outlined.PushPin,
                            text = if (is_pinned) stringResource(R.string.unpin) else stringResource(R.string.pin_to_top),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            mail_vm.toggle_pin(email_id)
                        }
                        detail_menu_action(
                            icon = Icons.Outlined.Bedtime,
                            text = stringResource(R.string.snooze),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            show_snooze_sheet = true
                        }
                        detail_menu_divider()
                        detail_menu_action(
                            icon = Icons.Outlined.FolderOpen,
                            text = stringResource(R.string.move_to_folder),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            settings_vm.load_labels()
                            show_folder_sheet = true
                        }
                        detail_menu_action(
                            icon = Icons.AutoMirrored.Outlined.Label,
                            text = stringResource(R.string.add_label),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            settings_vm.load_tags()
                            show_label_sheet = true
                        }
                        detail_menu_action(
                            icon = Icons.Outlined.Print,
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
                            icon = Icons.Outlined.Code,
                            text = stringResource(R.string.detail_view_raw_source),
                            tint = colors.text_primary,
                        ) {
                            show_topbar_menu = false
                            show_raw_source_dialog = true
                        }
                        detail_menu_divider()
                        detail_menu_action(
                            icon = Icons.Outlined.Report,
                            text = if (is_spam) stringResource(R.string.swipe_not_spam) else stringResource(R.string.report_spam),
                            tint = if (is_spam) colors.accent_blue else colors.danger,
                        ) {
                            show_topbar_menu = false
                            if (is_spam) {
                                is_spam_override = false
                                mail_vm.unmark_spam(listOf(email_id))
                                show_toast(context.getString(R.string.swipe_not_spam))
                            } else {
                                is_spam_override = true
                                mail_vm.mark_spam(listOf(email_id))
                                show_toast(context.getString(R.string.reported_as_spam))
                            }
                            on_back()
                        }
                        detail_menu_action(
                            icon = Icons.Outlined.Block,
                            text = stringResource(R.string.block_sender),
                            tint = colors.danger,
                        ) {
                            show_topbar_menu = false
                            val sender = messages.lastOrNull()?.sender_email
                            if (!sender.isNullOrBlank()) {
                                settings_vm.block_sender(sender)
                                show_toast(context.getString(R.string.sender_blocked))
                                on_back()
                            }
                        }
                        detail_menu_action(
                            icon = Icons.Outlined.Delete,
                            text = stringResource(R.string.delete_action),
                            tint = colors.danger,
                        ) {
                            show_topbar_menu = false
                            mail_vm.trash(listOf(email_id))
                            on_delete()
                            show_toast(context.getString(R.string.swipe_delete))
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

            Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = list_state,
                modifier = Modifier
                    .fillMaxSize()
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.lg)
                            .padding(top = AsterSpacing.sm, bottom = AsterSpacing.sm),
                    ) {
                        Text(
                            text = subject_text,
                            color = colors.text_primary,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                item {
                    val applied_tag_tokens = api_item?.raw_item?.tag_tokens ?: emptyList()
                    val settings_state_now by settings_vm.state.collectAsStateWithLifecycle()
                    val applied_tags = remember(applied_tag_tokens, settings_state_now.tags) {
                        applied_tag_tokens.mapNotNull { token ->
                            settings_state_now.tags.find { it.tag_token == token }
                        }
                    }
                    if (applied_tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = AsterSpacing.lg)
                                .padding(bottom = AsterSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            applied_tags.filter { it.encrypted_name.isNotBlank() }.forEach { tag ->
                                val tag_color = try {
                                    tag.encrypted_color?.let { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) }
                                } catch (_: Throwable) { null } ?: colors.accent_blue
                                Row(
                                    modifier = Modifier
                                        .clip(SquircleShape(8.dp))
                                        .background(tag_color.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(tag_color, shape = androidx.compose.foundation.shape.CircleShape),
                                    )
                                    Text(
                                        text = tag.encrypted_name,
                                        color = colors.text_primary,
                                        fontSize = 12.sp,
                                    )
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
                        expanded_ids.value.contains(msg.id) ||
                        (is_last_message && !collapsed_ids.value.contains(msg.id))

                    val is_hidden = hidden_count > 0 && idx >= 1 && idx < 1 + hidden_count
                    val is_after_indicator = hidden_count > 0 && idx == 1 + hidden_count

                    if (is_hidden) {
                        if (idx == 1) {
                            hidden_group_indicator(
                                count = hidden_count,
                                on_reveal = { hidden_group_revealed = true },
                            )
                        }
                        return@items
                    }

                    val is_system_sender = msg.sender_email.endsWith("@astermail.org") ||
                        msg.sender_email.endsWith("@aster.cx")

                    if (is_expanded) {
                        expanded_message(
                            msg = msg,
                            is_last = is_last,
                            message_index = idx,
                            my_email = my_email,
                            my_profile_pic = my_profile_pic,
                            show_top_divider = !is_after_indicator,
                            allow_external = !block_external_images || msg.id in allow_external_ids || is_system_sender,
                            blocked_for_traffic = blocked_for_traffic_only,
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
                            on_link_click = { url -> pending_link = url },
                            on_unsubscribe = { url ->
                                dismissed_unsub_ids = dismissed_unsub_ids + msg.id
                                scope.launch {
                                    if (!is_safe_unsubscribe_url(url)) {
                                        toast_message = context.getString(R.string.could_not_unsubscribe)
                                        return@launch
                                    }
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                        )
                                        toast_message = context.getString(R.string.opening_unsubscribe)
                                    } catch (_: Throwable) {
                                        toast_message = context.getString(R.string.could_not_unsubscribe)
                                    }
                                }
                            },
                            on_collapse = {
                                expanded_ids.value = expanded_ids.value.toMutableSet().apply { remove(msg.id) }
                                collapsed_ids.value = collapsed_ids.value + msg.id
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
                                        is_downloading_attachment = false
                                    }.onFailure {
                                        toast_message = context.getString(R.string.failed_to_load_preview)
                                        is_downloading_attachment = false
                                    }
                                }
                            },
                            on_attachment_download = { att ->
                                toast_message = context.getString(R.string.downloading_file, att.filename)
                                mail_vm.download_attachment(att) { result ->
                                    result.onSuccess { (resolved_att, bytes) ->
                                        val saved = save_attachment_to_storage(context, resolved_att, bytes)
                                        toast_message = if (saved) context.getString(R.string.saved_file, resolved_att.filename) else context.getString(R.string.failed_to_save)
                                    }.onFailure {
                                        toast_message = context.getString(R.string.failed_to_download, att.filename)
                                    }
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
                                expanded_ids.value = expanded_ids.value.toMutableSet().apply { add(msg.id) }
                                collapsed_ids.value = collapsed_ids.value - msg.id
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }

            }
        }

        AnimatedVisibility(
            visible = toast_message != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            toast_message?.let { msg ->
                Box(
                    modifier = Modifier
                        .padding(top = AsterSpacing.sm, start = AsterSpacing.lg, end = AsterSpacing.lg)
                        .clip(SquircleShape(18.dp))
                        .background(colors.bg_card)
                        .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                ) {
                    Text(
                        text = msg,
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        LaunchedEffect(toast_message) {
            if (toast_message != null) {
                delay(2500)
                toast_message = null
            }
        }

        org.astermail.android.ui.common.top_toast_overlay(
            state = top_toast_state,
            on_dismiss = { top_toast_state = null },
        )

        if (email != null && messages.isNotEmpty()) {
            val latest_msg = messages.last()
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.bg_primary)
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val read_state = api_item?.is_read == true
                    if (read_state) {
                        bottom_action(Icons.Outlined.MarkEmailUnread, stringResource(R.string.mark_as_unread), test_tag = "mark_read") {
                            mail_vm.mark_unread(email_id)
                            show_toast(context.getString(R.string.marked_as_unread))
                            on_back()
                        }
                    } else {
                        bottom_action(Icons.Outlined.MarkEmailRead, stringResource(R.string.mark_as_read), test_tag = "mark_read") {
                            mail_vm.mark_read(email_id)
                            show_toast(context.getString(R.string.mark_as_read))
                        }
                    }
                    bottom_action(Icons.Outlined.Delete, stringResource(R.string.move_to_trash), test_tag = "delete") {
                        mail_vm.trash(listOf(email_id))
                        on_delete()
                        show_toast(context.getString(R.string.move_to_trash))
                    }
                    bottom_action(Icons.Outlined.FolderOpen, stringResource(R.string.move_to_folder)) {
                        show_folder_sheet = true
                    }
                    bottom_action(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.label)) {
                        show_label_sheet = true
                    }
                    bottom_action(Icons.Filled.MoreHoriz, stringResource(R.string.more)) {
                        show_action_sheet = true
                    }
                }
            }
        }
    }

    if (show_action_sheet) {
        val target = action_target_id ?: messages.lastOrNull()?.id.orEmpty()
        action_menu_sheet(
            on_close = { show_action_sheet = false },
            on_reply = { show_action_sheet = false; on_reply(target, thread_ghost_email) },
            on_reply_all = { show_action_sheet = false; on_reply_all(target, thread_ghost_email) },
            on_forward = { show_action_sheet = false; on_forward(target, thread_ghost_email) },
            on_star = {
                show_action_sheet = false
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                mail_vm.toggle_star(email_id)
                show_toast(if (!is_starred) context.getString(R.string.starred) else context.getString(R.string.unstarred))
            },
            is_starred = is_starred,
            on_mark_unread = {
                show_action_sheet = false
                mail_vm.mark_unread(email_id)
                show_toast(context.getString(R.string.marked_as_unread))
                on_back()
            },
            on_archive = {
                show_action_sheet = false
                if (api_item?.is_archived == true) {
                    mail_vm.unarchive(listOf(email_id))
                    on_archive()
                    show_toast(context.getString(R.string.moved_to_inbox))
                } else {
                    mail_vm.archive(listOf(email_id))
                    on_archive()
                    show_toast(context.getString(R.string.archived))
                }
            },
            on_trash = {
                show_action_sheet = false
                mail_vm.trash(listOf(email_id))
                on_delete()
                show_toast(context.getString(R.string.move_to_trash))
            },
            on_spam = {
                show_action_sheet = false
                if (is_spam) {
                    is_spam_override = false
                    mail_vm.unmark_spam(listOf(email_id))
                    show_toast(context.getString(R.string.swipe_not_spam))
                } else {
                    is_spam_override = true
                    mail_vm.mark_spam(listOf(email_id))
                    val sender = messages.lastOrNull()?.sender_email
                    if (!sender.isNullOrBlank()) {
                        settings_vm.block_sender(sender)
                    }
                    show_toast(context.getString(R.string.reported_as_spam))
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
                show_toast(context.getString(R.string.customize_toolbar))
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
        org.astermail.android.design.components.AsterAlertDialog(
            on_dismiss = { pending_link = null },
            title = stringResource(R.string.open_external_link),
            message = stringResource(R.string.leaving_aster_warning),
            confirm_label = stringResource(R.string.open),
            cancel_label = stringResource(R.string.cancel),
            on_confirm = {
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
            },
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

    if (show_encryption_info) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { show_encryption_info = false },
            title = if (is_thread_encrypted)
                stringResource(R.string.end_to_end_encrypted)
            else
                stringResource(R.string.standard),
            message = if (is_thread_encrypted)
                stringResource(R.string.e2e_recipient_description)
            else
                stringResource(R.string.transit_recipient_description),
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
        val settings_state by settings_vm.state.collectAsStateWithLifecycle()
        val unnamed_folder_label = stringResource(R.string.unnamed_folder)
        val folder_decrypt_failed_label = stringResource(R.string.folder_decrypt_failed)
        val folder_items = settings_state.labels
            .filter { (it.folder_type == "folder" || it.folder_type == "custom") && !it.is_system }
            .map { label ->
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
        )
    }

    if (show_label_sheet) {
        val settings_state by settings_vm.state.collectAsStateWithLifecycle()
        val tag_items = settings_state.tags
            .filter { it.encrypted_name.isNotBlank() }
        tag_picker_sheet(
            title = stringResource(R.string.add_label),
            empty_message = stringResource(R.string.no_labels_yet_create),
            items = tag_items,
            on_close = { show_label_sheet = false },
            on_pick = { picked ->
                val display = picked.encrypted_name.takeIf { it.isNotBlank() } ?: picked.tag_token
                mail_vm.apply_tag(email_id, picked.tag_token, display)
                show_label_sheet = false
            },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun expanded_message(
    msg: ThreadMessage,
    is_last: Boolean,
    message_index: Int = 0,
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
    on_track: (String, String, String?) -> Unit = { _, _, _ -> },
    access_token: String? = null,
    on_link_click: (String) -> Unit = {},
    on_collapse: () -> Unit,
    on_reply: () -> Unit,
    on_reply_all: () -> Unit,
    on_forward: () -> Unit,
    on_more: () -> Unit,
    on_attachment_tap: (MessageAttachment) -> Unit = {},
    on_attachment_download: (MessageAttachment) -> Unit = {},
    is_system: Boolean = false,
    can_collapse: Boolean = true,
) {
    val colors = AsterMaterial.colors
    var show_details by remember { mutableStateOf(false) }
    val tracker_count = remember(msg.body_html, msg.trackers_blocked) {
        val local = if (msg.body_html != null) count_external_content(msg.body_html).tracker_count else 0
        maxOf(msg.trackers_blocked, local)
    }
    val chevron_rotation by animateFloatAsState(targetValue = if (show_details) 180f else 0f, label = "chevron")

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
            SenderAvatar(
                email = msg.sender_email,
                name = msg.sender_name,
                profile_picture_url = if (msg.sender_email.lowercase() == my_email) my_profile_pic else null,
            )
            Spacer(Modifier.width(AsterSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = msg.sender_name,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!msg.sender_name.equals(msg.sender_email, ignoreCase = true)) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = msg.sender_email,
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.to_label_prefix, msg.to_label),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = msg.timestamp.format_relative_time(stringResource(R.string.yesterday)),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { show_details = !show_details },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
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
                    AsterIconButton(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        content_description = stringResource(R.string.reply),
                        onClick = on_reply,
                    )
                    AsterIconButton(
                        icon = Icons.Filled.MoreVert,
                        content_description = stringResource(R.string.more_options),
                        onClick = on_more,
                        icon_size = 18,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = show_details,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(colors.bg_primary)) {
                info_row(
                    icon = Icons.Outlined.Lock,
                    label = stringResource(R.string.encryption),
                    value = if (msg.is_encrypted) stringResource(R.string.encrypted_e2e) else stringResource(R.string.encrypted_in_transit),
                    tint = if (msg.is_encrypted) colors.success else colors.text_secondary,
                )
                info_row(
                    icon = Icons.Outlined.Shield,
                    label = stringResource(R.string.tracker_protection),
                    value = if (tracker_count > 0) stringResource(R.string.trackers_blocked_count, tracker_count) else stringResource(R.string.no_trackers),
                    tint = if (tracker_count > 0) colors.warning else colors.success,
                    test_tag = "tracker_badge",
                )
                info_row(
                    icon = Icons.Outlined.Schedule,
                    label = stringResource(R.string.date),
                    value = msg.timestamp.format_full_datetime(),
                    tint = colors.text_secondary,
                )
            }
        }

        val unsub_info = remember(msg.body_html, msg.body) {
            detect_unsubscribe_info(
                html_content = msg.body_html,
                text_content = msg.body,
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
            msg.body_html, msg.body, msg.sender_email, is_system,
        ) {
            value = if (is_system) null else withContext(kotlinx.coroutines.Dispatchers.Default) {
                org.astermail.android.security.analyze_email(
                    html_content = msg.body_html.orEmpty(),
                    text_content = msg.body,
                    sender_name = msg.sender_name,
                    sender_email = msg.sender_email,
                    is_external = true,
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

        if (!msg.body_html.isNullOrBlank()) {
            val body_has_dark_hostile_styling = msg.body_html.contains(
                Regex("background(?:-color)?\\s*[:=]\\s*[\"']?#?(?:fff|FFF|ffffff|FFFFFF|white)", RegexOption.IGNORE_CASE),
            )
            email_html_view(
                html = msg.body_html,
                allow_external = allow_external,
                access_token = access_token,
                force_light = is_system && body_has_dark_hostile_styling,
                on_ready = on_body_ready,
                on_link_click = on_link_click,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AsterSpacing.sm)
                    .testTag("message_body"),
            )
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
                    imageVector = Icons.Outlined.Lock,
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
                val raw = msg.body.ifBlank {
                    if (msg.is_encrypted) {
                        e2e_no_key_text
                    } else {
                        no_body_text
                    }
                }
                val escaped = raw
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                val url_pattern = Regex(
                    "(https?://[^\\s<>\"']+|www\\.[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}(?:/[^\\s<>\"']*)?)",
                )
                val trailing_punct = Regex("[.,;:!?)\\]\\}\"']+\$")
                val linked = url_pattern.replace(escaped) { match ->
                    val raw = match.value
                    val trail_match = trailing_punct.find(raw)
                    val (clean, trail) = if (trail_match != null) {
                        raw.substring(0, trail_match.range.first) to raw.substring(trail_match.range.first)
                    } else raw to ""
                    val href = if (clean.startsWith("www.")) "http://$clean" else clean
                    "<a href=\"$href\">$clean</a>$trail"
                }
                val email_pattern = Regex(
                    "(?<![\\w@.-])([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})(?![\\w@.-])",
                )
                val linked_with_email = email_pattern.replace(linked) { match ->
                    val addr = match.value
                    "<a href=\"mailto:$addr\">$addr</a>"
                }
                "<div style=\"white-space:pre-wrap;overflow-wrap:break-word\">${linked_with_email.replace("\n", "<br>")}</div>"
                }
            }
            email_html_view(
                html = plain_html,
                allow_external = false,
                access_token = access_token,
                on_ready = on_body_ready,
                on_link_click = on_link_click,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AsterSpacing.sm)
                    .testTag("message_body"),
            )
        }

        val visible_attachments = remember(msg.attachments, msg.body_html) {
            val body = msg.body_html.orEmpty()
            msg.attachments.filter { att ->
                val cid = att.content_id?.trim()?.trim('<', '>').orEmpty()
                if (cid.isBlank()) return@filter true
                !body.contains("cid:$cid", ignoreCase = true)
            }
        }
        if (visible_attachments.isNotEmpty()) {
            attachment_section(
                attachments = visible_attachments,
                on_tap = on_attachment_tap,
                on_download = on_attachment_download,
            )
        }

        Spacer(Modifier.height(AsterSpacing.md))
        reply_action_row(
            on_reply = on_reply,
            on_reply_all = on_reply_all,
            on_forward = on_forward,
        )
        Spacer(Modifier.height(AsterSpacing.md))
    }
}

@Composable
private fun reply_action_row(
    on_reply: () -> Unit,
    on_reply_all: () -> Unit,
    on_forward: () -> Unit,
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
    ) {
        reply_action_button(
            icon = Icons.AutoMirrored.Filled.Reply,
            label = stringResource(R.string.reply),
            bg = colors.accent_blue,
            fg = androidx.compose.ui.graphics.Color.White,
            label_size = label_size,
            on_label_overflow = on_label_overflow,
            on_click = on_reply,
            modifier = Modifier.weight(1f),
        )
        reply_action_button(
            icon = Icons.AutoMirrored.Filled.ReplyAll,
            label = stringResource(R.string.reply_all),
            bg = colors.bg_card,
            fg = colors.text_primary,
            label_size = label_size,
            on_label_overflow = on_label_overflow,
            on_click = on_reply_all,
            modifier = Modifier.weight(1f),
        )
        reply_action_button(
            icon = Icons.AutoMirrored.Filled.Forward,
            label = stringResource(R.string.forward),
            bg = colors.bg_card,
            fg = colors.text_primary,
            label_size = label_size,
            on_label_overflow = on_label_overflow,
            on_click = on_forward,
            modifier = Modifier.weight(1f),
        )
    }
}

private val REPLY_ACTION_LABEL_MAX = 14.sp
private val REPLY_ACTION_LABEL_MIN = 9.sp

@Composable
private fun reply_action_button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    label_size: TextUnit,
    on_label_overflow: () -> Unit,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(SquircleShape(999.dp))
            .background(bg)
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
private fun compact_banner_action(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .clip(SquircleShape(999.dp))
            .then(
                if (primary) Modifier.background(colors.accent_blue)
                else Modifier.border(1.dp, colors.border_secondary, SquircleShape(999.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) Color.White else colors.accent_blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun compact_banner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    actions: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(SquircleShape(14.dp))
            .background(colors.bg_secondary)
            .padding(horizontal = AsterSpacing.md, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Text(
            text = label,
            color = colors.text_secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = if (expanded) 6 else 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = !expanded },
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        actions()
    }
}

@Composable
private fun unsubscribe_banner(
    on_unsubscribe: () -> Unit,
) {
    compact_banner(
        icon = Icons.Outlined.Unsubscribe,
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
private fun external_content_banner(
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
    compact_banner(icon = Icons.Outlined.ImageNotSupported, label = label) {
        compact_banner_action(
            label = stringResource(R.string.detail_external_allow_once),
            primary = false,
            onClick = on_allow_once,
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        compact_banner_action(
            label = stringResource(R.string.detail_external_always_allow),
            primary = true,
            onClick = on_always_allow,
        )
    }
}

@Composable
private fun traffic_saver_banner(
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
    compact_banner(icon = Icons.Outlined.ImageNotSupported, label = label) {
        compact_banner_action(
            label = stringResource(R.string.detail_external_allow_once),
            primary = false,
            onClick = on_load_once,
        )
        Spacer(Modifier.width(AsterSpacing.sm))
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
            append("X-Encrypted: ").append(if (message.is_encrypted) "end-to-end" else "in-transit").append("\n")
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
private fun info_row(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    test_tag: String? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(AsterSpacing.sm))
        Column {
            Text(text = label, color = colors.text_muted, fontSize = 11.sp)
            Text(text = value, color = colors.text_primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

    val is_undecryptable = msg.sender_email.isBlank() && msg.body.isBlank()

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
            verticalAlignment = Alignment.CenterVertically,
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
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                SenderAvatar(
                    email = msg.sender_email,
                    name = msg.sender_name,
                    size = 40.dp,
                    profile_picture_url = if (msg.sender_email.lowercase() == my_email) my_profile_pic else null,
                )
            }
            Spacer(Modifier.width(AsterSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (is_undecryptable) stringResource(R.string.encrypted) else msg.sender_name,
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (is_undecryptable) stringResource(R.string.e2e_encrypted_message)
                        else clean_preview_text(msg.preview, msg.body).ifBlank { stringResource(R.string.end_to_end_encrypted) },
                    color = colors.text_muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(AsterSpacing.xs))
            Text(
                text = msg.timestamp.format_relative_time(stringResource(R.string.yesterday)),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
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
            .padding(vertical = AsterSpacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        AsterDivider(modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .padding(start = 18.dp)
                .size(32.dp)
                .background(colors.bg_primary, CircleShape)
                .border(1.dp, colors.border_secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = colors.text_muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun action_menu_sheet(
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
                .navigationBarsPadding()
                .padding(top = AsterSpacing.xs),
        ) {
            sheet_row(stringResource(R.string.reply), colors.text_primary, on_reply)
            sheet_row(stringResource(R.string.reply_all), colors.text_primary, on_reply_all)
            sheet_row(stringResource(R.string.forward), colors.text_primary, on_forward)
            AsterDivider()
            sheet_row(if (is_starred) stringResource(R.string.unstar) else stringResource(R.string.star), colors.text_primary, on_star)
            sheet_row(stringResource(R.string.mark_as_unread), colors.text_primary, on_mark_unread)
            sheet_row(stringResource(R.string.label), colors.text_primary, on_label)
            sheet_row(stringResource(R.string.snooze), colors.text_primary, on_snooze)
            AsterDivider()
            sheet_row(stringResource(R.string.swipe_archive), colors.text_primary, on_archive)
            if (is_spam) {
                sheet_row(stringResource(R.string.swipe_not_spam), colors.accent_blue, on_spam)
            } else {
                sheet_row(stringResource(R.string.report_spam), colors.danger, on_spam)
            }
            sheet_row(stringResource(R.string.move_to_trash), colors.danger, on_trash)
            AsterDivider()
            sheet_row(stringResource(R.string.customize_toolbar), colors.text_secondary, on_customize_toolbar)
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun snooze_sheet(
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
private fun label_picker_sheet(
    title: String,
    empty_message: String,
    items: List<org.astermail.android.api.labels.LabelItem>,
    on_close: () -> Unit,
    on_pick: (org.astermail.android.api.labels.LabelItem) -> Unit,
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
                    val display = item.encrypted_name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.unnamed_folder)
                    sheet_row(display, colors.text_primary) { on_pick(item) }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun tag_picker_sheet(
    title: String,
    empty_message: String,
    items: List<org.astermail.android.api.tags.TagItem>,
    on_close: () -> Unit,
    on_pick: (org.astermail.android.api.tags.TagItem) -> Unit,
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
                        )
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

private fun snooze_options(
    later_today_label: String,
    tomorrow_morning_label: String,
    this_weekend_label: String,
    next_week_label: String,
): List<Pair<String, String>> {
    val now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
    val later_today = now.withHour(18).withMinute(0).withSecond(0).withNano(0).let {
        if (it.isBefore(now.plusHours(1))) now.plusHours(3) else it
    }
    val tomorrow = now.plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
    val this_weekend = now.with(java.time.DayOfWeek.SATURDAY).withHour(8).withMinute(0).withSecond(0).withNano(0).let {
        if (it.isBefore(now.plusHours(2)) || it.toLocalDate() == tomorrow.toLocalDate()) it.plusWeeks(1) else it
    }
    val next_week = now.plusWeeks(1).with(java.time.DayOfWeek.MONDAY).withHour(8).withMinute(0).withSecond(0).withNano(0)
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
    val sender = "${msg.sender_name} <${msg.sender_email}>"
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
private fun sheet_row(
    label: String,
    tint: Color,
    on_click: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                    imageVector = Icons.Outlined.Close,
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

private object html_cache {
    private const val max_entries = 32
    private val store = object : java.util.LinkedHashMap<Long, String>(max_entries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean = size > max_entries
    }
    @Synchronized fun get(key: Long): String? = store[key]
    @Synchronized fun put(key: Long, value: String) { store[key] = value }
    fun key(html_hash: Int, allow_external: Boolean, bg_hex: String, screen_w: Int = 0): Long {
        var h = html_hash.toLong() and 0xFFFFFFFFL
        h = h * 31L + (if (allow_external) 1L else 0L)
        h = h * 31L + bg_hex.hashCode().toLong()
        h = h * 31L + org.astermail.android.BuildConfig.VERSION_CODE.toLong()
        h = h * 31L + screen_w.toLong()
        return h
    }
}

@Volatile
private var email_image_client_instance: okhttp3.OkHttpClient? = null

private fun email_image_client(context: android.content.Context): okhttp3.OkHttpClient {
    email_image_client_instance?.let { return it }
    return synchronized(mail_detail_image_lock) {
        email_image_client_instance ?: okhttp3.OkHttpClient.Builder()
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

@Composable
private fun email_html_view(
    html: String,
    modifier: Modifier = Modifier,
    allow_external: Boolean = false,
    access_token: String? = null,
    force_light: Boolean = false,
    on_ready: () -> Unit = {},
    on_link_click: (String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val is_dark = !force_light && colors.bg_primary.luminance() < colors.text_primary.luminance()
    val bg_hex = if (force_light) "#FFFFFF" else String.format(java.util.Locale.US, "#%06X", colors.bg_primary.toArgb() and 0xFFFFFF)
    val fg_hex = if (force_light) "#111827" else String.format(java.util.Locale.US, "#%06X", colors.text_primary.toArgb() and 0xFFFFFF)
    val link_hex = String.format(java.util.Locale.US, "#%06X", colors.accent_blue.toArgb() and 0xFFFFFF)

    val screen_width_dp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp

    val settings_vm: SettingsViewModel = hiltViewModel()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val text_zoom = when (settings_state.preferences?.font_size_scale) {
        "small" -> 85
        "large" -> 120
        "extra_large" -> 140
        else -> 100
    }
    val forwarded_label = stringResource(R.string.forwarded_message_label)

    var content_height_dp by remember(html) { mutableStateOf(0.dp) }
    var has_measured by remember(html) { mutableStateOf(false) }

    val measure_js = """(function(){
        var el=document.getElementById('m');
        if(el) console.log('ASTER_HEIGHT:'+(el.offsetHeight));
    })()"""

    fun build_html(body: String): String {
        val is_html_body = body.trimStart().startsWith("<")
        val has_table = is_html_body && body.contains(Regex("<table", RegexOption.IGNORE_CASE))
        val has_newsletter_layout = has_table && (
            body.contains(Regex("style\\s*=\\s*[\"'][^\"']*width\\s*:\\s*[456789]\\d{2}px", RegexOption.IGNORE_CASE)) ||
            body.contains(Regex("<table[^>]*(?:width|bgcolor|background)\\s*=", RegexOption.IGNORE_CASE)) ||
            (body.split(Regex("<table\\b", RegexOption.IGNORE_CASE)).size - 1) > 2
        )
        val declares_light = body.contains(Regex("color-scheme\\s*:\\s*light\\s+only", RegexOption.IGNORE_CASE))
        val declares_light_bg = is_html_body && body.contains(
            Regex(
                "(?:background(?:-color)?\\s*:\\s*(?:#fff(?:fff)?|white|rgb\\(\\s*25[0-5])|bgcolor\\s*=\\s*[\"']?(?:#fff(?:fff)?|white))",
                RegexOption.IGNORE_CASE,
            ),
        )
        val simple_dark = is_dark && is_html_body && !has_newsletter_layout && !declares_light && !declares_light_bg
        val force_light = is_html_body && !simple_dark

        val sys_font = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"
        val body_style = when {
            is_html_body && !has_newsletter_layout ->
                "background-color:transparent;color:${if (simple_dark) "#e5e5e5" else "#111827"};margin:0;padding:6px 10px;font-family:$sys_font;font-size:14px;line-height:1.6;word-wrap:break-word"
            is_html_body ->
                "background-color:transparent;margin:0;padding:0"
            else ->
                "background-color:transparent;color:$fg_hex;margin:0;padding:6px 10px;font-family:$sys_font;font-size:14px;line-height:1.6;word-wrap:break-word"
        }

        val dark_css = if (simple_dark) """
html{color-scheme:dark!important}
html,body{background-color:transparent!important;color:#e5e5e5!important}
body *{color:inherit!important}
a,a *{color:#60a5fa!important}
""" else ""

        val table_css = if (has_newsletter_layout) {
            "table{border-collapse:collapse}td,th{min-width:0!important;box-sizing:border-box!important}#m td,#m th,#m p,#m h1,#m h2,#m h3,#m h4,#m h5,#m h6,#m div,#m span{white-space:normal!important;overflow-wrap:break-word!important;word-wrap:break-word!important}"
        } else {
            "table{max-width:100%!important;border-collapse:collapse;width:100%!important}td,th{overflow-wrap:break-word}"
        }
        val email_natural_w = if (has_newsletter_layout && screen_width_dp > 0) {
            val body_no_styles = body.replace(
                Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), ""
            )
            val found = mutableListOf<Int>()
            Regex("""(?:min-width|width)\s*:\s*(\d{3,4})px""", RegexOption.IGNORE_CASE)
                .findAll(body_no_styles).mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 400..900 }.forEach { found += it }
            Regex("""<(?:table|td|th|center|div)[^>]+\bwidth=["']?(\d{3,4})["']?""", RegexOption.IGNORE_CASE)
                .findAll(body_no_styles).mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 400..900 }.forEach { found += it }
            found.maxOrNull() ?: 600
        } else 0
        val viewport_meta = if (has_newsletter_layout && email_natural_w > 0) {
            val initial_scale = (screen_width_dp.toFloat() / email_natural_w).coerceAtMost(1.0f)
            "<meta name=\"viewport\" content=\"width=$email_natural_w,initial-scale=$initial_scale,maximum-scale=5,user-scalable=yes\">"
        } else {
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5\">"
        }
        val color_scheme_meta = if (force_light) "<meta name=\"color-scheme\" content=\"light only\">" else ""

        val bq_border = if (simple_dark) "#4b5563" else "#dadce0"
        val bq_color = if (simple_dark) "#9ca3af" else "#5f6368"
        val bq_border2 = if (simple_dark) "#444" else "#dadce0"
        val bq_border3 = if (simple_dark) "#555" else "#c4c7cc"
        val detail_border = if (simple_dark) "#374151" else "#e5e7eb"
        val detail_color = if (simple_dark) "#9ca3af" else "#6b7280"

        val csp_nonce = run {
            val nonce_bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(nonce_bytes)
            android.util.Base64.encodeToString(nonce_bytes, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        }
        val csp_meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src https://app.astermail.org data:; style-src 'unsafe-inline'; font-src https://app.astermail.org data:; script-src 'nonce-$csp_nonce'; base-uri 'none'; form-action 'none'; frame-src 'none'; object-src 'none'\">"

        return """<!DOCTYPE html><html${if (has_newsletter_layout) " data-nl=\"1\"" else ""}${if (is_html_body && !simple_dark) " style=\"background-color:transparent\"" else ""}><head>
$csp_meta
<meta charset="utf-8">
$viewport_meta
$color_scheme_meta
<style>
html{height:auto!important;min-height:0!important;background-color:transparent}
body{height:auto!important;min-height:0!important;margin:0;overflow-x:hidden;overflow-y:hidden}
*{box-sizing:border-box}
img{max-width:100%!important;height:auto!important}
a{color:$link_hex;text-decoration:underline}
pre,code{overflow-x:auto;max-width:100%}
$table_css
.aster_quote,.gmail_quote,.protonmail_quote,.yahoo_quoted,.moz-cite-prefix{display:none}
.aster-quoted-content .aster_quote,.aster-quoted-content .gmail_quote,.aster-quoted-content .protonmail_quote,.aster-quoted-content .yahoo_quoted,.aster-quoted-content .moz-cite-prefix,.aster-forwarded-content .aster_quote,.aster-forwarded-content .gmail_quote,.aster-forwarded-content .protonmail_quote{display:block;margin:0;padding:0}
blockquote{margin:8px 0;padding-left:12px;border-left:2px solid $bq_border;color:$bq_color}
.aster-quoted-wrapper{margin-top:8px}
.aster-quote-toggle{display:inline-flex;align-items:center;justify-content:center;height:16px;padding:0 4px;border-radius:3px;border:1px solid rgba(128,128,128,0.25);background:rgba(128,128,128,0.08);color:rgba(100,100,100,0.7);cursor:pointer;font-size:8px;letter-spacing:1.5px;line-height:1;vertical-align:middle;user-select:none;-webkit-tap-highlight-color:transparent}
.aster-quote-toggle:active,.aster-quote-toggle.aster-quote-expanded{background:rgba(128,128,128,0.2);border-color:rgba(128,128,128,0.45)}
.aster-quoted-content{margin-top:8px;color:$bq_color;font-size:14px;line-height:20px}
.aster-quoted-content .aster_quote_attr,.aster-quoted-content .gmail_attr{color:$bq_color;font-size:12px;margin-bottom:4px}
.aster-quoted-content blockquote{margin:0;padding:0 0 0 12px;border-left:2px solid $bq_border2;color:$bq_color}
.aster-quoted-content blockquote blockquote{border-left-color:$bq_border3}
details.aster-forwarded-collapse{margin-top:12px;border-top:1px solid $detail_border;padding-top:4px}
details.aster-forwarded-collapse>summary{cursor:pointer;color:$detail_color;font-size:13px;padding:6px 0;user-select:none;list-style:none}
details.aster-forwarded-collapse>summary::-webkit-details-marker{display:none}
details.aster-forwarded-collapse>summary::before{content:'\25B6';display:inline-block;font-size:8px;margin-right:6px;transition:transform 0.15s ease}
details[open].aster-forwarded-collapse>summary::before{transform:rotate(90deg)}
details.aster-forwarded-collapse>.aster-forwarded-content{padding-top:8px}
$dark_css
</style>
</head><body style="$body_style"><div id="m">$body</div>
<script nonce="$csp_nonce">
(function(){
  var body=document.body;
  if(!body)return;
  var is_nl=document.documentElement.getAttribute('data-nl');
  if(is_nl){
    var m_nr=document.getElementById('m');
    if(m_nr){
      var nw_els=m_nr.querySelectorAll('td,th,p,h1,h2,h3,h4,h5,h6,div,span');
      for(var k=0;k<nw_els.length;k++){
        var cs_nw=window.getComputedStyle(nw_els[k]);
        if(cs_nw.whiteSpace==='nowrap'||cs_nw.whiteSpace==='pre'){
          nw_els[k].style.whiteSpace='normal';
          nw_els[k].style.overflowWrap='break-word';
        }
      }
    }
  }
  function measure_h(){
    var m=document.getElementById('m');
    var base=m?m.offsetHeight:0;
    if(document.documentElement.getAttribute('data-nl'))return base;
    var de=document.documentElement;var b=document.body;var h=base;
    if(de)h=Math.max(h,de.scrollHeight,de.offsetHeight);
    if(b)h=Math.max(h,b.scrollHeight,b.offsetHeight);
    return h+4;
  }
  function report_h(){if(document.getElementById('m'))console.log('ASTER_HEIGHT:'+measure_h())}
  function report_h_exact(){if(document.getElementById('m'))console.log('ASTER_HEIGHT_EXACT:'+measure_h())}
  function linkify_text_nodes(root){
    var url_re=/((?:https?:\/\/|www\.)[^\s<>"']+[^\s<>"'.,;:!?)\]}])|([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/g;
    var skip_tags={A:1,SCRIPT:1,STYLE:1,TEXTAREA:1,CODE:1,PRE:1,BUTTON:1};
    var to_process=[];
    var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){
      var p=n.parentNode;while(p&&p!==root){if(p.nodeType===1&&skip_tags[p.tagName])return NodeFilter.FILTER_REJECT;p=p.parentNode}
      return n.nodeValue&&url_re.test(n.nodeValue)?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;
    }});
    while(w.nextNode())to_process.push(w.currentNode);
    to_process.forEach(function(n){
      var s=n.nodeValue;url_re.lastIndex=0;
      var frag=document.createDocumentFragment();var last=0;var m;
      while((m=url_re.exec(s))!==null){
        if(m.index>last)frag.appendChild(document.createTextNode(s.substring(last,m.index)));
        var a=document.createElement('a');
        if(m[1]){var href=m[1];if(/^www\./i.test(href))href='http://'+href;a.href=href;a.textContent=m[1];}
        else{a.href='mailto:'+m[2];a.textContent=m[2];}
        frag.appendChild(a);last=m.index+m[0].length;
      }
      if(last<s.length)frag.appendChild(document.createTextNode(s.substring(last)));
      n.parentNode.replaceChild(frag,n);
    });
  }
  try{linkify_text_nodes(body)}catch(_){}
  var aster_fit_scale=1.0;
  function aster_fit(){
    var m=document.getElementById('m');
    if(!m)return;
    if(document.documentElement.getAttribute('data-nl')){
      aster_fit_scale=1.0;
      console.log('ASTER_HEIGHT_EXACT:'+(Math.round(m.offsetHeight)));
      return;
    }
    var vw=window.innerWidth;
    if(vw<=100)return;
    var meta=document.querySelector('meta[name=viewport]');
    if(!meta)return;
    var max=m.scrollWidth||0;
    try{
      var els=m.querySelectorAll('*');
      for(var i=0;i<els.length;i++){
        var r=els[i].getBoundingClientRect();
        if(r.right>max)max=r.right;
      }
    }catch(_){}
    var widest2=Math.ceil(max);
    var scale2=1.0;
    if(widest2>vw+2){
      scale2=Math.max(0.25,vw/widest2);
      var content='width='+widest2+',initial-scale='+scale2+',maximum-scale=5,user-scalable=yes';
      if(meta.getAttribute('content')!==content){
        meta.setAttribute('content',content);
        document.documentElement.style.overflowX='auto';
        document.body.style.overflowX='auto';
      }
    }
    aster_fit_scale=scale2;
    console.log('ASTER_HEIGHT_EXACT:'+(Math.round(measure_h()*scale2)));
  }
  window.__aster_fit=aster_fit;
  try{
    var all_imgs=body.querySelectorAll('img');
    for(var ii=0;ii<all_imgs.length;ii++){
      (function(im){
        if(im.complete)return;
        im.addEventListener('load',function(){aster_fit()});
        im.addEventListener('error',function(){aster_fit()});
      })(all_imgs[ii]);
    }
  }catch(_){}
  try{
    var mo=new MutationObserver(function(){aster_fit()});
    mo.observe(body,{childList:true,subtree:true,attributes:true,attributeFilter:['src','style','width']});
    setTimeout(function(){try{mo.disconnect()}catch(_){}},5000);
  }catch(_){}
  setTimeout(aster_fit,0);
  setTimeout(aster_fit,300);
  setTimeout(aster_fit,1000);
  setTimeout(aster_fit,2500);
  function make_toggle(content_el){
    var wrapper=document.createElement('div');wrapper.className='aster-quoted-wrapper';
    var btn=document.createElement('button');btn.className='aster-quote-toggle';btn.type='button';btn.textContent='•••';
    var cdiv=document.createElement('div');cdiv.className='aster-quoted-content';cdiv.style.display='none';
    content_el.parentNode.insertBefore(wrapper,content_el);cdiv.appendChild(content_el);
    btn.addEventListener('click',function(){var h=cdiv.style.display==='none';cdiv.style.display=h?'':'none';btn.classList.toggle('aster-quote-expanded',h);report_h_exact()});
    wrapper.appendChild(btn);wrapper.appendChild(cdiv);
  }
  var proton=body.querySelector('div.protonmail_quote');
  if(proton){
    var cbq=proton.querySelector(':scope > blockquote');
    if(cbq){
      var meta=[];var prev=proton.previousSibling;
      while(prev){var pel=prev.nodeType===1?prev:null;var ptxt=(prev.textContent||'').trim();var is_sig=pel&&pel.classList&&pel.classList.contains('protonmail_signature_block');if(is_sig||!ptxt){meta.unshift(prev);prev=prev.previousSibling}else break}
      var par=proton.parentNode;while(cbq.firstChild)par.insertBefore(cbq.firstChild,proton);
      meta.push(proton);
      var det=document.createElement('details');det.className='aster-forwarded-collapse';
      var sum=document.createElement('summary');sum.textContent=${org.json.JSONObject.quote(forwarded_label)};det.appendChild(sum);
      var cdiv2=document.createElement('div');cdiv2.className='aster-forwarded-content';
      meta.forEach(function(n){cdiv2.appendChild(n)});det.appendChild(cdiv2);body.appendChild(det);
    }
  }
  if(!body.querySelector('details.aster-forwarded-collapse')){
    var gq=body.querySelector('div.aster_quote,div.gmail_quote');
    if(gq)make_toggle(gq);
  }
  if(!body.querySelector('.aster-quoted-wrapper')&&!body.querySelector('details.aster-forwarded-collapse')){
    var wrote_re=/(^|[\s> ])(On\s[^\n]{1,200}?\bwrote\s*:)/i;
    var walker=document.createTreeWalker(body,NodeFilter.SHOW_TEXT);
    var marker=null;
    while(walker.nextNode()){
      var nd=walker.currentNode;var txt=nd.nodeValue||'';var mm=wrote_re.exec(txt);
      if(mm){
        var on_idx=mm.index+(mm[1]?mm[1].length:0);
        marker=(on_idx>0)?nd.splitText(on_idx):nd;
        break;
      }
    }
    if(marker){
      var to_col=[];
      var cur=marker;
      while(cur){var nx=cur.nextSibling;to_col.push(cur);cur=nx}
      var anc=marker.parentNode;
      while(anc&&anc!==body){
        var ns=anc.nextSibling;
        while(ns){var nxn=ns.nextSibling;to_col.push(ns);ns=nxn}
        anc=anc.parentNode;
      }
      if(to_col.length>0){
        var w2=document.createElement('div');w2.className='aster-quoted-wrapper';
        var b2=document.createElement('button');b2.className='aster-quote-toggle';b2.type='button';b2.textContent='•••';
        var c2=document.createElement('div');c2.className='aster-quoted-content';c2.style.display='none';
        to_col.forEach(function(node){c2.appendChild(node)});
        b2.addEventListener('click',function(){var h=c2.style.display==='none';c2.style.display=h?'':'none';b2.classList.toggle('aster-quote-expanded',h);report_h_exact()});
        w2.appendChild(b2);w2.appendChild(c2);(document.getElementById('m')||body).appendChild(w2);
      }
    }
  }
  report_h_exact();
})();
</script></body></html>"""
    }

    val is_html_body_top = html.trimStart().startsWith("<")
    val bg_color = if (!is_html_body_top) android.graphics.Color.TRANSPARENT
        else runCatching { android.graphics.Color.parseColor(bg_hex) }.getOrDefault(android.graphics.Color.BLACK)

    val cache_key = remember(html, allow_external, bg_hex, screen_width_dp) { html_cache.key(html.hashCode(), allow_external, bg_hex, screen_width_dp) }
    var prebuilt_html by remember(html, allow_external) { mutableStateOf<String?>(html_cache.get(cache_key)) }
    var loaded_html by remember { mutableStateOf("") }
    var loaded_external by remember { mutableStateOf(false) }
    val scale_ref = remember { floatArrayOf(1f) }
    val nl_scale_ref = remember { floatArrayOf(1f) }
    val is_nl_ref = remember { booleanArrayOf(false) }

    val height_sink = remember {
        height_channel { h, exact ->
            if (h > 0) {
                val visual_h = (h * scale_ref[0]).toInt()
                val new_dp = visual_h.dp
                if (!has_measured) {
                    content_height_dp = new_dp
                    has_measured = true
                    on_ready()
                } else if (exact || new_dp > content_height_dp) {
                    content_height_dp = new_dp
                }
            }
        }
    }

    val proxy_base = "https://app.astermail.org/api/images/v1/proxy?url="

    fun proxy_html(raw: String): String {
        val cid_normalized = CID_SRC_PATTERN.replace(raw) { match ->
            val prefix = match.groupValues[1]
            val suffix = match.groupValues[3]
            "${prefix}data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==${suffix}"
        }
        if (!allow_external) return cid_normalized
        val protocol_normalized = PROXY_PROTOCOL_RELATIVE_SRC_PATTERN.replace(cid_normalized) { match ->
            "${match.groupValues[1]}https:${match.groupValues[2]}${match.groupValues[3]}"
        }
        val src_replaced = PROXY_SRC_PATTERN.replace(protocol_normalized) { match ->
            val prefix = match.groupValues[1]
            val url = match.groupValues[2]
            val suffix = match.groupValues[3]
            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
            "$prefix$proxy_base$encoded$suffix"
        }
        val srcset_replaced = PROXY_SRCSET_PATTERN.replace(src_replaced) { match ->
            val prefix = match.groupValues[1]
            val srcset_value = match.groupValues[2]
            val suffix = match.groupValues[3]
            val proxied = srcset_value.split(",").joinToString(",") { entry ->
                val parts = entry.trim().split(Regex("\\s+"), 2)
                val url = parts[0]
                val descriptor = if (parts.size > 1) " ${parts[1]}" else ""
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                    "$proxy_base$encoded$descriptor"
                } else if (url.startsWith("//")) {
                    val encoded = java.net.URLEncoder.encode("https:$url", "UTF-8")
                    "$proxy_base$encoded$descriptor"
                } else {
                    ""
                }
            }
            "$prefix$proxied$suffix"
        }
        return PROXY_CSS_URL_PATTERN.replace(srcset_replaced) { match ->
            val prefix = match.groupValues[1]
            val url = match.groupValues[2]
            val suffix = match.groupValues[3]
            val encoded = java.net.URLEncoder.encode(url, "UTF-8")
            "$prefix$proxy_base$encoded$suffix"
        }
    }

    LaunchedEffect(html, allow_external, bg_hex) {
        scale_ref[0] = 1f
        val cached = html_cache.get(cache_key)
        if (cached != null) {
            prebuilt_html = cached
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.Default) {
            val sanitized = EmailHtmlSanitizer.sanitize(html)
            build_html(proxy_html(sanitized))
        }
        html_cache.put(cache_key, result)
        prebuilt_html = result
    }

    LaunchedEffect(html, allow_external) {
        delay(600)
        if (!has_measured) {
            has_measured = true
            on_ready()
        }
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
                    "http", "https", "mailto", "tel", "sms", "aster" -> {
                        on_link_click(url)
                        return true
                    }
                    "about" -> return false
                    else -> return true
                }
            }

            override fun onScaleChanged(view: android.webkit.WebView?, oldScale: Float, newScale: Float) {
                scale_ref[0] = if (is_nl_ref[0]) nl_scale_ref[0] else newScale
            }

            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                val bg_detect_js = """(function(){
  var body=document.body;if(!body)return;
  if(!body.style.backgroundColor||body.style.backgroundColor==='transparent'||body.style.backgroundColor==='rgba(0, 0, 0, 0)'){
    var first=body.firstElementChild;
    if(first){
      var bg=first.getAttribute('bgcolor')||first.style.backgroundColor;
      if(!bg||bg==='transparent'||bg==='rgba(0, 0, 0, 0)'){
        var tag=first.tagName;
        if(tag==='TABLE'||tag==='DIV'||tag==='CENTER'){bg=window.getComputedStyle(first).backgroundColor}
      }
      if(bg&&bg!=='transparent'&&bg!=='rgba(0, 0, 0, 0)'){
        body.style.backgroundColor=bg;document.documentElement.style.backgroundColor=bg;
      }
    }
  }
})()"""
                val fit_and_measure_js = """(function(){window.__aster_fit&&window.__aster_fit();})()"""
                val email_prefs = settings_vm.state.value.preferences
                if (email_prefs?.force_dark_emails == true) {
                    view?.evaluateJavascript("""(function(){var s=document.createElement('style');s.textContent='html,body,table,td,div,p{background-color:#141414!important;color:#f0f0f0!important}a{color:#818cf8!important}img{filter:brightness(0.85)}';document.head.appendChild(s);})()""", null)
                }
                if (email_prefs?.underline_links == true) {
                    view?.evaluateJavascript("""(function(){var s=document.createElement('style');s.textContent='a{text-decoration:underline!important}';document.head.appendChild(s);})()""", null)
                }
                view?.evaluateJavascript(bg_detect_js, null)
                view?.evaluateJavascript(fit_and_measure_js, null)
                view?.postDelayed({ view.evaluateJavascript(fit_and_measure_js, null) }, 300)
                view?.postDelayed({ view.evaluateJavascript(fit_and_measure_js, null) }, 1000)
            }

            override fun shouldInterceptRequest(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                val req_uri = request?.url ?: return null
                val url = req_uri.toString()
                if (req_uri.scheme != "https") return null
                if (req_uri.host != "app.astermail.org") return null
                if (req_uri.path != "/api/images/v1/proxy") return null
                if (!url.startsWith(proxy_base)) return null
                val current_token = settings_vm.get_access_token()
                if (current_token.isNullOrBlank()) return null
                val ctx = view?.context ?: return null
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
                        if (refreshed.isNullOrBlank()) return null
                        resp = fetch(refreshed)
                    }
                    if (!resp.isSuccessful) { resp.close(); return null }
                    val body = resp.body
                    if (body == null) { resp.close(); return null }
                    val content_type = resp.header("Content-Type")?.substringBefore(';')?.trim()
                        ?.takeIf { it.isNotBlank() } ?: "image/jpeg"
                    val stream = object : java.io.FilterInputStream(body.byteStream()) {
                        override fun close() {
                            try { super.close() } finally { resp.close() }
                        }
                    }
                    android.webkit.WebResourceResponse(content_type, null, stream)
                } catch (_: Throwable) { null }
            }
        }
    }

    Box(modifier = modifier.background(colors.bg_primary), contentAlignment = Alignment.Center) {
        if (!has_measured) {
            email_body_skeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
            )
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(bg_color)
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
                    settings.saveFormData = false
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
                }
            },
            update = { web_view ->
                val built = prebuilt_html ?: return@AndroidView
                val is_newsletter = built.contains("data-nl=\"1\"")
                web_view.settings.textZoom = text_zoom
                web_view.settings.loadsImagesAutomatically = true
                web_view.settings.blockNetworkImage = !allow_external
                web_view.settings.mixedContentMode =
                    android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (loaded_html != html || loaded_external != allow_external) {
                    if (loaded_html != html) has_measured = false
                    web_view.setBackgroundColor(if (is_newsletter) android.graphics.Color.WHITE else bg_color)
                    loaded_html = html
                    loaded_external = allow_external
                    is_nl_ref[0] = is_newsletter
                    nl_scale_ref[0] = if (is_newsletter) {
                        Regex("initial-scale=([0-9.]+)").find(built)
                            ?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0.1f, 1.0f) ?: 1f
                    } else {
                        1f
                    }
                    scale_ref[0] = nl_scale_ref[0]
                    web_view.loadDataWithBaseURL("https://mail-content.invalid/", built, "text/html", "UTF-8", null)
                    if (!has_measured) web_view.evaluateJavascript(measure_js, null)
                    web_view.postDelayed({
                        if (!has_measured) {
                            web_view.evaluateJavascript(
                                "(function(){var m=document.getElementById('m');if(!m)return 0;var h=Math.round(m.getBoundingClientRect().height);if(!document.documentElement.getAttribute('data-nl')){var de=document.documentElement;var b=document.body;if(de)h=Math.max(h,de.scrollHeight);if(b)h=Math.max(h,b.scrollHeight);h+=4;}return h;})()",
                            ) { result ->
                                val parsed = result?.trim()?.removeSurrounding("\"")?.toIntOrNull() ?: 0
                                if (parsed > 0) {
                                    content_height_dp = (parsed * scale_ref[0]).toInt().dp
                                } else if (content_height_dp == 0.dp) {
                                    content_height_dp = 320.dp
                                }
                                has_measured = true
                                on_ready()
                            }
                        }
                    }, 800)
                }
            },
            modifier = run {
                val target = if (has_measured && content_height_dp > 0.dp) content_height_dp else 320.dp
                val animated_h by androidx.compose.animation.core.animateDpAsState(
                    targetValue = target,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 100),
                    label = "web_height",
                )
                val reveal by animateFloatAsState(
                    targetValue = if (has_measured) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
                    label = "web_reveal",
                )
                Modifier.height(animated_h).alpha(reveal)
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
                imageVector = Icons.Filled.AttachFile,
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
                imageVector = Icons.Filled.Download,
                contentDescription = stringResource(R.string.download),
                tint = colors.text_primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun attachment_icon(content_type: String): ImageVector {
    return when {
        content_type.startsWith("image/") -> Icons.Outlined.Image
        content_type.startsWith("video/") -> Icons.Outlined.Videocam
        content_type.startsWith("audio/") -> Icons.Outlined.MusicNote
        content_type.contains("pdf") -> Icons.Outlined.Description
        content_type.contains("zip") || content_type.contains("gzip") ||
            content_type.contains("tar") || content_type.contains("rar") ||
            content_type.contains("7z") -> Icons.Outlined.FolderZip
        content_type.contains("html") || content_type.contains("xml") ||
            content_type.contains("json") || content_type.contains("javascript") -> Icons.Outlined.Code
        content_type.startsWith("text/") || content_type.contains("document") ||
            content_type.contains("msword") || content_type.contains("spreadsheet") ||
            content_type.contains("presentation") -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
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
        val mime = attachment.content_type.ifBlank { "application/octet-stream" }
        val safe_name = sanitize_filename(attachment.filename)
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
                    icon = Icons.Outlined.Close,
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
                    icon = Icons.Filled.Download,
                    content_description = stringResource(R.string.download),
                    onClick = on_download,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                AsterIconButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
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
private fun bottom_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    test_tag: String? = null,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
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
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = tint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
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
            imageVector = Icons.Filled.Lock,
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
                imageVector = Icons.Filled.Warning,
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
