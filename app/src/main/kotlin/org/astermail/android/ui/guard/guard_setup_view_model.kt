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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_ALREADY_ON_CODE
import org.astermail.android.api.GUARD_DISABLED_CODE
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.api_error_code
import org.astermail.android.api.guard.GUARD_DEVICE_TYPE_MOBILE
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardDeviceKeys
import org.astermail.android.api.guard.GuardHoldScope
import org.astermail.android.api.guard.GuardJoinRequest
import org.astermail.android.api.guard.GuardSetupRequest
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardCode
import org.astermail.android.crypto.guard.GuardContextSeal
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardActionKey
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.GuardPasskeyException
import org.astermail.android.guard.generate_box_keys
import org.astermail.android.guard.guard_box_keys
import org.astermail.android.guard.guard_passkey_failure
import org.astermail.android.guard.guard_passkey_failure_for
import org.astermail.android.guard.guard_passkey_proof
import org.astermail.android.guard.guard_passkey_sheet
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.AccountStore

const val GUARD_ALL_SWITCHES = GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES or
    GuardSwitch.APPROVE_MAIL_APPS or GuardSwitch.RECOVERY_WAIT
const val GUARD_ALL_SCOPES = GuardHoldScope.FORWARDING_RULES or GuardHoldScope.RECOVERY_PASSWORD or
    GuardHoldScope.SECURITY_EXPORT
const val GUARD_DEFAULT_SWITCHES = GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES

private const val JOIN_POLL_MS = 2_000L
private const val RESCUE_GROUP = 5

fun generate_rescue_code(rng: SecureRandom): String {
    val alphabet = GuardCode.ALPHABET
    return buildString(GuardCode.RESCUE_LEN) {
        repeat(GuardCode.RESCUE_LEN) { append(alphabet[rng.nextInt(alphabet.length)]) }
    }
}

fun valid_switches(switches: Int, scopes: Int): Boolean {
    if (switches == 0) return false
    if (switches and GUARD_ALL_SWITCHES.inv() != 0) return false
    if (scopes and GUARD_ALL_SCOPES.inv() != 0) return false
    if (switches and GuardSwitch.APPROVE_MAIL_APPS != 0 && switches and GuardSwitch.NEW_DEVICE_APPROVAL == 0) return false
    if (scopes != 0 && switches and GuardSwitch.HOLD_CHANGES == 0) return false
    return true
}

data class guard_selection(val switches: Int, val scopes: Int)

fun guard_toggle_switch(current: guard_selection, bit: Int): guard_selection {
    val turning_on = current.switches and bit == 0
    var switches = current.switches xor bit
    var scopes = current.scopes
    if (turning_on && bit == GuardSwitch.APPROVE_MAIL_APPS) switches = switches or GuardSwitch.NEW_DEVICE_APPROVAL
    if (!turning_on && bit == GuardSwitch.NEW_DEVICE_APPROVAL) switches = switches and GuardSwitch.APPROVE_MAIL_APPS.inv()
    if (!turning_on && bit == GuardSwitch.HOLD_CHANGES) scopes = 0
    if (turning_on && bit == GuardSwitch.HOLD_CHANGES && scopes == 0) scopes = GUARD_ALL_SCOPES
    return guard_selection(switches, scopes)
}

fun guard_toggle_scope(current: guard_selection, bit: Int): guard_selection {
    if (current.switches and GuardSwitch.HOLD_CHANGES == 0) return current
    return current.copy(scopes = current.scopes xor bit)
}

fun guard_rescue_last_group(code: String): String = code.takeLast(RESCUE_GROUP)

fun guard_rescue_group_matches(code: String, input: String): Boolean {
    val cleaned = input.uppercase().filter { it.isLetterOrDigit() }
    return cleaned.length == RESCUE_GROUP && cleaned == guard_rescue_last_group(code)
}

fun guard_is_disabled(t: Throwable): Boolean = t is ApiError.NotFoundError || api_error_code(t) == GUARD_DISABLED_CODE

fun guard_is_on(status: GuardStatus): Boolean = status.enrolled_at != null || status.switches != 0

enum class guard_setup_step {
    loading,
    hidden,
    already_on,
    intro,
    choose,
    rescue,
    confirm_rescue,
    password,
    working,
    done,
    join_intro,
    join_waiting,
    join_done,
    join_denied,
    join_expired,
    failed,
}

enum class guard_setup_phase { keys, confirm, register }

enum class guard_setup_notice { wrong_group, wrong_password, totp_required, canceled, no_lock_screen, failed }

data class guard_setup_ui(
    val step: guard_setup_step = guard_setup_step.loading,
    val switches: Int = GUARD_DEFAULT_SWITCHES,
    val scopes: Int = GUARD_ALL_SCOPES,
    val rescue_code: String = "",
    val confirm_input: String = "",
    val password: String = "",
    val totp: String = "",
    val show_totp: Boolean = false,
    val notice: guard_setup_notice? = null,
    val passkey_failure: guard_passkey_failure? = null,
    val join_number: Int? = null,
    val join_expires_at: String = "",
    val phase: guard_setup_phase? = null,
    val failed_phase: guard_setup_phase? = null,
    val error: org.astermail.android.guard.guard_error_message? = null,
) {
    val can_continue_choose: Boolean get() = valid_switches(switches, scopes)
    val can_submit: Boolean get() = password.isNotEmpty() && (!show_totp || totp.isNotBlank())
    val can_use_passkey: Boolean get() = step == guard_setup_step.password && valid_switches(switches, scopes)
}

interface guard_setup_deps {
    fun user_id(): UUID?
    fun new_box_keys(): guard_box_keys
    fun new_action_alias(): String
    fun create_action_key(alias: String): ByteArray
    fun delete_action_key(alias: String)
    fun device_name(): String
    fun pick_number(): Int
    fun new_request_id(): UUID
    fun rescue_code(): String
    suspend fun password_hash(password: String): String?
}

class default_guard_setup_deps(
    private val accounts: AccountStore,
    private val auth: AuthRepository,
) : guard_setup_deps {
    private val random = SecureRandom()
    override fun user_id(): UUID? = accounts.get_current_id()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    override fun new_box_keys(): guard_box_keys = generate_box_keys(random)
    override fun new_action_alias(): String = GuardActionKey.new_alias()
    override fun create_action_key(alias: String): ByteArray = GuardActionKey.create(alias)
    override fun delete_action_key(alias: String) = GuardActionKey.delete(alias)
    override fun device_name(): String = org.astermail.android.guard.guard_device_label()
    override fun pick_number(): Int = guard_pick_number(random)
    override fun new_request_id(): UUID = UUID.randomUUID()
    override fun rescue_code(): String = generate_rescue_code(random)
    override suspend fun password_hash(password: String): String? = auth.derive_password_hash_b64(password)

    private companion object {
        const val MAX_DEVICE_NAME = 64
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GuardSetupModule {
    @Provides
    fun provide_guard_setup_deps(accounts: AccountStore, auth: AuthRepository): guard_setup_deps =
        default_guard_setup_deps(accounts, auth)
}

@HiltViewModel
class GuardSetupViewModel @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val deps: guard_setup_deps,
) : ViewModel() {

    private val _state = MutableStateFlow(guard_setup_ui())
    val state: StateFlow<guard_setup_ui> = _state.asStateFlow()

    private var join_keys: guard_box_keys? = null
    private var join_alias: String? = null
    private var join_request_id: String? = null
    private var poll_job: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        _state.update { it.copy(step = guard_setup_step.loading, notice = null, error = null, failed_phase = null, phase = null) }
        val status = try {
            api.status()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    step = if (guard_is_disabled(t)) guard_setup_step.hidden else guard_setup_step.failed,
                    error = org.astermail.android.guard.guard_error_message_for(t),
                )
            }
            return
        }
        val step = when {
            !guard_is_on(status) -> guard_setup_step.intro
            status.this_device == null -> guard_setup_step.join_intro
            else -> guard_setup_step.already_on
        }
        _state.update { it.copy(step = step) }
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    fun to_choose() = _state.update { it.copy(step = guard_setup_step.choose, notice = null) }

    fun toggle(bit: Int) = _state.update {
        val next = guard_toggle_switch(guard_selection(it.switches, it.scopes), bit)
        it.copy(switches = next.switches, scopes = next.scopes)
    }

    fun toggle_scope(bit: Int) = _state.update {
        val next = guard_toggle_scope(guard_selection(it.switches, it.scopes), bit)
        it.copy(switches = next.switches, scopes = next.scopes)
    }

    fun to_rescue() {
        val current = _state.value
        if (!current.can_continue_choose) return
        val code = current.rescue_code.ifEmpty { deps.rescue_code() }
        _state.update { it.copy(step = guard_setup_step.rescue, rescue_code = code, confirm_input = "", notice = null) }
    }

    fun to_confirm_rescue() = _state.update { it.copy(step = guard_setup_step.confirm_rescue, confirm_input = "", notice = null) }

    fun on_confirm_input(value: String) = _state.update {
        it.copy(confirm_input = value.take(RESCUE_GROUP + 1), notice = null)
    }

    fun confirm_rescue(): Boolean {
        val current = _state.value
        if (!guard_rescue_group_matches(current.rescue_code, current.confirm_input)) {
            _state.update { it.copy(notice = guard_setup_notice.wrong_group) }
            return false
        }
        _state.update { it.copy(step = guard_setup_step.password, notice = null) }
        return true
    }

    fun back() = _state.update {
        val step = when (it.step) {
            guard_setup_step.choose -> guard_setup_step.intro
            guard_setup_step.rescue -> guard_setup_step.choose
            guard_setup_step.confirm_rescue -> guard_setup_step.rescue
            guard_setup_step.password -> guard_setup_step.confirm_rescue
            else -> it.step
        }
        it.copy(step = step, notice = null, passkey_failure = null)
    }

    fun on_password(value: String) = _state.update { it.copy(password = value, notice = null, passkey_failure = null) }

    fun on_totp(value: String) = _state.update {
        it.copy(totp = value.filter { c -> c.isDigit() }.take(8), notice = null, passkey_failure = null)
    }

    fun submit(signer: guard_signer) {
        if (_state.value.step == guard_setup_step.working) return
        viewModelScope.launch { run_setup(signer) }
    }

    fun submit_passkey(signer: guard_signer, sheet: guard_passkey_sheet) {
        if (_state.value.step == guard_setup_step.working) return
        viewModelScope.launch { run_setup(signer, sheet) }
    }

    suspend fun run_setup(signer: guard_signer, sheet: guard_passkey_sheet? = null) {
        val ui = _state.value
        val user = deps.user_id()
        val ready = if (sheet == null) ui.can_submit else ui.can_use_passkey
        if (user == null || !ready || !valid_switches(ui.switches, ui.scopes)) {
            _state.update { it.copy(notice = guard_setup_notice.failed, passkey_failure = null) }
            return
        }
        _state.update {
            it.copy(
                step = guard_setup_step.working,
                notice = null,
                passkey_failure = null,
                phase = guard_setup_phase.keys,
                failed_phase = null,
                error = null,
            )
        }
        var alias: String? = null
        var saved = false
        try {
            val proof: StepUpProof = if (sheet != null) {
                guard_passkey_proof(api, user, deps::user_id, sheet)
            } else {
                val hash = deps.password_hash(ui.password)
                if (hash == null) {
                    to_password(guard_setup_notice.failed)
                    return
                }
                StepUpProof.Password(hash, ui.totp.takeIf { ui.show_totp && it.isNotBlank() })
            }
            val keys = deps.new_box_keys()
            val fresh_alias = deps.new_action_alias()
            alias = fresh_alias
            val action_pk = GuardDigests.compress_p256(deps.create_action_key(fresh_alias))
            val digest = GuardDigests.keys_digest(keys.ed25519_pk, keys.mlkem_pk, keys.x25519_pk, action_pk)
            val nonce = api.setup_nonce().nonce
            set_phase(guard_setup_phase.confirm)
            val message = GuardDigests.action_message(GuardActionCode.ENROLL, user, DeviceEnvelope.base64url_decode(nonce), digest)
            val der = when (val result = signer.with_alias(fresh_alias).sign(message)) {
                is guard_sign_result.signed -> result.der
                else -> {
                    to_password(sign_notice(result))
                    return
                }
            }
            if (deps.user_id() != user) throw GuardPasskeyException(guard_passkey_failure.account_changed)
            set_phase(guard_setup_phase.register)
            val response = api.setup(
                GuardSetupRequest(
                    device = device_keys(keys, action_pk),
                    nonce = nonce,
                    possession_signature = DeviceEnvelope.base64url_encode(der),
                    rescue_code = ui.rescue_code,
                    switches = ui.switches,
                    hold_scopes = ui.scopes,
                    proof = proof,
                ),
            )
            save(user, response.device_id, response.code_secret_sealed, keys, fresh_alias)
            saved = true
            _state.update {
                it.copy(step = guard_setup_step.done, password = "", totp = "", rescue_code = "", confirm_input = "")
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            val code = api_error_code(t)
            val passkey_failure = guard_passkey_failure_for(t)
            when {
                passkey_failure != null -> to_passkey_failure(passkey_failure)
                code == GUARD_ALREADY_ON_CODE -> {
                    _state.update { it.copy(password = "", totp = "") }
                    load()
                }
                guard_is_disabled(t) -> _state.update { it.copy(step = guard_setup_step.hidden, password = "") }
                code == TOTP_REQUIRED_CODE -> {
                    _state.update { it.copy(show_totp = true) }
                    to_password(guard_setup_notice.totp_required)
                }
                t is ApiError.InvalidCredentials || t is ApiError.StepUpRequired ||
                    code == STEP_UP_REQUIRED_CODE || code == INVALID_CREDENTIALS_CODE || code == INVALID_PASSWORD_CODE ->
                    if (sheet != null) {
                        to_passkey_failure(guard_passkey_failure.failed)
                    } else {
                        to_password(guard_setup_notice.wrong_password)
                    }
                else -> to_failed_phase(guard_setup_step.password, t)
            }
        } finally {
            if (!saved) alias?.let { deps.delete_action_key(it) }
        }
    }

    fun start_join(signer: guard_signer) {
        if (_state.value.step == guard_setup_step.working) return
        viewModelScope.launch { run_join(signer) }
    }

    suspend fun run_join(signer: guard_signer) {
        val user = deps.user_id()
        if (user == null) {
            _state.update { it.copy(step = guard_setup_step.failed) }
            return
        }
        _state.update {
            it.copy(step = guard_setup_step.working, notice = null, phase = guard_setup_phase.keys, failed_phase = null, error = null)
        }
        drop_join_alias()
        val fresh_alias = deps.new_action_alias()
        var joined = false
        try {
            val keys = deps.new_box_keys()
            val action_pk = GuardDigests.compress_p256(deps.create_action_key(fresh_alias))
            val digest = GuardDigests.keys_digest(keys.ed25519_pk, keys.mlkem_pk, keys.x25519_pk, action_pk)
            val nonce = api.setup_nonce().nonce
            set_phase(guard_setup_phase.confirm)
            val message = GuardDigests.action_message(GuardActionCode.JOIN, user, DeviceEnvelope.base64url_decode(nonce), digest)
            val der = when (val result = signer.with_alias(fresh_alias).sign(message)) {
                is guard_sign_result.signed -> result.der
                else -> {
                    _state.update { it.copy(step = guard_setup_step.join_intro, notice = sign_notice(result)) }
                    return
                }
            }
            set_phase(guard_setup_phase.register)
            val number = deps.pick_number()
            val client_request_id = deps.new_request_id()
            val started = api.join(
                GuardJoinRequest(
                    device = device_keys(keys, action_pk),
                    number_hash = DeviceEnvelope.base64url_encode(GuardDigests.number_hash(client_request_id, number)),
                    action = SignedAction(nonce = nonce, signature = DeviceEnvelope.base64url_encode(der)),
                    request_id = client_request_id.toString(),
                ),
            )
            join_keys = keys
            join_alias = fresh_alias
            joined = true
            join_request_id = started.request_id
            _state.update {
                it.copy(step = guard_setup_step.join_waiting, join_number = number, join_expires_at = started.expires_at)
            }
            start_polling()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (api_error_code(t) == GUARD_ALREADY_ON_CODE) load() else to_failed_phase(guard_setup_step.join_intro, t)
        } finally {
            if (!joined) deps.delete_action_key(fresh_alias)
        }
    }

    private fun drop_join_alias() {
        join_alias?.let { deps.delete_action_key(it) }
        join_alias = null
    }

    private fun start_polling() {
        poll_job?.cancel()
        poll_job = viewModelScope.launch {
            while (_state.value.step == guard_setup_step.join_waiting) {
                delay(JOIN_POLL_MS)
                poll_once()
            }
        }
    }

    suspend fun poll_once() {
        val id = join_request_id ?: return
        val status = try {
            api.join_status(id).status
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (api_error_code(t) == GUARD_REQUEST_GONE_CODE) end_join(guard_setup_step.join_expired)
            return
        }
        when (status) {
            "approved" -> complete_join(id)
            "denied" -> end_join(guard_setup_step.join_denied)
            "expired", "cancelled" -> end_join(guard_setup_step.join_expired)
        }
    }

    private suspend fun complete_join(id: String) {
        val user = deps.user_id()
        val keys = join_keys
        val alias = join_alias
        if (user == null || keys == null || alias == null) {
            end_join(guard_setup_step.failed)
            return
        }
        try {
            val complete = api.join_complete(id)
            save(user, complete.device_id, complete.code_secret_sealed, keys, alias)
            join_alias = null
            end_join(guard_setup_step.join_done)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            end_join(if (api_error_code(t) == GUARD_REQUEST_GONE_CODE) guard_setup_step.join_expired else guard_setup_step.failed)
        }
    }

    private fun end_join(step: guard_setup_step) {
        drop_join_alias()
        join_keys = null
        join_request_id = null
        _state.update { it.copy(step = step, join_number = null) }
    }

    fun restart_join() = _state.update { it.copy(step = guard_setup_step.join_intro, notice = null, join_number = null) }

    private fun save(user: UUID, device_id_raw: String, sealed: String, keys: guard_box_keys, alias: String) {
        val device_id = UUID.fromString(device_id_raw)
        val previous = store.stored_action_alias(user)
        val secret = GuardContextSeal.open(
            DeviceEnvelope.base64url_decode(sealed),
            GuardContextSeal.code_secret_aad(device_id),
            keys.x25519_sk,
            keys.mlkem_sk,
        )
        try {
            store.save_enrollment(
                user, device_id, secret, keys.x25519_sk, keys.mlkem_sk, keys.x25519_pk, keys.mlkem_pk, keys.ed25519_pk, keys.ed25519_sk,
                alias,
            )
        } finally {
            secret.fill(0)
        }
        if (previous != null && previous != alias) deps.delete_action_key(previous)
    }

    private fun set_phase(phase: guard_setup_phase) = _state.update { it.copy(phase = phase) }

    private fun to_failed_phase(step: guard_setup_step, t: Throwable) = _state.update {
        it.copy(
            step = step,
            notice = guard_setup_notice.failed,
            passkey_failure = null,
            failed_phase = it.phase,
            phase = null,
            error = org.astermail.android.guard.guard_error_message_for(t),
        )
    }

    private fun to_password(notice: guard_setup_notice) =
        _state.update { it.copy(step = guard_setup_step.password, notice = notice, passkey_failure = null) }

    private fun to_passkey_failure(failure: guard_passkey_failure) = _state.update {
        it.copy(
            step = guard_setup_step.password,
            notice = null,
            passkey_failure = failure.takeUnless { f -> f == guard_passkey_failure.canceled },
        )
    }

    private fun sign_notice(result: guard_sign_result): guard_setup_notice = when (result) {
        guard_sign_result.no_lock_screen -> guard_setup_notice.no_lock_screen
        guard_sign_result.canceled -> guard_setup_notice.canceled
        else -> guard_setup_notice.failed
    }

    private fun device_keys(k: guard_box_keys, action_pk: ByteArray) = GuardDeviceKeys(
        ed25519_pk = DeviceEnvelope.base64url_encode(k.ed25519_pk),
        mlkem_pk = DeviceEnvelope.base64url_encode(k.mlkem_pk),
        x25519_pk = DeviceEnvelope.base64url_encode(k.x25519_pk),
        action_pk = DeviceEnvelope.base64url_encode(action_pk),
        device_type = GUARD_DEVICE_TYPE_MOBILE,
        name = deps.device_name(),
    )

    override fun onCleared() {
        poll_job?.cancel()
        _state.update { it.copy(password = "", totp = "") }
        super.onCleared()
    }

    private companion object {
        const val TOTP_REQUIRED_CODE = "TOTP_REQUIRED"
        const val STEP_UP_REQUIRED_CODE = "STEP_UP_REQUIRED"
        const val INVALID_CREDENTIALS_CODE = "INVALID_CREDENTIALS"
        const val INVALID_PASSWORD_CODE = "INVALID_PASSWORD"
    }
}
