/*
 * Aster Mail Android
 * Copyright (C) 2026 Aster Privacy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.astermail.android.ui.settings.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial

internal fun plan_feature_icon(@StringRes feature_res: Int): ImageVector = when (feature_res) {
    R.string.settings_plan_bullet_free_storage,
    R.string.settings_plan_bullet_star_storage,
    R.string.settings_plan_bullet_nova_storage,
    R.string.settings_plan_bullet_supernova_storage,
    R.string.settings_plan_bullet_duo_storage,
    R.string.settings_plan_bullet_family_storage,
    -> TablerIcons.Database
    R.string.settings_plan_bullet_star_attachments,
    R.string.settings_plan_bullet_nova_attachments,
    R.string.settings_plan_bullet_supernova_attachments,
    -> TablerIcons.Paperclip
    R.string.settings_plan_bullet_free_aliases,
    R.string.settings_plan_bullet_star_aliases,
    R.string.settings_plan_bullet_unlimited_aliases,
    R.string.settings_plan_bullet_shared_aliases,
    -> TablerIcons.At
    R.string.settings_plan_bullet_star_domains,
    R.string.settings_plan_bullet_nova_domains,
    R.string.settings_plan_bullet_unlimited_domains,
    -> TablerIcons.World
    R.string.settings_plan_bullet_daily_send_limits,
    R.string.settings_plan_bullet_daily_emails,
    -> TablerIcons.Send
    R.string.settings_plan_bullet_star_templates,
    R.string.settings_plan_bullet_unlimited_templates,
    -> TablerIcons.Template
    R.string.settings_plan_bullet_unlimited_signatures -> TablerIcons.Signature
    R.string.settings_plan_bullet_tracker_protection -> TablerIcons.Shield
    R.string.settings_plan_bullet_vacation_reply -> TablerIcons.Umbrella
    R.string.settings_plan_bullet_catch_all -> TablerIcons.Inbox
    R.string.settings_plan_bullet_auto_forwarding -> TablerIcons.MailForward
    R.string.settings_plan_bullet_quiet_hours -> TablerIcons.Moon
    R.string.settings_plan_bullet_custom_avatars -> TablerIcons.Photo
    R.string.settings_plan_bullet_external_accounts -> TablerIcons.Refresh
    R.string.settings_plan_bullet_bridge_access -> TablerIcons.DeviceMobile
    R.string.settings_plan_bullet_priority_support,
    R.string.settings_plan_bullet_dedicated_support,
    -> TablerIcons.Lifebuoy
    R.string.settings_plan_bullet_carddav_import -> TablerIcons.Download
    R.string.settings_plan_bullet_contact_merge -> TablerIcons.Users
    R.string.settings_plan_bullet_encrypted_export -> TablerIcons.Upload
    R.string.settings_plan_bullet_protected_folders -> TablerIcons.Lock
    R.string.settings_plan_bullet_key_rotation -> TablerIcons.Key
    R.string.settings_plan_bullet_receipt_tracking -> TablerIcons.Receipt
    R.string.settings_plan_bullet_early_access -> TablerIcons.Rocket
    R.string.settings_plan_bullet_e2ee -> TablerIcons.ShieldLock
    R.string.settings_plan_bullet_zero_knowledge -> TablerIcons.EyeOff
    R.string.settings_plan_bullet_duo_members,
    R.string.settings_plan_bullet_family_members,
    -> TablerIcons.Users
    else -> TablerIcons.CircleCheck
}

@Composable
internal fun solid_progress_bar(fraction: Float, is_over: Boolean, height: Dp = 6.dp) {
    val colors = AsterMaterial.colors
    val filled = if (fraction <= 0f) 0f else fraction.coerceIn(0.02f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.bg_tertiary),
    ) {
        if (filled > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(filled)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (is_over) colors.danger else colors.accent_blue),
            )
        }
    }
}

@Composable
internal fun solid_badge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.accent_blue)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = colors.on_accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
