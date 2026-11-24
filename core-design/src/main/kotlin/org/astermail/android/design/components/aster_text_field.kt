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

package org.astermail.android.design.components


import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.design.AsterExpandVisibility
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.aster_animate_color
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.field_surface_color

@Composable
fun AsterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper_text: String? = null,
    error_text: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboard_options: KeyboardOptions = KeyboardOptions.Default,
    keyboard_actions: KeyboardActions = KeyboardActions.Default,
    visual_transformation: VisualTransformation = VisualTransformation.None,
    leading_icon: (@Composable () -> Unit)? = null,
    trailing_icon: (@Composable () -> Unit)? = null,
    content_type: ContentType? = null,
    min_height: Dp = 52.dp,
    min_lines: Int = 1,
    max_lines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    text_style: TextStyle? = null,
    rest_border: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val interaction_source = remember { MutableInteractionSource() }
    val is_focused by interaction_source.collectIsFocusedAsState()
    val has_error = error_text != null
    var field_state by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    val field_value = if (field_state.text == value) {
        field_state
    } else {
        TextFieldValue(
            text = value,
            selection = TextRange(
                adjusted_caret(field_state.text, value, field_state.selection.end),
            ),
        )
    }

    SideEffect {
        if (field_value != field_state) field_state = field_value
    }

    val ring_base = if (has_error) colors.danger else colors.accent_blue
    val ring_active = has_error || is_focused
    val ring_color by aster_animate_color(
        when {
            ring_active -> ring_base
            rest_border -> colors.border_primary
            else -> ring_base.copy(alpha = 0f)
        },
        label = "aster_text_field_ring",
    )
    val ring_width = if (rest_border && !ring_active) 1.dp else 2.dp
    val last_below = remember { text_field_below_holder() }
    if (error_text != null) last_below.error = error_text
    if (helper_text != null) last_below.helper = helper_text

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text_secondary,
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = min_height)
                .background(field_surface_color(colors), AsterShapes.control)
                .border(ring_width, ring_color, AsterShapes.control)
                .padding(horizontal = AsterSpacing.md, vertical = if (singleLine) 0.dp else 14.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (leading_icon != null) {
                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        leading_icon()
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = colors.text_muted,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = field_value,
                        onValueChange = { updated ->
                            field_state = updated
                            if (updated.text != value) onValueChange(updated.text)
                        },
                        enabled = enabled,
                        singleLine = singleLine,
                        minLines = min_lines,
                        maxLines = max_lines,
                        textStyle = LocalTextStyle.current.copy(
                            color = colors.text_primary,
                            fontSize = 16.sp,
                        ).merge(text_style),
                        cursorBrush = SolidColor(colors.accent_blue),
                        interactionSource = interaction_source,
                        keyboardOptions = keyboard_options,
                        keyboardActions = keyboard_actions,
                        visualTransformation = visual_transformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (content_type != null) {
                                    Modifier.semantics { contentType = content_type }
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
                if (trailing_icon != null) {
                    Box(modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp), contentAlignment = Alignment.Center) {
                        trailing_icon()
                    }
                }
            }
        }
        AsterExpandVisibility(visible = !has_error && helper_text != null) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = helper_text ?: last_below.helper.orEmpty(),
                    fontSize = 12.sp,
                    color = colors.text_muted,
                )
            }
        }
        AsterExpandVisibility(visible = has_error) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error_text ?: last_below.error.orEmpty(),
                    fontSize = 12.sp,
                    color = colors.danger,
                )
            }
        }
    }
}

private class text_field_below_holder {
    var error: String? = null
    var helper: String? = null
}
