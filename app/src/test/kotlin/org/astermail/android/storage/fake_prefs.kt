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

package org.astermail.android.storage

import android.content.SharedPreferences

class fake_prefs(
    private val commit_result: Boolean = true,
    private val read_error: Boolean = false,
) : SharedPreferences {

    val values = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int {
        if (read_error) throw IllegalStateException("prefs unreadable")
        return values[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (read_error) throw IllegalStateException("prefs unreadable")
        return values[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean {
        if (read_error) throw IllegalStateException("prefs unreadable")
        return values.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = fake_editor(this, commit_result)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}

class fake_editor(
    private val owner: fake_prefs,
    private val commit_result: Boolean,
) : SharedPreferences.Editor {

    private val staged = HashMap<String, Any?>()
    private val removed = HashSet<String>()
    private var cleared = false

    override fun putString(key: String?, value: String?): SharedPreferences.Editor = stage(key, value)

    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
        stage(key, values)

    override fun putInt(key: String?, value: Int): SharedPreferences.Editor = stage(key, value)

    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = stage(key, value)

    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = stage(key, value)

    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = stage(key, value)

    override fun remove(key: String?): SharedPreferences.Editor {
        key?.let { removed.add(it) }
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        cleared = true
        return this
    }

    override fun commit(): Boolean {
        if (!commit_result) return false
        if (cleared) owner.values.clear()
        for (key in removed) owner.values.remove(key)
        owner.values.putAll(staged)
        return true
    }

    override fun apply() {
        commit()
    }

    private fun stage(key: String?, value: Any?): SharedPreferences.Editor {
        key?.let { staged[it] = value }
        return this
    }
}
