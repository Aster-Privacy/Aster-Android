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

package org.astermail.android.api.preferences

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class UserPreferences(
    val language: String = "en",
    val theme: String = "system",
    val time_zone: String = "",
    val date_format: String = "",
    val time_format: String = "12h",
    val push_notifications: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val notify_new_email: Boolean = true,
    val notify_replies: Boolean = true,
    val notify_mentions: Boolean = true,
    val quiet_hours_enabled: Boolean = false,
    val quiet_hours_start: String = "22:00",
    val quiet_hours_end: String = "07:00",
    val font_size_scale: String = "default",
    val high_contrast: Boolean = false,
    val reduce_transparency: Boolean = false,
    val underline_links: Boolean = false,
    val color_vision: String = "none",
    val dyslexia_font: Boolean = false,
    val text_spacing: Boolean = false,
    val reduce_motion: Boolean = false,
    val compact_mode: Boolean = false,
    val mark_as_read: String = "1_second",
    val auto_advance: String = "Go to next message",
    val default_reply_behavior: String = "reply",
    val block_external_images: Boolean = true,
    val block_tracking_pixels: Boolean = true,
    val block_tracking_links: Boolean = true,
    val auto_save_recent_recipients: Boolean = true,
    val undo_send_enabled: Boolean = true,
    val undo_send_seconds: Int = 10,
    val confirm_delete: Boolean = false,
    val confirm_archive: Boolean = false,
    val confirm_spam: Boolean = false,
    val haptic_enabled: Boolean = true,
    val block_trackers: Boolean = true,
    val load_remote_images: String = "never",
    val send_read_receipts: Boolean = false,
    val warn_suspicious_links: Boolean = true,
    val strip_exif: Boolean = true,
    val ghost_mode: Boolean = false,
    val dev_mode: Boolean = false,
    val show_raw_headers: Boolean = false,
    val allow_insecure: Boolean = false,
    val verbose_logs: Boolean = false,
    val show_profile_pictures: Boolean = true,
    val show_email_preview: Boolean = true,
    val keyboard_shortcuts_enabled: Boolean = true,
    val low_network_mode: Boolean = false,
    val sidebar_more_collapsed: Boolean = false,
    val sidebar_folders_collapsed: Boolean = false,
    val sidebar_labels_collapsed: Boolean = false,
    val sidebar_aliases_collapsed: Boolean = false,
    val swipe_right_action: String = "archive",
    val swipe_left_action: String = "trash",
    val toolbar_actions: String = "",
    val conversation_grouping: Boolean = true,
    val inbox_categories_enabled: Boolean = true,
    val conversation_order: String = "newest",
    val show_message_size: Boolean = false,
    val force_dark_emails: Boolean = false,
    val folder_lock_mode: String = "session",
    val spam_filter_enabled: Boolean = true,
    val spam_sensitivity: String = "medium",
    val auto_delete_spam_days: Int = 30,
    val auto_discover_keys: Boolean = true,
    val encrypt_emails: Boolean = false,
    val require_encryption: Boolean = false,
    val show_encryption_indicators: Boolean = true,
    val publish_to_wkd: Boolean = false,
    val publish_to_keyservers: Boolean = false,
    val storage_format: String = "aster",
    val warn_external_recipients: Boolean = true,
    val show_aster_branding: Boolean = true,
)

@Serializable
data class EncryptedPreferencesResponse(
    val encrypted_preferences: String? = null,
    val preferences_nonce: String? = null,
)

@Serializable
data class SaveEncryptedPreferencesRequest(
    val encrypted_preferences: String,
    val preferences_nonce: String,
)

@Serializable
data class DefaultSenderResponse(
    val sender_id: String? = null,
)

@Serializable
data class SetDefaultSenderRequest(
    val sender_id: String? = null,
)

interface PreferencesApi {
    suspend fun get_preferences(): UserPreferences
    suspend fun get_preferences_raw(): String
    suspend fun save_preferences(prefs: UserPreferences): UserPreferences
    suspend fun save_preferences_raw(json_body: String)
    suspend fun get_encrypted_preferences(): EncryptedPreferencesResponse
    suspend fun save_encrypted_preferences(request: SaveEncryptedPreferencesRequest)
    suspend fun get_default_sender(): DefaultSenderResponse
    suspend fun set_default_sender(request: SetDefaultSenderRequest): DefaultSenderResponse
}

class PreferencesApiImpl(private val client: ApiClient) : PreferencesApi {
    private val base = "/api/settings/v1/preferences"

    override suspend fun get_preferences(): UserPreferences {
        val response = client.http.get("${client.base_url}$base")
        return decode_or_throw(response)
    }

    override suspend fun get_preferences_raw(): String {
        val response = client.http.get("${client.base_url}$base")
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return response.body()
    }

    override suspend fun save_preferences(prefs: UserPreferences): UserPreferences {
        val response = client.http.put("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(prefs)
        }
        return decode_or_throw(response)
    }

    override suspend fun save_preferences_raw(json_body: String) {
        val response = client.http.put("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(io.ktor.http.content.TextContent(json_body, ContentType.Application.Json))
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun get_encrypted_preferences(): EncryptedPreferencesResponse {
        val response = client.http.get("${client.base_url}$base")
        return decode_or_throw(response)
    }

    override suspend fun save_encrypted_preferences(request: SaveEncryptedPreferencesRequest) {
        val response = client.http.put("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun get_default_sender(): DefaultSenderResponse {
        val response = client.http.get("${client.base_url}$base/default-sender")
        return decode_or_throw(response)
    }

    override suspend fun set_default_sender(request: SetDefaultSenderRequest): DefaultSenderResponse {
        val response = client.http.put("${client.base_url}$base/default-sender") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
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
