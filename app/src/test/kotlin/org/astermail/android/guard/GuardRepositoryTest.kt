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

package org.astermail.android.guard

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_DEVICE_REQUIRED_CODE
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardChallenge
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.storage.InMemoryPrefs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardRepositoryTest {
    private val user = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val nonce = ByteArray(32) { it.toByte() }
    private val nonce_text = DeviceEnvelope.base64url_encode(nonce)
    private val subject = ByteArray(32) { (0xa0 + it).toByte() }

    private fun api(): GuardApi = mockk<GuardApi>().also {
        coEvery { it.challenge() } returns GuardChallenge(nonce_text)
    }

    private fun repo(api: GuardApi) = GuardRepository(api, GuardDeviceStore(InMemoryPrefs())) { user }

    @Test
    fun signed_challenges_once_and_signs_the_action_message() = runTest {
        val api = api()
        var seen: ByteArray? = null
        val signer = guard_signer { msg ->
            seen = msg
            guard_sign_result.signed(byteArrayOf(1, 2, 3))
        }
        val action = repo(api).signed_with(signer, GuardActionCode.APPROVE, subject)!!
        coVerify(exactly = 1) { api.challenge() }
        assertArrayEquals(GuardDigests.action_message(GuardActionCode.APPROVE, user, nonce, subject), seen)
        assertEquals(GuardActionCode.APPROVE.wire.toByte(), seen!![21])
        assertEquals(nonce_text, action.nonce)
        assertEquals(DeviceEnvelope.base64url_encode(byteArrayOf(1, 2, 3)), action.signature)
        assertNull(action.proof)
    }

    @Test
    fun proof_is_passed_through() = runTest {
        val proof = StepUpProof.Password("h", null)
        val action = repo(api()).signed_with({ guard_sign_result.signed(byteArrayOf(9)) }, GuardActionCode.SWITCHES, subject, proof)!!
        assertEquals(proof, action.proof)
    }

    @Test
    fun cancel_returns_null() = runTest {
        assertNull(repo(api()).signed_with({ guard_sign_result.canceled }, GuardActionCode.DENY, subject))
    }

    @Test(expected = GuardKeyLostException::class)
    fun key_lost_throws() = runTest {
        repo(api()).signed_with({ guard_sign_result.key_lost }, GuardActionCode.DENY, subject)
    }

    @Test(expected = GuardNoDeviceException::class)
    fun device_required_from_challenge_throws_no_device() = runTest {
        val api = mockk<GuardApi>()
        coEvery { api.challenge() } throws ApiError.ForbiddenError("x", GUARD_DEVICE_REQUIRED_CODE)
        repo(api).signed_with({ guard_sign_result.signed(byteArrayOf(1)) }, GuardActionCode.PANIC, GuardDigests.PANIC_SUBJECT)
    }
}
