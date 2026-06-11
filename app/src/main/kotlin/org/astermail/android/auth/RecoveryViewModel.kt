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
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
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

    private var recovery_token: String? = null
    private var decrypted_vault: ByteArray? = null
    private var user_email: String = ""

    fun send_recovery_email(email: String) {
        user_email = email.trim().lowercase()
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            try {
                recovery_api.initiate_email(InitiateEmailRecoveryRequest(email = user_email))
                _state.value = _state.value.copy(
                    step = RecoveryStep.email_sent,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: ctx.getString(R.string.error_send_recovery),
                )
            }
        }
    }

    fun go_to_code_step() {
        _state.value = _state.value.copy(step = RecoveryStep.code, error = null)
    }

    fun verify_code(code: String) {
        val normalized = code.trim().uppercase()
        if (!normalized.startsWith("ASTER-") || normalized.length != 20) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.error_invalid_recovery_code))
            return
        }
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            try {
                val code_hash = withContext(Dispatchers.Default) {
                    hash_recovery_code(normalized)
                }
                val response = recovery_api.initiate(
                    InitiateRecoveryRequest(code_hash = code_hash, email = user_email),
                )
                recovery_token = response.recovery_token

                withContext(Dispatchers.Default) {
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
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: ctx.getString(R.string.error_invalid_code),
                )
            }
        }
    }

    fun submit_new_password(password: String, confirm: String) {
        if (password.length < 12) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.error_password_min_length))
            return
        }
        if (password != confirm) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.error_passwords_no_match))
            return
        }
        val token = recovery_token ?: return
        val vault_bytes = decrypted_vault ?: return

        _state.value = _state.value.copy(
            step = RecoveryStep.processing,
            is_loading = true,
            error = null,
            processing_status = ctx.getString(R.string.status_deriving_key),
        )

        viewModelScope.launch {
            try {
                val new_salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val password_bytes = password.toByteArray(Charsets.UTF_8)

                _state.value = _state.value.copy(processing_status = ctx.getString(R.string.status_encrypting_vault))
                val new_password_hash = withContext(Dispatchers.Default) {
                    CryptoNative.derive_pbkdf2_hash(password_bytes, new_salt, PBKDF2_ITERATIONS)
                }

                val new_encrypted_vault = withContext(Dispatchers.Default) {
                    CryptoNative.encrypt_vault(vault_bytes, new_password_hash)
                }

                _state.value = _state.value.copy(processing_status = ctx.getString(R.string.status_generating_codes))
                val new_codes = generate_recovery_codes()
                val new_recovery_key = ByteArray(32).also { SecureRandom().nextBytes(it) }

                _state.value = _state.value.copy(processing_status = ctx.getString(R.string.status_creating_backup))
                val backup = withContext(Dispatchers.Default) {
                    encrypt_vault_backup(vault_bytes, new_recovery_key)
                }

                val shares = withContext(Dispatchers.Default) {
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
                        new_encrypted_vault = base64_encode(new_encrypted_vault),
                        new_vault_nonce = base64_encode(ByteArray(1) { 1 }),
                        new_recovery_shares = shares,
                        new_encrypted_vault_backup = backup.encrypted_data,
                        new_vault_backup_nonce = backup.nonce,
                        new_recovery_key_salt = backup.salt,
                    ),
                )

                new_password_hash.fill(0)
                new_recovery_key.fill(0)
                password_bytes.fill(0)
                vault_bytes.fill(0)
                decrypted_vault = null

                _state.value = _state.value.copy(
                    step = RecoveryStep.new_codes,
                    is_loading = false,
                    new_codes = new_codes,
                    processing_status = "",
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    step = RecoveryStep.password,
                    is_loading = false,
                    error = t.message ?: ctx.getString(R.string.error_recovery_failed),
                    processing_status = "",
                )
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
        val current = _state.value.step
        val prev = when (current) {
            RecoveryStep.email_sent -> RecoveryStep.email
            RecoveryStep.code -> RecoveryStep.email
            RecoveryStep.password -> RecoveryStep.code
            else -> return
        }
        _state.value = _state.value.copy(step = prev, error = null)
    }

    private fun hash_recovery_code(code: String): String {
        val cleaned = code.uppercase().trim()
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
        val code_bytes = code.uppercase().trim().toCharArray()
        val spec = PBEKeySpec(code_bytes, salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        spec.clearPassword()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
        val decrypted = cipher.doFinal(encrypted_key)
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
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
        val decrypted = cipher.doFinal(encrypted)
        derived.fill(0)
        return decrypted
    }

    data class VaultBackup(val encrypted_data: String, val nonce: String, val salt: String)

    private fun encrypt_vault_backup(vault: ByteArray, recovery_key: ByteArray): VaultBackup {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val derived = hkdf_sha256(recovery_key, salt, HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(vault)
        derived.fill(0)
        return VaultBackup(base64_encode(encrypted), base64_encode(nonce), base64_encode(salt))
    }

    private fun generate_recovery_share(code: String, recovery_key: ByteArray): RecoveryShareData {
        val code_hash = hash_recovery_code(code)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val code_chars = code.uppercase().trim().toCharArray()
        val spec = PBEKeySpec(code_chars, salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        spec.clearPassword()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(recovery_key)
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

    private fun hkdf_sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(byteArrayOf(1))
        val okm = mac.doFinal()
        prk.fill(0)
        return okm.copyOf(length)
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
