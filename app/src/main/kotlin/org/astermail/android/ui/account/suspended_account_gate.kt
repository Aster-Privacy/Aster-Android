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
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ChevronDown
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.astermail.android.R
import org.astermail.android.account.SuspendedAccountViewModel
import org.astermail.android.accounts.AccountsViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
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

    val current_account = accounts_state.accounts.firstOrNull { it.id == accounts_state.current_account_id }
    val account_email = current_account?.email.orEmpty()
    val account_name = current_account?.display_name.orEmpty()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            accounts_view_model.refresh_with_profile()
                            show_switcher = true
                        }
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                        .testTag("workspace_switcher"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    plan_ring(size = 36.dp, enabled = remember_has_paid_plan()) {
                        SenderAvatar(
                            email = account_email,
                            name = account_name,
                            size = 36.dp,
                            profile_picture_url = current_account?.profile_picture,
                            profile_color = current_account?.profile_color,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = display_name,
                            color = colors.text_primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        Text(
                            text = account_email,
                            color = colors.text_muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = TablerIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.text_muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.xl)
                    .padding(top = AsterSpacing.lg, bottom = AsterSpacing.xxxl),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.warning.copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = TablerIcons.AlertTriangle,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.suspended_alert),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        )
                        Text(
                            text = stringResource(R.string.suspended_alert_terms),
                            color = colors.text_primary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            modifier = Modifier.clickable { open_url(context, TERMS_URL) },
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = status_line(state),
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(AsterSpacing.lg))

                Text(
                    text = stringResource(R.string.suspended_appeal_hint),
                    color = colors.text_secondary,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )

                Spacer(Modifier.height(AsterSpacing.lg))

                Text(
                    text = stringResource(R.string.suspended_download_hint),
                    color = colors.text_secondary,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )

                Spacer(Modifier.height(36.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsterGhostButton(
                        label = stringResource(R.string.suspended_download),
                        onClick = { show_export = true },
                        enabled = !state.is_signing_out,
                    )
                    AsterButton(
                        label = stringResource(R.string.suspended_start_appeal),
                        onClick = { open_url(context, appeal_url_for(account_email)) },
                        enabled = !state.is_signing_out,
                    )
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.pending_deletion_sign_out),
                    color = colors.text_muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !state.is_signing_out) {
                            view_model.sign_out(on_signed_out)
                        }
                        .padding(vertical = 8.dp),
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
            current_picture = current_account?.profile_picture,
            current_color = current_account?.profile_color,
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
        else -> stringResource(R.string.suspended_title)
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
