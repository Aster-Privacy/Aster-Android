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

import org.astermail.android.ui.common.vertical_scroll_with_indicator
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Photo
import compose.icons.tablericons.Trash
import compose.icons.tablericons.Upload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.mirror_in_rtl
import org.astermail.android.security.LockdownStore
import org.astermail.android.settings.GalleryItem
import org.astermail.android.settings.gallery_categories_present
import org.astermail.android.settings.gallery_thumb_url
import org.astermail.android.settings.profile_picture_gallery_holder
import org.astermail.android.storage.AccountStore
import org.astermail.android.ui.common.current_user_avatar
import org.astermail.android.ui.common.nav_anim_duration_ms
import org.astermail.android.ui.common.nav_backward_exit
import org.astermail.android.ui.common.nav_forward_enter
import org.astermail.android.ui.common.plan_ring
import org.astermail.android.ui.common.remember_has_paid_plan

enum class profile_picture_view {
    main,
    gallery,
}

private enum class gallery_status {
    idle,
    loading,
    ready,
    failed,
}

private val view_ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val view_duration_ms = 240
private val view_shift = 32.dp
private val top_bar_height = 56.dp
private val hero_size = 128.dp
private val tile_min_size = 104.dp
private val option_icon_box = 40.dp

internal fun profile_picture_slide_sign(target: profile_picture_view, rtl: Boolean): Int {
    val forward = if (target == profile_picture_view.gallery) 1 else -1
    return if (rtl) -forward else forward
}

internal fun profile_picture_shown_view(
    requested: profile_picture_view,
    gallery_available: Boolean,
): profile_picture_view = if (gallery_available) requested else profile_picture_view.main

@Composable
private fun remember_gallery_available(): Boolean {
    val context = LocalContext.current
    var lockdown by remember(context) { mutableStateOf(LockdownStore.is_enabled(context)) }
    DisposableEffect(context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            lockdown = LockdownStore.is_enabled(context)
        }
        LockdownStore.register_listener(context, listener)
        lockdown = LockdownStore.is_enabled(context)
        onDispose { LockdownStore.unregister_listener(context, listener) }
    }
    return !lockdown
}

private fun Modifier.block_input(blocked: Boolean): Modifier =
    if (!blocked) {
        this
    } else {
        this
            .clearAndSetSemantics {}
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
    }

@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentScope.is_leaving(): Boolean =
    transition.targetState == EnterExitState.PostExit

@Composable
private fun view_transition(): AnimatedContentTransitionScope<profile_picture_view>.() -> ContentTransform {
    val reduce_motion = aster_reduce_motion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shift_px = with(LocalDensity.current) { view_shift.roundToPx() }
    return remember(reduce_motion, rtl, shift_px) {
        {
            if (reduce_motion) {
                fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap()) using null
            } else {
                val sign = profile_picture_slide_sign(targetState, rtl)
                val spec_float = tween<Float>(view_duration_ms, easing = view_ease)
                val spec_offset = tween<IntOffset>(view_duration_ms, easing = view_ease)
                (
                    fadeIn(animationSpec = spec_float) +
                        slideInHorizontally(animationSpec = spec_offset) { sign * shift_px }
                    ) togetherWith (
                    fadeOut(animationSpec = spec_float) +
                        slideOutHorizontally(animationSpec = spec_offset) { -sign * shift_px }
                    ) using null
            }
        }
    }
}

@Composable
fun profile_picture_sheet(
    account_store: AccountStore,
    picture: String?,
    display_name: String,
    email: String,
    has_saved_picture: Boolean,
    uploading: Boolean,
    removing: Boolean,
    failed: Boolean,
    on_dismiss: () -> Unit,
    on_upload: () -> Unit,
    on_remove: () -> Unit,
    on_choose_image: suspend (ByteArray) -> Boolean,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gallery = remember(context) { profile_picture_gallery_holder.get(context) }
    val gallery_available = remember_gallery_available()
    val has_paid_plan = remember_has_paid_plan()

    var view by remember { mutableStateOf(profile_picture_view.main) }
    var status by remember { mutableStateOf(gallery_status.idle) }
    var items by remember { mutableStateOf(emptyList<GalleryItem>()) }
    var filter by remember { mutableStateOf<String?>(null) }
    var pending_slug by remember { mutableStateOf<String?>(null) }
    var gallery_error by remember { mutableStateOf(false) }
    var load_job by remember { mutableStateOf<Job?>(null) }
    var pick_job by remember { mutableStateOf<Job?>(null) }

    val shown_view = profile_picture_shown_view(view, gallery_available)
    val busy = uploading || removing

    val reduce_motion = aster_reduce_motion()
    val anim_duration = if (reduce_motion) 0 else nav_anim_duration_ms
    val visible_state = remember { MutableTransitionState(false).apply { targetState = true } }
    val request_dismiss: () -> Unit = { visible_state.targetState = false }

    LaunchedEffect(visible_state.currentState, visible_state.targetState) {
        if (!visible_state.targetState && !visible_state.currentState) on_dismiss()
    }

    LaunchedEffect(gallery_available) {
        if (!gallery_available) {
            load_job?.cancel()
            pick_job?.cancel()
            pending_slug = null
            view = profile_picture_view.main
            if (status == gallery_status.loading) status = gallery_status.idle
        }
    }

    val load_gallery: () -> Unit = {
        load_job?.cancel()
        status = gallery_status.loading
        load_job = scope.launch {
            try {
                items = gallery.load_manifest()
                status = gallery_status.ready
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                status = gallery_status.failed
            }
        }
    }

    val open_gallery: () -> Unit = open@{
        if (!gallery_available) return@open
        view = profile_picture_view.gallery
        gallery_error = false
        if (status == gallery_status.idle || status == gallery_status.failed) load_gallery()
    }

    val choose: (String) -> Unit = choose@{ slug ->
        if (pending_slug != null || busy || !gallery_available) return@choose
        pending_slug = slug
        gallery_error = false
        pick_job = scope.launch {
            try {
                val bytes = gallery.fetch_image(slug)
                if (on_choose_image(bytes)) {
                    view = profile_picture_view.main
                } else {
                    gallery_error = true
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                gallery_error = true
            } finally {
                pending_slug = null
            }
        }
    }

    val go_back: () -> Unit = {
        if (profile_picture_shown_view(view, gallery_available) == profile_picture_view.gallery) {
            view = profile_picture_view.main
        } else {
            request_dismiss()
        }
    }

    val page_color = colors.bg_primary.copy(alpha = 1f)
    val light_bars = page_color.luminance() > 0.5f
    val transition = view_transition()

    Dialog(
        onDismissRequest = go_back,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val dialog_view = LocalView.current
        SideEffect {
            val window = (dialog_view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, dialog_view).apply {
                isAppearanceLightStatusBars = light_bars
                isAppearanceLightNavigationBars = light_bars
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
        }
        AnimatedVisibility(
            visibleState = visible_state,
            enter = nav_forward_enter(anim_duration),
            exit = nav_backward_exit(anim_duration),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(page_color)
                    .statusBarsPadding()
                    .testTag("profile_picture_sheet"),
            ) {
                profile_picture_top_bar(
                    shown_view = shown_view,
                    transition = transition,
                    on_back = go_back,
                )
                AnimatedContent(
                    targetState = shown_view,
                    transitionSpec = transition,
                    contentAlignment = Alignment.TopStart,
                    label = "profile_picture_stage",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { target ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .block_input(is_leaving()),
                    ) {
                        if (target == profile_picture_view.main) {
                            profile_picture_main_view(
                                account_store = account_store,
                                picture = picture,
                                display_name = display_name,
                                email = email,
                                has_paid_plan = has_paid_plan,
                                has_saved_picture = has_saved_picture,
                                busy = busy || pending_slug != null,
                                failed = failed,
                                gallery_available = gallery_available,
                                on_gallery = open_gallery,
                                on_upload = on_upload,
                                on_remove = on_remove,
                            )
                        } else {
                            profile_picture_gallery_view(
                                status = status,
                                items = items,
                                filter = filter,
                                pending_slug = pending_slug,
                                busy = busy,
                                failed = gallery_error,
                                on_filter = { filter = it },
                                on_retry = load_gallery,
                                on_choose = choose,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun profile_picture_top_bar(
    shown_view: profile_picture_view,
    transition: AnimatedContentTransitionScope<profile_picture_view>.() -> ContentTransform,
    on_back: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(top_bar_height)
            .padding(horizontal = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsterIconButton(
            icon = TablerIcons.ArrowLeft,
            content_description = stringResource(
                if (shown_view == profile_picture_view.gallery) R.string.back else R.string.close,
            ),
            onClick = on_back,
            tint = colors.text_primary,
            icon_size = 24,
            auto_mirror = true,
            modifier = Modifier.testTag("profile_picture_back"),
        )
        AnimatedContent(
            targetState = shown_view,
            transitionSpec = transition,
            contentAlignment = Alignment.CenterStart,
            label = "profile_picture_title",
            modifier = Modifier
                .weight(1f)
                .padding(start = AsterSpacing.xs),
        ) { target ->
            Text(
                text = stringResource(
                    if (target == profile_picture_view.gallery) {
                        R.string.profile_picture_gallery
                    } else {
                        R.string.profile_picture_title
                    },
                ),
                color = colors.text_primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun profile_picture_main_view(
    account_store: AccountStore,
    picture: String?,
    display_name: String,
    email: String,
    has_paid_plan: Boolean,
    has_saved_picture: Boolean,
    busy: Boolean,
    failed: Boolean,
    gallery_available: Boolean,
    on_gallery: () -> Unit,
    on_upload: () -> Unit,
    on_remove: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .vertical_scroll_with_indicator()
            .navigationBarsPadding()
            .padding(horizontal = AsterSpacing.lg)
            .padding(top = AsterSpacing.lg, bottom = AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        plan_ring(size = hero_size, enabled = has_paid_plan) {
            Box(
                modifier = Modifier
                    .size(hero_size)
                    .clip(CircleShape)
                    .testTag("profile_picture_preview"),
                contentAlignment = Alignment.Center,
            ) {
                current_user_avatar(
                    account_store = account_store,
                    size = hero_size,
                    profile_picture_url = picture,
                )
                if (busy) {
                    Box(
                        modifier = Modifier
                            .size(hero_size)
                            .background(colors.bg_primary.copy(alpha = 0.60f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = colors.accent_blue,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(AsterSpacing.lg))
        if (display_name.isNotBlank()) {
            Text(
                text = display_name,
                color = colors.text_primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (email.isNotBlank()) {
            Spacer(Modifier.height(AsterSpacing.xs))
            Text(
                text = email,
                color = colors.text_secondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(AsterSpacing.xl))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = settings_group_inset),
            ) {
                if (gallery_available) {
                    profile_picture_option_row(
                        icon = TablerIcons.Photo,
                        label = stringResource(R.string.profile_picture_gallery),
                        hint = stringResource(R.string.profile_picture_gallery_hint),
                        enabled = !busy,
                        danger = false,
                        test_tag = "profile_picture_option_gallery",
                        on_click = on_gallery,
                    )
                    settings_row_gap()
                }
                profile_picture_option_row(
                    icon = TablerIcons.Upload,
                    label = stringResource(R.string.profile_picture_upload),
                    hint = stringResource(R.string.profile_picture_upload_hint),
                    enabled = !busy,
                    danger = false,
                    test_tag = "profile_picture_option_upload",
                    on_click = on_upload,
                )
                if (has_saved_picture) {
                    settings_row_gap()
                    profile_picture_option_row(
                        icon = TablerIcons.Trash,
                        label = stringResource(R.string.remove_photo),
                        hint = stringResource(R.string.profile_picture_remove_hint),
                        enabled = !busy,
                        danger = true,
                        test_tag = "profile_picture_option_remove",
                        on_click = on_remove,
                    )
                }
            }
        }
        if (failed) {
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = stringResource(R.string.error_try_again),
                color = colors.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun profile_picture_option_row(
    icon: ImageVector,
    label: String,
    hint: String,
    enabled: Boolean,
    danger: Boolean,
    test_tag: String,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val accent = if (danger) colors.danger else colors.accent_blue
    val title_color = when {
        !enabled -> colors.text_muted
        danger -> colors.danger
        else -> colors.text_primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = on_click)
            .heightIn(min = settings_row_min_height)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm)
            .testTag(test_tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(option_icon_box)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (enabled) 0.12f else 0.06f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accent else colors.text_muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = title_color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = hint,
                color = colors.text_tertiary,
                fontSize = 13.sp,
            )
        }
        if (!danger) {
            Icon(
                imageVector = TablerIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(16.dp).mirror_in_rtl(),
            )
        }
    }
}

@Composable
private fun profile_picture_gallery_view(
    status: gallery_status,
    items: List<GalleryItem>,
    filter: String?,
    pending_slug: String?,
    busy: Boolean,
    failed: Boolean,
    on_filter: (String?) -> Unit,
    on_retry: () -> Unit,
    on_choose: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val nav_bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    when (status) {
        gallery_status.idle, gallery_status.loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.accent_blue,
                )
            }
        }
        gallery_status.failed -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = AsterSpacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.profile_picture_gallery_failed),
                    color = colors.text_secondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AsterSpacing.lg))
                AsterSecondaryButton(
                    label = stringResource(R.string.retry),
                    onClick = on_retry,
                    modifier = Modifier.testTag("profile_picture_gallery_retry"),
                )
            }
        }
        gallery_status.ready -> {
            val categories = remember(items) { gallery_categories_present(items) }
            val visible = remember(items, filter) {
                if (filter == null) items else items.filter { it.category == filter }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                    contentPadding = PaddingValues(
                        start = AsterSpacing.lg,
                        end = AsterSpacing.lg,
                        top = AsterSpacing.xs,
                        bottom = AsterSpacing.sm,
                    ),
                ) {
                    item(key = "all") {
                        gallery_chip(
                            label = stringResource(R.string.profile_picture_cat_all),
                            selected = filter == null,
                            on_click = { on_filter(null) },
                        )
                    }
                    items(categories, key = { it }) { category ->
                        gallery_chip(
                            label = stringResource(gallery_category_label(category)),
                            selected = filter == category,
                            on_click = { on_filter(category) },
                        )
                    }
                }
                if (failed) {
                    Text(
                        text = stringResource(R.string.error_try_again),
                        color = colors.danger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xs),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = tile_min_size),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("profile_picture_gallery_grid"),
                    contentPadding = PaddingValues(
                        start = AsterSpacing.lg,
                        end = AsterSpacing.lg,
                        top = AsterSpacing.sm,
                        bottom = AsterSpacing.xl + nav_bottom,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AsterSpacing.lg),
                ) {
                    items(visible, key = { it.slug }) { item ->
                        gallery_tile(
                            slug = item.slug,
                            credit = item.credit,
                            pending = pending_slug == item.slug,
                            enabled = pending_slug == null && !busy,
                            on_click = { on_choose(item.slug) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun gallery_chip(
    label: String,
    selected: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accent_blue else field_surface_color(colors), shape)
            .clickable(role = Role.Tab, onClick = on_click)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.on_accent else colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun gallery_tile(
    slug: String,
    credit: String?,
    pending: Boolean,
    enabled: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val request = remember(slug, context) {
        ImageRequest.Builder(context)
            .data(gallery_thumb_url(slug))
            .crossfade(150)
            .build()
    }
    val caption = credit?.let {
        stringResource(R.string.profile_picture_credit, "\u2068$it\u2069")
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(field_surface_color(colors), CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = on_click)
                .semantics { if (caption != null) contentDescription = caption }
                .testTag("profile_picture_tile_$slug"),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (pending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bg_primary.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent_blue,
                    )
                }
            }
        }
        if (caption != null) {
            Text(
                text = caption,
                color = colors.text_muted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {}
                    .testTag("profile_picture_credit_$slug"),
            )
        }
    }
}

private fun gallery_category_label(category: String): Int = when (category) {
    "space" -> R.string.profile_picture_cat_space
    "night_sky" -> R.string.profile_picture_cat_night_sky
    "water" -> R.string.profile_picture_cat_water
    "planets" -> R.string.profile_picture_cat_planets
    "landscapes" -> R.string.profile_picture_cat_landscapes
    "forest" -> R.string.profile_picture_cat_forest
    "cities" -> R.string.profile_picture_cat_cities
    "aurora" -> R.string.profile_picture_cat_aurora
    "mountains" -> R.string.profile_picture_cat_mountains
    "ocean" -> R.string.profile_picture_cat_ocean
    "desert" -> R.string.profile_picture_cat_desert
    else -> R.string.profile_picture_cat_all
}
