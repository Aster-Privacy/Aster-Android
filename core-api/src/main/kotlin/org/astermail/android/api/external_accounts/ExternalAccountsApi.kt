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
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.api.external_accounts

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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class ExternalAccount(
    val id: String = "",
    val account_token: String = "",
    val encrypted_account_data: String = "",
    val account_data_nonce: String = "",
    val integrity_hash: String = "",
    val protocol: String = "",
    val is_enabled: Boolean = true,
    val is_verified: Boolean = false,
    val last_sync_at: String? = null,
    val last_sync_status: String? = null,
    val last_sync_error: String? = null,
    val needs_reauth: Boolean = false,
    val email_count: Int = 0,
    val oauth_provider: String? = null,
    val oauth_email: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class ExternalAccountListResponse(
    val accounts: List<ExternalAccount> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class OAuthAuthorizeRequest(
    val provider: String,
    val account_token: String,
    val encrypted_account_data: String,
    val account_data_nonce: String,
    val integrity_hash: String,
    val tag_token: String? = null,
)

@Serializable
data class OAuthAuthorizeResponse(val authorize_url: String = "")

@Serializable
data class ManualImapCredentials(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val use_tls: Boolean = true,
    val smtp_host: String = "",
    val smtp_port: Int = 587,
    val smtp_username: String = "",
    val smtp_password: String = "",
)

@Serializable
data class CreateManualAccountRequest(
    val account_token: String,
    val encrypted_account_data: String,
    val account_data_nonce: String,
    val integrity_hash: String,
    val credentials: ManualImapCredentials,
    val protocol: String = "imap",
    val is_enabled: Boolean = true,
    val tag_token: String? = null,
)

@Serializable
data class SuccessResponse(val success: Boolean = false)

@Serializable
data class TriggerSyncRequest(
    val account_token: String,
    val full_resync: Boolean = false,
)

@Serializable
data class TriggerSyncResponse(
    val success: Boolean = false,
    val message: String = "",
    val quota_exceeded: Boolean = false,
)

@Serializable
data class SyncProgress(
    val account_token: String = "",
    val status: String = "",
    val total_messages: Int = 0,
    val processed_messages: Int = 0,
    val current_folder: String = "",
    val error_message: String? = null,
)

@Serializable
data class OAuthFolder(
    val name: String = "",
    val delimiter: String = "/",
    val excluded: Boolean = false,
)

@Serializable
data class OAuthFoldersRequest(val account_token: String)

@Serializable
data class OAuthFoldersResponse(val folders: List<OAuthFolder> = emptyList())

@Serializable
data class FolderMappingRequest(
    val account_token: String,
    val folder_mapping: Map<String, String>,
)

@Serializable
data class ExternalAccountSendAttachment(
    val data: String,
    val filename: String,
    val content_type: String,
    val size_bytes: Long,
    val content_id: String? = null,
)

@Serializable
data class ExternalAccountSendRequest(
    val account_token: String,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val attachments: List<ExternalAccountSendAttachment>? = null,
)

@Serializable
data class ExternalAccountSendResponse(
    val success: Boolean = false,
    val message: String = "",
)

interface ExternalAccountsApi {
    suspend fun list_accounts(): ExternalAccountListResponse
    suspend fun start_oauth(req: OAuthAuthorizeRequest): OAuthAuthorizeResponse
    suspend fun create_manual(req: CreateManualAccountRequest): ExternalAccount
    suspend fun delete_account(account_token: String): SuccessResponse
    suspend fun trigger_sync(req: TriggerSyncRequest): TriggerSyncResponse
    suspend fun get_sync_progress(account_token: String): SyncProgress
    suspend fun list_oauth_folders(account_token: String): OAuthFoldersResponse
    suspend fun save_folder_mapping(req: FolderMappingRequest): SuccessResponse
    suspend fun send_via_account(req: ExternalAccountSendRequest): ExternalAccountSendResponse
}

class ExternalAccountsApiImpl(private val client: ApiClient) : ExternalAccountsApi {
    private val base = "/api/mail/v1/external_accounts"

    private suspend inline fun <reified T> decode(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return response.body()
    }

    override suspend fun list_accounts(): ExternalAccountListResponse =
        decode(client.http.get("${client.base_url}$base"))

    override suspend fun start_oauth(req: OAuthAuthorizeRequest): OAuthAuthorizeResponse =
        decode(client.http.post("${client.base_url}$base/oauth/authorize") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })

    override suspend fun create_manual(req: CreateManualAccountRequest): ExternalAccount =
        decode(client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })

    override suspend fun delete_account(account_token: String): SuccessResponse {
        val response = client.http.delete("${client.base_url}$base") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            parameter("account_token", account_token)
        }
        return decode(response)
    }

    override suspend fun trigger_sync(req: TriggerSyncRequest): TriggerSyncResponse =
        decode(client.http.post("${client.base_url}$base/sync") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })

    override suspend fun get_sync_progress(account_token: String): SyncProgress =
        decode(client.http.get("${client.base_url}$base/sync_progress") {
            parameter("account_token", account_token)
        })

    override suspend fun list_oauth_folders(account_token: String): OAuthFoldersResponse =
        decode(client.http.post("${client.base_url}$base/oauth/folders") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(OAuthFoldersRequest(account_token))
        })

    override suspend fun save_folder_mapping(req: FolderMappingRequest): SuccessResponse =
        decode(client.http.put("${client.base_url}$base/folder_mapping") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })

    override suspend fun send_via_account(req: ExternalAccountSendRequest): ExternalAccountSendResponse =
        decode(client.http.post("${client.base_url}$base/send") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })
}
