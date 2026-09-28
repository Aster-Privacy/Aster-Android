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

package org.astermail.android.api.mail

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateAttachmentResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val backend_body = """{"id":"4b1f0a52-7c1e-4a8e-9a55-0d6c2f7e9b10","success":true}"""

    @Test
    fun backend_create_attachment_body_decodes() {
        val response = json.decodeFromString(CreateAttachmentResponse.serializer(), backend_body)
        assertEquals("4b1f0a52-7c1e-4a8e-9a55-0d6c2f7e9b10", response.id)
        assertTrue(response.success)
    }

    @Test
    fun backend_create_attachment_body_is_not_a_full_attachment() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(AttachmentResponse.serializer(), backend_body)
        }
    }
}
