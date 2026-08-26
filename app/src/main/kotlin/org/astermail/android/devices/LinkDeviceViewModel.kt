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

package org.astermail.android.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.api.ApiError
import org.astermail.android.api.devices.DEVICE_CODE_LENGTH
import org.astermail.android.api.devices.DeviceCodeApi
import org.astermail.android.api.devices.DeviceLinkError
import org.astermail.android.api.devices.PendingDevice
import org.astermail.android.api.devices.format_device_code
import org.astermail.android.api.devices.normalize_device_code
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.zeroize
import org.astermail.android.storage.SessionKeyStore

enum class LinkDeviceStep { INPUT, CONFIRM, SUCCESS }

enum class LinkDeviceError {
    INVALID_CODE,
    EXPIRED_CODE,
    ALREADY_LINKED,
    UPGRADE_REQUIRED,
    RATE_LIMITED,
    SESSION_EXPIRED,
    UNAVAILABLE,
    FAILED,
}

data class LinkDeviceUiState(
    val step: LinkDeviceStep = LinkDeviceStep.INPUT,
    val code_input: String = "",
    val pending_device: PendingDevice? = null,
    val linked_device_name: String = "",
    val is_verifying: Boolean = false,
    val is_confirming: Boolean = false,
    val error: LinkDeviceError? = null,
) {
    val is_code_complete: Boolean
        get() = normalize_device_code(code_input).length == DEVICE_CODE_LENGTH
}

@HiltViewModel
class LinkDeviceViewModel @Inject constructor(
    private val device_code_api: DeviceCodeApi,
    private val session_key_store: SessionKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LinkDeviceUiState())
    val state: StateFlow<LinkDeviceUiState> = _state.asStateFlow()

    fun on_code_change(raw: String) {
        _state.value = _state.value.copy(
            code_input = format_device_code(raw),
            error = null,
        )
    }

    fun verify_code() {
        val current = _state.value
        if (current.is_verifying || current.is_confirming) return
        if (!current.is_code_complete) {
            _state.value = current.copy(error = LinkDeviceError.INVALID_CODE)
            return
        }

        _state.value = current.copy(is_verifying = true, error = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { device_code_api.verify_code(current.code_input) }
            }.onSuccess { device ->
                _state.value = _state.value.copy(
                    step = LinkDeviceStep.CONFIRM,
                    pending_device = device,
                    is_verifying = false,
                    error = null,
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    is_verifying = false,
                    error = classify(throwable),
                )
            }
        }
    }

    fun confirm_link() {
        val current = _state.value
        val device = current.pending_device ?: return
        if (current.is_confirming) return

        _state.value = current.copy(is_confirming = true, error = null)
        viewModelScope.launch {
            runCatching {
                val envelope = withContext(Dispatchers.Default) { seal_for(device) }
                withContext(Dispatchers.IO) {
                    device_code_api.confirm_code(current.code_input, envelope)
                }
            }.onSuccess { response ->
                _state.value = _state.value.copy(
                    step = LinkDeviceStep.SUCCESS,
                    linked_device_name = response.machine_name.ifBlank { device.machine_name },
                    pending_device = null,
                    code_input = "",
                    is_confirming = false,
                    error = null,
                )
            }.onFailure { throwable ->
                val error = classify(throwable)
                val restart = error == LinkDeviceError.EXPIRED_CODE ||
                    error == LinkDeviceError.INVALID_CODE
                _state.value = _state.value.copy(
                    step = if (restart) LinkDeviceStep.INPUT else LinkDeviceStep.CONFIRM,
                    pending_device = if (restart) null else _state.value.pending_device,
                    code_input = if (restart) "" else _state.value.code_input,
                    is_confirming = false,
                    error = error,
                )
            }
        }
    }

    fun cancel_confirm() {
        _state.value = LinkDeviceUiState()
    }

    fun start_over() {
        _state.value = LinkDeviceUiState()
    }

    fun clear_error() {
        _state.value = _state.value.copy(error = null)
    }

    private fun seal_for(device: PendingDevice): String {
        val passphrase = session_key_store.get_passphrase() ?: throw SessionExpiredException()

        try {
            val mlkem_pk = DeviceEnvelope.base64url_decode(device.mlkem_pk)
            val x25519_pk = DeviceEnvelope.base64url_decode(device.x25519_pk)
            val ed25519_pk = DeviceEnvelope.base64url_decode(device.ed25519_pk)

            if (ed25519_pk.size != DeviceEnvelope.ED25519_PK_BYTES) {
                throw DeviceEnvelope.InvalidDeviceKeyException("bad ed25519 key")
            }

            return DeviceEnvelope.base64url_encode(
                DeviceEnvelope.seal_secret_for_device(
                    secret = passphrase,
                    device_mlkem_pk = mlkem_pk,
                    device_x25519_pk = x25519_pk,
                ),
            )
        } finally {
            zeroize(passphrase)
        }
    }

    private fun classify(throwable: Throwable): LinkDeviceError = when (throwable) {
        is SessionExpiredException -> LinkDeviceError.SESSION_EXPIRED
        is DeviceLinkError.CodeNotFound -> LinkDeviceError.EXPIRED_CODE
        is DeviceLinkError.AlreadyLinked -> LinkDeviceError.ALREADY_LINKED
        is DeviceLinkError.PlanUpgradeRequired -> LinkDeviceError.UPGRADE_REQUIRED
        is DeviceLinkError.ServiceUnavailable -> LinkDeviceError.UNAVAILABLE
        is DeviceEnvelope.InvalidDeviceKeyException -> LinkDeviceError.FAILED
        is ApiError.NotFoundError -> LinkDeviceError.EXPIRED_CODE
        is ApiError.Conflict -> LinkDeviceError.ALREADY_LINKED
        is ApiError.RateLimited -> LinkDeviceError.RATE_LIMITED
        is ApiError.InvalidCredentials, is ApiError.UnauthorizedError -> LinkDeviceError.SESSION_EXPIRED
        is ApiError.ValidationError -> LinkDeviceError.INVALID_CODE
        else -> LinkDeviceError.FAILED
    }

    private class SessionExpiredException : Exception("vault locked")
}
