// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.astermail.android.R

fun open_external_url(context: Context, url: String): Boolean {
    return start_external_intent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun start_external_intent(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        Toast.makeText(
            context,
            context.getString(R.string.could_not_open_link),
            Toast.LENGTH_SHORT,
        ).show()
        false
    }
}
