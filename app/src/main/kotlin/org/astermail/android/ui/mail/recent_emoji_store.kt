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

import android.content.Context
import org.astermail.android.storage.SecurePrefs

private const val recent_emoji_prefs = "aster_recent_emoji_v1"
private const val recent_emoji_key = "recent"
private const val recent_emoji_separator = ","
private const val recent_emoji_limit = 24

object RecentEmojiStore {
    fun load(context: Context): List<String> {
        val raw = runCatching {
            SecurePrefs.open(context, recent_emoji_prefs).getString(recent_emoji_key, null)
        }.getOrNull() ?: return emptyList()
        return raw.split(recent_emoji_separator).filter { it.isNotBlank() }.take(recent_emoji_limit)
    }

    fun record(context: Context, glyph: String): List<String> {
        if (glyph.isBlank() || glyph.contains(recent_emoji_separator)) return load(context)
        val updated = (listOf(glyph) + load(context).filter { it != glyph }).take(recent_emoji_limit)
        runCatching {
            SecurePrefs.open(context, recent_emoji_prefs)
                .edit()
                .putString(recent_emoji_key, updated.joinToString(recent_emoji_separator))
                .apply()
        }
        return updated
    }
}
