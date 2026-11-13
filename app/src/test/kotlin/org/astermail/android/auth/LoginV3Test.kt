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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.EnrollPasswordV3FinishRequest
import org.astermail.android.api.auth.EnrollPasswordV3FinishResponse
import org.astermail.android.api.auth.EnrollPasswordV3StartRequest
import org.astermail.android.api.auth.EnrollPasswordV3StartResponse
import org.astermail.android.api.auth.LoginResult
import org.astermail.android.api.auth.LoginV3FinishRequest
import org.astermail.android.api.auth.LoginV3StartRequest
import org.astermail.android.api.auth.LoginV3StartResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoginV3Test {

    private val vector: Map<String, String> = javaClass.classLoader!!
        .getResourceAsStream("login_v3_vectors.txt")!!
        .bufferedReader()
        .readLines()
        .filter { it.isNotBlank() }
        .associate { line ->
            val index = line.indexOf('=')
            line.substring(0, index) to line.substring(index + 1)
        }

    private fun bytes(key: String): ByteArray {
        val text = vector.getValue(key)
        return ByteArray(text.length / 2) { index -> text.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun seeds(vararg keys: String): () -> ByteArray {
        val queue = ArrayDeque(keys.map(::bytes))
        return { queue.removeFirst() }
    }

    private val start_request = LoginV3StartRequest(user_hash = "account", remember_me = true)
    private val signed_in: LoginResult = mockk()

    @Test
    fun the_input_matches_the_known_answer() {
        assertEquals("Zf0mb9J5M/moyYANjj6MvcSsDRNTExuGa4V9Ns6OoyA=", b64(LoginV3.derive_input(ByteArray(32))))
        assertEquals(
            "whJOV71hS32K8dRvAd3JDcqwIBL3Uy32tkblnA6EwOA=",
            b64(LoginV3.derive_input(ByteArray(32) { (it * 7 + 3).toByte() })),
        )
    }

    @Test
    fun enrollment_runs_only_when_offered_and_not_yet_enrolled() {
        assertTrue(LoginV3.should_enroll(offered = true, enrolled = false))
        assertFalse(LoginV3.should_enroll(offered = true, enrolled = true))
        assertFalse(LoginV3.should_enroll(offered = false, enrolled = false))
        assertFalse(LoginV3.should_enroll(offered = false, enrolled = true))
    }

    @Test
    fun a_sign_in_sends_the_shared_vector_messages() = runTest {
        val api = mockk<AuthApi>()
        val sent_start = slot<LoginV3StartRequest>()
        val sent_finish = slot<LoginV3FinishRequest>()
        coEvery { api.login_v3_start(capture(sent_start), "device") } returns
            LoginV3StartResponse("state", b64(bytes("credential_response")))
        coEvery { api.login_v3_finish(capture(sent_finish), "device") } returns signed_in

        val result = LoginV3.sign_in(api, bytes("password"), start_request, "device", seeds("login_start_seed"))

        assertSame(signed_in, result)
        assertEquals(b64(bytes("credential_request")), sent_start.captured.credential_request)
        assertEquals("account", sent_start.captured.user_hash)
        assertEquals(true, sent_start.captured.remember_me)
        assertEquals("android", sent_start.captured.client_platform)
        assertEquals("state", sent_finish.captured.state_id)
        assertEquals(b64(bytes("credential_finalization")), sent_finish.captured.credential_finalization)
    }

    @Test
    fun an_unavailable_server_falls_back_without_finishing() = runTest {
        val api = mockk<AuthApi>()
        coEvery { api.login_v3_start(any(), any()) } returns null

        assertNull(LoginV3.sign_in(api, bytes("password"), start_request, null, seeds("login_start_seed")))
        coVerify(exactly = 0) { api.login_v3_finish(any(), any()) }
    }

    @Test
    fun a_local_start_failure_falls_back_without_calling_the_server() = runTest {
        val api = mockk<AuthApi>()

        assertNull(LoginV3.sign_in(api, bytes("password"), start_request, null) { ByteArray(5) })
        coVerify(exactly = 0) { api.login_v3_start(any(), any()) }
    }

    @Test
    fun a_wrong_password_is_reported_as_invalid_credentials_without_finishing() = runTest {
        val api = mockk<AuthApi>()
        coEvery { api.login_v3_start(any(), any()) } returns
            LoginV3StartResponse("state", b64(bytes("unknown_account_credential_response")))

        try {
            LoginV3.sign_in(api, bytes("password"), start_request, null, seeds("login_start_seed"))
            fail("expected a rejection")
        } catch (rejected: ApiError) {
            assertSame(ApiError.InvalidCredentials, rejected)
        }
        coVerify(exactly = 0) { api.login_v3_finish(any(), any()) }
    }

    @Test
    fun a_server_refusal_at_start_is_passed_through() = runTest {
        val api = mockk<AuthApi>()
        coEvery { api.login_v3_start(any(), any()) } throws ApiError.InvalidCredentials

        try {
            LoginV3.sign_in(api, bytes("password"), start_request, null, seeds("login_start_seed"))
            fail("expected the refusal")
        } catch (refused: ApiError) {
            assertSame(ApiError.InvalidCredentials, refused)
        }
    }

    @Test
    fun an_enrollment_sends_the_shared_vector_upload() = runTest {
        val api = mockk<AuthApi>()
        val sent_start = slot<EnrollPasswordV3StartRequest>()
        val sent_finish = slot<EnrollPasswordV3FinishRequest>()
        coEvery { api.enroll_password_v3_start(capture(sent_start)) } returns
            EnrollPasswordV3StartResponse(b64(bytes("registration_response")))
        coEvery { api.enroll_password_v3_finish(capture(sent_finish)) } returns
            EnrollPasswordV3FinishResponse(login_version = 3)
        val proof = ByteArray(32) { 9 }

        val enrolled = LoginV3.enroll(
            api,
            bytes("password"),
            proof,
            seeds("registration_start_seed", "registration_finish_seed"),
        )

        assertTrue(enrolled)
        assertEquals(b64(bytes("registration_request")), sent_start.captured.registration_request)
        assertEquals(b64(bytes("registration_upload")), sent_finish.captured.registration_upload)
        assertEquals(b64(proof), sent_finish.captured.password_hash)
    }

    @Test
    fun an_enrollment_failure_is_quiet() = runTest {
        val refused = mockk<AuthApi>()
        coEvery { refused.enroll_password_v3_start(any()) } throws ApiError.NotFoundError
        assertFalse(LoginV3.enroll(refused, bytes("password"), ByteArray(32)))

        val malformed = mockk<AuthApi>()
        coEvery { malformed.enroll_password_v3_start(any()) } returns EnrollPasswordV3StartResponse("AAAA")
        assertFalse(LoginV3.enroll(malformed, bytes("password"), ByteArray(32)))
        coVerify(exactly = 0) { malformed.enroll_password_v3_finish(any()) }

        val unchanged = mockk<AuthApi>()
        coEvery { unchanged.enroll_password_v3_start(any()) } returns
            EnrollPasswordV3StartResponse(b64(bytes("registration_response")))
        coEvery { unchanged.enroll_password_v3_finish(any()) } returns EnrollPasswordV3FinishResponse()
        assertFalse(LoginV3.enroll(unchanged, bytes("password"), ByteArray(32)))
    }
}
