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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_CODE_INVALID_CODE
import org.astermail.android.api.GUARD_PAUSED_CODE
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.GUARD_WRONG_NUMBER_CODE
import org.astermail.android.api.api_error_code
import org.astermail.android.api.guard.GUARD_DEVICE_TYPE_MOBILE
import org.astermail.android.api.guard.GUARD_ENVELOPE_VERSION
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardDeviceKeys
import org.astermail.android.api.guard.GuardResume
import org.astermail.android.api.guard.GuardSignInBegin
import org.astermail.android.api.guard.GuardSignInComplete
import org.astermail.android.auth.AuthRepository
import org.astermail.android.auth.GuardCredentialsMissing
import org.astermail.android.auth.GuardSignInChallenge
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardCode
import org.astermail.android.crypto.guard.GuardContextSeal
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardActionKey
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.generate_box_keys
import org.astermail.android.guard.guard_box_keys
import org.astermail.android.guard.guard_pending_sign_in
import org.astermail.android.guard.guard_saved_rescue
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.AccountStore

fun guard_pick_number(random: SecureRandom): Int = random.nextInt(90) + 10

interface guard_sign_in_deps {
    fun new_box_keys(): guard_box_keys
    fun new_action_alias(): String
    fun create_action_key(alias: String): ByteArray
    fun delete_action_key(alias: String)
    fun action_public_key(alias: String): ByteArray?
    fun current_user_id(): UUID?
    fun device_name(): String
    fun now_ms(): Long
    fun pick_number(): Int
    fun open_envelope(envelope: ByteArray, keys: guard_box_keys): ByteArray
    suspend fun complete(challenge: GuardSignInChallenge, complete: GuardSignInComplete, passphrase: ByteArray?)
}

class default_guard_sign_in_deps(
    private val auth: AuthRepository,
    private val accounts: AccountStore,
) : guard_sign_in_deps {
    private val random = SecureRandom()
    override fun new_box_keys(): guard_box_keys = generate_box_keys(random)
    override fun new_action_alias(): String = GuardActionKey.new_alias()
    override fun create_action_key(alias: String): ByteArray = GuardActionKey.create(alias)
    override fun delete_action_key(alias: String) = GuardActionKey.delete(alias)
    override fun action_public_key(alias: String): ByteArray? = GuardActionKey.public_key_sec1(alias)
    override fun current_user_id(): UUID? =
        accounts.get_current_id()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    override fun device_name(): String = org.astermail.android.guard.guard_device_label()
    override fun now_ms(): Long = org.astermail.android.api.network.server_clock.now_ms()
    override fun pick_number(): Int = guard_pick_number(random)
    override fun open_envelope(envelope: ByteArray, keys: guard_box_keys): ByteArray =
        DeviceEnvelope.open_secret_for_device(
            envelope,
            DeviceEnvelope.DeviceRecipient(keys.ed25519_pk, keys.mlkem_pk, keys.x25519_pk),
            keys.mlkem_sk,
            keys.x25519_sk,
        )
    override suspend fun complete(challenge: GuardSignInChallenge, complete: GuardSignInComplete, passphrase: ByteArray?) =
        auth.complete_guard_sign_in(challenge, complete, passphrase)

    private companion object {
        const val MAX_DEVICE_NAME = 64
    }
}

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface GuardSignInEntryPoint {
    fun guard_device_store(): GuardDeviceStore
}

fun has_pending_guard_sign_in(context: android.content.Context): Boolean = runCatching {
    val store = guard_device_store_of(context)
    store.pending_sign_in() != null || store.rescue() != null
}.getOrDefault(false)

fun take_guard_setup_after_rescue(context: android.content.Context): Boolean = runCatching {
    guard_device_store_of(context).take_setup_after_rescue()
}.getOrDefault(false)

private fun guard_device_store_of(context: android.content.Context): GuardDeviceStore =
    dagger.hilt.android.EntryPointAccessors
        .fromApplication(context.applicationContext, GuardSignInEntryPoint::class.java)
        .guard_device_store()

@Module
@InstallIn(SingletonComponent::class)
object GuardSignInModule {
    @Provides
    fun provide_guard_sign_in_deps(auth: AuthRepository, accounts: AccountStore): guard_sign_in_deps =
        default_guard_sign_in_deps(auth, accounts)
}

sealed interface guard_wait_notice {
    data object wrong_code : guard_wait_notice
    data class retry(val retry_after_secs: Long) : guard_wait_notice
    data class error(val message: org.astermail.android.guard.guard_error_message) : guard_wait_notice
}

enum class guard_resume_block { unlock, network }

sealed interface guard_rescue_notice {
    data object invalid : guard_rescue_notice
    data class retry(val retry_after_secs: Long) : guard_rescue_notice
    data class error(val message: org.astermail.android.guard.guard_error_message) : guard_rescue_notice
}

sealed interface guard_wait_state {
    data object starting : guard_wait_state
    data class waiting(
        val number: Int,
        val seconds_left: Long,
        val notice: guard_wait_notice? = null,
        val expires_at_ms: Long = 0L,
    ) : guard_wait_state
    data class retry(val retry_after_secs: Long) : guard_wait_state
    data class paused(val retry_after_secs: Long, val ends_at_ms: Long) : guard_wait_state
    data class error(val message: org.astermail.android.guard.guard_error_message) : guard_wait_state
    data class resume_blocked(val reason: guard_resume_block) : guard_wait_state
    data object approved : guard_wait_state
    data object done : guard_wait_state
    data object denied : guard_wait_state
    data object expired : guard_wait_state
    data class held(val apply_after: String) : guard_wait_state
    data object needs_password : guard_wait_state
    data object failed : guard_wait_state
    data class rescue_entry(val notice: guard_rescue_notice? = null, val busy: Boolean = false) : guard_wait_state
    data class rescue_waiting(val apply_after: String) : guard_wait_state
    data object rescue_applied : guard_wait_state
    data object rescue_stopped : guard_wait_state
}

const val GUARD_POLL_INTERVAL_MS = 2_000L
const val GUARD_RESCUE_POLL_INTERVAL_MS = 60_000L
private const val TICK_MS = 1_000L

private sealed interface guard_resume_outcome {
    class begun(val response: org.astermail.android.api.guard.GuardSignInBeginResponse) : guard_resume_outcome
    data object fresh : guard_resume_outcome
    data class blocked(val reason: guard_resume_block) : guard_resume_outcome
}

@HiltViewModel
class GuardSignInViewModel @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val deps: guard_sign_in_deps,
) : ViewModel() {

    private val _state = MutableStateFlow<guard_wait_state>(guard_wait_state.starting)
    val state: StateFlow<guard_wait_state> = _state.asStateFlow()

    private var challenge: GuardSignInChallenge? = null
    private var keys: guard_box_keys? = null
    private var action_alias: String? = null
    private var fresh_alias: String? = null
    private var signer: guard_signer? = null
    private var number: Int = 0
    private var expires_at_ms: Long = 0
    private var poll_job: Job? = null
    private var rescue_job: Job? = null
    private var started = false
    private var finishing = false

    fun start(challenge: GuardSignInChallenge, signer: guard_signer) {
        if (started) return
        started = true
        this.challenge = challenge
        this.signer = signer
        viewModelScope.launch { begin(challenge, signer) }
    }

    fun retry_resume() {
        if (_state.value !is guard_wait_state.resume_blocked) return
        val c = challenge ?: return
        val s = signer ?: return
        _state.value = guard_wait_state.starting
        viewModelScope.launch { begin(c, s) }
    }

    fun restore(pending: guard_pending_sign_in) {
        if (started) return
        started = true
        challenge = GuardSignInChallenge(
            request_id = pending.request_id,
            guard_token = pending.guard_token,
            expires_at = pending.expires_at,
            methods = emptyList(),
            email = pending.account_hint,
            user_id = null,
            credentials = null,
        )
        keys = pending.keys
        action_alias = pending.action_alias
        fresh_alias = pending.action_alias?.takeIf { !is_enrolled_alias(it) }
        number = pending.number
        expires_at_ms = parse_ms(pending.expires_at)
        if (expires_at_ms <= deps.now_ms()) {
            to_expired()
            return
        }
        start_polling()
    }

    fun restore_pending(): Boolean {
        if (started) return true
        val pending = store.pending_sign_in()
        if (pending != null) {
            restore(pending)
            return true
        }
        val rescue = store.rescue() ?: return false
        started = true
        watch_rescue(rescue)
        return true
    }

    fun open_rescue() {
        if (_state.value !is guard_wait_state.waiting) return
        poll_job?.cancel()
        _state.value = guard_wait_state.rescue_entry()
    }

    fun close_rescue() {
        val current = _state.value as? guard_wait_state.rescue_entry ?: return
        if (current.busy) return
        start_polling()
    }

    fun submit_rescue(input: String) {
        val current = _state.value as? guard_wait_state.rescue_entry ?: return
        if (current.busy) return
        val c = challenge ?: return
        val normalized = GuardCode.normalize_rescue(input)
        if (normalized == null) {
            _state.value = current.copy(notice = guard_rescue_notice.invalid)
            return
        }
        _state.value = current.copy(busy = true)
        viewModelScope.launch {
            try {
                val result = api.sign_in_rescue(c.guard_token, normalized)
                store.clear_pending_sign_in()
                c.credentials?.wipe()
                challenge = null
                keys = null
                drop_fresh_key()
                val saved = guard_saved_rescue(result.rescue_id, result.apply_after, c.email)
                store.save_rescue(saved)
                watch_rescue(saved)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                when {
                    t is ApiError.GuardRetry ->
                        _state.value = guard_wait_state.rescue_entry(guard_rescue_notice.retry(t.retry_after_secs))
                    api_error_code(t) == GUARD_REQUEST_GONE_CODE -> to_expired()
                    else -> _state.value = guard_wait_state.rescue_entry(rescue_notice_for(t))
                }
            }
        }
    }

    private fun watch_rescue(rescue: guard_saved_rescue) {
        rescue_job?.cancel()
        _state.value = guard_wait_state.rescue_waiting(rescue.apply_after)
        rescue_job = viewModelScope.launch {
            while (true) {
                try {
                    val status = api.rescue_status(rescue.rescue_id)
                    when (status.status) {
                        RESCUE_APPLIED -> {
                            store.clear_rescue()
                            store.set_setup_after_rescue()
                            _state.value = guard_wait_state.rescue_applied
                            return@launch
                        }
                        RESCUE_VETOED, RESCUE_CANCELLED -> {
                            store.clear_rescue()
                            _state.value = guard_wait_state.rescue_stopped
                            return@launch
                        }
                        else -> if (status.apply_after.isNotBlank()) {
                            _state.value = guard_wait_state.rescue_waiting(status.apply_after)
                        }
                    }
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    if (t is ApiError.NotFoundError || api_error_code(t) == GUARD_REQUEST_GONE_CODE) {
                        store.clear_rescue()
                        _state.value = guard_wait_state.expired
                        return@launch
                    }
                }
                delay(GUARD_RESCUE_POLL_INTERVAL_MS)
            }
        }
    }

    fun submit_code(code: String) {
        val c = challenge ?: return
        val normalized = normalize_code(code)
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            try {
                apply_status(api.sign_in_code(c.guard_token, normalized).status, null)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                when {
                    t is ApiError.GuardRetry -> set_notice(guard_wait_notice.retry(t.retry_after_secs))
                    api_error_code(t) == GUARD_WRONG_NUMBER_CODE || api_error_code(t) == GUARD_CODE_INVALID_CODE ->
                        set_notice(guard_wait_notice.wrong_code)
                    is_transient_error(t) -> org.astermail.android.guard.guard_error_message_for(t)
                        ?.let { set_notice(guard_wait_notice.error(it)) } ?: handle_error(t)
                    else -> handle_error(t)
                }
            }
        }
    }

    fun cancel() {
        poll_job?.cancel()
        rescue_job?.cancel()
        store.clear_pending_sign_in()
        challenge?.credentials?.wipe()
        challenge = null
        keys = null
        drop_fresh_key()
        action_alias = null
        signer = null
        started = false
        finishing = false
        _state.value = guard_wait_state.starting
    }

    private suspend fun begin(c: GuardSignInChallenge, signer: guard_signer) {
        val request_id = runCatching { UUID.fromString(c.request_id) }.getOrNull()
        if (request_id == null) {
            _state.value = guard_wait_state.failed
            return
        }
        number = deps.pick_number()
        expires_at_ms = parse_ms(c.expires_at)
        val number_hash = DeviceEnvelope.base64url_encode(GuardDigests.number_hash(request_id, number))
        try {
            val status = when (val outcome = try_resume(c, signer, number_hash)) {
                is guard_resume_outcome.begun -> outcome.response
                guard_resume_outcome.fresh -> begin_fresh(c, number_hash)
                is guard_resume_outcome.blocked -> {
                    _state.value = guard_wait_state.resume_blocked(outcome.reason)
                    return
                }
            }
            if (status.expires_at.isNotBlank()) expires_at_ms = parse_ms(status.expires_at)
            save_pending(c)
            if (!apply_status(status.status, null)) start_polling()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (t is ApiError.GuardRetry) {
                _state.value = retry_state(t)
            } else {
                handle_error(t)
            }
        }
    }

    private fun retry_state(t: ApiError.GuardRetry): guard_wait_state =
        if (t.code == GUARD_PAUSED_CODE) {
            guard_wait_state.paused(t.retry_after_secs, deps.now_ms() + t.retry_after_secs.coerceAtLeast(0) * 1000)
        } else {
            guard_wait_state.retry(t.retry_after_secs)
        }

    private suspend fun try_resume(
        c: GuardSignInChallenge,
        signer: guard_signer,
        number_hash: String,
    ): guard_resume_outcome {
        val user = c.user_id?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return guard_resume_outcome.fresh
        val device_id = store.device_id(user) ?: return guard_resume_outcome.fresh
        val stored = store.box_keys(user) ?: return guard_resume_outcome.fresh
        val alias = store.action_alias(user, deps.current_user_id())
        val action_pub = deps.action_public_key(alias) ?: return guard_resume_outcome.fresh
        val compressed = GuardDigests.compress_p256(action_pub)
        val nonce = try {
            api.sign_in_resume_nonce(c.guard_token, device_id.toString())
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            return when {
                t is ApiError.NotFoundError -> guard_resume_outcome.fresh
                t is ApiError.GuardRetry || api_error_code(t) == GUARD_REQUEST_GONE_CODE -> throw t
                else -> guard_resume_outcome.blocked(guard_resume_block.network)
            }
        }
        val digest = GuardDigests.keys_digest(stored.ed25519_pk, stored.mlkem_pk, stored.x25519_pk, compressed)
        val message = GuardDigests.action_message(
            GuardActionCode.RESUME,
            user,
            DeviceEnvelope.base64url_decode(nonce.nonce),
            digest,
        )
        val signed = when (val result = signer.with_alias(alias).sign(message)) {
            is guard_sign_result.signed -> result
            guard_sign_result.key_lost -> return guard_resume_outcome.fresh
            else -> return guard_resume_outcome.blocked(guard_resume_block.unlock)
        }
        val response = api.sign_in_begin(
            c.guard_token,
            GuardSignInBegin(
                device = device_keys(stored, compressed),
                envelope_versions = listOf(GUARD_ENVELOPE_VERSION),
                number_hash = number_hash,
                resume = GuardResume(
                    device_id = device_id.toString(),
                    nonce = nonce.nonce,
                    signature = DeviceEnvelope.base64url_encode(signed.der),
                ),
            ),
        )
        keys = stored
        action_alias = alias
        return guard_resume_outcome.begun(response)
    }

    private suspend fun begin_fresh(
        c: GuardSignInChallenge,
        number_hash: String,
    ): org.astermail.android.api.guard.GuardSignInBeginResponse {
        val fresh = deps.new_box_keys()
        drop_fresh_key()
        val alias = deps.new_action_alias()
        fresh_alias = alias
        action_alias = alias
        val action_pk = GuardDigests.compress_p256(deps.create_action_key(alias))
        keys = fresh
        return api.sign_in_begin(
            c.guard_token,
            GuardSignInBegin(
                device = device_keys(fresh, action_pk),
                envelope_versions = listOf(GUARD_ENVELOPE_VERSION),
                number_hash = number_hash,
            ),
        )
    }

    private fun device_keys(k: guard_box_keys, action_pk: ByteArray) = GuardDeviceKeys(
        ed25519_pk = DeviceEnvelope.base64url_encode(k.ed25519_pk),
        mlkem_pk = DeviceEnvelope.base64url_encode(k.mlkem_pk),
        x25519_pk = DeviceEnvelope.base64url_encode(k.x25519_pk),
        action_pk = DeviceEnvelope.base64url_encode(action_pk),
        device_type = GUARD_DEVICE_TYPE_MOBILE,
        name = deps.device_name(),
    )

    private fun save_pending(c: GuardSignInChallenge) {
        val k = keys ?: return
        store.save_pending_sign_in(
            guard_pending_sign_in(
                c.request_id,
                c.guard_token,
                number,
                Instant.ofEpochMilli(expires_at_ms).toString(),
                k,
                c.email,
                action_alias,
            ),
        )
    }

    private fun start_polling() {
        poll_job?.cancel()
        _state.value = waiting_state(null)
        poll_job = viewModelScope.launch {
            var elapsed = 0L
            while (true) {
                delay(TICK_MS)
                elapsed += TICK_MS
                if (deps.now_ms() >= expires_at_ms) {
                    to_expired()
                    return@launch
                }
                val current = _state.value
                if (current is guard_wait_state.waiting) {
                    _state.value = current.copy(seconds_left = seconds_left())
                }
                if (elapsed % GUARD_POLL_INTERVAL_MS != 0L) continue
                val c = challenge ?: return@launch
                try {
                    val status = api.sign_in_status(c.guard_token)
                    if (apply_status(status.status, status.apply_after)) return@launch
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    if (t is ApiError.GuardRetry) {
                        delay(t.retry_after_secs.coerceIn(1, 60) * 1000)
                    } else if (is_final_error(t)) {
                        handle_error(t)
                        return@launch
                    }
                }
            }
        }
    }

    private fun apply_status(status: String, apply_after: String?): Boolean = when (status) {
        STATUS_APPROVED -> {
            viewModelScope.launch { finish() }
            true
        }
        STATUS_DENIED -> {
            stop_with(guard_wait_state.denied)
            true
        }
        STATUS_EXPIRED, STATUS_COMPLETED -> {
            to_expired()
            true
        }
        STATUS_RESCUE_PENDING -> {
            stop_with(guard_wait_state.held(apply_after.orEmpty()))
            true
        }
        else -> false
    }

    private suspend fun finish() {
        if (finishing) return
        finishing = true
        poll_job?.cancel()
        val c = challenge ?: return
        if (c.credentials == null) {
            stop_with(guard_wait_state.needs_password)
            return
        }
        _state.value = guard_wait_state.approved
        val k = keys
        var code_secret: ByteArray? = null
        try {
            val complete = api.sign_in_complete(c.guard_token)
            val guard = complete.guard
            val passphrase = guard?.approval_envelope?.let { env ->
                if (k == null) null else deps.open_envelope(DeviceEnvelope.base64url_decode(env), k)
            }
            val device_id = guard?.let { runCatching { UUID.fromString(it.device_id) }.getOrNull() }
            code_secret = if (guard != null && device_id != null && k != null) {
                runCatching {
                    GuardContextSeal.open(
                        DeviceEnvelope.base64url_decode(guard.code_secret_sealed),
                        GuardContextSeal.code_secret_aad(device_id),
                        k.x25519_sk,
                        k.mlkem_sk,
                    )
                }.getOrNull()
            } else {
                null
            }
            deps.complete(c, complete, passphrase)
            val user = runCatching { UUID.fromString(complete.login.user_id) }.getOrNull()
            val secret = code_secret
            val alias = action_alias
            if (user != null && device_id != null && secret != null && k != null) {
                val previous = store.stored_action_alias(user)
                val saved = store.save_enrollment(
                    user, device_id, secret, k.x25519_sk, k.mlkem_sk, k.x25519_pk, k.mlkem_pk, k.ed25519_pk, k.ed25519_sk,
                    alias,
                )
                if (saved) {
                    if (alias == fresh_alias) fresh_alias = null
                    if (previous != null && previous != alias) deps.delete_action_key(previous)
                }
            }
            stop_with(guard_wait_state.done)
        } catch (t: CancellationException) {
            throw t
        } catch (t: GuardCredentialsMissing) {
            stop_with(guard_wait_state.needs_password)
        } catch (t: Throwable) {
            handle_error(t)
        } finally {
            code_secret?.fill(0)
        }
    }

    private fun handle_error(t: Throwable) {
        when {
            t is ApiError.GuardDevicePending -> stop_with(guard_wait_state.held(t.activates_at))
            api_error_code(t) == GUARD_REQUEST_GONE_CODE -> to_expired()
            else -> stop_with(
                org.astermail.android.guard.guard_error_message_for(t, Instant.ofEpochMilli(deps.now_ms()))
                    ?.let { guard_wait_state.error(it) }
                    ?: guard_wait_state.failed,
            )
        }
    }

    private fun is_transient_error(t: Throwable): Boolean =
        t is ApiError.NetworkError || t is ApiError.ServerError || t is java.io.IOException

    private fun rescue_notice_for(t: Throwable): guard_rescue_notice {
        if (!is_transient_error(t)) return guard_rescue_notice.invalid
        return org.astermail.android.guard.guard_error_message_for(t)?.let { guard_rescue_notice.error(it) }
            ?: guard_rescue_notice.invalid
    }

    private fun is_final_error(t: Throwable): Boolean =
        t is ApiError.GuardDevicePending || api_error_code(t) == GUARD_REQUEST_GONE_CODE

    private fun set_notice(notice: guard_wait_notice) {
        val current = _state.value
        if (current is guard_wait_state.waiting) _state.value = current.copy(notice = notice)
    }

    private fun waiting_state(notice: guard_wait_notice?) = guard_wait_state.waiting(number, seconds_left(), notice, expires_at_ms)

    private fun seconds_left(): Long = ((expires_at_ms - deps.now_ms()) / 1000).coerceAtLeast(0)

    private fun to_expired() = stop_with(guard_wait_state.expired)

    private fun stop_with(next: guard_wait_state) {
        poll_job?.cancel()
        store.clear_pending_sign_in()
        if (next != guard_wait_state.done) {
            challenge?.credentials?.wipe()
            drop_fresh_key()
        }
        _state.value = next
    }

    private fun drop_fresh_key() {
        fresh_alias?.let { deps.delete_action_key(it) }
        if (action_alias == fresh_alias) action_alias = null
        fresh_alias = null
    }

    private fun is_enrolled_alias(alias: String): Boolean =
        store.enrolled_users().any { store.stored_action_alias(it) == alias }

    private fun parse_ms(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(deps.now_ms() + DEFAULT_WINDOW_MS)

    override fun onCleared() {
        poll_job?.cancel()
        rescue_job?.cancel()
        challenge?.credentials?.wipe()
        challenge = null
        super.onCleared()
    }

    companion object {
        const val STATUS_APPROVED = "approved"
        const val STATUS_DENIED = "denied"
        const val STATUS_EXPIRED = "expired"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_RESCUE_PENDING = "rescue_pending"
        const val RESCUE_APPLIED = "applied"
        const val RESCUE_VETOED = "vetoed"
        const val RESCUE_CANCELLED = "cancelled"
        private const val DEFAULT_WINDOW_MS = 5 * 60_000L

        fun normalize_code(code: String): String =
            code.uppercase().filter { it != ' ' && it != '-' }
    }
}
