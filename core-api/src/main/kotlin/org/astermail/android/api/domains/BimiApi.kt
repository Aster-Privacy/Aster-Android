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

package org.astermail.android.api.domains

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

const val BIMI_MAX_LOGO_BYTES = 64 * 1024
const val BIMI_LOGO_INVALID_CODE = "BIMI_LOGO_INVALID"
const val BIMI_DOMAIN_NOT_ACTIVE_CODE = "BIMI_DOMAIN_NOT_ACTIVE"
const val BIMI_LOGO_REQUIRED_CODE = "BIMI_LOGO_REQUIRED"

class BimiLogoInvalid(val errors: List<String>) : Exception("bimi logo invalid")

enum class BimiState { off, draft, pending, live, attention, external }

fun bimi_state_from(raw: String?): BimiState? {
    if (raw == null) return null
    return BimiState.entries.firstOrNull { it.name == raw } ?: BimiState.draft
}

val bimi_record_statuses = setOf("missing", "published", "conflict", "external")

val bimi_dmarc_statuses = setOf(
    "ready",
    "missing",
    "invalid",
    "not_enforced",
    "partial",
    "subdomain_policy_none",
    "organization_not_enforced",
)

@Serializable
data class BimiRecord(
    val record_type: String = "TXT",
    val host: String = "",
    val value: String = "",
)

@Serializable
data class BimiView(
    val state: String = "off",
    val domain_active: Boolean = false,
    val managed_dns: Boolean = false,
    val logo_url: String? = null,
    val preview_png: String? = null,
    val record: BimiRecord? = null,
    val record_status: String? = null,
    val dmarc_status: String? = null,
    val last_checked_at: String? = null,
    val last_live_at: String? = null,
) {
    val bimi_state: BimiState get() = bimi_state_from(state) ?: BimiState.draft
    val known_record_status: String? get() = record_status?.takeIf { it in bimi_record_statuses }
    val known_dmarc_status: String? get() = dmarc_status?.takeIf { it in bimi_dmarc_statuses }
}

@Serializable
data class BimiUploadResponse(
    val bimi: BimiView,
    val adjustments: List<String> = emptyList(),
)

interface BimiApi {
    suspend fun get_bimi(domain_id: String): BimiView
    suspend fun upload_logo(domain_id: String, svg: ByteArray): BimiUploadResponse
    suspend fun publish(domain_id: String): BimiView
    suspend fun check(domain_id: String): BimiView
    suspend fun turn_off(domain_id: String): BimiView
}

class BimiApiImpl(private val client: ApiClient) : BimiApi {
    private val domains_base = "/api/addresses/v1/domains"

    private fun bimi_url(domain_id: String) =
        "${client.base_url}$domains_base/${domain_id.encodeURLPathPart()}/bimi"

    override suspend fun get_bimi(domain_id: String): BimiView {
        val response = client.http.get(bimi_url(domain_id))
        return decode_or_throw(response)
    }

    override suspend fun upload_logo(domain_id: String, svg: ByteArray): BimiUploadResponse {
        val response = client.http.put("${bimi_url(domain_id)}/logo") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(ByteArrayContent(svg, ContentType("image", "svg+xml")))
        }
        return decode_or_throw(response)
    }

    override suspend fun publish(domain_id: String): BimiView {
        val response = client.http.post("${bimi_url(domain_id)}/publish") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun check(domain_id: String): BimiView {
        val response = client.http.post("${bimi_url(domain_id)}/check") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun turn_off(domain_id: String): BimiView {
        val response = client.http.delete(bimi_url(domain_id)) {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    private fun parse_logo_errors(body: String): List<String>? {
        if (body.isBlank()) return null
        return try {
            val obj = client.json.parseToJsonElement(body) as? JsonObject ?: return null
            if (obj["code"]?.jsonPrimitive?.content != BIMI_LOGO_INVALID_CODE) return null
            val details = obj["details"] as? JsonObject
            val errors = details?.get("errors") as? JsonArray
            errors?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        } catch (_: Throwable) {
            null
        }
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            if (response.status.value == 422) {
                parse_logo_errors(body)?.let { throw BimiLogoInvalid(it) }
            }
            val error = client.map_http_status(response.status.value, body)
            if (error is ApiError.RateLimited) throw with_retry_after(error, response.headers["Retry-After"])
            throw error
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
