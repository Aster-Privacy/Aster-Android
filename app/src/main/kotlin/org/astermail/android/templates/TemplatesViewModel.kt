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

package org.astermail.android.templates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.templates.TemplateItem
import org.astermail.android.api.templates.TemplatesApi
import org.astermail.android.crypto.FieldFormat
import org.astermail.android.crypto.StorageWriteKey
import org.astermail.android.mail.is_secret_identity_key
import org.astermail.android.storage.SessionKeyStore

private const val TEMPLATE_KEY_POLL_MS = 300L
const val TEMPLATE_KEY_SETTLE_MAX_MS = 20_000L

const val TEMPLATE_FIELD_KIND = FieldFormat.KIND_TEMPLATE
const val TEMPLATE_FIELD_NAME = "name"
const val TEMPLATE_FIELD_CATEGORY = "category"
const val TEMPLATE_FIELD_CONTENT = "content"

data class DecryptedTemplate(
    val id: String,
    val name: String,
    val category: String,
    val content: String,
    val sort_order: Int,
)

data class TemplatesUiState(
    val items: List<DecryptedTemplate> = emptyList(),
    val is_loading: Boolean = true,
    val keys_pending: Boolean = false,
    val is_saving: Boolean = false,
    val error: String? = null,
    val load_error: String? = null,
    val undecryptable_count: Int = 0,
    val draft: DecryptedTemplate? = null,
)

data class TemplateLoadOutcome(
    val items: List<DecryptedTemplate>,
    val undecryptable_count: Int,
)

fun template_keys_pending(undecryptable_count: Int, keys_settled: Boolean, waited_ms: Long): Boolean =
    undecryptable_count > 0 && !keys_settled && waited_ms < TEMPLATE_KEY_SETTLE_MAX_MS

fun template_load_outcome(decrypted: List<DecryptedTemplate?>): TemplateLoadOutcome =
    TemplateLoadOutcome(
        items = decrypted.filterNotNull(),
        undecryptable_count = decrypted.count { it == null },
    )

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val api: TemplatesApi,
    private val session_key_store: SessionKeyStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    private val open_lock = Mutex()
    private var load_job: Job? = null
    private var listing: List<TemplateItem>? = null
    private var listing_generation = 0L
    private var listed_at_ms = 0L
    private var opened_epoch = -1L
    private val deleted_ids = mutableSetOf<String>()

    init {
        watch_key_readiness()
    }

    fun load() {
        load_job?.cancel()
        load_job = viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null, load_error = null)
            val fetched = try {
                api.list().templates
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = _state.value.copy(
                    is_loading = false,
                    keys_pending = false,
                    load_error = readable_error(t),
                    undecryptable_count = 0,
                )
                return@launch
            }
            listing_generation += 1
            listing = fetched.filterNot { it.id in deleted_ids }
            listed_at_ms = System.currentTimeMillis()
            open_listing()
        }
    }

    private suspend fun open_listing() {
        open_lock.withLock {
            val current = listing ?: return
            val generation = listing_generation
            val epoch = readiness_epoch()
            val settled = keys_settled()
            val outcome = withContext(Dispatchers.Default) { open_with_session_keys(current) }
            if (generation != listing_generation) return
            opened_epoch = epoch
            val pending = template_keys_pending(
                outcome.undecryptable_count,
                settled,
                System.currentTimeMillis() - listed_at_ms,
            )
            _state.value = _state.value.copy(
                is_loading = pending,
                keys_pending = pending,
                items = outcome.items.filterNot { it.id in deleted_ids },
                undecryptable_count = if (pending) 0 else outcome.undecryptable_count,
            )
        }
    }

    private fun open_with_session_keys(items: List<TemplateItem>): TemplateLoadOutcome {
        if (items.isEmpty()) return TemplateLoadOutcome(emptyList(), 0)
        val keys = session_template_keys()
        try {
            val result = open_templates(items, keys)
            result.opening_keys.forEach { StorageWriteKey.note_if_public(session_key_store, it) }
            return result.outcome
        } finally {
            keys.forEach { it.fill(0) }
        }
    }

    private fun session_template_keys(): List<ByteArray> {
        val storage_keys = runCatching { StorageWriteKey.read_keys(session_key_store) }.getOrDefault(emptyList())
        val identity_keys = runCatching {
            buildList {
                session_key_store.get_identity_key()?.let { add(it) }
                session_key_store.get_previous_keys()?.let { addAll(it) }
            }.filter(::is_secret_identity_key)
        }.getOrDefault(emptyList())
        return storage_keys + template_context_keys(identity_keys)
    }

    private fun watch_key_readiness() {
        val events = runCatching {
            combine(
                session_key_store.identity_key_events(),
                session_key_store.account_keys_ready_events(),
                session_key_store.key_material_events(),
            ) { identity, account, material -> identity + account + material }
        }.getOrNull() ?: return
        viewModelScope.launch {
            while (true) {
                try {
                    if (_state.value.keys_pending) {
                        withTimeoutOrNull(TEMPLATE_KEY_POLL_MS) { events.first { it != opened_epoch } }
                        val waited_ms = System.currentTimeMillis() - listed_at_ms
                        val unchanged = readiness_epoch() == opened_epoch
                        if (unchanged && template_keys_pending(1, keys_settled(), waited_ms)) continue
                    } else {
                        combine(events, _state) { epoch, current ->
                            current.keys_pending || (epoch != opened_epoch && current.undecryptable_count > 0)
                        }.first { it }
                        if (_state.value.keys_pending) continue
                    }
                    if (load_job?.isActive != true) open_listing()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    delay(TEMPLATE_KEY_POLL_MS)
                }
            }
        }
    }

    private fun readiness_epoch(): Long =
        runCatching { session_key_store.key_readiness_epoch() }.getOrDefault(0L)

    private fun keys_settled(): Boolean =
        runCatching { session_key_store.keys_settled() }.getOrDefault(true)

    fun start_new() {
        _state.value = _state.value.copy(
            draft = DecryptedTemplate(id = "", name = "", category = "", content = "", sort_order = next_order()),
            error = null,
        )
    }

    fun start_edit(id: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(draft = item, error = null)
    }

    fun cancel_edit() {
        _state.value = _state.value.copy(draft = null, error = null)
    }

    fun update_draft(name: String? = null, category: String? = null, content: String? = null) {
        val current = _state.value.draft ?: return
        _state.value = _state.value.copy(
            draft = current.copy(
                name = name ?: current.name,
                category = category ?: current.category,
                content = content ?: current.content,
            ),
            error = null,
        )
    }

    fun save_draft() {
        if (_state.value.is_saving) return
        val current = _state.value.draft ?: return
        if (current.name.isBlank()) {
            _state.value = _state.value.copy(error = context.getString(R.string.name_cannot_be_empty))
            return
        }
        if (current.content.isBlank()) {
            _state.value = _state.value.copy(error = context.getString(R.string.template_content_required))
            return
        }
        val draft = current.copy(
            name = current.name.trim(),
            category = current.category.trim().ifBlank { context.getString(R.string.template_category_general) },
            content = current.content.trim(),
        )
        viewModelScope.launch {
            _state.value = _state.value.copy(is_saving = true, error = null)
            val outcome = runCatching {
                withContext(Dispatchers.Default) {
                    val key = org.astermail.android.crypto.StorageKeyGate.await_write_key(session_key_store)
                    try {
                        store_template(api, key, draft)
                    } finally {
                        key.fill(0)
                    }
                }
            }
            outcome.fold(
                onSuccess = {
                    _state.value = _state.value.copy(is_saving = false, draft = null)
                    load()
                },
                onFailure = {
                    _state.value = _state.value.copy(is_saving = false, error = readable_error(it))
                },
            )
        }
    }

    fun delete(id: String) {
        if (_state.value.is_saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_saving = true, error = null)
            val outcome = runCatching { api.delete(id) }
            outcome.fold(
                onSuccess = {
                    deleted_ids.add(id)
                    listing = listing?.filterNot { it.id == id }
                    _state.value = _state.value.copy(
                        is_saving = false,
                        items = _state.value.items.filter { it.id != id },
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(is_saving = false, error = readable_error(it))
                },
            )
        }
    }

    private fun next_order(): Int =
        maxOf(
            _state.value.items.maxOfOrNull { it.sort_order } ?: 0,
            listing?.maxOfOrNull { it.sort_order } ?: 0,
        ) + 1

    private fun readable_error(t: Throwable): String =
        org.astermail.android.localized_api_error(context, t, context.getString(R.string.something_went_wrong))
}
