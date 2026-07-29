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

package org.astermail.android.api.aliases

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
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

const val SENDER_PIN_MODE_OFF = 0
const val SENDER_PIN_MODE_LOCK_FIRST = 1
const val SENDER_PIN_MODE_ALLOWLIST = 2

@Serializable
data class AliasStatsResponse(
    val received: Long = 0,
    val forwarded: Long = 0,
    val blocked: Long = 0,
    val replied: Long = 0,
    val distinct_senders: Long = 0,
    val created_at: String = "",
    val last_sender_at: String? = null,
    val last_sender_encrypted: String? = null,
    val last_sender_nonce: String? = null,
)

@Serializable
data class AliasPin(
    val id: String,
    val sender_hash: String = "",
    val encrypted_sender: String? = null,
    val sender_nonce: String? = null,
    val is_blocked: Boolean = false,
    val created_at: String = "",
)

@Serializable
data class ListPinsResponse(
    val pins: List<AliasPin> = emptyList(),
    val mode: Int = SENDER_PIN_MODE_OFF,
)

@Serializable
data class AddPinRequest(
    val sender_hash: String,
    val encrypted_sender: String? = null,
    val sender_nonce: String? = null,
    val is_blocked: Boolean = false,
)

@Serializable
data class SetPinModeRequest(val mode: Int)

@Serializable
data class AliasContact(
    val id: String,
    val contact_hash: String = "",
    val reverse_alias_hash: String = "",
    val encrypted_contact: String? = null,
    val contact_nonce: String? = null,
    val is_blocked: Boolean = false,
    val created_at: String = "",
)

@Serializable
data class AliasContactListResponse(
    val contacts: List<AliasContact> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class CreateAliasContactRequest(
    val alias_id: String,
    val contact_hash: String,
    val reverse_alias_hash: String,
    val encrypted_contact: String? = null,
    val contact_nonce: String? = null,
)

@Serializable
data class CreateAliasContactResponse(
    val id: String = "",
    val success: Boolean = false,
)

@Serializable
data class BlockAliasContactRequest(val is_blocked: Boolean)

@Serializable
data class AliasDeliveryEvent(
    val id: String,
    val blocked_reason: String = "",
    val created_at: String = "",
)

@Serializable
data class AliasDeliveryLogResponse(
    val events: List<AliasDeliveryEvent> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class AliasRuleCondition(
    val field: String,
    val operator: String,
    val value: String,
)

@Serializable
data class AliasRuleActions(
    val block: Boolean? = null,
    val to_trash: Boolean? = null,
    val label: String? = null,
    val banner: String? = null,
    val subject_mask: String? = null,
)

@Serializable
data class AliasRule(
    val id: String,
    val priority: Int = 0,
    val conditions: List<AliasRuleCondition> = emptyList(),
    val actions: AliasRuleActions = AliasRuleActions(),
    val is_enabled: Boolean = true,
)

@Serializable
data class AliasRulesListResponse(
    val rules: List<AliasRule> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class CreateAliasRuleRequest(
    val conditions: List<AliasRuleCondition>,
    val actions: AliasRuleActions,
    val priority: Int = 0,
    val is_enabled: Boolean = true,
)

@Serializable
data class UpdateAliasRuleRequest(
    val conditions: List<AliasRuleCondition>? = null,
    val actions: AliasRuleActions? = null,
    val priority: Int? = null,
    val is_enabled: Boolean? = null,
)

interface AliasDetailApi {
    suspend fun get_stats(alias_id: String): AliasStatsResponse
    suspend fun list_pins(alias_id: String): ListPinsResponse
    suspend fun add_pin(alias_id: String, request: AddPinRequest)
    suspend fun delete_pin(alias_id: String, pin_id: String)
    suspend fun set_pin_mode(alias_id: String, mode: Int)
    suspend fun list_contacts(alias_id: String): AliasContactListResponse
    suspend fun create_contact(alias_id: String, request: CreateAliasContactRequest): CreateAliasContactResponse
    suspend fun delete_contact(alias_id: String, contact_id: String)
    suspend fun set_contact_blocked(alias_id: String, contact_id: String, is_blocked: Boolean)
    suspend fun get_delivery_log(alias_id: String): AliasDeliveryLogResponse
    suspend fun list_rules(alias_id: String): AliasRulesListResponse
    suspend fun create_rule(alias_id: String, request: CreateAliasRuleRequest)
    suspend fun update_rule(alias_id: String, rule_id: String, request: UpdateAliasRuleRequest)
    suspend fun delete_rule(alias_id: String, rule_id: String)
}

private const val aliases_base = "/api/addresses/v1/aliases"

class AliasDetailApiImpl(private val client: ApiClient) : AliasDetailApi {

    override suspend fun get_stats(alias_id: String): AliasStatsResponse =
        decode_or_throw(client.http.get("${client.base_url}$aliases_base/$alias_id/stats"))

    override suspend fun list_pins(alias_id: String): ListPinsResponse =
        decode_or_throw(client.http.get("${client.base_url}$aliases_base/$alias_id/pins"))

    override suspend fun add_pin(alias_id: String, request: AddPinRequest) {
        val response = client.http.post("${client.base_url}$aliases_base/$alias_id/pins") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        throw_unless_successful(response)
    }

    override suspend fun delete_pin(alias_id: String, pin_id: String) {
        val response = client.http.delete("${client.base_url}$aliases_base/$alias_id/pins/$pin_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        throw_unless_successful(response)
    }

    override suspend fun set_pin_mode(alias_id: String, mode: Int) {
        val response = client.http.patch("${client.base_url}$aliases_base/$alias_id/pin-mode") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(SetPinModeRequest(mode))
        }
        throw_unless_successful(response)
    }

    override suspend fun list_contacts(alias_id: String): AliasContactListResponse =
        decode_or_throw(client.http.get("${client.base_url}$aliases_base/$alias_id/contacts"))

    override suspend fun create_contact(
        alias_id: String,
        request: CreateAliasContactRequest,
    ): CreateAliasContactResponse {
        val response = client.http.post("${client.base_url}$aliases_base/$alias_id/contacts") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_contact(alias_id: String, contact_id: String) {
        val response = client.http.delete("${client.base_url}$aliases_base/$alias_id/contacts/$contact_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        throw_unless_successful(response)
    }

    override suspend fun set_contact_blocked(alias_id: String, contact_id: String, is_blocked: Boolean) {
        val response = client.http.post("${client.base_url}$aliases_base/$alias_id/contacts/$contact_id/block") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(BlockAliasContactRequest(is_blocked))
        }
        throw_unless_successful(response)
    }

    override suspend fun get_delivery_log(alias_id: String): AliasDeliveryLogResponse =
        decode_or_throw(client.http.get("${client.base_url}$aliases_base/$alias_id/delivery-log"))

    override suspend fun list_rules(alias_id: String): AliasRulesListResponse =
        decode_or_throw(client.http.get("${client.base_url}$aliases_base/$alias_id/rules"))

    override suspend fun create_rule(alias_id: String, request: CreateAliasRuleRequest) {
        val response = client.http.post("${client.base_url}$aliases_base/$alias_id/rules") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        throw_unless_successful(response)
    }

    override suspend fun update_rule(alias_id: String, rule_id: String, request: UpdateAliasRuleRequest) {
        val response = client.http.patch("${client.base_url}$aliases_base/$alias_id/rules/$rule_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        throw_unless_successful(response)
    }

    override suspend fun delete_rule(alias_id: String, rule_id: String) {
        val response = client.http.delete("${client.base_url}$aliases_base/$alias_id/rules/$rule_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        throw_unless_successful(response)
    }

    private suspend fun throw_unless_successful(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val body = try { response.body<String>() } catch (_: Throwable) { "" }
        throw client.map_http_status(response.status.value, body)
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
