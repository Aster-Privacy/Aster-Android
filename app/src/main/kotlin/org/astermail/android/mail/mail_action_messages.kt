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

import android.content.Context
import org.astermail.android.R

fun archived_action_message(context: Context, count: Int, message_scope: Boolean): String =
    if (message_scope) {
        context.resources.getQuantityString(R.plurals.archived_messages, count, count)
    } else {
        context.resources.getQuantityString(R.plurals.archived_conversations, count, count)
    }

fun trashed_action_message(context: Context, count: Int, message_scope: Boolean): String =
    if (message_scope) {
        context.resources.getQuantityString(R.plurals.moved_messages_to_trash, count, count)
    } else {
        context.resources.getQuantityString(R.plurals.moved_to_trash, count, count)
    }

fun batch_action_key(action: String, message_scope: Boolean): String =
    if (message_scope) "${action}_message" else action
