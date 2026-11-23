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

package org.astermail.android.translation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationMemoryTest {

    @After
    fun teardown() {
        TranslationMemory.clear()
    }

    @Test
    fun a_translation_is_remembered_per_target_language() {
        val key_en = TranslationMemory.key(42, "en")
        val key_fr = TranslationMemory.key(42, "fr")
        TranslationMemory.put(key_en, remembered_translation("de", "<p>Hello</p>", "Invoice"))

        assertEquals("<p>Hello</p>", TranslationMemory.get(key_en)?.body)
        assertEquals("Invoice", TranslationMemory.get(key_en)?.subject)
        assertNull(TranslationMemory.get(key_fr))
    }

    @Test
    fun clearing_forgets_every_translation() {
        val key = TranslationMemory.key(7, "en")
        TranslationMemory.put(key, remembered_translation("de", "body", null))

        TranslationMemory.clear()

        assertNull(TranslationMemory.get(key))
    }

    @Test
    fun the_oldest_translation_is_dropped_once_the_memory_is_full() {
        val first = TranslationMemory.key(0, "en")
        TranslationMemory.put(first, remembered_translation("de", "0", null))
        for (index in 1..TranslationMemory.MAX_ENTRIES) {
            TranslationMemory.put(TranslationMemory.key(index, "en"), remembered_translation("de", "$index", null))
        }

        assertNull(TranslationMemory.get(first))
        assertEquals("1", TranslationMemory.get(TranslationMemory.key(1, "en"))?.body)
    }

    @Test
    fun the_subject_is_split_from_the_last_segment() {
        val parts = split_subject_segment(listOf("Hello", "World", " Invoice "), subject_included = true)

        assertEquals(listOf("Hello", "World"), parts.body)
        assertEquals("Invoice", parts.subject)
    }

    @Test
    fun segments_stay_whole_without_a_subject() {
        val parts = split_subject_segment(listOf("Hello", "World"), subject_included = false)

        assertEquals(listOf("Hello", "World"), parts.body)
        assertNull(parts.subject)
    }

    @Test
    fun only_east_asian_languages_are_marked_limited_quality() {
        assertEquals(
            setOf("ja", "ko", "zh"),
            translation_language_codes.filter { translation_quality_limited(it) }.toSet(),
        )
        assertEquals(false, translation_quality_limited(null))
    }
}
