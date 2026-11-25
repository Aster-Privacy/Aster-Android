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

package org.astermail.android.templates

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.api.templates.TemplateItem
import org.astermail.android.crypto.AccountKey
import org.astermail.android.crypto.FieldFormat

private const val template_key_length = 32
private val template_b64_whitespace = Regex("\\s")

class TemplateOpenResult(val outcome: TemplateLoadOutcome, val opening_keys: List<ByteArray>)

fun template_context_keys(identity_keys: List<String>): List<ByteArray> =
    identity_keys.distinct().flatMap { identity ->
        AccountKey.DATA_CONTEXTS.map { context ->
            MessageDigest.getInstance("SHA-256").digest((identity + context).toByteArray(Charsets.UTF_8))
        }
    }

fun decode_template_b64(input: String): ByteArray {
    val cleaned = input.replace(template_b64_whitespace, "")
    return runCatching { Base64.getDecoder().decode(cleaned) }
        .recoverCatching { Base64.getUrlDecoder().decode(cleaned) }
        .getOrThrow()
}

fun open_template_with(item: TemplateItem, key: ByteArray): DecryptedTemplate? {
    if (key.size != template_key_length) return null
    return runCatching {
        DecryptedTemplate(
            id = item.id,
            name = open_template_field(item.encrypted_name, item.name_nonce, key, TEMPLATE_FIELD_NAME, item.id),
            category = open_template_field(item.encrypted_category, item.category_nonce, key, TEMPLATE_FIELD_CATEGORY, item.id),
            content = open_template_field(item.encrypted_content, item.content_nonce, key, TEMPLATE_FIELD_CONTENT, item.id),
            sort_order = item.sort_order,
        )
    }.getOrNull()
}

fun open_templates(items: List<TemplateItem>, keys: List<ByteArray>): TemplateOpenResult {
    val opening_keys = mutableListOf<ByteArray>()
    val opened = items.map { item ->
        keys.firstNotNullOfOrNull { key ->
            open_template_with(item, key)?.also {
                if (opening_keys.none { seen -> seen.contentEquals(key) }) opening_keys.add(key)
            }
        }
    }
    val outcome = template_load_outcome(opened)
    return TemplateOpenResult(outcome.copy(items = outcome.items.sortedBy { it.sort_order }), opening_keys)
}

private fun open_template_field(
    ct_b64: String,
    nonce_b64: String,
    key: ByteArray,
    field: String,
    record_id: String,
): String {
    val plain = FieldFormat.decrypt(
        decode_template_b64(ct_b64),
        decode_template_b64(nonce_b64),
        key,
        TEMPLATE_FIELD_KIND,
        field,
        record_id,
    )
    return String(plain, Charsets.UTF_8)
}
