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
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.security.ReauthProofRequest

@Serializable
data class DnsZoneRecord(
    val id: String,
    val type: String,
    val host: String = "@",
    val value: String = "",
    val ttl: Int = 3600,
    val priority: Int? = null,
    val protected: Boolean = false,
    val protected_reason: String? = null,
)

@Serializable
data class DnsZoneLimits(
    val max_records: Int = 0,
)

@Serializable
data class DnsZoneResponse(
    val managed: Boolean = false,
    val records: List<DnsZoneRecord> = emptyList(),
    val limits: DnsZoneLimits = DnsZoneLimits(),
)

@Serializable
data class DnsZoneRecordRequest(
    val type: String,
    val host: String,
    val value: String,
    val ttl: Int,
    val priority: Int? = null,
)

@Serializable
data class DnsZoneUnlockResponse(
    val expires_in: Int = 0,
)

interface DnsZoneApi {
    suspend fun get_zone(domain_id: String): DnsZoneResponse
    suspend fun create_record(domain_id: String, request: DnsZoneRecordRequest): DnsZoneRecord?
    suspend fun update_record(domain_id: String, record_id: String, request: DnsZoneRecordRequest): DnsZoneRecord?
    suspend fun delete_record(domain_id: String, record_id: String)
    suspend fun unlock(domain_id: String, proof: ReauthProofRequest): DnsZoneUnlockResponse
}

class DnsZoneApiImpl(private val client: ApiClient) : DnsZoneApi {

    private val domains_base = "/api/addresses/v1/domains"

    private fun zone_url(domain_id: String): String =
        "${client.base_url}$domains_base/${domain_id.encodeURLPathPart()}/dns/zone"

    override suspend fun get_zone(domain_id: String): DnsZoneResponse {
        val response = client.http.get(zone_url(domain_id))
        ensure_success(response)
        return decode(response)
    }

    override suspend fun create_record(domain_id: String, request: DnsZoneRecordRequest): DnsZoneRecord? {
        val response = client.http.post("${zone_url(domain_id)}/records") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        ensure_success(response)
        return decode_record(response)
    }

    override suspend fun update_record(
        domain_id: String,
        record_id: String,
        request: DnsZoneRecordRequest,
    ): DnsZoneRecord? {
        val response = client.http.patch("${zone_url(domain_id)}/records/${record_id.encodeURLPathPart()}") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        ensure_success(response)
        return decode_record(response)
    }

    override suspend fun delete_record(domain_id: String, record_id: String) {
        val response = client.http.delete("${zone_url(domain_id)}/records/${record_id.encodeURLPathPart()}") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        ensure_success(response)
    }

    override suspend fun unlock(domain_id: String, proof: ReauthProofRequest): DnsZoneUnlockResponse {
        val response = client.http.post("${zone_url(domain_id)}/unlock") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(proof)
        }
        ensure_success(response)
        return decode(response)
    }

    private suspend fun ensure_success(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val body = try { response.body<String>() } catch (_: Throwable) { "" }
        val error = client.map_http_status(response.status.value, body, response.headers["Retry-After"])
        if (error is ApiError.RateLimited) throw with_retry_after(error, response.headers["Retry-After"])
        throw error
    }

    private suspend fun decode_record(response: HttpResponse): DnsZoneRecord? {
        val text = try { response.body<String>() } catch (_: Throwable) { "" }
        if (text.isBlank()) return null
        return try {
            val element = client.json.parseToJsonElement(text)
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
            val nested = obj["record"]
            if (nested is kotlinx.serialization.json.JsonObject) {
                client.json.decodeFromJsonElement(DnsZoneRecord.serializer(), nested)
            } else if (obj["id"] != null) {
                client.json.decodeFromJsonElement(DnsZoneRecord.serializer(), obj)
            } else {
                null
            }
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (_: Throwable) {
            null
        }
    }

    private suspend inline fun <reified T> decode(response: HttpResponse): T =
        try {
            response.body()
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (_: Throwable) {
            throw ApiError.UnknownError("decode failed")
        }
}
