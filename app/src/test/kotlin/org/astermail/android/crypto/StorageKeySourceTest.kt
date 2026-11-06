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

package org.astermail.android.crypto

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageKeySourceTest {

    private val password_key_patterns = listOf(
        Regex("""CryptoNative\s*(\.|::)\s*derive_storage_key\b"""),
        Regex("""derive_stretched_storage_key\b"""),
        Regex("""derive_storage_key_for_version\b"""),
        Regex("""aster-storage-encryption-key-v1"""),
        Regex("""aster-hkdf-salt-v1"""),
    )

    private val allowed_files = setOf(
        "crypto/storage_write_key.kt",
        "auth/AuthRepository.kt",
        "auth/device_recovery.kt",
        "auth/stretched_storage_keys.kt",
    )

    private val source_root = File("src/main/kotlin/org/astermail/android")

    private fun relative(file: File): String =
        file.relativeTo(source_root).invariantSeparatorsPath

    @Test
    fun `the source tree is where the scan expects it`() {
        assertTrue(File(source_root, "crypto/storage_write_key.kt").isFile)
    }

    @Test
    fun `only the single source and reader paths derive a storage key from the password`() {
        val offenders = source_root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { relative(it) !in allowed_files }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (password_key_patterns.any { it.containsMatchIn(line) }) "${relative(file)}:${index + 1}" else null
                }
            }
            .toList()

        assertTrue("password storage key derived outside StorageWriteKey: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the single source still derives the password key`() {
        val hits = File(source_root, "crypto/storage_write_key.kt").readLines()
            .count { line -> password_key_patterns.any { it.containsMatchIn(line) } }

        assertTrue(hits > 0)
    }
}
