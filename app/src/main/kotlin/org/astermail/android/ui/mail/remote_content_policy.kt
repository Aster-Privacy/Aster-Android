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

package org.astermail.android.ui.mail

import org.astermail.android.api.preferences.UserPreferences

internal const val REMOTE_IMAGES_ALWAYS = "always"

internal const val REMOTE_IMAGES_ASK = "ask"

internal const val REMOTE_IMAGES_NEVER = "never"

internal fun effective_remote_image_mode(preferences: UserPreferences?): String {
    if (preferences == null) return REMOTE_IMAGES_NEVER
    if (!preferences.block_external_images) return REMOTE_IMAGES_ALWAYS
    return when (preferences.load_remote_images) {
        REMOTE_IMAGES_ALWAYS -> REMOTE_IMAGES_ALWAYS
        REMOTE_IMAGES_ASK -> REMOTE_IMAGES_ASK
        else -> REMOTE_IMAGES_NEVER
    }
}

internal fun setting_blocks_remote_images(preferences: UserPreferences?): Boolean =
    effective_remote_image_mode(preferences) != REMOTE_IMAGES_ALWAYS

internal fun allows_remote_image_grant(preferences: UserPreferences?): Boolean =
    effective_remote_image_mode(preferences) != REMOTE_IMAGES_NEVER

internal fun message_loads_remote_content(
    blocked: Boolean,
    granted: Boolean,
    grant_allowed: Boolean,
    is_system_sender: Boolean,
): Boolean = !blocked || is_system_sender || (grant_allowed && granted)

internal fun setting_removes_tracking_pixels(preferences: UserPreferences?): Boolean {
    if (setting_blocks_remote_images(preferences)) return true
    return preferences?.block_external_content != false && preferences?.block_tracking_pixels != false
}

internal fun remote_content_sanitize_options(preferences: UserPreferences?): EmailHtmlSanitizer.SanitizeOptions =
    EmailHtmlSanitizer.SanitizeOptions(
        clean_tracking_links = preferences?.block_external_content != false &&
            preferences?.block_tracking_links != false,
        remove_tracking_pixels = setting_removes_tracking_pixels(preferences),
        block_remote_fonts = preferences?.block_remote_fonts != false,
        block_remote_css = preferences?.block_remote_css != false,
    )
