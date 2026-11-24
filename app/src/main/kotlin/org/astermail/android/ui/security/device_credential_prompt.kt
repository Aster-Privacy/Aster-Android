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

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import org.astermail.android.security.AppLockStore
import org.astermail.android.security.BiometricGatePreparation
import org.astermail.android.security.DeviceCredentialPolicy
import org.astermail.android.security.DeviceCredentialResult
import org.astermail.android.security.DeviceCredentialRoute
import org.astermail.android.security.DeviceCredentialUnlockGate

private val confirm_request_counter = AtomicInteger(0)

fun has_secure_screen_lock(context: Context): Boolean =
    runCatching { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }.getOrDefault(false)

private fun prompt_error_result(code: Int): DeviceCredentialResult = when (code) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
    -> DeviceCredentialResult.CANCELED
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    -> DeviceCredentialResult.UNAVAILABLE
    else -> DeviceCredentialResult.FAILED
}

fun launch_device_credential(
    activity: FragmentActivity,
    store: AppLockStore,
    title: String,
    subtitle: String?,
    prepare: () -> Cipher?,
    complete: (Cipher) -> Boolean,
    on_result: (DeviceCredentialResult) -> Unit,
) {
    if (!has_secure_screen_lock(activity)) {
        on_result(DeviceCredentialResult.UNAVAILABLE)
        return
    }
    val route = DeviceCredentialPolicy.route(Build.VERSION.SDK_INT)
    if (DeviceCredentialPolicy.prepares_before_prompt(route)) {
        val cipher = prepare()
        if (cipher == null) {
            on_result(DeviceCredentialResult.UNAVAILABLE)
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(DEVICE_CREDENTIAL)
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated = result.cryptoObject?.cipher
                val ok = authenticated != null && complete(authenticated)
                on_result(if (ok) DeviceCredentialResult.SUCCESS else DeviceCredentialResult.FAILED)
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                on_result(prompt_error_result(code))
            }
        }
        store.begin_external_flow()
        try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
                .authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (t: Throwable) {
            on_result(DeviceCredentialResult.FAILED)
        }
        return
    }
    check(route == DeviceCredentialRoute.KEYGUARD_CONFIRM)
    val keyguard = activity.getSystemService(KeyguardManager::class.java)
    @Suppress("DEPRECATION")
    val intent = runCatching { keyguard?.createConfirmDeviceCredentialIntent(title, subtitle) }.getOrNull()
    if (intent == null) {
        on_result(DeviceCredentialResult.UNAVAILABLE)
        return
    }
    val key = "aster_app_lock_confirm_" + confirm_request_counter.incrementAndGet()
    val holder = arrayOfNulls<androidx.activity.result.ActivityResultLauncher<android.content.Intent>>(1)
    val launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.StartActivityForResult()) { result ->
        holder[0]?.unregister()
        if (result.resultCode != Activity.RESULT_OK) {
            on_result(DeviceCredentialResult.CANCELED)
            return@register
        }
        val cipher = prepare()
        val ok = cipher != null && runCatching { complete(cipher) }.getOrDefault(false)
        on_result(
            when {
                cipher == null -> DeviceCredentialResult.UNAVAILABLE
                ok -> DeviceCredentialResult.SUCCESS
                else -> DeviceCredentialResult.FAILED
            },
        )
    }
    holder[0] = launcher
    store.begin_external_flow()
    try {
        launcher.launch(intent)
    } catch (t: Throwable) {
        launcher.unregister()
        on_result(DeviceCredentialResult.FAILED)
    }
}

fun launch_device_unlock(
    activity: FragmentActivity,
    store: AppLockStore,
    title: String,
    subtitle: String?,
    unlock_session: Boolean,
    on_result: (DeviceCredentialResult) -> Unit,
) {
    val app_context = activity.applicationContext
    launch_device_credential(
        activity = activity,
        store = store,
        title = title,
        subtitle = subtitle,
        prepare = { (DeviceCredentialUnlockGate.prepare_unlock(app_context) as? BiometricGatePreparation.Verify)?.cipher },
        complete = { cipher -> store.complete_device_unlock(cipher, unlock_session) },
        on_result = on_result,
    )
}

fun launch_device_lock_setup(
    activity: FragmentActivity,
    store: AppLockStore,
    title: String,
    subtitle: String?,
    on_result: (DeviceCredentialResult) -> Unit,
) {
    val app_context = activity.applicationContext
    val lock_key = store.new_device_lock_key()
    if (lock_key == null) {
        on_result(DeviceCredentialResult.UNAVAILABLE)
        return
    }
    launch_device_credential(
        activity = activity,
        store = store,
        title = title,
        subtitle = subtitle,
        prepare = { (DeviceCredentialUnlockGate.prepare_enroll(app_context) as? BiometricGatePreparation.Enroll)?.cipher },
        complete = { cipher -> store.setup_device(cipher, lock_key.copyOf()) },
        on_result = { result ->
            lock_key.fill(0)
            if (result != DeviceCredentialResult.SUCCESS && !store.is_device_mode()) {
                runCatching { DeviceCredentialUnlockGate.reset(app_context) }
            }
            on_result(result)
        },
    )
}
