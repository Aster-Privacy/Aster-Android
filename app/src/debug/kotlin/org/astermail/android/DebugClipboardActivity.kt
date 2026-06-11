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

package org.astermail.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

class DebugClipboardActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE)
        val selection = "${MediaStore.Images.Media.MIME_TYPE} LIKE 'image/%'"
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        var uri_str: String? = null
        var mime_str: String? = null
        contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val mime = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                val u = ContentUris.withAppendedId(collection, id)
                uri_str = u.toString()
                mime_str = mime
                val clip = ClipData.newUri(contentResolver, "image", u)
                cm.setPrimaryClip(clip)
            }
        }
        Log.w("AsterCompose", "DebugClipboard set uri=$uri_str mime=$mime_str")
        Toast.makeText(this, "Clipboard set: ${uri_str ?: "none"}", Toast.LENGTH_SHORT).show()
        finish()
    }
}
