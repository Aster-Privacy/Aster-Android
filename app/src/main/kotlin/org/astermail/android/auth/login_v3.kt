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

import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.EnrollPasswordV3FinishRequest
import org.astermail.android.api.auth.EnrollPasswordV3StartRequest
import org.astermail.android.api.auth.LoginResult
import org.astermail.android.api.auth.LoginV3FinishRequest
import org.astermail.android.api.auth.LoginV3StartRequest
import org.astermail.android.crypto.hkdf_sha256
import org.astermail.android.crypto.opaque.LoginV3Client
import org.astermail.android.crypto.opaque.LoginV3Start

object LoginV3 {
    const val login_version = 3
    private const val input_bytes = 32
    private val input_info = "aster-login-v3".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    fun derive_input(v1_proof: ByteArray): ByteArray =
        hkdf_sha256(v1_proof, ByteArray(input_bytes), input_info, input_bytes)

    fun should_enroll(offered: Boolean, enrolled: Boolean): Boolean = offered && !enrolled

    suspend fun sign_in(
        auth_api: AuthApi,
        input: ByteArray,
        request: LoginV3StartRequest,
        trusted_device_token: String?,
        new_seed: () -> ByteArray = ::random_seed,
    ): LoginResult? {
        val started = compute { LoginV3Client.login_start(input, new_seed()) } ?: return null
        val challenge = auth_api.login_v3_start(
            request.copy(credential_request = encode(started.message)),
            trusted_device_token,
        ) ?: return null
        val finalization = compute {
            LoginV3Client.login_finish(started, input, decode(challenge.credential_response))
        } ?: throw ApiError.InvalidCredentials
        return auth_api.login_v3_finish(
            LoginV3FinishRequest(
                state_id = challenge.state_id,
                credential_finalization = encode(finalization),
            ),
            trusted_device_token,
        )
    }

    suspend fun enroll(
        auth_api: AuthApi,
        input: ByteArray,
        password_proof: ByteArray,
        new_seed: () -> ByteArray = ::random_seed,
    ): Boolean = try {
        val started: LoginV3Start? = compute { LoginV3Client.registration_start(input, new_seed()) }
        val answer = started?.let {
            auth_api.enroll_password_v3_start(EnrollPasswordV3StartRequest(encode(it.message)))
        }
        val upload = if (started == null || answer == null) {
            null
        } else {
            compute {
                LoginV3Client.registration_finish(
                    started,
                    input,
                    decode(answer.registration_response),
                    new_seed(),
                )
            }
        }
        upload != null && auth_api.enroll_password_v3_finish(
            EnrollPasswordV3FinishRequest(
                password_hash = encode(password_proof),
                registration_upload = encode(upload),
            ),
        ).login_version == login_version
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }

    private suspend fun <T : Any> compute(block: () -> T): T? = withContext(Dispatchers.Default) {
        try {
            block()
        } catch (_: Throwable) {
            null
        }
    }

    private fun random_seed(): ByteArray = ByteArray(LoginV3Client.SEED_LEN).also(random::nextBytes)

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}
