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
package org.astermail.android.mail

const val MAX_RECIPIENTS_PER_FIELD = 50
const val MAX_RECIPIENTS_PER_SEND = 100

enum class RecipientLimitViolation {
    FIELD,
    TOTAL,
}

fun recipient_limit_violation(
    to: List<String>,
    cc: List<String>,
    bcc: List<String>,
): RecipientLimitViolation? {
    if (to.size > MAX_RECIPIENTS_PER_FIELD ||
        cc.size > MAX_RECIPIENTS_PER_FIELD ||
        bcc.size > MAX_RECIPIENTS_PER_FIELD
    ) {
        return RecipientLimitViolation.FIELD
    }
    if (to.size + cc.size + bcc.size > MAX_RECIPIENTS_PER_SEND) {
        return RecipientLimitViolation.TOTAL
    }
    return null
}
