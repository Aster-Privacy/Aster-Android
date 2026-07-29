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

package org.astermail.android.settings

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSignatureContentTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun signature(id: String, content: String, is_default: Boolean = true) =
        DecryptedSignature(
            id = id,
            name = "Default",
            content = content,
            is_default = is_default,
            is_html = false,
            alias_id = null,
            placement = null,
        )

    @Test
    fun editing_an_existing_default_replaces_its_content() {
        val updated = apply_default_signature_content(
            current = listOf(signature("sig-1", "old text")),
            target_id = "sig-1",
            content = "new text",
            default_name = "Default",
            is_html = false,
        )

        assertEquals(1, updated.size)
        assertEquals("new text", updated.single().content)
    }

    @Test
    fun other_signatures_are_left_untouched() {
        val updated = apply_default_signature_content(
            current = listOf(
                signature("sig-1", "old text"),
                signature("sig-2", "alias sig", is_default = false),
            ),
            target_id = "sig-1",
            content = "new text",
            default_name = "Default",
            is_html = false,
        )

        assertEquals("new text", updated.first { it.id == "sig-1" }.content)
        assertEquals("alias sig", updated.first { it.id == "sig-2" }.content)
    }

    @Test
    fun a_missing_default_is_appended_rather_than_dropped() {
        val updated = apply_default_signature_content(
            current = emptyList(),
            target_id = "sig-new",
            content = "hello",
            default_name = "Default",
            is_html = true,
        )

        assertEquals(1, updated.size)
        assertEquals("sig-new", updated.single().id)
        assertEquals("hello", updated.single().content)
        assertTrue(updated.single().is_default)
        assertTrue(updated.single().is_html)
    }

    @Test
    fun the_saved_edit_survives_a_cache_round_trip() {
        val updated = apply_default_signature_content(
            current = listOf(signature("sig-1", "old text")),
            target_id = "sig-1",
            content = "new text",
            default_name = "Default",
            is_html = false,
        )

        val serializer = ListSerializer(DecryptedSignature.serializer())
        val rehydrated = json.decodeFromString(serializer, json.encodeToString(serializer, updated))

        assertEquals("new text", rehydrated.single { it.is_default }.content)
    }
}
