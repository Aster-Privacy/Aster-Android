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

package org.astermail.android.api.user

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.UserInfo

@Serializable
data class UpdateDisplayNameRequest(val display_name: String)

@Serializable
data class UpdateProfilePictureRequest(val profile_picture: String? = null)

@Serializable
data class UpdateDisplayNameResponse(val user: UserInfo)

@Serializable
data class UpdateProfilePictureResponse(
    val success: Boolean,
    val profile_picture: String? = null,
)

@Serializable
data class Badge(
    val slug: String,
    val display_name: String,
    val description: String? = null,
    val icon: String,
    val color: String,
    val granted_at: String,
)

interface UserApi {
    suspend fun update_display_name(display_name: String): UpdateDisplayNameResponse
    suspend fun update_profile_picture(profile_picture: String?): UpdateProfilePictureResponse
    suspend fun fetch_badges(): List<Badge>
}

class UserApiImpl(private val client: ApiClient) : UserApi {
    private val base = "/api/core/v1/auth"

    override suspend fun update_display_name(display_name: String): UpdateDisplayNameResponse {
        val response = client.http.patch("${client.base_url}$base/me/display-name") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(UpdateDisplayNameRequest(display_name))
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
        return response.body()
    }

    override suspend fun update_profile_picture(profile_picture: String?): UpdateProfilePictureResponse {
        val response = client.http.patch("${client.base_url}$base/me/profile-picture") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(UpdateProfilePictureRequest(profile_picture))
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
        return response.body()
    }

    override suspend fun fetch_badges(): List<Badge> {
        val response = client.http.get("${client.base_url}/api/core/v1/badges")
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
        return response.body()
    }
}
