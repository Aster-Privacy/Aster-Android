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

package org.astermail.android.api

import org.astermail.android.api.recovery.CompleteRecoveryRequest
import org.astermail.android.api.recovery.CompleteRecoveryResponse
import org.astermail.android.api.recovery.InitiateEmailRecoveryRequest
import org.astermail.android.api.recovery.InitiateEmailRecoveryResponse
import org.astermail.android.api.recovery.InitiateRecoveryRequest
import org.astermail.android.api.recovery.InitiateRecoveryResponse
import org.astermail.android.api.recovery.NewEmailRecoveryBackup
import org.astermail.android.api.recovery.RecoveryShareData
import org.astermail.android.api.recovery.ValidateEmailRecoveryRequest
import org.astermail.android.api.recovery.ValidateEmailRecoveryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryModelsTest {

    @Test
    fun `InitiateRecoveryRequest stores fields`() {
        val request = InitiateRecoveryRequest(
            code_hash = "ch_abc",
            email = "alice@astermail.org",
        )
        assertEquals("ch_abc", request.code_hash)
        assertEquals("alice@astermail.org", request.email)
    }

    @Test
    fun `InitiateRecoveryRequest copy`() {
        val original = InitiateRecoveryRequest(code_hash = "a", email = "b@c.d")
        val copied = original.copy(code_hash = "new_hash")
        assertEquals("new_hash", copied.code_hash)
        assertEquals("b@c.d", copied.email)
    }

    @Test
    fun `InitiateRecoveryResponse stores all fields`() {
        val response = InitiateRecoveryResponse(
            encrypted_vault_backup = "evb",
            vault_backup_nonce = "vbn",
            recovery_key_salt = "rks",
            encrypted_recovery_key = "erk",
            recovery_key_nonce = "rkn",
            code_salt = "cs",
            recovery_token = "rt",
        )
        assertEquals("evb", response.encrypted_vault_backup)
        assertEquals("vbn", response.vault_backup_nonce)
        assertEquals("rks", response.recovery_key_salt)
        assertEquals("erk", response.encrypted_recovery_key)
        assertEquals("rkn", response.recovery_key_nonce)
        assertEquals("cs", response.code_salt)
        assertEquals("rt", response.recovery_token)
    }

    @Test
    fun `InitiateRecoveryResponse copy`() {
        val original = InitiateRecoveryResponse(
            encrypted_vault_backup = "a", vault_backup_nonce = "b",
            recovery_key_salt = "c", encrypted_recovery_key = "d",
            recovery_key_nonce = "e", code_salt = "f", recovery_token = "g",
        )
        val copied = original.copy(recovery_token = "new_rt")
        assertEquals("new_rt", copied.recovery_token)
        assertEquals("a", copied.encrypted_vault_backup)
    }

    @Test
    fun `InitiateEmailRecoveryRequest stores email`() {
        val request = InitiateEmailRecoveryRequest(email = "alice@astermail.org")
        assertEquals("alice@astermail.org", request.email)
    }

    @Test
    fun `InitiateEmailRecoveryResponse stores success`() {
        val response_true = InitiateEmailRecoveryResponse(success = true)
        assertTrue(response_true.success)

        val response_false = InitiateEmailRecoveryResponse(success = false)
        assertFalse(response_false.success)
    }

    @Test
    fun `ValidateEmailRecoveryRequest stores token`() {
        val request = ValidateEmailRecoveryRequest(token = "verify_token_123")
        assertEquals("verify_token_123", request.token)
    }

    @Test
    fun `ValidateEmailRecoveryResponse stores all fields`() {
        val response = ValidateEmailRecoveryResponse(
            encrypted_vault_backup = "evb",
            vault_backup_nonce = "vbn",
            vault_backup_salt = "vbs",
            email_vault_key = "evk",
            recovery_token = "rt",
        )
        assertEquals("evb", response.encrypted_vault_backup)
        assertEquals("vbn", response.vault_backup_nonce)
        assertEquals("vbs", response.vault_backup_salt)
        assertEquals("evk", response.email_vault_key)
        assertEquals("rt", response.recovery_token)
    }

    @Test
    fun `ValidateEmailRecoveryResponse copy`() {
        val original = ValidateEmailRecoveryResponse(
            encrypted_vault_backup = "a", vault_backup_nonce = "b",
            vault_backup_salt = "c", email_vault_key = "d", recovery_token = "e",
        )
        val copied = original.copy(email_vault_key = "new_key")
        assertEquals("new_key", copied.email_vault_key)
        assertEquals("a", copied.encrypted_vault_backup)
    }

    @Test
    fun `RecoveryShareData stores all fields`() {
        val share = RecoveryShareData(
            code_hash = "ch",
            code_salt = "cs",
            encrypted_recovery_key = "erk",
            recovery_key_nonce = "rkn",
        )
        assertEquals("ch", share.code_hash)
        assertEquals("cs", share.code_salt)
        assertEquals("erk", share.encrypted_recovery_key)
        assertEquals("rkn", share.recovery_key_nonce)
    }

    @Test
    fun `RecoveryShareData copy`() {
        val original = RecoveryShareData(
            code_hash = "a", code_salt = "b",
            encrypted_recovery_key = "c", recovery_key_nonce = "d",
        )
        val copied = original.copy(code_hash = "new_ch")
        assertEquals("new_ch", copied.code_hash)
        assertEquals("b", copied.code_salt)
    }

    @Test
    fun `NewEmailRecoveryBackup stores all fields`() {
        val backup = NewEmailRecoveryBackup(
            encrypted_vault_backup = "evb",
            vault_backup_nonce = "vbn",
            vault_backup_salt = "vbs",
            email_vault_key = "evk",
        )
        assertEquals("evb", backup.encrypted_vault_backup)
        assertEquals("vbn", backup.vault_backup_nonce)
        assertEquals("vbs", backup.vault_backup_salt)
        assertEquals("evk", backup.email_vault_key)
    }

    @Test
    fun `CompleteRecoveryRequest required fields`() {
        val shares = listOf(
            RecoveryShareData(
                code_hash = "ch1", code_salt = "cs1",
                encrypted_recovery_key = "erk1", recovery_key_nonce = "rkn1",
            ),
        )
        val request = CompleteRecoveryRequest(
            recovery_token = "rt",
            new_password_hash = "nph",
            new_password_salt = "nps",
            new_encrypted_vault = "nev",
            new_vault_nonce = "nvn",
            new_recovery_shares = shares,
            new_encrypted_vault_backup = "nevb",
            new_vault_backup_nonce = "nvbn",
            new_recovery_key_salt = "nrks",
        )
        assertEquals("rt", request.recovery_token)
        assertEquals("nph", request.new_password_hash)
        assertEquals("nps", request.new_password_salt)
        assertEquals("nev", request.new_encrypted_vault)
        assertEquals("nvn", request.new_vault_nonce)
        assertEquals(1, request.new_recovery_shares.size)
        assertEquals("ch1", request.new_recovery_shares[0].code_hash)
        assertEquals("nevb", request.new_encrypted_vault_backup)
        assertEquals("nvbn", request.new_vault_backup_nonce)
        assertEquals("nrks", request.new_recovery_key_salt)
        assertNull(request.new_email_recovery_backup)
    }

    @Test
    fun `CompleteRecoveryRequest with email backup`() {
        val backup = NewEmailRecoveryBackup(
            encrypted_vault_backup = "evb",
            vault_backup_nonce = "vbn",
            vault_backup_salt = "vbs",
            email_vault_key = "evk",
        )
        val request = CompleteRecoveryRequest(
            recovery_token = "rt",
            new_password_hash = "nph",
            new_password_salt = "nps",
            new_encrypted_vault = "nev",
            new_vault_nonce = "nvn",
            new_recovery_shares = emptyList(),
            new_encrypted_vault_backup = "nevb",
            new_vault_backup_nonce = "nvbn",
            new_recovery_key_salt = "nrks",
            new_email_recovery_backup = backup,
        )
        assertEquals("evb", request.new_email_recovery_backup!!.encrypted_vault_backup)
    }

    @Test
    fun `CompleteRecoveryRequest with empty shares`() {
        val request = CompleteRecoveryRequest(
            recovery_token = "rt",
            new_password_hash = "nph",
            new_password_salt = "nps",
            new_encrypted_vault = "nev",
            new_vault_nonce = "nvn",
            new_recovery_shares = emptyList(),
            new_encrypted_vault_backup = "nevb",
            new_vault_backup_nonce = "nvbn",
            new_recovery_key_salt = "nrks",
        )
        assertTrue(request.new_recovery_shares.isEmpty())
    }

    @Test
    fun `CompleteRecoveryRequest with multiple shares`() {
        val shares = listOf(
            RecoveryShareData("ch1", "cs1", "erk1", "rkn1"),
            RecoveryShareData("ch2", "cs2", "erk2", "rkn2"),
            RecoveryShareData("ch3", "cs3", "erk3", "rkn3"),
        )
        val request = CompleteRecoveryRequest(
            recovery_token = "rt",
            new_password_hash = "nph",
            new_password_salt = "nps",
            new_encrypted_vault = "nev",
            new_vault_nonce = "nvn",
            new_recovery_shares = shares,
            new_encrypted_vault_backup = "nevb",
            new_vault_backup_nonce = "nvbn",
            new_recovery_key_salt = "nrks",
        )
        assertEquals(3, request.new_recovery_shares.size)
        assertEquals("ch3", request.new_recovery_shares[2].code_hash)
    }

    @Test
    fun `CompleteRecoveryResponse stores success`() {
        val response_true = CompleteRecoveryResponse(success = true)
        assertTrue(response_true.success)

        val response_false = CompleteRecoveryResponse(success = false)
        assertFalse(response_false.success)
    }

    @Test
    fun `RecoveryShareData equality`() {
        val a = RecoveryShareData("ch", "cs", "erk", "rkn")
        val b = RecoveryShareData("ch", "cs", "erk", "rkn")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `NewEmailRecoveryBackup equality`() {
        val a = NewEmailRecoveryBackup("a", "b", "c", "d")
        val b = NewEmailRecoveryBackup("a", "b", "c", "d")
        assertEquals(a, b)
    }
}
