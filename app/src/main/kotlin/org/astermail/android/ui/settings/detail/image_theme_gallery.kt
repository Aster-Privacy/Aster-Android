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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.X
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.roundToInt
import org.astermail.android.R
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.ui.theme.ThemeBackground
import org.astermail.android.ui.theme.draw_theme_backdrop
import org.astermail.android.ui.theme.remember_theme_bitmap
import org.astermail.android.ui.theme.theme_backgrounds

private val gallery_card_shape = RoundedCornerShape(30.dp)

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
    val palette = AsterColorThemes.palette_for(current.color_theme)
    val accent by animateColorAsState(palette?.accent_color ?: Color(0xFF3B82F6), tween(260), label = "gallery_accent")
    val on_accent by animateColorAsState(palette?.on_accent ?: Color.White, tween(260), label = "gallery_on_accent")
    val soft = theme_backgrounds.map { remember_theme_bitmap(it.drawable_res, sample = 16, soften = true) }

    Dialog(
        onDismissRequest = on_dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .drawBehind { draw_gallery_backdrop(pager, soft.map { it.value }) },
        ) {
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = on_dismiss)
                            .testTag("image_theme_gallery_close"),
                        contentAlignment = Alignment.Center,
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
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(40.dp))
                }

                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    pageSpacing = 12.dp,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                ) { page ->
                    gallery_preview_card(
                        background = theme_backgrounds[page],
                        is_active = theme_backgrounds[page].id == active_id,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
                                val emphasis = 1f - offset.coerceIn(0f, 1f)
                                val scale = 0.92f + 0.08f * emphasis
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.7f + 0.3f * emphasis
                            },
                    )
                }

                Crossfade(targetState = current, animationSpec = tween(180), label = "gallery_title") { shown ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(shown.label_res),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.image_theme_credit),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    theme_backgrounds.indices.forEach { index ->
                        val selected = index == pager.currentPage
                        val width by animateDpAsState(if (selected) 18.dp else 6.dp, tween(220), label = "gallery_dot")
                        val dot_alpha by animateFloatAsState(if (selected) 0.95f else 0.3f, tween(220), label = "gallery_dot_alpha")
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = dot_alpha)),
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
                        .background(if (already_active) Color.White.copy(alpha = 0.14f) else accent)
                        .clickable(enabled = !already_active) { on_apply(current) }
                        .testTag("image_theme_gallery_apply"),
                    contentAlignment = Alignment.Center,
                ) {
                    val label_color = if (already_active) Color.White else on_accent
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (already_active) {
                            Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = label_color,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(if (already_active) R.string.image_theme_in_use else R.string.image_theme_apply),
                            color = label_color,
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

private fun DrawScope.draw_soft_image(bitmap: ImageBitmap, alpha: Float) {
    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

private fun DrawScope.draw_gallery_backdrop(pager: PagerState, soft: List<ImageBitmap?>) {
    val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (soft.size - 1).toFloat())
    val base = floor(position).toInt()
    val next = (base + 1).coerceAtMost(soft.size - 1)
    val mix = position - base
    soft.getOrNull(base)?.let { draw_soft_image(it, 0.6f) }
    if (next != base && mix > 0f) soft.getOrNull(next)?.let { draw_soft_image(it, 0.6f * mix) }
    drawRect(Color.Black.copy(alpha = 0.42f))
}

@Composable
private fun gallery_preview_card(
    background: ThemeBackground,
    is_active: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = AsterColorThemes.palette_for(background.color_theme)
    val accent = palette?.accent_color ?: Color(0xFF3B82F6)
    val card = lerp(background.tint, Color.White, 0.03f).copy(alpha = 0.68f)
    val pill = lerp(background.tint, Color.White, 0.08f).copy(alpha = 0.7f)
    val bitmap by remember_theme_bitmap(background.drawable_res)
    val shown by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(200), label = "gallery_card_fade")
    Box(
        modifier = modifier
            .clip(gallery_card_shape)
            .background(background.tint)
            .border(1.dp, Color.White.copy(alpha = if (is_active) 0.55f else 0.12f), gallery_card_shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            bitmap?.let { draw_theme_backdrop(it, Size(size.width, size.height), alpha = shown) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                preview_bar(width = 64.dp, height = 12.dp, alpha = 0.92f)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(pill),
            )
            Spacer(Modifier.height(14.dp))
            repeat(5) { index ->
                preview_row(tint = card, accent = accent, unread = index < 2, wide = index % 2 == 0)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(50.dp)
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
            .clip(RoundedCornerShape(16.dp))
            .background(tint)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            preview_bar(width = if (wide) 88.dp else 66.dp, height = 8.dp, alpha = if (unread) 0.92f else 0.6f)
            Spacer(Modifier.height(6.dp))
            preview_bar(width = if (wide) 120.dp else 100.dp, height = 6.dp, alpha = 0.32f)
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
