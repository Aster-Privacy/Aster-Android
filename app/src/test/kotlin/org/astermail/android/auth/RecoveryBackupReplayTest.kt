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

package org.astermail.android.auth

import android.util.Base64
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.recovery.InitiateRecoveryResponse
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.crypto.hkdf_sha256
import org.astermail.android.storage.fake_prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryBackupReplayTest {

    private val dispatcher = StandardTestDispatcher()
    private val email = "user@astermail.org"
    private val code = "ASTER-ABCD-EFGH-IJKL"
    private lateinit var application: android.app.Application
    private lateinit var recovery_api: RecoveryApi
    private lateinit var prefs: fake_prefs

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        prefs = fake_prefs()
        application = mockk(relaxed = true)
        every { application.getSharedPreferences(any(), any()) } returns prefs
        every { application.getString(org.astermail.android.R.string.error_invalid_code) } returns
            "invalid recovery code"
        recovery_api = mockk(relaxed = true)
        coEvery { recovery_api.verifier_salts(any()) } throws RuntimeException("offline")
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
        Dispatchers.resetMain()
    }

    private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)

    private fun legacy_backup_response(identity_key: String): InitiateRecoveryResponse {
        val recovery_key = random(32)
        val code_salt = random(32)
        val code_nonce = random(12)
        val code_key = PasswordKdf.derive_aes_key(canonicalize_recovery_code(code), code_salt, 310000)
        val encrypted_recovery_key = AesGcm.encrypt(code_key, code_nonce, recovery_key)
        val backup_salt = random(16)
        val backup_nonce = random(12)
        val backup_key = hkdf_sha256(
            recovery_key,
            backup_salt,
            "Aster Mail_Recovery_Vault_v1".toByteArray(Charsets.UTF_8),
            32,
        )
        val vault = "{\"identity_key\":\"$identity_key\"}".toByteArray(Charsets.UTF_8)
        return InitiateRecoveryResponse(
            encrypted_vault_backup = b64(AesGcm.encrypt(backup_key, backup_nonce, vault)),
            vault_backup_nonce = b64(backup_nonce),
            recovery_key_salt = b64(backup_salt),
            encrypted_recovery_key = b64(encrypted_recovery_key),
            recovery_key_nonce = b64(code_nonce),
            code_salt = b64(code_salt),
            recovery_token = "token",
        )
    }

    private fun email_floor_key(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(email.toByteArray(Charsets.UTF_8))
        return "email:" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun recover_with(response: InitiateRecoveryResponse): RecoveryViewModel {
        coEvery { recovery_api.initiate(any()) } returns response
        val vm = RecoveryViewModel(application, recovery_api, mockk(relaxed = true))
        vm.compute_dispatcher = dispatcher
        vm.submit_email(email)
        vm.verify_code(code)
        return vm
    }

    @Test
    fun `recovery refuses a backup older than one this device already saved`() = runTest {
        prefs.values[email_floor_key()] = 42L

        val vm = recover_with(legacy_backup_response("replayed"))
        advanceUntilIdle()

        assertNotEquals(RecoveryStep.password, vm.state.value.step)
        assertEquals("invalid recovery code", vm.state.value.error)
    }

    @Test
    fun `recovery still opens a backup written before generations existed`() = runTest {
        val vm = recover_with(legacy_backup_response("legacy"))
        advanceUntilIdle()

        assertEquals(RecoveryStep.password, vm.state.value.step)
        assertEquals(null, vm.state.value.error)
    }
}
