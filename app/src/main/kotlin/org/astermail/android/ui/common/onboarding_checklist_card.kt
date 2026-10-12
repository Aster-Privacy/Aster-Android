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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Book
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.auto_mirrored
import org.astermail.android.design.island_surface_color

const val tutorial_list_panel_tag = "tutorial_list_panel"
const val tutorial_list_toggle_tag = "tutorial_list_toggle"
const val tutorial_list_progress_tag = "tutorial_list_progress"

private const val tutorial_list_motion_ms = 160

private val tutorial_list_item_shape = RoundedCornerShape(8.dp)
private val tutorial_list_check_shape = RoundedCornerShape(6.dp)

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
    is_open: Boolean = true,
    on_toggle: () -> Unit = {},
    on_domain: (() -> Unit)? = null,
) {
    val rows = tutorial_list_rows(tasks).filter { checklist_label(it.key) != null }
    if (rows.isEmpty()) return
    val progress = tutorial_list_progress(done = rows.count { it.done }, total = rows.size)
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AsterShapes.island)
            .background(island_surface_color(colors))
            .padding(6.dp),
    ) {
        tutorial_list_header(
            is_open = is_open,
            progress = progress,
            on_toggle = on_toggle,
        )
        AnimatedVisibility(
            visible = is_open,
            enter = expandVertically(tween(tutorial_list_motion_ms)) + fadeIn(tween(tutorial_list_motion_ms)),
            exit = shrinkVertically(tween(tutorial_list_motion_ms)) + fadeOut(tween(tutorial_list_motion_ms)),
        ) {
            tutorial_list_panel(
                rows = rows,
                progress = progress,
                on_task = on_task,
                on_dismiss = on_dismiss,
                on_domain = on_domain,
            )
        }
    }
}

@Composable
private fun tutorial_list_header(
    is_open: Boolean,
    progress: tutorial_list_progress,
    on_toggle: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val toggle_label = stringResource(
        if (is_open) R.string.onboarding_checklist_close else R.string.onboarding_checklist_open,
    )
    val progress_label = stringResource(
        R.string.onboarding_checklist_progress,
        progress.done,
        progress.total,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tutorial_list_item_shape)
            .clickable(role = Role.Button, onClick = on_toggle)
            .semantics {
                stateDescription = progress_label
                onClick(label = toggle_label) {
                    on_toggle()
                    true
                }
                if (is_open) {
                    collapse {
                        on_toggle()
                        true
                    }
                } else {
                    expand {
                        on_toggle()
                        true
                    }
                }
            }
            .heightIn(min = 40.dp)
            .padding(start = 10.dp, end = 8.dp)
            .testTag(tutorial_list_toggle_tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.onboarding_checklist_title),
            color = colors.text_primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = progress_label,
            color = colors.text_muted,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = if (is_open) TablerIcons.X else TablerIcons.Book,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun tutorial_list_panel(
    rows: List<tutorial_list_row>,
    progress: tutorial_list_progress,
    on_task: (String) -> Unit,
    on_dismiss: () -> Unit,
    on_domain: (() -> Unit)?,
) {
    val colors = AsterMaterial.colors
    val completed_label = stringResource(R.string.completed)
    val animated_fraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(300),
        label = "tutorial_list_progress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tutorial_list_panel_tag),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.bg_hover),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated_fraction.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(colors.accent_blue)
                    .testTag(tutorial_list_progress_tag),
            )
        }

        rows.forEach { row ->
            val label = checklist_label(row.key) ?: return@forEach
            tutorial_list_row_item(
                label = stringResource(label),
                on_click = { on_task(row.key) },
                done = row.done,
                done_label = completed_label,
                show_check = true,
            )
        }

        if (on_domain != null) {
            tutorial_list_row_item(
                label = stringResource(R.string.onboarding_checklist_domain_heading),
                on_click = on_domain,
                badge = stringResource(R.string.onboarding_checklist_domain_badge),
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border_primary),
        )
        Box(
            modifier = Modifier
                .clip(tutorial_list_item_shape)
                .clickable(role = Role.Button, onClick = on_dismiss)
                .heightIn(min = 36.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.onboarding_checklist_hide_forever),
                color = colors.text_muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun tutorial_list_row_item(
    label: String,
    on_click: () -> Unit,
    done: Boolean = false,
    done_label: String = "",
    show_check: Boolean = false,
    badge: String? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tutorial_list_item_shape)
            .clickable(role = Role.Button, onClick = on_click)
            .semantics { if (done) stateDescription = done_label }
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (show_check) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(tutorial_list_check_shape)
                    .then(
                        if (done) {
                            Modifier.background(colors.accent_blue)
                        } else {
                            Modifier.border(
                                1.5.dp,
                                colors.text_primary.copy(alpha = 0.4f),
                                tutorial_list_check_shape,
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = colors.on_accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            color = if (done) colors.text_muted else colors.text_primary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = badge,
                color = colors.text_muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.bg_hover)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = TablerIcons.ChevronRight.auto_mirrored(),
            contentDescription = null,
            tint = colors.text_muted.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
    }
}
