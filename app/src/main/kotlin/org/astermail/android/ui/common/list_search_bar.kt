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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.ui.mail.search_field_bg_color
import androidx.compose.ui.res.stringResource

@Composable
fun list_search_bar(
    query: String,
    on_query_change: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    test_tag: String = "list_search_bar",
) {
    val colors = AsterMaterial.colors
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(SquircleShape(24.dp))
            .background(search_field_bg_color(colors))
            .padding(horizontal = AsterSpacing.md)
            .testTag(test_tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Search,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.sm))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.text_muted,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                modifier = Modifier.fillMaxWidth().testTag("${test_tag}_input"),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(AsterSpacing.sm))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(SquircleShape(999.dp))
                    .clickable { on_query_change("") }
                    .testTag("${test_tag}_clear"),
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
}
