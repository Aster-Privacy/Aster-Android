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

package org.astermail.android.ui.contacts

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Menu2
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.aster_spinner
import org.astermail.android.ui.mail.search_field_bg_color

internal val contacts_top_bar_height = 52.dp

private const val leading_exit_search = 0
private const val leading_drawer = 1
private const val leading_back = 2

@Composable
internal fun contacts_search_top_bar(
    query: String,
    on_query_change: (String) -> Unit,
    placeholder: String,
    is_syncing: Boolean,
    on_open_drawer: (() -> Unit)?,
    on_back: () -> Unit,
    focus_requester: FocusRequester,
    is_focused: Boolean,
    on_focus_change: (Boolean) -> Unit,
    on_exit_search: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val reduce_motion = aster_reduce_motion()
    val search_active = is_focused || query.isNotEmpty()
    val pill_interaction = remember { MutableInteractionSource() }
    val leading_state = when {
        search_active -> leading_exit_search
        on_open_drawer != null -> leading_drawer
        else -> leading_back
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.xs)
            .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs)
            .height(contacts_top_bar_height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(
            targetState = leading_state,
            animationSpec = if (reduce_motion) snap() else tween(durationMillis = 150),
            label = "contacts_leading_icon",
        ) { leading ->
            when (leading) {
                leading_exit_search -> AsterIconButton(
                    icon = TablerIcons.ArrowLeft,
                    auto_mirror = true,
                    content_description = stringResource(R.string.contacts_close_search),
                    onClick = on_exit_search,
                    modifier = Modifier.testTag("contacts_search_exit"),
                )
                leading_drawer -> AsterIconButton(
                    icon = TablerIcons.Menu2,
                    content_description = stringResource(R.string.open_drawer),
                    onClick = { on_open_drawer?.invoke() },
                    modifier = Modifier.testTag("open_drawer"),
                )
                else -> AsterIconButton(
                    icon = TablerIcons.ArrowLeft,
                    auto_mirror = true,
                    content_description = stringResource(R.string.back),
                    onClick = on_back,
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(contacts_top_bar_height)
                .padding(horizontal = AsterSpacing.sm)
                .acrylic(colors, SquircleShape(26.dp), search_field_bg_color(colors))
                .clickable(
                    interactionSource = pill_interaction,
                    indication = null,
                ) { focus_requester.requestFocus() }
                .padding(start = AsterSpacing.lg, end = AsterSpacing.sm)
                .testTag("contact_search_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = if (search_active) Alignment.CenterStart else Alignment.Center,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.text_secondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (search_active) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = on_query_change,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    textStyle = TextStyle(color = colors.text_primary, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent_blue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus_requester)
                        .onFocusChanged { on_focus_change(it.isFocused) }
                        .testTag("contact_search_bar_input"),
                )
            }
            if (is_syncing) {
                Spacer(Modifier.width(AsterSpacing.sm))
                aster_spinner(
                    color = colors.accent_blue,
                    stroke_width = 2.dp,
                    stroke_cap = StrokeCap.Round,
                    modifier = Modifier.size(18.dp).testTag("contacts_sync_spinner"),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(AsterSpacing.xs))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(SquircleShape(999.dp))
                        .clickable { on_query_change("") }
                        .testTag("contact_search_bar_clear"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.X,
                        contentDescription = stringResource(R.string.clear),
                        tint = colors.text_secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        trailing()
    }
}
