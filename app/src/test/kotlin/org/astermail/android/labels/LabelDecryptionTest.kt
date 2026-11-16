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


package org.astermail.android.labels

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.tags.TagItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelDecryptionTest {

    private val ciphertext_name = "q9Zk3LmP0aXv7RtY2bWc8NfH4jE6sDuG1oKi5pQr"
    private val ciphertext_icon = "Vb7nM2xC9zL4kJ8hG3fD6sA1pO5iU0yT"

    private val plaintext = mapOf(
        ciphertext_name to "Newsletters and promotions",
        ciphertext_icon to "map-pin",
    )

    private val opens_everything: (String, String) -> String? = { value, _ -> plaintext[value] }
    private val opens_nothing: (String, String) -> String? = { _, _ -> null }
    private val opens_only_names: (String, String) -> String? = { value, _ ->
        if (value == ciphertext_name) plaintext[value] else null
    }

    private fun label() = LabelItem(
        id = "l1",
        label_token = "l1",
        encrypted_name = ciphertext_name,
        name_nonce = "n1",
        encrypted_icon = ciphertext_icon,
        icon_nonce = "n2",
        folder_type = LABEL_FOLDER_TYPE,
    )

    private fun tag() = TagItem(
        id = "t1",
        tag_token = "t1",
        encrypted_name = ciphertext_name,
        name_nonce = "n1",
        encrypted_icon = ciphertext_icon,
        icon_nonce = "n2",
    )

    @Test
    fun decrypted_long_label_name_is_listed() {
        val opened = label_after_decryption(label(), opens_everything)
        assertFalse(opened.decryption_failed)
        assertEquals("Newsletters and promotions", readable_label_name(opened))
        assertEquals("map-pin", readable_label_icon(opened))
        assertEquals(listOf("Newsletters and promotions"), label_rows(listOf(opened)).map { it.encrypted_name })
    }

    @Test
    fun label_that_fails_to_decrypt_stays_hidden() {
        val failed = label_after_decryption(label(), opens_nothing)
        assertTrue(failed.decryption_failed)
        assertNull(failed.encrypted_name)
        assertNull(failed.encrypted_icon)
        assertNull(readable_label_name(failed))
        assertTrue(label_rows(listOf(failed)).isEmpty())
    }

    @Test
    fun label_that_throws_while_decrypting_stays_hidden() {
        val failed = label_after_decryption(label()) { _, _ -> error("bad key") }
        assertTrue(failed.decryption_failed)
        assertTrue(label_rows(listOf(failed)).isEmpty())
    }

    @Test
    fun failed_icon_alone_keeps_the_name() {
        val opened = label_after_decryption(label(), opens_only_names)
        assertFalse(opened.decryption_failed)
        assertEquals("Newsletters and promotions", readable_label_name(opened))
        assertNull(readable_label_icon(opened))
    }

    @Test
    fun field_without_a_nonce_is_kept_as_written() {
        val legacy = label().copy(encrypted_name = "Zamówienia internetowe2026", name_nonce = null)
        val opened = label_after_decryption(legacy, opens_nothing)
        assertFalse(opened.decryption_failed)
        assertEquals("Zamówienia internetowe2026", readable_label_name(opened))
    }

    @Test
    fun decrypted_long_tag_name_is_readable() {
        val opened = tag_after_decryption(tag(), opens_everything)
        assertTrue(tag_name_readable(opened))
        assertEquals("Newsletters and promotions", tag_display_name(opened, "Unknown"))
        assertEquals("map-pin", readable_tag_icon(opened))
    }

    @Test
    fun tag_that_fails_to_decrypt_stays_hidden() {
        val failed = tag_after_decryption(tag(), opens_nothing)
        assertTrue(failed.decryption_failed)
        assertFalse(tag_name_readable(failed))
        assertEquals("Unknown", tag_display_name(failed, "Unknown"))
        assertNull(readable_tag_icon(failed))
    }

    @Test
    fun failure_flag_hides_a_name_even_if_one_is_left() {
        val label = label().copy(encrypted_name = "Faktury", decryption_failed = true)
        val tag = tag().copy(encrypted_name = "Faktury", decryption_failed = true)
        assertNull(readable_label_name(label))
        assertFalse(tag_name_readable(tag))
    }
}
