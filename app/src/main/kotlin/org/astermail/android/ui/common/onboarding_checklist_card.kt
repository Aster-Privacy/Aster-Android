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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.auto_mirrored
import org.astermail.android.design.island_surface_color

const val tutorial_list_panel_tag = "tutorial_list_panel"
const val tutorial_list_toggle_tag = "tutorial_list_toggle"
const val tutorial_list_remaining_tag = "tutorial_list_remaining"
const val tutorial_list_progress_tag = "tutorial_list_progress"

private const val tutorial_list_motion_ms = 160

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
    on_see_plans: (() -> Unit)? = null,
    on_get_domain: (() -> Unit)? = null,
    on_own_domain: (() -> Unit)? = null,
) {
    val rows = tutorial_list_rows(tasks).filter { checklist_label(it.key) != null }
    if (rows.isEmpty()) return
    val progress = tutorial_list_progress(done = rows.count { it.done }, total = rows.size)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        tutorial_list_toggle(
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
                on_see_plans = on_see_plans,
                on_get_domain = on_get_domain,
                on_own_domain = on_own_domain,
                modifier = Modifier.padding(top = AsterSpacing.sm),
            )
        }
    }
}

@Composable
private fun tutorial_list_toggle(
    is_open: Boolean,
    progress: tutorial_list_progress,
    on_toggle: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val remaining = progress.remaining
    val label = stringResource(
        if (is_open) R.string.onboarding_checklist_close else R.string.onboarding_checklist_open,
    )
    val progress_label = stringResource(
        R.string.onboarding_checklist_progress,
        progress.done,
        progress.total,
    )
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(island_surface_color(colors))
                .border(1.dp, colors.border_primary, CircleShape)
                .clickable(role = Role.Button, onClick = on_toggle)
                .semantics {
                    contentDescription = label
                    stateDescription = progress_label
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
                .testTag(tutorial_list_toggle_tag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (is_open) TablerIcons.X else TablerIcons.Book,
                contentDescription = null,
                tint = colors.text_secondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (!is_open && remaining > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 16.dp)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(colors.accent_blue)
                    .padding(horizontal = 4.dp)
                    .testTag(tutorial_list_remaining_tag)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = remaining.toString(),
                    color = colors.on_accent,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun tutorial_list_panel(
    rows: List<tutorial_list_row>,
    progress: tutorial_list_progress,
    on_task: (String) -> Unit,
    on_dismiss: () -> Unit,
    on_see_plans: (() -> Unit)?,
    on_get_domain: (() -> Unit)?,
    on_own_domain: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val completed_label = stringResource(R.string.completed)
    val animated_fraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(300),
        label = "tutorial_list_progress",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AsterShapes.island)
            .background(island_surface_color(colors))
            .border(1.dp, colors.border_primary, AsterShapes.island)
            .padding(AsterSpacing.sm)
            .testTag(tutorial_list_panel_tag),
    ) {
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.heightIn(min = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (progress.all_done) {
                            R.string.onboarding_checklist_complete
                        } else {
                            R.string.onboarding_checklist_title
                        },
                    ),
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Spacer(Modifier.width(12.dp))
                if (progress.all_done && on_see_plans != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button, onClick = on_see_plans)
                            .heightIn(min = 28.dp)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.plan_prompt_action),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            imageVector = TablerIcons.ChevronRight.auto_mirrored(),
                            contentDescription = null,
                            tint = colors.accent_blue,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(
                            R.string.onboarding_checklist_progress,
                            progress.done,
                            progress.total,
                        ),
                        color = colors.text_muted,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
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

        if (on_get_domain != null || on_own_domain != null) {
            tutorial_list_divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_checklist_domain_heading),
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_checklist_domain_badge),
                    color = colors.text_muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.bg_hover)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (on_get_domain != null) {
                tutorial_list_row_item(
                    label = stringResource(R.string.onboarding_checklist_domain_get),
                    on_click = on_get_domain,
                )
            }
            if (on_own_domain != null) {
                tutorial_list_row_item(
                    label = stringResource(R.string.onboarding_checklist_domain_existing),
                    on_click = on_own_domain,
                )
            }
        }

        tutorial_list_divider()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = on_dismiss)
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.onboarding_checklist_hide_forever),
                color = colors.text_muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun tutorial_list_divider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(AsterMaterial.colors.border_primary),
    )
}

@Composable
private fun tutorial_list_row_item(
    label: String,
    on_click: () -> Unit,
    done: Boolean = false,
    done_label: String = "",
    show_check: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = on_click)
            .semantics { if (done) stateDescription = done_label }
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (show_check) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .then(
                        if (done) {
                            Modifier.background(colors.accent_blue)
                        } else {
                            Modifier.border(2.dp, colors.text_muted, CircleShape)
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
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            color = if (done) colors.text_muted else colors.text_primary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = TablerIcons.ChevronRight.auto_mirrored(),
            contentDescription = null,
            tint = colors.text_muted.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
    }
}
