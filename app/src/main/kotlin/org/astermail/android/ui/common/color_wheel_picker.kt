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

package org.astermail.android.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.AsterTextField

const val default_custom_color = "#3b82f6"

private val rainbow_stops = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

private fun rgb_color(rgb: Int): Color = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))

@Composable
fun color_swatch_row(
    value: String?,
    on_pick: (String) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<String> = display_color_presets,
    max_swatch_size: Dp = 32.dp,
    test_tag_prefix: String = "color_swatch",
    custom_test_tag: String = "custom_color_swatch",
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val metrics = fit_swatch_row(
            available = maxWidth.value,
            count = presets.size + 1,
            max_size = max_swatch_size.value,
            max_gap = 10f,
            min_gap = 4f,
        )
        val swatch_size = metrics.size.dp
        val cell_width = swatch_cell_width(metrics, maxWidth.value, presets.size + 1).dp
        val selected_hex = normalize_hex_color(value)
        Row(verticalAlignment = Alignment.CenterVertically) {
            presets.forEachIndexed { index, hex ->
                val normalized = normalize_hex_color(hex) ?: hex
                val rgb = hex_to_rgb(normalized) ?: 0x3B82F6
                val is_selected = selected_hex == normalized
                val name_res = preset_color_name_res(normalized)
                val swatch_label = if (name_res != null) {
                    stringResource(name_res)
                } else {
                    stringResource(R.string.color_swatch_option, index + 1, presets.size)
                }
                Box(
                    modifier = Modifier
                        .width(cell_width)
                        .swatch_target(swatch_size)
                        .selectable(
                            selected = is_selected,
                            interactionSource = null,
                            indication = ripple(bounded = false, radius = swatch_size / 2 + 4.dp),
                            role = Role.RadioButton,
                        ) { on_pick(normalized) }
                        .semantics { contentDescription = swatch_label }
                        .testTag("${test_tag_prefix}_$normalized"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(swatch_size)
                            .clip(CircleShape)
                            .background(rgb_color(rgb))
                            .swatch_ring(is_selected),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (is_selected) swatch_check(rgb, swatch_size)
                    }
                }
            }
            custom_color_swatch(
                value = value,
                presets = presets,
                on_pick = on_pick,
                modifier = Modifier.width(cell_width),
                size = swatch_size,
                test_tag = custom_test_tag,
            )
        }
    }
}

private val min_swatch_target = 48.dp

private fun Modifier.swatch_target(swatch_size: Dp): Modifier =
    sizeIn(minWidth = min_swatch_target, minHeight = min_swatch_target)
        .sizeIn(minWidth = swatch_size, minHeight = swatch_size)

private fun preset_color_name_res(hex: String): Int? = when (preset_color_name(hex)) {
    "red" -> R.string.color_name_red
    "orange" -> R.string.color_name_orange
    "yellow" -> R.string.color_name_yellow
    "green" -> R.string.color_name_green
    "teal" -> R.string.color_name_teal
    "blue" -> R.string.color_name_blue
    "purple" -> R.string.color_name_purple
    "pink" -> R.string.color_name_pink
    "brown" -> R.string.color_name_brown
    "gray" -> R.string.color_name_gray
    else -> null
}

@Composable
private fun Modifier.swatch_ring(is_selected: Boolean): Modifier {
    val colors = AsterMaterial.colors
    return border(
        if (is_selected) 2.dp else 1.dp,
        if (is_selected) colors.text_primary else colors.border_secondary,
        CircleShape,
    )
}

@Composable
private fun swatch_check(fill_rgb: Int, swatch_size: Dp) {
    Icon(
        imageVector = TablerIcons.Check,
        contentDescription = null,
        tint = if (prefers_dark_text(fill_rgb)) Color.Black else Color.White,
        modifier = Modifier.size(swatch_size * 0.5f),
    )
}

@Composable
fun custom_color_swatch(
    value: String?,
    presets: List<String>,
    on_pick: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    test_tag: String = "custom_color_swatch",
) {
    val is_selected = is_custom_color(value, presets)
    val selected_rgb = if (is_selected) hex_to_rgb(value ?: "") else null
    var is_open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.custom_color)
    Box(
        modifier = modifier
            .swatch_target(size)
            .selectable(
                selected = is_selected,
                interactionSource = null,
                indication = ripple(bounded = false, radius = size / 2 + 4.dp),
                role = Role.RadioButton,
            ) { is_open = true }
            .semantics { contentDescription = label }
            .testTag(test_tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (selected_rgb != null) {
                        Modifier.background(rgb_color(selected_rgb))
                    } else {
                        Modifier.background(Brush.sweepGradient(rainbow_stops))
                    }
                )
                .swatch_ring(is_selected),
            contentAlignment = Alignment.Center,
        ) {
            if (selected_rgb != null) swatch_check(selected_rgb, size)
        }
    }
    if (is_open) {
        color_wheel_sheet(
            initial = value,
            on_dismiss = { is_open = false },
            on_done = { hex ->
                is_open = false
                on_pick(hex)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun color_wheel_sheet(
    initial: String?,
    on_dismiss: () -> Unit,
    on_done: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val start_hex = normalize_hex_color(initial) ?: default_custom_color
    var hsv by remember { mutableStateOf(hex_to_hsv(start_hex) ?: hsv_color(0f, 0f, 1f)) }
    var hex_text by remember { mutableStateOf(start_hex) }
    var hex_error by remember { mutableStateOf(false) }
    var hex_focused by remember { mutableStateOf(false) }
    val focus_manager = LocalFocusManager.current
    val validate_hex = {
        val valid = normalize_hex_color(hex_text) != null
        hex_error = !valid
        valid
    }
    val current_hex = hsv_to_hex(hsv)
    val current_color = rgb_color(hsv_to_rgb(hsv))
    val close = { after: () -> Unit ->
        scope.launch { sheet_state.hide() }.invokeOnCompletion { after() }
        Unit
    }

    aster_modal_sheet(
        on_dismiss_request = on_dismiss,
        sheet_state = sheet_state,
        container_color = sheet_container_color(colors),
        modifier = Modifier.testTag("color_wheel_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AsterSpacing.lg)
                .padding(bottom = AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.custom_color),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.text_primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.lg))
            hsv_wheel(
                hsv = hsv,
                on_change = { hue, saturation ->
                    hsv = hsv.copy(hue = hue, saturation = saturation)
                    hex_text = hsv_to_hex(hsv)
                    hex_error = false
                },
                modifier = Modifier.size(240.dp),
            )
            Spacer(Modifier.height(AsterSpacing.lg))
            Text(
                text = stringResource(R.string.color_brightness),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text_secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            brightness_slider(
                hsv = hsv,
                on_change = { value ->
                    hsv = hsv.copy(value = value)
                    hex_text = hsv_to_hex(hsv)
                    hex_error = false
                },
                label = stringResource(R.string.color_brightness),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .size(40.dp)
                        .background(current_color, CircleShape)
                        .border(1.dp, colors.border_secondary, CircleShape)
                        .testTag("color_wheel_preview"),
                )
                Spacer(Modifier.width(AsterSpacing.md))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (hex_focused && !state.hasFocus) validate_hex()
                            hex_focused = state.hasFocus
                        }
                        .testTag("color_wheel_hex"),
                ) {
                    AsterTextField(
                        value = hex_text,
                        onValueChange = { text ->
                            val trimmed = text.take(7)
                            hex_text = trimmed
                            hex_error = false
                            hex_to_hsv(trimmed)?.let { hsv = it }
                        },
                        label = stringResource(R.string.hex_color),
                        placeholder = stringResource(R.string.custom_theme_hex_placeholder),
                        error_text = if (hex_error) stringResource(R.string.custom_theme_hex_invalid) else null,
                        keyboard_options = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboard_actions = KeyboardActions(
                            onDone = {
                                validate_hex()
                                focus_manager.clearFocus()
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                AsterSecondaryButton(
                    label = stringResource(R.string.cancel),
                    onClick = { close(on_dismiss) },
                    modifier = Modifier.weight(1f).testTag("color_wheel_cancel"),
                )
                AsterButton(
                    label = stringResource(R.string.done),
                    onClick = {
                        if (validate_hex()) close { on_done(current_hex) }
                    },
                    modifier = Modifier.weight(1f).testTag("color_wheel_done"),
                )
            }
        }
    }
}

@Composable
private fun hsv_wheel(
    hsv: hsv_color,
    on_change: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest_change by rememberUpdatedState(on_change)
    Canvas(
        modifier = modifier
            .testTag("color_wheel")
            .pointerInput(Unit) {
                fun apply(position: Offset) {
                    val radius = minOf(size.width, size.height) / 2f
                    val (hue, saturation) = wheel_hue_saturation(
                        position.x - size.width / 2f,
                        position.y - size.height / 2f,
                        radius,
                    )
                    latest_change(hue, saturation)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    apply(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        apply(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(rainbow_stops, center), radius)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius),
            radius,
        )
        if (hsv.value < 1f) {
            drawCircle(Color.Black.copy(alpha = 1f - hsv.value), radius)
        }
        val (dx, dy) = wheel_offset(hsv.hue, hsv.saturation, radius)
        draw_thumb(Offset(center.x + dx, center.y + dy), rgb_color(hsv_to_rgb(hsv)))
    }
}

private fun DrawScope.draw_thumb(position: Offset, fill: Color) {
    val radius = 12.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.22f), radius + 2.dp.toPx(), position + Offset(0f, 1.dp.toPx()))
    drawCircle(Color.White, radius, position)
    drawCircle(fill, radius - 3.dp.toPx(), position)
    drawCircle(Color.Black.copy(alpha = 0.12f), radius, position, style = Stroke(1.dp.toPx()))
}

@Composable
private fun brightness_slider(
    hsv: hsv_color,
    on_change: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val latest_change by rememberUpdatedState(on_change)
    val full = rgb_color(hsv_to_rgb(hsv.copy(value = 1f)))
    Canvas(
        modifier = modifier
            .height(32.dp)
            .testTag("color_wheel_brightness")
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(hsv.value, 0f..1f)
                setProgress { target ->
                    latest_change(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                val inset = 14.dp.toPx()
                fun apply(position: Offset) {
                    val span = (size.width - inset * 2f).coerceAtLeast(1f)
                    latest_change(((position.x - inset) / span).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    apply(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        apply(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val inset = 14.dp.toPx()
        val track_height = 12.dp.toPx()
        val top = (size.height - track_height) / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, full), startX = inset, endX = size.width - inset),
            topLeft = Offset(inset - track_height / 2f, top),
            size = Size(size.width - inset * 2f + track_height, track_height),
            cornerRadius = CornerRadius(track_height / 2f),
        )
        val x = inset + (size.width - inset * 2f) * hsv.value
        draw_thumb(Offset(x, size.height / 2f), rgb_color(hsv_to_rgb(hsv)))
    }
}
