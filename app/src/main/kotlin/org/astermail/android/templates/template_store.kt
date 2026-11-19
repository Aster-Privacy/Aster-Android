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

import java.util.Base64
import org.astermail.android.api.templates.CreateTemplateRequest
import org.astermail.android.api.templates.TemplatesApi
import org.astermail.android.api.templates.UpdateTemplateRequest
import org.astermail.android.crypto.BoundFieldRecords
import org.astermail.android.crypto.FieldFormat

data class SealedTemplateFields(
    val name: Pair<String, String>,
    val category: Pair<String, String>,
    val content: Pair<String, String>,
)

fun seal_template_fields(draft: DecryptedTemplate, key: ByteArray, record_id: String?, version: Int): SealedTemplateFields =
    SealedTemplateFields(
        name = seal_template_field(draft.name, key, TEMPLATE_FIELD_NAME, record_id, version),
        category = seal_template_field(draft.category, key, TEMPLATE_FIELD_CATEGORY, record_id, version),
        content = seal_template_field(draft.content, key, TEMPLATE_FIELD_CONTENT, record_id, version),
    )

private fun seal_template_field(
    plaintext: String,
    key: ByteArray,
    field_name: String,
    record_id: String?,
    version: Int,
): Pair<String, String> {
    val field = FieldFormat.encrypt(
        plaintext.toByteArray(Charsets.UTF_8),
        key,
        TEMPLATE_FIELD_KIND,
        field_name,
        record_id,
        version,
    )
    val encoder = Base64.getEncoder()
    return encoder.encodeToString(field.ciphertext) to encoder.encodeToString(field.nonce)
}

private fun update_request(fields: SealedTemplateFields, sort_order: Int): UpdateTemplateRequest =
    UpdateTemplateRequest(
        encrypted_name = fields.name.first,
        name_nonce = fields.name.second,
        encrypted_category = fields.category.first,
        category_nonce = fields.category.second,
        encrypted_content = fields.content.first,
        content_nonce = fields.content.second,
        sort_order = sort_order,
    )

suspend fun store_template(
    api: TemplatesApi,
    key: ByteArray,
    draft: DecryptedTemplate,
    version: Int = FieldFormat.write_version(),
): String {
    if (draft.id.isNotBlank()) {
        api.update(draft.id, update_request(seal_template_fields(draft, key, draft.id, version), draft.sort_order))
        if (version == FieldFormat.V2) BoundFieldRecords.mark(TEMPLATE_FIELD_KIND, draft.id)
        return draft.id
    }
    val first = seal_template_fields(draft, key, null, version)
    val created = api.create(
        CreateTemplateRequest(
            encrypted_name = first.name.first,
            name_nonce = first.name.second,
            encrypted_category = first.category.first,
            category_nonce = first.category.second,
            encrypted_content = first.content.first,
            content_nonce = first.content.second,
            sort_order = draft.sort_order,
        ),
    )
    if (version == FieldFormat.V2 && created.id.isNotBlank()) {
        api.update(created.id, update_request(seal_template_fields(draft, key, created.id, version), draft.sort_order))
        BoundFieldRecords.mark(TEMPLATE_FIELD_KIND, created.id)
    }
    return created.id
}
