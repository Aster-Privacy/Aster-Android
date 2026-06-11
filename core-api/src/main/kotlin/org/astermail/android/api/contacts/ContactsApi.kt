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

package org.astermail.android.api.contacts

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

const val CONTACT_DATA_VERSION = 2

@Serializable
data class ContactItem(
    val id: String,
    val contact_token: String? = null,
    val encrypted_data: String? = null,
    val data_nonce: String? = null,
    val integrity_hash: String? = null,
    val data_version: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class ListContactsResponse(
    val items: List<ContactItem> = emptyList(),
    val next_cursor: String? = null,
    val has_more: Boolean = false,
)

@Serializable
data class ContactsCountResponse(
    val count: Int = 0,
)

@Serializable
data class CreateContactRequest(
    val contact_token: String,
    val encrypted_data: String,
    val data_nonce: String,
    val integrity_hash: String? = null,
    val data_version: Int? = null,
    val name_search_token: String? = null,
    val email_search_token: String? = null,
    val company_search_token: String? = null,
)

@Serializable
data class CreateContactResponse(
    val id: String? = null,
    val success: Boolean = false,
    val created_at: String? = null,
)

@Serializable
data class UpdateContactRequest(
    val encrypted_data: String,
    val data_nonce: String,
    val integrity_hash: String? = null,
    val name_search_token: String? = null,
    val email_search_token: String? = null,
    val company_search_token: String? = null,
)

@Serializable
data class DeleteContactResponse(
    val success: Boolean = false,
    val deleted_count: Int = 0,
)

@Serializable
data class BulkDeleteContactsRequest(
    val contact_ids: List<String>,
)

@Serializable
data class SearchContactsResponse(
    val items: List<ContactItem> = emptyList(),
)

@Serializable
data class ContactGroupEncrypted(
    val id: String,
    val encrypted_name: String,
    val name_nonce: String,
    val color: String,
    val contact_count: Int = 0,
    val created_at: String? = null,
)

@Serializable
data class ListContactGroupsResponse(
    val groups: List<ContactGroupEncrypted> = emptyList(),
)

@Serializable
data class CreateContactGroupRequest(
    val group_token: String,
    val encrypted_name: String,
    val name_nonce: String,
    val color: String,
)

@Serializable
data class CreateContactGroupResponse(
    val id: String,
    val created_at: String? = null,
)

@Serializable
data class SuccessResponse(
    val success: Boolean = false,
)

interface ContactsApi {
    suspend fun list_contacts(limit: Int? = null, cursor: String? = null, group_id: String? = null): ListContactsResponse
    suspend fun get_contacts_count(): ContactsCountResponse
    suspend fun get_contact(contact_id: String): ContactItem
    suspend fun create_contact(request: CreateContactRequest): CreateContactResponse
    suspend fun update_contact(contact_id: String, request: UpdateContactRequest)
    suspend fun delete_contact(contact_id: String): DeleteContactResponse
    suspend fun bulk_delete_contacts(request: BulkDeleteContactsRequest): DeleteContactResponse
    suspend fun search_contacts(search_token: String, field: String = "all", limit: Int? = null): SearchContactsResponse
    suspend fun list_contact_groups(): ListContactGroupsResponse
    suspend fun create_contact_group(request: CreateContactGroupRequest): CreateContactGroupResponse
    suspend fun delete_contact_group(group_id: String): SuccessResponse
    suspend fun add_contact_to_group(contact_id: String, group_id: String): SuccessResponse
    suspend fun remove_contact_from_group(contact_id: String, group_id: String): SuccessResponse
}

class ContactsApiImpl(private val client: ApiClient) : ContactsApi {
    private val base = "/api/contacts/v1"

    override suspend fun list_contacts(limit: Int?, cursor: String?, group_id: String?): ListContactsResponse {
        val response = client.http.get("${client.base_url}$base") {
            limit?.let { parameter("limit", it) }
            cursor?.let { parameter("cursor", it) }
            group_id?.let { parameter("group_id", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun get_contacts_count(): ContactsCountResponse {
        val response = client.http.get("${client.base_url}$base/count")
        return decode_or_throw(response)
    }

    override suspend fun get_contact(contact_id: String): ContactItem {
        val response = client.http.get("${client.base_url}$base/$contact_id")
        return decode_or_throw(response)
    }

    override suspend fun create_contact(request: CreateContactRequest): CreateContactResponse {
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_contact(contact_id: String, request: UpdateContactRequest) {
        val response = client.http.put("${client.base_url}$base/$contact_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun delete_contact(contact_id: String): DeleteContactResponse {
        val response = client.http.delete("${client.base_url}$base/$contact_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun bulk_delete_contacts(request: BulkDeleteContactsRequest): DeleteContactResponse {
        val response = client.http.delete("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun search_contacts(search_token: String, field: String, limit: Int?): SearchContactsResponse {
        val response = client.http.get("${client.base_url}$base/search") {
            parameter("q", search_token)
            parameter("field", field)
            limit?.let { parameter("limit", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun list_contact_groups(): ListContactGroupsResponse {
        val response = client.http.get("${client.base_url}$base/groups")
        return decode_or_throw(response)
    }

    override suspend fun create_contact_group(request: CreateContactGroupRequest): CreateContactGroupResponse {
        val response = client.http.post("${client.base_url}$base/groups") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_contact_group(group_id: String): SuccessResponse {
        val response = client.http.delete("${client.base_url}$base/groups/$group_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun add_contact_to_group(contact_id: String, group_id: String): SuccessResponse {
        val response = client.http.post("${client.base_url}$base/$contact_id/groups/$group_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody("{}")
        }
        return decode_or_throw(response)
    }

    override suspend fun remove_contact_from_group(contact_id: String, group_id: String): SuccessResponse {
        val response = client.http.delete("${client.base_url}$base/$contact_id/groups/$group_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
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
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
