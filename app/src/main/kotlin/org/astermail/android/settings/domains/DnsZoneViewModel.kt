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
package org.astermail.android.settings.domains

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.api.domains.DnsZoneApi
import org.astermail.android.api.domains.DnsZoneRecord
import org.astermail.android.api.domains.DnsZoneRecordRequest
import org.astermail.android.api.security.ReauthProofRequest
import org.astermail.android.api.security.SecurityApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.settings.ReauthPrompt
import org.astermail.android.settings.Reauthorized
import org.astermail.android.settings.reauthorize

enum class DnsZonePhase { idle, loading, unavailable, unmanaged, locked, managed, failed }

enum class DnsPendingOp { adding, saving, deleting }

enum class DnsDraftField { host, value, ttl, priority, weight, port }

sealed interface DnsZoneMutation {
    data class create(val request: DnsZoneRecordRequest) : DnsZoneMutation
    data class update(val record_id: String, val request: DnsZoneRecordRequest) : DnsZoneMutation
    data class delete(val record: DnsZoneRecord) : DnsZoneMutation
}

data class DnsZoneUiState(
    val domain_id: String? = null,
    val domain_name: String = "",
    val phase: DnsZonePhase = DnsZonePhase.idle,
    val records: List<DnsZoneRecord> = emptyList(),
    val max_records: Int = 0,
    val pending: Map<String, DnsPendingOp> = emptyMap(),
    val refreshing: Boolean = false,
    val load_error: DnsZoneReason? = null,
    val action_error: DnsZoneReason? = null,
    val saving: Boolean = false,
    val save_error: DnsZoneReason? = null,
    val save_done_count: Int = 0,
    val reauth: ReauthPrompt? = null,
) {
    val editable_count: Int get() = records.count { !it.protected }
    val at_limit: Boolean get() = max_records > 0 && records.size >= max_records
}

const val DNS_PENDING_ID_PREFIX = "pending:"

fun dns_zone_reason_field(reason: DnsZoneReason?): DnsDraftField? = when (reason) {
    DnsZoneReason.invalid_host,
    DnsZoneReason.wildcard,
    DnsZoneReason.protected_host,
    DnsZoneReason.apex_mx,
    DnsZoneReason.apex_cname,
    DnsZoneReason.cname_conflict,
    DnsZoneReason.invalid_srv_host,
    DnsZoneReason.dkim_cname -> DnsDraftField.host
    DnsZoneReason.invalid_value,
    DnsZoneReason.invalid_ip,
    DnsZoneReason.private_ip,
    DnsZoneReason.invalid_target,
    DnsZoneReason.cname_self,
    DnsZoneReason.txt_too_long,
    DnsZoneReason.invalid_txt,
    DnsZoneReason.apex_spf,
    DnsZoneReason.spf_conflict,
    DnsZoneReason.duplicate,
    DnsZoneReason.invalid_srv_value,
    DnsZoneReason.invalid_caa_flags,
    DnsZoneReason.invalid_caa_tag,
    DnsZoneReason.invalid_caa_value,
    DnsZoneReason.missing_field -> DnsDraftField.value
    DnsZoneReason.invalid_ttl -> DnsDraftField.ttl
    DnsZoneReason.invalid_priority -> DnsDraftField.priority
    DnsZoneReason.invalid_weight -> DnsDraftField.weight
    DnsZoneReason.invalid_port -> DnsDraftField.port
    else -> null
}

fun sort_zone_records(records: List<DnsZoneRecord>): List<DnsZoneRecord> =
    records.sortedWith(
        compareBy<DnsZoneRecord>(
            { if (it.protected) 1 else 0 },
            { it.type.uppercase() },
            { if (it.host == "@") "" else it.host.lowercase() },
            { it.priority ?: 0 },
        ),
    )

@HiltViewModel
class DnsZoneViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: DnsZoneApi,
    private val auth_repository: AuthRepository,
    private val security_api: SecurityApi,
) : ViewModel() {

    private val _state = MutableStateFlow(DnsZoneUiState())
    val state: StateFlow<DnsZoneUiState> = _state.asStateFlow()

    private var generation = 0
    private var pending_counter = 0
    private var deferred: DnsZoneMutation? = null

    fun bind(domain_id: String, domain_name: String) {
        if (_state.value.domain_id == domain_id) return
        generation += 1
        deferred = null
        _state.value = DnsZoneUiState(domain_id = domain_id, domain_name = domain_name)
        load()
    }

    fun refresh() = load(force = true)

    fun load(force: Boolean = false) {
        val current = _state.value
        val domain_id = current.domain_id ?: return
        if (!force && current.phase != DnsZonePhase.idle && current.phase != DnsZonePhase.failed) return
        if (current.refreshing) return
        val gen = generation
        val has_content = current.phase == DnsZonePhase.managed
        _state.update {
            it.copy(
                phase = if (has_content) it.phase else DnsZonePhase.loading,
                refreshing = true,
                load_error = null,
            )
        }
        viewModelScope.launch {
            try {
                val zone = api.get_zone(domain_id)
                if (gen != generation) return@launch
                _state.update {
                    if (!zone.managed) {
                        it.copy(phase = DnsZonePhase.unmanaged, records = emptyList(), refreshing = false, pending = emptyMap())
                    } else {
                        it.copy(
                            phase = DnsZonePhase.managed,
                            records = sort_zone_records(zone.records),
                            max_records = zone.limits.max_records,
                            refreshing = false,
                            pending = emptyMap(),
                        )
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (gen != generation) return@launch
                val reason = dns_zone_reason(t)
                _state.update {
                    when {
                        is_dns_zone_unavailable(t) -> it.copy(phase = DnsZonePhase.unavailable, refreshing = false)
                        reason == DnsZoneReason.locked -> it.copy(phase = DnsZonePhase.locked, refreshing = false)
                        it.phase == DnsZonePhase.managed -> it.copy(refreshing = false, action_error = reason)
                        else -> it.copy(phase = DnsZonePhase.failed, refreshing = false, load_error = reason)
                    }
                }
            }
        }
    }

    fun validate(draft: DnsRecordDraft, replacing_id: String?): DnsDraftResult {
        val s = _state.value
        return validate_dns_draft(draft, s.domain_name, s.records.filterNot { it.id.startsWith(DNS_PENDING_ID_PREFIX) }, replacing_id)
    }

    fun submit(draft: DnsRecordDraft, replacing_id: String?): DnsDraftResult {
        val result = validate(draft, replacing_id)
        val request = result.request ?: return result
        if (_state.value.saving) return result
        if (replacing_id == null && _state.value.at_limit) {
            _state.update { it.copy(save_error = DnsZoneReason.too_many_records) }
            return result
        }
        val mutation = if (replacing_id == null) {
            DnsZoneMutation.create(request)
        } else {
            DnsZoneMutation.update(replacing_id, request)
        }
        run_mutation(mutation)
        return result
    }

    fun delete(record: DnsZoneRecord) {
        if (record.protected || record.id.startsWith(DNS_PENDING_ID_PREFIX)) return
        if (_state.value.pending.containsKey(record.id)) return
        run_mutation(DnsZoneMutation.delete(record))
    }

    fun clear_save_error() = _state.update { it.copy(save_error = null) }

    fun clear_action_error() = _state.update { it.copy(action_error = null) }

    fun dismiss_reauth() {
        deferred = null
        _state.update { it.copy(reauth = null, saving = false) }
    }

    fun confirm_reauth(password: String, totp_code: String?, get_credential: suspend (String) -> String) {
        val prompt = _state.value.reauth ?: return
        if (prompt.busy) return
        val domain_id = _state.value.domain_id ?: return
        val gen = generation
        _state.update { it.copy(reauth = prompt.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val outcome = reauthorize(
                context = context,
                prompt = prompt,
                password = password,
                totp_code = totp_code,
                derive_password_hash = { auth_repository.derive_password_hash_b64(it) },
                load_key_options = { load_key_options() },
                get_credential = get_credential,
                call = { proof: ReauthProofRequest -> api.unlock(domain_id, proof) },
            )
            if (gen != generation) return@launch
            when (outcome) {
                is Reauthorized.Done -> {
                    val next = deferred
                    deferred = null
                    _state.update { it.copy(reauth = null) }
                    if (next != null) run_mutation(next) else _state.update { it.copy(saving = false) }
                }
                is Reauthorized.Retry -> _state.update { it.copy(reauth = outcome.prompt) }
            }
        }
    }

    private suspend fun load_key_options(): WebAuthnAssertionOptions = security_api.step_up_assertion_options()

    private fun run_mutation(mutation: DnsZoneMutation) {
        val domain_id = _state.value.domain_id ?: return
        val gen = generation
        val snapshot = _state.value.records
        val pending_id = when (mutation) {
            is DnsZoneMutation.create -> "$DNS_PENDING_ID_PREFIX${++pending_counter}"
            is DnsZoneMutation.update -> mutation.record_id
            is DnsZoneMutation.delete -> mutation.record.id
        }
        _state.update { s ->
            when (mutation) {
                is DnsZoneMutation.create -> s.copy(
                    saving = true,
                    save_error = null,
                    records = sort_zone_records(s.records + mutation.request.as_record(pending_id)),
                    pending = s.pending + (pending_id to DnsPendingOp.adding),
                )
                is DnsZoneMutation.update -> s.copy(
                    saving = true,
                    save_error = null,
                    records = sort_zone_records(
                        s.records.map { if (it.id == mutation.record_id) mutation.request.as_record(it.id) else it },
                    ),
                    pending = s.pending + (pending_id to DnsPendingOp.saving),
                )
                is DnsZoneMutation.delete -> s.copy(
                    action_error = null,
                    pending = s.pending + (pending_id to DnsPendingOp.deleting),
                )
            }
        }
        viewModelScope.launch {
            try {
                val returned: DnsZoneRecord? = when (mutation) {
                    is DnsZoneMutation.create -> api.create_record(domain_id, mutation.request)
                    is DnsZoneMutation.update -> api.update_record(domain_id, mutation.record_id, mutation.request)
                    is DnsZoneMutation.delete -> {
                        api.delete_record(domain_id, mutation.record.id)
                        null
                    }
                }
                if (gen != generation) return@launch
                val needs_refresh = mutation !is DnsZoneMutation.delete && (returned == null || returned.id.isBlank())
                _state.update { s ->
                    val records = when (mutation) {
                        is DnsZoneMutation.delete -> s.records.filterNot { it.id == pending_id }
                        else -> if (returned != null && returned.id.isNotBlank()) {
                            s.records.map { if (it.id == pending_id) returned else it }
                        } else {
                            s.records
                        }
                    }
                    s.copy(
                        records = sort_zone_records(records),
                        pending = s.pending - pending_id,
                        saving = if (mutation is DnsZoneMutation.delete) s.saving else false,
                        save_done_count = if (mutation is DnsZoneMutation.delete) s.save_done_count else s.save_done_count + 1,
                    )
                }
                if (needs_refresh) load(force = true)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (gen != generation) return@launch
                val reason = dns_zone_reason(t)
                if (reason == DnsZoneReason.step_up) deferred = mutation
                _state.update { s ->
                    val restored = restore(s.records, snapshot, mutation, pending_id)
                    val base = s.copy(records = sort_zone_records(restored), pending = s.pending - pending_id)
                    when {
                        reason == DnsZoneReason.step_up -> base.copy(reauth = ReauthPrompt())
                        mutation is DnsZoneMutation.delete -> base.copy(action_error = reason)
                        else -> base.copy(saving = false, save_error = reason)
                    }
                }
                if (reason == DnsZoneReason.zone_not_managed || reason == DnsZoneReason.locked) load(force = true)
            }
        }
    }

    private fun restore(
        current: List<DnsZoneRecord>,
        snapshot: List<DnsZoneRecord>,
        mutation: DnsZoneMutation,
        pending_id: String,
    ): List<DnsZoneRecord> = when (mutation) {
        is DnsZoneMutation.create -> current.filterNot { it.id == pending_id }
        is DnsZoneMutation.update -> {
            val original = snapshot.firstOrNull { it.id == mutation.record_id }
            if (original == null) current else current.map { if (it.id == mutation.record_id) original else it }
        }
        is DnsZoneMutation.delete -> current
    }
}

private fun DnsZoneRecordRequest.as_record(id: String): DnsZoneRecord = DnsZoneRecord(
    id = id,
    type = type,
    host = host,
    value = value,
    ttl = ttl,
    priority = priority,
)
