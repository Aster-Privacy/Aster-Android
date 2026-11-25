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

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

data class ArchivedBlob(
    val key: String,
    val kind: String,
    val target: String,
    val payload: String,
    val archived_ms: Long,
    val replaced_with_blank: Boolean = false,
    val baseline: String = "",
)

class PreferencesCacheStore(context: Context? = null, backing: SharedPreferences? = null) {

    private val prefs: SharedPreferences? = backing ?: context?.let {
        runCatching { SecurePrefs.open(it, prefs_name) }.getOrNull()
    }

    fun read(account_key: String?): String? {
        val key = entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write(account_key: String?, payload: String) {
        val key = entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun archive_unreadable(
        account_key: String?,
        kind: String,
        payload: String,
        target: String = "",
        now_ms: Long = System.currentTimeMillis(),
        replaced_with_blank: Boolean = false,
        baseline: String = "",
    ): Boolean {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty() || kind.isEmpty() || payload.isEmpty()) return false
        if (listOf(id, kind, target, baseline).any { it.contains(archive_separator) }) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(listOf(id, kind, target, payload).joinToString(archive_separator).toByteArray(Charsets.UTF_8))
        val key = archive_key_prefix + digest.joinToString("") { "%02x".format(it) }
        val store = prefs ?: return false
        return synchronized(archive_lock) {
            runCatching {
                val entries = archived_entries(store, id)
                if (store.contains(key)) {
                    val existing = entries.firstOrNull { it.key == key } ?: return@runCatching true
                    val rebased = baseline.isNotEmpty() && baseline != existing.baseline
                    if (existing.replaced_with_blank == replaced_with_blank && !rebased) return@runCatching true
                    val updated = if (rebased) {
                        existing.copy(replaced_with_blank = replaced_with_blank, baseline = baseline, archived_ms = now_ms)
                    } else {
                        existing.copy(replaced_with_blank = replaced_with_blank)
                    }
                    return@runCatching store.edit().putString(key, archive_value(updated, id)).commit()
                }
                val same_target = entries.filter { it.kind == kind && it.target == target }
                if (same_target.isEmpty() && entries.map { it.kind to it.target }.toSet().size >= max_archived_targets) {
                    val refused_key = refused_entry_key(id)
                    store.edit().putInt(refused_key, store.getInt(refused_key, 0) + 1).commit()
                    return@runCatching false
                }
                val editor = store.edit()
                same_target
                    .sortedWith(compareBy<ArchivedBlob>({ it.archived_ms }, { it.key }))
                    .dropLast(max_versions_per_target - 1)
                    .forEach { editor.remove(it.key) }
                val entry = ArchivedBlob(key, kind, target, payload, now_ms, replaced_with_blank, baseline)
                editor.putString(key, archive_value(entry, id)).commit()
            }.getOrDefault(false)
        }
    }

    fun list_archived(account_key: String?, kind: String): List<ArchivedBlob> {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return emptyList()
        val store = prefs ?: return emptyList()
        return runCatching {
            archived_entries(store, id)
                .filter { it.kind == kind }
                .sortedWith(compareBy<ArchivedBlob>({ it.archived_ms }, { it.key }))
        }.getOrDefault(emptyList())
    }

    private fun archive_value(entry: ArchivedBlob, account_id: String): String {
        val stamp = buildString {
            append(entry.archived_ms)
            if (entry.replaced_with_blank) append(blank_marker)
            if (entry.baseline.isNotEmpty()) append(baseline_marker)
        }
        val fields = listOf(stamp, account_id, entry.kind, entry.target)
        val tail = if (entry.baseline.isNotEmpty()) listOf(entry.baseline, entry.payload) else listOf(entry.payload)
        return (fields + tail).joinToString(archive_separator)
    }

    fun archive_refused_count(account_key: String?): Int {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return 0
        return runCatching { prefs?.getInt(refused_entry_key(id), 0) ?: 0 }.getOrDefault(0)
    }

    private fun refused_entry_key(account_id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(account_id.toByteArray(Charsets.UTF_8))
        return archive_refused_prefix + digest.joinToString("") { "%02x".format(it) }
    }

    fun drop_archived(key: String): Boolean {
        if (!key.startsWith(archive_key_prefix)) return false
        val store = prefs ?: return false
        return runCatching { store.edit().remove(key).commit() }.getOrDefault(false)
    }

    private fun archived_entries(store: SharedPreferences, account_id: String): List<ArchivedBlob> =
        store.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(archive_key_prefix)) return@mapNotNull null
            val text = value as? String ?: return@mapNotNull null
            val flags = text.substringBefore(archive_separator).split(flag_separator)
            val archived_ms = flags.first().toLongOrNull() ?: return@mapNotNull null
            val blank = flags.contains(blank_flag)
            val with_baseline = flags.contains(baseline_flag)
            val field_count = if (with_baseline) 6 else 5
            val parts = text.split(archive_separator, limit = field_count)
            if (parts.size != field_count || parts[1] != account_id) return@mapNotNull null
            val baseline = if (with_baseline) parts[4] else ""
            ArchivedBlob(key, parts[2], parts[3], parts.last(), archived_ms, blank, baseline)
        }

    fun read_badges(account_key: String?): String? {
        val key = badge_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_badges(account_key: String?, payload: String) {
        val key = badge_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_badge_preferences(account_key: String?): String? {
        val key = badge_prefs_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_badge_preferences(account_key: String?, payload: String) {
        val key = badge_prefs_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_signatures(account_key: String?): String? {
        val key = signature_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_signatures(account_key: String?, payload: String) {
        val key = signature_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_alias_preferences(account_key: String?): String? {
        val key = alias_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_alias_preferences(account_key: String?, payload: String) {
        val key = alias_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_twin_address(account_key: String?): String? {
        val key = twin_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_twin_address(account_key: String?, payload: String) {
        val key = twin_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_tags(account_key: String?): String? {
        val key = tag_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_tags(account_key: String?, payload: String) {
        val key = tag_entry_key(account_key) ?: return
        val legacy_key = legacy_tag_entry_key(account_key)
        runCatching {
            prefs?.edit()?.apply {
                putString(key, payload)
                if (legacy_key != null) remove(legacy_key)
            }?.apply()
        }
    }

    fun read_storage(account_key: String?): String? {
        val key = storage_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_storage(account_key: String?, payload: String) {
        val key = storage_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_limits(account_key: String?): String? {
        val key = limits_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_limits(account_key: String?, payload: String) {
        val key = limits_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_subscription(account_key: String?): String? {
        val key = subscription_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_subscription(account_key: String?, payload: String) {
        val key = subscription_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_referral(account_key: String?): String? {
        val key = referral_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_referral(account_key: String?, payload: String) {
        val key = referral_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun read_domain_health(account_key: String?): String? {
        val key = domain_health_entry_key(account_key) ?: return null
        return runCatching { prefs?.getString(key, null) }.getOrNull()
    }

    fun write_domain_health(account_key: String?, payload: String) {
        val key = domain_health_entry_key(account_key) ?: return
        runCatching { prefs?.edit()?.putString(key, payload)?.apply() }
    }

    fun clear(account_key: String?) {
        val key = entry_key(account_key) ?: return
        val referral_key = referral_entry_key(account_key)
        val domain_health_key = domain_health_entry_key(account_key)
        val limits_key = limits_entry_key(account_key)
        val subscription_key = subscription_entry_key(account_key)
        val badge_key = badge_entry_key(account_key)
        val badge_prefs_key = badge_prefs_entry_key(account_key)
        val signature_key = signature_entry_key(account_key)
        val alias_key = alias_entry_key(account_key)
        val tag_key = tag_entry_key(account_key)
        val legacy_tag_key = legacy_tag_entry_key(account_key)
        val twin_key = twin_entry_key(account_key)
        val storage_key = storage_entry_key(account_key)
        runCatching {
            prefs?.edit()?.apply {
                remove(key)
                if (badge_key != null) remove(badge_key)
                if (badge_prefs_key != null) remove(badge_prefs_key)
                if (signature_key != null) remove(signature_key)
                if (alias_key != null) remove(alias_key)
                if (tag_key != null) remove(tag_key)
                if (legacy_tag_key != null) remove(legacy_tag_key)
                if (twin_key != null) remove(twin_key)
                if (storage_key != null) remove(storage_key)
                if (limits_key != null) remove(limits_key)
                if (subscription_key != null) remove(subscription_key)
                if (referral_key != null) remove(referral_key)
                if (domain_health_key != null) remove(domain_health_key)
            }?.commit()
        }
    }

    fun clear_all() {
        runCatching {
            val store = prefs ?: return@runCatching
            val editor = store.edit()
            store.all.keys
                .filterNot { it.startsWith(archive_key_prefix) || it.startsWith(archive_refused_prefix) }
                .forEach { editor.remove(it) }
            editor.commit()
        }
    }

    private fun entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$key_prefix$id"
    }

    private fun badge_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$badge_key_prefix$id"
    }

    private fun badge_prefs_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$badge_prefs_key_prefix$id"
    }

    private fun signature_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$signature_key_prefix$id"
    }

    private fun tag_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$tag_key_prefix$id"
    }

    private fun legacy_tag_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$legacy_tag_key_prefix$id"
    }

    private fun alias_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$alias_key_prefix$id"
    }

    private fun twin_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$twin_key_prefix$id"
    }

    private fun limits_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$limits_key_prefix$id"
    }

    private fun storage_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$storage_key_prefix$id"
    }

    private fun referral_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$referral_key_prefix$id"
    }

    private fun domain_health_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$domain_health_key_prefix$id"
    }

    private fun subscription_entry_key(account_key: String?): String? {
        val id = account_key?.trim().orEmpty()
        if (id.isEmpty()) return null
        return "$subscription_key_prefix$id"
    }

    private companion object {
        const val prefs_name = "aster_preferences_cache"
        const val key_prefix = "prefs_json_"
        const val archive_key_prefix = "unreadable_archive_"
        const val archive_separator = "\n"
        const val max_versions_per_target = 16
        const val max_archived_targets = 512
        const val archive_refused_prefix = "archive_refused_"
        const val flag_separator = ";"
        const val blank_flag = "blank"
        const val baseline_flag = "base"
        const val blank_marker = flag_separator + blank_flag
        const val baseline_marker = flag_separator + baseline_flag
        val archive_lock = Any()
        const val badge_key_prefix = "badges_json_"
        const val badge_prefs_key_prefix = "badge_prefs_json_"
        const val signature_key_prefix = "signatures_json_"
        const val alias_key_prefix = "alias_prefs_json_"
        const val tag_key_prefix = "tags_v2_json_"
        const val legacy_tag_key_prefix = "tags_json_"
        const val twin_key_prefix = "twin_address_json_"
        const val storage_key_prefix = "storage_json_"
        const val limits_key_prefix = "plan_limits_json_"
        const val subscription_key_prefix = "subscription_json_"
        const val referral_key_prefix = "referral_json_"
        const val domain_health_key_prefix = "domain_health_json_"
    }
}
