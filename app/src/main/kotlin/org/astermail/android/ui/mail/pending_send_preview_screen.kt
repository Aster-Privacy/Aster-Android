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
import compose.icons.tablericons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.mail.MailViewModel

@Composable
fun pending_send_preview_screen(
    on_back: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val mail_vm: MailViewModel = hiltViewModel()
    val pending by mail_vm.pending_undo_send.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        if (pending == null) on_back()
    }

    val p = pending ?: return
    var now_ms by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.started_at_ms) {
        while (true) {
            now_ms = System.currentTimeMillis()
            val remaining = (p.started_at_ms + p.duration_ms) - now_ms
            if (remaining <= 0) break
            delay(((remaining - 1) % 1000 + 1))
        }
    }
    val remaining_ms = (p.started_at_ms + p.duration_ms) - now_ms
    val seconds_left = (((remaining_ms + 999) / 1000).toInt()).coerceAtLeast(0)

    val from_email = p.sender_email.orEmpty()
    val from_name = p.sender_display_name?.takeIf { it.isNotBlank() } ?: from_email
    val to_line = p.to.joinToString(", ")
    val subject_text = p.subject.ifBlank { stringResource(R.string.no_subject) }

    val message = remember(p.started_at_ms, p.body_html) {
        ThreadMessage(
            id = "pending_send_${p.started_at_ms}",
            sender_name = from_name,
            sender_email = from_email,
            to_label = to_line,
            to_addresses = p.to,
            cc_addresses = p.cc,
            timestamp = p.started_at_ms,
            body = "",
            body_html = p.body_html,
            is_encrypted = true,
            item_type = "sent",
            attachments = p.attachment_names.mapIndexed { index, filename ->
                MessageAttachment(
                    id = "pending_attachment_$index",
                    filename = filename,
                    content_type = p.attachment_types.getOrElse(index) { "application/octet-stream" },
                    size_bytes = p.attachment_sizes.getOrElse(index) { 0L },
                )
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            AsterIconButton(
                icon = TablerIcons.ArrowLeft,
                auto_mirror = true,
                content_description = stringResource(R.string.back),
                onClick = on_back,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(SquircleShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = TablerIcons.Clock,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.sending_in_countdown, seconds_left),
                    color = colors.accent_blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "subject_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AsterSpacing.lg, end = AsterSpacing.lg)
                        .padding(top = AsterSpacing.sm, bottom = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = subject_text,
                        color = colors.text_primary,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "pending_message") {
                expanded_message(
                    msg = message,
                    is_last = true,
                    can_collapse = false,
                    show_header_reply = false,
                    on_collapse = {},
                    on_reply = {},
                    on_reply_all = {},
                    on_forward = {},
                    on_more = {},
                )
            }
            item(key = "pending_footer") {
                Spacer(Modifier.height(AsterSpacing.lg))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg_primary)
                .padding(WindowInsets.navigationBars.asPaddingValues()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reply_action_button(
                    icon = TablerIcons.ArrowBackUp,
                    label = stringResource(R.string.undo),
                    bg = colors.accent_blue,
                    fg = Color.White,
                    label_size = 14.sp,
                    on_label_overflow = {},
                    on_click = {
                        p.undo()
                        on_back()
                    },
                    modifier = Modifier.weight(1f),
                )
                reply_action_button(
                    icon = TablerIcons.Check,
                    label = stringResource(R.string.done),
                    bg = Color.Transparent,
                    fg = colors.text_primary,
                    label_size = 14.sp,
                    on_label_overflow = {},
                    on_click = on_back,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
