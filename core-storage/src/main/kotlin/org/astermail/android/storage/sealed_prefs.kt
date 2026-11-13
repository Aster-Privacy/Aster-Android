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

class SealedPrefs(
    private val delegate: SharedPreferences,
    private val store: String,
    private val is_plain_name: (String) -> Boolean = { false },
) : SharedPreferences {

    fun has_sealed_values(): Boolean =
        runCatching { delegate.all.values.any { LockSeal.is_sealed(it as? String) } }.getOrDefault(false)

    fun drops_write(name: String): Boolean = !LockKeyHolder.is_open() && holds_sealed_value(name)

    fun has_unsealed_secrets(): Boolean =
        runCatching {
            delegate.all.any { (name, value) ->
                value is String && !is_plain_name(name) && !LockSeal.is_sealed(value)
            }
        }.getOrDefault(false)

    fun seal_all(): Boolean = LockKeyHolder.with_key { key ->
        if (key == null) return@with_key false
        val editor = delegate.edit()
        var changed = false
        for ((name, value) in delegate.all) {
            if (value !is String || is_plain_name(name) || LockSeal.is_sealed(value)) continue
            editor.putString(name, LockSeal.seal(key, store, name, value))
            changed = true
        }
        !changed || editor.commit()
    }

    fun unseal_all(lock_key: ByteArray? = null): Boolean =
        if (lock_key != null) unseal_with(lock_key) else LockKeyHolder.with_key { unseal_with(it) }

    private fun unseal_with(key: ByteArray?): Boolean {
        if (key == null) return !has_sealed_values()
        val editor = delegate.edit()
        var changed = false
        for ((name, value) in delegate.all) {
            if (value !is String || !LockSeal.is_sealed(value)) continue
            val opened = LockSeal.open(key, store, name, value) ?: return false
            editor.putString(name, opened)
            changed = true
        }
        return !changed || editor.commit()
    }

    override fun getAll(): MutableMap<String, *> {
        val out = HashMap<String, Any?>()
        LockKeyHolder.with_key { key ->
            for ((name, value) in delegate.all) {
                if (value is String && LockSeal.is_sealed(value)) {
                    val opened = key?.let { LockSeal.open(it, store, name, value) }
                    if (opened != null) out[name] = opened
                } else {
                    out[name] = value
                }
            }
        }
        return out
    }

    override fun getString(key: String?, defValue: String?): String? {
        val name = key ?: return defValue
        val raw = delegate.getString(name, null) ?: return defValue
        if (!LockSeal.is_sealed(raw)) return raw
        return LockKeyHolder.with_key { lock_key ->
            lock_key?.let { LockSeal.open(it, store, name, raw) }
        } ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        delegate.getStringSet(key, defValues)

    override fun getInt(key: String?, defValue: Int): Int = delegate.getInt(key, defValue)

    override fun getLong(key: String?, defValue: Long): Long = delegate.getLong(key, defValue)

    override fun getFloat(key: String?, defValue: Float): Float = delegate.getFloat(key, defValue)

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = delegate.getBoolean(key, defValue)

    override fun contains(key: String?): Boolean = delegate.contains(key)

    override fun edit(): SharedPreferences.Editor = Editor(delegate.edit())

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = delegate.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = delegate.unregisterOnSharedPreferenceChangeListener(listener)

    private fun holds_sealed_value(name: String): Boolean =
        runCatching { LockSeal.is_sealed(delegate.getString(name, null)) }.getOrDefault(false)

    private inner class Editor(private val inner: SharedPreferences.Editor) : SharedPreferences.Editor {

        private var cleared = false
        private val removed = HashSet<String>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key == null || is_plain_name(key)) {
                inner.putString(key, value)
                return@apply
            }
            val sealed = value?.let { plain ->
                LockKeyHolder.with_key { lock_key -> lock_key?.let { LockSeal.seal(it, store, key, plain) } }
            }
            if (sealed != null) {
                inner.putString(key, sealed)
            } else if (LockKeyHolder.is_open() || cleared || key in removed || !holds_sealed_value(key)) {
                inner.putString(key, value)
            }
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { inner.putStringSet(key, values) }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { inner.putInt(key, value) }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { inner.putLong(key, value) }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            apply { inner.putFloat(key, value) }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            apply { inner.putBoolean(key, value) }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) removed.add(key)
            inner.remove(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            cleared = true
            inner.clear()
        }

        override fun commit(): Boolean = inner.commit()

        override fun apply() = inner.apply()
    }
}
