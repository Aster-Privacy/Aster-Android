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
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.templates.CreateTemplateRequest
import org.astermail.android.api.templates.TemplateItem
import org.astermail.android.api.templates.TemplatesApi
import org.astermail.android.api.templates.UpdateTemplateRequest
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.storage.SessionKeyStore

data class DecryptedTemplate(
    val id: String,
    val name: String,
    val category: String,
    val content: String,
    val sort_order: Int,
)

data class TemplatesUiState(
    val items: List<DecryptedTemplate> = emptyList(),
    val is_loading: Boolean = false,
    val is_saving: Boolean = false,
    val error: String? = null,
    val draft: DecryptedTemplate? = null,
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val api: TemplatesApi,
    private val session_key_store: SessionKeyStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            val result = runCatching {
                val response = api.list()
                val key = derive_key() ?: error("session expired")
                try {
                    response.templates.mapNotNull { decrypt(it, key) }
                } finally {
                    key.fill(0)
                }
            }
            _state.value = result.fold(
                onSuccess = { _state.value.copy(is_loading = false, items = it.sortedBy { t -> t.sort_order }) },
                onFailure = { _state.value.copy(is_loading = false, error = readable_error(it)) },
            )
        }
    }

    fun start_new() {
        _state.value = _state.value.copy(
            draft = DecryptedTemplate(id = "", name = "", category = "", content = "", sort_order = next_order()),
        )
    }

    fun start_edit(id: String) {
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(draft = item)
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
        )
    }

    fun save_draft() {
        val draft = _state.value.draft ?: return
        if (draft.name.isBlank()) {
            _state.value = _state.value.copy(error = context.getString(R.string.name_cannot_be_empty))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(is_saving = true, error = null)
            val outcome = runCatching {
                val key = derive_key() ?: error("session expired")
                try {
                    val name_field = encrypt_to_b64(draft.name, key)
                    val category_field = encrypt_to_b64(draft.category, key)
                    val content_field = encrypt_to_b64(draft.content, key)

                    if (draft.id.isBlank()) {
                        api.create(
                            CreateTemplateRequest(
                                encrypted_name = name_field.first,
                                name_nonce = name_field.second,
                                encrypted_category = category_field.first,
                                category_nonce = category_field.second,
                                encrypted_content = content_field.first,
                                content_nonce = content_field.second,
                                sort_order = draft.sort_order,
                            ),
                        )
                    } else {
                        api.update(
                            draft.id,
                            UpdateTemplateRequest(
                                encrypted_name = name_field.first,
                                name_nonce = name_field.second,
                                encrypted_category = category_field.first,
                                category_nonce = category_field.second,
                                encrypted_content = content_field.first,
                                content_nonce = content_field.second,
                                sort_order = draft.sort_order,
                            ),
                        )
                    }
                } finally {
                    key.fill(0)
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
        viewModelScope.launch {
            _state.value = _state.value.copy(is_saving = true, error = null)
            val outcome = runCatching { api.delete(id) }
            outcome.fold(
                onSuccess = {
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

    private fun derive_key(): ByteArray? {
        val passphrase = session_key_store.get_passphrase() ?: return null
        return try {
            CryptoNative.derive_storage_key(passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    private fun decrypt(item: TemplateItem, key: ByteArray): DecryptedTemplate? = runCatching {
        DecryptedTemplate(
            id = item.id,
            name = decrypt_b64_to_string(item.encrypted_name, item.name_nonce, key),
            category = decrypt_b64_to_string(item.encrypted_category, item.category_nonce, key),
            content = decrypt_b64_to_string(item.encrypted_content, item.content_nonce, key),
            sort_order = item.sort_order,
        )
    }.getOrNull()

    private fun decrypt_b64_to_string(ct_b64: String, nonce_b64: String, key: ByteArray): String {
        val ct = decode_b64_flex(ct_b64)
        val nonce = decode_b64_flex(nonce_b64)
        val plain = CryptoNative.decrypt_field(ct, nonce, key)
        return String(plain, Charsets.UTF_8)
    }

    private fun decode_b64_flex(input: String): ByteArray {
        return runCatching { Base64.decode(input, Base64.DEFAULT) }
            .recoverCatching { Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING) }
            .getOrThrow()
    }

    private fun encrypt_to_b64(plaintext: String, key: ByteArray): Pair<String, String> {
        val field = CryptoNative.encrypt_field(plaintext.toByteArray(Charsets.UTF_8), key)
        return Base64.encodeToString(field.ciphertext, Base64.NO_WRAP) to
            Base64.encodeToString(field.nonce, Base64.NO_WRAP)
    }

    private fun next_order(): Int = (_state.value.items.maxOfOrNull { it.sort_order } ?: 0) + 1

    private fun readable_error(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.something_went_wrong)
}
