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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.X
import kotlin.math.absoluteValue
import org.astermail.android.R
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.ui.theme.ThemeBackground
import org.astermail.android.ui.theme.theme_backgrounds

private val gallery_card_shape = RoundedCornerShape(28.dp)

@Composable
fun image_theme_gallery(
    initial: ThemeBackground,
    active_id: String?,
    on_dismiss: () -> Unit,
    on_apply: (ThemeBackground) -> Unit,
) {
    val start = theme_backgrounds.indexOfFirst { it.id == initial.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { theme_backgrounds.size }
    val current = theme_backgrounds[pager.currentPage]
    val accent = AsterColorThemes.palette_for(current.color_theme)?.accent_color ?: Color(0xFF3B82F6)
    val animated_accent by animateColorAsState(accent, tween(260), label = "gallery_accent")

    Dialog(
        onDismissRequest = on_dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Crossfade(targetState = current, animationSpec = tween(320), label = "gallery_backdrop") { shown ->
                Image(
                    painter = painterResource(shown.drawable_res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                        .graphicsLayer { alpha = 0.55f },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    gallery_round_button(
                        background = Color.White.copy(alpha = 0.16f),
                        on_click = on_dismiss,
                        test_tag = "image_theme_gallery_close",
                    ) {
                        Icon(
                            imageVector = TablerIcons.X,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.image_themes),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(40.dp))
                }

                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 44.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                ) { page ->
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
                    val emphasis = 1f - offset.coerceIn(0f, 1f)
                    gallery_preview_card(
                        background = theme_backgrounds[page],
                        is_active = theme_backgrounds[page].id == active_id,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 0.9f + 0.1f * emphasis
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.55f + 0.45f * emphasis
                            },
                    )
                }

                Text(
                    text = stringResource(current.label_res),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.image_theme_credit),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    theme_backgrounds.indices.forEach { index ->
                        val selected = index == pager.currentPage
                        val width by animateDpAsState(if (selected) 18.dp else 6.dp, tween(220), label = "gallery_dot")
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (selected) 0.95f else 0.35f)),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                val already_active = current.id == active_id
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (already_active) Color.White.copy(alpha = 0.16f) else animated_accent)
                        .clickable(enabled = !already_active) { on_apply(current) }
                        .testTag("image_theme_gallery_apply"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (already_active) {
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(if (already_active) R.string.image_theme_in_use else R.string.image_theme_apply),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun gallery_round_button(
    background: Color,
    on_click: () -> Unit,
    test_tag: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = on_click)
            .testTag(test_tag),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun gallery_preview_card(
    background: ThemeBackground,
    is_active: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = AsterColorThemes.palette_for(background.color_theme)
    val accent = palette?.accent_color ?: Color(0xFF3B82F6)
    val tint = (palette?.bg_card ?: Color(0xFF16181D)).copy(alpha = 0.5f)
    Box(
        modifier = modifier
            .clip(gallery_card_shape)
            .border(1.dp, Color.White.copy(alpha = if (is_active) 0.5f else 0.14f), gallery_card_shape),
    ) {
        Image(
            painter = painterResource(background.drawable_res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.22f to Color.Black.copy(alpha = 0.3f),
                        0.7f to Color.Black.copy(alpha = 0.34f),
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                preview_bar(width = 64.dp, height = 12.dp, alpha = 0.9f)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(tint),
            )
            Spacer(Modifier.height(14.dp))
            repeat(5) { index ->
                preview_row(tint = tint, accent = accent, unread = index < 2, wide = index % 2 == 0)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun preview_row(tint: Color, accent: Color, unread: Boolean, wide: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            preview_bar(width = if (wide) 88.dp else 66.dp, height = 8.dp, alpha = if (unread) 0.92f else 0.6f)
            Spacer(Modifier.height(6.dp))
            preview_bar(width = if (wide) 120.dp else 100.dp, height = 6.dp, alpha = 0.35f)
        }
        if (unread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

@Composable
private fun preview_bar(width: Dp, height: Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = alpha)),
    )
}
