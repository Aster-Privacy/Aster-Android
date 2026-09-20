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

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.Refresh
import kotlin.math.roundToInt
import org.astermail.android.R
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.ui.common.nav_anim_duration_ms
import org.astermail.android.ui.common.nav_backward_exit
import org.astermail.android.ui.common.nav_forward_enter
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.ColorThemeId
import org.astermail.android.ui.theme.ThemeBackground
import org.astermail.android.ui.theme.ThemeCategory
import org.astermail.android.ui.theme.no_theme_background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.mutableIntStateOf
import compose.icons.tablericons.Crop
import compose.icons.tablericons.Photo
import compose.icons.tablericons.PhotoOff
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.astermail.android.ui.theme.CustomThemeImageError
import org.astermail.android.ui.theme.CustomThemeImageException
import org.astermail.android.ui.theme.CustomThemeImageMeta
import org.astermail.android.ui.theme.custom_theme_background
import org.astermail.android.ui.theme.custom_theme_background_entry
import org.astermail.android.ui.theme.custom_theme_image
import org.astermail.android.ui.theme.preload_theme_bitmap
import org.astermail.android.ui.theme.remember_theme_bitmap
import org.astermail.android.ui.theme.remember_theme_thumbnail
import org.astermail.android.ui.theme.draw_theme_background
import org.astermail.android.ui.theme.draw_theme_veil
import org.astermail.android.ui.theme.theme_background_for
import org.astermail.android.ui.theme.theme_categories
import org.astermail.android.design.SquircleShape

private data class LibraryPalette(
    val page_bg: Color,
    val raised_bg: Color,
    val hairline: Color,
    val control_bg: Color,
    val dash_line: Color,
    val muted_text: Color,
    val faint_text: Color,
)

private fun mix(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)

private fun library_palette_for(
    tint: Color,
    accent: Color,
    fallback: Color = Color(0xFF1C1C20),
): LibraryPalette {
    val base = if (tint == Color.Unspecified) fallback else tint
    val canvas = mix(mix(Color(0xFF0A0A0C), base, 0.55f), accent, 0.06f)
    fun lift(amount: Float): Color = mix(canvas, Color.White, amount * 0.55f)
    return LibraryPalette(
        page_bg = canvas,
        raised_bg = lift(0.08f),
        hairline = lift(0.17f),
        control_bg = lift(0.17f),
        dash_line = lift(0.32f),
        muted_text = lift(0.68f),
        faint_text = lift(0.56f),
    )
}

private val default_library_palette = library_palette_for(Color(0xFF0B0B0D), Color(0xFF3B82F6))

private val local_library_palette = staticCompositionLocalOf { default_library_palette }
private val disabled_icon = Color(0xFF55555C)
private val library_veil_ink = Color(0xFF0A0A0C)
private val error_text = Color(0xFFFF8A8A)

private val shelf_tile_shape = RoundedCornerShape(22.dp)

private val default_accent = Color(0xFF3B82F6)

internal fun accent_for(color: ColorThemeId): Color = AsterColorThemes.palette_for(color)?.accent_color ?: default_accent

internal fun on_accent_for(color: ColorThemeId): Color = AsterColorThemes.palette_for(color)?.on_accent ?: Color.White

private val swatch_order = listOf(
    ColorThemeId.purple, ColorThemeId.green, ColorThemeId.rose, ColorThemeId.orange,
    ColorThemeId.teal, ColorThemeId.indigo, ColorThemeId.amber, ColorThemeId.cyan,
    ColorThemeId.slate, ColorThemeId.lime, ColorThemeId.fuchsia,
    ColorThemeId.emerald, ColorThemeId.pink, ColorThemeId.black,
)

@Composable
fun image_theme_library(
    active_id: String?,
    active_color: ColorThemeId,
    on_dismiss: () -> Unit,
    on_apply: (ThemeBackground?, ColorThemeId) -> Unit,
) {
    val library_custom_meta by custom_theme_image.meta.collectAsState()
    val active = remember(active_id, library_custom_meta) { theme_background_for(active_id) }
    var pending_id by rememberSaveable { mutableStateOf(active?.id ?: no_theme_background) }
    var pending_color by rememberSaveable { mutableStateOf(active_color.name) }
    var color_chosen by rememberSaveable { mutableStateOf(active_color != ColorThemeId.default) }
    val pending = remember(pending_id, library_custom_meta) { theme_background_for(pending_id) }
    val color = ColorThemeId.from_key(pending_color)
    val dirty = pending_id != (active?.id ?: no_theme_background) || color != active_color
    val accent by animateColorAsState(accent_for(color), tween(260), label = "library_accent")
    val on_accent by animateColorAsState(on_accent_for(color), tween(260), label = "library_on_accent")
    val context = LocalContext.current
    var applied_tick by remember { mutableIntStateOf(0) }
    var show_applied by remember { mutableStateOf(false) }
    var confirm_reset by remember { mutableStateOf(false) }

    LaunchedEffect(pending?.cache_key) {
        pending?.let { preload_theme_bitmap(context, it) }
    }

    LaunchedEffect(applied_tick) {
        if (applied_tick == 0) return@LaunchedEffect
        show_applied = true
        delay(1800)
        show_applied = false
    }

    val reduce_motion = aster_reduce_motion()
    val anim_duration = if (reduce_motion) 0 else nav_anim_duration_ms
    val visible_state = remember { MutableTransitionState(false).apply { targetState = true } }
    val request_dismiss = { visible_state.targetState = false }

    LaunchedEffect(visible_state.currentState, visible_state.targetState) {
        if (!visible_state.targetState && !visible_state.currentState) on_dismiss()
    }

    if (confirm_reset) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { confirm_reset = false },
            title = stringResource(R.string.image_theme_reset_confirm_title),
            message = stringResource(R.string.image_theme_reset_confirm_message),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.cancel),
                    onClick = { confirm_reset = false },
                )
                org.astermail.android.design.components.AsterDialogDestructiveButton(
                    label = stringResource(R.string.image_theme_reset_short),
                    onClick = {
                        confirm_reset = false
                        pending_id = no_theme_background
                        pending_color = ColorThemeId.default.name
                        color_chosen = false
                        on_apply(null, ColorThemeId.default)
                    },
                )
            },
        )
    }

    Dialog(
        onDismissRequest = request_dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
        }
        val nav_bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val theme_base = remember(color) {
            AsterColorThemes.semantic_colors_for(true, AsterColorThemes.palette_for(color)).bg_card
        }
        val palette = remember(pending?.tint, accent, theme_base) {
            library_palette_for(pending?.tint ?: Color.Unspecified, accent, theme_base)
        }
        CompositionLocalProvider(local_library_palette provides palette) {
            AnimatedVisibility(
                visibleState = visible_state,
                enter = nav_forward_enter(anim_duration),
                exit = nav_backward_exit(anim_duration),
            ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(local_library_palette.current.page_bg)
                    .testTag("image_theme_library"),
            ) {
                val preview_bitmap = remember_theme_bitmap(pending)
                preview_bitmap?.let { bitmap ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        draw_theme_background(bitmap)
                        draw_theme_veil(library_veil_ink)
                    }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    library_top_bar(
                        reset_enabled = pending != null || color != ColorThemeId.default,
                        on_back = request_dismiss,
                        on_reset = { confirm_reset = true },
                    )
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 104.dp + nav_bottom),
                    ) {
                        item(key = "colors") {
                            colors_section(
                                pending = pending,
                                selected = color,
                                on_color = {
                                    pending_color = it.name
                                    color_chosen = true
                                },
                            )
                        }
                        item(key = "no_photo") {
                            no_photo_row(
                                selected = pending_id == no_theme_background,
                                accent = accent,
                                on_click = { pending_id = no_theme_background },
                            )
                        }
                        item(key = "yours") {
                            your_photo_section(
                                selected = pending_id == custom_theme_background,
                                accent = accent,
                                on_accent = on_accent,
                                on_pick = { meta ->
                                    pending_id = custom_theme_background
                                    if (!color_chosen) pending_color = meta.accent.name
                                },
                                on_removed = {
                                    if (pending_id == custom_theme_background) pending_id = no_theme_background
                                    if (active_id == custom_theme_background) on_apply(null, color)
                                },
                            )
                        }
                        items(theme_categories, key = { it.first.name }) { (category, list) ->
                            category_shelf(
                                category = category,
                                list = list,
                                selected_id = pending?.id,
                                accent = accent,
                                on_accent = on_accent,
                                on_pick = { chosen ->
                                    if (chosen.id == pending_id) return@category_shelf
                                    pending_id = chosen.id
                                    if (!color_chosen) pending_color = chosen.color_theme.name
                                },
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = dirty,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 2 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    apply_bar(
                        accent = accent,
                        on_accent = on_accent,
                        nav_bottom = nav_bottom.value,
                        on_apply = {
                            on_apply(pending, color)
                            applied_tick++
                        },
                    )
                }
                AnimatedVisibility(
                    visible = show_applied && !dirty,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp + nav_bottom),
                ) {
                    Text(
                        text = stringResource(R.string.image_theme_applied),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(local_library_palette.current.control_bg)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("image_theme_applied"),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun library_top_bar(reset_enabled: Boolean, on_back: () -> Unit, on_reset: () -> Unit) {
    val reset_tint by animateColorAsState(if (reset_enabled) Color.White else disabled_icon, tween(200), label = "reset_tint")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = on_back)
                .testTag("image_theme_back"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.ArrowLeft,
                contentDescription = stringResource(R.string.close),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.image_themes),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Text(
            text = stringResource(R.string.image_theme_reset_short),
            color = reset_tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(SquircleShape(12.dp))
                .clickable(enabled = reset_enabled, onClick = on_reset)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("image_theme_reset"),
        )
    }
}

@Composable
private fun no_photo_row(
    selected: Boolean,
    accent: Color,
    on_click: () -> Unit,
) {
    val palette = local_library_palette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(SquircleShape(16.dp))
            .background(palette.raised_bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else palette.hairline,
                shape = SquircleShape(16.dp),
            )
            .clickable(onClick = on_click)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("image_theme_no_photo"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.PhotoOff,
            contentDescription = null,
            tint = if (selected) accent else palette.muted_text,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.image_theme_no_photo),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.image_theme_no_photo_subtitle),
                color = palette.muted_text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        if (selected) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun colors_section(
    pending: ThemeBackground?,
    selected: ColorThemeId,
    on_color: (ColorThemeId) -> Unit,
) {
    val options = remember(selected) {
        if (selected in swatch_order || AsterColorThemes.palette_for(selected) == null) swatch_order else listOf(selected) + swatch_order
    }
    val row_state = rememberLazyListState(initialFirstVisibleItemIndex = (options.indexOf(selected) - 1).coerceAtLeast(0))
    LaunchedEffect(selected) {
        val index = options.indexOf(selected)
        if (index < 0) return@LaunchedEffect
        val visible = row_state.layoutInfo.visibleItemsInfo
        val fully_visible = visible.any { it.index == index && it.offset >= 0 && it.offset + it.size <= row_state.layoutInfo.viewportEndOffset }
        if (!fully_visible) row_state.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.image_theme_colors),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Crossfade(targetState = selected, animationSpec = tween(180), label = "color_name") { shown ->
                Text(
                    text = stringResource(color_theme_label_res(shown)),
                    color = accent_for(shown),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = row_state,
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            modifier = Modifier.testTag("image_theme_colors"),
        ) {
            items(options, key = { it.name }) { option ->
                val palette = AsterColorThemes.palette_for(option) ?: return@items
                theme_swatch(
                    label = stringResource(color_theme_label_res(option)),
                    palette = palette,
                    selected = option == selected,
                    on_click = { on_color(option) },
                    modifier = Modifier.width(76.dp).testTag("image_theme_color_${option.name}"),
                )
            }
        }
        Text(
            text = when {
                pending == null || pending.is_custom -> stringResource(R.string.image_theme_page_subtitle)
                pending.is_generated -> stringResource(R.string.image_theme_credit_generated)
                else -> stringResource(R.string.image_theme_credit_modified, pending.credit)
            },
            color = local_library_palette.current.muted_text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
    }
}

@Composable
private fun your_photo_section(
    selected: Boolean,
    accent: Color,
    on_accent: Color,
    on_pick: (CustomThemeImageMeta) -> Unit,
    on_removed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta by custom_theme_image.meta.collectAsState()
    var importing by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<CustomThemeImageError?>(null) }
    var editor_source by remember { mutableStateOf<Bitmap?>(null) }
    var editor_crop by remember { mutableStateOf<RectF?>(null) }
    var editor_new_source by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || importing || committing) return@rememberLauncherForActivityResult
        importing = true
        error = null
        scope.launch {
            val result = custom_theme_image.import_source(context, uri)
            importing = false
            result
                .onSuccess { bitmap ->
                    editor_crop = null
                    editor_new_source = true
                    editor_source = bitmap
                }
                .onFailure { failure ->
                    error = (failure as? CustomThemeImageException)?.reason ?: CustomThemeImageError.unreadable
                }
        }
    }
    val adjust = {
        if (!importing && !committing) {
            importing = true
            error = null
            scope.launch {
                val bitmap = custom_theme_image.load_source(context)
                importing = false
                if (bitmap == null) {
                    error = CustomThemeImageError.unreadable
                } else {
                    editor_crop = custom_theme_image.stored_crop(context)
                    editor_new_source = false
                    editor_source = bitmap
                }
            }
        }
    }
    editor_source?.let { source ->
        image_theme_photo_editor(
            source = source,
            initial_crop = editor_crop,
            accent = accent,
            on_accent = on_accent,
            busy = committing,
            on_cancel = {
                editor_source = null
                source.recycle()
            },
            on_set = { crop ->
                committing = true
                scope.launch {
                    val result = custom_theme_image.commit(context, source, crop, editor_new_source)
                    committing = false
                    editor_source = null
                    result
                        .onSuccess(on_pick)
                        .onFailure { failure ->
                            source.recycle()
                            error = (failure as? CustomThemeImageException)?.reason ?: CustomThemeImageError.unreadable
                        }
                }
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .testTag("image_theme_yours"),
    ) {
        Text(
            text = stringResource(R.string.image_theme_category_yours),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        val message = stringResource(
            when (error) {
                CustomThemeImageError.too_large -> R.string.image_theme_photo_too_large
                CustomThemeImageError.unsupported -> R.string.image_theme_photo_unsupported
                CustomThemeImageError.unreadable -> R.string.image_theme_photo_unreadable
                null -> R.string.image_theme_photo_private
            },
        )
        val message_color = if (error != null) error_text else local_library_palette.current.muted_text
        val choose = {
            if (!importing && !committing) {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        val current = meta
        if (current == null) {
            choose_photo_row(
                importing = importing,
                accent = accent,
                message = message,
                message_color = message_color,
                on_click = choose,
            )
        } else {
            val screen_height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
            Column(modifier = Modifier.fillMaxWidth()) {
                custom_photo_preview(
                    background = custom_theme_background_entry(current),
                    selected = selected,
                    accent = accent,
                    on_accent = on_accent,
                    on_click = { on_pick(current) },
                    max_height = screen_height * 0.52f,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    photo_action_button(
                        icon = TablerIcons.Crop,
                        label = stringResource(R.string.image_theme_adjust_photo),
                        busy = importing || committing,
                        accent = accent,
                        on_click = adjust,
                        modifier = Modifier.weight(1f).testTag("image_theme_custom_adjust"),
                    )
                    photo_action_button(
                        icon = TablerIcons.Photo,
                        label = stringResource(R.string.image_theme_replace_photo),
                        busy = false,
                        accent = accent,
                        on_click = choose,
                        modifier = Modifier.weight(1f).testTag("image_theme_custom_replace"),
                    )
                    photo_action_button(
                        icon = TablerIcons.Trash,
                        label = stringResource(R.string.image_theme_remove_photo),
                        busy = false,
                        accent = accent,
                        destructive = true,
                        on_click = {
                            if (!importing && !committing) {
                                error = null
                                scope.launch {
                                    withContext(Dispatchers.IO) { custom_theme_image.delete(context) }
                                    on_removed()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("image_theme_custom_remove"),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = message_color,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun choose_photo_row(
    importing: Boolean,
    accent: Color,
    message: String,
    message_color: Color,
    on_click: () -> Unit,
) {
    val dash = local_library_palette.current.dash_line
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(local_library_palette.current.raised_bg)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(enabled = !importing, onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("image_theme_custom_add"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(local_library_palette.current.control_bg),
            contentAlignment = Alignment.Center,
        ) {
            if (importing) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = TablerIcons.Plus,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = stringResource(R.string.image_theme_choose_photo),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message,
                color = message_color,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun photo_action_button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    busy: Boolean,
    accent: Color,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val tint = if (destructive) error_text else Color.White
    Column(
        modifier = modifier
            .height(74.dp)
            .clip(SquircleShape(20.dp))
            .background(local_library_palette.current.raised_bg)
            .clickable(enabled = !busy, onClick = on_click)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun category_shelf(
    category: ThemeCategory,
    list: List<ThemeBackground>,
    selected_id: String?,
    accent: Color,
    on_accent: Color,
    on_pick: (ThemeBackground) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .testTag("image_theme_category_${category.name}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(category.label_res),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = list.size.toString(),
                color = local_library_palette.current.faint_text,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { background ->
                shelf_tile(
                    background = background,
                    selected = background.id == selected_id,
                    accent = accent,
                    on_accent = on_accent,
                    on_click = { on_pick(background) },
                )
            }
        }
    }
}

@Composable
private fun custom_photo_preview(
    background: ThemeBackground,
    selected: Boolean,
    accent: Color,
    on_accent: Color,
    on_click: () -> Unit,
    max_height: Dp,
) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "photo_select")
    val shape = SquircleShape(24.dp)
    val photo = remember_theme_bitmap(background)
    val ratio = photo?.let { it.width.toFloat() / it.height.toFloat() } ?: (3f / 4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .heightIn(max = max_height)
            .aspectRatio(ratio.coerceIn(0.4f, 2.2f))
            .clip(shape)
            .background(background.tint)
            .clickable(onClick = on_click)
            .testTag("image_theme_${background.id}"),
    ) {
        photo?.let { bitmap ->
            Canvas(modifier = Modifier.fillMaxSize()) { draw_cover_sharp(bitmap) }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    if (progress > 0f) 3.dp else 1.dp,
                    lerp(local_library_palette.current.hairline, accent, progress),
                    shape,
                ),
        )
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(lerp(local_library_palette.current.control_bg, accent, progress)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = lerp(local_library_palette.current.control_bg, on_accent, progress),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun DrawScope.draw_cover_sharp(bitmap: ImageBitmap) {
    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

@Composable
private fun shelf_tile(
    background: ThemeBackground,
    selected: Boolean,
    accent: Color,
    on_accent: Color,
    on_click: () -> Unit,
    width: Dp = 144.dp,
    height: Dp = 304.dp,
    fill_width: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = shelf_tile_shape,
) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "tile_select")
    Box(
        modifier = Modifier
            .then(if (fill_width) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(shape)
            .border(1.dp, local_library_palette.current.hairline, shape)
            .clickable(onClick = on_click)
            .testTag("image_theme_${background.id}"),
    ) {
        image_theme_thumbnail(background, Modifier.fillMaxSize())
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, lerp(local_library_palette.current.hairline, accent, progress), shape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(lerp(local_library_palette.current.control_bg, accent, progress)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = lerp(local_library_palette.current.control_bg, on_accent, progress),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun apply_bar(accent: Color, on_accent: Color, nav_bottom: Float, on_apply: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(local_library_palette.current.raised_bg)
            .border(1.dp, local_library_palette.current.hairline)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp + nav_bottom.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent)
                .clickable(onClick = on_apply)
                .testTag("image_theme_apply"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.image_theme_apply_changes),
                color = on_accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun image_theme_thumbnail(background: ThemeBackground, modifier: Modifier = Modifier) {
    val thumb by remember_theme_thumbnail(background)
    Canvas(modifier = modifier.background(background.tint)) {
        thumb?.let { draw_cover(it) }
    }
}

private fun DrawScope.draw_cover(bitmap: ImageBitmap) {
    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}
