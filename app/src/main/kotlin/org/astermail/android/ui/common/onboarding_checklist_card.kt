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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Circle
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape

val onboarding_checklist_order = listOf("recovery_method", "import_mail", "install_app", "first_email")

private fun checklist_label(key: String): Int? = when (key) {
    "recovery_method" -> R.string.onboarding_task_recovery
    "import_mail" -> R.string.onboarding_task_import
    "install_app" -> R.string.onboarding_task_install
    "first_email" -> R.string.onboarding_task_first_email
    else -> null
}

@Composable
fun onboarding_checklist_card(
    tasks: Map<String, Boolean>,
    on_task: (String) -> Unit,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val rows = onboarding_checklist_order.mapNotNull { key ->
        val label = checklist_label(key) ?: return@mapNotNull null
        val done = tasks[key] ?: return@mapNotNull null
        Triple(key, label, done)
    }
    if (rows.isEmpty()) return
    val done_count = rows.count { it.third }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(colors.accent_blue.copy(alpha = 0.08f))
            .padding(AsterSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.onboarding_checklist_title, done_count, rows.size),
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = TablerIcons.X,
                contentDescription = stringResource(R.string.dismiss),
                tint = colors.text_muted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = on_dismiss),
            )
        }
        Spacer(Modifier.height(AsterSpacing.xs))
        rows.forEach { (key, label, done) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !done) { on_task(key) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (done) TablerIcons.CircleCheck else TablerIcons.Circle,
                    contentDescription = null,
                    tint = if (done) colors.success else colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = stringResource(label),
                    color = if (done) colors.text_muted else colors.text_secondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
