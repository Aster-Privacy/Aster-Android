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

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import org.astermail.android.api.ApiClient

class KtorResealApi(private val client: ApiClient) : ResealApi {

    private fun url(path: String): String = "${client.base_url}/api$path"

    override suspend fun get(path: String): ApiReply = call { client.http.get(url(path)) }

    override suspend fun post(path: String, body: JsonElement): ApiReply = call {
        client.http.post(url(path)) {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
    }

    override suspend fun put(path: String, body: JsonElement): ApiReply = call {
        client.http.put(url(path)) {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
    }

    private suspend fun call(send: suspend () -> HttpResponse): ApiReply {
        val response = try {
            send()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return ApiReply(status = null)
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        val data = text?.takeIf { it.isNotBlank() }?.let(::parse_json_text)
        return ApiReply(
            status = response.status.value,
            data = data,
            code = reply_code(data),
            retry_after_secs = response.headers["Retry-After"]?.trim()?.toLongOrNull(),
        )
    }
}
