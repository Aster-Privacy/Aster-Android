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

package org.astermail.android.share

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

fun app_owns_media_row(
    resolver: ContentResolver,
    uri: Uri,
    own_package: String,
    sdk_int: Int = Build.VERSION.SDK_INT,
): Boolean {
    if (!is_media_share_authority(uri.authority)) return false
    if (sdk_int < 29) return false
    return try {
        val owner_column = MediaStore.MediaColumns.OWNER_PACKAGE_NAME
        resolver.query(uri, arrayOf(owner_column), null, null, null)?.use { cursor ->
            val owner_index = cursor.getColumnIndex(owner_column)
            val owners = if (owner_index < 0) {
                null
            } else {
                buildList<String?> {
                    while (cursor.moveToNext()) {
                        add(if (cursor.isNull(owner_index)) null else cursor.getString(owner_index))
                    }
                }
            }
            media_rows_include_own(cursor.count, owners, own_package)
        } ?: true
    } catch (_: Throwable) {
        true
    }
}
