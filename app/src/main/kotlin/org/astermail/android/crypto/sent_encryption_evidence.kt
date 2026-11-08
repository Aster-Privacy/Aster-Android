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


package org.astermail.android.crypto

import org.json.JSONObject

data class SentEncryptionEvidence(
    val outside: Int,
    val outside_pgp: Int,
    val internal_readable: Int,
)

enum class SentEncryptionScope { INTERNAL, OUTSIDE }

object SentEncryptionEvidenceRules {
    const val FIELD = "sent_encryption"
    private const val MAX_RECIPIENT_COUNT = 100_000

    fun normalize_address(value: String): String {
        val trimmed = value.trim()
        val open = trimmed.lastIndexOf('<')
        val close = trimmed.lastIndexOf('>')
        val address = if (open != -1 && close > open) trimmed.substring(open + 1, close) else trimmed
        return address.trim().lowercase()
    }

    private fun unique_addresses(values: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (value in values) {
            val address = normalize_address(value)
            if (address.isNotEmpty()) seen.add(address)
        }
        return seen.toList()
    }

    fun build(
        all_recipients: List<String>,
        outside_recipients: List<String>,
        client_copies: Map<String, String>?,
        internal_copy_is_encrypted: Boolean,
    ): SentEncryptionEvidence {
        val all = unique_addresses(all_recipients)
        val outside = unique_addresses(outside_recipients)
        val outside_set = outside.toHashSet()
        val copied = client_copies.orEmpty()
            .filter { it.value.isNotEmpty() }
            .keys
            .map { normalize_address(it) }
            .toHashSet()
        val internal_count = all.count { it !in outside_set }
        return SentEncryptionEvidence(
            outside = outside.size,
            outside_pgp = outside.count { it in copied },
            internal_readable = if (internal_copy_is_encrypted) 0 else internal_count,
        )
    }

    fun to_json(evidence: SentEncryptionEvidence): JSONObject = JSONObject().apply {
        put("outside", evidence.outside)
        put("outside_pgp", evidence.outside_pgp)
        put("internal_readable", evidence.internal_readable)
    }

    fun with_evidence(envelope_json: String, evidence: SentEncryptionEvidence?): String {
        if (evidence == null) return envelope_json
        return runCatching {
            JSONObject(envelope_json).put(FIELD, to_json(evidence)).toString()
        }.getOrDefault(envelope_json)
    }

    private fun count_of(value: Any?): Int? {
        val number = value as? Number ?: return null
        val as_double = number.toDouble()
        if (as_double.isNaN() || as_double != Math.floor(as_double)) return null
        if (as_double < 0 || as_double > MAX_RECIPIENT_COUNT) return null
        return as_double.toInt()
    }

    fun read(envelope: JSONObject?): SentEncryptionEvidence? {
        val raw = envelope?.opt(FIELD) as? JSONObject ?: return null
        val outside = count_of(raw.opt("outside")) ?: return null
        val outside_pgp = count_of(raw.opt("outside_pgp")) ?: return null
        val internal_readable = count_of(raw.opt("internal_readable")) ?: return null
        if (outside_pgp > outside) return null
        return SentEncryptionEvidence(outside, outside_pgp, internal_readable)
    }

    fun scope(evidence: SentEncryptionEvidence?): SentEncryptionScope? {
        if (evidence == null) return null
        if (evidence.outside_pgp != evidence.outside || evidence.internal_readable != 0) return null
        return if (evidence.outside > 0) SentEncryptionScope.OUTSIDE else SentEncryptionScope.INTERNAL
    }

    fun scope_from_envelope_json(envelope_json: String): SentEncryptionScope? =
        scope(runCatching { read(JSONObject(envelope_json)) }.getOrNull())

    fun shows_end_to_end(
        scope: SentEncryptionScope?,
        is_external: Boolean,
        has_recipient_key: Boolean,
        e2e_verified: Boolean,
    ): Boolean {
        if (scope != null) return true
        return (!is_external || has_recipient_key) && e2e_verified
    }

    fun reaches_outside_recipients(scope: SentEncryptionScope?, is_external: Boolean): Boolean =
        if (scope != null) scope == SentEncryptionScope.OUTSIDE else is_external
}
