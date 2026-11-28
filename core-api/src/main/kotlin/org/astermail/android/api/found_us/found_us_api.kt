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

package org.astermail.android.api.found_us

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

val found_us_sources: List<String> = listOf(
    "reddit",
    "youtube",
    "friend",
    "invite_link",
    "search_engine",
    "comparison_site",
    "privacy_directory",
    "article",
    "app_store",
    "github",
    "social_media",
    "short_video",
    "forum",
    "ai_assistant",
    "other",
)

const val found_us_other = "other"

const val found_us_max_detail_chars = 200

@Serializable
data class FoundUsStatusResponse(
    val eligible: Boolean = false,
)

@Serializable
data class FoundUsActionResponse(
    val ok: Boolean = false,
)

data class FoundUsAnswer(
    val source: String,
    val detail: String? = null,
)

private val white_space_points: Set<Int> = buildSet {
    addAll(0x09..0x0D)
    add(0x20)
    add(0x85)
    add(0xA0)
    add(0x1680)
    addAll(0x2000..0x200A)
    add(0x2028)
    add(0x2029)
    add(0x202F)
    add(0x205F)
    add(0x3000)
}

private val hidden_types: Set<Int> = setOf(
    Character.CONTROL.toInt(),
    Character.FORMAT.toInt(),
    Character.SURROGATE.toInt(),
    Character.PRIVATE_USE.toInt(),
)

private fun code_points(text: String): IntArray = text.codePoints().toArray()

private fun from_points(points: List<Int>): String {
    val builder = StringBuilder(points.size)
    points.forEach { builder.appendCodePoint(it) }
    return builder.toString()
}

private fun collapse_white_space(points: List<Int>): List<Int> {
    val out = ArrayList<Int>(points.size)
    var in_run = false
    for (point in points) {
        if (point in white_space_points) {
            if (!in_run) out.add(0x20)
            in_run = true
        } else {
            out.add(point)
            in_run = false
        }
    }
    return out
}

fun count_found_us_chars(text: String): Int = text.codePointCount(0, text.length)

fun limit_found_us_chars(text: String, max: Int = found_us_max_detail_chars): String {
    if (count_found_us_chars(text) <= max) return text
    return text.substring(0, text.offsetByCodePoints(0, max))
}

fun clean_found_us_detail(text: String): String {
    val first = collapse_white_space(code_points(text).toList())
    val visible = first.filter { Character.getType(it) !in hidden_types }
    val second = collapse_white_space(visible)
    return from_points(second).trim { it == ' ' }
}

fun found_us_finish_body(answer: FoundUsAnswer?): JsonObject {
    if (answer == null) return JsonObject(emptyMap())
    val fields = linkedMapOf<String, JsonPrimitive>("source" to JsonPrimitive(answer.source))
    if (answer.source == found_us_other && answer.detail != null) {
        fields["detail"] = JsonPrimitive(answer.detail)
    }
    return JsonObject(fields)
}

interface FoundUsApi {
    suspend fun get_status(): FoundUsStatusResponse
    suspend fun finish(answer: FoundUsAnswer?): FoundUsActionResponse
    suspend fun snooze(): FoundUsActionResponse
}

class FoundUsApiImpl(private val client: ApiClient) : FoundUsApi {
    private val base = "/api/core/v1/found-us"

    override suspend fun get_status(): FoundUsStatusResponse =
        decode_or_throw(client.http.get("${client.base_url}$base"))

    override suspend fun finish(answer: FoundUsAnswer?): FoundUsActionResponse =
        post_json("$base/finish", found_us_finish_body(answer))

    override suspend fun snooze(): FoundUsActionResponse =
        post_json("$base/later", JsonObject(emptyMap()))

    private suspend fun post_json(path: String, body: JsonObject): FoundUsActionResponse {
        client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$path") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(body)
        }
        return decode_or_throw(response)
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body()
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
