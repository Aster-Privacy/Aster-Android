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

package org.astermail.android.mail.ratchet

import java.util.Locale
import org.astermail.android.mail.is_aster_domain_recipient

enum class PeerKind {
    BUILTIN,
    HOSTED,
    EXTERNAL,
}

data class PinContact(
    val id: String,
    val kind: PeerKind,
    val spellings: List<String>,
)

object PinIdentity {

    private fun split_address(lowered: String): Pair<String, String>? {
        val at = lowered.lastIndexOf('@')
        if (at == -1) return null
        return lowered.substring(0, at) to lowered.substring(at + 1)
    }

    private fun base_local_part(local: String): String {
        val plus = local.indexOf('+')
        return if (plus == -1) local else local.substring(0, plus)
    }

    fun peer_kind(raw: String): PeerKind =
        if (is_aster_domain_recipient(raw)) PeerKind.BUILTIN else PeerKind.EXTERNAL

    fun canonical_id(raw: String, kind: PeerKind): String {
        val lowered = raw.trim().lowercase(Locale.ROOT)
        val parts = split_address(lowered)
        if (parts == null || kind == PeerKind.EXTERNAL) return lowered
        val base = base_local_part(parts.first)
        val local = if (kind == PeerKind.BUILTIN) base.replace(".", "") else base
        if (local.isEmpty() || parts.second.isEmpty()) return lowered
        return "$local@${parts.second}"
    }

    fun canonical_id(raw: String): String = canonical_id(raw, peer_kind(raw))

    private fun stored_spellings(raw: String, canonical: String): List<String> {
        val untrimmed = raw.lowercase(Locale.ROOT)
        val lowered = untrimmed.trim()
        val variants = mutableListOf(canonical, lowered, untrimmed)
        val parts = split_address(lowered)
        if (parts != null) {
            val base = base_local_part(parts.first)
            if (base.isNotEmpty() && parts.second.isNotEmpty()) {
                val tag = parts.first.substring(base.length)
                val dotless = base.replace(".", "")
                if (tag.isNotEmpty() && dotless.isNotEmpty()) variants.add("$dotless$tag@${parts.second}")
                variants.add("$base@${parts.second}")
            }
        }
        return variants.distinct().filter { it.isNotEmpty() }
    }

    fun describe(raw: String, kind: PeerKind): PinContact {
        val id = canonical_id(raw, kind)
        val spellings = if (kind == PeerKind.EXTERNAL) {
            listOf(id, raw.lowercase(Locale.ROOT)).distinct().filter { it.isNotEmpty() }
        } else {
            stored_spellings(raw, id)
        }
        return PinContact(id, kind, spellings)
    }

    fun describe(raw: String): PinContact = describe(raw, peer_kind(raw))

    fun dotted_spellings(canonical: String, limit: Int): List<String> {
        val parts = split_address(canonical) ?: return emptyList()
        val letters = parts.first
        if (letters.length < 2 || limit <= 0) return emptyList()
        val gaps = letters.length - 1
        val spellings = mutableListOf<String>()

        fun spell(dots: List<Int>): String {
            val out = StringBuilder()
            letters.forEachIndexed { index, letter ->
                out.append(letter)
                if (index in dots) out.append('.')
            }
            return out.append('@').append(parts.second).toString()
        }

        fun place(remaining: Int, start: Int, dots: List<Int>) {
            if (spellings.size >= limit) return
            if (remaining == 0) {
                spellings.add(spell(dots))
                return
            }
            var gap = start
            while (gap <= gaps - remaining) {
                place(remaining - 1, gap + 1, dots + gap)
                if (spellings.size >= limit) return
                gap += 1
            }
        }

        var count = 1
        while (count <= gaps && spellings.size < limit) {
            place(count, 0, emptyList())
            count += 1
        }
        return spellings
    }
}
