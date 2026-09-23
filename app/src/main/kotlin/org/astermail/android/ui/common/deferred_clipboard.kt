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

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard

@Composable
fun deferred_clipboard_provider(content: @Composable () -> Unit) {
    val system_clipboard = LocalClipboard.current
    val clipboard = remember(system_clipboard) { deferred_clipboard(system_clipboard) }
    CompositionLocalProvider(LocalClipboard provides clipboard, content = content)
}

private class deferred_clipboard(private val delegate: Clipboard) : Clipboard {
    override val nativeClipboard: NativeClipboard
        get() = delegate.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? {
        val manager = delegate.nativeClipboard
        val description = runCatching { manager.primaryClipDescription }.getOrNull() ?: return null
        return ClipEntry(deferred_clip_data(description, manager))
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        delegate.setClipEntry(clipEntry)
    }
}

internal class deferred_clip_data(
    description: ClipDescription,
    private val manager: ClipboardManager,
) : ClipData(description, Item("")) {
    private val source: ClipData? by lazy { runCatching { manager.primaryClip }.getOrNull() }

    override fun getItemCount(): Int = source?.itemCount ?: 0

    override fun getItemAt(index: Int): Item =
        source?.takeIf { index in 0 until it.itemCount }?.getItemAt(index) ?: Item("")
}
