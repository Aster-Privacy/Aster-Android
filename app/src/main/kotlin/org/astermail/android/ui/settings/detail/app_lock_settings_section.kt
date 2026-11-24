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

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.security.AppLockMode
import org.astermail.android.security.AppLockStore
import org.astermail.android.security.AppLockTimeout
import org.astermail.android.security.BiometricEnrollOrigin
import org.astermail.android.security.BiometricUnlockGate
import org.astermail.android.security.DeviceCredentialResult
import org.astermail.android.ui.common.find_host_activity
import org.astermail.android.ui.security.AppLockSetupSheet
import org.astermail.android.ui.security.AppLockVerifySheet
import org.astermail.android.ui.security.has_secure_screen_lock
import org.astermail.android.ui.security.launch_biometric_enroll
import org.astermail.android.ui.security.launch_device_lock_setup
import org.astermail.android.ui.security.launch_device_unlock

private enum class AppLockSettingsModal { setup, verify_to_change, change, verify }

@androidx.annotation.StringRes
internal fun app_lock_timeout_label(timeout: AppLockTimeout): Int = when (timeout) {
    AppLockTimeout.IMMEDIATELY -> R.string.app_lock_timeout_immediately
    AppLockTimeout.ONE_MINUTE -> R.string.app_lock_timeout_one_minute
    AppLockTimeout.FIVE_MINUTES -> R.string.app_lock_timeout_five_minutes
    AppLockTimeout.FIFTEEN_MINUTES -> R.string.app_lock_timeout_fifteen_minutes
    AppLockTimeout.ONE_HOUR -> R.string.app_lock_timeout_one_hour
}

internal fun app_lock_rows_visible(vanguard_enabled: Boolean?, lock_configured: Boolean): Boolean =
    vanguard_enabled == true || lock_configured

@Composable
internal fun app_lock_rows(store: AppLockStore) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val config_version by store.config_version.collectAsState()

    var enabled by remember { mutableStateOf(store.is_configured()) }
    var mode by remember { mutableStateOf(store.unlock_mode()) }
    var timeout by remember { mutableStateOf(store.timeout()) }
    var hide_notifications by remember { mutableStateOf(store.notification_content_hidden_setting()) }
    var biometric_enabled by remember { mutableStateOf(BiometricUnlockGate.is_enrolled(context)) }
    var modal by remember { mutableStateOf<AppLockSettingsModal?>(null) }
    var verify_description by remember { mutableStateOf("") }
    var pending_action by remember { mutableStateOf<(() -> Unit)?>(null) }
    var timeout_expanded by remember { mutableStateOf(false) }

    val biometric_available = remember {
        androidx.biometric.BiometricManager.from(context).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG,
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
    val screen_lock_available = remember { has_secure_screen_lock(context) }

    fun refresh() {
        enabled = store.is_configured()
        mode = store.unlock_mode()
        timeout = store.timeout()
        hide_notifications = store.notification_content_hidden_setting()
        biometric_enabled = BiometricUnlockGate.is_enrolled(context)
    }

    LaunchedEffect(config_version) { refresh() }

    val device_error_str = stringResource(R.string.app_lock_device_error)
    val device_unavailable_str = stringResource(R.string.app_lock_device_unavailable)
    val setup_failed_str = stringResource(R.string.app_lock_setup_unavailable)
    val verify_title = stringResource(R.string.app_lock_verify_title)
    val device_setup_title = stringResource(R.string.app_lock_device_setup_title)
    val device_setup_subtitle = stringResource(R.string.app_lock_device_setup_subtitle)

    fun host(): FragmentActivity? = context.find_host_activity() as? FragmentActivity

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun authenticate(description: String, action: () -> Unit) {
        if (store.unlock_mode() == AppLockMode.DEVICE) {
            val activity = host() ?: return
            launch_device_unlock(
                activity = activity,
                store = store,
                title = verify_title,
                subtitle = description,
                unlock_session = false,
            ) { result ->
                when (result) {
                    DeviceCredentialResult.SUCCESS -> action()
                    DeviceCredentialResult.CANCELED -> {}
                    DeviceCredentialResult.UNAVAILABLE -> toast(device_unavailable_str)
                    DeviceCredentialResult.FAILED -> toast(device_error_str)
                }
            }
        } else {
            verify_description = description
            pending_action = action
            modal = AppLockSettingsModal.verify
        }
    }

    fun start_device_setup() {
        modal = null
        val activity = host() ?: return
        launch_device_lock_setup(
            activity = activity,
            store = store,
            title = device_setup_title,
            subtitle = device_setup_subtitle,
        ) { result ->
            when (result) {
                DeviceCredentialResult.SUCCESS -> refresh()
                DeviceCredentialResult.CANCELED -> refresh()
                DeviceCredentialResult.UNAVAILABLE -> { refresh(); toast(device_unavailable_str) }
                DeviceCredentialResult.FAILED -> { refresh(); toast(setup_failed_str) }
            }
        }
    }

    fun enroll_biometric() {
        val activity = host() ?: return
        launch_biometric_enroll(
            activity = activity,
            origin = BiometricEnrollOrigin.SETTINGS_AFTER_PIN,
            pin_verified = true,
            biometric_available = biometric_available,
        ) { biometric_enabled = it }
    }

    val disable_str = stringResource(R.string.app_lock_enter_to_disable)
    val disable_device_str = stringResource(R.string.app_lock_confirm_to_disable)
    val change_str = stringResource(R.string.app_lock_enter_to_change)
    val enable_biometric_str = stringResource(R.string.app_lock_enter_to_enable_biometric)
    val enable_biometric_device_str = stringResource(R.string.app_lock_confirm_to_enable_biometric)
    val settings_change_str = stringResource(R.string.app_lock_confirm_to_change_settings)
    val device_mode = mode == AppLockMode.DEVICE

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = settings_group_inset)) {
        settings_toggle_row(
            title = stringResource(R.string.app_lock_pin),
            subtitle = stringResource(
                if (!enabled) R.string.app_lock_off_description
                else if (device_mode) R.string.app_lock_status_screen_lock
                else R.string.app_lock_status_app_pin,
            ),
            checked = enabled,
            test_tag = "app_lock_toggle",
            on_change = { want ->
                if (want) {
                    if (!store.can_setup()) toast(setup_failed_str) else modal = AppLockSettingsModal.setup
                } else {
                    authenticate(if (device_mode) disable_device_str else disable_str) {
                        store.disable()
                        refresh()
                    }
                }
            },
        )
        if (enabled && !device_mode) {
            Text(
                text = stringResource(R.string.app_lock_change_pin),
                color = colors.accent_blue,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(horizontal = AsterSpacing.lg)
                    .clickable(role = Role.Button) {
                        verify_description = change_str
                        modal = AppLockSettingsModal.verify_to_change
                    }
                    .heightIn(min = 48.dp)
                    .padding(vertical = AsterSpacing.sm),
            )
        }
        if (enabled && biometric_available) {
            settings_row_gap()
            settings_toggle_row(
                title = stringResource(R.string.app_lock_use_biometric),
                subtitle = stringResource(R.string.app_lock_biometric_subtitle),
                checked = biometric_enabled,
                test_tag = "app_lock_biometric_toggle",
                on_change = { want ->
                    if (want) {
                        authenticate(if (device_mode) enable_biometric_device_str else enable_biometric_str) { enroll_biometric() }
                    } else {
                        authenticate(settings_change_str) {
                            BiometricUnlockGate.reset(context)
                            biometric_enabled = false
                        }
                    }
                },
            )
        }
        if (enabled) {
            settings_row_gap()
            settings_toggle_row(
                title = stringResource(R.string.app_lock_hide_notification_content),
                subtitle = stringResource(R.string.app_lock_hide_notification_content_desc),
                checked = hide_notifications,
                test_tag = "app_lock_hide_notifications_toggle",
                on_change = { want ->
                    if (want) {
                        store.set_notification_content_hidden(true)
                        hide_notifications = true
                    } else {
                        authenticate(settings_change_str) {
                            store.set_notification_content_hidden(false)
                            hide_notifications = false
                        }
                    }
                },
            )
            settings_row_gap()
            detail_row(
                title = stringResource(R.string.app_lock_timeout_title),
                value = stringResource(app_lock_timeout_label(timeout)),
                on_click = { timeout_expanded = !timeout_expanded },
            )
            if (timeout_expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppLockTimeout.entries.forEach { option ->
                        choice_option_row(
                            label = stringResource(app_lock_timeout_label(option)),
                            selected = option == timeout,
                            on_click = {
                                if (option != timeout) {
                                    authenticate(settings_change_str) {
                                        if (store.set_timeout(option)) timeout = option
                                        timeout_expanded = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    when (modal) {
        AppLockSettingsModal.setup, AppLockSettingsModal.change -> AppLockSetupSheet(
            store = store,
            on_dismiss = { modal = null; refresh() },
            on_success = { modal = null; refresh() },
            on_choose_device = if (modal == AppLockSettingsModal.setup && screen_lock_available) {
                { start_device_setup() }
            } else {
                null
            },
        )
        AppLockSettingsModal.verify_to_change -> AppLockVerifySheet(
            store = store,
            description = verify_description,
            on_dismiss = { modal = null },
            on_success = { modal = AppLockSettingsModal.change },
        )
        AppLockSettingsModal.verify -> AppLockVerifySheet(
            store = store,
            description = verify_description,
            on_dismiss = { modal = null; pending_action = null },
            on_success = {
                modal = null
                val action = pending_action
                pending_action = null
                action?.invoke()
            },
        )
        null -> {}
    }
}
