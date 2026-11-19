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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.templates.CreateTemplateRequest
import org.astermail.android.api.templates.CreateTemplateResponse
import org.astermail.android.api.templates.TemplatesApi
import org.astermail.android.api.templates.TemplatesListResponse
import org.astermail.android.api.templates.UpdateTemplateRequest
import org.astermail.android.crypto.BoundFieldRecords
import org.astermail.android.crypto.FieldFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemplateStoreTest {

    private val key = ByteArray(32) { (it * 3).toByte() }

    private class fake_api : TemplatesApi {
        val stored = mutableMapOf<String, Pair<String, String>>()
        var creates = 0
        var updates = 0
        override suspend fun list(): TemplatesListResponse = error("unused")
        override suspend fun create(request: CreateTemplateRequest): CreateTemplateResponse {
            creates += 1
            stored["srv-1"] = request.encrypted_content to request.content_nonce
            return CreateTemplateResponse(id = "srv-1", success = true)
        }
        override suspend fun update(template_id: String, request: UpdateTemplateRequest) {
            updates += 1
            stored[template_id] = request.encrypted_content!! to request.content_nonce!!
        }
        override suspend fun delete(template_id: String) {}
    }

    @Before
    @After
    fun reset() {
        BoundFieldRecords.bind(null)
    }

    private fun draft(id: String) = DecryptedTemplate(id = id, name = "Hello", category = "General", content = "Body text", sort_order = 1)

    private fun opens_bound_to(stored: Pair<String, String>, record_id: String?): Boolean = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, Base64.getDecoder().decode(stored.second)),
        )
        cipher.updateAAD(FieldFormat.aad(FieldFormat.KIND_TEMPLATE, TEMPLATE_FIELD_CONTENT, record_id))
        String(cipher.doFinal(Base64.getDecoder().decode(stored.first)), Charsets.UTF_8) == "Body text"
    }.getOrDefault(false)

    @Test
    fun a_new_template_is_bound_to_its_server_id_once_v2_is_on() = runBlocking {
        val api = fake_api()
        assertEquals("srv-1", store_template(api, key, draft(""), FieldFormat.V2))
        val stored = api.stored.getValue("srv-1")
        assertTrue(opens_bound_to(stored, "srv-1"))
        assertFalse(opens_bound_to(stored, null))
        assertTrue(BoundFieldRecords.is_bound(FieldFormat.KIND_TEMPLATE, "srv-1"))
    }

    @Test
    fun an_edit_with_v2_on_marks_the_record_bound() = runBlocking {
        val api = fake_api()
        store_template(api, key, draft("tpl-5"), FieldFormat.V2)
        assertTrue(opens_bound_to(api.stored.getValue("tpl-5"), "tpl-5"))
        assertTrue(BoundFieldRecords.is_bound(FieldFormat.KIND_TEMPLATE, "tpl-5"))
    }

    @Test
    fun with_the_switch_off_templates_are_written_as_before() = runBlocking {
        val api = fake_api()
        store_template(api, key, draft(""), FieldFormat.V1)
        assertEquals(1, api.creates)
        assertEquals(0, api.updates)
        assertFalse(BoundFieldRecords.is_bound(FieldFormat.KIND_TEMPLATE, "srv-1"))
    }
}
