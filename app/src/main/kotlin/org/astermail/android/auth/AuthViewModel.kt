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

package org.astermail.android.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.CAPTCHA_FAILED_CODE
import org.astermail.android.api.recovery_email.RecoveryEmailApiImpl
import org.astermail.android.api.recovery_email.RecoveryEmailError

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(
        val message: String,
        val restart_login: Boolean = false,
        val username_taken: Boolean = false,
        val passkey_settings: Boolean = false,
    ) : AuthUiState
    data object AccountSuspended : AuthUiState
    data object Success : AuthUiState
    data class TotpChallenge(val challenge: org.astermail.android.auth.TotpChallenge) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val repository: AuthRepository,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _ui_state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val ui_state: StateFlow<AuthUiState> = _ui_state.asStateFlow()

    private val _is_checking_username = MutableStateFlow(false)
    val is_checking_username: StateFlow<Boolean> = _is_checking_username.asStateFlow()

    private val _login_captcha_required = MutableStateFlow(false)
    val login_captcha_required: StateFlow<Boolean> = _login_captcha_required.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { PlayIntegrityLogin.warm_up(ctx) }
    }

    private val _recovery_codes = MutableStateFlow<List<String>?>(null)
    val recovery_codes: StateFlow<List<String>?> = _recovery_codes.asStateFlow()

    private val _recovery_backup_failed = MutableStateFlow(false)
    val recovery_backup_failed: StateFlow<Boolean> = _recovery_backup_failed.asStateFlow()

    private val _is_retrying_recovery_backup = MutableStateFlow(false)
    val is_retrying_recovery_backup: StateFlow<Boolean> = _is_retrying_recovery_backup.asStateFlow()

    private val _recovery_email_error = MutableStateFlow<String?>(null)
    val recovery_email_error: StateFlow<String?> = _recovery_email_error.asStateFlow()

    private val _is_saving_recovery_email = MutableStateFlow(false)
    val is_saving_recovery_email: StateFlow<Boolean> = _is_saving_recovery_email.asStateFlow()

    val is_signed_in: StateFlow<Boolean> = repository.is_signed_in

    fun submit_login(
        email: String,
        password: String,
        captcha_token: String? = null,
        remember_me: Boolean = true,
    ) {
        if (_ui_state.value == AuthUiState.Loading) return
        if (!is_supported_sign_in_domain(email)) {
            _ui_state.value = AuthUiState.Error(
                ctx.getString(R.string.error_sign_in_domain_unsupported),
            )
            return
        }
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                kotlinx.coroutines.withTimeout(25_000L) {
                    repository.login(email, password, captcha_token, remember_me).getOrThrow()
                }
            }
            _ui_state.value = result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is LoginOutcome.Success -> AuthUiState.Success
                        is LoginOutcome.NeedsTotp -> AuthUiState.TotpChallenge(outcome.challenge)
                    }
                },
                onFailure = { cause ->
                    if (is_captcha_failure(cause)) {
                        _login_captcha_required.value = true
                        if (captcha_token == null) AuthUiState.Idle else failure_state(cause)
                    } else {
                        failure_state(cause)
                    }
                },
            )
        }
    }

    private fun is_captcha_failure(cause: Throwable): Boolean =
        cause is ApiError.ForbiddenError && cause.code == CAPTCHA_FAILED_CODE

    fun submit_totp(
        code: String,
        challenge: org.astermail.android.auth.TotpChallenge,
        trust_device: Boolean = false,
        use_backup_code: Boolean = false,
    ) {
        if (_ui_state.value == AuthUiState.Loading) return
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                kotlinx.coroutines.withTimeout(25_000L) {
                    repository.verify_totp(code, challenge, trust_device, use_backup_code).getOrThrow()
                }
            }
            _ui_state.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { second_factor_failure_state(it, challenge) },
            )
        }
    }

    fun submit_passkey(
        challenge: org.astermail.android.auth.TotpChallenge,
        trust_device: Boolean = false,
        on_unavailable: (() -> Unit)? = null,
        get_assertion: suspend (org.astermail.android.api.auth.WebAuthnAssertionOptions) -> String,
    ) {
        if (_ui_state.value == AuthUiState.Loading) return
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = runCatching {
                val options = repository.begin_webauthn(challenge).getOrThrow()
                val response_json = get_assertion(options)
                val request = assertion_verify_request(
                    response_json = response_json,
                    options = options,
                    challenge = challenge,
                    trust_device = trust_device,
                    device_label = repository.login_device_label(),
                )
                repository.verify_webauthn(request, challenge, trust_device).getOrThrow()
            }
            if (result.exceptionOrNull() is PasskeyUnavailableException && on_unavailable != null) {
                _ui_state.value = AuthUiState.Idle
                on_unavailable()
                return@launch
            }
            _ui_state.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { cause ->
                    when (cause) {
                        is PasskeyCancelledException -> AuthUiState.Idle
                        is PasskeyUnavailableException ->
                            AuthUiState.Error(
                                passkey_message(passkey_unavailable_message(cause, challenge), cause),
                                passkey_settings = org.astermail.android.auth.passkey_settings_supported(),
                            )
                        is PasskeyFailedException ->
                            AuthUiState.Error(passkey_message(R.string.error_passkey_failed, cause))
                        else ->
                            if (is_passkey_challenge_expired(cause)) {
                                AuthUiState.Error(ctx.getString(R.string.error_passkey_timed_out))
                            } else {
                                second_factor_failure_state(cause, challenge)
                            }
                    }
                },
            )
        }
    }

    fun submit_security_key(
        challenge: org.astermail.android.auth.TotpChallenge,
        trust_device: Boolean = false,
        run: suspend (
            prepare: suspend () -> org.astermail.android.api.auth.WebAuthnAssertionOptions,
            origin: String,
        ) -> SecurityKeyAssertion,
    ) {
        if (_ui_state.value == AuthUiState.Loading) return
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = runCatching {
                val origin = security_key_origin(ctx, repository.webauthn_origin())
                val assertion = run({ repository.begin_webauthn(challenge).getOrThrow() }, origin)
                val request = assertion_verify_request(
                    response_json = assertion.response_json,
                    options = assertion.options,
                    challenge = challenge,
                    trust_device = trust_device,
                    device_label = repository.login_device_label(),
                )
                repository.verify_webauthn(request, challenge, trust_device).getOrThrow()
            }
            _ui_state.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { cause -> security_key_failure_state(cause, challenge) },
            )
        }
    }

    private fun security_key_failure_state(
        cause: Throwable,
        challenge: org.astermail.android.auth.TotpChallenge,
    ): AuthUiState {
        if (cause is PasskeyCancelledException) return AuthUiState.Idle
        val message_res = security_key_error_message(cause)
        if (message_res != null) return AuthUiState.Error(ctx.getString(message_res))
        if (is_passkey_challenge_expired(cause)) {
            return AuthUiState.Error(ctx.getString(R.string.security_key_error_timed_out))
        }
        return second_factor_failure_state(cause, challenge)
    }

    private fun passkey_unavailable_message(
        cause: PasskeyUnavailableException,
        challenge: org.astermail.android.auth.TotpChallenge,
    ): Int {
        val totp = challenge.available_methods.isEmpty() || challenge.available_methods.contains("totp")
        return when {
            cause.no_provider && totp -> R.string.error_passkey_no_provider
            cause.no_provider -> R.string.error_passkey_no_provider_no_totp
            totp -> R.string.error_passkey_unavailable
            else -> R.string.error_passkey_unavailable_no_totp
        }
    }

    private fun passkey_message(message_res: Int, cause: Throwable): String {
        val message = ctx.getString(message_res)
        val code = org.astermail.android.auth.passkey_error_code(cause) ?: return message
        return ctx.getString(R.string.passkey_error_with_code, message, code)
    }

    private fun second_factor_failure_state(
        cause: Throwable,
        challenge: org.astermail.android.auth.TotpChallenge,
    ): AuthUiState {
        if (is_pending_login_expired(cause) && !repository.is_signed_in.value) {
            challenge.password_bytes.fill(0)
            challenge.password_hash_bytes.fill(0)
            return AuthUiState.Error(map_error(cause), restart_login = true)
        }
        return failure_state(cause)
    }

    private fun is_pending_login_expired(cause: Throwable): Boolean =
        server_error_code(cause) == org.astermail.android.PENDING_LOGIN_EXPIRED_CODE

    private fun server_error_code(cause: Throwable): String? = when (cause) {
        is ApiError.ValidationError -> cause.code
        is ApiError.ForbiddenError -> cause.code
        else -> null
    }

    private fun failure_state(cause: Throwable): AuthUiState {
        if (cause is kotlinx.coroutines.TimeoutCancellationException && repository.is_signed_in.value) {
            return AuthUiState.Success
        }
        if (cause is ApiError.ForbiddenError && cause.code == org.astermail.android.api.ACCOUNT_SUSPENDED_CODE) {
            return AuthUiState.AccountSuspended
        }
        return AuthUiState.Error(map_error(cause))
    }

    fun check_username_availability(username: String, domain: String, on_available: () -> Unit) {
        if (_is_checking_username.value) return
        _is_checking_username.value = true
        _ui_state.value = AuthUiState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.check_username_availability(username.trim().trim('.'), domain)
            val failure = result.exceptionOrNull()
            val taken = result.getOrNull() == false ||
                (failure != null && org.astermail.android.is_username_taken(failure))
            _is_checking_username.value = false
            when {
                taken -> _ui_state.value = AuthUiState.Error(
                    message = ctx.getString(R.string.error_username_taken),
                    username_taken = true,
                )
                failure is ApiError.RateLimited -> _ui_state.value = AuthUiState.Error(
                    message = map_error(failure),
                    username_taken = true,
                )
                else -> withContext(Dispatchers.Main) { on_available() }
            }
        }
    }

    fun submit_register(
        email: String,
        password: String,
        confirm_password: String,
        captcha_token: String? = null,
        remember_me: Boolean = true,
        display_name: String? = null,
    ) {
        if (_ui_state.value == AuthUiState.Loading) return
        val trimmed = email.trim()
        if (!is_valid_email(trimmed)) {
            _ui_state.value = AuthUiState.Error(ctx.getString(R.string.error_invalid_email))
            return
        }
        if (password.length < 12) {
            _ui_state.value = AuthUiState.Error(ctx.getString(R.string.error_password_min_length))
            return
        }
        if (password != confirm_password) {
            _ui_state.value = AuthUiState.Error(ctx.getString(R.string.error_passwords_no_match))
            return
        }
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.register(trimmed, password, captcha_token, remember_me, display_name)
            result.fold(
                onSuccess = { success ->
                    _recovery_codes.value = success.recovery_codes
                    _recovery_backup_failed.value = !success.recovery_backup_saved
                    _ui_state.value = AuthUiState.Success
                },
                onFailure = { t ->
                    _ui_state.value = AuthUiState.Error(
                        message = map_error(t),
                        username_taken = org.astermail.android.is_username_taken(t),
                    )
                },
            )
        }
    }

    fun consume_recovery_codes() {
        _recovery_codes.value = null
    }

    fun retry_recovery_backup() {
        if (_is_retrying_recovery_backup.value) return
        _is_retrying_recovery_backup.value = true
        viewModelScope.launch {
            val saved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { repository.retry_recovery_backup() }.getOrDefault(false)
            }
            _recovery_backup_failed.value = !saved
            _is_retrying_recovery_backup.value = false
        }
    }

    fun save_recovery_email(email: String, on_saved: () -> Unit) {
        if (_is_saving_recovery_email.value) return
        _recovery_email_error.value = null
        _is_saving_recovery_email.value = true
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.save_recovery_email(email)
            }
            _is_saving_recovery_email.value = false
            result.fold(
                onSuccess = { on_saved() },
                onFailure = { t -> _recovery_email_error.value = recovery_email_error_message(t) },
            )
        }
    }

    fun clear_recovery_email_error() {
        _recovery_email_error.value = null
    }

    private fun recovery_email_error_message(t: Throwable): String {
        (t as? RecoveryEmailError)?.let { error ->
            return when (error.code) {
                RecoveryEmailApiImpl.STEP_UP_REQUIRED ->
                    ctx.getString(R.string.recovery_step_up_description)
                RecoveryEmailApiImpl.TOTP_REQUIRED ->
                    ctx.getString(R.string.totp_code_required_error)
                RecoveryEmailApiImpl.INVALID_INPUT ->
                    ctx.getString(R.string.error_invalid_email)
                RecoveryEmailApiImpl.EMAIL_SEND_FAILED ->
                    ctx.getString(R.string.error_send_recovery)
                else -> map_error(t)
            }
        }
        return when ((t as? ApiError.UnknownError)?.detail) {
            RecoveryEmailApiImpl.RECOVERY_EMAIL_IN_USE ->
                ctx.getString(R.string.recovery_email_already_in_use)
            RecoveryEmailApiImpl.RECOVERY_EMAIL_COOLDOWN ->
                ctx.getString(R.string.recovery_email_resend_cooldown)
            else -> map_error(t)
        }
    }

    fun reset_state() {
        _ui_state.value = AuthUiState.Idle
    }

    fun cancel_totp(challenge: org.astermail.android.auth.TotpChallenge): Boolean {
        if (_ui_state.value == AuthUiState.Loading) return false
        challenge.password_bytes.fill(0)
        challenge.password_hash_bytes.fill(0)
        _ui_state.value = AuthUiState.Idle
        return true
    }

    private fun is_supported_sign_in_domain(email: String): Boolean {
        val at_index = email.indexOf('@')

        if (at_index == -1) return true

        val domain = email.substring(at_index + 1).lowercase(java.util.Locale.ROOT).trimEnd('.')

        return domain == "astermail.org" || domain.endsWith(".astermail.org") ||
            domain == "aster.cx" || domain.endsWith(".aster.cx")
    }
    private fun is_valid_email(email: String): Boolean {
        val at = email.indexOf('@')
        if (at <= 0 || at == email.length - 1) return false
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        if (local.isBlank() || domain.isBlank()) return false
        return domain.contains('.')
    }

    private fun map_error(t: Throwable): String = when (t) {
        is LegacyLoginSaltException -> org.astermail.android.legacy_login_salt_text(ctx, t)
            ?: ctx.getString(R.string.error_legacy_login_salt_new_device)
        is org.astermail.android.crypto.AuthSaltCollisionException -> ctx.getString(R.string.error_auth_salt_collision)
        is AccountLimitReachedException -> t.max_accounts?.takeIf { it > 0 }
            ?.let { ctx.getString(R.string.error_account_limit_reached_count, it) }
            ?: ctx.getString(R.string.error_account_limit_reached)
        is ApiError.PlanLimitExceeded -> ctx.getString(R.string.error_plan_limit_reached)
        is ApiError.InvalidCredentials, is ApiError.UnauthorizedError -> ctx.getString(R.string.error_invalid_credentials)
        is ApiError.ForbiddenError -> if (t.detail.contains("captcha", ignoreCase = true)) {
            ctx.getString(R.string.error_captcha_failed)
        } else {
            ctx.getString(R.string.error_access_denied)
        }
        is ApiError.NotFoundError -> ctx.getString(R.string.error_account_not_found)
        is ApiError.NetworkError -> ctx.getString(R.string.error_no_connection)
        is ApiError.ServerError -> ctx.getString(R.string.error_server)
        is ApiError.ValidationError ->
            org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.error_invalid_request))
        is ApiError.RateLimited -> ctx.getString(R.string.error_too_many_attempts)
        is ApiError.UpgradeRequired -> ctx.getString(R.string.error_client_upgrade_required)
        is ApiError.Conflict ->
            org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.error_generic))
        is ApiError.UnknownError -> ctx.getString(R.string.error_generic)
        is java.net.UnknownHostException -> ctx.getString(R.string.error_no_connection)
        is java.net.ConnectException -> ctx.getString(R.string.error_no_connection)
        is java.net.SocketTimeoutException -> ctx.getString(R.string.error_timeout)
        is kotlinx.coroutines.TimeoutCancellationException -> ctx.getString(R.string.error_timeout)
        is javax.net.ssl.SSLException -> ctx.getString(R.string.error_ssl)
        else -> ctx.getString(R.string.error_generic)
    }
}
