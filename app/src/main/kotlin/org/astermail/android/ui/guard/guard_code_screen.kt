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

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.crypto.guard.GuardCode
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.field_surface_color
import org.astermail.android.design.components.AsterButton
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.storage.AccountStore
import org.astermail.android.ui.common.page_surface
import org.astermail.android.ui.common.secure_screen

data class guard_code_view(val code: String?, val seconds_left: Int, val missing: Boolean)

fun guard_code_state(secret: ByteArray?, now_seconds: Long): guard_code_view {
    val left = GuardCode.seconds_left(now_seconds)
    if (secret == null || secret.isEmpty()) return guard_code_view(code = null, seconds_left = left, missing = true)
    return guard_code_view(code = GuardCode.now(secret, now_seconds), seconds_left = left, missing = false)
}

@HiltViewModel
class GuardCodeViewModel @Inject constructor(
    private val store: GuardDeviceStore,
    private val accounts: AccountStore,
) : ViewModel() {
    fun view(now_seconds: Long): guard_code_view {
        val user = accounts.get_current_id()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val secret = user?.let { store.code_secret(it) }
        return try {
            guard_code_state(secret, now_seconds)
        } finally {
            secret?.fill(0)
        }
    }
}

suspend fun guard_confirm_user(activity: FragmentActivity, title: String, subtitle: String): Boolean {
    val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        return false
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(authenticators)
        .setConfirmationRequired(false)
        .build()
    return suspendCancellableCoroutine { cont ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (cont.isActive) cont.resume(false)
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        try {
            prompt.authenticate(info)
        } catch (_: Throwable) {
            if (cont.isActive) cont.resume(false)
        }
        cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
    }
}

@Composable
fun guard_code_host(view_model: GuardCodeViewModel, on_back: () -> Unit, on_join: (() -> Unit)?) {
    val activity = find_fragment_activity(LocalContext.current)
    val scope = rememberCoroutineScope()
    val lifecycle_owner = LocalLifecycleOwner.current
    var revealed by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<guard_code_view?>(null) }
    val title = stringResource(R.string.guard_prompt_title)
    val subtitle = stringResource(R.string.guard_code_prompt_subtitle)

    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                revealed = false
                view = null
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revealed) {
        while (revealed) {
            view = view_model.view(System.currentTimeMillis() / 1000)
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        val probe = view_model.view(System.currentTimeMillis() / 1000)
        if (probe.missing) view = probe
    }
    guard_code_screen(
        view = view,
        revealed = revealed,
        on_reveal = {
            scope.launch {
                val ok = activity != null && guard_confirm_user(activity, title, subtitle)
                if (ok) revealed = true
            }
        },
        on_back = on_back,
        on_join = on_join,
    )
}

@Composable
fun guard_code_screen(
    view: guard_code_view?,
    revealed: Boolean,
    on_reveal: () -> Unit,
    on_back: () -> Unit,
    on_join: (() -> Unit)?,
) {
    secure_screen()
    val colors = AsterMaterial.colors
    androidx.activity.compose.BackHandler { on_back() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors),
    ) {
        guard_wizard_result {
            if (view?.missing == true) {
                guard_result_block(
                    title = stringResource(R.string.guard_code_title),
                    body = stringResource(R.string.guard_code_missing),
                    primary_label = stringResource(if (on_join != null) R.string.guard_code_join_action else R.string.done),
                    on_primary = on_join ?: on_back,
                    secondary_label = if (on_join != null) stringResource(R.string.guard_approve_close) else null,
                    on_secondary = if (on_join != null) on_back else null,
                )
            } else if (revealed && view?.code != null) {
                guard_wordmark()
                Spacer(Modifier.height(AsterSpacing.xxl + AsterSpacing.sm))
                guard_wizard_title(stringResource(R.string.guard_code_title), Modifier.fillMaxWidth(), TextAlign.Center)
                Spacer(Modifier.height(AsterSpacing.sm))
                guard_wizard_body(stringResource(R.string.guard_code_body), Modifier.fillMaxWidth(), TextAlign.Center)
                Spacer(Modifier.height(AsterSpacing.xxl))
                guard_code_card(view.code, view.seconds_left)
                Spacer(Modifier.height(AsterSpacing.xxl))
                AsterButton(
                    label = stringResource(R.string.done),
                    onClick = on_back,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                guard_result_block(
                    title = stringResource(R.string.guard_code_title),
                    body = stringResource(R.string.guard_code_hidden),
                    primary_label = stringResource(R.string.guard_code_show),
                    on_primary = on_reveal,
                    secondary_label = stringResource(R.string.guard_approve_close),
                    on_secondary = on_back,
                )
            }
        }
    }
}

@Composable
private fun guard_code_card(code: String, seconds_left: Int) {
    val colors = AsterMaterial.colors
    val description = stringResource(R.string.guard_code_description, code.toCharArray().joinToString(" "))
    val tone = if (seconds_left <= 5) colors.warning else colors.accent_blue
    val shape = SquircleShape(12.dp)
    guard_card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AsterSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                code.forEachIndexed { index, digit ->
                    if (index > 0 && index == code.length / 2) Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 52.dp)
                            .background(field_surface_color(colors), shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit.toString(),
                            color = colors.text_primary,
                            fontSize = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            guard_progress_bar(
                progress = (seconds_left.toFloat() / GuardCode.STEP_SECONDS.toFloat()).coerceIn(0f, 1f),
                color = tone,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AsterSpacing.md))
            guard_chip(
                text = stringResource(R.string.guard_code_refresh, seconds_left),
                icon = TablerIcons.Clock,
                color = tone,
            )
        }
    }
}
