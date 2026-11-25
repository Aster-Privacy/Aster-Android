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

package org.astermail.android.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.api.settings.DomainAddressWebsitesRow

internal const val ALIAS_WEBSITES_MAX_COUNT = 10

private val WEBSITES_INPUT_SEPARATORS = Regex("[,;\n]")

internal fun split_alias_websites_input(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.split(WEBSITES_INPUT_SEPARATORS)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { normalize_alias_website_url(it) }
        .distinct()
        .take(ALIAS_WEBSITES_MAX_COUNT)
}

internal fun serialize_alias_websites_payload(websites: List<String>): String =
    JsonArray(websites.map { JsonPrimitive(it) }).toString()

internal fun parse_alias_websites_payload(raw: String): List<String> {
    val parsed = try {
        Json.parseToJsonElement(raw)
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        null
    }
    val entries = if (parsed is JsonArray) {
        parsed.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    } else {
        raw.split(WEBSITES_INPUT_SEPARATORS)
    }
    return entries
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { normalize_alias_website_url(it) }
        .distinct()
        .take(ALIAS_WEBSITES_MAX_COUNT)
}

internal fun decrypt_domain_address_websites(
    encrypted_websites: String?,
    websites_nonce: String?,
    decrypt: (String, String) -> String,
): List<String> {
    if (encrypted_websites.isNullOrBlank() || websites_nonce.isNullOrBlank()) return emptyList()
    return try {
        parse_alias_websites_payload(decrypt(encrypted_websites, websites_nonce))
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        emptyList()
    }
}

internal fun apply_domain_address_websites(
    addresses: List<CustomDomainAddressInfo>,
    rows_by_domain: Map<String, List<DomainAddressWebsitesRow>>,
    previous: List<CustomDomainAddressInfo>,
    domain_id_of: (CustomDomainAddressInfo) -> String?,
    decrypt: (String, String) -> String,
): List<CustomDomainAddressInfo> {
    val previous_by_id = previous.associateBy { it.id }
    return addresses.map { addr ->
        if (addr.decryption_failed) return@map addr
        val rows = domain_id_of(addr)?.let { rows_by_domain[it] }
        if (rows == null) {
            val prior = previous_by_id[addr.id] ?: return@map addr
            return@map addr.copy(
                encrypted_websites = prior.encrypted_websites,
                websites_nonce = prior.websites_nonce,
                websites = prior.websites,
                websites_loaded = prior.websites_loaded,
            )
        }
        val row = rows.firstOrNull { it.id == addr.id }
        addr.copy(
            encrypted_websites = row?.encrypted_websites,
            websites_nonce = row?.websites_nonce,
            websites = decrypt_domain_address_websites(row?.encrypted_websites, row?.websites_nonce, decrypt),
            websites_loaded = true,
        )
    }
}
