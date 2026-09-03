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

package org.astermail.android.ui.compose

import android.content.Context
import android.content.SharedPreferences
import org.astermail.android.mail.ThreadUiState
import org.astermail.android.mail.is_sendable_address
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.ui.common.resolve_primary_sender_email
import org.astermail.android.ui.mail.extract_delivered_to
import org.json.JSONArray
import org.json.JSONObject

object compose_seed_store {

    private const val prefs_name = "aster_compose_seed"
    private const val identity_key = "identity_json"
    private const val max_threads = 6

    @Volatile
    private var identity_memo: compose_identity_snapshot? = null

    private val thread_memo = object : LinkedHashMap<String, compose_thread_snapshot>(
        max_threads,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, compose_thread_snapshot>?,
        ): Boolean = size > max_threads
    }

    private fun prefs(context: Context): SharedPreferences? =
        runCatching { SecurePrefs.open(context.applicationContext, prefs_name) }.getOrNull()

    fun read_identity(
        context: Context,
        expected_user_email: String = "",
    ): compose_identity_snapshot {
        val stored = identity_memo ?: run {
            val payload = runCatching { prefs(context)?.getString(identity_key, null) }.getOrNull()
                ?: return compose_identity_snapshot()
            val decoded = runCatching { decode_identity(payload) }.getOrNull()
                ?: return compose_identity_snapshot()
            identity_memo = decoded
            decoded
        }
        if (expected_user_email.isNotBlank() &&
            !stored.user_email.equals(expected_user_email, ignoreCase = true)
        ) {
            return compose_identity_snapshot()
        }
        return stored
    }

    fun publish_identity(context: Context, snapshot: compose_identity_snapshot) {
        if (!snapshot.is_ready) return
        val merged = merge_identity(read_identity(context), snapshot)
        if (identity_memo == merged) return
        identity_memo = merged
        runCatching {
            prefs(context)?.edit()?.putString(identity_key, encode_identity(merged))?.apply()
        }
    }

    private fun merge_identity(
        cached: compose_identity_snapshot,
        fresh: compose_identity_snapshot,
    ): compose_identity_snapshot {
        if (!cached.is_ready) return fresh
        if (!cached.user_email.equals(fresh.user_email, ignoreCase = true)) return fresh
        val options = fresh.alias_options.toMutableList()
        cached.alias_options.forEach { if (it !in options) options.add(it) }
        val ghosts = fresh.ghost_addresses.toMutableList()
        cached.ghost_addresses.forEach { if (it !in ghosts) ghosts.add(it) }
        val names = cached.alias_display_names.toMutableMap()
        names.putAll(fresh.alias_display_names)
        return fresh.copy(
            display_name = fresh.display_name.ifBlank { cached.display_name },
            alias_options = options.toList(),
            primary_sender_email = fresh.primary_sender_email.ifBlank { cached.primary_sender_email },
            alias_display_names = names.toMap(),
            ghost_addresses = ghosts.toList(),
        )
    }

    fun publish_thread(snapshot: compose_thread_snapshot) {
        if (snapshot.messages.isEmpty()) return
        synchronized(thread_memo) {
            snapshot.item_id?.takeIf { it.isNotBlank() }?.let { thread_memo[it] = snapshot }
            snapshot.messages.forEach { message ->
                if (message.id.isNotBlank()) thread_memo[message.id] = snapshot
            }
        }
    }

    fun read_thread(target_id: String?): compose_thread_snapshot {
        val key = target_id?.trim().orEmpty()
        if (key.isEmpty()) return compose_thread_snapshot()
        return synchronized(thread_memo) { thread_memo[key] } ?: compose_thread_snapshot()
    }

    fun clear(context: Context) {
        identity_memo = null
        synchronized(thread_memo) { thread_memo.clear() }
        runCatching { prefs(context)?.edit()?.clear()?.apply() }
    }

    private fun encode_identity(snapshot: compose_identity_snapshot): String {
        val names = JSONObject()
        snapshot.alias_display_names.forEach { (address, name) -> names.put(address, name) }
        return JSONObject()
            .put("user_email", snapshot.user_email)
            .put("display_name", snapshot.display_name)
            .put("primary_sender_email", snapshot.primary_sender_email)
            .put("alias_options", JSONArray(snapshot.alias_options))
            .put("ghost_addresses", JSONArray(snapshot.ghost_addresses))
            .put("alias_display_names", names)
            .toString()
    }

    private fun decode_identity(payload: String): compose_identity_snapshot {
        val root = JSONObject(payload)
        val names = root.optJSONObject("alias_display_names")
        val name_map = mutableMapOf<String, String>()
        if (names != null) {
            val keys = names.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = names.optString(key).orEmpty()
                if (key.isNotBlank() && value.isNotBlank()) name_map[key] = value
            }
        }
        return compose_identity_snapshot(
            user_email = root.optString("user_email").orEmpty(),
            display_name = root.optString("display_name").orEmpty(),
            alias_options = string_list(root.optJSONArray("alias_options")),
            primary_sender_email = root.optString("primary_sender_email").orEmpty(),
            alias_display_names = name_map.toMap(),
            ghost_addresses = string_list(root.optJSONArray("ghost_addresses")),
        )
    }

    private fun string_list(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).orEmpty().trim()
            if (value.isNotBlank()) values.add(value)
        }
        return values.toList()
    }
}

fun thread_snapshot_from(state: ThreadUiState): compose_thread_snapshot {
    if (state.messages.isEmpty()) return compose_thread_snapshot()
    return compose_thread_snapshot(
        item_id = state.item?.id,
        item_subject = state.item?.subject.orEmpty(),
        messages = state.messages.map { message ->
            compose_thread_message(
                id = message.id,
                sender_email = message.sender_email,
                display_sender_email = message.display_sender_email,
                to_addresses = message.to_addresses,
                cc_addresses = message.cc_addresses,
                subject = message.subject,
                body_html = message.body_html.orEmpty(),
                body_text = message.body_text,
                timestamp = message.timestamp,
                raw_headers = message.raw_headers,
                delivered_to = extract_delivered_to(message.raw_headers),
                is_sent = message.raw_item.item_type == "sent",
            )
        },
    )
}

fun publish_compose_thread_seed(state: ThreadUiState) {
    compose_seed_store.publish_thread(thread_snapshot_from(state))
}

fun identity_snapshot_from(
    state: SettingsUiState,
    fallback_user_email: String = "",
): compose_identity_snapshot {
    val user_email = state.user?.email?.takeIf { it.isNotBlank() } ?: fallback_user_email
    val options = mutableListOf<String>()
    if (user_email.isNotBlank()) options.add(user_email)
    state.aliases
        .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
        .forEach { if (it.address !in options) options.add(it.address) }
    state.custom_domain_addresses
        .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
        .forEach { if (it.address !in options) options.add(it.address) }
    state.ghost_aliases
        .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
        .forEach { if (it.address !in options) options.add(it.address) }
    val names = mutableMapOf<String, String>()
    state.aliases.forEach { alias ->
        val name = alias.encrypted_display_name?.trim().orEmpty()
        if (alias.address.isNotBlank() && name.isNotBlank()) names[alias.address] = name
    }
    state.custom_domain_addresses.forEach { address ->
        val name = address.encrypted_display_name?.trim().orEmpty()
        if (address.address.isNotBlank() && name.isNotBlank()) names[address.address] = name
    }
    return compose_identity_snapshot(
        user_email = user_email,
        display_name = state.user?.display_name.orEmpty(),
        alias_options = options.toList(),
        primary_sender_email = resolve_primary_sender_email(
            state.default_sender_id,
            user_email,
            state.aliases,
            state.ghost_aliases,
            state.custom_domain_addresses,
        ),
        alias_display_names = names.toMap(),
        ghost_addresses = state.ghost_aliases.map { it.address }.filter { it.isNotBlank() },
    )
}

fun publish_compose_identity_seed(
    context: Context,
    state: SettingsUiState,
    fallback_user_email: String = "",
) {
    if (state.user == null) return
    val snapshot = identity_snapshot_from(state, fallback_user_email)
    val merged = if (!state.default_sender_loaded) {
        val cached = compose_seed_store.read_identity(context).primary_sender_email
        if (cached.isNotBlank() && cached in snapshot.alias_options) {
            snapshot.copy(primary_sender_email = cached)
        } else {
            snapshot
        }
    } else {
        snapshot
    }
    compose_seed_store.publish_identity(context, merged)
}
