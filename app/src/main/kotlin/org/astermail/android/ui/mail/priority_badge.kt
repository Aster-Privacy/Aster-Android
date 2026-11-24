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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Flag
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.mail.MailPriority
import org.astermail.android.mail.MailPriorityRules
import org.astermail.android.ui.settings.detail.tone_badge

fun priority_badge_label(priority: MailPriority): Int = when (priority) {
    MailPriority.HIGH -> R.string.mail_high_priority
    MailPriority.LOW -> R.string.mail_low_priority
    MailPriority.NORMAL -> R.string.mail_priority_normal
}

fun priority_option_label(priority: MailPriority): Int = when (priority) {
    MailPriority.HIGH -> R.string.mail_priority_high
    MailPriority.LOW -> R.string.mail_priority_low
    MailPriority.NORMAL -> R.string.mail_priority_normal
}

@Composable
internal fun priority_badge(
    raw_headers: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val priority = remember(raw_headers) { MailPriorityRules.from_headers(raw_headers) }
    if (priority == MailPriority.NORMAL) return
    val colors = AsterMaterial.colors
    tone_badge(
        text = androidx.compose.ui.res.stringResource(priority_badge_label(priority)),
        tone = if (priority == MailPriority.HIGH) colors.danger else colors.text_muted,
        icon = TablerIcons.Flag,
        icon_size = 12.dp,
        modifier = modifier.testTag(if (priority == MailPriority.HIGH) "priority_badge_high" else "priority_badge_low"),
    )
}
