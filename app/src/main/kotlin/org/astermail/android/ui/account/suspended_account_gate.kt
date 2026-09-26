//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.account

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.astermail.android.R
import org.astermail.android.account.SuspendedAccountViewModel
import org.astermail.android.accounts.AccountsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.storage.StoredAccount
import org.astermail.android.ui.common.page_surface
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan
import org.astermail.android.ui.drawer.workspace_switcher_sheet
import org.astermail.android.ui.mail.SenderAvatar
import org.astermail.android.ui.settings.detail.ExportScreen

private const val TERMS_URL = "https://astermail.org/terms"
private const val APPEAL_URL = "https://astermail.org/appeal"

internal fun appeal_url_for(address: String): String =
    if (address.isBlank()) APPEAL_URL else "$APPEAL_URL?address=${Uri.encode(address)}"

@Composable
fun SuspendedAccountGate(
    on_switched: (StoredAccount, Boolean) -> Unit,
    on_add_account: () -> Unit,
    on_signed_out: (Boolean) -> Unit,
    view_model: SuspendedAccountViewModel = hiltViewModel(),
    accounts_view_model: AccountsViewModel = hiltViewModel(),
) {
    val state by view_model.state.collectAsStateWithLifecycle()
    val accounts_state by accounts_view_model.state.collectAsStateWithLifecycle()

    LaunchedEffect(accounts_state.current_account_id) {
        view_model.reset()
        view_model.check()
    }

    if (!state.visible) return

    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var show_switcher by remember { mutableStateOf(false) }
    var show_export by remember { mutableStateOf(false) }

    val profile = state.profile
    val current_account = accounts_state.accounts.firstOrNull { it.id == (profile?.user_id ?: accounts_state.current_account_id) }
    val account_email = profile?.email?.takeIf { it.isNotBlank() } ?: current_account?.email.orEmpty()
    val account_name = profile?.display_name ?: current_account?.display_name.orEmpty()
    val account_picture = profile?.profile_picture ?: current_account?.profile_picture
    val account_color = profile?.profile_color ?: current_account?.profile_color
    val display_name = account_name.ifBlank { account_email.substringBefore('@') }

    BackHandler(enabled = !show_export) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .testTag("suspended_account_gate"),
    ) {
        val card = lerp(colors.dropdown_bg, colors.text_primary, 0.09f)
        val status_text = status_line(state)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AsterSpacing.xl)
                .padding(top = 40.dp, bottom = AsterSpacing.xxxl),
        ) {
            Image(
                painter = painterResource(R.drawable.aster_wordmark),
                contentDescription = null,
                modifier = Modifier.height(28.dp),
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(card)
                    .clickable {
                        accounts_view_model.refresh_with_profile()
                        show_switcher = true
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("workspace_switcher"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                plan_ring(size = 48.dp, enabled = remember_has_paid_plan()) {
                    SenderAvatar(
                        email = account_email,
                        name = account_name,
                        size = 48.dp,
                        profile_picture_url = account_picture,
                        profile_color = account_color,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = display_name,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = account_email,
                        color = colors.text_muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.suspended_title),
                color = colors.text_primary,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (status_text.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status_text,
                    color = colors.text_tertiary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.warning.copy(alpha = 0.14f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = TablerIcons.AlertTriangle,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.suspended_alert),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                    Text(
                        text = stringResource(R.string.suspended_alert_terms),
                        color = colors.text_primary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { open_url(context, TERMS_URL) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            action_row(
                title = stringResource(R.string.suspended_start_appeal),
                hint = stringResource(R.string.suspended_appeal_hint),
                background = card,
                enabled = !state.is_signing_out,
                tag = "suspended_start_appeal",
                onClick = { open_url(context, appeal_url_for(account_email)) },
            )

            Spacer(Modifier.height(8.dp))

            action_row(
                title = stringResource(R.string.suspended_download),
                hint = stringResource(R.string.suspended_download_hint),
                background = card,
                enabled = !state.is_signing_out,
                tag = "suspended_download",
                onClick = { show_export = true },
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, colors.text_primary.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                    .clickable(enabled = !state.is_signing_out) {
                        view_model.sign_out(on_signed_out)
                    }
                    .testTag("suspended_sign_out"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.pending_deletion_sign_out),
                    color = colors.text_primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (show_export) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.solid_bg),
            ) {
                ExportScreen(on_back = { show_export = false })
            }
        }
    }

    if (show_switcher) {
        workspace_switcher_sheet(
            accounts = accounts_state.accounts,
            current_account_id = accounts_state.current_account_id,
            current_email = account_email,
            current_name = account_name,
            current_picture = account_picture,
            current_color = account_color,
            plan_code = null,
            storage_used_fraction = 0f,
            storage_used_label = "",
            max_accounts = accounts_state.max_accounts,
            is_unlimited_accounts = accounts_state.is_unlimited,
            can_add = accounts_state.can_add_more,
            can_resubscribe = false,
            needs_sign_in = { account -> !accounts_view_model.has_stored_session(account.id) },
            on_dismiss = { show_switcher = false },
            on_resubscribe = { show_switcher = false },
            on_switch = { account ->
                show_switcher = false
                if (accounts_view_model.has_stored_session(account.id)) {
                    accounts_view_model.switch_account(account.id) { restored ->
                        on_switched(account, restored)
                    }
                } else {
                    accounts_view_model.switch_account(account.id)
                    on_switched(account, false)
                }
            },
            on_add = {
                show_switcher = false
                on_add_account()
            },
            on_manage_account = { show_switcher = false },
            on_open_storage = { show_switcher = false },
            on_logout = {
                show_switcher = false
                view_model.sign_out(on_signed_out)
            },
            on_logout_all = {
                show_switcher = false
                view_model.sign_out(on_signed_out)
            },
        )
    }
}

@Composable
private fun action_row(
    title: String,
    hint: String,
    background: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = hint,
                color = colors.text_muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun status_line(state: SuspendedAccountViewModel.UiState): String {
    val suspended_at = state.suspended_at
    val deletion_at = state.deletion_eligible_at
    return when {
        suspended_at != null && deletion_at != null -> stringResource(
            R.string.suspended_since_with_deletion,
            format_date(suspended_at),
            format_date(deletion_at),
        )
        suspended_at != null -> stringResource(R.string.suspended_since, format_date(suspended_at))
        else -> ""
    }
}

private fun format_date(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(instant)

private fun open_url(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
