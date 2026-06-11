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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.api.subscriptions.MailingListSubscription
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterRadius
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDestructiveButton
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTopBar
import org.astermail.android.subscriptions.MailingListsViewModel

@Composable
fun MailingListsScreen(on_back: () -> Unit = {}, on_open_drawer: (() -> Unit)? = null) {
    val vm: MailingListsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load()
        vm.auto_scan_if_empty()
    }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clear_message()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            vm.clear_error()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        if (on_open_drawer != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = AsterSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                org.astermail.android.design.components.AsterIconButton(
                    icon = Icons.Filled.Menu,
                    content_description = stringResource(R.string.open_drawer),
                    onClick = on_open_drawer,
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.subscriptions),
                    color = colors.text_primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
            }
        } else {
            AsterTopBar(title = stringResource(R.string.subscriptions), on_back = on_back)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AsterSpacing.lg),
        ) {
            val active = state.items.filter { it.status == "active" }
            val unsubscribed = state.items.filter { it.status == "unsubscribed" }

            AsterCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                    Column {
                        Text(
                            text = "${active.size} ${stringResource(R.string.active).lowercase()}",
                            color = colors.text_primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${state.items.size} ${stringResource(R.string.senders_tracked)}" +
                                if (unsubscribed.isNotEmpty()) " - ${unsubscribed.size} ${stringResource(R.string.unsubscribed)}" else "",
                            color = colors.text_tertiary,
                            fontSize = 13.sp,
                        )
                        if (state.is_scanning) {
                            Spacer(Modifier.height(AsterSpacing.sm))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = colors.accent_blue,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(AsterSpacing.sm))
                                Text(
                                    text = stringResource(R.string.scanning),
                                    color = colors.text_tertiary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(AsterSpacing.lg))

            when {
                state.is_loading && state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.accent_blue, modifier = Modifier.size(24.dp))
                    }
                }
                state.items.isEmpty() -> {
                    AsterCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MarkEmailRead,
                                contentDescription = null,
                                tint = colors.text_tertiary,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(AsterSpacing.md))
                            Text(
                                text = stringResource(R.string.no_mailing_lists),
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(AsterSpacing.xs))
                            Text(
                                text = stringResource(R.string.scan_inbox_hint),
                                color = colors.text_tertiary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                else -> {
                    if (active.isNotEmpty()) {
                        section_header(stringResource(R.string.active_tab))
                        AsterCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                active.forEachIndexed { idx, item ->
                                    subscription_row(
                                        item = item,
                                        is_pending = item.id in state.pending_ids,
                                        on_action = { vm.unsubscribe(item.id) },
                                        action_label = stringResource(R.string.unsubscribe),
                                        is_destructive = true,
                                    )
                                    if (idx < active.lastIndex) AsterDivider()
                                }
                            }
                        }
                        Spacer(Modifier.height(AsterSpacing.lg))
                    }
                    if (unsubscribed.isNotEmpty()) {
                        section_header(stringResource(R.string.unsubscribed_tab))
                        AsterCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                unsubscribed.forEachIndexed { idx, item ->
                                    subscription_row(
                                        item = item,
                                        is_pending = item.id in state.pending_ids,
                                        on_action = { vm.reactivate(item.id) },
                                        action_label = stringResource(R.string.reactivate),
                                    )
                                    if (idx < unsubscribed.lastIndex) AsterDivider()
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.xxl))
        }
    }
}

@Composable
private fun section_header(label: String) {
    val colors = AsterMaterial.colors
    Text(
        text = label.uppercase(),
        color = colors.text_tertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = AsterSpacing.xs, bottom = AsterSpacing.xs),
    )
}

@Composable
private fun subscription_row(
    item: MailingListSubscription,
    is_pending: Boolean,
    on_action: () -> Unit,
    action_label: String,
    is_destructive: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(
            email = item.sender_email,
            name = item.sender_name,
            size = 36.dp,
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.sender_name.ifBlank { item.sender_email },
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = item.sender_email,
                color = colors.text_tertiary,
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.rules_emails_count, item.email_count),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                )
                if (item.category.isNotBlank() && item.category != "unknown") {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Text(text = "·", color = colors.text_muted, fontSize = 11.sp)
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Text(
                        text = item.category.replaceFirstChar { it.uppercase() },
                        color = colors.text_muted,
                        fontSize = 11.sp,
                    )
                }
                if (item.risk_level == "risky") {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Box(
                        modifier = Modifier
                            .clip(SquircleShape(AsterRadius.sm))
                            .background(colors.danger.copy(alpha = 0.12f))
                            .padding(horizontal = AsterSpacing.xs, vertical = 1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.risky),
                            color = colors.danger,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        if (is_destructive) {
            AsterDestructiveButton(
                label = action_label,
                onClick = on_action,
                is_loading = is_pending,
                modifier = Modifier.width(130.dp).height(40.dp),
            )
        } else {
            AsterSecondaryButton(
                label = action_label,
                onClick = on_action,
                is_loading = is_pending,
                modifier = Modifier.width(130.dp).height(40.dp),
            )
        }
    }
}
