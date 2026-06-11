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

package org.astermail.android.api.labels

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class LabelItem(
    val id: String,
    val label_token: String,
    val encrypted_name: String? = null,
    val name_nonce: String? = null,
    val encrypted_color: String? = null,
    val color_nonce: String? = null,
    val encrypted_icon: String? = null,
    val icon_nonce: String? = null,
    val is_system: Boolean = false,
    val is_locked: Boolean = false,
    val folder_type: String = "folder",
    val sort_order: Int = 0,
    val parent_token: String? = null,
    val item_count: Long? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class LabelsListResponse(
    val labels: List<LabelItem> = emptyList(),
    val total: Long = 0,
    val has_more: Boolean = false,
)

@Serializable
data class CreateLabelRequest(
    val label_token: String,
    val encrypted_name: String,
    val name_nonce: String,
    val encrypted_color: String? = null,
    val color_nonce: String? = null,
    val encrypted_icon: String? = null,
    val icon_nonce: String? = null,
    val folder_type: String = "label",
    val sort_order: Int? = null,
    val parent_token: String? = null,
)

@Serializable
data class CreateLabelResponse(
    val id: String? = null,
    val label_token: String? = null,
    val success: Boolean = false,
)

@Serializable
data class UpdateLabelRequest(
    val encrypted_name: String? = null,
    val name_nonce: String? = null,
    val encrypted_color: String? = null,
    val color_nonce: String? = null,
    val encrypted_icon: String? = null,
    val icon_nonce: String? = null,
    val sort_order: Int? = null,
)

@Serializable
data class ReferralInfoResponse(
    val referral_link: String = "",
    val referral_code: String = "",
    val total_referrals: Long = 0,
    val pending_referrals: Long = 0,
    val completed_referrals: Long = 0,
    val months_earned: Long = 0,
    val credit_balance_cents: Long = 0,
    val credits_earned_cents: Long = 0,
    val commission_earned_cents: Long = 0,
    val earned_install_ios_cents: Long = 0,
    val earned_install_android_cents: Long = 0,
    val earned_install_desktop_cents: Long = 0,
)

interface LabelsApi {
    suspend fun list_labels(include_counts: Boolean = true, folder_type: String? = null): LabelsListResponse
    suspend fun create_label(request: CreateLabelRequest): CreateLabelResponse
    suspend fun update_label(label_id: String, request: UpdateLabelRequest)
    suspend fun delete_label(label_id: String)
    suspend fun get_referral_info(): ReferralInfoResponse
}

class LabelsApiImpl(private val client: ApiClient) : LabelsApi {
    private val labels_base = "/api/mail/v1/labels"
    private val billing_base = "/api/payments/v1"

    override suspend fun list_labels(include_counts: Boolean, folder_type: String?): LabelsListResponse {
        val response = client.http.get("${client.base_url}$labels_base") {
            parameter("include_counts", include_counts)
            folder_type?.let { parameter("folder_type", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun create_label(request: CreateLabelRequest): CreateLabelResponse {
        val response = client.http.post("${client.base_url}$labels_base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_label(label_id: String, request: UpdateLabelRequest) {
        val response = client.http.put("${client.base_url}$labels_base/$label_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun delete_label(label_id: String) {
        val response = client.http.delete("${client.base_url}$labels_base/$label_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun get_referral_info(): ReferralInfoResponse {
        val response = client.http.get("${client.base_url}$billing_base/referrals")
        return decode_or_throw(response)
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body()
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
