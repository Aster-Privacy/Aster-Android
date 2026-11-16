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

package org.astermail.android.contacts

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.Normalizer
import java.util.Locale

const val SOCIAL_NETWORKS_KEY = "social_networks"
const val SOCIAL_TYPE_OTHER = "other"
const val MAX_SOCIAL_NETWORKS = 50
const val MAX_SOCIAL_VALUE = 500
const val MAX_SOCIAL_LABEL = 50

val KNOWN_SOCIAL_TYPES: List<String> = listOf(
    "twitter", "linkedin", "github", "instagram", "facebook", "mastodon", "bluesky",
)

data class ContactSocial(
    val type: String,
    val value: String,
    val label: String = "",
) {
    val is_custom: Boolean get() = type !in KNOWN_SOCIAL_TYPES

    companion object {
        fun custom(label: String, value: String): ContactSocial = ContactSocial(SOCIAL_TYPE_OTHER, value, label)
    }
}

private fun JSONObject.string_or_empty(key: String): String = opt(key) as? String ?: ""

fun decode_social(obj: JSONObject): ContactSocial? {
    val value = obj.opt("value") as? String ?: return null
    val raw_type = obj.string_or_empty("type").trim()
    val lowered = raw_type.lowercase(Locale.ROOT)
    val type = when {
        lowered in KNOWN_SOCIAL_TYPES -> lowered
        lowered.isEmpty() || lowered == SOCIAL_TYPE_OTHER || lowered == "custom" -> SOCIAL_TYPE_OTHER
        else -> raw_type
    }
    return ContactSocial(type, value, obj.string_or_empty("label").trim())
}

fun decode_socials(arr: JSONArray?): List<ContactSocial> {
    if (arr == null) return emptyList()
    val out = mutableListOf<ContactSocial>()
    for (i in 0 until arr.length()) {
        val entry = arr.opt(i) as? JSONObject ?: continue
        decode_social(entry)?.let { out.add(it) }
    }
    return out
}

fun decode_contact_socials(obj: JSONObject): List<ContactSocial> =
    decode_socials(obj.opt(SOCIAL_NETWORKS_KEY) as? JSONArray)

fun encode_social(social: ContactSocial): JSONObject {
    val obj = JSONObject().put("value", social.value).put("type", social.type)
    if (social.is_custom && social.label.isNotEmpty()) obj.put("label", social.label)
    return obj
}

fun normalize_social(social: ContactSocial): ContactSocial? {
    val value = clean_relation_text(social.value, MAX_SOCIAL_VALUE)
    if (value.isEmpty()) return null
    val label = if (social.is_custom) clean_relation_text(social.label, MAX_SOCIAL_LABEL) else ""
    return ContactSocial(social.type.trim().ifEmpty { SOCIAL_TYPE_OTHER }, value, label)
}

fun normalize_socials(socials: List<ContactSocial>): List<ContactSocial> =
    socials.asSequence().mapNotNull { normalize_social(it) }.take(MAX_SOCIAL_NETWORKS).toList()

fun social_dedupe_key(social: ContactSocial): String {
    val value = Normalizer.normalize(social.value, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    return social.type.lowercase(Locale.ROOT) + "\u0000" + social.label.lowercase(Locale.ROOT) + "\u0000" + value
}

fun merge_socials(first: List<ContactSocial>, second: List<ContactSocial>): List<ContactSocial> {
    val seen = mutableSetOf<String>()
    return normalize_socials(first + second).filter { seen.add(social_dedupe_key(it)) }
}

fun socials_json_array(original: JSONArray?, socials: List<ContactSocial>): JSONArray {
    val originals = mutableListOf<Pair<JSONObject, ContactSocial?>>()
    if (original != null) {
        for (i in 0 until original.length()) {
            val entry = original.opt(i) as? JSONObject ?: continue
            originals.add(entry to decode_social(entry)?.let { normalize_social(it) })
        }
    }
    val used = BooleanArray(originals.size)
    val out = JSONArray()
    for (social in normalize_socials(socials)) {
        val match = originals.indices.firstOrNull { !used[it] && originals[it].second == social }
        if (match != null) {
            used[match] = true
            out.put(originals[match].first)
        } else {
            out.put(encode_social(social))
        }
    }
    return out
}

fun write_contact_socials(obj: JSONObject, socials: List<ContactSocial>?) {
    if (socials == null) return
    val original = obj.opt(SOCIAL_NETWORKS_KEY) as? JSONArray
    if (decode_socials(original) == socials) return
    val arr = socials_json_array(original, socials)
    if (arr.length() == 0) obj.remove(SOCIAL_NETWORKS_KEY) else obj.put(SOCIAL_NETWORKS_KEY, arr)
}

fun safe_http_url(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    if (host.isBlank() || uri.rawUserInfo != null) return null
    return trimmed
}

private val SOCIAL_HANDLE = Regex("^[A-Za-z0-9_.-]{1,100}$")
private val MASTODON_HANDLE = Regex("^@?([A-Za-z0-9_]{1,64})@([A-Za-z0-9.-]{1,253}\\.[A-Za-z]{2,63})$")

fun safe_social_url(social: ContactSocial): String? {
    safe_http_url(social.value)?.let { return it }
    val handle = social.value.trim().removePrefix("@")
    if (social.type == "mastodon") {
        val match = MASTODON_HANDLE.matchEntire(social.value.trim()) ?: return null
        return "https://" + match.groupValues[2] + "/@" + match.groupValues[1]
    }
    if (!SOCIAL_HANDLE.matches(handle)) return null
    return when (social.type) {
        "twitter" -> "https://x.com/$handle"
        "linkedin" -> "https://linkedin.com/in/$handle"
        "github" -> "https://github.com/$handle"
        "instagram" -> "https://instagram.com/$handle"
        "facebook" -> "https://facebook.com/$handle"
        "bluesky" -> if (handle.contains('.')) "https://bsky.app/profile/$handle" else null
        else -> null
    }
}

fun social_matches(social: ContactSocial, type_name: String, folded_query: String, fold: (String) -> String): Boolean {
    if (folded_query.isEmpty()) return false
    return fold(social.value).contains(folded_query) ||
        (social.label.isNotEmpty() && fold(social.label).contains(folded_query)) ||
        fold(type_name).contains(folded_query)
}

fun social_vcard_type_token(label: String): String =
    label.filter { it.isLetterOrDigit() && it.code < 0x80 || it == '-' || it == '_' }

private val URL_STANDARD_TYPES = setOf("home", "work", "pref", "other", "internet", "homepage", "home-page", "x-home", "x-work")

fun social_from_vcard_profile(value: String, tokens: List<String>, ab_label: String?): ContactSocial? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val clean_tokens = tokens.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() && !it.equals("pref", true) }
    clean_tokens.firstOrNull { it.lowercase(Locale.ROOT) in KNOWN_SOCIAL_TYPES }?.let {
        return ContactSocial(it.lowercase(Locale.ROOT), trimmed)
    }
    val label = ab_label?.trim()?.takeIf { it.isNotEmpty() }
        ?: clean_tokens.firstOrNull { !it.equals(SOCIAL_TYPE_OTHER, true) }.orEmpty()
    return ContactSocial.custom(label, trimmed)
}

fun social_from_vcard_url(value: String, tokens: List<String>, ab_label: String?): ContactSocial? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    val clean_tokens = tokens.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
    val label_text = ab_label?.trim().orEmpty()
    if (label_text.isNotEmpty() && !label_text.startsWith("_$!<")) return ContactSocial.custom(label_text, trimmed)
    val custom = clean_tokens.firstOrNull { it.lowercase(Locale.ROOT) !in URL_STANDARD_TYPES } ?: return null
    val lowered = custom.lowercase(Locale.ROOT)
    if (lowered in KNOWN_SOCIAL_TYPES) return ContactSocial(lowered, trimmed)
    return ContactSocial.custom(custom, trimmed)
}
