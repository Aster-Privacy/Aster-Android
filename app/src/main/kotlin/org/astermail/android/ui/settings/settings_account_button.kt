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

package org.astermail.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.billing.ResubscribeViewModel
import org.astermail.android.storage.StoredAccount
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan
import org.astermail.android.ui.drawer.workspace_switcher_sheet
import org.astermail.android.ui.mail.SenderAvatar

val settings_account_button_size: Dp = 48.dp
private val settings_account_avatar_size: Dp = 34.dp

@Composable
fun settings_account_button(
    accounts: List<StoredAccount>,
    current_account_id: String?,
    user_email: String,
    display_email: String,
    plan_code: String?,
    storage_used_fraction: Float,
    storage_used_label: String,
    max_accounts: Int,
    is_unlimited_accounts: Boolean,
    can_add_account: Boolean,
    needs_sign_in: (StoredAccount) -> Boolean,
    on_open_sheet: () -> Unit,
    on_switch_account: (StoredAccount) -> Unit,
    on_add_account: () -> Unit,
    on_manage_account: () -> Unit,
    on_open_storage: () -> Unit,
    on_logout: () -> Unit,
    on_logout_all: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var show_sheet by remember { mutableStateOf(false) }
    val current_account = accounts.firstOrNull { it.id == current_account_id }
    val account_description = stringResource(R.string.account)

    Box(
        modifier = modifier
            .size(settings_account_button_size)
            .clip(CircleShape)
            .clickable(role = Role.Button) {
                on_open_sheet()
                show_sheet = true
            }
            .semantics { contentDescription = account_description }
            .testTag("settings_search_account"),
        contentAlignment = Alignment.Center,
    ) {
        plan_ring(size = settings_account_avatar_size, enabled = remember_has_paid_plan()) {
            SenderAvatar(
                email = current_account?.email ?: user_email,
                name = current_account?.display_name.orEmpty(),
                size = settings_account_avatar_size,
                profile_picture_url = current_account?.profile_picture,
                profile_color = current_account?.profile_color,
                use_contact_photo = false,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }

    if (show_sheet) {
        account_manager_sheet(
            accounts = accounts,
            current_account_id = current_account_id,
            user_email = user_email,
            display_email = display_email,
            plan_code = plan_code,
            storage_used_fraction = storage_used_fraction,
            storage_used_label = storage_used_label,
            max_accounts = max_accounts,
            is_unlimited_accounts = is_unlimited_accounts,
            can_add_account = can_add_account,
            needs_sign_in = needs_sign_in,
            on_dismiss = { show_sheet = false },
            on_switch_account = on_switch_account,
            on_add_account = on_add_account,
            on_manage_account = on_manage_account,
            on_open_storage = on_open_storage,
            on_logout = on_logout,
            on_logout_all = on_logout_all,
        )
    }
}

@Composable
fun account_manager_sheet(
    accounts: List<StoredAccount>,
    current_account_id: String?,
    user_email: String,
    display_email: String,
    plan_code: String?,
    storage_used_fraction: Float,
    storage_used_label: String,
    max_accounts: Int,
    is_unlimited_accounts: Boolean,
    can_add_account: Boolean,
    needs_sign_in: (StoredAccount) -> Boolean,
    on_dismiss: () -> Unit,
    on_switch_account: (StoredAccount) -> Unit,
    on_add_account: () -> Unit,
    on_manage_account: () -> Unit,
    on_open_storage: () -> Unit,
    on_logout: () -> Unit,
    on_logout_all: () -> Unit,
) {
    val current_account = accounts.firstOrNull { it.id == current_account_id }
    val resubscribe_vm: ResubscribeViewModel = hiltViewModel()
    val resubscribe_kind by resubscribe_vm.kind.collectAsStateWithLifecycle()
    val resubscribe_context = LocalContext.current
    LaunchedEffect(current_account_id) {
        resubscribe_vm.load(current_account_id)
    }

    workspace_switcher_sheet(
        accounts = accounts,
        current_account_id = current_account_id,
        current_email = user_email.ifBlank { current_account?.email.orEmpty() },
        current_display_email = display_email.ifBlank {
            user_email.ifBlank { current_account?.email.orEmpty() }
        },
        current_name = current_account?.display_name.orEmpty(),
        current_picture = current_account?.profile_picture,
        current_color = current_account?.profile_color,
        plan_code = plan_code,
        storage_used_fraction = storage_used_fraction,
        storage_used_label = storage_used_label,
        max_accounts = max_accounts,
        is_unlimited_accounts = is_unlimited_accounts,
        can_add = can_add_account,
        can_resubscribe = resubscribe_kind != null,
        needs_sign_in = needs_sign_in,
        on_dismiss = on_dismiss,
        on_resubscribe = {
            on_dismiss()
            resubscribe_vm.resubscribe { message ->
                org.astermail.android.ui.common.app_toast.show(message.toString())
            }
        },
        on_switch = { account ->
            on_dismiss()
            on_switch_account(account)
        },
        on_add = {
            on_dismiss()
            on_add_account()
        },
        on_manage_account = {
            on_dismiss()
            on_manage_account()
        },
        on_open_storage = {
            on_dismiss()
            on_open_storage()
        },
        on_logout = {
            on_dismiss()
            on_logout()
        },
        on_logout_all = {
            on_dismiss()
            on_logout_all()
        },
    )
}
