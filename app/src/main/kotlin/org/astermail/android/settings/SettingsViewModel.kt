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
import org.astermail.android.R
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.UserInfo
import org.astermail.android.api.autoforward.AutoForwardApi
import org.astermail.android.api.autoforward.CreateForwardingRuleRequest
import org.astermail.android.api.autoforward.ForwardingRule
import org.astermail.android.api.autoforward.ToggleForwardingRuleRequest
import org.astermail.android.api.developer.ApiKeyInfo
import org.astermail.android.api.developer.CreateApiKeyRequest
import org.astermail.android.api.developer.DeveloperApi
import org.astermail.android.api.developer.WebhookInfo
import org.astermail.android.api.ghost.CreateGhostAliasRequest
import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.ghost.GhostAliasApi
import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.ReferralInfoResponse
import org.astermail.android.api.tags.CreateTagRequest
import org.astermail.android.api.tags.TagItem
import org.astermail.android.api.tags.TagsApi
import org.astermail.android.api.tags.UpdateTagRequest
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.api.preferences.SaveEncryptedPreferencesRequest
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.BlockedSenderInfo
import org.astermail.android.api.settings.FeedbackRequest
import org.astermail.android.api.settings.SecurityStatusResponse
import org.astermail.android.api.settings.SessionInfo
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.api.settings.StorageOverview
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.user.UserApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.TokenStore

data class SettingsUiState(
    val user: UserInfo? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val blocked_senders: List<BlockedSenderInfo> = emptyList(),
    val aliases: List<AliasInfo> = emptyList(),
    val storage: StorageOverview? = null,
    val subscription: SubscriptionInfo? = null,
    val security_status: SecurityStatusResponse? = null,
    val labels: List<LabelItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val referral: ReferralInfoResponse? = null,
    val preferences: UserPreferences? = null,
    val ghost_aliases: List<GhostAlias> = emptyList(),
    val forwarding_rules: List<ForwardingRule> = emptyList(),
    val api_keys: List<ApiKeyInfo> = emptyList(),
    val webhooks: List<WebhookInfo> = emptyList(),
    val is_loading: Boolean = false,
    val error: String? = null,
    val save_status: SaveStatus = SaveStatus.IDLE,
    val action_result: String? = null,
)

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

data class DecryptedSignature(
    val id: String,
    val name: String,
    val content: String,
    val is_default: Boolean,
    val is_html: Boolean,
    val alias_id: String?,
    val placement: Int?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth_api: AuthApi,
    private val user_api: UserApi,
    private val settings_api: SettingsApi,
    private val labels_api: LabelsApi,
    private val tags_api: TagsApi,
    private val preferences_api: PreferencesApi,
    private val signatures_api: org.astermail.android.api.signatures.SignaturesApi,
    private val ghost_alias_api: GhostAliasApi,
    private val auto_forward_api: AutoForwardApi,
    private val developer_api: DeveloperApi,
    private val subscriptions_api: SubscriptionsApi,
    private val auth_repository: AuthRepository,
    private val session_key_store: SessionKeyStore,
    private val token_store: TokenStore,
    val account_store: org.astermail.android.storage.AccountStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    private val optimistic_label_tokens = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load_preferences()
    }

    private val _signature_text = MutableStateFlow("")
    val signature_text: StateFlow<String> = _signature_text.asStateFlow()

    private val _signature_loaded = MutableStateFlow(false)
    val signature_loaded: StateFlow<Boolean> = _signature_loaded.asStateFlow()

    private val _signatures = MutableStateFlow<List<DecryptedSignature>>(emptyList())
    val signatures: StateFlow<List<DecryptedSignature>> = _signatures.asStateFlow()

    @Volatile private var default_signature_id: String? = null
    @Volatile private var default_signature_is_html: Boolean = false

    fun load_profile() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val user = auth_api.me()
                _state.value = _state.value.copy(user = user, is_loading = false)
                auth_repository.refresh_profile()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun update_display_name(name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                val response = user_api.update_display_name(name)
                _state.value = _state.value.copy(
                    user = response.user,
                    save_status = SaveStatus.SAVED,
                )
                auth_repository.refresh_profile()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    suspend fun update_profile_picture(data_uri: String): Boolean {
        return try {
            val response = user_api.update_profile_picture(data_uri)
            val updated_user = _state.value.user?.copy(profile_picture = response.profile_picture)
            _state.value = _state.value.copy(user = updated_user)
            val current = account_store.get_current()
            if (current != null) {
                account_store.add_or_update(current.copy(profile_picture = response.profile_picture))
            }
            auth_repository.refresh_profile()
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun load_sessions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = settings_api.list_sessions()
                _state.value = _state.value.copy(
                    sessions = response.sessions,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun revoke_session(session_id: String) {
        viewModelScope.launch {
            try {
                settings_api.revoke_session(session_id)
                _state.value = _state.value.copy(
                    sessions = _state.value.sessions.filter { it.id != session_id },
                    action_result = context.getString(R.string.session_revoked),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_revoke_session),
                )
            }
        }
    }

    fun logout_others() {
        viewModelScope.launch {
            try {
                settings_api.logout_others()
                _state.value = _state.value.copy(
                    sessions = _state.value.sessions.filter { it.is_current },
                    action_result = context.getString(R.string.all_other_sessions_signed_out),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_sign_out_other_sessions),
                )
            }
        }
    }

    fun clear_action_result() {
        _state.value = _state.value.copy(action_result = null)
    }

    fun logout() {
        viewModelScope.launch {
            auth_repository.logout()
        }
    }

    fun reset_save_status() {
        _state.value = _state.value.copy(save_status = SaveStatus.IDLE)
    }

    fun reset_for_account_switch() {
        _state.value = SettingsUiState()
    }

    fun load_blocked_senders() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = settings_api.list_blocked_senders()
                _state.value = _state.value.copy(
                    blocked_senders = response.blocked_senders,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun block_sender(address: String) {
        viewModelScope.launch {
            try {
                settings_api.block_sender(address)
                _state.update { s -> s.copy(blocked_senders = s.blocked_senders + BlockedSenderInfo(address = address)) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_block_sender),
                )
            }
        }
    }

    fun unblock_sender(address: String) {
        viewModelScope.launch {
            try {
                settings_api.unblock_sender(address)
                _state.update { s -> s.copy(blocked_senders = s.blocked_senders.filter { it.address != address }) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_unblock_sender),
                )
            }
        }
    }

    fun load_aliases() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = settings_api.list_aliases()
                val decrypted = response.aliases.map { decrypt_alias(it) }
                _state.value = _state.value.copy(
                    aliases = decrypted,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun delete_alias(alias_id: String) {
        viewModelScope.launch {
            try {
                settings_api.delete_alias(alias_id)
                _state.update { s -> s.copy(aliases = s.aliases.filter { it.id != alias_id }) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_delete_alias),
                )
            }
        }
    }

    fun load_storage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val overview = settings_api.get_storage_overview()
                _state.value = _state.value.copy(storage = overview, is_loading = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun load_subscription() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val sub = settings_api.get_subscription()
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.i("SettingsVM", "subscription loaded status=${sub.status} plan=${sub.plan_name}")
                }
                _state.value = _state.value.copy(subscription = sub, is_loading = false)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("SettingsVM", "load_subscription failed", t)
                }
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun load_security_status() {
        viewModelScope.launch {
            try {
                val status = settings_api.get_security_status()
                _state.value = _state.value.copy(security_status = status)
            } catch (_: Throwable) {
            }
        }
    }

    suspend fun send_feedback(category: String, message: String) {
        settings_api.send_feedback(FeedbackRequest(category = category, message = message))
    }

    fun get_recovery_codes(): List<String>? {
        return session_key_store.get_recovery_codes()
    }

    fun load_labels(folder_type: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = labels_api.list_labels(include_counts = true, folder_type = folder_type)
                val decrypted = response.labels.map { decrypt_label(it) }
                val server_tokens = decrypted.map { it.label_token }.toSet()
                val surviving = _state.value.labels.filter {
                    it.label_token in optimistic_label_tokens && it.label_token !in server_tokens
                }
                optimistic_label_tokens.removeAll(server_tokens)
                _state.value = _state.value.copy(
                    labels = decrypted + surviving,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun create_label(request: CreateLabelRequest) {
        viewModelScope.launch {
            try {
                val response = labels_api.create_label(request)
                if (response.success) {
                    load_labels()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun delete_label(label_id: String) {
        viewModelScope.launch {
            try {
                labels_api.delete_label(label_id)
                _state.value = _state.value.copy(
                    labels = _state.value.labels.filter { it.id != label_id },
                )
            } catch (_: Throwable) {
            }
        }
    }

    fun load_tags() {
        viewModelScope.launch {
            try {
                val response = tags_api.list_tags(include_counts = true)
                val decrypted = response.tags.map { decrypt_tag(it) }
                _state.value = _state.value.copy(tags = decrypted)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun create_tag(name: String, color: String? = null, icon: String? = null) {
        viewModelScope.launch {
            try {
                val identity_key = session_key_store.get_identity_key() ?: return@launch
                val name_field = encrypt_field_with_version(name, identity_key, TAG_VERSION_CURRENT)
                val color_field = color?.let { encrypt_field_with_version(it, identity_key, TAG_VERSION_CURRENT) }
                val icon_field = icon?.let { encrypt_field_with_version(it, identity_key, TAG_VERSION_CURRENT) }
                tags_api.create_tag(
                    CreateTagRequest(
                        tag_token = generate_token_b64(),
                        encrypted_name = name_field.ciphertext_b64,
                        name_nonce = name_field.nonce_b64,
                        encrypted_color = color_field?.ciphertext_b64,
                        color_nonce = color_field?.nonce_b64,
                        encrypted_icon = icon_field?.ciphertext_b64,
                        icon_nonce = icon_field?.nonce_b64,
                    ),
                )
                load_tags()
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_create_tag),
                )
            }
        }
    }

    fun create_folder(name: String, color: String? = null, sort_order: Int? = null) {
        viewModelScope.launch {
            try {
                val identity_key = session_key_store.get_identity_key() ?: return@launch
                val token = generate_token_b64()
                val name_field = encrypt_field_with_version(name, identity_key, FOLDER_VERSION_CURRENT)
                val color_field = color?.let { encrypt_field_with_version(it, identity_key, FOLDER_VERSION_CURRENT) }
                labels_api.create_label(
                    CreateLabelRequest(
                        label_token = token,
                        encrypted_name = name_field.ciphertext_b64,
                        name_nonce = name_field.nonce_b64,
                        encrypted_color = color_field?.ciphertext_b64,
                        color_nonce = color_field?.nonce_b64,
                        folder_type = "folder",
                        sort_order = sort_order,
                    ),
                )
                val optimistic = LabelItem(
                    id = token,
                    label_token = token,
                    encrypted_name = name,
                    encrypted_color = color,
                    folder_type = "folder",
                    sort_order = sort_order ?: 0,
                    item_count = 0,
                )
                optimistic_label_tokens.add(token)
                _state.value = _state.value.copy(
                    labels = _state.value.labels + optimistic,
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_create_folder),
                )
            }
        }
    }

    fun delete_tag(tag_id: String) {
        viewModelScope.launch {
            try {
                tags_api.delete_tag(tag_id)
                _state.value = _state.value.copy(
                    tags = _state.value.tags.filter { it.id != tag_id },
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_delete_tag),
                )
            }
        }
    }

    fun load_referral_info() {
        viewModelScope.launch {
            val info = try {
                labels_api.get_referral_info()
            } catch (_: Throwable) {
                ReferralInfoResponse()
            }
            _state.value = _state.value.copy(referral = info)
        }
    }

    fun load_preferences() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = preferences_api.get_encrypted_preferences()
                val identity_key = session_key_store.get_identity_key()
                val enc = response.encrypted_preferences
                val nonce = response.preferences_nonce
                val prefs = if (!enc.isNullOrBlank() && !nonce.isNullOrBlank() && !identity_key.isNullOrBlank()) {
                    try {
                        decrypt_preferences(enc, nonce, identity_key)
                    } catch (_: Throwable) {
                        try { preferences_api.get_preferences() } catch (_: Throwable) { UserPreferences() }
                    }
                } else {
                    try { preferences_api.get_preferences() } catch (_: Throwable) { UserPreferences() }
                }
                _state.value = _state.value.copy(preferences = prefs, is_loading = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun load_signature() {
        viewModelScope.launch {
            try {
                val list = signatures_api.list_signatures()
                val decrypted = list.signatures.map { sig ->
                    val name = try {
                        decrypt_signature_field(sig.encrypted_name, sig.name_nonce)
                    } catch (_: Throwable) { "" }
                    val content = try {
                        decrypt_signature_field(sig.encrypted_content, sig.content_nonce)
                    } catch (_: Throwable) { "" }
                    DecryptedSignature(
                        id = sig.id,
                        name = name,
                        content = content,
                        is_default = sig.is_default,
                        is_html = sig.is_html,
                        alias_id = sig.alias_id,
                        placement = sig.placement,
                    )
                }
                _signatures.value = decrypted
                val global_default = decrypted.firstOrNull { it.alias_id == null && it.is_default }
                    ?: decrypted.firstOrNull { it.alias_id == null }
                if (global_default == null) {
                    default_signature_id = null
                    default_signature_is_html = false
                    _signature_text.value = ""
                } else {
                    default_signature_id = global_default.id
                    default_signature_is_html = global_default.is_html
                    _signature_text.value = global_default.content
                }
                _signature_loaded.value = true
            } catch (_: Throwable) {
                _signature_loaded.value = true
            }
        }
    }

    fun signature_for(alias_id: String?): DecryptedSignature? {
        val all = _signatures.value
        if (alias_id != null) {
            val bound = all.firstOrNull { it.alias_id == alias_id }
            if (bound != null) return bound
        }
        return all.firstOrNull { it.alias_id == null && it.is_default }
            ?: all.firstOrNull { it.alias_id == null }
    }

    fun create_signature(
        name: String,
        content: String,
        is_default: Boolean,
        is_html: Boolean,
        alias_id: String?,
        placement: Int?,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                val name_enc = encrypt_signature_field(name)
                val content_enc = encrypt_signature_field(content)
                signatures_api.create_signature(
                    org.astermail.android.api.signatures.CreateSignatureRequest(
                        encrypted_name = name_enc.ciphertext_b64,
                        name_nonce = name_enc.nonce_b64,
                        encrypted_content = content_enc.ciphertext_b64,
                        content_nonce = content_enc.nonce_b64,
                        is_default = is_default,
                        is_html = is_html,
                        alias_id = alias_id,
                        placement = placement,
                    ),
                )
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_signature()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun update_signature(
        id: String,
        name: String?,
        content: String?,
        is_html: Boolean?,
        alias_id: String?,
        placement: Int?,
        clear_alias: Boolean = false,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                val name_enc = name?.let { encrypt_signature_field(it) }
                val content_enc = content?.let { encrypt_signature_field(it) }
                val effective_alias_id = if (clear_alias) null else alias_id
                signatures_api.update_signature(
                    id,
                    org.astermail.android.api.signatures.UpdateSignatureRequest(
                        encrypted_name = name_enc?.ciphertext_b64,
                        name_nonce = name_enc?.nonce_b64,
                        encrypted_content = content_enc?.ciphertext_b64,
                        content_nonce = content_enc?.nonce_b64,
                        is_html = is_html,
                        alias_id = effective_alias_id,
                        placement = placement,
                    ),
                )
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_signature()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun delete_signature(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                signatures_api.delete_signature(id)
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_signature()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun set_default_signature(id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                signatures_api.set_default_signature(id)
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_signature()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun save_signature(content: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                val existing_id = default_signature_id
                if (existing_id != null) {
                    val enc = encrypt_signature_field(content)
                    signatures_api.update_signature(
                        existing_id,
                        org.astermail.android.api.signatures.UpdateSignatureRequest(
                            encrypted_content = enc.ciphertext_b64,
                            content_nonce = enc.nonce_b64,
                        ),
                    )
                } else {
                    val name_enc = encrypt_signature_field(context.getString(org.astermail.android.R.string.default_signature_name))
                    val content_enc = encrypt_signature_field(content)
                    val created = signatures_api.create_signature(
                        org.astermail.android.api.signatures.CreateSignatureRequest(
                            encrypted_name = name_enc.ciphertext_b64,
                            name_nonce = name_enc.nonce_b64,
                            encrypted_content = content_enc.ciphertext_b64,
                            content_nonce = content_enc.nonce_b64,
                            is_default = true,
                            is_html = false,
                        ),
                    )
                    default_signature_id = created.id
                }
                _signature_text.value = content
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    private fun decrypt_signature_field(ciphertext_b64: String, nonce_b64: String): String {
        if (ciphertext_b64.isBlank() || nonce_b64.isBlank()) return ""
        val ciphertext = android.util.Base64.decode(ciphertext_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
        val key = derive_encryption_key()
        try {
            return String(aes_gcm_decrypt(ciphertext, key, nonce), Charsets.UTF_8)
        } finally {
            key.fill(0)
        }
    }

    private fun encrypt_signature_field(plaintext: String): EncryptedField {
        val data = plaintext.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive_encryption_key()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val ct = cipher.doFinal(data)
            data.fill(0)
            return EncryptedField(
                ciphertext_b64 = android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP),
                nonce_b64 = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP),
            )
        } finally {
            key.fill(0)
        }
    }

    fun save_preferences(prefs: UserPreferences) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                val identity_key = session_key_store.get_identity_key()
                if (!identity_key.isNullOrBlank()) {
                    val request = encrypt_preferences(prefs, identity_key)
                    preferences_api.save_encrypted_preferences(request)
                } else {
                    preferences_api.save_preferences(prefs)
                }
                _state.value = _state.value.copy(
                    preferences = prefs,
                    save_status = SaveStatus.SAVED,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    suspend fun verify_password(password: String): Boolean {
        val entered = password.toByteArray(Charsets.UTF_8)
        val stored = session_key_store.get_passphrase() ?: return false
        val match = entered.contentEquals(stored)
        entered.fill(0)
        return match
    }

    fun update_sidebar_state(key: String, value: Boolean) {
        val current = _state.value.preferences ?: UserPreferences()
        val updated = when (key) {
            "sidebar_more_collapsed" -> current.copy(sidebar_more_collapsed = value)
            "sidebar_folders_collapsed" -> current.copy(sidebar_folders_collapsed = value)
            "sidebar_labels_collapsed" -> current.copy(sidebar_labels_collapsed = value)
            "sidebar_aliases_collapsed" -> current.copy(sidebar_aliases_collapsed = value)
            else -> current
        }
        _state.value = _state.value.copy(preferences = updated)
        save_preferences(updated)
    }

    fun load_ghost_aliases() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = ghost_alias_api.list_ghost_aliases()
                _state.value = _state.value.copy(
                    ghost_aliases = response.aliases,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun create_ghost_alias(note: String) {
        viewModelScope.launch {
            try {
                ghost_alias_api.create_ghost_alias(CreateGhostAliasRequest(note = note))
                load_ghost_aliases()
            } catch (_: Throwable) {
            }
        }
    }

    sealed class GhostAliasResult {
        data class Success(val address: String) : GhostAliasResult()
        data class Failure(val message: String) : GhostAliasResult()
    }

    suspend fun create_ghost_alias_now(note: String): GhostAliasResult {
        return try {
            val response = ghost_alias_api.create_ghost_alias(CreateGhostAliasRequest(note = note))
            load_ghost_aliases()
            val address = response.address
            if (address.isBlank()) {
                GhostAliasResult.Failure(context.getString(R.string.server_returned_no_address))
            } else {
                GhostAliasResult.Success(address)
            }
        } catch (t: Throwable) {
            GhostAliasResult.Failure(t.message ?: context.getString(R.string.could_not_create_ghost_alias))
        }
    }

    fun expire_ghost_alias(alias_id: String) {
        viewModelScope.launch {
            try {
                ghost_alias_api.expire_ghost_alias(alias_id)
                load_ghost_aliases()
            } catch (_: Throwable) {
            }
        }
    }

    fun extend_ghost_alias(alias_id: String) {
        viewModelScope.launch {
            try {
                ghost_alias_api.extend_ghost_alias(alias_id)
                load_ghost_aliases()
            } catch (_: Throwable) {
            }
        }
    }

    fun load_forwarding_rules() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = auto_forward_api.list_rules()
                _state.value = _state.value.copy(
                    forwarding_rules = response.rules,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun create_forwarding_rule(target: String, keep_copy: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                auto_forward_api.create_rule(
                    CreateForwardingRuleRequest(target_address = target, keep_copy = keep_copy),
                )
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_forwarding_rules()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun update_forwarding_rule(target: String, keep_copy: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                auto_forward_api.update_rule(
                    org.astermail.android.api.autoforward.UpdateForwardingRuleRequest(
                        target_address = target,
                        keep_copy = keep_copy,
                    ),
                )
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_forwarding_rules()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun toggle_forwarding_rule(rule_id: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                auto_forward_api.toggle_rule(
                    ToggleForwardingRuleRequest(id = rule_id, enabled = enabled),
                )
                _state.update { s ->
                    s.copy(
                        forwarding_rules = s.forwarding_rules.map {
                            if (it.id == rule_id) it.copy(enabled = enabled) else it
                        },
                    )
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun delete_forwarding_rule(rule_id: String) {
        viewModelScope.launch {
            try {
                auto_forward_api.delete_rule(rule_id)
                _state.update { s -> s.copy(forwarding_rules = s.forwarding_rules.filter { it.id != rule_id }) }
            } catch (_: Throwable) {
            }
        }
    }

    fun load_api_keys() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = developer_api.list_api_keys()
                _state.value = _state.value.copy(
                    api_keys = response.api_keys,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun create_api_key(name: String) {
        viewModelScope.launch {
            try {
                developer_api.create_api_key(CreateApiKeyRequest(name = name))
                load_api_keys()
            } catch (_: Throwable) {
            }
        }
    }

    fun revoke_api_key(key_id: String) {
        viewModelScope.launch {
            try {
                developer_api.revoke_api_key(key_id)
                _state.update { s -> s.copy(api_keys = s.api_keys.filter { it.id != key_id }) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_revoke_api_key),
                )
            }
        }
    }

    fun load_webhooks() {
        viewModelScope.launch {
            try {
                val response = developer_api.list_webhooks()
                _state.value = _state.value.copy(webhooks = response.webhooks)
            } catch (_: Throwable) {
            }
        }
    }

    fun get_access_token(): String? = token_store.access_token

    fun refresh_access_token_blocking(): String? {
        return try {
            kotlinx.coroutines.runBlocking {
                val response = auth_api.refresh()
                val existing_refresh = token_store.refresh_token ?: response.access_token
                token_store.save(response.access_token, existing_refresh)
                response.access_token
            }
        } catch (_: Throwable) {
            token_store.access_token
        }
    }

    private fun decrypt_tag(tag: TagItem): TagItem {
        val identity_key = session_key_store.get_identity_key() ?: return tag.copy(encrypted_name = "")
        val all_keys = buildList {
            add(identity_key)
            session_key_store.get_previous_keys()?.forEach { add(it) }
        }
        return try {
            val name = if (tag.encrypted_name.isNotBlank() && tag.name_nonce.isNotBlank()) {
                decrypt_tag_field_with_fallback(tag.encrypted_name, tag.name_nonce, all_keys) ?: ""
            } else tag.encrypted_name

            val enc_color = tag.encrypted_color
            val c_nonce = tag.color_nonce
            val color = if (!enc_color.isNullOrBlank() && !c_nonce.isNullOrBlank()) {
                decrypt_tag_field_with_fallback(enc_color, c_nonce, all_keys)
            } else enc_color

            val enc_icon = tag.encrypted_icon
            val i_nonce = tag.icon_nonce
            val icon = if (!enc_icon.isNullOrBlank() && !i_nonce.isNullOrBlank()) {
                decrypt_tag_field_with_fallback(enc_icon, i_nonce, all_keys)
            } else enc_icon

            tag.copy(encrypted_name = name, encrypted_color = color, encrypted_icon = icon)
        } catch (_: Throwable) {
            tag.copy(encrypted_name = "")
        }
    }

    private fun decrypt_tag_field_with_fallback(
        ciphertext_b64: String,
        nonce_b64: String,
        identity_keys: List<String>,
    ): String? {
        val ciphertext = android.util.Base64.decode(ciphertext_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
        for (key in identity_keys) {
            try {
                val derived = derive_field_key(key, TAG_VERSION_CURRENT)
                try {
                    val result = aes_gcm_decrypt(ciphertext, derived, nonce)
                    return String(result, Charsets.UTF_8)
                } finally {
                    derived.fill(0)
                }
            } catch (_: Throwable) {
            }
        }
        val legacy_keks = session_key_store.get_legacy_keks().orEmpty()
        for (kek_b64 in legacy_keks) {
            try {
                val raw_key = android.util.Base64.decode(kek_b64, android.util.Base64.DEFAULT)
                if (raw_key.size == 32) {
                    try {
                        val result = aes_gcm_decrypt(ciphertext, raw_key, nonce)
                        return String(result, Charsets.UTF_8)
                    } finally {
                        raw_key.fill(0)
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun decrypt_label(label: LabelItem): LabelItem {
        val identity_key = session_key_store.get_identity_key() ?: return label.copy(encrypted_name = null)
        return try {
            val enc_name = label.encrypted_name
            val n_nonce = label.name_nonce
            val name = if (!enc_name.isNullOrBlank() && !n_nonce.isNullOrBlank()) {
                try {
                    decrypt_field_with_versions(enc_name, n_nonce, identity_key, FOLDER_VERSIONS)
                } catch (_: Throwable) {
                    null
                }
            } else enc_name

            val enc_color = label.encrypted_color
            val c_nonce = label.color_nonce
            val color = if (!enc_color.isNullOrBlank() && !c_nonce.isNullOrBlank()) {
                try {
                    decrypt_field_with_versions(enc_color, c_nonce, identity_key, FOLDER_VERSIONS)
                } catch (_: Throwable) {
                    null
                }
            } else enc_color

            label.copy(encrypted_name = name, encrypted_color = color)
        } catch (_: Throwable) {
            label.copy(encrypted_name = null)
        }
    }

    private fun decrypt_tag_field(
        ciphertext_b64: String,
        nonce_b64: String,
        identity_key: String,
    ): String = decrypt_field_with_versions(ciphertext_b64, nonce_b64, identity_key, TAG_VERSIONS)

    private fun decrypt_field_with_versions(
        ciphertext_b64: String,
        nonce_b64: String,
        identity_key: String,
        versions: List<String>,
    ): String {
        val ciphertext = android.util.Base64.decode(ciphertext_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
        for (version in versions) {
            try {
                val key = derive_field_key(identity_key, version)
                try {
                    val result = aes_gcm_decrypt(ciphertext, key, nonce)
                    return String(result, Charsets.UTF_8)
                } finally {
                    key.fill(0)
                }
            } catch (_: Throwable) {
            }
        }
        throw IllegalStateException("field decryption failed")
    }

    private fun encrypt_field_with_version(
        plaintext: String,
        identity_key: String,
        version: String,
    ): EncryptedField {
        val data = plaintext.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive_field_key(identity_key, version)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val ct = cipher.doFinal(data)
            data.fill(0)
            return EncryptedField(
                ciphertext_b64 = android.util.Base64.encodeToString(ct, android.util.Base64.NO_WRAP),
                nonce_b64 = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP),
            )
        } finally {
            key.fill(0)
        }
    }

    private fun derive_field_key(identity_key: String, version: String): ByteArray {
        val material = (identity_key + version).toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(material)
        material.fill(0)
        return key
    }

    private fun generate_token_b64(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private data class EncryptedField(val ciphertext_b64: String, val nonce_b64: String)

    private fun decrypt_alias(alias: AliasInfo): AliasInfo {
        if (alias.encrypted_local_part.isBlank()) return alias
        val local_part = try {
            if (alias.is_random) {
                try {
                    val bytes = android.util.Base64.decode(
                        alias.encrypted_local_part, android.util.Base64.DEFAULT,
                    )
                    String(bytes, Charsets.UTF_8)
                } catch (_: Throwable) {
                    alias.encrypted_local_part
                }
            } else {
                decrypt_alias_field(alias.encrypted_local_part, alias.local_part_nonce)
            }
        } catch (_: Throwable) {
            return alias.copy(encrypted_local_part = "", decryption_failed = true)
        }
        val enc_name = alias.encrypted_display_name
        val name_nonce = alias.display_name_nonce
        val display_name = if (!enc_name.isNullOrBlank() && !name_nonce.isNullOrBlank()) {
            try {
                decrypt_alias_field(enc_name, name_nonce)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        return alias.copy(
            encrypted_local_part = local_part,
            encrypted_display_name = display_name,
        )
    }

    private fun decrypt_alias_field(ciphertext_b64: String, nonce_b64: String): String {
        if (nonce_b64.isBlank()) throw IllegalStateException("no nonce")
        val ciphertext = android.util.Base64.decode(ciphertext_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)

        try {
            val key = derive_encryption_key()
            try {
                return String(aes_gcm_decrypt(ciphertext, key, nonce), Charsets.UTF_8)
            } finally {
                key.fill(0)
            }
        } catch (_: Throwable) {
        }

        val identity_key = session_key_store.get_identity_key()
        if (identity_key != null) {
            for (version in ALIAS_VERSIONS) {
                try {
                    val material = (identity_key + version).toByteArray(Charsets.UTF_8)
                    val key = MessageDigest.getInstance("SHA-256").digest(material)
                    return String(aes_gcm_decrypt(ciphertext, key, nonce), Charsets.UTF_8)
                } catch (_: Throwable) {
                }
            }
        }

        val legacy_keks = session_key_store.get_legacy_keks().orEmpty()
        for (kek_b64 in legacy_keks) {
            try {
                val raw_key = android.util.Base64.decode(kek_b64, android.util.Base64.DEFAULT)
                if (raw_key.size == 32) {
                    try {
                        return String(aes_gcm_decrypt(ciphertext, raw_key, nonce), Charsets.UTF_8)
                    } finally {
                        raw_key.fill(0)
                    }
                }
            } catch (_: Throwable) {
            }
        }
        throw IllegalStateException("alias decryption failed")
    }

    private fun aes_gcm_decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun derive_encryption_key(): ByteArray {
        val passphrase = session_key_store.get_passphrase()
            ?: throw IllegalStateException("no passphrase")
        try {
            val prefix = SALT_PREFIX.toByteArray(Charsets.UTF_8)
            val salt_input = ByteArray(prefix.size + passphrase.size)
            System.arraycopy(prefix, 0, salt_input, 0, prefix.size)
            System.arraycopy(passphrase, 0, salt_input, prefix.size, passphrase.size)
            val salt = MessageDigest.getInstance("SHA-256").digest(salt_input)
            salt_input.fill(0)

            val info = DERIVED_KEY_INFO.toByteArray(Charsets.UTF_8)
            return hkdf_sha256(passphrase, salt, info, 32)
        } finally {
            passphrase.fill(0)
        }
    }

    private fun hkdf_sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        val okm = mac.doFinal()
        prk.fill(0)
        return okm.copyOf(length)
    }

    private val prefs_json = kotlinx.serialization.json.Json {
        this.ignoreUnknownKeys = true
        this.encodeDefaults = true
        this.explicitNulls = false
    }

    private fun decrypt_preferences(
        encrypted_b64: String,
        nonce_b64: String,
        identity_key: String,
    ): UserPreferences {
        val ciphertext = android.util.Base64.decode(encrypted_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
        val key_material = (identity_key + PREFERENCES_KEY_SUFFIX).toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(key_material)
        val plaintext = aes_gcm_decrypt(ciphertext, key, nonce)
        key.fill(0)
        val json_str = String(plaintext, Charsets.UTF_8)
        return prefs_json.decodeFromString(UserPreferences.serializer(), json_str)
    }

    private fun encrypt_preferences(
        prefs: UserPreferences,
        identity_key: String,
    ): SaveEncryptedPreferencesRequest {
        val json_str = prefs_json.encodeToString(UserPreferences.serializer(), prefs)
        val plaintext = json_str.toByteArray(Charsets.UTF_8)
        val key_material = (identity_key + PREFERENCES_KEY_SUFFIX).toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(key_material)
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        key.fill(0)
        return SaveEncryptedPreferencesRequest(
            encrypted_preferences = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
            preferences_nonce = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP),
        )
    }

    companion object {
        private const val SALT_PREFIX = "aster-hkdf-salt-v1:"
        private const val DERIVED_KEY_INFO = "aster-storage-encryption-key-v1"
        private const val TAG_VERSION_CURRENT = "astermail-tags-v1"
        private const val FOLDER_VERSION_CURRENT = "astermail-labels-v1"
        private const val PREFERENCES_KEY_SUFFIX = "astermail-preferences-v1"
        private val TAG_VERSIONS = listOf(TAG_VERSION_CURRENT)
        private val FOLDER_VERSIONS = listOf(FOLDER_VERSION_CURRENT, TAG_VERSION_CURRENT)
        private val ALIAS_VERSIONS = listOf("astermail-envelope-v1", "astermail-import-v1")
    }
}
