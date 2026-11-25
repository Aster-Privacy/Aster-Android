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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.api.templates.TemplateItem
import org.astermail.android.crypto.BoundFieldRecords
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemplateDecryptTest {

    private val storage_key = ByteArray(32) { (it * 7 + 1).toByte() }
    private val other_key = ByteArray(32) { (it * 11 + 5).toByte() }
    private val previous_identity = "previous-identity-secret"

    @Before
    @After
    fun reset() {
        BoundFieldRecords.bind(null)
    }

    private fun seal(key: ByteArray, plaintext: String, nonce_seed: Int, url_safe: Boolean = false): Pair<String, String> {
        val nonce = ByteArray(12) { (it + nonce_seed).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = if (url_safe) Base64.getUrlEncoder().withoutPadding() else Base64.getEncoder()
        return encoder.encodeToString(sealed) to encoder.encodeToString(nonce)
    }

    private fun item(id: String, key: ByteArray, name: String, sort_order: Int, url_safe: Boolean = false): TemplateItem {
        val sealed_name = seal(key, name, 1, url_safe)
        val sealed_category = seal(key, "General", 2, url_safe)
        val sealed_content = seal(key, "Body of $name", 3, url_safe)
        return TemplateItem(
            id = id,
            encrypted_name = sealed_name.first,
            name_nonce = sealed_name.second,
            encrypted_category = sealed_category.first,
            category_nonce = sealed_category.second,
            encrypted_content = sealed_content.first,
            content_nonce = sealed_content.second,
            sort_order = sort_order,
        )
    }

    private fun previous_context_key(context: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest((previous_identity + context).toByteArray(Charsets.UTF_8))

    @Test
    fun a_template_sealed_with_a_previous_identity_context_key_opens() {
        val sealed = item("tpl-1", previous_context_key("astermail-preferences-v1"), "Greeting", 1)
        assertNull(open_template_with(sealed, storage_key))
        val keys = listOf(storage_key.copyOf()) + template_context_keys(listOf(previous_identity))
        val result = open_templates(listOf(sealed), keys)
        assertEquals(0, result.outcome.undecryptable_count)
        assertEquals("Greeting", result.outcome.items.single().name)
        assertEquals("Body of Greeting", result.outcome.items.single().content)
    }

    @Test
    fun context_keys_cover_every_data_context() {
        val keys = template_context_keys(listOf(previous_identity, previous_identity))
        assertEquals(10, keys.size)
        assertTrue(keys.any { it.contentEquals(previous_context_key("astermail-recovery-email-v1")) })
    }

    @Test
    fun readable_templates_stay_listed_when_one_cannot_open() {
        val readable_late = item("tpl-a", storage_key, "Later", 5)
        val unreadable = item("tpl-b", other_key, "Hidden", 2)
        val readable_early = item("tpl-c", storage_key, "Earlier", 1)
        val result = open_templates(listOf(readable_late, unreadable, readable_early), listOf(storage_key))
        assertEquals(1, result.outcome.undecryptable_count)
        assertEquals(listOf("Earlier", "Later"), result.outcome.items.map { it.name })
        assertEquals(1, result.opening_keys.size)
    }

    @Test
    fun url_safe_unpadded_fields_open() {
        val sealed = item("tpl-u", storage_key, "Url safe", 1, url_safe = true)
        assertEquals("Url safe", open_template_with(sealed, storage_key)?.name)
    }

    @Test
    fun wrapped_base64_opens() {
        val sealed = item("tpl-w", storage_key, "Wrapped", 1)
        val wrapped = sealed.copy(encrypted_content = sealed.encrypted_content.chunked(8).joinToString("\n"))
        assertEquals("Body of Wrapped", open_template_with(wrapped, storage_key)?.content)
    }

    @Test
    fun a_short_key_is_skipped() {
        val sealed = item("tpl-s", storage_key, "Short", 1)
        assertNull(open_template_with(sealed, ByteArray(16)))
    }

    @Test
    fun unreadable_templates_wait_for_keys_before_the_warning() {
        assertTrue(template_keys_pending(1, keys_settled = false, waited_ms = 0))
        assertFalse(template_keys_pending(1, keys_settled = true, waited_ms = 0))
        assertFalse(template_keys_pending(0, keys_settled = false, waited_ms = 0))
        assertFalse(template_keys_pending(1, keys_settled = false, waited_ms = TEMPLATE_KEY_SETTLE_MAX_MS))
    }
}
