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

package org.astermail.android.ui.security

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.security.AppLockStore
import kotlin.math.roundToInt

private enum class SetupStep {
    choose_mode, choose_digits, set_pin, confirm_pin, set_text, confirm_text,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSetupSheet(
    store: AppLockStore,
    on_dismiss: () -> Unit,
    on_success: () -> Unit,
) {
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(SetupStep.choose_mode) }
    var chosen_mode by remember { mutableStateOf("numeric") }
    var chosen_digits by remember { mutableIntStateOf(4) }
    var first_pin by remember { mutableStateOf("") }
    var confirm_input by remember { mutableStateOf("") }
    var text_input by remember { mutableStateOf("") }
    var first_text by remember { mutableStateOf("") }
    var shake_trigger by remember { mutableIntStateOf(0) }
    var error_msg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var show_passphrase by remember { mutableStateOf(false) }

    val mismatch_str = stringResource(R.string.app_lock_pin_mismatch)
    val short_str = stringResource(R.string.app_lock_passphrase_too_short)

    fun go_back() {
        when (step) {
            SetupStep.choose_digits -> { step = SetupStep.choose_mode }
            SetupStep.set_pin -> { step = SetupStep.choose_digits; first_pin = "" }
            SetupStep.confirm_pin -> { step = SetupStep.set_pin; first_pin = ""; confirm_input = ""; error_msg = null }
            SetupStep.set_text -> { step = SetupStep.choose_mode; text_input = ""; first_text = "" }
            SetupStep.confirm_text -> { step = SetupStep.set_text; text_input = ""; first_text = ""; error_msg = null }
            SetupStep.choose_mode -> on_dismiss()
        }
    }

    fun handle_first_digit(d: String) {
        if (first_pin.length >= chosen_digits) return
        val next = first_pin + d
        first_pin = next
        if (next.length == chosen_digits) { confirm_input = ""; step = SetupStep.confirm_pin }
    }

    fun handle_confirm_digit(d: String) {
        if (saving) return
        if (confirm_input.length >= chosen_digits) return
        val next = confirm_input + d
        confirm_input = next
        if (next.length == chosen_digits) {
            if (next != first_pin) {
                shake_trigger++
                error_msg = mismatch_str
                scope.launch {
                    delay(800)
                    confirm_input = ""; first_pin = ""
                    step = SetupStep.set_pin; error_msg = null
                }
                return
            }
            saving = true
            scope.launch {
                withContext(Dispatchers.Default) { store.setup_pin(next, "numeric", chosen_digits) }
                saving = false
                on_success()
            }
        }
    }

    fun handle_text_continue() {
        if (step == SetupStep.set_text) {
            if (text_input.length < 4) {
                error_msg = short_str
                scope.launch { delay(2000); error_msg = null }
                return
            }
            first_text = text_input
            text_input = ""
            step = SetupStep.confirm_text
        } else if (step == SetupStep.confirm_text) {
            if (text_input != first_text) {
                shake_trigger++
                error_msg = mismatch_str
                scope.launch {
                    delay(800)
                    text_input = ""; first_text = ""
                    step = SetupStep.set_text; error_msg = null
                }
                return
            }
            saving = true
            scope.launch {
                withContext(Dispatchers.Default) { store.setup_pin(text_input, "text", null) }
                saving = false
                on_success()
            }
        }
    }

    val step_index = when (step) {
        SetupStep.choose_mode -> 0
        SetupStep.choose_digits, SetupStep.set_text -> 1
        else -> 2
    }

    val title = when (step) {
        SetupStep.choose_mode -> stringResource(R.string.app_lock_choose_mode)
        SetupStep.choose_digits -> stringResource(R.string.app_lock_choose_digits)
        SetupStep.set_pin -> stringResource(R.string.app_lock_set_pin)
        SetupStep.confirm_pin -> stringResource(R.string.app_lock_confirm_pin)
        SetupStep.set_text -> stringResource(R.string.app_lock_set_passphrase)
        SetupStep.confirm_text -> stringResource(R.string.app_lock_confirm_passphrase)
    }

    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        containerColor = colors.bg_card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.xl, end = AsterSpacing.xl, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step != SetupStep.choose_mode) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(SquircleShape(8.dp))
                            .clickable(onClick = ::go_back),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TablerIcons.ArrowLeft,
                            contentDescription = null,
                            tint = colors.text_primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(AsterSpacing.sm))
                }
                Text(
                    text = title,
                    color = colors.text_primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (i <= step_index) colors.accent_blue else colors.border_secondary),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            when (step) {
                SetupStep.choose_mode -> {
                    mode_option_row(
                        label = stringResource(R.string.app_lock_mode_numeric),
                        desc = stringResource(R.string.app_lock_mode_numeric_desc),
                        selected = chosen_mode == "numeric",
                        on_click = { chosen_mode = "numeric" },
                    )
                    mode_option_row(
                        label = stringResource(R.string.app_lock_mode_text),
                        desc = stringResource(R.string.app_lock_mode_text_desc),
                        selected = chosen_mode == "text",
                        on_click = { chosen_mode = "text" },
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AsterSecondaryButton(
                            label = stringResource(R.string.cancel),
                            onClick = on_dismiss,
                            modifier = Modifier.weight(1f),
                        )
                        AsterButton(
                            label = stringResource(R.string.action_continue),
                            onClick = { if (chosen_mode == "numeric") step = SetupStep.choose_digits else step = SetupStep.set_text },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SetupStep.choose_digits -> {
                    listOf(4, 6, 8).forEach { n ->
                        val label = when (n) {
                            4 -> stringResource(R.string.app_lock_digits_4)
                            6 -> stringResource(R.string.app_lock_digits_6)
                            else -> stringResource(R.string.app_lock_digits_8)
                        }
                        mode_option_row(
                            label = label,
                            desc = null,
                            selected = chosen_digits == n,
                            on_click = { chosen_digits = n },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    AsterButton(
                        label = stringResource(R.string.action_continue),
                        onClick = { first_pin = ""; step = SetupStep.set_pin },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SetupStep.set_pin -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AsterSpacing.lg),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PinDots(digits = chosen_digits, filled = first_pin.length, shake_trigger = 0)
                        PinPad(
                            on_digit = ::handle_first_digit,
                            on_backspace = { first_pin = first_pin.dropLast(1) },
                            on_confirm = {},
                            enabled = true,
                        )
                    }
                }

                SetupStep.confirm_pin -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AsterSpacing.lg),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PinDots(digits = chosen_digits, filled = confirm_input.length, shake_trigger = shake_trigger)
                        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                            if (error_msg != null) Text(text = error_msg!!, color = colors.danger, fontSize = 12.sp)
                        }
                        PinPad(
                            on_digit = ::handle_confirm_digit,
                            on_backspace = { confirm_input = confirm_input.dropLast(1) },
                            on_confirm = {},
                            enabled = !saving,
                        )
                    }
                }

                SetupStep.set_text, SetupStep.confirm_text -> {
                    setup_passphrase_field(
                        value = text_input,
                        on_change = { text_input = it },
                        show = show_passphrase,
                        on_toggle_show = { show_passphrase = !show_passphrase },
                        shake_trigger = shake_trigger,
                        on_submit = ::handle_text_continue,
                    )
                    if (error_msg != null) {
                        Text(text = error_msg!!, color = colors.danger, fontSize = 13.sp)
                    }
                    AsterButton(
                        label = stringResource(R.string.action_continue),
                        onClick = ::handle_text_continue,
                        enabled = text_input.isNotEmpty() && !saving,
                        is_loading = saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockVerifySheet(
    store: AppLockStore,
    description: String,
    on_dismiss: () -> Unit,
    on_success: () -> Unit,
) {
    val sheet_state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = AsterMaterial.colors
    val scope = rememberCoroutineScope()
    val config = remember { store.get_config() }
    val pin_type = config?.pin_type ?: "numeric"
    val digits = config?.digits ?: 4

    var input by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var error_msg by remember { mutableStateOf<String?>(null) }
    var shake_trigger by remember { mutableIntStateOf(0) }
    var locked_out by remember { mutableStateOf(store.is_locked_out()) }
    var lockout_secs by remember { mutableLongStateOf(store.lockout_remaining_seconds()) }
    var show_passphrase by remember { mutableStateOf(false) }

    val wrong_str = stringResource(R.string.app_lock_wrong_pin)

    LaunchedEffect(locked_out) {
        if (!locked_out) return@LaunchedEffect
        while (true) {
            val secs = store.lockout_remaining_seconds()
            lockout_secs = secs
            if (secs <= 0) { locked_out = false; break }
            delay(1000)
        }
    }

    fun attempt(value: String) {
        if (verifying || locked_out) return
        scope.launch {
            verifying = true
            val ok = withContext(Dispatchers.Default) { store.verify_pin(value) }
            verifying = false
            if (ok) { on_success(); return@launch }
            input = ""
            if (store.is_locked_out()) {
                locked_out = true
                lockout_secs = store.lockout_remaining_seconds()
                error_msg = null
            } else {
                shake_trigger++
                error_msg = wrong_str
                launch { delay(2000); error_msg = null }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = on_dismiss,
        sheetState = sheet_state,
        containerColor = colors.bg_card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.xl, end = AsterSpacing.xl, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
        ) {
            Text(
                text = stringResource(R.string.app_lock_verify_title),
                color = colors.text_primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (locked_out) {
                Text(
                    text = stringResource(R.string.app_lock_try_again_in, lockout_secs),
                    color = colors.danger,
                    fontSize = 13.sp,
                )
            } else {
                Text(text = description, color = colors.text_secondary, fontSize = 14.sp)
            }

            if (pin_type == "numeric") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PinDots(digits = digits, filled = input.length, shake_trigger = shake_trigger)
                    Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                        when {
                            verifying -> androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent_blue)
                            error_msg != null -> Text(text = error_msg!!, color = colors.danger, fontSize = 12.sp)
                        }
                    }
                    PinPad(
                        on_digit = { d ->
                            if (!verifying && !locked_out) {
                                val next = input + d
                                input = next
                                if (next.length == digits) attempt(next)
                            }
                        },
                        on_backspace = { if (!verifying && !locked_out) input = input.dropLast(1) },
                        on_confirm = { if (input.isNotEmpty()) attempt(input) },
                        enabled = !verifying && !locked_out,
                    )
                }
            } else {
                setup_passphrase_field(
                    value = input,
                    on_change = { if (!verifying && !locked_out) input = it },
                    show = show_passphrase,
                    on_toggle_show = { show_passphrase = !show_passphrase },
                    shake_trigger = shake_trigger,
                    on_submit = { if (input.isNotEmpty()) attempt(input) },
                )
                if (error_msg != null) Text(text = error_msg!!, color = colors.danger, fontSize = 13.sp)
                AsterButton(
                    label = stringResource(R.string.app_lock_unlock),
                    onClick = { if (input.isNotEmpty()) attempt(input) },
                    enabled = input.isNotEmpty() && !verifying && !locked_out,
                    is_loading = verifying,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun mode_option_row(label: String, desc: String?, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (selected) colors.accent_blue else colors.bg_secondary
    val title_color = if (selected) Color.White else colors.text_primary
    val desc_color = if (selected) Color.White.copy(alpha = 0.75f) else colors.text_muted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(bg)
            .border(1.dp, if (selected) colors.accent_blue else colors.border_secondary, SquircleShape(14.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
    ) {
        Text(text = label, color = title_color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (desc != null) {
            Spacer(Modifier.height(2.dp))
            Text(text = desc, color = desc_color, fontSize = 12.sp)
        }
    }
}

@Composable
private fun setup_passphrase_field(
    value: String,
    on_change: (String) -> Unit,
    show: Boolean,
    on_toggle_show: () -> Unit,
    shake_trigger: Int,
    on_submit: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val offset_x = remember { Animatable(0f) }
    LaunchedEffect(shake_trigger) {
        if (shake_trigger == 0) return@LaunchedEffect
        for (x in listOf(-10f, 10f, -10f, 10f, 0f)) {
            offset_x.animateTo(x, animationSpec = tween(60))
        }
    }
    Box(
        modifier = Modifier
            .offset { IntOffset(offset_x.value.roundToInt(), 0) }
            .fillMaxWidth()
            .background(colors.input_bg, SquircleShape(14.dp))
            .border(1.dp, colors.input_border, SquircleShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = on_change,
            textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
            cursorBrush = SolidColor(colors.accent_blue),
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardActions = KeyboardActions(onDone = { on_submit() }),
            modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
        )
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.app_lock_passphrase_hint),
                color = colors.text_muted,
                fontSize = 15.sp,
            )
        }
        Icon(
            imageVector = if (show) TablerIcons.EyeOff else TablerIcons.Eye,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.CenterEnd)
                .clickable(onClick = on_toggle_show),
        )
    }
}
