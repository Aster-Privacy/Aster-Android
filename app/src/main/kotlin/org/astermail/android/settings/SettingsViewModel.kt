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

import org.astermail.android.BuildConfig
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.api.ApiError
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
import org.astermail.android.api.family.FamilyApi
import org.astermail.android.api.family.ReservedAddress
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
import org.astermail.android.api.preferences.encode_preferences_preserving_unknown
import org.astermail.android.api.preferences.merge_decrypted_preferences
import org.astermail.android.api.preferences.rebase_preferences_changes
import kotlinx.serialization.json.jsonObject
import org.astermail.android.api.recovery_email.RecoveryEmailApi
import org.astermail.android.api.recovery_email.RecoveryEmailApiImpl
import org.astermail.android.api.recovery_email.SaveRecoveryEmailRequest
import org.astermail.android.api.security.AuditEvent
import org.astermail.android.api.security.HardwareKey
import org.astermail.android.api.security.SecurityApi
import org.astermail.android.api.security.SetLoginAlertRequest
import org.astermail.android.api.security.TrustedDevice
import org.astermail.android.api.settings.AddDomainRequest
import org.astermail.android.api.settings.AliasDirectory
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.DeletedAliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.api.settings.AliasPreferences
import org.astermail.android.api.settings.BlockedSenderInfo
import org.astermail.android.api.settings.CheckAliasAvailabilityRequest
import org.astermail.android.api.settings.CreateAliasRequest
import org.astermail.android.api.settings.CreateDirectoryRequest
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.DirectoryAvailabilityRequest
import org.astermail.android.api.settings.FeedbackRequest
import org.astermail.android.api.settings.SecurityStatusResponse
import org.astermail.android.api.settings.SessionInfo
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.api.settings.StorageOverview
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.api.settings.UpdateAliasPreferencesRequest
import org.astermail.android.api.settings.UpdateAliasRequest
import org.astermail.android.api.settings.UpdateDirectoryRequest
import org.astermail.android.api.settings.UpdateDomainRequest
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.user.Badge
import org.astermail.android.api.user.UserApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.TokenStore

data class SettingsUiState(
    val user: UserInfo? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val blocked_senders: List<BlockedSenderInfo> = emptyList(),
    val aliases: List<AliasInfo> = emptyList(),
    val max_aliases: Int = 0,
    val custom_domain_addresses: List<CustomDomainAddressInfo> = emptyList(),
    val domains: List<CustomDomain> = emptyList(),
    val domains_loading: Boolean = false,
    val storage: StorageOverview? = null,
    val subscription: SubscriptionInfo? = null,
    val security_status: SecurityStatusResponse? = null,
    val labels: List<LabelItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val referral: ReferralInfoResponse? = null,
    val preferences: UserPreferences? = null,
    val preferences_authoritative: Boolean = false,
    val reserved_addresses: List<ReservedAddress> = emptyList(),
    val family_seats_used: Int = 0,
    val family_max_members: Int = 0,
    val ghost_aliases: List<GhostAlias> = emptyList(),
    val forwarding_rules: List<ForwardingRule> = emptyList(),
    val api_keys: List<ApiKeyInfo> = emptyList(),
    val webhooks: List<WebhookInfo> = emptyList(),
    val directories: List<AliasDirectory> = emptyList(),
    val directories_loading: Boolean = false,
    val deleted_aliases: List<DecryptedDeletedAlias> = emptyList(),
    val deleted_aliases_loading: Boolean = false,
    val alias_preferences: AliasPreferences? = null,
    val recovery_email_address: String? = null,
    val recovery_email_set: Boolean = false,
    val recovery_email_verified: Boolean = false,
    val login_alerts_enabled: Boolean? = null,
    val hardware_keys: List<HardwareKey> = emptyList(),
    val trusted_devices: List<TrustedDevice> = emptyList(),
    val audit_events: List<AuditEvent> = emptyList(),
    val vanguard_enabled: Boolean? = null,
    val security_loading: Boolean = false,
    val pgp_key_info: org.astermail.android.api.encryption.PgpKeyInfo? = null,
    val recovery_codes_status: org.astermail.android.api.encryption.RecoveryCodesStatus? = null,
    val encryption_settings: org.astermail.android.api.encryption.EncryptionSettings? = null,
    val wkd_status: org.astermail.android.api.encryption.WkdStatusResponse? = null,
    val keyserver_status: org.astermail.android.api.encryption.KeyserverStatusResponse? = null,
    val badges: List<Badge> = emptyList(),
    val is_loading: Boolean = false,
    val error: String? = null,
    val save_status: SaveStatus = SaveStatus.IDLE,
    val action_result: String? = null,
    val default_sender_id: String? = null,
)

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

data class DecryptedDeletedAlias(
    val id: String,
    val address: String,
    val display_name: String?,
    val deleted_at: String,
)

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
    private val family_api: FamilyApi,
    private val ghost_alias_api: GhostAliasApi,
    private val auto_forward_api: AutoForwardApi,
    private val developer_api: DeveloperApi,
    private val subscriptions_api: SubscriptionsApi,
    private val recovery_email_api: RecoveryEmailApi,
    private val security_api: SecurityApi,
    private val encryption_api: org.astermail.android.api.encryption.EncryptionApi,
    private val auth_repository: AuthRepository,
    private val session_key_store: SessionKeyStore,
    private val token_store: TokenStore,
    val account_store: org.astermail.android.storage.AccountStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    internal var default_dispatcher: CoroutineDispatcher = Dispatchers.Default

    private val _state = MutableStateFlow(SettingsUiState())
    private val optimistic_label_tokens = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _signature_text = MutableStateFlow("")
    val signature_text: StateFlow<String> = _signature_text.asStateFlow()

    private val _signature_loaded = MutableStateFlow(false)
    val signature_loaded: StateFlow<Boolean> = _signature_loaded.asStateFlow()

    private val _signatures = MutableStateFlow<List<DecryptedSignature>>(emptyList())
    val signatures: StateFlow<List<DecryptedSignature>> = _signatures.asStateFlow()

    @Volatile private var default_signature_id: String? = null
    @Volatile private var default_signature_is_html: Boolean = false
    private var load_preferences_job: kotlinx.coroutines.Job? = null
    private var save_preferences_job: kotlinx.coroutines.Job? = null
    private var prefs_load_succeeded = false
    private var account_uses_encrypted_prefs = false

    init {
        load_preferences()
    }

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
        load_default_sender()
    }

    fun load_badges() {
        viewModelScope.launch {
            try {
                val result = user_api.fetch_badges()
                _state.value = _state.value.copy(badges = result)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_badges", t)
            }
        }
    }

    fun load_default_sender() {
        viewModelScope.launch {
            try {
                val response = preferences_api.get_default_sender()
                _state.value = _state.value.copy(default_sender_id = response.sender_id)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_default_sender", t)
            }
        }
    }

    fun set_default_sender(sender_id: String?) {
        _state.value = _state.value.copy(default_sender_id = sender_id)
        viewModelScope.launch {
            try {
                preferences_api.set_default_sender(
                    org.astermail.android.api.preferences.SetDefaultSenderRequest(sender_id = sender_id),
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    default_sender_id = null,
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
                val refreshed = settings_api.list_sessions()
                _state.value = _state.value.copy(sessions = refreshed.sessions)
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

    fun logout(on_done: () -> Unit = {}) {
        viewModelScope.launch {
            auth_repository.logout()
            on_done()
        }
    }

    fun reset_save_status() {
        _state.value = _state.value.copy(save_status = SaveStatus.IDLE)
    }

    fun reset_for_account_switch() {
        load_preferences_job?.cancel()
        save_preferences_job?.cancel()
        prefs_load_succeeded = false
        account_uses_encrypted_prefs = false
        last_preferences_raw_json = null
        last_synced_preferences = null
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
                val page_size = 100
                val max_pages = 50
                val all_aliases = mutableListOf<AliasInfo>()
                var offset = 0
                var max_aliases = 0
                var page = 0
                while (page < max_pages) {
                    val response = settings_api.list_aliases(limit = page_size, offset = offset)
                    all_aliases.addAll(response.aliases)
                    max_aliases = response.max_aliases
                    if (!response.has_more || response.aliases.isEmpty()) break
                    offset += response.aliases.size
                    page++
                }
                val decrypted = withContext(default_dispatcher) {
                    all_aliases.map { decrypt_alias(it) }
                }
                _state.value = _state.value.copy(
                    aliases = decrypted,
                    max_aliases = max_aliases,
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

    fun load_custom_domain_addresses() {
        viewModelScope.launch {
            try {
                val response = settings_api.list_all_domain_addresses()
                val decrypted = response.addresses.map { addr ->
                    if (addr.encrypted_local_part.isBlank()) return@map addr
                    val local_part = try {
                        decrypt_alias_field(addr.encrypted_local_part, addr.local_part_nonce)
                    } catch (_: Throwable) {
                        return@map addr.copy(encrypted_local_part = "", decryption_failed = true)
                    }
                    addr.copy(encrypted_local_part = local_part)
                }
                _state.value = _state.value.copy(custom_domain_addresses = decrypted)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("SettingsVM", "load_custom_domain_addresses failed", t)
                }
            }
        }
    }

    fun delete_alias(alias_id: String) {
        viewModelScope.launch {
            try {
                settings_api.delete_alias(alias_id)
                _state.update { s -> s.copy(aliases = s.aliases.filter { it.id != alias_id }) }
                load_deleted_aliases()
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_delete_alias),
                )
            }
        }
    }

    fun load_deleted_aliases() {
        viewModelScope.launch {
            _state.value = _state.value.copy(deleted_aliases_loading = true)
            try {
                val response = settings_api.list_deleted_aliases()
                val decrypted = response.aliases.map { decrypt_deleted_alias(it) }
                _state.value = _state.value.copy(
                    deleted_aliases = decrypted,
                    deleted_aliases_loading = false,
                )
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("SettingsVM", "load_deleted_aliases failed", t)
                }
                _state.value = _state.value.copy(deleted_aliases_loading = false)
            }
        }
    }

    fun restore_deleted_alias(deleted_id: String) {
        viewModelScope.launch {
            try {
                settings_api.restore_deleted_alias(deleted_id)
                _state.update { s -> s.copy(deleted_aliases = s.deleted_aliases.filter { it.id != deleted_id }) }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_restored),
                )
                load_aliases()
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_restore_alias),
                )
                load_deleted_aliases()
            }
        }
    }

    fun purge_deleted_alias(deleted_id: String) {
        viewModelScope.launch {
            try {
                settings_api.purge_deleted_alias(deleted_id)
                _state.update { s -> s.copy(deleted_aliases = s.deleted_aliases.filter { it.id != deleted_id }) }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_purged),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_purge_alias),
                )
                load_deleted_aliases()
            }
        }
    }

    fun empty_deleted_aliases() {
        viewModelScope.launch {
            try {
                settings_api.empty_deleted_aliases()
                _state.value = _state.value.copy(
                    deleted_aliases = emptyList(),
                    action_result = context.getString(R.string.trash_emptied),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_empty_trash),
                )
                load_deleted_aliases()
            }
        }
    }

    private fun decrypt_deleted_alias(alias: DeletedAliasInfo): DecryptedDeletedAlias {
        val local_part = try {
            if (alias.is_random) {
                try {
                    String(
                        android.util.Base64.decode(alias.encrypted_local_part, android.util.Base64.DEFAULT),
                        Charsets.UTF_8,
                    )
                } catch (_: Throwable) {
                    alias.encrypted_local_part
                }
            } else {
                decrypt_alias_field(alias.encrypted_local_part, alias.local_part_nonce)
            }
        } catch (_: Throwable) {
            ""
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
        val address = if (local_part.isNotBlank()) "$local_part@${alias.domain}" else "@${alias.domain}"
        return DecryptedDeletedAlias(
            id = alias.id,
            address = address,
            display_name = display_name,
            deleted_at = alias.deleted_at,
        )
    }

    fun create_alias(local_part: String, domain: String, display_name: String? = null) {
        viewModelScope.launch { create_alias_now(local_part, domain) }
    }

    fun toggle_alias(alias_id: String) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val new_enabled = !current.is_enabled
        if (new_enabled) {
            val max = _state.value.max_aliases
            val active_count = _state.value.aliases.count { it.is_enabled }
            if (max > 0 && active_count >= max) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_forward_limit_reached),
                )
                return
            }
        }
        _state.update { s ->
            s.copy(aliases = s.aliases.map { if (it.id == alias_id) it.copy(is_enabled = new_enabled) else it })
        }
        viewModelScope.launch {
            try {
                settings_api.update_alias(alias_id, UpdateAliasRequest(is_enabled = new_enabled))
            } catch (_: Throwable) {
                _state.update { s ->
                    s.copy(aliases = s.aliases.map { if (it.id == alias_id) it.copy(is_enabled = current.is_enabled) else it })
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun load_domains() {
        viewModelScope.launch {
            _state.value = _state.value.copy(domains_loading = true)
            try {
                val response = settings_api.list_domains()
                _state.value = _state.value.copy(domains = response.domains, domains_loading = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    domains_loading = false,
                    action_result = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun add_domain(domain_name: String) {
        viewModelScope.launch {
            try {
                settings_api.add_domain(AddDomainRequest(domain_name = domain_name))
                load_domains()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    action_result = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun delete_domain(domain_id: String) {
        viewModelScope.launch {
            try {
                settings_api.delete_domain(domain_id)
                _state.update { s -> s.copy(domains = s.domains.filter { it.id != domain_id }) }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    action_result = t.message ?: context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun toggle_domain_catch_all(domain_id: String) {
        val current = _state.value.domains.firstOrNull { it.id == domain_id } ?: return
        val new_val = !current.catch_all_enabled
        _state.update { s ->
            s.copy(domains = s.domains.map { if (it.id == domain_id) it.copy(catch_all_enabled = new_val) else it })
        }
        viewModelScope.launch {
            try {
                val updated = settings_api.update_domain(domain_id, UpdateDomainRequest(catch_all_enabled = new_val))
                _state.update { s ->
                    s.copy(domains = s.domains.map { if (it.id == domain_id) updated else it })
                }
            } catch (_: Throwable) {
                _state.update { s ->
                    s.copy(domains = s.domains.map { if (it.id == domain_id) current else it })
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    suspend fun get_dns_records_now(domain_id: String): List<org.astermail.android.api.settings.DnsRecord> {
        return try {
            settings_api.get_dns_records(domain_id).records
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun trigger_domain_verification_now(domain_id: String) {
        try {
            val updated = settings_api.trigger_domain_verification(domain_id)
            _state.update { s -> s.copy(domains = s.domains.map { if (it.id == domain_id) updated else it }) }
        } catch (_: Throwable) {
            _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
        }
    }

    suspend fun check_alias_availability(local_part: String, domain: String): Boolean {
        return try {
            val addr_hash = compute_alias_address_hash(local_part.lowercase(), domain)
            val routing_hash = compute_routing_address_hash(local_part.lowercase(), domain)
            val response = settings_api.check_alias_availability(
                CheckAliasAvailabilityRequest(
                    alias_address_hash = addr_hash,
                    routing_address_hash = routing_hash,
                )
            )
            response.available
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun check_directory_availability(key: String): Boolean {
        return try {
            val dir_hash = compute_directory_key_hash(key.lowercase())
            val response = settings_api.check_directory_availability(
                DirectoryAvailabilityRequest(directory_hash = dir_hash)
            )
            response.available
        } catch (_: Throwable) {
            false
        }
    }

    fun load_directories() {
        viewModelScope.launch {
            _state.value = _state.value.copy(directories_loading = true)
            try {
                val response = settings_api.list_directories()
                val decrypted = response.directories.map { decrypt_directory(it) }
                _state.value = _state.value.copy(directories = decrypted, directories_loading = false)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(directories_loading = false)
            }
        }
    }

    suspend fun create_directory_now(key: String, domain: String, captcha_token: String? = null): Boolean {
        return try {
            val dir_hash = compute_directory_key_hash(key.lowercase())
            val (enc_label, label_nonce) = encrypt_alias_field(key.lowercase())
            settings_api.create_directory(
                CreateDirectoryRequest(
                    directory_hash = dir_hash,
                    encrypted_label = enc_label,
                    label_nonce = label_nonce,
                    domain = domain,
                    auto_create_enabled = true,
                    captcha_token = captcha_token,
                )
            )
            load_directories()
            true
        } catch (t: Throwable) {
            _state.value = _state.value.copy(action_result = t.message ?: context.getString(R.string.something_went_wrong))
            false
        }
    }

    fun toggle_directory_auto_create(directory_id: String) {
        val current = _state.value.directories.firstOrNull { it.id == directory_id } ?: return
        val new_val = !current.auto_create_enabled
        _state.update { s -> s.copy(directories = s.directories.map { if (it.id == directory_id) it.copy(auto_create_enabled = new_val) else it }) }
        viewModelScope.launch {
            try {
                settings_api.update_directory(directory_id, UpdateDirectoryRequest(auto_create_enabled = new_val))
            } catch (_: Throwable) {
                _state.update { s -> s.copy(directories = s.directories.map { if (it.id == directory_id) it.copy(auto_create_enabled = !new_val) else it }) }
            }
        }
    }

    fun delete_directory(directory_id: String) {
        viewModelScope.launch {
            try {
                settings_api.delete_directory(directory_id)
                _state.update { s -> s.copy(directories = s.directories.filter { it.id != directory_id }) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    fun load_alias_preferences() {
        viewModelScope.launch {
            try {
                val prefs = settings_api.get_alias_preferences()
                _state.value = _state.value.copy(alias_preferences = prefs)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_alias_preferences", t)
            }
        }
    }

    fun update_alias_preference(update: UpdateAliasPreferencesRequest) {
        val previous = _state.value.alias_preferences
        _state.update { it.copy(alias_preferences = it.alias_preferences?.copy(
            alias_sender_format = update.alias_sender_format ?: it.alias_preferences.alias_sender_format,
            readable_reverse_aliases = update.readable_reverse_aliases ?: it.alias_preferences.readable_reverse_aliases,
            alias_always_expand = update.alias_always_expand ?: it.alias_preferences.alias_always_expand,
            alias_unsubscribe_action = update.alias_unsubscribe_action ?: it.alias_preferences.alias_unsubscribe_action,
            alias_disabled_response = update.alias_disabled_response ?: it.alias_preferences.alias_disabled_response,
            alias_delete_action = update.alias_delete_action ?: it.alias_preferences.alias_delete_action,
        ) ?: AliasPreferences(
            alias_sender_format = update.alias_sender_format,
            readable_reverse_aliases = update.readable_reverse_aliases,
            alias_always_expand = update.alias_always_expand,
            alias_unsubscribe_action = update.alias_unsubscribe_action,
            alias_disabled_response = update.alias_disabled_response,
            alias_delete_action = update.alias_delete_action,
        )) }
        viewModelScope.launch {
            try {
                settings_api.update_alias_preferences(update)
            } catch (_: Throwable) {
                _state.update { it.copy(alias_preferences = previous) }
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    suspend fun create_alias_now(local_part: String, domain: String, captcha_token: String? = null): Boolean {
        return try {
            val (enc_local, local_nonce) = encrypt_alias_field(local_part.lowercase())
            val addr_hash = compute_alias_address_hash(local_part.lowercase(), domain)
            val routing_hash = compute_routing_address_hash(local_part.lowercase(), domain)
            settings_api.create_alias(
                CreateAliasRequest(
                    encrypted_local_part = enc_local,
                    local_part_nonce = local_nonce,
                    alias_address_hash = addr_hash,
                    routing_address_hash = routing_hash,
                    domain = domain,
                    captcha_token = captcha_token,
                )
            )
            load_aliases()
            true
        } catch (t: Throwable) {
            _state.value = _state.value.copy(action_result = t.message ?: context.getString(R.string.something_went_wrong))
            false
        }
    }

    fun add_domain_now(domain_name: String, captcha_token: String? = null, on_done: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val domain = settings_api.add_domain(AddDomainRequest(domain_name = domain_name, captcha_token = captcha_token))
                _state.update { s -> s.copy(domains = s.domains + domain) }
                on_done(true)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
                on_done(false)
            }
        }
    }

    fun trigger_domain_verification(domain_id: String, on_result: (CustomDomain) -> Unit) {
        viewModelScope.launch {
            try {
                val updated = settings_api.trigger_domain_verification(domain_id)
                _state.update { s ->
                    s.copy(domains = s.domains.map { if (it.id == domain_id) updated else it })
                }
                on_result(updated)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    action_result = t.message ?: context.getString(R.string.something_went_wrong),
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
                _state.update {
                    it.copy(
                        security_status = status.copy(
                            recovery_email_set = it.recovery_email_set,
                            recovery_email_verified = it.recovery_email_verified,
                        ),
                    )
                }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_security_status", t)
            }
        }
    }

    fun load_login_alerts() {
        viewModelScope.launch {
            try {
                val status = security_api.get_login_alerts()
                _state.update { it.copy(login_alerts_enabled = status.enabled) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_login_alerts", t)
            }
        }
    }

    fun set_login_alerts(enabled: Boolean) {
        _state.update { it.copy(login_alerts_enabled = enabled) }
        viewModelScope.launch {
            try {
                security_api.set_login_alerts(SetLoginAlertRequest(enabled = enabled))
            } catch (_: Throwable) {
                _state.update { it.copy(
                    login_alerts_enabled = !enabled,
                    action_result = context.getString(R.string.something_went_wrong),
                ) }
            }
        }
    }

    fun load_hardware_keys() {
        viewModelScope.launch {
            try {
                val response = security_api.list_hardware_keys()
                _state.update { it.copy(hardware_keys = response.keys) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_hardware_keys", t)
            }
        }
    }

    fun delete_hardware_key(key_id: String) {
        viewModelScope.launch {
            try {
                security_api.delete_hardware_key(key_id)
                _state.update { it.copy(hardware_keys = it.hardware_keys.filter { k -> k.id != key_id }) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    fun rename_hardware_key(key_id: String, name: String) {
        viewModelScope.launch {
            try {
                val resp = security_api.rename_hardware_key(key_id, name.trim())
                if (resp.success) {
                    _state.update { st ->
                        st.copy(
                            hardware_keys = st.hardware_keys.map { k ->
                                if (k.id == key_id) k.copy(name = name.trim()) else k
                            },
                            action_result = context.getString(R.string.hardware_key_renamed),
                        )
                    }
                } else {
                    _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
                }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    fun load_trusted_devices() {
        viewModelScope.launch {
            try {
                val response = security_api.list_trusted_devices()
                _state.update { it.copy(trusted_devices = response.devices) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_trusted_devices", t)
            }
        }
    }

    fun revoke_trusted_device(device_id: String) {
        viewModelScope.launch {
            try {
                security_api.revoke_trusted_device(device_id)
                _state.update { it.copy(trusted_devices = it.trusted_devices.filter { d -> d.id != device_id }) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    fun revoke_all_trusted_devices() {
        viewModelScope.launch {
            try {
                security_api.revoke_all_trusted_devices()
                _state.update { it.copy(trusted_devices = emptyList()) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    fun load_audit_log() {
        viewModelScope.launch {
            try {
                val response = security_api.get_audit_log(per_page = 10)
                _state.update { it.copy(audit_events = response.events) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_audit_log", t)
            }
        }
    }

    fun load_vanguard_status() {
        viewModelScope.launch {
            try {
                val v = security_api.get_vanguard_status()
                _state.update { it.copy(vanguard_enabled = v.enabled) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_vanguard_status", t)
            }
        }
    }

    fun enable_vanguard() {
        viewModelScope.launch {
            try {
                val v = security_api.enable_vanguard()
                _state.update { it.copy(vanguard_enabled = v.enabled) }
            } catch (_: Throwable) {}
        }
    }

    fun disable_vanguard() {
        viewModelScope.launch {
            try {
                val v = security_api.disable_vanguard()
                _state.update { it.copy(vanguard_enabled = v.enabled) }
            } catch (_: Throwable) {}
        }
    }

    fun load_pgp_key_info() {
        viewModelScope.launch {
            try {
                val info = encryption_api.get_pgp_key_info()
                _state.update { it.copy(pgp_key_info = info) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_pgp_key_info", t)
            }
        }
    }

    fun load_recovery_codes_status() {
        viewModelScope.launch {
            try {
                val status = encryption_api.get_recovery_codes_status()
                _state.update { it.copy(recovery_codes_status = status) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_recovery_codes_status", t)
            }
        }
    }

    fun load_encryption_settings() {
        viewModelScope.launch {
            try {
                val settings = encryption_api.get_encryption_settings()
                _state.update { it.copy(encryption_settings = settings) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_encryption_settings", t)
            }
        }
    }

    fun toggle_auto_discover_keys() {
        val current = _state.value.encryption_settings?.auto_discover_keys ?: true
        _state.update { it.copy(encryption_settings = it.encryption_settings?.copy(auto_discover_keys = !current)) }
        viewModelScope.launch {
            try {
                encryption_api.update_encryption_settings(
                    org.astermail.android.api.encryption.UpdateEncryptionSettingsRequest(auto_discover_keys = !current)
                )
            } catch (_: Throwable) {
                _state.update { it.copy(encryption_settings = it.encryption_settings?.copy(auto_discover_keys = current)) }
            }
        }
    }

    fun toggle_encrypt_by_default() {
        val current = _state.value.encryption_settings?.encrypt_by_default ?: false
        _state.update { it.copy(encryption_settings = it.encryption_settings?.copy(encrypt_by_default = !current)) }
        viewModelScope.launch {
            try {
                encryption_api.update_encryption_settings(
                    org.astermail.android.api.encryption.UpdateEncryptionSettingsRequest(encrypt_by_default = !current)
                )
            } catch (_: Throwable) {
                _state.update { it.copy(encryption_settings = it.encryption_settings?.copy(encrypt_by_default = current)) }
            }
        }
    }

    fun load_wkd_keyserver_status() {
        viewModelScope.launch {
            try {
                val wkd = encryption_api.get_wkd_status()
                _state.update { it.copy(wkd_status = wkd) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_wkd_keyserver_status/wkd", t)
            }
            try {
                val ks = encryption_api.get_keyserver_status()
                _state.update { it.copy(keyserver_status = ks) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_wkd_keyserver_status/keyserver", t)
            }
        }
    }

    fun toggle_wkd_publishing() {
        val published = _state.value.wkd_status?.published == true
        viewModelScope.launch {
            try {
                val result = if (published) encryption_api.unpublish_from_wkd() else encryption_api.publish_to_wkd()
                _state.update { it.copy(wkd_status = org.astermail.android.api.encryption.WkdStatusResponse(published = !published, url = result.url)) }
            } catch (_: Throwable) {}
        }
    }

    fun toggle_keyserver_publishing() {
        viewModelScope.launch {
            try {
                encryption_api.publish_to_keyserver()
                _state.update { it.copy(keyserver_status = it.keyserver_status?.copy(published = true)) }
            } catch (_: Throwable) {}
        }
    }

    suspend fun export_public_key_now(): String? {
        return try {
            val result = encryption_api.export_public_key()
            result.public_key_armored.ifBlank { null }
        } catch (_: Throwable) { null }
    }

    suspend fun export_private_key_now(password: String): String? {
        val hash = auth_repository.derive_password_hash_b64(password) ?: return null
        return try {
            val result = encryption_api.export_private_key(
                org.astermail.android.api.encryption.ExportKeyRequest(
                    include_private = true,
                    password_hash = hash,
                    format = "armored",
                )
            )
            result.private_key_encrypted?.ifBlank { null }
                ?: result.encrypted_private_key_blob?.ifBlank { null }
        } catch (_: Throwable) { null }
    }

    suspend fun regenerate_recovery_codes_now(): List<String> {
        return try {
            val result = encryption_api.regenerate_recovery_codes()
            _state.update { it.copy(recovery_codes_status = result.info) }
            if (result.codes.isNotEmpty()) {
                session_key_store.put_recovery_codes(result.codes)
            }
            result.codes
        } catch (_: Throwable) { emptyList() }
    }

    fun load_recovery_email() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = recovery_email_api.get_state()
                val identity_key = session_key_store.get_identity_key()
                val enc = response.encrypted_email
                val nonce = response.email_nonce
                val address = if (!enc.isNullOrBlank() && !nonce.isNullOrBlank() && !identity_key.isNullOrBlank()) {
                    try {
                        decrypt_recovery_email(enc, nonce, identity_key)
                    } catch (_: Throwable) {
                        null
                    }
                } else {
                    null
                }
                val is_set = !enc.isNullOrBlank() && !nonce.isNullOrBlank()
                _state.value = _state.value.copy(
                    recovery_email_address = address,
                    recovery_email_set = is_set,
                    recovery_email_verified = response.verified,
                    security_status = _state.value.security_status?.copy(
                        recovery_email_set = is_set,
                        recovery_email_verified = response.verified,
                    ),
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

    fun save_recovery_email(email: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING, error = null)
            val normalized = email.trim().lowercase()
            try {
                val identity_key = session_key_store.get_identity_key()
                    ?: throw IllegalStateException("no identity key")
                val encrypted = encrypt_recovery_email(normalized, identity_key)
                val email_hash = hash_recovery_email(normalized)
                recovery_email_api.save(
                    SaveRecoveryEmailRequest(
                        encrypted_email = encrypted.ciphertext_b64,
                        email_nonce = encrypted.nonce_b64,
                        email_hash = email_hash,
                        plaintext_email = normalized,
                    ),
                )
                _state.value = _state.value.copy(
                    recovery_email_address = normalized,
                    recovery_email_set = true,
                    recovery_email_verified = false,
                    security_status = _state.value.security_status?.copy(
                        recovery_email_set = true,
                        recovery_email_verified = false,
                    ),
                    save_status = SaveStatus.SAVED,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = recovery_email_error_message(t),
                )
            }
        }
    }

    fun resend_recovery_verification() {
        viewModelScope.launch {
            val email = _state.value.recovery_email_address
            if (email.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.recovery_email_resend_failed),
                )
                return@launch
            }
            try {
                recovery_email_api.resend(email)
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.recovery_email_resent),
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    action_result = recovery_email_error_message(t),
                )
            }
        }
    }

    fun remove_recovery_email() {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING, error = null)
            try {
                recovery_email_api.remove()
                _state.value = _state.value.copy(
                    recovery_email_address = null,
                    recovery_email_set = false,
                    recovery_email_verified = false,
                    security_status = _state.value.security_status?.copy(
                        recovery_email_set = false,
                        recovery_email_verified = false,
                    ),
                    save_status = SaveStatus.SAVED,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = recovery_email_error_message(t),
                )
            }
        }
    }

    private fun recovery_email_error_message(t: Throwable): String {
        val detail = (t as? ApiError.UnknownError)?.detail
        return when (detail) {
            RecoveryEmailApiImpl.RECOVERY_EMAIL_IN_USE ->
                context.getString(R.string.recovery_email_already_in_use)
            RecoveryEmailApiImpl.RECOVERY_EMAIL_COOLDOWN ->
                context.getString(R.string.recovery_email_resend_cooldown)
            else -> t.message ?: context.getString(R.string.something_went_wrong)
        }
    }

    private fun derive_recovery_email_key(identity_key: String): ByteArray {
        val material = (identity_key + RECOVERY_EMAIL_KEY_SUFFIX).toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(material)
        material.fill(0)
        return key
    }

    private fun encrypt_recovery_email(email: String, identity_key: String): EncryptedField {
        val data = email.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive_recovery_email_key(identity_key)
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

    private fun decrypt_recovery_email(
        ciphertext_b64: String,
        nonce_b64: String,
        identity_key: String,
    ): String {
        val ciphertext = android.util.Base64.decode(ciphertext_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
        val key = derive_recovery_email_key(identity_key)
        try {
            return String(aes_gcm_decrypt(ciphertext, key, nonce), Charsets.UTF_8)
        } finally {
            key.fill(0)
        }
    }

    private fun hash_recovery_email(email: String): String {
        val material = (RECOVERY_EMAIL_HASH_PREFIX + email.trim().lowercase())
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
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
                var decrypted = response.labels.map { decrypt_label(it) }
                val any_decryption_failed = response.labels.indices.any { i ->
                    !response.labels[i].encrypted_name.isNullOrBlank() &&
                        decrypted[i].encrypted_name.isNullOrBlank()
                }
                if (any_decryption_failed && auth_repository.try_refresh_vault_keys()) {
                    decrypted = response.labels.map { decrypt_label(it) }
                }
                val still_all_failed = response.labels.any { !it.encrypted_name.isNullOrBlank() } &&
                    decrypted.all { it.encrypted_name.isNullOrBlank() }
                if (org.astermail.android.BuildConfig.DEBUG) {
                    val decrypt_failed = response.labels.indices.count { i ->
                        !response.labels[i].encrypted_name.isNullOrBlank() &&
                            decrypted[i].encrypted_name.isNullOrBlank()
                    }
                    android.util.Log.i(
                        "SettingsVM",
                        "load_labels received=${response.labels.size} decrypt_failed=$decrypt_failed " +
                            "all_failed=$still_all_failed identity_key=${session_key_store.get_identity_key() != null}",
                    )
                }
                if (still_all_failed) {
                    val had_readable_labels = _state.value.labels.any { !it.encrypted_name.isNullOrBlank() }
                    _state.value = if (had_readable_labels) {
                        _state.value.copy(is_loading = false)
                    } else {
                        _state.value.copy(labels = decrypted, is_loading = false)
                    }
                    return@launch
                }
                val server_tokens = decrypted.map { it.label_token }.toSet()
                val surviving = _state.value.labels.filter {
                    it.label_token in optimistic_label_tokens && it.label_token !in server_tokens
                }
                val preserved = if (folder_type != null) {
                    _state.value.labels.filter { existing ->
                        existing.label_token !in optimistic_label_tokens &&
                            existing.folder_type != folder_type &&
                            !(folder_type == "folder" && existing.folder_type == "custom")
                    }
                } else emptyList()
                optimistic_label_tokens.removeAll(server_tokens)
                _state.value = _state.value.copy(
                    labels = decrypted + surviving + preserved,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_labels failed", t)
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
                var decrypted = response.tags.map { decrypt_tag(it) }
                val all_decryption_failed = response.tags.any { it.encrypted_name.isNotBlank() } &&
                    decrypted.all { it.encrypted_name.isBlank() }
                if (all_decryption_failed && auth_repository.try_refresh_vault_keys()) {
                    decrypted = response.tags.map { decrypt_tag(it) }
                }
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
                val identity_key = session_key_store.get_identity_key() ?: run {
                    _state.value = _state.value.copy(action_result = context.getString(R.string.failed_create_folder))
                    return@launch
                }
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
                kotlinx.coroutines.withTimeout(10_000L) {
                    labels_api.get_referral_info()
                }
            } catch (_: Throwable) {
                ReferralInfoResponse()
            }
            _state.value = _state.value.copy(referral = info)
        }
    }

    private suspend fun await_identity_key(max_attempts: Int = 12, delay_ms: Long = 200): String? {
        var key = session_key_store.get_identity_key()
        var attempts = 0
        while (key.isNullOrBlank() && attempts < max_attempts) {
            kotlinx.coroutines.delay(delay_ms)
            key = session_key_store.get_identity_key()
            attempts++
        }
        return key
    }

    fun load_preferences() {
        load_preferences_job?.cancel()
        load_preferences_job = viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = preferences_api.get_encrypted_preferences()
                val enc = response.encrypted_preferences
                val nonce = response.preferences_nonce
                val has_encrypted = !enc.isNullOrBlank() && !nonce.isNullOrBlank()
                account_uses_encrypted_prefs = has_encrypted

                if (has_encrypted) {
                    val identity_key = await_identity_key()
                    if (identity_key.isNullOrBlank()) {
                        _state.value = _state.value.copy(
                            preferences = _state.value.preferences ?: UserPreferences(),
                            is_loading = false,
                            error = context.getString(R.string.preferences_locked_retry),
                        )
                        return@launch
                    }
                    val decrypted = try {
                        decrypt_preferences(enc, nonce, identity_key, _state.value.preferences)
                    } catch (_: Throwable) {
                        null
                    }
                    if (decrypted != null) {
                        prefs_load_succeeded = true
                        last_synced_preferences = decrypted
                        _state.value = _state.value.copy(
                            preferences = decrypted,
                            preferences_authoritative = true,
                            is_loading = false,
                        )
                    } else {
                        prefs_load_succeeded = true
                        _state.value = _state.value.copy(
                            preferences = _state.value.preferences ?: UserPreferences(),
                            preferences_authoritative = true,
                            is_loading = false,
                            error = null,
                        )
                    }
                } else {
                    val prefs = load_plaintext_preferences()
                    prefs_load_succeeded = true
                    last_synced_preferences = prefs
                    _state.value = _state.value.copy(
                        preferences = prefs,
                        preferences_authoritative = true,
                        is_loading = false,
                    )
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    preferences = _state.value.preferences ?: UserPreferences(),
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
        if (!prefs_load_succeeded) {
            _state.value = _state.value.copy(
                save_status = SaveStatus.ERROR,
                error = context.getString(R.string.preferences_locked_retry),
            )
            return
        }
        load_preferences_job?.cancel()
        save_preferences_job?.cancel()
        val baseline = last_synced_preferences
        _state.value = _state.value.copy(preferences = prefs, save_status = SaveStatus.SAVING)
        save_preferences_job = viewModelScope.launch {
            try {
                val identity_key = await_identity_key()
                if (!identity_key.isNullOrBlank()) {
                    var to_save = prefs
                    val fresh = preferences_api.get_encrypted_preferences()
                    val fresh_enc = fresh.encrypted_preferences
                    val fresh_nonce = fresh.preferences_nonce
                    if (!fresh_enc.isNullOrBlank() && !fresh_nonce.isNullOrBlank()) {
                        val server_prefs = try {
                            decrypt_preferences(fresh_enc, fresh_nonce, identity_key, baseline)
                        } catch (_: Throwable) {
                            null
                        }
                        if (server_prefs != null) {
                            to_save = rebase_preferences_changes(prefs_json, server_prefs, baseline, prefs)
                        }
                    }
                    val payload = encode_preferences_preserving_unknown(prefs_json, to_save, last_preferences_raw_json)
                    preferences_api.save_encrypted_preferences(encrypt_preferences_payload(payload, identity_key))
                    last_preferences_raw_json = payload
                    last_synced_preferences = to_save
                    _state.value = _state.value.copy(preferences = to_save, save_status = SaveStatus.SAVED)
                } else if (account_uses_encrypted_prefs) {
                    _state.value = _state.value.copy(
                        save_status = SaveStatus.ERROR,
                        error = context.getString(R.string.preferences_locked_retry),
                    )
                    return@launch
                } else {
                    val raw = last_preferences_raw_json
                    if (raw != null) {
                        val payload = encode_preferences_preserving_unknown(prefs_json, prefs, raw)
                        preferences_api.save_preferences_raw(payload)
                        last_preferences_raw_json = payload
                    } else {
                        preferences_api.save_preferences(prefs)
                    }
                    last_synced_preferences = prefs
                    _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                }
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
        val match = constant_time_equals(entered, stored)
        entered.fill(0)
        return match
    }

    private fun constant_time_equals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun update_sidebar_state(key: String, value: Boolean) {
        val current = _state.value.preferences ?: return
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
                val decrypted = response.aliases.map { decrypt_ghost_alias(it) }
                _state.value = _state.value.copy(ghost_aliases = decrypted, is_loading = false)
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
            create_ghost_alias_now(note)
        }
    }

    sealed class GhostAliasResult {
        data class Success(val address: String) : GhostAliasResult()
        data class Failure(val message: String) : GhostAliasResult()
    }

    suspend fun create_ghost_alias_now(note: String): GhostAliasResult {
        return try {
            val domain = "astermail.org"
            val local_part = generate_ghost_local_part()
            val (enc_local, local_nonce) = encrypt_alias_field(local_part)
            val addr_hash = compute_alias_address_hash(local_part, domain)
            val routing_hash = compute_routing_address_hash(local_part, domain)
            val response = ghost_alias_api.create_ghost_alias(
                CreateGhostAliasRequest(
                    encrypted_local_part = enc_local,
                    local_part_nonce = local_nonce,
                    alias_address_hash = addr_hash,
                    routing_address_hash = routing_hash,
                    domain = domain,
                    expires_in_days = 30,
                )
            )
            val new_alias = GhostAlias(
                id = response.id,
                encrypted_local_part = enc_local,
                local_part_nonce = local_nonce,
                alias_address_hash = addr_hash,
                routing_address_hash = routing_hash,
                domain = domain,
                expires_at = response.expires_at,
                decrypted_address = "$local_part@$domain",
            )
            _state.update { s -> s.copy(ghost_aliases = listOf(new_alias) + s.ghost_aliases) }
            GhostAliasResult.Success("$local_part@$domain")
        } catch (t: Throwable) {
            GhostAliasResult.Failure(t.message ?: context.getString(R.string.could_not_create_ghost_alias))
        }
    }

    private fun generate_ghost_local_part(): String {
        val rng = java.security.SecureRandom()
        val first = GHOST_FIRST_WORDS[rng.nextInt(GHOST_FIRST_WORDS.size)]
        val second = GHOST_SECOND_WORDS[rng.nextInt(GHOST_SECOND_WORDS.size)]
        val token = (1..GHOST_TOKEN_LENGTH)
            .map { GHOST_TOKEN_ALPHABET[rng.nextInt(GHOST_TOKEN_ALPHABET.length)] }
            .joinToString("")
        return "$first.$second$token"
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
                    CreateForwardingRuleRequest(
                        name = target.take(200),
                        forward_to = listOf(target),
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

    fun update_forwarding_rule(rule_id: String, target: String, keep_copy: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                auto_forward_api.update_rule(
                    org.astermail.android.api.autoforward.UpdateForwardingRuleRequest(
                        id = rule_id,
                        name = target.take(200),
                        forward_to = listOf(target),
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
        val previous = _state.value.forwarding_rules
        _state.update { s ->
            s.copy(
                forwarding_rules = s.forwarding_rules.map {
                    if (it.id == rule_id) it.copy(is_enabled = enabled) else it
                },
            )
        }
        viewModelScope.launch {
            try {
                auto_forward_api.toggle_rule(
                    ToggleForwardingRuleRequest(id = rule_id, is_enabled = enabled),
                )
            } catch (t: Throwable) {
                _state.update { s -> s.copy(forwarding_rules = previous) }
                _state.value = _state.value.copy(
                    error = t.message ?: context.getString(R.string.something_went_wrong),
                )
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
                val decrypted = response.api_keys.map { key ->
                    val name = if (key.name_encrypted.isNotBlank() && key.name_nonce.isNotBlank()) {
                        try {
                            decrypt_alias_field(key.name_encrypted, key.name_nonce)
                        } catch (_: Throwable) {
                            context.getString(R.string.api_key_default_name)
                        }
                    } else {
                        context.getString(R.string.api_key_default_name)
                    }
                    key.copy(decrypted_name = name)
                }
                _state.value = _state.value.copy(
                    api_keys = decrypted,
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
                val decrypted = response.webhooks.map { hook ->
                    val url = if (hook.url_encrypted.isNotBlank() && hook.url_nonce.isNotBlank()) {
                        try {
                            decrypt_alias_field(hook.url_encrypted, hook.url_nonce)
                        } catch (_: Throwable) {
                            ""
                        }
                    } else {
                        ""
                    }
                    hook.copy(decrypted_url = url)
                }
                _state.value = _state.value.copy(webhooks = decrypted)
            } catch (_: Throwable) {
            }
        }
    }

    fun get_access_token(): String? = token_store.access_token

    fun refresh_access_token_blocking(): String? {
        return try {
            kotlinx.coroutines.runBlocking {
                val current_refresh = token_store.refresh_token
                val response = auth_api.refresh(current_refresh)
                val new_refresh = response.refresh_token ?: current_refresh
                if (new_refresh != null) {
                    token_store.save(response.access_token, new_refresh)
                }
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

    private fun decrypt_label_field_with_fallback(
        ciphertext_b64: String,
        nonce_b64: String,
        identity_keys: List<String>,
    ): String? {
        val ciphertext = android.util.Base64.decode(ciphertext_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
        for (key in identity_keys) {
            for (version in FOLDER_VERSIONS) {
                try {
                    val derived = derive_field_key(key, version)
                    try {
                        val result = aes_gcm_decrypt(ciphertext, derived, nonce)
                        return String(result, Charsets.UTF_8)
                    } finally {
                        derived.fill(0)
                    }
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
        val all_keys = buildList {
            add(identity_key)
            session_key_store.get_previous_keys()?.forEach { add(it) }
        }
        return try {
            val enc_name = label.encrypted_name
            val n_nonce = label.name_nonce
            val name = if (!enc_name.isNullOrBlank() && !n_nonce.isNullOrBlank()) {
                decrypt_label_field_with_fallback(enc_name, n_nonce, all_keys)
            } else enc_name

            val enc_color = label.encrypted_color
            val c_nonce = label.color_nonce
            val color = if (!enc_color.isNullOrBlank() && !c_nonce.isNullOrBlank()) {
                decrypt_label_field_with_fallback(enc_color, c_nonce, all_keys)
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

    private fun decrypt_directory(dir: AliasDirectory): AliasDirectory {
        val enc = dir.encrypted_label
        val nonce = dir.label_nonce
        if (enc.isNullOrBlank() || nonce.isNullOrBlank()) return dir
        return try {
            val label = decrypt_alias_field(enc, nonce)
            dir.copy(decrypted_label = label)
        } catch (_: Throwable) {
            dir
        }
    }

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

    private fun encrypt_alias_field(plaintext: String): Pair<String, String> {
        val key = derive_encryption_key()
        try {
            val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            return android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP) to
                android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
        } finally {
            key.fill(0)
        }
    }

    private fun normalize_alias_local_part(local_part: String): String {
        return local_part.lowercase().replace(".", "")
    }

    private fun compute_alias_address_hash(local_part: String, domain: String): String {
        val enc_key = derive_encryption_key()
        try {
            val info = "astermail-alias-hmac-v1".toByteArray(Charsets.UTF_8)
            val combined = enc_key + info
            val hmac_key_bytes = MessageDigest.getInstance("SHA-256").digest(combined)
            combined.fill(0)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmac_key_bytes, "HmacSHA256"))
            val sig = mac.doFinal("${normalize_alias_local_part(local_part)}@$domain".toByteArray(Charsets.UTF_8))
            hmac_key_bytes.fill(0)
            return android.util.Base64.encodeToString(sig, android.util.Base64.NO_WRAP)
        } finally {
            enc_key.fill(0)
        }
    }

    private fun compute_routing_address_hash(local_part: String, domain: String): String {
        val data = "${normalize_alias_local_part(local_part)}@$domain".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(data)
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    private fun compute_directory_key_hash(key: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.lowercase().toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    private fun decrypt_ghost_alias(alias: GhostAlias): GhostAlias {
        if (alias.encrypted_local_part.isBlank()) return alias.copy(decryption_failed = true)
        return try {
            val local_part = decrypt_alias_field(alias.encrypted_local_part, alias.local_part_nonce)
            val address = if (alias.domain.isNotBlank()) "$local_part@${alias.domain}" else local_part
            alias.copy(decrypted_address = address)
        } catch (_: Throwable) {
            alias.copy(decryption_failed = true)
        }
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

    @Volatile
    private var last_preferences_raw_json: String? = null

    @Volatile
    private var last_synced_preferences: UserPreferences? = null

    private fun decrypt_preferences(
        encrypted_b64: String,
        nonce_b64: String,
        identity_key: String,
        previous: UserPreferences?,
    ): UserPreferences {
        val ciphertext = android.util.Base64.decode(encrypted_b64, android.util.Base64.DEFAULT)
        val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
        val key_material = (identity_key + PREFERENCES_KEY_SUFFIX).toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(key_material)
        val plaintext = aes_gcm_decrypt(ciphertext, key, nonce)
        key.fill(0)
        val json_str = String(plaintext, Charsets.UTF_8)
        last_preferences_raw_json = json_str
        return merge_decrypted_preferences(prefs_json, json_str, previous)
    }

    private suspend fun load_plaintext_preferences(): UserPreferences {
        val raw = try { preferences_api.get_preferences_raw() } catch (_: Throwable) { null }
        val sanitized = raw?.let { sanitize_plaintext_raw(it) }
        if (sanitized != null) {
            val merged = runCatching {
                merge_decrypted_preferences(prefs_json, sanitized, _state.value.preferences)
            }.getOrNull()
            if (merged != null) {
                last_preferences_raw_json = sanitized
                return merged
            }
        }
        return preferences_api.get_preferences()
    }

    private fun sanitize_plaintext_raw(raw: String): String? {
        val obj = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
        }.getOrNull() ?: return null
        val filtered = kotlinx.serialization.json.buildJsonObject {
            for ((k, v) in obj) {
                if (k != "encrypted_preferences" && k != "preferences_nonce") put(k, v)
            }
        }
        return prefs_json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), filtered)
    }

    private fun encrypt_preferences_payload(
        json_str: String,
        identity_key: String,
    ): SaveEncryptedPreferencesRequest {
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

    fun load_reserved_addresses() {
        viewModelScope.launch {
            _state.update { it.copy(is_loading = true, error = null) }
            try {
                val r = family_api.list_reservations()
                _state.update { it.copy(
                    reserved_addresses = r.reservations,
                    family_seats_used = r.seats_used,
                    family_max_members = r.max_members,
                    is_loading = false,
                ) }
            } catch (t: Throwable) {
                _state.update { it.copy(is_loading = false, error = t.message) }
            }
        }
    }

    fun release_reservation(id: String, on_done: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                family_api.release_reservation(id)
                _state.update { it.copy(reserved_addresses = it.reserved_addresses.filter { r -> r.id != id }) }
                on_done(true)
            } catch (_: Throwable) {
                on_done(false)
            }
        }
    }

    fun regenerate_reservation_link(id: String, on_done: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val r = family_api.regenerate_claim_link(id)
                _state.update { it.copy(
                    reserved_addresses = it.reserved_addresses.map { a ->
                        if (a.id == id) a.copy(claim_url = r.claim_url) else a
                    },
                ) }
                on_done(r.claim_url)
            } catch (_: Throwable) {
                on_done(null)
            }
        }
    }

    companion object {
        private const val SALT_PREFIX = "aster-hkdf-salt-v1:"
        private const val DERIVED_KEY_INFO = "aster-storage-encryption-key-v1"
        private const val TAG_VERSION_CURRENT = "astermail-tags-v1"
        private const val FOLDER_VERSION_CURRENT = "astermail-labels-v1"
        private const val PREFERENCES_KEY_SUFFIX = "astermail-preferences-v1"
        private const val RECOVERY_EMAIL_KEY_SUFFIX = "astermail-recovery-email-v1"
        private const val RECOVERY_EMAIL_HASH_PREFIX = "aster-recovery-email-uniqueness-v1:"
        private val TAG_VERSIONS = listOf(TAG_VERSION_CURRENT)
        private val FOLDER_VERSIONS = listOf(FOLDER_VERSION_CURRENT, TAG_VERSION_CURRENT)
        private val ALIAS_VERSIONS = listOf("astermail-envelope-v1", "astermail-import-v1")

        private const val GHOST_TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"
        private const val GHOST_TOKEN_LENGTH = 8
        private val GHOST_FIRST_WORDS = listOf(
            "sage", "ember", "coral", "cedar", "haven", "iris", "jasper", "luna", "moss", "reed",
            "wren", "ash", "briar", "brook", "clover", "dawn", "elm", "fern", "flint", "glen",
            "hazel", "ivy", "jade", "lark", "maple", "nova", "olive", "pearl", "pine", "rain",
            "robin", "rowan", "sky", "thorn", "vale", "willow", "birch", "cliff", "cove", "dune",
            "frost", "gale", "heath", "indigo", "juniper", "kit", "lake", "marsh", "mist", "oak",
            "petal", "quill", "ridge", "river", "rune", "shade", "silver", "slate", "snow", "sparrow",
            "stone", "storm", "summit", "terra", "tide", "vine", "wave", "winter", "zen", "aurora",
            "bay", "blaze", "breeze", "cobalt", "delta", "echo", "flora", "grove", "harbor", "isle",
            "lyric", "onyx",
        )
        private val GHOST_SECOND_WORDS = listOf(
            "ridge", "vale", "frost", "stone", "brook", "field", "wood", "lake", "dale", "ward",
            "hill", "lane", "marsh", "cross", "moon", "star", "light", "crest", "peak", "shore",
            "drift", "bloom", "glade", "grove", "haven", "moor", "cliff", "dell", "fern", "ford",
            "gate", "glen", "haze", "isle", "knoll", "ledge", "loft", "nest", "path", "pond",
            "rain", "reef", "rise", "sage", "shade", "slope", "spring", "trail", "veil", "vista",
            "wind", "hollow", "ember", "arrow", "flare", "harbor", "bridge", "canyon", "dusk", "echo",
            "flame", "forge", "gleam", "heron", "inlet", "jewel", "kelp", "leaf", "meadow", "north",
            "orchid", "pine", "quartz", "raven", "sierra", "torch", "umber",
        )
    }
}

