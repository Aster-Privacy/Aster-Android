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

package org.astermail.android.ui.compose

import org.astermail.android.mail.has_mixed_recipients
import org.astermail.android.mail.is_internal_recipient

enum class SendBlocker {
    MIXED_RECIPIENTS,
    EXPIRATION_LOCKED,
    EXPIRY_PASSWORD_LOCKED,
    EXPIRY_PASSWORD_INTERNAL,
    EXPIRY_NEEDS_SECURE_MESSAGE,
}

enum class ExpiryPasswordMode { AVAILABLE, LOCKED, HIDDEN }

internal fun all_recipients_internal(recipients: List<String>): Boolean {
    val addresses = recipients.filter { it.isNotBlank() }
    return addresses.isNotEmpty() && addresses.all { is_internal_recipient(it) }
}

internal fun send_blocker_for(
    recipients: List<String>,
    via_connected_account: Boolean,
    has_expiry: Boolean,
    has_expiry_password: Boolean,
    expiration_locked: Boolean,
    expiry_password_locked: Boolean,
    require_encryption: Boolean,
): SendBlocker? {
    if (via_connected_account) return null
    if (has_mixed_recipients(recipients)) return SendBlocker.MIXED_RECIPIENTS
    if (has_expiry && expiration_locked) return SendBlocker.EXPIRATION_LOCKED
    if (has_expiry_password && expiry_password_locked) return SendBlocker.EXPIRY_PASSWORD_LOCKED
    val internal_only = all_recipients_internal(recipients)
    if (has_expiry_password && internal_only) return SendBlocker.EXPIRY_PASSWORD_INTERNAL
    if (has_expiry && require_encryption && !internal_only) return SendBlocker.EXPIRY_NEEDS_SECURE_MESSAGE
    return null
}

internal fun expiry_password_mode_for(
    recipients: List<String>,
    password_locked: Boolean,
): ExpiryPasswordMode = when {
    all_recipients_internal(recipients) -> ExpiryPasswordMode.HIDDEN
    password_locked -> ExpiryPasswordMode.LOCKED
    else -> ExpiryPasswordMode.AVAILABLE
}
