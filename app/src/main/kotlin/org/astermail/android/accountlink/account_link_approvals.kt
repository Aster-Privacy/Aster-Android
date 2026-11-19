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

package org.astermail.android.accountlink

import java.security.SecureRandom

data class AccountLinkRequest(
    val code: String,
    val account_id: String,
    val binding_tag: String,
    val caller: String,
    val peer_envelope_versions: List<Int>? = null,
)

data class AccountLinkPrompt(
    val caller_label: String,
    val account_email: String,
)

class AccountLinkApprovals(
    private val now_ms: () -> Long = System::currentTimeMillis,
    private val ttl_ms: Long = DEFAULT_TTL_MS,
    private val random: SecureRandom = SecureRandom(),
) {
    private class Entry(
        val request: AccountLinkRequest,
        val prompt: AccountLinkPrompt,
        val opened_at: Long,
        var approved: Boolean = false,
    )

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun open(request: AccountLinkRequest, prompt: AccountLinkPrompt): String {
        drop_expired()
        entries.entries.removeAll { it.value.request == request }
        while (entries.size >= MAX_OPEN) entries.remove(entries.keys.first())
        val id = new_id()
        entries[id] = Entry(request, prompt, now_ms())
        return id
    }

    @Synchronized
    fun prompt_for(id: String): AccountLinkPrompt? {
        drop_expired()
        return entries[id]?.takeIf { !it.approved }?.prompt
    }

    @Synchronized
    fun approve(id: String): Boolean {
        drop_expired()
        val entry = entries[id] ?: return false
        entry.approved = true
        return true
    }

    @Synchronized
    fun decline(id: String) {
        entries.remove(id)
    }

    @Synchronized
    fun consume(request: AccountLinkRequest): Boolean {
        drop_expired()
        val id = entries.entries.firstOrNull { it.value.approved && it.value.request == request }?.key
            ?: return false
        entries.remove(id)
        return true
    }

    private fun drop_expired() {
        val now = now_ms()
        entries.entries.removeAll { now - it.value.opened_at !in 0..ttl_ms }
    }

    private fun new_id(): String {
        val bytes = ByteArray(ID_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_TTL_MS = 5L * 60L * 1000L
        private const val MAX_OPEN = 8
        private const val ID_BYTES = 16

        val shared = AccountLinkApprovals()
    }
}
