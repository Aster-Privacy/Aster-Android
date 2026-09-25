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

package org.astermail.android.ui.settings.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.At
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.Key
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Send
import java.util.Locale
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterDialog
import org.astermail.android.design.components.AsterDialogOutlineButton
import org.astermail.android.design.components.AsterDialogPrimaryButton
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.settings.PrimaryAddressStep
import org.astermail.android.settings.PrimaryAddressViewModel
import org.astermail.android.settings.primary_address_domains
import org.astermail.android.settings.primary_local_part_valid
import org.astermail.android.settings.primary_address_reason_account_kind
import org.astermail.android.settings.primary_address_reason_cooldown
import org.astermail.android.settings.primary_address_reason_custom_domain
import org.astermail.android.settings.primary_address_reason_plan

private fun routing_form(address: String): String {
    val at = address.lastIndexOf("@")

    if (at <= 0) return address.lowercase(Locale.ROOT)

    val local = address.substring(0, at).lowercase(Locale.ROOT).replace(".", "")

    return local + "@" + address.substring(at + 1).lowercase(Locale.ROOT)
}

@Composable
internal fun change_primary_address_dialog(
    current_address: String,
    display_name: String,
    alias_addresses: List<String>,
    on_dismiss: () -> Unit,
    on_changed: (String) -> Unit,
    vm: PrimaryAddressViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.busy) {}


    val shown_current = state.current_address.ifEmpty { current_address }
    val eligible_aliases = remember(alias_addresses, shown_current) {
        alias_addresses.filter { address ->
            val at = address.lastIndexOf('@')
            at > 0 &&
                address.substring(at + 1).lowercase(Locale.ROOT) in primary_address_domains &&
                !address.equals(shown_current, ignoreCase = true) &&
                primary_local_part_valid(address.substring(0, at).lowercase(Locale.ROOT))
        }
    }
    val new_address_is_existing_alias =
        remember(alias_addresses, state.new_address, state.consumes_alias) {
            state.new_address.isNotEmpty() &&
                (
                    state.consumes_alias ||
                        alias_addresses.any {
                            routing_form(it) == routing_form(state.new_address)
                        }
                    )
        }
    val next_change_label = remember(state.next_change_available_at) {
        format_settings_date(state.next_change_available_at)
    }
    val next_change_body = if (next_change_label.isEmpty()) {
        stringResource(R.string.address_change_once_body_no_date)
    } else {
        stringResource(R.string.address_change_once_body, next_change_label)
    }

    AsterDialog(
        on_dismiss = { if (!state.busy) on_dismiss() },
        is_busy = state.busy,
        title = when (state.step) {
            PrimaryAddressStep.INTRO -> stringResource(R.string.address_change_title)
            PrimaryAddressStep.PICK -> stringResource(R.string.address_change_pick_title)
            PrimaryAddressStep.REVIEW -> stringResource(R.string.address_change_review_title)
            PrimaryAddressStep.PASSWORD -> stringResource(R.string.address_change_password_title)
            PrimaryAddressStep.CODE -> stringResource(R.string.address_change_code_title)
            PrimaryAddressStep.DONE -> stringResource(
                R.string.address_change_done_title,
                state.final_address,
            )
        },
        body = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                when (state.step) {
                    PrimaryAddressStep.INTRO -> {
                        Text(
                            text = stringResource(R.string.address_change_intro_lead),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                        address_change_point(
                            icon = TablerIcons.Inbox,
                            title = stringResource(
                                R.string.address_change_keep_old_title,
                                shown_current,
                            ),
                            body = stringResource(R.string.address_change_keep_old_body),
                        )
                        address_change_point(
                            icon = TablerIcons.CircleCheck,
                            title = stringResource(R.string.address_change_no_limit_title),
                            body = stringResource(R.string.address_change_no_limit_body),
                        )
                        address_change_point(
                            icon = TablerIcons.Refresh,
                            title = stringResource(R.string.address_change_once_title),
                            body = if (next_change_label.isEmpty()) {
                                stringResource(R.string.address_change_once_body_no_date)
                            } else {
                                stringResource(
                                    R.string.address_change_once_body,
                                    next_change_label,
                                )
                            },
                        )
                        address_change_warning_card(
                            title = stringResource(R.string.address_change_permanent_title),
                            body = stringResource(
                                R.string.address_change_permanent_body,
                                shown_current,
                            ),
                        )
                        address_change_lock_message(
                            reason = state.lock_reason,
                            eligibility_failed = state.eligibility_failed,
                            next_change_available_at = state.next_change_available_at,
                        )?.let { locked ->
                            Text(text = locked, color = colors.warning, fontSize = 13.sp)
                        }
                    }

                    PrimaryAddressStep.PICK -> {
                        if (eligible_aliases.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.address_change_use_alias),
                                color = colors.text_tertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            eligible_aliases.forEach { alias ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(SquircleShape(12.dp))
                                        .background(colors.bg_secondary, SquircleShape(12.dp))
                                        .clickable(role = Role.Button) { vm.use_alias(alias) }
                                        .padding(
                                            horizontal = AsterSpacing.md,
                                            vertical = 10.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = alias,
                                        color = colors.text_primary,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        AsterTextField(
                            value = state.local_part,
                            onValueChange = vm::set_local_part,
                            label = stringResource(R.string.address_change_use_new),
                            keyboard_options = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                            ),
                        )
                        domain_picker(
                            domain = state.domain,
                            on_select = vm::set_domain,
                        )

                        val verdict = when {
                            state.local_part.isEmpty() -> null
                            !state.local_part_valid ->
                                stringResource(R.string.address_change_invalid) to colors.danger
                            state.checking -> stringResource(R.string.address_change_checking) to
                                colors.text_secondary
                            state.availability_check_failed ->
                                stringResource(R.string.address_change_check_failed) to
                                    colors.warning
                            state.same_as_current ->
                                stringResource(R.string.address_change_same_as_current) to
                                    colors.danger
                            state.is_available == false ->
                                stringResource(
                                    R.string.address_change_unavailable,
                                    state.new_address,
                                ) to colors.danger
                            state.is_available == true -> stringResource(
                                R.string.address_change_available,
                                state.new_address,
                            ) to colors.success
                            else -> null
                        }
                        verdict?.let { (label, tint) ->
                            Text(text = label, color = tint, fontSize = 13.sp)
                        }
                        address_change_warning_card(
                            title = stringResource(R.string.address_change_permanent_title),
                            body = stringResource(
                                R.string.address_change_permanent_body,
                                shown_current,
                            ),
                        )
                    }

                    PrimaryAddressStep.REVIEW -> {
                        address_change_compare(
                            label = stringResource(R.string.address_change_from),
                            value = shown_current,
                        )
                        address_change_compare(
                            label = stringResource(R.string.address_change_to),
                            value = state.new_address,
                            emphasized = true,
                        )
                        address_change_point(
                            icon = TablerIcons.Inbox,
                            title = stringResource(
                                R.string.address_change_keep_old_title,
                                shown_current,
                            ),
                            body = stringResource(R.string.address_change_keep_old_body),
                        )
                        address_change_point(
                            icon = TablerIcons.Send,
                            title = stringResource(
                                R.string.address_change_effect_sending,
                                state.new_address,
                            ),
                            body = null,
                        )
                        address_change_point(
                            icon = TablerIcons.Key,
                            title = stringResource(
                                R.string.address_change_effect_key,
                                state.new_address,
                            ),
                            body = null,
                        )
                        address_change_point(
                            icon = TablerIcons.CircleCheck,
                            title = stringResource(R.string.address_change_effect_signed_in),
                            body = null,
                        )
                        if (new_address_is_existing_alias) {
                            address_change_point(
                                icon = TablerIcons.At,
                                title = stringResource(
                                    R.string.address_change_effect_alias_title,
                                    state.new_address,
                                ),
                                body = stringResource(
                                    R.string.address_change_effect_alias_body,
                                ),
                            )
                        }
                        address_change_point(
                            icon = TablerIcons.Clock,
                            title = stringResource(R.string.address_change_once_title),
                            body = next_change_body,
                        )
                        address_change_point(
                            icon = TablerIcons.AlertTriangle,
                            tint = colors.warning,
                            title = stringResource(R.string.address_change_permanent_title),
                            body = stringResource(R.string.address_change_effect_final),
                        )
                        AsterTextField(
                            value = state.confirm_text,
                            onValueChange = vm::set_confirm_text,
                            label = stringResource(
                                R.string.address_change_type_to_confirm,
                                state.new_address,
                            ),
                            keyboard_options = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                            ),
                        )
                    }

                    PrimaryAddressStep.PASSWORD -> {
                        Text(
                            text = stringResource(R.string.address_change_password_body),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                        AsterTextField(
                            value = state.password,
                            onValueChange = vm::set_password,
                            label = stringResource(R.string.password),
                            enabled = !state.busy,
                            keyboard_options = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                            ),
                            visual_transformation = if (state.show_password) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailing_icon = {
                                AsterIconButton(
                                    icon = if (state.show_password) {
                                        TablerIcons.EyeOff
                                    } else {
                                        TablerIcons.Eye
                                    },
                                    content_description = stringResource(
                                        if (state.show_password) {
                                            R.string.hide_password
                                        } else {
                                            R.string.show_password
                                        },
                                    ),
                                    onClick = { vm.toggle_show_password() },
                                    tint = colors.text_muted,
                                )
                            },
                        )
                    }

                    PrimaryAddressStep.CODE -> {
                        Text(
                            text = stringResource(
                                R.string.address_change_code_body,
                                shown_current,
                            ),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                        AsterTextField(
                            value = state.code,
                            onValueChange = vm::set_code,
                            label = stringResource(R.string.address_change_code_label),
                            enabled = !state.busy,
                            keyboard_options = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                            ),
                        )
                        Text(
                            modifier = Modifier
                                .clip(SquircleShape(8.dp))
                                .clickable(enabled = state.can_resend_code, role = Role.Button) { vm.resend_code() }
                                .padding(vertical = 4.dp),
                            text = if (state.resend_seconds > 0) {
                                stringResource(
                                    R.string.address_change_resend_in,
                                    state.resend_seconds,
                                )
                            } else {
                                stringResource(R.string.address_change_resend)
                            },
                            color = if (state.can_resend_code) {
                                colors.accent_blue
                            } else {
                                colors.text_tertiary
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        state.status?.let { status ->
                            Text(text = status, color = colors.text_secondary, fontSize = 13.sp)
                        }
                    }

                    PrimaryAddressStep.DONE -> {
                        Text(
                            text = stringResource(
                                R.string.address_change_done_body,
                                state.retained_address.ifEmpty { shown_current },
                            ),
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = next_change_body,
                            color = colors.text_secondary,
                            fontSize = 13.sp,
                        )
                        state.status?.let { status ->
                            Text(text = status, color = colors.warning, fontSize = 13.sp)
                        }
                    }
                }

                state.error?.let { message ->
                    Text(text = message, color = colors.danger, fontSize = 13.sp)
                }
            }
        },
        footer = {
            when (state.step) {
                PrimaryAddressStep.INTRO -> {
                    AsterDialogOutlineButton(
                        label = stringResource(R.string.cancel),
                        onClick = on_dismiss,
                    )
                    if (state.eligibility_failed) {
                        AsterDialogPrimaryButton(
                            label = stringResource(R.string.retry),
                            enabled = !state.busy,
                            onClick = vm::load_eligibility,
                        )
                    } else {
                        AsterDialogPrimaryButton(
                            label = stringResource(R.string.continue_action),
                            enabled = state.eligible,
                            onClick = vm::go_to_pick,
                        )
                    }
                }

                PrimaryAddressStep.PICK -> {
                    AsterDialogOutlineButton(
                        label = stringResource(R.string.back),
                        onClick = vm::back_to_intro,
                    )
                    AsterDialogPrimaryButton(
                        label = stringResource(R.string.continue_action),
                        enabled = state.can_continue_from_pick,
                        onClick = vm::go_to_review,
                    )
                }

                PrimaryAddressStep.REVIEW -> {
                    AsterDialogOutlineButton(
                        label = stringResource(R.string.back),
                        onClick = vm::back_to_pick,
                    )
                    AsterDialogPrimaryButton(
                        label = stringResource(R.string.continue_action),
                        enabled = state.can_continue_from_review,
                        onClick = vm::go_to_password,
                    )
                }

                PrimaryAddressStep.PASSWORD -> {
                    AsterDialogOutlineButton(
                        label = stringResource(R.string.back),
                        enabled = !state.busy,
                        onClick = vm::back_to_review,
                    )
                    AsterDialogPrimaryButton(
                        label = stringResource(R.string.continue_action),
                        enabled = state.password.isNotBlank() && !state.busy,
                        is_loading = state.busy,
                        onClick = vm::start_change,
                    )
                }

                PrimaryAddressStep.CODE -> {
                    AsterDialogOutlineButton(
                        label = stringResource(R.string.cancel),
                        enabled = !state.busy,
                        onClick = on_dismiss,
                    )
                    AsterDialogPrimaryButton(
                        label = stringResource(R.string.address_change_title),
                        enabled = state.can_submit_code,
                        is_loading = state.busy,
                        onClick = { vm.confirm_change(display_name, on_changed) },
                    )
                }

                PrimaryAddressStep.DONE -> {
                    if (state.key_retry_available) {
                        AsterDialogOutlineButton(
                            label = stringResource(R.string.retry),
                            enabled = !state.busy,
                            onClick = { vm.retry_republish(display_name) },
                        )
                    }
                    AsterDialogPrimaryButton(
                        label = stringResource(R.string.done),
                        enabled = !state.busy,
                        onClick = on_dismiss,
                    )
                }
            }
        },
    )
}

@Composable
private fun domain_picker(domain: String, on_select: (String) -> Unit) {
    val colors = AsterMaterial.colors
    var open by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(18.dp))
                .background(colors.input_bg, SquircleShape(18.dp))
                .border(1.5.dp, colors.input_border, SquircleShape(18.dp))
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = AsterSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "@$domain",
                color = colors.text_primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = TablerIcons.ChevronDown,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(20.dp),
            )
        }
        aster_menu(expanded = open, on_dismiss = { open = false }) {
            primary_address_domains.forEach { option ->
                aster_menu_item(
                    label = "@$option",
                    selected = option == domain,
                    on_click = {
                        on_select(option)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun address_change_warning_card(title: String, body: String) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(colors.warning, SquircleShape(14.dp))
            .padding(horizontal = AsterSpacing.md, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                color = Color.Black.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun address_change_point(
    icon: ImageVector,
    title: String,
    body: String?,
    tint: Color? = null,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: colors.text_secondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (body != null) {
                Text(text = body, color = colors.text_secondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun address_change_compare(
    label: String,
    value: String,
    emphasized: Boolean = false,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .background(colors.bg_secondary, SquircleShape(12.dp))
            .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
    ) {
        Text(
            text = label,
            color = colors.text_tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = if (emphasized) colors.accent_blue else colors.text_primary,
            fontSize = 14.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun address_change_lock_message(
    reason: String?,
    eligibility_failed: Boolean,
    next_change_available_at: String?,
): String? {
    val next_change = remember(next_change_available_at) {
        format_settings_date(next_change_available_at)
    }
    if (eligibility_failed) {
        return stringResource(R.string.address_change_eligibility_failed)
    }
    if (reason == null) return null
    return when (reason) {
        primary_address_reason_account_kind ->
            stringResource(R.string.address_change_locked_account_kind)
        primary_address_reason_plan ->
            stringResource(R.string.address_change_locked_plan)
        primary_address_reason_custom_domain ->
            stringResource(R.string.address_change_locked_custom_domain)
        primary_address_reason_cooldown -> if (next_change.isEmpty()) {
            stringResource(R.string.address_change_locked_cooldown_unknown)
        } else {
            stringResource(R.string.address_change_locked_cooldown, next_change)
        }
        else -> stringResource(R.string.address_change_not_available)
    }
}

