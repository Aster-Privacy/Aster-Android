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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

enum class RegisterStep {
    email,
    password,
    generating,
    recovery_key,
    recovery_email,
    plan_selection,
}

class RegisterFlowState(
    val step: MutableState<RegisterStep>,
    val username: MutableState<String>,
    val display_name: MutableState<String>,
    val email_domain: MutableState<String>,
    val password: MutableState<String>,
    val confirm_password: MutableState<String>,
    val remember_me: MutableState<Boolean>,
    val recovery_email: MutableState<String>,
    val recovery_email_saved: MutableState<Boolean>,
    val captcha_token: MutableState<String?>,
)

@Composable
fun remember_register_flow_state(): RegisterFlowState {
    val step = rememberSaveable { mutableStateOf(RegisterStep.email) }
    val username = rememberSaveable { mutableStateOf("") }
    val display_name = rememberSaveable { mutableStateOf("") }
    val email_domain = rememberSaveable { mutableStateOf("astermail.org") }
    val password = remember { mutableStateOf("") }
    val confirm_password = remember { mutableStateOf("") }
    val remember_me = rememberSaveable { mutableStateOf(true) }
    val recovery_email = rememberSaveable { mutableStateOf("") }
    val recovery_email_saved = rememberSaveable { mutableStateOf(false) }
    val captcha_token = remember { mutableStateOf<String?>(null) }
    return RegisterFlowState(
        step,
        username,
        display_name,
        email_domain,
        password,
        confirm_password,
        remember_me,
        recovery_email,
        recovery_email_saved,
        captcha_token,
    )
}

fun step_progress(step: RegisterStep): Float {
    val order = listOf(
        RegisterStep.email,
        RegisterStep.password,
        RegisterStep.generating,
        RegisterStep.recovery_key,
        RegisterStep.recovery_email,
        RegisterStep.plan_selection,
    )
    val idx = order.indexOf(step)
    return (idx + 1).toFloat() / order.size.toFloat()
}
