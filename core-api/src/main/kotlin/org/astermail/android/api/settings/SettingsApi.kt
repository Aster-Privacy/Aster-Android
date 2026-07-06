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

package org.astermail.android.api.settings

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class SessionInfo(
    val id: String,
    val device_type: String = "",
    val browser: String = "",
    val os: String = "",
    val last_active: String? = null,
    val created_at: String? = null,
    val is_current: Boolean = false,
)

@Serializable
data class SessionListResponse(
    val sessions: List<SessionInfo> = emptyList(),
)

@Serializable
data class ChangePasswordRequest(
    val current_password_hash: String,
    val new_password_hash: String,
    val new_password_salt: String,
    val new_encrypted_vault: String,
    val new_vault_nonce: String,
)

@Serializable
data class ChangePasswordResponse(
    val success: Boolean = true,
    val message: String? = null,
    val csrf_token: String? = null,
    val access_token: String? = null,
)

@Serializable
data class UpdateProfileColorRequest(
    val profile_color: String,
)

@Serializable
data class UpdateProfileColorResponse(
    val success: Boolean = true,
    val profile_color: String? = null,
)

@Serializable
data class SecurityStatusResponse(
    @kotlinx.serialization.SerialName("two_factor_enabled")
    val totp_enabled: Boolean = false,
    val recovery_email_set: Boolean = false,
    val recovery_email_verified: Boolean = false,
    @kotlinx.serialization.SerialName("last_password_change")
    val password_last_changed: String? = null,
    val hardware_keys_count: Int = 0,
)

@Serializable
data class TotpStatusResponse(
    val enabled: Boolean = false,
    val backup_codes_remaining: Int = 0,
)

@Serializable
private data class BlockRequest(val address: String)

@Serializable
private data class UnblockRequest(val address: String)

@Serializable
data class BlockedSenderInfo(
    val address: String = "",
    val blocked_count: Int = 0,
    val created_at: String? = null,
)

@Serializable
data class BlockedSendersResponse(
    val blocked_senders: List<BlockedSenderInfo> = emptyList(),
)

@Serializable
data class AliasInfo(
    val id: String,
    val encrypted_local_part: String = "",
    val local_part_nonce: String = "",
    val encrypted_display_name: String? = null,
    val display_name_nonce: String? = null,
    val alias_address_hash: String = "",
    val domain: String = "",
    val is_enabled: Boolean = true,
    val is_random: Boolean = false,
    val profile_picture: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val decryption_failed: Boolean = false,
) {
    val address: String get() = when {
        decryption_failed -> if (domain.isNotBlank()) "@$domain" else id
        domain.isNotBlank() && encrypted_local_part.isNotBlank() -> "$encrypted_local_part@$domain"
        else -> id
    }
}

@Serializable
data class AliasListResponse(
    val aliases: List<AliasInfo> = emptyList(),
    val total: Long = 0,
    val has_more: Boolean = false,
    val max_aliases: Int = 0,
)

@Serializable
data class CustomDomainAddressInfo(
    val id: String,
    val domain_name: String,
    val encrypted_local_part: String = "",
    val local_part_nonce: String = "",
    val local_part_hash: String = "",
    val is_enabled: Boolean = true,
    val decryption_failed: Boolean = false,
) {
    val address: String get() = when {
        decryption_failed -> if (domain_name.isNotBlank()) "@$domain_name" else id
        domain_name.isNotBlank() && encrypted_local_part.isNotBlank() -> "$encrypted_local_part@$domain_name"
        else -> id
    }
}

@Serializable
data class AllDomainAddressesResponse(
    val addresses: List<CustomDomainAddressInfo> = emptyList(),
)

@Serializable
data class DeletedAliasInfo(
    val id: String,
    val original_alias_id: String = "",
    val encrypted_local_part: String = "",
    val local_part_nonce: String = "",
    val encrypted_display_name: String? = null,
    val display_name_nonce: String? = null,
    val domain: String = "",
    val is_random: Boolean = false,
    val deleted_at: String = "",
)

@Serializable
data class ListDeletedAliasesResponse(
    val aliases: List<DeletedAliasInfo> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class CreateAliasResponse(
    val id: String = "",
    val success: Boolean = false,
)

@Serializable
data class CheckAliasAvailabilityRequest(
    val alias_address_hash: String,
    val routing_address_hash: String,
)

@Serializable
data class CheckAliasAvailabilityResponse(
    val available: Boolean = false,
)

@Serializable
data class AliasDirectory(
    val id: String = "",
    val directory_hash: String = "",
    val encrypted_label: String? = null,
    val label_nonce: String? = null,
    val domain: String = "",
    val auto_create_enabled: Boolean = true,
    val color: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val decrypted_label: String = "",
)

@Serializable
data class ListDirectoriesResponse(
    val directories: List<AliasDirectory> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class CreateDirectoryRequest(
    val directory_hash: String,
    val legacy_hash: String? = null,
    val encrypted_label: String? = null,
    val label_nonce: String? = null,
    val domain: String,
    val auto_create_enabled: Boolean = true,
    val captcha_token: String? = null,
)

@Serializable
data class CreateDirectoryResponse(
    val id: String = "",
    val success: Boolean = false,
)

@Serializable
data class DirectoryAvailabilityRequest(
    val directory_hash: String,
    val legacy_hash: String? = null,
    val domain: String? = null,
)

@Serializable
data class DirectoryAvailabilityResponse(
    val available: Boolean = false,
)

@Serializable
data class UpdateDirectoryRequest(
    val auto_create_enabled: Boolean,
)

@Serializable
data class AliasPreferences(
    val alias_sender_format: String? = null,
    val readable_reverse_aliases: Boolean? = null,
    val alias_always_expand: Boolean? = null,
    val alias_unsubscribe_action: String? = null,
    val alias_disabled_response: String? = null,
    val alias_delete_action: String? = null,
)

@Serializable
data class UpdateAliasPreferencesRequest(
    val alias_sender_format: String? = null,
    val readable_reverse_aliases: Boolean? = null,
    val alias_always_expand: Boolean? = null,
    val alias_unsubscribe_action: String? = null,
    val alias_disabled_response: String? = null,
    val alias_delete_action: String? = null,
)

@Serializable
data class CreateAliasRequest(
    val encrypted_local_part: String,
    val local_part_nonce: String,
    val alias_address_hash: String,
    val routing_address_hash: String? = null,
    val domain: String,
    val encrypted_display_name: String? = null,
    val display_name_nonce: String? = null,
    val encrypted_note: String? = null,
    val note_nonce: String? = null,
    val captcha_token: String? = null,
)

@Serializable
data class UpdateAliasRequest(
    val is_enabled: Boolean? = null,
    val display_name: String? = null,
)

@Serializable
data class CustomDomain(
    val id: String,
    val domain_name: String,
    val status: String = "",
    val txt_verified: Boolean = false,
    val mx_verified: Boolean = false,
    val spf_verified: Boolean = false,
    val dkim_verified: Boolean = false,
    val dmarc_configured: Boolean = false,
    val catch_all_enabled: Boolean = false,
    val is_primary: Boolean = false,
    val verification_token: String = "",
    val created_at: String = "",
    val verified_at: String? = null,
)

@Serializable
data class DomainListResponse(
    val domains: List<CustomDomain> = emptyList(),
)

@Serializable
data class AddDomainRequest(
    val domain_name: String,
    val captcha_token: String? = null,
)

@Serializable
data class UpdateDomainRequest(
    val catch_all_enabled: Boolean? = null,
)

@Serializable
data class DnsRecord(
    @SerialName("record_type") val type: String,
    @SerialName("host") val name: String,
    val value: String,
    @SerialName("is_verified") val verified: Boolean = false,
)

@Serializable
data class DnsRecordsResponse(
    val records: List<DnsRecord> = emptyList(),
)

@Serializable
data class StorageOverview(
    @SerialName("total_used_bytes")
    val used_bytes: Long = 0,
    @SerialName("total_limit_bytes")
    val total_bytes: Long = 0,
    val percentage_used: Double = 0.0,
    val is_over_limit: Boolean = false,
    val addon_bytes: Long = 0,
)

@Serializable
data class SubscriptionPlanNested(
    val id: String? = null,
    val code: String? = null,
    val name: String? = null,
    val price_cents: Int = 0,
    val billing_period: String? = null,
    val storage_limit_bytes: Long = 0,
)

@Serializable
data class SubscriptionInfo(
    val plan_name: String? = null,
    val status: String? = null,
    val amount: Int = 0,
    val currency: String? = "usd",
    val interval: String? = null,
    val current_period_end: String? = null,
    val plan: SubscriptionPlanNested? = null,
) {
    val effective_price_cents: Int
        get() = if (amount > 0) amount else (plan?.price_cents ?: 0)

    val effective_plan_name: String?
        get() = plan_name?.takeIf { it.isNotBlank() }
            ?: plan?.name?.takeIf { it.isNotBlank() }
            ?: plan?.code?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() }

    val effective_interval: String?
        get() = interval ?: plan?.billing_period
}

@Serializable
data class FeedbackRequest(
    val category: String,
    val message: String,
    val platform: String = "android",
)

@Serializable
data class RecoveryKeyResponse(
    val recovery_key: String? = null,
)

interface SettingsApi {
    suspend fun list_sessions(): SessionListResponse
    suspend fun revoke_session(session_id: String)
    suspend fun revoke_all_sessions()
    suspend fun change_password(request: ChangePasswordRequest): ChangePasswordResponse
    suspend fun update_profile_color(color: String): UpdateProfileColorResponse
    suspend fun get_security_status(): SecurityStatusResponse
    suspend fun get_totp_status(): TotpStatusResponse
    suspend fun logout_others()
    suspend fun list_blocked_senders(): BlockedSendersResponse
    suspend fun block_sender(address: String)
    suspend fun unblock_sender(address: String)
    suspend fun list_aliases(limit: Int = 100, offset: Int = 0): AliasListResponse
    suspend fun delete_alias(alias_id: String)
    suspend fun list_deleted_aliases(): ListDeletedAliasesResponse
    suspend fun restore_deleted_alias(deleted_id: String)
    suspend fun purge_deleted_alias(deleted_id: String)
    suspend fun empty_deleted_aliases()
    suspend fun create_alias(request: CreateAliasRequest): CreateAliasResponse
    suspend fun update_alias(alias_id: String, request: UpdateAliasRequest): Boolean
    suspend fun list_all_domain_addresses(): AllDomainAddressesResponse
    suspend fun list_domains(): DomainListResponse
    suspend fun add_domain(request: AddDomainRequest): CustomDomain
    suspend fun delete_domain(domain_id: String)
    suspend fun trigger_domain_verification(domain_id: String): CustomDomain
    suspend fun get_dns_records(domain_id: String): DnsRecordsResponse
    suspend fun update_domain(domain_id: String, request: UpdateDomainRequest): CustomDomain
    suspend fun get_storage_overview(): StorageOverview
    suspend fun get_subscription(): SubscriptionInfo
    suspend fun send_feedback(request: FeedbackRequest)
    suspend fun get_recovery_key(): RecoveryKeyResponse
    suspend fun check_alias_availability(request: CheckAliasAvailabilityRequest): CheckAliasAvailabilityResponse
    suspend fun list_directories(): ListDirectoriesResponse
    suspend fun create_directory(request: CreateDirectoryRequest): CreateDirectoryResponse
    suspend fun check_directory_availability(request: DirectoryAvailabilityRequest): DirectoryAvailabilityResponse
    suspend fun update_directory(directory_id: String, request: UpdateDirectoryRequest): Boolean
    suspend fun delete_directory(directory_id: String)
    suspend fun get_alias_preferences(): AliasPreferences
    suspend fun update_alias_preferences(request: UpdateAliasPreferencesRequest)
}

class SettingsApiImpl(private val client: ApiClient) : SettingsApi {
    private val auth_base = "/api/core/v1/auth"
    private val security_base = "/api/core/v1/security"

    override suspend fun list_sessions(): SessionListResponse {
        val response = client.http.get("${client.base_url}$security_base/sessions")
        return decode_or_throw(response)
    }

    override suspend fun revoke_session(session_id: String) {
        val response = client.http.delete("${client.base_url}$security_base/sessions/$session_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun revoke_all_sessions() {
        val response = client.http.delete("${client.base_url}$security_base/sessions") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun change_password(request: ChangePasswordRequest): ChangePasswordResponse {
        val response = client.http.patch("${client.base_url}$auth_base/me/password") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_profile_color(color: String): UpdateProfileColorResponse {
        val response = client.http.patch("${client.base_url}$auth_base/me/profile-color") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(UpdateProfileColorRequest(color))
        }
        return decode_or_throw(response)
    }

    override suspend fun get_security_status(): SecurityStatusResponse {
        val response = client.http.get("${client.base_url}/api/core/v1/account/security")
        return decode_or_throw(response)
    }

    override suspend fun get_totp_status(): TotpStatusResponse {
        val response = client.http.get("${client.base_url}$auth_base/totp/status")
        return decode_or_throw(response)
    }

    override suspend fun logout_others() {
        val response = client.http.post("${client.base_url}$auth_base/logout-others") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody("{}")
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun list_blocked_senders(): BlockedSendersResponse {
        val response = client.http.get("${client.base_url}/api/contacts/v1/blocked_senders")
        return decode_or_throw(response)
    }

    override suspend fun block_sender(address: String) {
        val response = client.http.post("${client.base_url}/api/contacts/v1/blocked_senders") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(BlockRequest(address))
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun unblock_sender(address: String) {
        val response = client.http.delete("${client.base_url}/api/contacts/v1/blocked_senders") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(UnblockRequest(address))
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun list_aliases(limit: Int, offset: Int): AliasListResponse {
        val response = client.http.get("${client.base_url}/api/addresses/v1/aliases") {
            url {
                parameters.append("limit", limit.toString())
                parameters.append("offset", offset.toString())
            }
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_alias(alias_id: String) {
        val response = client.http.delete("${client.base_url}/api/addresses/v1/aliases/$alias_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun list_deleted_aliases(): ListDeletedAliasesResponse {
        val response = client.http.get("${client.base_url}/api/addresses/v1/aliases/deleted")
        return decode_or_throw(response)
    }

    override suspend fun restore_deleted_alias(deleted_id: String) {
        val response = client.http.post("${client.base_url}/api/addresses/v1/aliases/deleted/$deleted_id/restore") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun purge_deleted_alias(deleted_id: String) {
        val response = client.http.delete("${client.base_url}/api/addresses/v1/aliases/deleted/$deleted_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun empty_deleted_aliases() {
        val response = client.http.delete("${client.base_url}/api/addresses/v1/aliases/deleted") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun create_alias(request: CreateAliasRequest): CreateAliasResponse {
        val response = client.http.post("${client.base_url}/api/addresses/v1/aliases") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_alias(alias_id: String, request: UpdateAliasRequest): Boolean {
        val response = client.http.patch("${client.base_url}/api/addresses/v1/aliases/$alias_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
        return true
    }

    override suspend fun list_all_domain_addresses(): AllDomainAddressesResponse {
        val response = client.http.get("${client.base_url}/api/addresses/v1/domains/addresses")
        return decode_or_throw(response)
    }

    override suspend fun list_domains(): DomainListResponse {
        val response = client.http.get("${client.base_url}/api/addresses/v1/domains")
        return decode_or_throw(response)
    }

    override suspend fun add_domain(request: AddDomainRequest): CustomDomain {
        val response = client.http.post("${client.base_url}/api/addresses/v1/domains") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_domain(domain_id: String) {
        val response = client.http.delete("${client.base_url}/api/addresses/v1/domains/$domain_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun trigger_domain_verification(domain_id: String): CustomDomain {
        val response = client.http.post("${client.base_url}/api/addresses/v1/domains/$domain_id/verify") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            timeout {
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
            }
        }
        return decode_or_throw(response)
    }

    override suspend fun get_dns_records(domain_id: String): DnsRecordsResponse {
        val response = client.http.get("${client.base_url}/api/addresses/v1/domains/$domain_id/dns-records")
        return decode_or_throw(response)
    }

    override suspend fun update_domain(domain_id: String, request: UpdateDomainRequest): CustomDomain {
        val response = client.http.patch("${client.base_url}/api/addresses/v1/domains/$domain_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun get_storage_overview(): StorageOverview {
        val response = client.http.get("${client.base_url}/api/sync/v1/storage/overview")
        return decode_or_throw(response)
    }

    override suspend fun get_subscription(): SubscriptionInfo {
        val response = client.http.get("${client.base_url}/api/payments/v1/subscription")
        return decode_or_throw(response)
    }

    override suspend fun send_feedback(request: FeedbackRequest) {
        val response = client.http.post("${client.base_url}/api/core/v1/feedback") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun get_recovery_key(): RecoveryKeyResponse {
        val response = client.http.get("${client.base_url}/api/core/v1/recovery/options")
        return decode_or_throw(response)
    }

    override suspend fun check_alias_availability(request: CheckAliasAvailabilityRequest): CheckAliasAvailabilityResponse {
        val response = client.http.post("${client.base_url}/api/addresses/v1/aliases/check") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun list_directories(): ListDirectoriesResponse {
        val response = client.http.get("${client.base_url}/api/addresses/v1/aliases/directories")
        return decode_or_throw(response)
    }

    override suspend fun create_directory(request: CreateDirectoryRequest): CreateDirectoryResponse {
        val response = client.http.post("${client.base_url}/api/addresses/v1/aliases/directories") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun check_directory_availability(request: DirectoryAvailabilityRequest): DirectoryAvailabilityResponse {
        val response = client.http.post("${client.base_url}/api/addresses/v1/aliases/directories/availability") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_directory(directory_id: String, request: UpdateDirectoryRequest): Boolean {
        val response = client.http.patch("${client.base_url}/api/addresses/v1/aliases/directories/$directory_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return response.status.value in 200..299
    }

    override suspend fun delete_directory(directory_id: String) {
        val response = client.http.delete("${client.base_url}/api/addresses/v1/aliases/directories/$directory_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun get_alias_preferences(): AliasPreferences {
        val response = client.http.get("${client.base_url}/api/addresses/v1/aliases/preferences")
        return decode_or_throw(response)
    }

    override suspend fun update_alias_preferences(request: UpdateAliasPreferencesRequest) {
        val response = client.http.patch("${client.base_url}/api/addresses/v1/aliases/preferences") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
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
