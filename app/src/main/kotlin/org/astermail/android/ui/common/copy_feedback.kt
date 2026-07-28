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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import org.astermail.android.ui.theme.local_accessibility

@Composable
fun remember_copy_action(): (label: String, value: String, toast: String) -> Unit {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val haptic_enabled = local_accessibility.current.haptic_enabled
    return remember(context, haptics, haptic_enabled) {
        { label, value, toast ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }
    }
}
