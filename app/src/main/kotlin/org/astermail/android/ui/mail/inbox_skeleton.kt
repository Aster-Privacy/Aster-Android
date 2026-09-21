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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.shimmer
import org.astermail.android.design.components.shimmer_appearance
import org.astermail.android.design.components.shimmer_line
import org.astermail.android.design.components.shimmer_state
import org.astermail.android.ui.common.page_surface

const val inbox_skeleton_tag = "inbox_skeleton"
const val inbox_skeleton_row_tag = "inbox_skeleton_row"

internal const val skeleton_preview_row_limit = 12
internal const val skeleton_preview_alpha = 0.62f
private const val skeleton_preview_field_sep = "\u001f"
private const val skeleton_preview_row_sep = "\u001e"

data class SkeletonRowPreview(
    val sender: String = "",
    val subject: String = "",
    val preview: String = "",
    val time: String = "",
    val unread: Boolean = false,
)

internal fun encode_skeleton_rows(rows: List<SkeletonRowPreview>): String =
    rows.take(skeleton_preview_row_limit).joinToString(skeleton_preview_row_sep) { row ->
        listOf(
            row.sender.sanitized_skeleton_field(),
            row.subject.sanitized_skeleton_field(),
            row.preview.sanitized_skeleton_field(),
            row.time.sanitized_skeleton_field(),
            if (row.unread) "1" else "0",
        ).joinToString(skeleton_preview_field_sep)
    }

private fun String.sanitized_skeleton_field(): String =
    replace(skeleton_preview_field_sep, " ").replace(skeleton_preview_row_sep, " ").take(160)

internal fun decode_skeleton_rows(raw: String?): List<SkeletonRowPreview> {
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.split(skeleton_preview_row_sep).mapNotNull { chunk ->
        val parts = chunk.split(skeleton_preview_field_sep)
        if (parts.size < 5) {
            null
        } else {
            SkeletonRowPreview(parts[0], parts[1], parts[2], parts[3], parts[4] == "1")
        }
    }
}

internal const val skeleton_sweep_lag = 0.06f
internal const val skeleton_defer_ms = 150L
internal const val skeleton_min_visible_ms = 140L
internal const val skeleton_fade_out_ms = 90
internal const val skeleton_handoff_ms = 90
internal const val skeleton_await_rows_ms = 60L

internal fun skeleton_visible_after(has_data: Boolean, pending: Boolean, pending_for_ms: Long): Boolean =
    !has_data && pending && pending_for_ms >= skeleton_defer_ms

enum class SkeletonPhase { blank, skeleton, content }

data class SkeletonStep(val target: SkeletonPhase, val after_ms: Long)

data class SkeletonGeometry(
    val list_density: String? = null,
    val show_avatar: Boolean = true,
    val show_preview: Boolean = true,
)

fun skeleton_geometry_of(
    prefs: org.astermail.android.api.preferences.UserPreferences?,
): SkeletonGeometry? = prefs?.let {
    SkeletonGeometry(
        list_density = it.mail_list_density,
        show_avatar = it.show_profile_pictures != false,
        show_preview = it.show_email_preview != false,
    )
}

private const val skeleton_geometry_prefs = "aster_inbox_skeleton_geometry"
private const val skeleton_geometry_density_key = "list_density"
private const val skeleton_geometry_avatar_key = "show_avatar"
private const val skeleton_geometry_preview_key = "show_preview"

private fun skeleton_geometry_store(context: android.content.Context): android.content.SharedPreferences? =
    runCatching {
        context.getSharedPreferences(skeleton_geometry_prefs, android.content.Context.MODE_PRIVATE)
    }.getOrNull()

internal fun read_skeleton_geometry(context: android.content.Context): SkeletonGeometry {
    val store = skeleton_geometry_store(context) ?: return SkeletonGeometry()
    return runCatching {
        SkeletonGeometry(
            list_density = store.getString(skeleton_geometry_density_key, null),
            show_avatar = store.getBoolean(skeleton_geometry_avatar_key, true),
            show_preview = store.getBoolean(skeleton_geometry_preview_key, true),
        )
    }.getOrDefault(SkeletonGeometry())
}

internal fun write_skeleton_geometry(context: android.content.Context, value: SkeletonGeometry) {
    val store = skeleton_geometry_store(context) ?: return
    runCatching {
        store.edit()
            .putString(skeleton_geometry_density_key, value.list_density)
            .putBoolean(skeleton_geometry_avatar_key, value.show_avatar)
            .putBoolean(skeleton_geometry_preview_key, value.show_preview)
            .apply()
    }
}

internal fun next_skeleton_geometry(
    shown: SkeletonGeometry,
    live: SkeletonGeometry?,
    phase: SkeletonPhase,
): SkeletonGeometry = when {
    live == null -> shown
    phase == SkeletonPhase.skeleton -> shown
    else -> live
}

@Composable
fun remember_skeleton_geometry(phase: SkeletonPhase, live: SkeletonGeometry?): SkeletonGeometry {
    val context = androidx.compose.ui.platform.LocalContext.current
    val shown = remember(context) { mutableStateOf(read_skeleton_geometry(context)) }
    LaunchedEffect(live, phase) {
        if (live != null && live != shown.value) write_skeleton_geometry(context, live)
        shown.value = next_skeleton_geometry(shown.value, live, phase)
    }
    return shown.value
}

private const val skeleton_row_height_key = "row_height_px"

private const val skeleton_first_row_height_key = "first_row_height_px"

internal const val skeleton_row_sample_count = 4

internal val skeleton_three_line_min = 84.dp

internal fun modal_row_height(samples: Collection<Int>): Int {
    val usable = samples.filter { it > 0 }
    if (usable.isEmpty()) return 0
    return usable.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
        .first().key
}

internal fun skeleton_row_shows_preview(show_preview: Boolean, row_height: Dp): Boolean =
    show_preview && (row_height <= 0.dp || row_height >= skeleton_three_line_min)

internal fun read_skeleton_row_height(context: android.content.Context): Int {
    val store = skeleton_geometry_store(context) ?: return 0
    return runCatching { store.getInt(skeleton_row_height_key, 0) }.getOrDefault(0)
}

internal fun read_skeleton_first_row_height(context: android.content.Context): Int {
    val store = skeleton_geometry_store(context) ?: return 0
    return runCatching { store.getInt(skeleton_first_row_height_key, 0) }.getOrDefault(0)
}

internal fun write_skeleton_row_height(context: android.content.Context, px: Int) {
    val store = skeleton_geometry_store(context) ?: return
    runCatching { store.edit().putInt(skeleton_row_height_key, px).apply() }
}

internal fun write_skeleton_first_row_height(context: android.content.Context, px: Int) {
    val store = skeleton_geometry_store(context) ?: return
    runCatching { store.edit().putInt(skeleton_first_row_height_key, px).apply() }
}

private fun skeleton_rows_key(folder: String): String = "rows_" + folder

internal fun read_skeleton_rows(
    context: android.content.Context,
    folder: String,
): List<SkeletonRowPreview> {
    val store = skeleton_geometry_store(context) ?: return emptyList()
    return runCatching {
        decode_skeleton_rows(store.getString(skeleton_rows_key(folder), null))
    }.getOrDefault(emptyList())
}

internal fun write_skeleton_rows(
    context: android.content.Context,
    folder: String,
    rows: List<SkeletonRowPreview>,
) {
    val store = skeleton_geometry_store(context) ?: return
    runCatching {
        store.edit().putString(skeleton_rows_key(folder), encode_skeleton_rows(rows)).apply()
    }
}

@Composable
fun remember_row_preview_recorder(folder: String): (Int, SkeletonRowPreview) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val samples = remember(context, folder) { sortedMapOf<Int, SkeletonRowPreview>() }
    val written = remember(context, folder) { arrayOf(encode_skeleton_rows(read_skeleton_rows(context, folder))) }
    return remember(context, folder) {
        { index: Int, row: SkeletonRowPreview ->
            if (index in 0 until skeleton_preview_row_limit && samples[index] != row) {
                samples[index] = row
                val encoded = encode_skeleton_rows(samples.values.toList())
                if (encoded != written[0]) {
                    written[0] = encoded
                    write_skeleton_rows(context, folder, samples.values.toList())
                }
            }
        }
    }
}

@Composable
fun remember_row_height_recorder(): (Int, Int) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val samples = remember(context) { mutableMapOf<Int, Int>() }
    val written = remember(context) {
        intArrayOf(read_skeleton_row_height(context), read_skeleton_first_row_height(context))
    }
    return remember(context) {
        { index: Int, height: Int ->
            if (index in 0 until skeleton_row_sample_count && height > 0 && samples[index] != height) {
                samples[index] = height
                if (index == 0 && height != written[1]) {
                    written[1] = height
                    write_skeleton_first_row_height(context, height)
                }
                if (samples.size >= skeleton_row_sample_count) {
                    val modal = modal_row_height(samples.filterKeys { it > 0 }.values)
                    if (modal > 0 && modal != written[0]) {
                        written[0] = modal
                        write_skeleton_row_height(context, modal)
                    }
                }
            }
        }
    }
}

@Composable
fun remember_row_geometry(live: SkeletonGeometry?): SkeletonGeometry {
    val context = androidx.compose.ui.platform.LocalContext.current
    val shown = remember(context) { mutableStateOf(read_skeleton_geometry(context)) }
    LaunchedEffect(live) {
        if (live == null) return@LaunchedEffect
        if (live != shown.value) write_skeleton_geometry(context, live)
        shown.value = live
    }
    return shown.value
}

internal fun initial_skeleton_phase(wanted: Boolean, rows_imminent: Boolean): SkeletonPhase = when {
    !wanted -> SkeletonPhase.content
    rows_imminent -> SkeletonPhase.blank
    else -> SkeletonPhase.skeleton
}

internal fun plan_skeleton_step(
    current: SkeletonPhase,
    wanted: Boolean,
    rows_imminent: Boolean,
    skeleton_shown_for_ms: Long,
): SkeletonStep = when {
    wanted && current == SkeletonPhase.skeleton -> SkeletonStep(SkeletonPhase.skeleton, 0L)
    wanted && (rows_imminent || current == SkeletonPhase.content) ->
        SkeletonStep(SkeletonPhase.skeleton, skeleton_await_rows_ms)
    wanted -> SkeletonStep(SkeletonPhase.skeleton, 0L)
    current == SkeletonPhase.skeleton ->
        SkeletonStep(SkeletonPhase.content, (skeleton_min_visible_ms - skeleton_shown_for_ms).coerceAtLeast(0L))
    else -> SkeletonStep(SkeletonPhase.content, 0L)
}

@Composable
fun remember_skeleton_phase(wanted: Boolean, rows_imminent: Boolean): State<SkeletonPhase> {
    val phase = remember { mutableStateOf(initial_skeleton_phase(wanted, rows_imminent)) }
    val shown_at = remember {
        longArrayOf(if (phase.value == SkeletonPhase.skeleton) android.os.SystemClock.uptimeMillis() else 0L)
    }
    LaunchedEffect(wanted, rows_imminent) {
        val shown_for = if (phase.value == SkeletonPhase.skeleton) {
            android.os.SystemClock.uptimeMillis() - shown_at[0]
        } else {
            0L
        }
        val step = plan_skeleton_step(phase.value, wanted, rows_imminent, shown_for)
        if (step.after_ms > 0L) delay(step.after_ms)
        if (phase.value == step.target) return@LaunchedEffect
        if (step.target == SkeletonPhase.skeleton) shown_at[0] = android.os.SystemClock.uptimeMillis()
        phase.value = step.target
    }
    return phase
}

@Composable
fun Modifier.skeleton_handoff(phase: SkeletonPhase): Modifier {
    val reduce_motion = aster_reduce_motion()
    val alpha = remember { Animatable(if (phase == SkeletonPhase.skeleton) 0f else 1f) }
    LaunchedEffect(phase, reduce_motion) {
        when (phase) {
            SkeletonPhase.skeleton -> alpha.snapTo(0f)
            SkeletonPhase.blank -> Unit
            SkeletonPhase.content -> if (alpha.value < 1f) {
                if (reduce_motion) alpha.snapTo(1f) else alpha.animateTo(1f, tween(skeleton_handoff_ms))
            }
        }
    }
    return this.graphicsLayer { this.alpha = alpha.value }
}

@Composable
fun inbox_skeleton(
    modifier: Modifier = Modifier,
    list_density: String? = null,
    row_count: Int = 10,
    show_avatar: Boolean = true,
    show_preview: Boolean = true,
    row_height: Dp = 0.dp,
    first_row_height: Dp = 0.dp,
    previews: List<SkeletonRowPreview> = emptyList(),
) {
    val colors = AsterMaterial.colors
    val state = shimmer_state(surface = inbox_card_read_color(colors))
    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer()
            .page_surface(colors)
            .testTag(inbox_skeleton_tag),
    ) {
        repeat(row_count) { index ->
            inbox_skeleton_row(
                state = state,
                list_density = list_density,
                is_first = index == 0,
                is_last = index == row_count - 1,
                show_avatar = show_avatar,
                show_preview = show_preview,
                row_height = if (index == 0 && first_row_height > 0.dp) first_row_height else row_height,
                cached = previews.getOrNull(index),
            )
        }
    }
}

@Composable
fun inbox_skeleton_layer(
    phase: SkeletonPhase,
    modifier: Modifier = Modifier,
    live_geometry: SkeletonGeometry? = null,
    folder: String = "inbox",
) {
    val reduce_motion = aster_reduce_motion()
    val geometry = remember_skeleton_geometry(phase, live_geometry)
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val row_height = remember(context, density) {
        val px = read_skeleton_row_height(context)
        if (px > 0) with(density) { px.toDp() } else 0.dp
    }
    val first_row_height = remember(context, density) {
        val px = read_skeleton_first_row_height(context)
        if (px > 0) with(density) { px.toDp() } else 0.dp
    }
    val previews = remember(context, folder) { read_skeleton_rows(context, folder) }
    AnimatedVisibility(
        visible = phase == SkeletonPhase.skeleton,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = if (reduce_motion) ExitTransition.None else fadeOut(tween(skeleton_fade_out_ms)),
    ) {
        val loading_label = stringResource(R.string.loading)
        inbox_skeleton(
            list_density = geometry.list_density,
            show_avatar = geometry.show_avatar,
            show_preview = geometry.show_preview,
            row_height = row_height,
            first_row_height = first_row_height,
            previews = previews,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = loading_label
            },
        )
    }
}

@Composable
private fun skeleton_text_line(
    style: TextStyle,
    state: shimmer_appearance,
    modifier: Modifier = Modifier,
) {
    Text(
        text = " ",
        style = style,
        maxLines = 1,
        modifier = modifier.shimmer_line(state),
    )
}

@Composable
private fun skeleton_cached_line(
    text: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    weight: FontWeight? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        color = color.copy(alpha = color.alpha * skeleton_preview_alpha),
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun inbox_skeleton_row(
    state: shimmer_appearance = shimmer_state(surface = inbox_card_read_color(AsterMaterial.colors)),
    list_density: String? = null,
    is_first: Boolean = false,
    is_last: Boolean = true,
    show_avatar: Boolean = true,
    show_preview: Boolean = true,
    row_height: Dp = 0.dp,
    cached: SkeletonRowPreview? = null,
) {
    val colors = AsterMaterial.colors
    val metrics = remember(list_density) { inbox_row_metrics(list_density) }
    val content_height = if (row_height > 0.dp && !is_last) row_height - inbox_group_split else row_height
    val draw_preview = skeleton_row_shows_preview(show_preview, content_height)
    val shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val card_color = remember(colors) { inbox_card_read_color(colors) }
    val sender_style = inbox_sender_text_style()
    val time_style = inbox_time_text_style()
    val subject_style = inbox_subject_text_style()
    val preview_style = inbox_preview_text_style()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(inbox_skeleton_row_tag)
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(shape)
            .acrylic_backdrop(colors)
            .drawBehind { drawRect(card_color) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
                .then(
                    if (content_height > 0.dp) {
                        Modifier.height(content_height)
                    } else {
                        Modifier.defaultMinSize(minHeight = metrics.min_height)
                    },
                )
                .padding(
                    start = inbox_card_content_padding,
                    end = inbox_card_content_padding,
                    top = metrics.vertical_padding,
                    bottom = metrics.vertical_padding,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            if (show_avatar) {
                Box(
                    modifier = Modifier
                        .size(metrics.avatar_size)
                        .shimmer(state, CircleShape),
                )
                Spacer(Modifier.width(AsterSpacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (cached != null && cached.sender.isNotBlank()) {
                            skeleton_cached_line(
                                text = cached.sender,
                                style = sender_style,
                                color = inbox_sender_color(colors, cached.unread),
                                weight = if (cached.unread) FontWeight.Bold else FontWeight.Normal,
                            )
                        } else {
                            skeleton_text_line(sender_style, state, Modifier.fillMaxWidth(0.42f))
                        }
                    }
                    if (cached != null && cached.time.isNotBlank()) {
                        skeleton_cached_line(
                            text = cached.time,
                            style = time_style,
                            color = inbox_time_color(colors, cached.unread),
                            weight = if (cached.unread) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(start = AsterSpacing.sm),
                        )
                    } else {
                        skeleton_text_line(
                            time_style,
                            state,
                            Modifier
                                .padding(start = AsterSpacing.sm)
                                .width(36.dp),
                        )
                    }
                }
                Spacer(Modifier.height(metrics.line_gap))
                if (draw_preview) {
                    if (cached != null && cached.subject.isNotBlank()) {
                        skeleton_cached_line(
                            text = cached.subject,
                            style = subject_style,
                            color = inbox_subject_color(colors, cached.unread),
                            weight = if (cached.unread) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    } else {
                        skeleton_text_line(subject_style, state, Modifier.fillMaxWidth(0.68f))
                    }
                    Spacer(Modifier.height(metrics.line_gap))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        val body_style = if (draw_preview) preview_style else subject_style
                        val body_text = if (draw_preview) cached?.preview else cached?.subject
                        val body_unread = cached?.unread == true
                        if (!body_text.isNullOrBlank()) {
                            skeleton_cached_line(
                                text = body_text,
                                style = body_style,
                                color = if (draw_preview) {
                                    inbox_preview_color(colors, body_unread)
                                } else {
                                    inbox_subject_color(colors, body_unread)
                                },
                            )
                        } else {
                            skeleton_text_line(
                                body_style,
                                state,
                                Modifier.fillMaxWidth(if (draw_preview) 0.9f else 0.68f),
                            )
                        }
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Box(modifier = Modifier.size(inbox_star_slot_size))
                }
            }
        }
    }
}
