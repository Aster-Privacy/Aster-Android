//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.aster_animate_color
import org.astermail.android.design.components.AsterIconButton

internal const val GUARD_SETUP_STEPS = 5

internal fun guard_setup_step_number(step: guard_setup_step): Int = when (step) {
    guard_setup_step.intro -> 1
    guard_setup_step.choose -> 2
    guard_setup_step.rescue -> 3
    guard_setup_step.confirm_rescue -> 4
    guard_setup_step.password -> 5
    else -> 0
}

internal fun guard_setup_can_close(step: guard_setup_step): Boolean = when (step) {
    guard_setup_step.done,
    guard_setup_step.working,
    guard_setup_step.loading,
    -> false
    else -> true
}

internal fun guard_step_filled(index: Int, step: Int): Boolean = index < step

internal fun guard_track_color(colors: AsterSemanticColors): Color = colors.text_tertiary.copy(alpha = 0.32f)

@Composable
internal fun guard_step_progress_bar(step: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val label = stringResource(R.string.guard_setup_step_of, step, total)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("guard_step_progress")
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.xs),
    ) {
        repeat(total) { index ->
            val fill by aster_animate_color(
                target = if (guard_step_filled(index, step)) colors.accent_blue else guard_track_color(colors),
                label = "guard_step_segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(fill),
            )
        }
    }
}

@Composable
internal fun guard_wizard_bar(on_back: (() -> Unit)?, on_close: (() -> Unit)?, back_closes: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            if (on_back != null) {
                AsterIconButton(
                    icon = TablerIcons.ArrowLeft,
                    content_description = stringResource(if (back_closes) R.string.close else R.string.back),
                    onClick = on_back,
                    auto_mirror = true,
                    modifier = Modifier.testTag("guard_wizard_back"),
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            guard_wordmark(height = 20.dp)
        }
        Box(Modifier.size(48.dp)) {
            if (on_close != null) {
                AsterIconButton(
                    icon = TablerIcons.X,
                    content_description = stringResource(R.string.close),
                    onClick = on_close,
                    modifier = Modifier.testTag("guard_wizard_close"),
                )
            }
        }
    }
}

@Composable
internal fun guard_wizard_title(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        textAlign = align,
        color = AsterMaterial.colors.text_primary,
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp),
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun guard_wizard_body(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        textAlign = align,
        color = AsterMaterial.colors.text_secondary,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
    )
}

@Composable
internal fun guard_wizard_scaffold(
    step: Int,
    title: String,
    body: String?,
    on_back: (() -> Unit)?,
    on_close: (() -> Unit)?,
    back_closes: Boolean = false,
    total: Int = GUARD_SETUP_STEPS,
    centered: Boolean = false,
    bottom: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("guard_wizard"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        guard_wizard_bar(on_back = on_back, on_close = on_close, back_closes = back_closes)
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
        ) {
            if (step > 0) {
                guard_step_progress_bar(
                    step = step,
                    total = total,
                    modifier = Modifier.padding(horizontal = AsterSpacing.lg),
                )
            }
            if (centered) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        guard_wizard_title(title, Modifier.fillMaxWidth(), align = TextAlign.Center)
                        if (body != null) {
                            Spacer(Modifier.height(AsterSpacing.sm))
                            guard_wizard_body(body, Modifier.fillMaxWidth(), align = TextAlign.Center)
                        }
                        Spacer(Modifier.height(AsterSpacing.xxl))
                        content()
                    }
                }
            } else Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AsterSpacing.lg),
            ) {
                Spacer(Modifier.height(AsterSpacing.xxl))
                if (step > 0) {
                    Text(
                        text = stringResource(R.string.guard_setup_step_of, step, total),
                        color = colors.text_tertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Spacer(Modifier.height(AsterSpacing.xs))
                }
                guard_wizard_title(title, Modifier.fillMaxWidth())
                if (body != null) {
                    Spacer(Modifier.height(AsterSpacing.sm))
                    guard_wizard_body(body, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(AsterSpacing.xxl))
                content()
                Spacer(Modifier.height(AsterSpacing.lg))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        start = AsterSpacing.lg,
                        end = AsterSpacing.lg,
                        top = AsterSpacing.sm,
                        bottom = AsterSpacing.lg,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                content = bottom,
            )
        }
    }
}

@Composable
internal fun guard_wizard_result(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
