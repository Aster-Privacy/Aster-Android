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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.contacts.ContactPhotoDirectory
import org.astermail.android.mail.OwnAddressAvatars

val local_own_address_avatars: ProvidableCompositionLocal<State<Map<String, String>>?> =
    staticCompositionLocalOf { null }

val local_contact_photo_version: ProvidableCompositionLocal<State<Long>?> =
    staticCompositionLocalOf { null }

@Composable
fun collected_own_address_avatars_state(): State<Map<String, String>> =
    OwnAddressAvatars.entries.collectAsStateWithLifecycle()

@Composable
fun collected_contact_photo_version_state(): State<Long> =
    ContactPhotoDirectory.version.collectAsStateWithLifecycle()

@Composable
internal fun own_address_avatar_entries(): Map<String, String> {
    local_own_address_avatars.current?.let { return it.value }
    val entries by OwnAddressAvatars.entries.collectAsStateWithLifecycle()
    return entries
}

@Composable
internal fun contact_photo_directory_version(): Long {
    local_contact_photo_version.current?.let { return it.value }
    val version by ContactPhotoDirectory.version.collectAsStateWithLifecycle()
    return version
}
