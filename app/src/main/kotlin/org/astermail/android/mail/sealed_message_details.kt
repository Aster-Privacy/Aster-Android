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

package org.astermail.android.mail

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class SealedAddress(val name: String, val email: String)

internal data class SealedMessageDetails(
    val from_name: String,
    val to: List<SealedAddress>,
    val cc: List<SealedAddress>,
    val sent_at: String,
)

internal const val SEALED_DETAILS_VERSION = 1

private const val MAX_SEALED_NAME_LENGTH = 256
private const val MAX_SEALED_EMAIL_LENGTH = 320
private const val MAX_SEALED_ADDRESSES = 1000

private val SEALED_CONTROL_CHARACTERS = Regex("[\u0000-\u001f\u007f]")
private val SEALED_WRAPPING_QUOTES = Regex("^\"(.*)\"$")
private val SEALED_ANGLE_ADDRESS = Regex("^\\s*(.*?)\\s*<([^<>]+)>\\s*$")

private val SEALED_INSTANT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC)

private fun clean_sealed_name(value: String): String {
    val unquoted = SEALED_CONTROL_CHARACTERS.replace(value, " ").trim()
        .replace(SEALED_WRAPPING_QUOTES, "$1")
        .trim()
    return unquoted.take(MAX_SEALED_NAME_LENGTH)
}

private fun to_sealed_address(entry: String): SealedAddress? {
    val angled = SEALED_ANGLE_ADDRESS.find(entry)
    val email = (angled?.groupValues?.get(2) ?: entry).trim()
    if (email.isEmpty() || email.length > MAX_SEALED_EMAIL_LENGTH) return null
    return SealedAddress(angled?.let { clean_sealed_name(it.groupValues[1]) }.orEmpty(), email)
}

private fun to_sealed_addresses(entries: List<String>): List<SealedAddress> =
    entries.mapNotNull { to_sealed_address(it) }.take(MAX_SEALED_ADDRESSES)

private fun parse_sealed_instant(value: String): Instant? {
    val text = value.trim()
    if (text.isEmpty()) return null
    return runCatching { Instant.parse(text) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
}

private fun format_sealed_instant(instant: Instant): String = SEALED_INSTANT_FORMAT.format(instant)

internal fun sealed_sender_name(
    chosen_name: String?,
    sender_alias_hash: String?,
    account_name: String?,
): String {
    if (!chosen_name.isNullOrEmpty()) return chosen_name
    return if (sender_alias_hash.isNullOrEmpty()) account_name.orEmpty() else ""
}

internal fun build_sealed_details(
    from_name: String?,
    to: List<String>,
    cc: List<String>,
    sent_at: String? = null,
    now: Instant = Instant.now(),
): SealedMessageDetails = SealedMessageDetails(
    from_name = clean_sealed_name(from_name.orEmpty()),
    to = to_sealed_addresses(to),
    cc = to_sealed_addresses(cc),
    sent_at = format_sealed_instant(sent_at?.let { parse_sealed_instant(it) } ?: now),
)

private fun serialize_sealed_addresses(addresses: List<SealedAddress>): JSONArray {
    val result = JSONArray()
    for (address in addresses) {
        result.put(JSONObject().apply {
            put("name", address.name)
            put("email", address.email)
        })
    }
    return result
}

internal fun serialize_sealed_details(details: SealedMessageDetails): JSONObject = JSONObject().apply {
    put("v", SEALED_DETAILS_VERSION)
    put("from_name", details.from_name)
    put("to", serialize_sealed_addresses(details.to))
    put("cc", serialize_sealed_addresses(details.cc))
    put("sent_at", details.sent_at)
}

private fun parse_sealed_addresses(value: Any?): List<SealedAddress>? {
    if (value !is JSONArray || value.length() > MAX_SEALED_ADDRESSES) return null
    val addresses = ArrayList<SealedAddress>(value.length())
    for (index in 0 until value.length()) {
        val entry = value.opt(index) as? JSONObject ?: return null
        val email = entry.opt("email") as? String ?: return null
        if (email.isBlank() || email.length > MAX_SEALED_EMAIL_LENGTH) return null
        val name = when (val raw_name = entry.opt("name")) {
            null -> ""
            is String -> raw_name
            else -> return null
        }
        addresses.add(SealedAddress(clean_sealed_name(name), email.trim()))
    }
    return addresses
}

internal fun parse_sealed_details(value: Any?): SealedMessageDetails? {
    if (value !is JSONObject) return null
    val version = value.opt("v") as? Number ?: return null
    if (version.toDouble() != SEALED_DETAILS_VERSION.toDouble()) return null
    val from_name = value.opt("from_name") as? String ?: return null
    val sent_at = value.opt("sent_at") as? String ?: return null
    val instant = parse_sealed_instant(sent_at) ?: return null
    val to = parse_sealed_addresses(value.opt("to")) ?: return null
    val cc = parse_sealed_addresses(value.opt("cc")) ?: return null
    return SealedMessageDetails(
        from_name = clean_sealed_name(from_name),
        to = to,
        cc = cc,
        sent_at = format_sealed_instant(instant),
    )
}

internal fun build_subject_bundle_text(
    subject: String,
    body: String,
    details: SealedMessageDetails?,
): String {
    val existing = extract_subject_bundle(body)
    return ASTER_SUBJECT_BUNDLE_PREFIX + JSONObject().apply {
        put("s", subject.ifBlank { existing.subject.orEmpty() })
        put("b", existing.body)
        if (details != null) put("details", serialize_sealed_details(details))
    }.toString()
}

internal fun carries_sealed_details(
    ratchet_decrypted: Boolean,
    is_unauthenticated: Boolean,
    is_unconfirmed_sender: Boolean,
): Boolean = ratchet_decrypted && !is_unauthenticated && !is_unconfirmed_sender

internal fun DecryptedEnvelope.with_sealed_details(details: SealedMessageDetails?): DecryptedEnvelope {
    if (details == null) return this
    return copy(
        from_name = details.from_name.ifBlank { from_email.substringBefore('@').ifBlank { from_email } },
        to = details.to.map { it.name to it.email },
        cc = details.cc.map { it.name to it.email },
        sent_at = details.sent_at,
        details_sealed = true,
    )
}
