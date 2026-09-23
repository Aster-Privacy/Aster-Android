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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

enum class RegisterStep {
    email,
    password,
    generating,
    recovery_key,
    recovery_email,
    notifications,
    addresses,
    custom_domain,
    import_mail,
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
    val saved_recovery_email: MutableState<String>,
    val captcha_token: MutableState<String?>,
    val address_slots: List<register_address_slot>,
)

class register_address_slot(
    value: String = "",
    domain: String? = null,
    added: Boolean = false,
) {
    var value by mutableStateOf(value)
    var domain by mutableStateOf(domain)
    var error by mutableStateOf<String?>(null)
    var added by mutableStateOf(added)
}

private const val register_address_slot_count = 3

private val register_address_slots_saver = listSaver<List<register_address_slot>, Any?>(
    save = { slots -> slots.flatMap { listOf(it.value, it.domain, it.added) } },
    restore = { saved ->
        saved.chunked(3).map { (value, domain, added) ->
            register_address_slot(value as String, domain as String?, added as Boolean)
        }
    },
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
    val saved_recovery_email = rememberSaveable { mutableStateOf("") }
    val captcha_token = remember { mutableStateOf<String?>(null) }
    val address_slots = rememberSaveable(saver = register_address_slots_saver) {
        List(register_address_slot_count) { register_address_slot() }
    }
    return RegisterFlowState(
        step,
        username,
        display_name,
        email_domain,
        password,
        confirm_password,
        remember_me,
        recovery_email,
        saved_recovery_email,
        captcha_token,
        address_slots,
    )
}

fun previous_register_step(step: RegisterStep): RegisterStep? = when (step) {
    RegisterStep.password -> RegisterStep.email
    RegisterStep.notifications -> RegisterStep.recovery_email
    RegisterStep.addresses -> RegisterStep.notifications
    RegisterStep.custom_domain -> RegisterStep.addresses
    RegisterStep.import_mail -> RegisterStep.custom_domain
    RegisterStep.email,
    RegisterStep.generating,
    RegisterStep.recovery_key,
    RegisterStep.recovery_email,
    -> null
}

fun step_progress(step: RegisterStep): Float {
    val order = RegisterStep.values()
    val idx = order.indexOf(step)
    return (idx + 1).toFloat() / order.size.toFloat()
}
