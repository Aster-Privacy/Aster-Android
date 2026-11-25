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

package org.astermail.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.storage.StoredAccount
import org.astermail.android.ui.mail.SenderAvatar

val top_bar_account_button_size: Dp = 48.dp
val top_bar_account_avatar_size: Dp = 34.dp
private val top_bar_leading_icon_size: Dp = 22.dp

fun top_bar_account_end_inset(ring_enabled: Boolean): Dp {
    val icon_inset = (top_bar_account_button_size - top_bar_leading_icon_size) / 2
    val visual_size = if (ring_enabled) {
        top_bar_account_avatar_size + (plan_ring_stroke + plan_ring_gap) * 2
    } else {
        top_bar_account_avatar_size
    }
    val avatar_inset = (top_bar_account_button_size - visual_size) / 2
    return (icon_inset - avatar_inset).coerceAtLeast(0.dp)
}

@Composable
fun top_bar_account_button(
    stored_account: StoredAccount?,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings_vm = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val user = settings_state.user
    val email = user?.email?.takeIf { it.isNotBlank() } ?: stored_account?.email.orEmpty()
    val name = user?.display_name ?: stored_account?.display_name.orEmpty()
    val picture = if (user != null) user.profile_picture else stored_account?.profile_picture
    val color = if (user != null) user.profile_color else stored_account?.profile_color
    val description = stringResource(R.string.account)
    val ring_enabled = remember_has_paid_plan()
    Box(
        modifier = modifier
            .padding(end = top_bar_account_end_inset(ring_enabled))
            .size(top_bar_account_button_size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = on_click)
            .semantics { contentDescription = description }
            .testTag("open_account"),
        contentAlignment = Alignment.Center,
    ) {
        plan_ring(size = top_bar_account_avatar_size, enabled = ring_enabled) {
            if (email.isBlank() && name.isBlank()) {
                Box(
                    modifier = Modifier
                        .size(top_bar_account_avatar_size)
                        .clip(CircleShape)
                        .background(AsterMaterial.colors.text_muted.copy(alpha = 0.18f)),
                )
            } else {
                SenderAvatar(
                    email = email,
                    name = name,
                    size = top_bar_account_avatar_size,
                    profile_picture_url = picture,
                    profile_color = color,
                    use_contact_photo = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}
