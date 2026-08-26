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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import kotlinx.coroutines.launch
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

@Composable
private fun category_chip(label: String, icon: ImageVector, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (selected) colors.accent_blue else colors.bg_secondary
    val content_color = if (selected) Color.White else colors.text_secondary
    Row(
        modifier = Modifier
            .clickable(onClick = on_click)
            .clip(SquircleShape(999.dp))
            .background(bg, SquircleShape(999.dp))
            .border(
                1.dp,
                if (selected) colors.accent_blue else colors.border_secondary,
                SquircleShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content_color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.xs))
        Text(label, color = content_color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private const val MAX_FEEDBACK_LENGTH = 2000

@Composable
fun FeedbackScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val vm: SettingsViewModel = shared_settings_view_model()
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf("general") }
    var message by rememberSaveable { mutableStateOf("") }
    var is_sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    val feedback_sent_text = stringResource(R.string.feedback_sent)
    val feedback_failed_text = stringResource(R.string.feedback_failed)

    detail_scaffold(title = stringResource(R.string.feedback), on_back = on_back) {
        Text(
            text = stringResource(R.string.feedback_subtitle),
            color = colors.text_secondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = AsterSpacing.md),
        )
        section_label(stringResource(R.string.category))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = AsterSpacing.sm)) {
            category_chip(stringResource(R.string.category_feedback), TablerIcons.Heart, category == "general") { category = "general" }
            Spacer(Modifier.width(AsterSpacing.sm))
            category_chip(stringResource(R.string.category_idea), TablerIcons.Bulb, category == "feature") { category = "feature" }
            Spacer(Modifier.width(AsterSpacing.sm))
            category_chip(stringResource(R.string.category_bug), TablerIcons.Bug, category == "bug") { category = "bug" }
        }
        v_gap(AsterSpacing.md)
        section_label(stringResource(R.string.message))
        val focus_requester = remember { FocusRequester() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(colors.input_bg, SquircleShape(18.dp))
                .border(1.dp, colors.input_border, SquircleShape(18.dp))
                .clickable { focus_requester.requestFocus() }
                .padding(AsterSpacing.lg),
        ) {
            if (message.isEmpty()) {
                Text(
                    text = stringResource(R.string.feedback_placeholder),
                    color = colors.text_muted,
                    fontSize = 15.sp,
                )
            }
            BasicTextField(
                value = message,
                onValueChange = { if (it.length <= MAX_FEEDBACK_LENGTH) message = it },
                textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent_blue),
                modifier = Modifier.fillMaxWidth().focusRequester(focus_requester),
            )
        }
        Text(
            text = "${message.length} / $MAX_FEEDBACK_LENGTH",
            color = colors.text_tertiary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = AsterSpacing.xs),
            textAlign = TextAlign.End,
        )
        v_gap(AsterSpacing.lg)
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.feedback_privacy_note),
                color = colors.text_tertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(AsterSpacing.md),
            )
        }
        v_gap(AsterSpacing.lg)
        AsterButton(
            label = if (sent) stringResource(R.string.sent) else if (is_sending) stringResource(R.string.sending_feedback) else stringResource(R.string.send_feedback),
            onClick = {
                scope.launch {
                    is_sending = true
                    try {
                        vm.send_feedback(category, message)
                        sent = true
                        Toast.makeText(context, feedback_sent_text, Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {
                        Toast.makeText(context, feedback_failed_text, Toast.LENGTH_SHORT).show()
                    }
                    is_sending = false
                }
            },
            enabled = message.isNotBlank() && !is_sending && !sent,
            is_loading = is_sending,
        )
        v_gap(AsterSpacing.xxl)
    }
}
