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

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterMotionSpec
import org.astermail.android.design.aster_error_shake
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.security.AppLockMode
import org.astermail.android.security.AppLockStore
import org.astermail.android.security.DeviceCredentialResult
import org.astermail.android.security.DeviceCredentialUnlockGate
import org.astermail.android.security.BiometricEnrollOrigin
import org.astermail.android.security.BiometricErrorOutcome
import org.astermail.android.security.BiometricLaunchBlock
import org.astermail.android.security.BiometricGatePreparation
import org.astermail.android.security.BiometricUnlockGate
import org.astermail.android.security.biometric_error_message
import org.astermail.android.security.biometric_error_outcome
import org.astermail.android.security.biometric_launch_block
import org.astermail.android.security.should_auto_prompt_biometric
import org.astermail.android.security.should_rearm_auto_prompt
import org.astermail.android.security.should_rebind_after_pin
import org.astermail.android.ui.common.find_host_activity
import kotlin.math.roundToInt

@Composable
fun AppLockScreen(store: AppLockStore, on_sign_out: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
    ) {
        app_lock_content(store = store, on_sign_out = on_sign_out)
    }
}

@Composable
fun lock_dialog_window_effect() {
    org.astermail.android.design.components.aster_popup_window_effect(full_window = true)
}

@Composable
private fun app_lock_content(store: AppLockStore, on_sign_out: () -> Unit) {
    lock_dialog_window_effect()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember { store.get_config() }
    val pin_type = config?.pin_type ?: "numeric"
    val digits = config?.digits ?: 6
    val device_mode = config?.mode == AppLockMode.DEVICE

    var input by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var error_msg by remember { mutableStateOf<String?>(null) }
    var shake_trigger by remember { mutableIntStateOf(0) }
    var locked_out by remember { mutableStateOf(store.is_locked_out()) }
    var lockout_secs by remember { mutableLongStateOf(store.lockout_remaining_seconds()) }
    var attempt_notice by remember { mutableStateOf(store.attempt_notice()) }
    var show_passphrase by remember { mutableStateOf(false) }
    var biometric_available by remember {
        mutableStateOf(BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)
    }
    var auto_prompt_armed by remember { mutableStateOf(true) }
    var device_prompt_in_flight by remember { mutableStateOf(false) }
    val host_activity = remember(context) { context.find_host_activity() as? FragmentActivity }
    val lifecycle_owner = host_activity ?: LocalLifecycleOwner.current
    val lifecycle_state by lifecycle_owner.lifecycle.currentStateAsState()
    var biometric_enrolled by remember {
        mutableStateOf(
            run {
                BiometricUnlockGate.reconcile(context)
                BiometricUnlockGate.is_enrolled(context)
            },
        )
    }

    val reduce_motion = aster_reduce_motion()
    val reveal_alpha = remember { Animatable(1f) }
    var revealing by remember { mutableStateOf(false) }

    fun reveal_app() {
        if (revealing) return
        revealing = true
        scope.launch {
            reveal_alpha.animateTo(
                0f,
                tween(
                    durationMillis = if (reduce_motion) AsterMotionSpec.reduced_fade_out_ms else AsterMotionSpec.fade_exit_ms,
                    easing = AsterEasing.emphasized_accelerate,
                ),
            )
            store.mark_session_unlocked()
        }
    }

    val locked_out_str = stringResource(R.string.app_lock_locked_out)
    val biometric_failed_str = stringResource(if (device_mode) R.string.app_lock_biometric_failed_device else R.string.app_lock_biometric_failed)
    val biometric_error_str = stringResource(if (device_mode) R.string.app_lock_biometric_error_device else R.string.app_lock_biometric_error)
    val biometric_reset_str = stringResource(if (device_mode) R.string.app_lock_biometric_reset_device else R.string.app_lock_biometric_reset)
    val device_error_str = stringResource(R.string.app_lock_device_error)
    val device_unavailable_str = stringResource(R.string.app_lock_device_unavailable)
    val device_key_lost_str = stringResource(R.string.app_lock_device_key_lost)

    LaunchedEffect(locked_out) {
        if (!locked_out) return@LaunchedEffect
        while (true) {
            val secs = store.lockout_remaining_seconds()
            lockout_secs = secs
            if (secs <= 0) { locked_out = false; break }
            delay(1000)
        }
    }

    fun finish_pin_unlock() {
        val activity = host_activity
        val rebind = should_rebind_after_pin(BiometricUnlockGate.is_rebind_pending(context), biometric_available)
        if (activity == null || !rebind) {
            reveal_app()
            return
        }
        BiometricUnlockGate.clear_rebind_pending(context)
        launch_biometric_enroll(
            activity = activity,
            origin = BiometricEnrollOrigin.AFTER_PIN_ON_LOCK_SCREEN,
            pin_verified = true,
            biometric_available = biometric_available,
        ) { reveal_app() }
    }

    fun launch_device(from_user: Boolean) {
        val activity = host_activity
        val block = biometric_launch_block(
            has_host = activity != null,
            resumed = activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true,
            state_saved = activity?.supportFragmentManager?.isStateSaved == true,
        )
        if (activity == null || block != BiometricLaunchBlock.NONE) {
            if (from_user) error_msg = device_error_str
            else auto_prompt_armed = block == BiometricLaunchBlock.NOT_RESUMED
            return
        }
        if (device_prompt_in_flight) return
        error_msg = null
        device_prompt_in_flight = true
        launch_device_unlock(
            activity = activity,
            store = store,
            title = activity.getString(R.string.app_lock_biometric_title),
            subtitle = activity.getString(R.string.app_lock_device_subtitle),
            unlock_session = false,
        ) { result ->
            device_prompt_in_flight = false
            when (result) {
                DeviceCredentialResult.SUCCESS -> finish_pin_unlock()
                DeviceCredentialResult.CANCELED -> {}
                DeviceCredentialResult.UNAVAILABLE -> {
                    error_msg = if (DeviceCredentialUnlockGate.is_enrolled(context)) device_unavailable_str else device_key_lost_str
                }
                DeviceCredentialResult.FAILED -> error_msg = device_error_str
            }
        }
    }

    fun launch_biometric(from_user: Boolean) {
        val activity = host_activity
        val block = biometric_launch_block(
            has_host = activity != null,
            resumed = activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true,
            state_saved = activity?.supportFragmentManager?.isStateSaved == true,
        )
        when (block) {
            BiometricLaunchBlock.NONE -> {}
            BiometricLaunchBlock.NO_HOST -> {
                error_msg = biometric_failed_str
                return
            }
            BiometricLaunchBlock.NOT_RESUMED, BiometricLaunchBlock.STATE_SAVED -> {
                if (from_user) {
                    error_msg = biometric_error_str
                } else {
                    auto_prompt_armed = block == BiometricLaunchBlock.NOT_RESUMED
                }
                return
            }
        }
        if (activity == null) return
        val preparation = BiometricUnlockGate.prepare_unlock(context)
        val cipher = (preparation as? BiometricGatePreparation.Verify)?.cipher
        if (cipher == null) {
            biometric_enrolled = BiometricUnlockGate.is_enrolled(context)
            error_msg = if (BiometricUnlockGate.is_rebind_pending(context)) biometric_reset_str else biometric_failed_str
            return
        }
        error_msg = null
        val executor = ContextCompat.getMainExecutor(activity)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_lock_biometric_title))
            .setSubtitle(activity.getString(R.string.app_lock_biometric_subtitle))
            .setNegativeButtonText(
                activity.getString(if (device_mode) R.string.app_lock_use_screen_lock else R.string.app_lock_use_pin),
            )
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (store.complete_biometric_unlock(result.cryptoObject?.cipher ?: cipher, unlock_session = false)) {
                    reveal_app()
                } else {
                    BiometricUnlockGate.invalidate(context)
                    biometric_enrolled = false
                    error_msg = biometric_reset_str
                }
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (device_mode && code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    launch_device(from_user = true)
                    return
                }
                when (biometric_error_outcome(code)) {
                    BiometricErrorOutcome.IGNORE -> {}
                    BiometricErrorOutcome.SHOW_MESSAGE -> {
                        error_msg = biometric_error_message(message, biometric_error_str)
                    }
                    BiometricErrorOutcome.SHOW_MESSAGE_AND_HIDE -> {
                        biometric_available = false
                        error_msg = biometric_error_message(message, biometric_failed_str)
                    }
                }
            }
        }
        activity.after_prompt_closed {
            try {
                BiometricPrompt(activity, executor, callback).authenticate(info, BiometricPrompt.CryptoObject(cipher))
            } catch (e: Exception) {
                error_msg = biometric_error_str
            }
        }
    }

    LaunchedEffect(lifecycle_state) {
        if (device_prompt_in_flight) return@LaunchedEffect
        if (should_rearm_auto_prompt(lifecycle_state.isAtLeast(Lifecycle.State.STARTED))) auto_prompt_armed = true
    }

    LaunchedEffect(auto_prompt_armed, lifecycle_state, biometric_available, biometric_enrolled) {
        val resumed = lifecycle_state.isAtLeast(Lifecycle.State.RESUMED)
        if (should_auto_prompt_biometric(auto_prompt_armed, resumed, biometric_available, biometric_enrolled)) {
            auto_prompt_armed = false
            launch_biometric(from_user = false)
        } else if (device_mode && auto_prompt_armed && resumed && !(biometric_available && biometric_enrolled)) {
            auto_prompt_armed = false
            launch_device(from_user = false)
        }
    }

    fun attempt_verify(value: String) {
        if (verifying || locked_out) return
        scope.launch {
            verifying = true
            val ok = try {
                withContext(Dispatchers.Default) { store.verify_pin(value, unlock_session = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                verifying = false
            }
            if (ok) {
                finish_pin_unlock()
                return@launch
            }
            input = ""
            attempt_notice = store.attempt_notice()
            if (store.is_locked_out()) {
                locked_out = true
                lockout_secs = store.lockout_remaining_seconds()
                error_msg = null
            } else {
                shake_trigger++
                val message = pin_failure_message(context.resources, attempt_notice)
                error_msg = message
                launch { delay(2000); if (error_msg == message) error_msg = null }
            }
        }
    }

    val attempt_hint = if (locked_out) null else pin_attempt_hint(attempt_notice)

    fun handle_digit(d: String) {
        if (verifying || locked_out) return
        if (input.length >= digits) return
        val next = input + d
        input = next
        if (next.length == digits) attempt_verify(next)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = reveal_alpha.value }
            .background(colors.solid_bg)
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.lg),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.aster_wordmark),
                contentDescription = null,
                modifier = Modifier.height(org.astermail.android.ui.auth.auth_logo_height),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.app_lock_title),
                    color = colors.text_primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (locked_out) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pluralStringResource(R.plurals.app_lock_try_again_in, lockout_secs.toInt(), lockout_secs),
                        color = colors.text_muted,
                        fontSize = 14.sp,
                    )
                }
            }

            if (device_mode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.width(280.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_lock_device_subtitle),
                        color = colors.text_muted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (error_msg != null) {
                        Text(text = error_msg!!, color = colors.danger, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    AsterButton(
                        label = stringResource(R.string.app_lock_unlock),
                        onClick = { launch_device(from_user = true) },
                        enabled = !device_prompt_in_flight,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (pin_type == "numeric") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PinDots(digits = digits, filled = input.length, shake_trigger = shake_trigger)
                    Box(modifier = Modifier.heightIn(min = 18.dp), contentAlignment = Alignment.Center) {
                        when {
                            verifying -> org.astermail.android.design.components.aster_spinner(modifier = Modifier.size(16.dp), stroke_width = 2.dp, color = colors.accent_blue)
                            error_msg != null -> Text(text = error_msg!!, color = colors.danger, fontSize = 12.sp, textAlign = TextAlign.Center)
                            attempt_hint != null -> Text(text = attempt_hint, color = colors.text_muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                PinPad(
                    on_digit = ::handle_digit,
                    on_backspace = { if (!locked_out && !verifying) input = input.dropLast(1) },
                    on_confirm = { if (input.isNotEmpty()) attempt_verify(input) },
                    enabled = !verifying && !locked_out,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.width(280.dp),
                ) {
                    ShakeBox(trigger = shake_trigger) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.input_bg, SquircleShape(14.dp))
                                .border(1.dp, colors.input_border, SquircleShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                        ) {
                            BasicTextField(
                                value = input,
                                onValueChange = { if (!verifying && !locked_out) input = it },
                                textStyle = TextStyle(color = colors.text_primary, fontSize = 15.sp),
                                cursorBrush = SolidColor(colors.accent_blue),
                                singleLine = true,
                                visualTransformation = if (show_passphrase) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (input.isNotEmpty()) attempt_verify(input) }),
                                modifier = Modifier.fillMaxWidth().padding(end = 34.dp),
                            )
                            if (input.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.app_lock_passphrase_hint),
                                    color = colors.text_muted,
                                    fontSize = 15.sp,
                                )
                            }
                            val eye_label = stringResource(
                                if (show_passphrase) R.string.hide_password else R.string.show_password,
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.CenterEnd)
                                    .wrapContentSize(unbounded = true)
                                    .minimumInteractiveComponentSize()
                                    .clickable(role = Role.Button) { show_passphrase = !show_passphrase }
                                    .semantics { contentDescription = eye_label },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (show_passphrase) TablerIcons.EyeOff else TablerIcons.Eye,
                                    contentDescription = null,
                                    tint = colors.text_muted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    if (error_msg != null) {
                        Text(text = error_msg!!, color = colors.danger, fontSize = 12.sp, textAlign = TextAlign.Center)
                    } else if (attempt_hint != null) {
                        Text(text = attempt_hint, color = colors.text_muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    AsterButton(
                        label = stringResource(R.string.app_lock_unlock),
                        onClick = { if (input.isNotEmpty()) attempt_verify(input) },
                        enabled = input.isNotEmpty() && !verifying && !locked_out,
                        is_loading = verifying,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (biometric_available && biometric_enrolled) {
                Box(
                    modifier = Modifier
                        .clip(SquircleShape(12.dp))
                        .clickable(role = Role.Button) { launch_biometric(from_user = true) }
                        .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TablerIcons.Fingerprint,
                            contentDescription = null,
                            tint = colors.accent_blue,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.app_lock_use_biometric),
                            color = colors.accent_blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(SquircleShape(10.dp))
                    .clickable(onClick = on_sign_out)
                    .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.sign_out),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun PinDots(digits: Int, filled: Int, shake_trigger: Int) {
    val colors = AsterMaterial.colors

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.aster_error_shake(shake_trigger),
    ) {
        repeat(digits) { i ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .then(
                        if (i < filled) {
                            Modifier.background(colors.accent_blue)
                        } else {
                            Modifier.border(2.dp, colors.text_muted.copy(alpha = 0.4f), CircleShape)
                        },
                    ),
            )
        }
    }
}

@Composable
fun PinPad(
    on_digit: (String) -> Unit,
    on_backspace: () -> Unit,
    on_confirm: () -> Unit,
    enabled: Boolean,
) {
    val colors = AsterMaterial.colors
    val keys = listOf("1","2","3","4","5","6","7","8","9")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    PinKey(
                        label = k,
                        enabled = enabled,
                        bg = colors.bg_secondary,
                        on_click = { on_digit(k) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PinIconKey(
                icon = TablerIcons.Backspace,
                content_description = stringResource(R.string.delete),
                enabled = enabled,
                bg = colors.bg_secondary,
                on_click = on_backspace,
            )
            PinKey(label = "0", enabled = enabled, bg = colors.bg_secondary, on_click = { on_digit("0") })
            PinIconKey(
                icon = TablerIcons.Check,
                content_description = stringResource(R.string.confirm),
                enabled = enabled,
                bg = colors.bg_secondary,
                on_click = on_confirm,
            )
        }
    }
}

@Composable
fun PinKey(label: String?, enabled: Boolean, bg: Color, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (enabled && label != null) Modifier.clickable(role = Role.Button, onClick = on_click) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                color = if (enabled) colors.text_primary else colors.text_muted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun PinIconKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content_description: String,
    enabled: Boolean,
    bg: Color,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = on_click) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = content_description,
            tint = if (enabled) colors.text_primary else colors.text_muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun ShakeBox(trigger: Int, content: @Composable () -> Unit) {
    Box(modifier = Modifier.aster_error_shake(trigger)) {
        content()
    }
}
