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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.FolderRowDao

private const val folder_cache_meta_prefs = "aster_folder_cache_meta"
private const val folder_cache_layout_key = "layout_signature"

@Singleton
class FolderCacheStore @Inject constructor(
    private val db_provider: dagger.Lazy<AsterDatabase>,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    private val dao: FolderRowDao by lazy { db_provider.get().folder_row_dao() }

    private val meta: android.content.SharedPreferences? by lazy {
        runCatching { context.getSharedPreferences(folder_cache_meta_prefs, android.content.Context.MODE_PRIVATE) }
            .getOrNull()
    }

    fun layout_signature(): String? =
        runCatching { meta?.getString(folder_cache_layout_key, null) }.getOrNull()

    fun set_layout_signature(signature: String) {
        runCatching { meta?.edit()?.putString(folder_cache_layout_key, signature)?.apply() }
    }

    suspend fun rows(folder: String, limit: Int = folder_cache_row_limit): List<InboxItem> =
        withContext(Dispatchers.IO) {
            runCatching { dao.rows_for_folder(folder, limit).map { it.to_inbox_item() } }.getOrDefault(emptyList())
        }

    suspend fun save(folder: String, items: List<InboxItem>, cached_at: Long) {
        withContext(Dispatchers.IO) {
            runCatching {
                val rows = folder_cache_rows(folder, items, cached_at)
                val stale = folder_cache_prune_ids(dao.ids_for_folder(folder), rows.map { it.id })
                dao.replace_folder(folder, rows, stale)
            }
        }
    }

    suspend fun clear_folder(folder: String) {
        withContext(Dispatchers.IO) { runCatching { dao.clear_folder(folder) } }
    }

    suspend fun clear_all() {
        withContext(Dispatchers.IO) { runCatching { dao.clear_all() } }
    }
}
