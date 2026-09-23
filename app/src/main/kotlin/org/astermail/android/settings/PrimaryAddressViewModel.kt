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

package org.astermail.android.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.account.PrimaryAddressApi
import org.astermail.android.api.account.PrimaryAddressAvailabilityRequest
import org.astermail.android.api.account.PrimaryAddressConfirmRequest
import org.astermail.android.api.account.PrimaryAddressStartRequest
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.localized_api_error
import org.astermail.android.storage.SessionKeyStore

val primary_address_domains = listOf("astermail.org", "aster.cx")

const val primary_address_code_length = 6

private const val availability_debounce_ms = 400L

private const val resend_cooldown_seconds = 60
private const val republish_timeout_ms = 30000L

private fun address_ignoring_dots(address: String): String {
    val at = address.lastIndexOf('@')
    if (at <= 0) return address.lowercase(Locale.ROOT)
    val local = address.substring(0, at).replace(".", "").lowercase(Locale.ROOT)
    val domain = address.substring(at + 1).lowercase(Locale.ROOT)
    return "$local@$domain"
}

fun primary_local_part_valid(local_part: String): Boolean {
    val stripped = local_part.replace(".", "")
    return local_part.length <= 64 &&
        local_part.matches(Regex("^[a-z0-9.]+$")) &&
        !local_part.startsWith(".") &&
        !local_part.endsWith(".") &&
        !local_part.contains("..") &&
        stripped.length in 3..40
}

enum class PrimaryAddressStep { INTRO, PICK, REVIEW, PASSWORD, CODE, DONE }

const val primary_address_reason_account_kind = "account_kind"

const val primary_address_reason_plan = "plan"

const val primary_address_reason_cooldown = "cooldown"

const val primary_address_reason_custom_domain = "custom_domain"

data class PrimaryAddressUiState(
    val step: PrimaryAddressStep = PrimaryAddressStep.INTRO,
    val current_address: String = "",
    val eligible: Boolean = false,
    val lock_reason: String? = null,
    val next_change_available_at: String? = null,
    val renames_allowed_per_year: Int = 0,
    val eligibility_failed: Boolean = false,
    val local_part: String = "",
    val domain: String = primary_address_domains.first(),
    val checking: Boolean = false,
    val is_available: Boolean? = null,
    val availability_check_failed: Boolean = false,
    val confirm_text: String = "",
    val password: String = "",
    val show_password: Boolean = false,
    val code: String = "",
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val final_address: String = "",
    val retained_address: String = "",
    val resend_seconds: Int = 0,
    val code_locked: Boolean = false,
) {
    val new_address: String
        get() = "$local_part@$domain"

    val local_part_stripped: String
        get() = local_part.replace(".", "")

    val local_part_valid: Boolean
        get() = primary_local_part_valid(local_part)

    val same_as_current: Boolean
        get() = current_address.isNotEmpty() &&
            address_ignoring_dots(new_address) == address_ignoring_dots(current_address)

    val eligibility_loaded: Boolean
        get() = current_address.isNotEmpty()

    val can_continue_from_pick: Boolean
        get() = local_part_valid && is_available == true && !checking && !same_as_current

    val can_continue_from_review: Boolean
        get() = confirm_text.trim().equals(new_address, ignoreCase = true)

    val can_submit_code: Boolean
        get() = code.length == primary_address_code_length && !busy && !code_locked

    val can_resend_code: Boolean
        get() = !busy && resend_seconds <= 0 && !code_locked
}

@HiltViewModel
class PrimaryAddressViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val primary_address_api: PrimaryAddressApi,
    private val auth_repository: AuthRepository,
    private val session_key_store: SessionKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PrimaryAddressUiState())
    val state = _state.asStateFlow()

    private var availability_job: Job? = null
    private var resend_job: Job? = null

    fun load_eligibility() {
        viewModelScope.launch {
            runCatching { primary_address_api.get_eligibility() }
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        eligible = response.eligible,
                        lock_reason = response.reason,
                        current_address = response.current_address,
                        next_change_available_at = response.next_change_available_at,
                        renames_allowed_per_year = response.renames_allowed_per_year,
                        eligibility_failed = false,
                    )
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _state.value = _state.value.copy(
                        eligible = false,
                        eligibility_failed = throwable !is ApiError.NotFoundError,
                    )
                }
        }
    }

    fun reset() {
        availability_job?.cancel()
        availability_job = null
        resend_job?.cancel()
        resend_job = null
        val kept = _state.value
        _state.value = PrimaryAddressUiState(
            current_address = kept.current_address,
            eligible = kept.eligible,
            lock_reason = kept.lock_reason,
            next_change_available_at = kept.next_change_available_at,
            renames_allowed_per_year = kept.renames_allowed_per_year,
            eligibility_failed = kept.eligibility_failed,
        )
    }

    fun go_to_pick() {
        if (!_state.value.eligible) return
        _state.value = _state.value.copy(step = PrimaryAddressStep.PICK, error = null)
    }

    fun go_to_review() {
        if (!_state.value.can_continue_from_pick) return
        _state.value = _state.value.copy(
            step = PrimaryAddressStep.REVIEW,
            confirm_text = "",
            error = null,
        )
    }

    fun go_to_password() {
        if (!_state.value.can_continue_from_review) return
        _state.value = _state.value.copy(step = PrimaryAddressStep.PASSWORD, error = null)
    }

    fun back_to_intro() {
        _state.value = _state.value.copy(
            step = PrimaryAddressStep.INTRO,
            password = "",
            show_password = false,
            error = null,
        )
    }

    fun back_to_pick() {
        _state.value = _state.value.copy(
            step = PrimaryAddressStep.PICK,
            password = "",
            show_password = false,
            error = null,
        )
    }

    fun back_to_review() {
        _state.value = _state.value.copy(
            step = PrimaryAddressStep.REVIEW,
            password = "",
            show_password = false,
            error = null,
        )
    }

    fun set_local_part(value: String) {
        _state.value = _state.value.copy(
            local_part = value.trim().lowercase(Locale.ROOT),
            is_available = null,
            availability_check_failed = false,
            checking = false,
            error = null,
        )
        schedule_availability_check()
    }

    fun set_domain(value: String) {
        _state.value = _state.value.copy(
            domain = value,
            is_available = null,
            availability_check_failed = false,
            checking = false,
            error = null,
        )
        schedule_availability_check()
    }

    fun use_alias(address: String) {
        val at = address.lastIndexOf('@')
        if (at <= 0 || at == address.length - 1) return
        _state.value = _state.value.copy(
            local_part = address.substring(0, at).lowercase(Locale.ROOT),
            domain = address.substring(at + 1).lowercase(Locale.ROOT),
            is_available = null,
            availability_check_failed = false,
            checking = false,
            error = null,
        )
        schedule_availability_check()
    }

    fun set_confirm_text(value: String) {
        _state.value = _state.value.copy(confirm_text = value, error = null)
    }

    fun set_password(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun toggle_show_password() {
        _state.value = _state.value.copy(show_password = !_state.value.show_password)
    }

    fun set_code(value: String) {
        _state.value = _state.value.copy(
            code = value.filter { it.isDigit() }.take(primary_address_code_length),
            error = null,
        )
    }

    private fun schedule_availability_check() {
        availability_job?.cancel()
        val snapshot = _state.value
        if (!snapshot.local_part_valid || snapshot.same_as_current) return

        val local_part = snapshot.local_part
        val domain = snapshot.domain

        availability_job = viewModelScope.launch {
            delay(availability_debounce_ms)
            _state.value = _state.value.copy(checking = true, availability_check_failed = false)

            val probe = try {
                Result.success(
                    primary_address_api.check_availability(
                        PrimaryAddressAvailabilityRequest(
                            local_part = local_part,
                            domain = domain,
                        ),
                    ).available,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Result.failure(failure)
            }

            val latest = _state.value
            if (latest.local_part != local_part || latest.domain != domain) return@launch
            _state.value = latest.copy(
                checking = false,
                is_available = probe.getOrNull(),
                availability_check_failed = probe.isFailure,
            )
        }
    }

    fun start_change() {
        val snapshot = _state.value
        if (snapshot.busy || snapshot.password.isBlank()) return

        _state.value = snapshot.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                val password_hash = withContext(Dispatchers.Default) {
                    auth_repository.derive_password_hash_b64(snapshot.password)
                }
                    ?: throw IllegalStateException("password_hash_unavailable")
                primary_address_api.start(
                    PrimaryAddressStartRequest(
                        new_local_part = snapshot.local_part,
                        new_domain = snapshot.domain,
                        password_hash = password_hash,
                    ),
                )
            }

            result.onSuccess {
                _state.value = _state.value.copy(
                    busy = false,
                    password = "",
                    code = "",
                    code_locked = false,
                    step = PrimaryAddressStep.CODE,
                )
                start_resend_countdown()
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                _state.value = _state.value.copy(
                    busy = false,
                    password = "",
                    error = request_error_message(throwable, resend = false),
                )
                if (throwable is ApiError.ForbiddenError ||
                    throwable is ApiError.PlanLimitExceeded
                ) {
                    load_eligibility()
                }
            }
        }
    }

    private fun request_error_message(throwable: Throwable, resend: Boolean): String = when (throwable) {
        is ApiError.InvalidCredentials ->
            context.getString(R.string.address_change_password_wrong)
        is ApiError.PlanLimitExceeded ->
            context.getString(R.string.address_change_locked_plan)
        is ApiError.ForbiddenError ->
            context.getString(R.string.address_change_not_available)
        is ApiError.Conflict ->
            context.getString(R.string.address_change_taken_now)
        is ApiError.RateLimited -> if (resend) {
            context.getString(R.string.address_change_resend_too_soon)
        } else {
            context.getString(R.string.address_change_too_many_requests)
        }
        is ApiError.NotFoundError ->
            context.getString(R.string.address_change_code_expired)
        is ApiError.ValidationError ->
            context.getString(R.string.address_change_invalid_address)
        is ApiError.ServerError -> if (throwable.code == 503) {
            context.getString(R.string.address_change_send_failed)
        } else {
            context.getString(R.string.address_change_failed)
        }
        else -> localized_api_error(
            context,
            throwable,
            context.getString(R.string.address_change_failed),
        )
    }

    private fun start_resend_countdown() {
        resend_job?.cancel()
        _state.value = _state.value.copy(resend_seconds = resend_cooldown_seconds)
        resend_job = viewModelScope.launch {
            while (_state.value.resend_seconds > 0) {
                delay(1000L)
                _state.value = _state.value.copy(
                    resend_seconds = (_state.value.resend_seconds - 1).coerceAtLeast(0),
                )
            }
        }
    }

    fun resend_code() {
        val snapshot = _state.value
        if (!snapshot.can_resend_code) return

        _state.value = snapshot.copy(busy = true, error = null, status = null)
        viewModelScope.launch {
            runCatching { primary_address_api.resend() }
                .onSuccess {
                    _state.value = _state.value.copy(
                        busy = false,
                        code = "",
                        status = context.getString(R.string.address_change_code_resent),
                    )
                    start_resend_countdown()
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    _state.value = _state.value.copy(
                        busy = false,
                        error = request_error_message(throwable, resend = true),
                    )
                }
        }
    }

    fun confirm_change(display_name: String, on_changed: (String) -> Unit) {
        val snapshot = _state.value
        if (!snapshot.can_submit_code) return

        val at = snapshot.current_address.trim().lastIndexOf('@')
        if (at <= 0 || at == snapshot.current_address.trim().length - 1) {
            _state.value = snapshot.copy(
                error = context.getString(R.string.address_change_failed),
            )
            return
        }
        val normalized_current = snapshot.current_address.trim().lowercase(Locale.ROOT)
        val retained_local_part = normalized_current.substring(0, at)
        val retained_domain = normalized_current.substring(at + 1)

        _state.value = snapshot.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                val (encrypted_local_part, local_part_nonce) =
                    encrypt_alias_field_with(session_key_store, retained_local_part)

                primary_address_api.confirm(
                    PrimaryAddressConfirmRequest(
                        code = snapshot.code,
                        new_user_hash = CryptoNative.hash_email(snapshot.new_address),
                        retained_encrypted_local_part = encrypted_local_part,
                        retained_local_part_nonce = local_part_nonce,
                        retained_alias_address_hash = compute_alias_address_hash_with(
                            session_key_store,
                            retained_local_part,
                            retained_domain,
                        ),
                        retained_routing_address_hash = compute_routing_address_hash_for(
                            retained_local_part,
                            retained_domain,
                        ),
                    ),
                )
            }

            result.onSuccess { response ->
                _state.value = _state.value.copy(
                    code = "",
                    status = context.getString(R.string.address_change_updating_key),
                )
                val follow_up = runCatching {
                    withTimeout(republish_timeout_ms) {
                        session_key_store.put_user_email(response.new_address)
                        val republished = auth_repository.add_address_to_identity_key(
                            response.new_address,
                            display_name,
                        )
                        auth_repository.refresh_profile()
                        republished
                    }
                }
                follow_up.exceptionOrNull()?.let { throwable ->
                    if (throwable is CancellationException) throw throwable
                }
                _state.value = _state.value.copy(
                    busy = false,
                    status = if (follow_up.getOrDefault(false)) {
                        null
                    } else {
                        context.getString(R.string.address_change_done_partial)
                    },
                    eligible = false,
                    next_change_available_at = response.next_change_available_at
                        ?: _state.value.next_change_available_at,
                    final_address = response.new_address,
                    retained_address = snapshot.current_address,
                    step = PrimaryAddressStep.DONE,
                )
                on_changed(response.new_address)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                _state.value = _state.value.copy(
                    busy = false,
                    status = null,
                    code_locked = throwable is ApiError.RateLimited,
                    error = confirm_error_message(throwable),
                )
            }
        }
    }

    private fun confirm_error_message(throwable: Throwable): String = when (throwable) {
        is ApiError.InvalidCredentials ->
            context.getString(R.string.address_change_code_invalid)
        is ApiError.RateLimited ->
            context.getString(R.string.address_change_code_too_many)
        is ApiError.NotFoundError ->
            context.getString(R.string.address_change_code_expired)
        is ApiError.Conflict ->
            context.getString(R.string.address_change_taken_now)
        is ApiError.PlanLimitExceeded ->
            context.getString(R.string.address_change_locked_plan)
        is ApiError.ForbiddenError ->
            context.getString(R.string.address_change_not_available)
        is ApiError.ValidationError ->
            context.getString(R.string.address_change_invalid_address)
        else -> localized_api_error(
            context,
            throwable,
            context.getString(R.string.address_change_failed),
        )
    }
}
