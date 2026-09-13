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

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsRightLeft
import compose.icons.tablericons.At
import compose.icons.tablericons.CircleCheck

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

private val address_domains = listOf("astermail.org", "aster.cx")

private class address_slot(default_domain: String) {
    var value by mutableStateOf("")
    var domain by mutableStateOf(default_domain)
    var error by mutableStateOf<String?>(null)
    var added by mutableStateOf(false)
}

private fun starts_and_ends_alphanumeric(value: String): Boolean {
    if (value.isEmpty()) return false
    return value.first().isLetterOrDigit() && value.last().isLetterOrDigit()
}

@Composable
fun RegisterAddressesStep(
    state: RegisterFlowState,
    on_done: () -> Unit,
    view_model: SettingsViewModel = shared_settings_view_model(),
) {
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()
    val default_domain = state.email_domain.value.takeIf { it in address_domains } ?: address_domains.first()
    val slots = remember { List(3) { address_slot(default_domain) } }
    var is_adding by remember { mutableStateOf(false) }
    val begin_end_error = stringResource(R.string.address_must_begin_end_alphanumeric)
    val generic_error = stringResource(R.string.address_create_failed)

    val pending_count = slots.count { !it.added && it.value.isNotBlank() }
    val added_count = slots.count { it.added }

    val add_pending: () -> Unit = {
        if (!is_adding) {
            var valid = true
            slots.forEach { slot ->
                if (!slot.added && slot.value.isNotBlank() && !starts_and_ends_alphanumeric(slot.value)) {
                    slot.error = begin_end_error
                    valid = false
                }
            }
            if (valid) {
                is_adding = true
                scope.launch {
                    slots.forEach { slot ->
                        if (!slot.added && slot.value.isNotBlank()) {
                            val ok = view_model.create_alias_now(slot.value.trim(), slot.domain)
                            if (ok) {
                                slot.added = true
                                slot.error = null
                            } else {
                                slot.error = view_model.state.value.action_result ?: generic_error
                                view_model.clear_action_result()
                            }
                        }
                    }
                    is_adding = false
                }
            }
        }
    }

    auth_centered_column {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(40.dp),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.addresses_step_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.addresses_step_desc),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        slots.forEachIndexed { index, slot ->
            address_slot_field(
                slot = slot,
                index = index,
                enabled = !is_adding,
                is_last = index == slots.lastIndex,
                on_submit = { if (pending_count > 0) add_pending() },
            )
            Spacer(Modifier.height(AsterSpacing.md))
        }

        Spacer(Modifier.height(AsterSpacing.sm))

        AsterButton(
            label = stringResource(R.string.alias_action_add),
            onClick = add_pending,
            enabled = pending_count > 0 && !is_adding,
            is_loading = is_adding,
        )

        Spacer(Modifier.height(AsterSpacing.sm))

        AsterGhostButton(
            label = if (added_count > 0) stringResource(R.string.continue_action) else stringResource(R.string.skip_for_now),
            onClick = on_done,
            modifier = Modifier.fillMaxWidth(),
            enabled = !is_adding,
        )
    }
}

@Composable
private fun address_slot_field(
    slot: address_slot,
    index: Int,
    enabled: Boolean,
    is_last: Boolean,
    on_submit: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val field_enabled = enabled && !slot.added
    Column(modifier = Modifier.fillMaxWidth()) {
        AsterTextField(
            value = slot.value,
            onValueChange = { input ->
                val filtered = input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '.' }
                slot.value = filtered.replace(Regex("\\.{2,}"), ".")
                slot.error = null
            },
            placeholder = stringResource(R.string.address_n, index + 1),
            enabled = field_enabled,
            error_text = slot.error,
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (is_last) ImeAction.Done else ImeAction.Next,
            ),
            keyboard_actions = KeyboardActions(onDone = { on_submit() }),
            leading_icon = {
                Icon(TablerIcons.At, null, tint = colors.text_muted)
            },
            trailing_icon = {
                if (slot.added) {
                    Icon(
                        imageVector = TablerIcons.CircleCheck,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    address_domain_switch(
                        selected = slot.domain,
                        enabled = field_enabled,
                        on_switch = {
                            slot.domain = address_domains[(address_domains.indexOf(slot.domain) + 1) % address_domains.size]
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun address_domain_switch(
    selected: String,
    enabled: Boolean,
    on_switch: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = on_switch)
            .padding(horizontal = AsterSpacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "@$selected",
            color = colors.accent_blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = TablerIcons.ArrowsRightLeft,
            contentDescription = stringResource(R.string.switch_domain),
            tint = colors.accent_blue,
            modifier = Modifier.size(14.dp),
        )
    }
}
