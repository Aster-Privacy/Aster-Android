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

class VaultWriterSourceTest {

    private val app_root = File("src/main/kotlin/org/astermail/android")
    private val core_api_root = File("../core-api/src/main/kotlin/org/astermail/android")
    private val helper = "crypto/storage_rotation/vault_updater.kt"

    private val helper_only = listOf(
        Regex("""\bupdate_vault\s*\("""),
        Regex("""UpdateVaultRequest\b"""),
        Regex("""keys/vault"""),
        Regex("""\.put\s*\(\s*VAULT_PATH"""),
        Regex("""http\s*\.\s*put\s*\(\s*"[^"]*/vault"""),
    )

    private val non_cas_endpoints = mapOf(
        Regex("""\bnew_encrypted_vault\s*="""") to setOf("auth/AuthRepository.kt", "auth/RecoveryViewModel.kt"),
        Regex("""recovery_api\s*\.\s*backup\s*\(""") to setOf("auth/AuthRepository.kt"),
    )

    private fun kotlin_files(root: File): Sequence<Pair<String, File>> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }

    private fun hits(pattern: Regex, allowed: Set<String>): List<String> =
        (kotlin_files(app_root) + kotlin_files(core_api_root))
            .filter { (path, _) -> path !in allowed }
            .flatMap { (path, file) ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (pattern.containsMatchIn(line)) "$path:${index + 1}" else null
                }
            }
            .toList()

    @Test
    fun `the source trees are where the scan expects them`() {
        assertTrue(File(app_root, helper).isFile)
        assertTrue(File(core_api_root, "api/keys/KeysApi.kt").isFile)
    }

    @Test
    fun `every vault put goes through the read modify write helper`() {
        val offenders = helper_only.flatMap { hits(it, setOf(helper)) }
        assertTrue("vault written outside VaultUpdater: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the helper still owns the vault put`() {
        val text = File(app_root, helper).readText()
        assertTrue(Regex("""api\.put\(VAULT_PATH""").containsMatchIn(text))
        assertTrue(text.contains("\"expected_vault_sha256\""))
    }

    @Test
    fun `endpoints that replace the vault without a hash check stay in their known callers`() {
        val offenders = non_cas_endpoints.flatMap { (pattern, allowed) -> hits(pattern, allowed) }
        assertTrue("new vault replacement path: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the recovery code rotation sends its vault through the helper`() {
        val text = File(app_root, "auth/AuthRepository.kt").readText()
        val start = text.indexOf("suspend fun rotate_recovery_codes(")
        val end = text.indexOf("private fun backup_vault_bytes(", start)
        assertTrue(start >= 0 && end > start)
        val body = text.substring(start, end)
        assertTrue(body.contains("replace_through("))
        assertTrue(!body.contains("get_encrypted_vault()"))
    }

    @Test
    fun `the recovery code rotation uploads the backup and the vault in one checked request`() {
        val text = File(app_root, "auth/AuthRepository.kt").readText()
        val start = text.indexOf("suspend fun rotate_recovery_codes(")
        val end = text.indexOf("private fun backup_vault_bytes(", start)
        val body = text.substring(start, end)
        assertEquals(1, Regex("""recovery_api\.backup\(""").findAll(body).count())
        assertTrue(!body.contains("update_vault"))
        assertTrue(!body.contains("put_vault"))
        val request = body.substring(body.indexOf("SaveRecoveryBackupRequest("))
        assertTrue(request.contains("encrypted_vault_backup = "))
        assertTrue(request.contains("encrypted_vault = sealed.encrypted_vault"))
        assertTrue(request.contains("expected_vault_sha256 = snapshot.vault_sha256"))
    }
}
