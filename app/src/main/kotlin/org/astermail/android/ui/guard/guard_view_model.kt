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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.GUARD_ENVELOPE_REQUIRED_CODE
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.GUARD_WRONG_NUMBER_CODE
import org.astermail.android.api.api_error_code
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardNewDevice
import org.astermail.android.api.guard.GuardRequestItem
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.GuardKeyLostException
import org.astermail.android.guard.GuardNoDeviceException
import org.astermail.android.guard.GuardRepository
import org.astermail.android.guard.guard_context_view
import org.astermail.android.guard.guard_event
import org.astermail.android.guard.guard_local_approvals
import org.astermail.android.guard.guard_push
import org.astermail.android.guard.guard_signer
import org.astermail.android.guard.open_guard_context
import org.astermail.android.notifications.LoginAlertNotifier
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SessionKeyStore

const val GUARD_KIND_JOIN = "join"
const val GUARD_NUMBER_DIGITS = 2

enum class guard_approve_notice { failed }

sealed interface guard_approve_state {
    data object loading : guard_approve_state
    data class ready(
        val view: guard_context_view?,
        val kind: String,
        val device_name: String,
        val created_at: String,
        val number_input: String = "",
        val expires_at: String = "",
        val notice: guard_approve_notice? = null,
        val error: org.astermail.android.guard.guard_error_message? = null,
    ) : guard_approve_state {
        val can_approve: Boolean get() = guard_parse_number(number_input) != null
        val is_join: Boolean get() = kind == GUARD_KIND_JOIN
    }
    data object unlock_first : guard_approve_state
    data object signing : guard_approve_state
    data object approved : guard_approve_state
    data object denied : guard_approve_state
    data object gone : guard_approve_state
    data object mismatched : guard_approve_state
    data object key_lost : guard_approve_state
    data object no_device : guard_approve_state
}

fun guard_parse_number(input: String): Int? {
    if (input.length != GUARD_NUMBER_DIGITS || !input.all { it in '0'..'9' }) return null
    return input.toInt().takeIf { it in GuardDigests.NUMBER_MIN..GuardDigests.NUMBER_MAX }
}

fun guard_supported_envelope_version(offered: List<Int>): Int? =
    offered.filter { it == DeviceEnvelope.ENVELOPE_V1 || it == DeviceEnvelope.ENVELOPE_V2 }.maxOrNull()

class guard_new_device_keys(
    val ed25519: ByteArray,
    val mlkem: ByteArray,
    val x25519: ByteArray,
    val digest: ByteArray,
)

fun guard_verified_device_keys(device: GuardNewDevice): guard_new_device_keys? = runCatching {
    val ed = DeviceEnvelope.base64url_decode(device.ed25519_pk)
    val mlkem = DeviceEnvelope.base64url_decode(device.mlkem_pk)
    val x = DeviceEnvelope.base64url_decode(device.x25519_pk)
    val action = device.action_pk?.takeIf { it.isNotBlank() }?.let { DeviceEnvelope.base64url_decode(it) }
        ?: GuardDigests.ABSENT_ACTION_PK
    val digest = GuardDigests.keys_digest(ed, mlkem, x, action)
    val claimed = DeviceEnvelope.base64url_decode(device.keys_digest)
    if (!java.security.MessageDigest.isEqual(digest, claimed)) return null
    guard_new_device_keys(ed, mlkem, x, digest)
}.getOrNull()

interface guard_approve_deps {
    fun user_id(): UUID?
    fun passphrase(): ByteArray?
    fun seal(passphrase: ByteArray, keys: guard_new_device_keys, version: Int): ByteArray
    fun cancel_notification(request_id: String, account_id: String)
    fun record_approval(request_id: String, account_id: String) {
        guard_local_approvals.record(request_id, account_id)
    }
    fun forget_approval(request_id: String, account_id: String) {
        guard_local_approvals.forget(request_id, account_id)
    }
}

class default_guard_approve_deps(
    private val context: android.content.Context,
    private val accounts: AccountStore,
    private val session_keys: SessionKeyStore,
) : guard_approve_deps {
    override fun user_id(): UUID? =
        accounts.get_current_id()?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    override fun passphrase(): ByteArray? = session_keys.get_passphrase()

    override fun seal(passphrase: ByteArray, keys: guard_new_device_keys, version: Int): ByteArray =
        DeviceEnvelope.seal_secret_for_device(
            secret = passphrase,
            recipient = DeviceEnvelope.DeviceRecipient(keys.ed25519, keys.mlkem, keys.x25519),
            version = version,
        )

    override fun cancel_notification(request_id: String, account_id: String) {
        LoginAlertNotifier.cancel_guard(context, guard_push(guard_event.sign_in_request, request_id, account_id))
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GuardApproveModule {
    @Provides
    fun provide_guard_approve_deps(
        @ApplicationContext context: android.content.Context,
        accounts: AccountStore,
        session_keys: SessionKeyStore,
    ): guard_approve_deps = default_guard_approve_deps(context, accounts, session_keys)
}

@HiltViewModel
class GuardApproveViewModel @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val deps: guard_approve_deps,
    saved: SavedStateHandle,
) : ViewModel() {

    val request_id: String = saved.get<String>(ARG_ID).orEmpty()

    private val _state = MutableStateFlow<guard_approve_state>(guard_approve_state.loading)
    val state: StateFlow<guard_approve_state> = _state.asStateFlow()

    private var item: GuardRequestItem? = null
    private var device_keys: guard_new_device_keys? = null
    private var last_ready: guard_approve_state.ready? = null

    init {
        viewModelScope.launch { load() }
    }

    private fun repository(user: UUID) = GuardRepository(api, store) { user }

    suspend fun load() {
        val user = deps.user_id() ?: return set(guard_approve_state.gone)
        val request_uuid = runCatching { UUID.fromString(request_id) }.getOrNull()
            ?: return set(guard_approve_state.gone)
        val found = try {
            api.requests().requests.firstOrNull { it.id == request_id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (found == null) return gone(user)
        val keys = guard_verified_device_keys(found.new_device) ?: return gone(user)
        item = found
        device_keys = keys
        val device_id = store.device_id(user)
        val box = store.box_keys(user)
        val view = if (device_id != null && box != null) {
            open_guard_context(found.context, request_uuid, device_id, box)
        } else {
            null
        }
        val ready = guard_approve_state.ready(
            view = view,
            kind = found.kind,
            device_name = found.new_device.name,
            created_at = found.created_at,
            expires_at = found.expires_at,
        )
        last_ready = ready
        if (!has_passphrase()) set(guard_approve_state.unlock_first) else set(ready)
    }

    fun on_number(input: String) {
        val current = _state.value as? guard_approve_state.ready ?: return
        val digits = input.filter { it in '0'..'9' }.take(GUARD_NUMBER_DIGITS)
        val next = current.copy(number_input = digits, notice = null, error = null)
        last_ready = next
        set(next)
    }

    fun retry_unlocked() {
        val ready = last_ready ?: return
        if (has_passphrase()) set(ready)
    }

    fun approve(signer: guard_signer) {
        val current = _state.value as? guard_approve_state.ready ?: return
        val number = guard_parse_number(current.number_input) ?: return
        viewModelScope.launch { run_approve(signer, current, number) }
    }

    fun deny(signer: guard_signer) {
        val current = _state.value as? guard_approve_state.ready ?: return
        viewModelScope.launch { run_deny(signer, current) }
    }

    private suspend fun run_approve(signer: guard_signer, current: guard_approve_state.ready, number: Int) {
        val user = deps.user_id() ?: return set(guard_approve_state.gone)
        val found = item ?: return set(guard_approve_state.gone)
        val keys = device_keys ?: return set(guard_approve_state.gone)
        val request_uuid = UUID.fromString(found.id)
        val version = guard_supported_envelope_version(found.envelope_versions) ?: DeviceEnvelope.ENVELOPE_V2
        val passphrase = deps.passphrase() ?: return set(guard_approve_state.unlock_first)
        val envelope = try {
            DeviceEnvelope.base64url_encode(deps.seal(passphrase, keys, version))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return set(current.copy(notice = guard_approve_notice.failed))
        } finally {
            passphrase.fill(0)
        }
        set(guard_approve_state.signing)
        val subject = GuardDigests.approve_subject(request_uuid, keys.digest, number)
        try {
            val action = repository(user).signed_with(signer, GuardActionCode.APPROVE, subject)
                ?: return set(current)
            deps.record_approval(found.id, user.toString())
            try {
                api.approve(found.id, number, envelope, action)
            } catch (t: Throwable) {
                deps.forget_approval(found.id, user.toString())
                throw t
            }
            deps.cancel_notification(found.id, user.toString())
            set(guard_approve_state.approved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handle_error(user, t, current)
        }
    }

    private suspend fun run_deny(signer: guard_signer, current: guard_approve_state.ready) {
        val user = deps.user_id() ?: return set(guard_approve_state.gone)
        val found = item ?: return set(guard_approve_state.gone)
        set(guard_approve_state.signing)
        try {
            val action = repository(user).signed_with(
                signer,
                GuardActionCode.DENY,
                GuardDigests.id_subject(UUID.fromString(found.id)),
            ) ?: return set(current)
            api.deny(found.id, action)
            deps.cancel_notification(found.id, user.toString())
            set(guard_approve_state.denied)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handle_error(user, t, current)
        }
    }

    private fun handle_error(user: UUID, t: Throwable, current: guard_approve_state.ready) {
        when {
            t is GuardKeyLostException -> set(guard_approve_state.key_lost)
            t is GuardNoDeviceException -> set(guard_approve_state.no_device)
            api_error_code(t) == GUARD_WRONG_NUMBER_CODE -> {
                deps.cancel_notification(request_id, user.toString())
                set(guard_approve_state.mismatched)
            }
            api_error_code(t) == GUARD_REQUEST_GONE_CODE -> gone(user)
            api_error_code(t) == GUARD_ENVELOPE_REQUIRED_CODE -> set(guard_approve_state.unlock_first)
            else -> set(current.copy(notice = guard_approve_notice.failed, error = org.astermail.android.guard.guard_error_message_for(t)))
        }
    }

    private fun gone(user: UUID) {
        deps.cancel_notification(request_id, user.toString())
        set(guard_approve_state.gone)
    }

    private fun has_passphrase(): Boolean {
        val p = deps.passphrase() ?: return false
        p.fill(0)
        return true
    }

    private fun set(next: guard_approve_state) {
        if (next is guard_approve_state.ready) last_ready = next
        _state.value = next
    }

    companion object {
        const val ARG_ID = "id"
    }
}
