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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.astermail.android.R
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogPrimaryButton

private val official_sender_domains = setOf("astermail.org", "aster.cx")

private val official_sender_roles = setOf(
    "hello",
    "support",
    "security",
    "privacy",
    "billing",
    "abuse",
    "legal",
    "press",
    "team",
    "noreply",
    "no-reply",
    "updates",
)

fun is_official_address(email: String?): Boolean {
    val parts = email?.trim()?.lowercase()?.split('@') ?: return false
    if (parts.size != 2) return false
    val local = parts[0]
    val domain = parts[1]
    if (local.isEmpty() || domain.isEmpty()) return false
    return domain in official_sender_domains && local in official_sender_roles
}

fun is_official_sender(
    email: String?,
    system_origin: Boolean,
    is_external: Boolean,
    sender_verification: String?,
): Boolean {
    if (!is_official_address(email)) return false
    if (is_external) return false
    return system_origin || sender_verification == "verified"
}

fun is_official_sender(email: Email): Boolean = is_official_sender(
    email = email.sender_email,
    system_origin = email.system_origin,
    is_external = email.is_external,
    sender_verification = email.trust_message?.sender_verification,
)

fun is_official_sender(msg: ThreadMessage): Boolean = is_official_sender(
    email = msg.sender_email,
    system_origin = msg.system_origin,
    is_external = msg.is_external,
    sender_verification = msg.trust_message?.sender_verification,
)

@Composable
fun official_badge(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    interactive: Boolean = true,
) {
    var show_info by remember { mutableStateOf(false) }
    val label = stringResource(R.string.official_sender)
    Image(
        painter = painterResource(R.drawable.official_badge),
        contentDescription = label,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (interactive) Modifier.clickable { show_info = true } else Modifier)
            .testTag("official_badge"),
    )
    if (show_info) {
        AsterDialog(
            on_dismiss = { show_info = false },
            title = label,
            message = stringResource(R.string.official_sender_desc),
            footer = {
                AsterDialogPrimaryButton(
                    label = stringResource(R.string.done),
                    onClick = { show_info = false },
                )
            },
        )
    }
}
