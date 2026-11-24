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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.components.aster_popup_window_effect

fun sheet_container_color(colors: AsterSemanticColors): Color =
    if (colors.is_glass) colors.solid_bg else colors.bg_card

val aster_sheet_shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun aster_modal_sheet(
    on_dismiss_request: () -> Unit,
    sheet_state: SheetState,
    container_color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = aster_sheet_shape,
    scrim_color: Color = BottomSheetDefaults.ScrimColor,
    drag_handle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val content_color = AsterMaterial.colors.text_primary
    ModalBottomSheet(
        onDismissRequest = on_dismiss_request,
        modifier = modifier,
        sheetState = sheet_state,
        shape = shape,
        containerColor = container_color,
        contentColor = content_color,
        tonalElevation = 0.dp,
        scrimColor = scrim_color,
        dragHandle = drag_handle,
    ) {
        aster_popup_window_effect(light_navigation_bars = container_color.luminance() > 0.5f)
        sheet_toast_host()
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun remember_sheet_dismiss(sheet_state: SheetState, on_hidden: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(on_hidden)
    return remember(sheet_state) {
        {
            scope.launch { sheet_state.hide() }.invokeOnCompletion {
                if (!sheet_state.isVisible) latest.value()
            }
        }
    }
}
