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
package org.astermail.android.mail.extraction

import android.content.Context
import android.content.SharedPreferences

private const val receipt_review_store_pref_key = "store"
private const val receipt_banner_collapsed_pref_key = "receipt_banner_collapsed"

private fun receipt_review_prefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(receipt_review_store_key, Context.MODE_PRIVATE)

private class shared_prefs_receipt_review_storage(private val prefs: SharedPreferences) : receipt_review_storage {
    override fun read(): String? = prefs.getString(receipt_review_store_pref_key, null)

    override fun write(value: String) {
        prefs.edit().putString(receipt_review_store_pref_key, value).apply()
    }
}

fun receipt_review_store_for(context: Context): receipt_review_store =
    receipt_review_store(shared_prefs_receipt_review_storage(receipt_review_prefs(context)))

fun read_receipt_banner_collapsed(context: Context): Boolean =
    receipt_review_prefs(context).getBoolean(receipt_banner_collapsed_pref_key, false)

fun write_receipt_banner_collapsed(context: Context, collapsed: Boolean) {
    receipt_review_prefs(context).edit().putBoolean(receipt_banner_collapsed_pref_key, collapsed).apply()
}
