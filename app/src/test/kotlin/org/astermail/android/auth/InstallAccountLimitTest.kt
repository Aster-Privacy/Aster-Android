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

import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.auth.Argon2Params
import org.astermail.android.api.auth.RegisterRequest
import org.astermail.android.api.auth.RegistrationExtras
import org.astermail.android.api.auth.registration_body
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallAccountLimitTest {

    private val client = ApiClient(
        base_url = "http://127.0.0.1:1",
        token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? = null
            override suspend fun refresh(): BearerTokens? = null
            override suspend fun clear() {}
        },
        allow_cleartext_for_test = true,
    )

    private val request = RegisterRequest(
        username = "someone",
        user_hash = "h",
        password_hash = "p",
        password_salt = "s",
        argon2_params = Argon2Params(memory = 65536, iterations = 3, parallelism = 4),
        identity_key = "i",
        signed_prekey = "k",
        signed_prekey_signature = "g",
        encrypted_vault = "v",
        vault_nonce = "n",
    )

    @Test
    fun `account limit response keeps its code and details`() {
        val err = client.map_http_status(
            403,
            """{"code":"INSTALL_ACCOUNT_LIMIT","error":"limit","details":{"effective_max":3,"paid_on_install":false,"upgrade_plan":"nova"}}""",
        )
        assertTrue(err is ApiError.ForbiddenError)
        err as ApiError.ForbiddenError
        assertEquals("INSTALL_ACCOUNT_LIMIT", err.code)
        assertEquals("nova", err.details["upgrade_plan"])
        assertEquals("3", err.details["effective_max"])
        assertTrue(is_install_account_limit(err))
    }

    @Test
    fun `other forbidden codes are not the account limit`() {
        val err = client.map_http_status(403, """{"code":"CAPTCHA_REQUIRED","error":"x"}""")
        assertFalse(is_install_account_limit(err))
        assertFalse(is_install_account_limit(ApiError.ServerError(500)))
    }

    @Test
    fun `precondition required maps to its own error with details`() {
        val err = client.map_http_status(
            428,
            """{"code":"SOME_CODE","error":"x","details":{"difficulty":22}}""",
        )
        assertTrue(err is ApiError.PreconditionRequired)
        err as ApiError.PreconditionRequired
        assertEquals("SOME_CODE", err.code)
        assertEquals("22", err.details["difficulty"])
    }

    @Test
    fun `default extension submits with no extras`() = runBlocking {
        var seen: RegistrationExtras? = null
        val result = NoRegistrationExtension.around_register({ }) { extras ->
            seen = extras
            "ok"
        }
        assertEquals("ok", result)
        assertSame(RegistrationExtras.none, seen)
        assertTrue(seen!!.headers.isEmpty())
        assertTrue(seen!!.fields.isEmpty())
    }

    @Test
    fun `body without extras matches the plain request`() {
        val plain = client.json.encodeToJsonElement(RegisterRequest.serializer(), request)
        assertEquals(plain, registration_body(client.json, request, RegistrationExtras.none))
    }

    @Test
    fun `extra fields are added but never override request fields`() {
        val extras = RegistrationExtras(
            fields = mapOf(
                "username" to JsonPrimitive("other"),
                "added" to JsonPrimitive("value"),
            ),
        )
        val body = registration_body(client.json, request, extras)
        assertEquals("someone", body["username"]!!.jsonPrimitive.content)
        assertEquals("value", body["added"]!!.jsonPrimitive.content)
        assertEquals("android", body.jsonObject["client_platform"]!!.jsonPrimitive.content)
    }
}
