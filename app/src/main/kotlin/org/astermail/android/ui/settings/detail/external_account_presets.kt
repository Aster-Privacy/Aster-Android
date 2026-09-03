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

package org.astermail.android.ui.settings.detail

internal data class ExternalProviderPreset(
    val host: String,
    val port: Int,
    val smtp_host: String,
    val smtp_port: Int,
    val use_tls: Boolean,
    val app_password_url: String? = null,
)

private val google_preset = ExternalProviderPreset(
    host = "imap.gmail.com",
    port = 993,
    smtp_host = "smtp.gmail.com",
    smtp_port = 587,
    use_tls = true,
    app_password_url = "https://myaccount.google.com/apppasswords",
)

private val outlook_preset = ExternalProviderPreset(
    host = "outlook.office365.com",
    port = 993,
    smtp_host = "smtp.office365.com",
    smtp_port = 587,
    use_tls = true,
)

private val icloud_preset = ExternalProviderPreset(
    host = "imap.mail.me.com",
    port = 993,
    smtp_host = "smtp.mail.me.com",
    smtp_port = 587,
    use_tls = true,
    app_password_url = "https://account.apple.com",
)

private val external_presets = mapOf(
    "gmail.com" to google_preset,
    "googlemail.com" to google_preset,
    "yahoo.com" to ExternalProviderPreset(
        host = "imap.mail.yahoo.com",
        port = 993,
        smtp_host = "smtp.mail.yahoo.com",
        smtp_port = 587,
        use_tls = true,
        app_password_url = "https://login.yahoo.com/account/security",
    ),
    "outlook.com" to outlook_preset,
    "hotmail.com" to outlook_preset,
    "live.com" to outlook_preset,
    "icloud.com" to icloud_preset,
    "me.com" to icloud_preset,
)

internal fun external_provider_preset(email: String): ExternalProviderPreset? {
    val at = email.lastIndexOf('@')

    if (at < 0) return null

    return external_presets[email.substring(at + 1).trim().lowercase()]
}

internal fun is_external_preset_host(host: String): Boolean {
    val normalized = host.trim().lowercase()

    return external_presets.values.any { it.host == normalized }
}

private val google_mail_hosts = setOf("imap.gmail.com", "smtp.gmail.com", "pop.gmail.com")

private val app_password_spaces = Regex("\\p{Zs}")

private val app_password_groups = Regex("^[a-z0-9]{4}(?:\\p{Zs}[a-z0-9]{4}){3}$", RegexOption.IGNORE_CASE)

internal fun normalize_app_password(host: String, password: String): String {
    if (host.trim().lowercase() !in google_mail_hosts) return password

    val trimmed = password.trim()

    if (!app_password_groups.matches(trimmed)) return password

    return app_password_spaces.replace(trimmed, "")
}
