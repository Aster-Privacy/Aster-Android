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
package org.astermail.android.crypto.storage_rotation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageWriteKeySourceTest {

    private val app_root = File("src/main/kotlin/org/astermail/android")
    private val core_api_root = File("../core-api/src/main/kotlin/org/astermail/android")

    private fun source(path: String): String = File(app_root, path).readText().replace("\r\n", "\n")

    private fun assert_has(path: String, pattern: Regex) {
        assertTrue("$path no longer matches ${pattern.pattern}", pattern.containsMatchIn(source(path)))
    }

    private fun kotlin_files(root: File): Sequence<Pair<String, File>> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }

    @Test
    fun `ratchet state uploads seal only under the data kek`() {
        val path = "mail/ratchet/RatchetStateStore.kt"
        assert_has(path, Regex("""fun write_master_key\(\): ByteArray\? =\s*org\.astermail\.android\.crypto\.StorageWriteKey\.write_key\(session_key_store\)"""))
        assert_has(path, Regex("""fun derive_state_encryption_key\(\): ByteArray\? \{\s*val master = write_master_key\(\) \?: return null"""))
        assert_has("mail/ratchet/RatchetStateSyncer.kt", Regex("""val key = state_store\.derive_state_encryption_key\(\) \?: return false"""))
    }

    @Test
    fun `escrow uploads seal only under the data kek`() {
        val path = "mail/ratchet/MessageEscrow.kt"
        assert_has(path, Regex("""private suspend fun post_sealed\([^)]*\): Boolean \{\s*val base = state_store\.write_master_key\(\)"""))
        assert_has(path, Regex("""if \(escrow_key == null\) \{\s*payload\.fill\(0\)\s*return false"""))
    }

    @Test
    fun `metadata patches encrypt only under the data kek`() {
        val path = "mail/MailRepository.kt"
        assert_has(path, Regex("""val write_master = org\.astermail\.android\.crypto\.StorageWriteKey\.write_key\(session_key_store\)"""))
        assert_has(path, Regex("""MetadataKeySet\(source, keys, write_master != null\)"""))
        assert_has(path, Regex("""private fun metadata_write_key\(\): ByteArray\? \{\s*val set = metadata_key_set\(\)\s*return if \(set\.has_write_key\) set\.keys\.firstOrNull\(\) else null"""))
        assert_has(path, Regex("""private fun metadata_key\(\): ByteArray\? = metadata_write_key\(\)"""))
    }

    @Test
    fun `password storage keys are used only to read old rows`() {
        val allowed = setOf(
            "crypto/storage_write_key.kt",
            "crypto/storage_rotation/storage_rekey_service.kt",
        )
        val pattern = Regex("""\bpassword_(read_keys|storage_key)\b""")
        val found = (kotlin_files(app_root) + kotlin_files(core_api_root))
            .filter { (path, _) -> path !in allowed }
            .flatMap { (path, file) ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (pattern.containsMatchIn(line)) "$path:${index + 1}" else null
                }
            }
            .toList()
        assertEquals(emptyList<String>(), found)
    }
}
