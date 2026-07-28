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

class InMemoryPrefs : SharedPreferences {

    private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val listeners = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>(),
    )

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listener?.let { listeners.remove(it) }
    }

    private fun notify_change(key: String?) {
        for (listener in listeners) {
            runCatching { listener.onSharedPreferenceChanged(this, key) }
        }
    }

    private inner class Editor : SharedPreferences.Editor {

        private val pending = linkedMapOf<String, Any?>()
        private var clear_requested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { pending[key] = values?.toSet() }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }

        override fun clear(): SharedPreferences.Editor = apply { clear_requested = true }

        override fun commit(): Boolean {
            val changed = mutableListOf<String?>()
            if (clear_requested) {
                val cleared = values.keys.toList()
                values.clear()
                changed.addAll(cleared)
            }
            for ((key, value) in pending) {
                if (value == null) values.remove(key) else values[key] = value
                changed.add(key)
            }
            pending.clear()
            clear_requested = false
            for (key in changed) notify_change(key)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
