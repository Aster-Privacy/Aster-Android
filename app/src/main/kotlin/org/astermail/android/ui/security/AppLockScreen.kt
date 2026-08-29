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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.security.AppLockStore
import org.astermail.android.security.BiometricGatePreparation
import org.astermail.android.security.BiometricUnlockGate
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
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
        )
        window.setDimAmount(0f)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
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

    var input by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var error_msg by remember { mutableStateOf<String?>(null) }
    var shake_trigger by remember { mutableIntStateOf(0) }
    var locked_out by remember { mutableStateOf(store.is_locked_out()) }
    var lockout_secs by remember { mutableLongStateOf(store.lockout_remaining_seconds()) }
    var show_passphrase by remember { mutableStateOf(false) }
    var biometric_available by remember {
        mutableStateOf(BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)
    }
    var biometric_prompted by remember { mutableStateOf(false) }

    val wrong_pin_str = stringResource(R.string.app_lock_wrong_pin)
    val locked_out_str = stringResource(R.string.app_lock_locked_out)
    val biometric_failed_str = stringResource(R.string.app_lock_biometric_failed)

    LaunchedEffect(locked_out) {
        if (!locked_out) return@LaunchedEffect
        while (true) {
            val secs = store.lockout_remaining_seconds()
            lockout_secs = secs
            if (secs <= 0) { locked_out = false; break }
            delay(1000)
        }
    }

    fun launch_biometric() {
        val activity = context as? FragmentActivity ?: return
        val preparation = BiometricUnlockGate.prepare(context)
        val cipher = when (preparation) {
            is BiometricGatePreparation.Enroll -> preparation.cipher
            is BiometricGatePreparation.Verify -> preparation.cipher
            BiometricGatePreparation.Unavailable -> {
                biometric_available = false
                error_msg = biometric_failed_str
                return
            }
        }
        val executor = ContextCompat.getMainExecutor(context)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_lock_biometric_title))
            .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
            .setNegativeButtonText(context.getString(R.string.app_lock_use_pin))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated_cipher = result.cryptoObject?.cipher
                if (authenticated_cipher == null) {
                    error_msg = biometric_failed_str
                    return
                }
                val ok = when (preparation) {
                    is BiometricGatePreparation.Enroll ->
                        BiometricUnlockGate.complete_enroll(context, authenticated_cipher)
                    else ->
                        BiometricUnlockGate.complete_verify(context, authenticated_cipher)
                }
                if (ok) {
                    store.mark_session_unlocked()
                } else {
                    BiometricUnlockGate.reset(context)
                    error_msg = biometric_failed_str
                }
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (code == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                    code == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                    code == BiometricPrompt.ERROR_HW_UNAVAILABLE
                ) {
                    biometric_available = false
                }
            }
        }).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    LaunchedEffect(biometric_available) {
        if (biometric_available && !biometric_prompted && BiometricUnlockGate.is_enrolled(context)) {
            biometric_prompted = true
            launch_biometric()
        }
    }

    fun attempt_verify(value: String) {
        if (verifying || locked_out) return
        scope.launch {
            verifying = true
            val ok = try {
                withContext(Dispatchers.Default) { store.verify_pin(value) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                verifying = false
            }
            if (ok) return@launch
            input = ""
            if (store.is_locked_out()) {
                locked_out = true
                lockout_secs = store.lockout_remaining_seconds()
                error_msg = null
            } else {
                shake_trigger++
                error_msg = wrong_pin_str
                launch { delay(2000); error_msg = null }
            }
        }
    }

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
            .background(colors.bg_primary)
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
                modifier = Modifier.height(22.dp),
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

            if (pin_type == "numeric") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PinDots(digits = digits, filled = input.length, shake_trigger = shake_trigger)
                    Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                        when {
                            verifying -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent_blue)
                            error_msg != null -> Text(text = error_msg!!, color = colors.danger, fontSize = 12.sp)
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
                                modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
                            )
                            if (input.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.app_lock_passphrase_hint),
                                    color = colors.text_muted,
                                    fontSize = 15.sp,
                                )
                            }
                            Icon(
                                imageVector = if (show_passphrase) TablerIcons.EyeOff else TablerIcons.Eye,
                                contentDescription = null,
                                tint = colors.text_muted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.CenterEnd)
                                    .clickable { show_passphrase = !show_passphrase },
                            )
                        }
                    }
                    if (error_msg != null) {
                        Text(text = error_msg!!, color = colors.danger, fontSize = 12.sp)
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

            if (biometric_available) {
                Box(
                    modifier = Modifier
                        .clip(SquircleShape(12.dp))
                        .clickable { launch_biometric() }
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
    val offset_x = remember { Animatable(0f) }

    LaunchedEffect(shake_trigger) {
        if (shake_trigger == 0) return@LaunchedEffect
        for (x in listOf(-10f, 10f, -10f, 10f, 0f)) {
            offset_x.animateTo(x, animationSpec = tween(60))
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.offset { IntOffset(offset_x.value.roundToInt(), 0) },
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
                enabled = enabled,
                bg = colors.bg_secondary,
                on_click = on_backspace,
            )
            PinKey(label = "0", enabled = enabled, bg = colors.bg_secondary, on_click = { on_digit("0") })
            PinIconKey(
                icon = TablerIcons.Check,
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
            .then(if (enabled && label != null) Modifier.clickable(onClick = on_click) else Modifier),
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
            .then(if (enabled) Modifier.clickable(onClick = on_click) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) colors.text_primary else colors.text_muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun ShakeBox(trigger: Int, content: @Composable () -> Unit) {
    val offset_x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        for (x in listOf(-10f, 10f, -10f, 10f, 0f)) {
            offset_x.animateTo(x, animationSpec = tween(60))
        }
    }
    Box(modifier = Modifier.offset { IntOffset(offset_x.value.roundToInt(), 0) }) {
        content()
    }
}
