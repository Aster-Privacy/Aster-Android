// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.astermail.android.api.signatures.UpdateSignatureRequest
import org.astermail.android.api.signatures.signature_alias_field
import org.astermail.android.api.signatures.signature_placement_field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureFieldsTest {

    private val json = Json { explicitNulls = false }

    @Test
    fun clearing_alias_sends_explicit_null() {
        assertEquals(JsonNull, signature_alias_field(null, clear = true))
    }

    @Test
    fun omitted_alias_stays_absent() {
        assertNull(signature_alias_field(null, clear = false))
    }

    @Test
    fun setting_alias_sends_the_id() {
        val body = json.encodeToString(
            UpdateSignatureRequest(alias_id = signature_alias_field("alias-1", clear = false)),
        )
        assertEquals("""{"alias_id":"alias-1"}""", body)
    }

    @Test
    fun clearing_alias_survives_explicit_nulls_disabled() {
        val body = json.encodeToString(
            UpdateSignatureRequest(alias_id = signature_alias_field(null, clear = true)),
        )
        assertEquals("""{"alias_id":null}""", body)
    }

    @Test
    fun clearing_placement_survives_explicit_nulls_disabled() {
        val body = json.encodeToString(
            UpdateSignatureRequest(placement = signature_placement_field(null, clear = true)),
        )
        assertEquals("""{"placement":null}""", body)
    }

    @Test
    fun setting_placement_sends_the_value() {
        val body = json.encodeToString(
            UpdateSignatureRequest(placement = signature_placement_field(1, clear = false)),
        )
        assertEquals("""{"placement":1}""", body)
    }

    @Test
    fun untouched_fields_are_omitted_entirely() {
        val body = json.encodeToString(UpdateSignatureRequest(encrypted_content = "abc"))
        assertEquals("""{"encrypted_content":"abc"}""", body)
    }
}
