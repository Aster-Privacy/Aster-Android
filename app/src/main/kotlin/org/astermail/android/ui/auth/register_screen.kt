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

package org.astermail.android.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.auth.AuthUiState
import org.astermail.android.auth.AuthViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterTopBar

@Composable
fun RegisterScreen(
    on_back: () -> Unit,
    on_registered: () -> Unit,
    on_sign_in: () -> Unit,
    on_terms_click: () -> Unit,
    on_privacy_click: () -> Unit,
    view_model: AuthViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state = remember_register_flow_state()
    val auth_state by view_model.ui_state.collectAsStateWithLifecycle()
    val recovery by view_model.recovery_mnemonic.collectAsStateWithLifecycle()

    LaunchedEffect(recovery) {
        if (recovery != null && state.step.value == RegisterStep.generating) {
            state.step.value = RegisterStep.recovery_key
        }
    }

    LaunchedEffect(auth_state) {
        if (auth_state is AuthUiState.Error && state.step.value == RegisterStep.generating) {
            state.step.value = RegisterStep.password
        }
    }

    val is_loading = auth_state is AuthUiState.Loading
    val error_message = (auth_state as? AuthUiState.Error)?.message

    val handle_back: () -> Unit = {
        when (state.step.value) {
            RegisterStep.email -> on_back()
            RegisterStep.password -> {
                view_model.reset_state()
                state.step.value = RegisterStep.email
            }
            RegisterStep.recovery_email -> {
                state.step.value = RegisterStep.recovery_key
            }
            else -> { }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            register_progress_header(
                step = state.step.value,
                on_back = handle_back,
                show_back = state.step.value == RegisterStep.email ||
                    state.step.value == RegisterStep.password ||
                    state.step.value == RegisterStep.recovery_email,
                show_progress = state.step.value != RegisterStep.plan_selection,
            )

            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                when (state.step.value) {
                    RegisterStep.email -> RegisterUsernameStep(
                        state = state,
                        error_message = null,
                        on_next = {
                            view_model.reset_state()
                            state.step.value = RegisterStep.password
                        },
                        on_sign_in = on_sign_in,
                    )
                    RegisterStep.password -> RegisterPasswordStep(
                        state = state,
                        error_message = error_message,
                        is_loading = is_loading,
                        on_next = {
                            val full_email = "${state.username.value.trim()}@${state.email_domain.value}"
                            state.step.value = RegisterStep.generating
                            view_model.submit_register(
                                full_email,
                                state.password.value,
                                state.confirm_password.value,
                                state.captcha_token.value,
                            )
                        },
                        on_sign_in = on_sign_in,
                        on_terms_click = on_terms_click,
                        on_privacy_click = on_privacy_click,
                    )
                    RegisterStep.generating -> RegisterGeneratingStep()
                    RegisterStep.recovery_key -> RegisterRecoveryStep(
                        mnemonic = recovery.orEmpty(),
                        on_continue = {
                            state.step.value = RegisterStep.recovery_email
                        },
                    )
                    RegisterStep.recovery_email -> RegisterRecoveryEmailStep(
                        state = state,
                        on_continue = {
                            view_model.consume_recovery_mnemonic()
                            state.step.value = RegisterStep.plan_selection
                        },
                        on_skip = {
                            view_model.consume_recovery_mnemonic()
                            state.step.value = RegisterStep.plan_selection
                        },
                    )
                    RegisterStep.plan_selection -> RegisterPlanStep(
                        on_continue = on_registered,
                    )
                }
            }
        }
    }
}

@Composable
private fun register_progress_header(
    step: RegisterStep,
    on_back: () -> Unit,
    show_back: Boolean,
    show_progress: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val progress = step_progress(step)
    val animated_progress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 400),
        label = "register_progress",
    )

    if (step == RegisterStep.generating) {
        Spacer(Modifier.height(16.dp))
        return
    }

    Column {
        AsterTopBar(title = "", on_back = if (show_back) on_back else { {} })
        if (!show_progress) return
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xxl, vertical = AsterSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colors.border_secondary, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = animated_progress)
                        .background(colors.accent_blue, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}
