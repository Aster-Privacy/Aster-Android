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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageKeySourceTest {

    private val password_key_calls = linkedMapOf(
        "PasswordKdf.derive_aes_key" to Regex("""PasswordKdf\s*\.\s*derive_aes_key\s*\("""),
        "derive_pbkdf2_hash" to Regex("""derive_pbkdf2_hash\s*\("""),
        "pbkdf2_hmac" to Regex("""pbkdf2_hmac"""),
        "PBKDF2WithHmac" to Regex("""PBKDF2WithHmac""", RegexOption.IGNORE_CASE),
        "PBEKeySpec" to Regex("""PBEKeySpec"""),
        "SecretKeyFactory" to Regex("""SecretKeyFactory"""),
        "encrypt_vault_with_password" to Regex("""encrypt_vault_with_password\s*\("""),
        "SentMailResealCrypto.seal" to Regex("""SentMailResealCrypto\s*\.\s*seal\s*\("""),
        "CryptoNative.derive_storage_key" to Regex("""CryptoNative\s*(\.|::)\s*derive_storage_key\b"""),
        "derive_storage_key" to Regex("""derive_storage_key"""),
        "derive_stretched_storage_key" to Regex("""derive_stretched_storage_key"""),
        "derive_storage_key_for_version" to Regex("""derive_storage_key_for_version"""),
        "password_storage_key" to Regex("""password_storage_key\s*\("""),
        "storage_password_keys" to Regex("""storage_password_keys\s*\("""),
        "external_account_master_key" to Regex("""external_account_master_key"""),
        "aster-storage-encryption-key-v1" to Regex("""aster-storage-encryption-key-v1"""),
        "aster-hkdf-salt-v1" to Regex("""aster-hkdf-salt-v1"""),
    )

    private val vault_wrap = listOf(
        Triple("auth/AuthRepository.kt", "PasswordKdf.derive_aes_key", 3),
        Triple("auth/AuthRepository.kt", "derive_pbkdf2_hash", 4),
        Triple("auth/AuthRepository.kt", "encrypt_vault_with_password", 3),
        Triple("auth/RecoveryViewModel.kt", "PasswordKdf.derive_aes_key", 2),
        Triple("auth/RecoveryViewModel.kt", "derive_pbkdf2_hash", 1),
        Triple("auth/RecoveryViewModel.kt", "encrypt_vault_with_password", 2),
        Triple("auth/backup_unlocked_keys.kt", "PasswordKdf.derive_aes_key", 1),
        Triple("crypto/storage_rotation/storage_rekey_service.kt", "derive_pbkdf2_hash", 1),
        Triple("crypto/storage_rotation/storage_rekey_service.kt", "encrypt_vault_with_password", 1),
    )

    private val key_files_outside_storage = listOf(
        Triple("auth/PrivateKeyExport.kt", "PasswordKdf.derive_aes_key", 1),
        Triple("auth/recovery_code.kt", "PBKDF2WithHmac", 1),
        Triple("auth/recovery_code.kt", "PBEKeySpec", 2),
        Triple("auth/recovery_code.kt", "SecretKeyFactory", 2),
        Triple("security/AppLockStore.kt", "PasswordKdf.derive_aes_key", 1),
        Triple("security/lock_key_wrap.kt", "PasswordKdf.derive_aes_key", 2),
        Triple("folders/folder_protection.kt", "pbkdf2_hmac", 3),
    )

    private val legacy_readers = listOf(
        Triple("auth/AuthRepository.kt", "CryptoNative.derive_storage_key", 3),
        Triple("auth/AuthRepository.kt", "derive_storage_key", 4),
        Triple("auth/AuthRepository.kt", "password_storage_key", 1),
        Triple("auth/AuthRepository.kt", "storage_password_keys", 1),
        Triple("auth/VaultMasterKeyUpgrade.kt", "derive_storage_key", 2),
        Triple("auth/VaultMasterKeyUpgrade.kt", "password_storage_key", 1),
        Triple("auth/device_recovery.kt", "CryptoNative.derive_storage_key", 1),
        Triple("auth/device_recovery.kt", "derive_storage_key", 4),
        Triple("auth/stretched_storage_keys.kt", "derive_storage_key", 2),
        Triple("auth/stretched_storage_keys.kt", "derive_storage_key_for_version", 2),
        Triple("crypto/storage_write_key.kt", "CryptoNative.derive_storage_key", 1),
        Triple("crypto/storage_write_key.kt", "derive_storage_key", 1),
        Triple("crypto/storage_write_key.kt", "derive_stretched_storage_key", 1),
        Triple("crypto/storage_write_key.kt", "password_storage_key", 1),
        Triple("crypto/storage_rotation/storage_rekey_service.kt", "password_storage_key", 2),
        Triple("crypto/storage_rotation/storage_rekey_service.kt", "storage_password_keys", 2),
        Triple("mail/account_data_conversion.kt", "PasswordKdf.derive_aes_key", 1),
        Triple("mail/MailRepository.kt", "derive_pbkdf2_hash", 1),
    )

    private val not_admitted_fallbacks = listOf(
        Triple("mail/MailRepository.kt", "PasswordKdf.derive_aes_key", 2),
        Triple("mail/sent_mail_reseal.kt", "PasswordKdf.derive_aes_key", 2),
        Triple("mail/sent_mail_reseal.kt", "SentMailResealCrypto.seal", 2),
    )

    private val allowed: Map<Pair<String, String>, Int> =
        (vault_wrap + key_files_outside_storage + legacy_readers + not_admitted_fallbacks)
            .associate { (file, call, count) -> (file to call) to count }

    private val source_root = File("src/main/kotlin/org/astermail/android")

    private fun relative(file: File): String =
        file.relativeTo(source_root).invariantSeparatorsPath

    private fun password_key_call_sites(): Map<Pair<String, String>, Int> {
        val sites = linkedMapOf<Pair<String, String>, Int>()
        source_root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { relative(it) }
            .forEach { file ->
                val source = file.readText()
                for ((call, pattern) in password_key_calls) {
                    val count = pattern.findAll(source).count()
                    if (count > 0) {
                        sites[relative(file) to call] = count
                    }
                }
            }
        return sites
    }

    @Test
    fun `the source tree is where the scan expects it`() {
        assertTrue(File(source_root, "crypto/storage_write_key.kt").isFile)
    }

    @Test
    fun `password derived keys stay in their allowlist with exact counts`() {
        val found = password_key_call_sites()

        val unexpected = found.filter { (site, count) -> allowed[site] != count }
            .map { (site, count) -> "${site.first} uses ${site.second} $count time(s), allowed ${allowed[site] ?: 0}" }
        val missing = allowed.filter { (site, _) -> site !in found }
            .map { (site, count) -> "${site.first} no longer uses ${site.second}, allowed $count" }

        assertTrue(
            "password derived key use changed; write with the data_kek and update the allowlist only for a reviewed compatibility path: ${unexpected + missing}",
            unexpected.isEmpty() && missing.isEmpty(),
        )
    }

    @Test
    fun `the call site scan finds the known sites`() {
        val found = password_key_call_sites()

        assertEquals(2, found["mail/MailRepository.kt" to "PasswordKdf.derive_aes_key"])
        assertEquals(2, found["mail/sent_mail_reseal.kt" to "SentMailResealCrypto.seal"])
        assertEquals(1, found["crypto/storage_write_key.kt" to "derive_stretched_storage_key"])
    }

    @Test
    fun `the only password sealed mail writes are the not admitted fallbacks`() {
        val mail_sites = password_key_call_sites().filterKeys { it.first.startsWith("mail/") }
        val mail_allowed = (legacy_readers + not_admitted_fallbacks)
            .filter { it.first.startsWith("mail/") }
            .associate { (file, call, count) -> (file to call) to count }

        assertEquals(mail_allowed, mail_sites)
    }

    @Test
    fun `the not admitted fallbacks still prefer the level two key`() {
        val mail_repository = File(source_root, "mail/MailRepository.kt").readText()
        val envelope_writer = mail_repository.substringAfter("private fun encrypt_envelope(")
        assertTrue(
            envelope_writer.indexOf("LevelTwoEnvelope.write_key(") in 0 until envelope_writer.indexOf("PasswordKdf.derive_aes_key("),
        )

        val resealer = File(source_root, "mail/sent_mail_reseal.kt").readText().substringAfter("private fun reseal_plaintext(")
        assertTrue(resealer.contains("LevelTwoEnvelope.write_key(store)"))
        assertTrue(resealer.contains("LevelTwoEnvelope.seal_b64(plaintext, level2_key)"))
    }

    @Test
    fun `the level two write key is admitted data kek only`() {
        val level2 = File(source_root, "mail/level2_envelope.kt").readText().substringAfter("fun write_key(store: SessionKeyStore)")
            .substringBefore("fun stored_sha256_hex(")
        assertTrue(level2.contains("if (!store.is_level2_admitted()) return null"))
        assertTrue(level2.contains("StorageWriteKey.write_key(store)"))
    }

    @Test
    fun `the password external account key has no production caller`() {
        assertTrue(password_key_call_sites().keys.none { it.second == "external_account_master_key" })
    }

    @Test
    fun `the single source still derives the password key`() {
        val found = password_key_call_sites()

        assertTrue(("crypto/storage_write_key.kt" to "CryptoNative.derive_storage_key") in found)
    }
}
