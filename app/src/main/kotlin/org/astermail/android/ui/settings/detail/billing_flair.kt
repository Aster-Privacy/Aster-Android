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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import kotlin.math.PI
import kotlin.math.sin

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

private val star_seeds = listOf(
    0.06f to 0.18f, 0.13f to 0.62f, 0.21f to 0.09f, 0.29f to 0.41f, 0.36f to 0.78f,
    0.44f to 0.22f, 0.52f to 0.55f, 0.58f to 0.12f, 0.66f to 0.70f, 0.73f to 0.31f,
    0.81f to 0.08f, 0.87f to 0.48f, 0.93f to 0.24f, 0.97f to 0.66f, 0.17f to 0.88f,
    0.48f to 0.92f, 0.76f to 0.90f, 0.09f to 0.40f, 0.63f to 0.38f, 0.90f to 0.84f,
)

@Composable
internal fun Modifier.starfield(
    accent: Color,
    is_dark: Boolean,
    band_fraction: Float = 0.42f,
    edges_only: Boolean = false,
): Modifier {
    val transition = rememberInfiniteTransition(label = "starfield")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 9000, easing = LinearEasing)),
        label = "twinkle",
    )
    val star_color = if (is_dark) blend(Color.White, accent, 0.12f) else accent
    return drawBehind {
        val band = size.height * band_fraction
        val wash_alpha = if (is_dark) 0.11f else 0.055f
        drawRect(
            brush = Brush.radialGradient(
                0.0f to accent.copy(alpha = wash_alpha),
                0.5f to accent.copy(alpha = wash_alpha * 0.35f),
                1.0f to Color.Transparent,
                center = Offset(size.width * 0.74f, -band * 0.30f),
                radius = size.width * 0.78f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                0.0f to accent.copy(alpha = wash_alpha * 0.55f),
                1.0f to Color.Transparent,
                center = Offset(size.width * 0.10f, band * 0.70f),
                radius = size.width * 0.42f,
            ),
        )
        val count = star_seeds.size
        star_seeds.forEachIndexed { index, (fx, fy) ->
            if (edges_only && fx > 0.22f && fx < 0.78f) return@forEachIndexed
            val center = Offset(size.width * fx, band * fy)
            val twinkle = 0.62f + 0.38f * sin(2.0 * PI * (phase + index.toFloat() / count)).toFloat()
            val core = if (index % 4 == 0) 1.3.dp.toPx() else 0.85.dp.toPx()
            val halo = core * 5.5f
            val base_alpha = if (is_dark) (if (index % 3 == 0) 0.62f else 0.34f) else (if (index % 3 == 0) 0.34f else 0.18f)
            val alpha = base_alpha * twinkle
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to star_color.copy(alpha = alpha * 0.55f),
                    0.4f to star_color.copy(alpha = alpha * 0.14f),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = halo,
                ),
                radius = halo,
                center = center,
            )
            drawCircle(color = star_color.copy(alpha = alpha), radius = core, center = center)
        }
    }
}

@Composable
internal fun hero_surface(
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val accent = colors.accent_blue
    val shape = SquircleShape(corner)
    Column(
        modifier = modifier
            .border(1.dp, galaxy_border_brush(accent, colors.text_primary), shape)
            .clip(shape)
            .background(colors.bg_card)
            .background(
                Brush.verticalGradient(
                    0.00f to accent.copy(alpha = if (colors.is_dark) 0.14f else 0.07f),
                    0.32f to accent.copy(alpha = 0.03f),
                    0.55f to Color.Transparent,
                ),
            )
            .starfield(accent, colors.is_dark, band_fraction = 0.30f, edges_only = true),
        content = content,
    )
}

@Composable
internal fun icon_tile(
    icon: ImageVector,
    size: Dp = 40.dp,
    corner: Dp = 12.dp,
    accent: Color = AsterMaterial.colors.accent_blue,
    muted: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(corner)
    val brush = if (muted) {
        Brush.verticalGradient(listOf(colors.bg_tertiary, colors.bg_tertiary))
    } else {
        Brush.linearGradient(
            0.00f to blend(accent, Color.White, 0.28f),
            0.55f to accent,
            1.00f to blend(accent, Color.Black, 0.18f),
        )
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(brush)
            .then(if (muted) Modifier.border(1.dp, colors.border_primary, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (muted) colors.text_secondary else Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
internal fun gradient_bar(fraction: Float, is_over: Boolean, height: Dp = 8.dp) {
    val colors = AsterMaterial.colors
    val target = fraction.coerceIn(0.02f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "storage_fill",
    )
    val accent = if (is_over) colors.danger else colors.accent_blue
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.bg_tertiary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        0.0f to blend(accent, Color.White, 0.30f),
                        1.0f to accent,
                    ),
                ),
        )
    }
}
