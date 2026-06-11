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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

@Composable
private fun category_chip(label: String, icon: ImageVector, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .clickable(onClick = on_click)
            .background(
                if (selected) colors.accent_blue.copy(alpha = 0.15f) else colors.bg_secondary,
                SquircleShape(18.dp),
            )
            .border(
                1.dp,
                if (selected) colors.accent_blue else colors.border_secondary,
                SquircleShape(18.dp),
            )
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent_blue else colors.text_secondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(AsterSpacing.xs))
        Text(label, color = if (selected) colors.accent_blue else colors.text_primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FeedbackScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val vm: SettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("feedback") }
    var message by remember { mutableStateOf("") }
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
            category_chip(stringResource(R.string.category_feedback), Icons.Outlined.Favorite, category == "feedback") { category = "feedback" }
            Spacer(Modifier.width(AsterSpacing.sm))
            category_chip(stringResource(R.string.category_idea), Icons.Outlined.Lightbulb, category == "idea") { category = "idea" }
            Spacer(Modifier.width(AsterSpacing.sm))
            category_chip(stringResource(R.string.category_bug), Icons.Outlined.BugReport, category == "bug") { category = "bug" }
        }
        v_gap(AsterSpacing.md)
        section_label(stringResource(R.string.message))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(colors.input_bg, SquircleShape(18.dp))
                .border(1.dp, colors.input_border, SquircleShape(18.dp))
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
                onValueChange = { message = it },
                textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent_blue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
