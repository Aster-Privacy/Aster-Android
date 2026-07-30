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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import org.astermail.android.design.components.AsterDivider
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

    val from_email = p.sender_email ?: ""
    val from_name = p.sender_display_name?.takeIf { it.isNotBlank() } ?: from_email
    val to_line = p.to.joinToString(", ")
    val cc_line = p.cc.joinToString(", ")
    val bcc_line = p.bcc.joinToString(", ")
    val subject_text = p.subject.ifBlank { stringResource(R.string.no_subject) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xs, vertical = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { on_back() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.ArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = colors.text_primary,
                )
            }
            Spacer(Modifier.width(AsterSpacing.xs))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Lock,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.end_to_end_encrypted),
                    color = colors.accent_blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(44.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = subject_text,
                color = colors.text_primary,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.lg)
                    .padding(top = AsterSpacing.sm, bottom = AsterSpacing.sm),
            )
            AsterDivider()
            Spacer(Modifier.height(AsterSpacing.sm))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                SenderAvatar(email = from_email, name = from_name)
                Spacer(Modifier.width(AsterSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = from_name,
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (from_email.isNotBlank() && from_email != from_name) {
                        Text(
                            text = from_email,
                            color = colors.text_muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (to_line.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.to_recipients_inline, to_line),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                        )
                    }
                    if (cc_line.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.cc_recipients_inline, cc_line),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                        )
                    }
                    if (bcc_line.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.bcc_recipients_inline, bcc_line),
                            color = colors.text_muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            email_html_view(
                html = p.body_html,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.md),
            )

            if (p.attachment_names.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    p.attachment_names.forEach { fname ->
                        Row(
                            modifier = Modifier
                                .clip(SquircleShape(18.dp))
                                .background(colors.dropdown_bg)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = TablerIcons.Paperclip,
                                contentDescription = null,
                                tint = colors.text_muted,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = fname, color = colors.text_primary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(AsterSpacing.lg))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.dropdown_bg)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Clock,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sending_in_countdown, seconds_left),
                color = colors.text_primary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.undo),
                color = colors.accent_blue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .clickable {
                        p.undo()
                        on_back()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
