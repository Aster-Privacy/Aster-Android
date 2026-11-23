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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TranslationRegistryTest {

    private fun bundled_registry(): File? =
        listOf(
            File("src/full/assets/bergamot/registry.json"),
            File("app/src/full/assets/bergamot/registry.json"),
        ).firstOrNull { it.isFile }

    private fun bundled_hashes(): Map<String, String> {
        val file = bundled_registry()
        assumeTrue(file != null)
        val hashes = TranslationAssets.read_registry_hashes(file!!.readText(Charsets.UTF_8))
        assertNotNull(hashes)
        return hashes!!
    }

    @Test
    fun the_bundled_registry_matches_its_pinned_hash() {
        val file = bundled_registry()
        assumeTrue(file != null)
        assertEquals(TranslationAssets.BUNDLED_REGISTRY_SHA256, TranslationAssets.sha256_hex(file!!.readBytes()))
    }

    @Test
    fun the_bundled_registry_pins_every_model_file_of_every_supported_pair() {
        val hashes = bundled_hashes()
        val expected_packs = translation_language_codes
            .filter { it != PIVOT_LANGUAGE }
            .flatMap { listOf("$it$PIVOT_LANGUAGE", "$PIVOT_LANGUAGE$it") }
            .toSet()
        val packs = hashes.keys.mapNotNull { TranslationAssets.pack_of(it) }.toSet()
        assertEquals(expected_packs, packs)
        for (pack in expected_packs) {
            assertTrue(pack, hashes.keys.count { it.startsWith("$pack/") } >= 3)
        }
        assertTrue(hashes.values.all { TranslationAssets.is_sha256_hex(it) })
    }

    @Test
    fun every_route_between_supported_languages_has_pinned_packs() {
        val packs = bundled_hashes().keys.mapNotNull { TranslationAssets.pack_of(it) }.toSet()
        for (from in translation_language_codes) {
            for (to in translation_language_codes) {
                assertTrue("$from$to", packs.containsAll(TranslationDownloadPolicy.route_packs(from, to)))
            }
        }
    }

    @Test
    fun a_registry_entry_with_a_bad_hash_or_path_is_dropped() {
        val good = "B".repeat(64)
        val other = "a".repeat(64)
        val registry = "{\"deen\":{" +
            "\"lex\":{\"name\":\"deen/lex.bin\",\"expectedSha256Hash\":\"abc\"}," +
            "\"model\":{\"name\":\"../deen/model.bin\",\"expectedSha256Hash\":\"$other\"}," +
            "\"vocab\":{\"name\":\"deen/vocab.spm\",\"expectedSha256Hash\":\"$good\"}}}"

        val hashes = TranslationAssets.read_registry_hashes(registry)

        assertEquals(mapOf("deen/vocab.spm" to "b".repeat(64)), hashes)
    }

    @Test
    fun a_registry_with_no_valid_entries_is_refused() {
        assertNull(TranslationAssets.read_registry_hashes("{}"))
        assertNull(TranslationAssets.read_registry_hashes("not json"))
    }

    @Test
    fun pack_names_come_only_from_a_four_letter_prefix() {
        assertEquals("deen", TranslationAssets.pack_of("deen/model.deen.intgemm.alphas.bin"))
        assertNull(TranslationAssets.pack_of("model.deen.bin"))
        assertNull(TranslationAssets.pack_of("DEEN/model.bin"))
        assertNull(TranslationAssets.pack_of("deen1/model.bin"))
    }

    @Test
    fun unsafe_model_paths_are_refused() {
        assertNull(TranslationAssets.sanitize("../registry.json"))
        assertNull(TranslationAssets.sanitize("/deen/model.bin"))
        assertNull(TranslationAssets.sanitize("deen\\model.bin"))
        assertNull(TranslationAssets.sanitize("deen/model.bin?x=1"))
        assertEquals("deen/model.bin", TranslationAssets.sanitize("deen/model.bin"))
    }

    @Test
    fun hex_checks_reject_uppercase_and_short_values() {
        assertTrue(TranslationAssets.is_sha256_hex("0".repeat(64)))
        assertFalse(TranslationAssets.is_sha256_hex("0".repeat(63)))
        assertFalse(TranslationAssets.is_sha256_hex("A".repeat(64)))
    }
}
