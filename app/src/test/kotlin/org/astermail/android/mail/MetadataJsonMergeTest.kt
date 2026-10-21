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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.mail.MailItemMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataJsonMergeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun unknown_fields_survive_and_known_fields_are_replaced() {
        val original = """{"category":"promotions","category_pinned":false,"future_field":{"a":1}}"""
        val merged = merge_metadata_json(json, original, MailItemMetadata(category = "primary", category_pinned = true))
        val parsed = json.parseToJsonElement(merged).jsonObject
        assertEquals("primary", parsed["category"]!!.jsonPrimitive.content)
        assertEquals("true", parsed["category_pinned"]!!.jsonPrimitive.content)
        assertEquals("{\"a\":1}", parsed["future_field"].toString())
    }

    @Test
    fun unreadable_original_falls_back_to_the_known_fields() {
        val merged = merge_metadata_json(json, "not json", MailItemMetadata(category = "primary", category_pinned = true))
        val parsed = json.parseToJsonElement(merged).jsonObject
        assertEquals("primary", parsed["category"]!!.jsonPrimitive.content)
        assertTrue(parsed.keys.none { it == "future_field" })
    }
}
