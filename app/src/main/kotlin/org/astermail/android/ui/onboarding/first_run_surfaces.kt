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

package org.astermail.android.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock
import compose.icons.tablericons.MailOpened
import compose.icons.tablericons.Star
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterSecondaryButton

const val first_run_recovery_delay_ms = 24L * 60L * 60L * 1000L
const val first_run_plan_delay_ms = 72L * 60L * 60L * 1000L
const val first_run_recovery_snooze_ms = 3L * 24L * 60L * 60L * 1000L

@Composable
fun FirstRunSetupSheet(
    email: String,
    on_import: () -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors

    BackHandler { on_dismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .padding(horizontal = AsterSpacing.xl),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = AsterSpacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = TablerIcons.MailOpened,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(40.dp),
            )

            Spacer(Modifier.height(AsterSpacing.xl))

            Text(
                text = stringResource(R.string.first_run_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text_primary,
                textAlign = TextAlign.Center,
            )

            if (email.isNotBlank()) {
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.accent_blue,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(AsterSpacing.md))

            Text(
                text = stringResource(R.string.first_run_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text_secondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AsterSpacing.xxl))

            AsterButton(
                label = stringResource(R.string.first_run_import),
                onClick = on_import,
            )

            Spacer(Modifier.height(AsterSpacing.md))

            AsterSecondaryButton(
                label = stringResource(R.string.first_run_skip),
                onClick = on_dismiss,
            )

            Spacer(Modifier.height(AsterSpacing.xl))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TablerIcons.Lock,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.first_run_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text_muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun FirstRunPromptCard(
    visible: Boolean,
    title: String,
    body: String,
    action_label: String,
    dismiss_label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    on_action: () -> Unit,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accent_blue,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text_primary,
                    )
                }

                Spacer(Modifier.height(AsterSpacing.sm))

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text_secondary,
                )

                Spacer(Modifier.height(AsterSpacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = on_dismiss) {
                        Text(
                            text = dismiss_label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.text_secondary,
                        )
                    }
                    Spacer(Modifier.width(AsterSpacing.xs))
                    TextButton(onClick = on_action) {
                        Text(
                            text = action_label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accent_blue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryReminderCard(
    visible: Boolean,
    on_add_recovery: () -> Unit,
    on_later: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FirstRunPromptCard(
        visible = visible,
        title = stringResource(R.string.recovery_reminder_title),
        body = stringResource(R.string.recovery_reminder_body),
        action_label = stringResource(R.string.recovery_reminder_action),
        dismiss_label = stringResource(R.string.recovery_reminder_later),
        icon = TablerIcons.Lock,
        on_action = on_add_recovery,
        on_dismiss = on_later,
        modifier = modifier,
    )
}

@Composable
fun PlanPromptCard(
    visible: Boolean,
    on_see_plans: () -> Unit,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FirstRunPromptCard(
        visible = visible,
        title = stringResource(R.string.plan_prompt_title),
        body = stringResource(R.string.plan_prompt_body),
        action_label = stringResource(R.string.plan_prompt_action),
        dismiss_label = stringResource(R.string.plan_prompt_dismiss),
        icon = TablerIcons.Star,
        on_action = on_see_plans,
        on_dismiss = on_dismiss,
        modifier = modifier,
    )
}
