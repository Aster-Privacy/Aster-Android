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
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256
import org.astermail.android.crypto.PasswordKdf
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.recovery.CompleteRecoveryRequest
import org.astermail.android.api.recovery.InitiateEmailRecoveryRequest
import org.astermail.android.api.recovery.InitiateRecoveryRequest
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.api.recovery.RecoveryShareData
import org.astermail.android.crypto.CryptoNative

enum class RecoveryStep {
    email,
    email_sent,
    code,
    password,
    processing,
    new_codes,
    success,
}

data class RecoveryUiState(
    val step: RecoveryStep = RecoveryStep.email,
    val is_loading: Boolean = false,
    val error: String? = null,
    val processing_status: String = "",
    val new_codes: List<String> = emptyList(),
)

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    application: Application,
    private val recovery_api: RecoveryApi,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(RecoveryUiState())
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    internal var compute_dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default

    private var recovery_token: String? = null
    private var decrypted_vault: ByteArray? = null
    private var user_email: String = ""

    fun send_recovery_email(email: String) {
        if (_state.value.is_loading) return
        user_email = email.trim().lowercase(java.util.Locale.ROOT)
        val at_index = user_email.indexOf('@')

        if (at_index <= 0) {
            _state.value = _state.value.copy(
                error = ctx.getString(R.string.error_enter_full_email_address),
            )
            return
        }

        val username = user_email.substring(0, at_index)
        val email_domain = user_email.substring(at_index + 1).trimEnd('.')

        if (!is_supported_recovery_domain(email_domain)) {
            _state.value = _state.value.copy(
                error = ctx.getString(R.string.error_sign_in_domain_unsupported),
            )
            return
        }

        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            try {
                recovery_api.initiate_email(
                    InitiateEmailRecoveryRequest(
                        username = username,
                        email_domain = email_domain,
                    ),
                )
                _state.value = _state.value.copy(
                    step = RecoveryStep.email_sent,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = map_error(t, R.string.error_send_recovery),
                )
            }
        }
    }

    private fun is_supported_recovery_domain(domain: String): Boolean {
        return domain == "astermail.org" || domain.endsWith(".astermail.org") ||
            domain == "aster.cx" || domain.endsWith(".aster.cx")
    }

    fun go_to_code_step() {
        _state.value = _state.value.copy(step = RecoveryStep.code, error = null)
    }

    fun verify_code(code: String) {
        if (_state.value.is_loading) return
        val normalized = code.trim().uppercase(java.util.Locale.ROOT)
        if (!normalized.startsWith("ASTER-") || normalized.length != 20) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.error_invalid_recovery_code))
            return
        }
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            try {
                val code_hash = withContext(compute_dispatcher) {
                    hash_recovery_code(normalized)
                }
                val response = recovery_api.initiate(
                    InitiateRecoveryRequest(code_hash = code_hash, email = user_email),
                )
                recovery_token = response.recovery_token

                withContext(compute_dispatcher) {
                    val recovery_key = decrypt_recovery_key_with_code(
                        normalized,
                        base64_decode(response.encrypted_recovery_key),
                        base64_decode(response.recovery_key_nonce),
                        base64_decode(response.code_salt),
                    )
                    decrypted_vault = decrypt_vault_backup(
                        base64_decode(response.encrypted_vault_backup),
                        base64_decode(response.vault_backup_nonce),
                        base64_decode(response.recovery_key_salt),
                        recovery_key,
                    )
                    recovery_key.fill(0)
                }

                _state.value = _state.value.copy(
                    step = RecoveryStep.password,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = map_error(t, R.string.error_invalid_code),
                )
            }
        }
    }

    fun submit_new_password(password: String, confirm: String) {
        if (_state.value.is_loading) return
        if (password.length < 12) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.error_password_min_length))
            return
        }
        if (password != confirm) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.error_passwords_no_match))
            return
        }
        val token = recovery_token
        val vault_bytes = decrypted_vault
        if (token == null || vault_bytes == null) {
            _state.value = _state.value.copy(
                step = RecoveryStep.code,
                is_loading = false,
                error = ctx.getString(R.string.error_recovery_failed),
                processing_status = "",
            )
            return
        }

        _state.value = _state.value.copy(
            step = RecoveryStep.processing,
            is_loading = true,
            error = null,
            processing_status = ctx.getString(R.string.status_deriving_key),
        )

        viewModelScope.launch {
            val password_bytes = password.toByteArray(Charsets.UTF_8)
            try {
                val new_salt = ByteArray(32).also { SecureRandom().nextBytes(it) }

                _state.value = _state.value.copy(processing_status = ctx.getString(R.string.status_encrypting_vault))
                val new_password_hash = withContext(compute_dispatcher) {
                    CryptoNative.derive_pbkdf2_hash(password_bytes, new_salt, PBKDF2_ITERATIONS)
                }

                val new_envelope = withContext(compute_dispatcher) {
                    CryptoNative.encrypt_vault_with_password(vault_bytes, password_bytes)
                }

                _state.value = _state.value.copy(processing_status = ctx.getString(R.string.status_generating_codes))
                val new_codes = generate_recovery_codes()
                val new_recovery_key = ByteArray(32).also { SecureRandom().nextBytes(it) }

                _state.value = _state.value.copy(processing_status = ctx.getString(R.string.status_creating_backup))
                val backup = withContext(compute_dispatcher) {
                    encrypt_vault_backup(vault_bytes, new_recovery_key)
                }

                val shares = withContext(compute_dispatcher) {
                    new_codes.map { code ->
                        generate_recovery_share(code, new_recovery_key)
                    }
                }

                _state.value = _state.value.copy(processing_status = ctx.getString(R.string.status_saving_credentials))
                recovery_api.complete(
                    CompleteRecoveryRequest(
                        recovery_token = token,
                        new_password_hash = base64_encode(new_password_hash),
                        new_password_salt = base64_encode(new_salt),
                        new_encrypted_vault = base64_encode(new_envelope.encrypted_vault),
                        new_vault_nonce = base64_encode(new_envelope.vault_nonce),
                        new_recovery_shares = shares,
                        new_encrypted_vault_backup = backup.encrypted_data,
                        new_vault_backup_nonce = backup.nonce,
                        new_recovery_key_salt = backup.salt,
                    ),
                )

                new_password_hash.fill(0)
                new_recovery_key.fill(0)
                vault_bytes.fill(0)
                decrypted_vault = null

                _state.value = _state.value.copy(
                    step = RecoveryStep.new_codes,
                    is_loading = false,
                    new_codes = new_codes,
                    processing_status = "",
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = _state.value.copy(
                    step = RecoveryStep.password,
                    is_loading = false,
                    error = map_error(t, R.string.error_recovery_failed),
                    processing_status = "",
                )
            } finally {
                password_bytes.fill(0)
            }
        }
    }

    fun go_to_success() {
        _state.value = _state.value.copy(step = RecoveryStep.success, new_codes = emptyList())
    }

    fun clear_error() {
        _state.value = _state.value.copy(error = null)
    }

    fun go_back() {
        if (_state.value.is_loading) return
        val current = _state.value.step
        val prev = when (current) {
            RecoveryStep.email_sent -> RecoveryStep.email
            RecoveryStep.code -> RecoveryStep.email
            RecoveryStep.password -> RecoveryStep.code
            else -> return
        }
        _state.value = _state.value.copy(step = prev, error = null)
    }

    private fun map_error(t: Throwable, fallback: Int): String = when (t) {
        is ApiError.ValidationError ->
            org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.error_invalid_request))
        is ApiError.RateLimited -> ctx.getString(R.string.error_too_many_attempts)
        is ApiError.NotFoundError -> ctx.getString(R.string.error_account_not_found)
        is ApiError.NetworkError -> ctx.getString(R.string.error_no_connection)
        is ApiError.ServerError -> ctx.getString(R.string.error_server)
        is ApiError.UnknownError -> ctx.getString(fallback)
        is java.net.UnknownHostException -> ctx.getString(R.string.error_no_connection)
        is java.net.ConnectException -> ctx.getString(R.string.error_no_connection)
        is java.net.SocketTimeoutException -> ctx.getString(R.string.error_timeout)
        is javax.net.ssl.SSLException -> ctx.getString(R.string.error_ssl)
        else -> ctx.getString(fallback)
    }

    private fun hash_recovery_code(code: String): String {
        val cleaned = code.uppercase(java.util.Locale.ROOT).trim()
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cleaned.toByteArray(Charsets.UTF_8))
        return base64_encode(hash)
    }

    private fun decrypt_recovery_key_with_code(
        code: String,
        encrypted_key: ByteArray,
        nonce: ByteArray,
        salt: ByteArray,
    ): ByteArray {
        val derived = PasswordKdf.derive_aes_key(code.uppercase(java.util.Locale.ROOT).trim(), salt, PBKDF2_ITERATIONS)
        val decrypted = AesGcm.decrypt(derived, nonce, encrypted_key)
        derived.fill(0)
        return decrypted
    }

    private fun decrypt_vault_backup(
        encrypted: ByteArray,
        nonce: ByteArray,
        salt: ByteArray,
        recovery_key: ByteArray,
    ): ByteArray {
        val derived = hkdf_sha256(recovery_key, salt, HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
        val decrypted = AesGcm.decrypt(derived, nonce, encrypted)
        derived.fill(0)
        return decrypted
    }

    data class VaultBackup(val encrypted_data: String, val nonce: String, val salt: String)

    private fun encrypt_vault_backup(vault: ByteArray, recovery_key: ByteArray): VaultBackup {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val derived = hkdf_sha256(recovery_key, salt, HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
        val encrypted = AesGcm.encrypt(derived, nonce, vault)
        derived.fill(0)
        return VaultBackup(base64_encode(encrypted), base64_encode(nonce), base64_encode(salt))
    }

    private fun generate_recovery_share(code: String, recovery_key: ByteArray): RecoveryShareData {
        val code_hash = hash_recovery_code(code)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val derived = PasswordKdf.derive_aes_key(code.uppercase(java.util.Locale.ROOT).trim(), salt, PBKDF2_ITERATIONS)
        val encrypted = AesGcm.encrypt(derived, nonce, recovery_key)
        derived.fill(0)

        return RecoveryShareData(
            code_hash = code_hash,
            code_salt = base64_encode(salt),
            encrypted_recovery_key = base64_encode(encrypted),
            recovery_key_nonce = base64_encode(nonce),
        )
    }

    private fun generate_recovery_codes(): List<String> {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..6).map {
            val seg1 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            val seg2 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            val seg3 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            "ASTER-$seg1-$seg2-$seg3"
        }
    }

    private fun base64_encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun base64_decode(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.DEFAULT)

    companion object {
        private const val PBKDF2_ITERATIONS = 310000
        private const val HKDF_INFO = "Aster Mail_Recovery_Vault_v1"
    }
}
