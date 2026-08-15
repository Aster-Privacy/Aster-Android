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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.UserInfo
import org.astermail.android.api.autoforward.AutoForwardApi
import org.astermail.android.api.autoforward.CreateForwardingRuleRequest
import org.astermail.android.api.autoforward.ForwardingRule
import org.astermail.android.api.autoforward.ResendForwardingConfirmationRequest
import org.astermail.android.api.autoforward.ToggleForwardingRuleRequest
import org.astermail.android.api.developer.ApiKeyInfo
import org.astermail.android.api.developer.CreateApiKeyRequest
import org.astermail.android.api.developer.DeveloperApi
import org.astermail.android.api.developer.WebhookInfo
import org.astermail.android.api.family.FamilyApi
import org.astermail.android.api.family.FamilySeatUsage
import org.astermail.android.api.family.ReservedAddress
import org.astermail.android.api.family.family_seat_usage
import org.astermail.android.api.aliases.AddPinRequest
import org.astermail.android.api.aliases.AliasRuleActions
import org.astermail.android.api.aliases.AliasRuleCondition
import org.astermail.android.api.aliases.CreateAliasContactRequest
import org.astermail.android.api.aliases.CreateAliasRuleRequest
import org.astermail.android.api.aliases.SENDER_PIN_MODE_OFF
import org.astermail.android.api.aliases.UpdateAliasRuleRequest
import org.astermail.android.api.ghost.CreateGhostAliasRequest
import org.astermail.android.api.ghost.GHOST_ALIAS_DOMAIN
import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.ghost.GhostAliasApi
import org.astermail.android.api.labels.BulkReorderLabelsRequest
import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.labels.ReferralHistoryItem
import org.astermail.android.api.labels.ReferralInfoResponse
import org.astermail.android.api.labels.ReorderLabelEntry
import org.astermail.android.api.labels.RemoveFolderPasswordRequest
import org.astermail.android.api.labels.SetFolderPasswordRequest
import org.astermail.android.api.labels.UpdateLabelRequest
import org.astermail.android.api.labels.VerifyFolderPasswordRequest
import org.astermail.android.folders.folder_sibling_group
import org.astermail.android.api.tags.CreateTagRequest
import org.astermail.android.api.tags.TagItem
import org.astermail.android.api.tags.TagsApi
import org.astermail.android.api.tags.UpdateTagRequest
import org.astermail.android.api.preferences.PreferencesApi
import org.astermail.android.api.preferences.SaveEncryptedPreferencesRequest
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.preferences.effective_theme_values
import org.astermail.android.api.preferences.encode_preferences_preserving_unknown
import org.astermail.android.api.preferences.merge_decrypted_preferences
import org.astermail.android.api.preferences.rebase_preferences_changes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.astermail.android.api.recovery_email.RecoveryEmailApi
import org.astermail.android.api.recovery_email.RecoveryEmailApiImpl
import org.astermail.android.api.recovery_email.RecoveryEmailError
import org.astermail.android.api.recovery_email.RemoveRecoveryEmailRequest
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
import org.astermail.android.api.settings.AliasRun
import org.astermail.android.api.settings.AllowSenderRequest
import org.astermail.android.api.settings.AllowedSenderInfo
import org.astermail.android.api.settings.BlockSenderRequest
import org.astermail.android.api.settings.BulkAddAddressItem
import org.astermail.android.api.settings.BulkAddAddressesRequest
import org.astermail.android.api.settings.BulkCreateAliasItem
import org.astermail.android.api.settings.BulkCreateAliasRequest
import org.astermail.android.api.settings.BlockedSenderInfo
import org.astermail.android.api.settings.CheckAliasAvailabilityRequest
import org.astermail.android.api.settings.CreateAliasRequest
import org.astermail.android.api.settings.CreateDirectoryRequest
import org.astermail.android.api.settings.CreateDomainAddressRequest
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.api.settings.UpdateDomainAddressRequest
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
import org.astermail.android.storage.PreferencesCacheStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.TextSize
import org.astermail.android.storage.ThemeMode
import org.astermail.android.storage.ThemeStore
import org.astermail.android.storage.TokenStore

data class BlockedSenderView(
    val id: String,
    val sender_token: String,
    val address: String,
    val is_domain: Boolean,
    val created_at: String?,
)

data class AllowedSenderView(
    val id: String,
    val sender_token: String,
    val address: String,
    val is_domain: Boolean,
    val created_at: String?,
)

data class SettingsUiState(
    val user: UserInfo? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val blocked_senders: List<BlockedSenderView> = emptyList(),
    val blocked_senders_loading: Boolean = false,
    val blocked_senders_error: String? = null,
    val allowed_senders: List<AllowedSenderView> = emptyList(),
    val allowed_senders_loading: Boolean = false,
    val allowed_senders_error: String? = null,
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
    val referral_history: List<ReferralHistoryItem> = emptyList(),
    val preferences: UserPreferences? = null,
    val preferences_authoritative: Boolean = false,
    val reserved_addresses: List<ReservedAddress> = emptyList(),
    val family_seats: FamilySeatUsage? = null,
    val ghost_aliases: List<GhostAlias> = emptyList(),
    val forwarding_rules: List<ForwardingRule> = emptyList(),
    val forwarding_resending_address: String? = null,
    val forwarding_notice: String? = null,
    val api_keys: List<ApiKeyInfo> = emptyList(),
    val webhooks: List<WebhookInfo> = emptyList(),
    val directories: List<AliasDirectory> = emptyList(),
    val directories_loading: Boolean = false,
    val deleted_aliases: List<DecryptedDeletedAlias> = emptyList(),
    val deleted_aliases_loading: Boolean = false,
    val alias_preferences: AliasPreferences? = null,
    val expanded_alias_ids: Set<String> = emptySet(),
    val alias_details: Map<String, AliasDetailState> = emptyMap(),
    val mail_rules: List<org.astermail.android.api.mail_rules.MailRule> = emptyList(),
    val recovery_email_address: String? = null,
    val recovery_email_set: Boolean = false,
    val recovery_email_verified: Boolean = false,
    val recovery_email_step_up_required: Boolean = false,
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
    val badge_preferences: org.astermail.android.api.user.BadgePreferences? = null,
    val is_loading: Boolean = false,
    val error: String? = null,
    val save_status: SaveStatus = SaveStatus.IDLE,
    val action_result: String? = null,
    val default_sender_id: String? = null,
    val connection_method: String = "direct",
    val connection_loading: Boolean = false,
    val connection_saving: Boolean = false,
)

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

data class DecryptedDeletedAlias(
    val id: String,
    val address: String,
    val display_name: String?,
    val deleted_at: String,
)

@kotlinx.serialization.Serializable
data class DecryptedSignature(
    val id: String,
    val name: String,
    val content: String,
    val is_default: Boolean,
    val is_html: Boolean,
    val alias_id: String?,
    val placement: Int?,
)

private val cached_preferences_json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val SUBSCRIPTION_TTL_MS = 300_000L
private const val TAGS_TTL_MS = 60_000L
private const val PREFERENCES_TTL_MS = 30_000L
private const val PROFILE_TTL_MS = 60_000L
private const val LIST_TTL_MS = 60_000L
private const val MAX_ALIAS_WEBSITES = 10
private const val IMPORT_BATCH_SIZE = 100
private const val MAX_WEBSITE_URL_LENGTH = 200
private const val ALIAS_RUN_POLL_MIN_MS = 1200L
private const val ALIAS_RUN_POLL_MAX_MS = 8000L
private const val ALIAS_RUN_POLL_MAX_FAILURES = 5

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
    private val alias_detail_api: org.astermail.android.api.aliases.AliasDetailApi,
    private val mail_rules_api: org.astermail.android.api.mail_rules.MailRulesApi,
    private val auth_repository: AuthRepository,
    private val session_key_store: SessionKeyStore,
    private val token_store: TokenStore,
    private val preferences_cache: PreferencesCacheStore,
    private val theme_store: ThemeStore,
    val account_store: org.astermail.android.storage.AccountStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    internal var default_dispatcher: CoroutineDispatcher = Dispatchers.Default

    private val _state = MutableStateFlow(SettingsUiState())
    private val alias_run_polls = mutableMapOf<String, kotlinx.coroutines.Job>()
    private var last_subscription_load_ms = 0L
    private var last_tags_load_ms = 0L
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
    private var last_preferences_load_ms = 0L
    private var last_default_sender_load_ms = 0L
    private var last_profile_load_ms = 0L
    private var last_aliases_load_ms = 0L
    private var last_mail_rules_load_ms = 0L
    private var last_domain_addresses_load_ms = 0L
    private var last_storage_load_ms = 0L
    private val last_labels_load_ms = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var save_preferences_job: kotlinx.coroutines.Job? = null
    private var prefs_load_succeeded = false
    private var account_uses_encrypted_prefs = false

    init {
        hydrate_cached_preferences()
        hydrate_cached_signatures()
        load_preferences()
    }

    private fun cache_account_key(): String? =
        session_key_store.get_user_id()?.takeIf { it.isNotBlank() }
            ?: account_store.get_current_id()?.takeIf { it.isNotBlank() }

    fun refresh_cached_preferences() {
        hydrate_cached_preferences()
    }

    private fun hydrate_cached_preferences() {
        val raw = preferences_cache.read(cache_account_key()) ?: return
        val cached = runCatching {
            cached_preferences_json.decodeFromString(UserPreferences.serializer(), raw)
        }.getOrNull() ?: return
        _state.value = _state.value.copy(preferences = cached)
        apply_preferences_to_theme_store(cached)
    }

    private fun persist_cached_preferences(prefs: UserPreferences) {
        val key = cache_account_key() ?: return
        val raw = runCatching {
            cached_preferences_json.encodeToString(UserPreferences.serializer(), prefs)
        }.getOrNull() ?: return
        preferences_cache.write(key, raw)
    }

    fun clear_cached_preferences(account_key: String?) {
        preferences_cache.clear(account_key)
    }

    private fun apply_preferences_to_theme_store(prefs: UserPreferences) {
        val theme_values = effective_theme_values(prefs)
        val mode = when (theme_values.theme) {
            ThemeMode.light.name -> ThemeMode.light
            ThemeMode.dark.name -> ThemeMode.dark
            else -> ThemeMode.system
        }
        if (theme_store.theme_mode.value != mode) theme_store.set_theme_mode(mode)
        if (theme_store.color_theme.value != theme_values.color_theme) {
            theme_store.set_color_theme(theme_values.color_theme)
        }
        if (theme_store.custom_theme_seed.value != theme_values.custom_theme_seed) {
            theme_store.set_custom_theme_seed(theme_values.custom_theme_seed)
        }
        if (theme_store.custom_theme_overrides.value != prefs.custom_theme_overrides) {
            theme_store.set_custom_theme_overrides(prefs.custom_theme_overrides)
        }
        if (theme_store.font_choice.value != prefs.font_choice) {
            theme_store.set_font_choice(prefs.font_choice)
        }
        val size = when (prefs.font_size_scale) {
            "small" -> TextSize.small
            "large" -> TextSize.large
            "extra_large" -> TextSize.extra_large
            else -> TextSize.default_size
        }
        if (theme_store.text_size.value != size) theme_store.set_text_size(size)
        if (theme_store.high_contrast.value != prefs.high_contrast) {
            theme_store.set_high_contrast(prefs.high_contrast)
        }
        if (theme_store.reduce_transparency.value != prefs.reduce_transparency) {
            theme_store.set_reduce_transparency(prefs.reduce_transparency)
        }
        if (theme_store.reduce_motion.value != prefs.reduce_motion) {
            theme_store.set_reduce_motion(prefs.reduce_motion)
        }
        if (theme_store.compact_mode.value != prefs.compact_mode) {
            theme_store.set_compact_mode(prefs.compact_mode)
        }
        if (theme_store.text_spacing.value != prefs.text_spacing) {
            theme_store.set_text_spacing(prefs.text_spacing)
        }
        if (theme_store.underline_links.value != prefs.underline_links) {
            theme_store.set_underline_links(prefs.underline_links)
        }
        if (theme_store.dyslexia_font.value != prefs.dyslexia_font) {
            theme_store.set_dyslexia_font(prefs.dyslexia_font)
        }
        if (theme_store.haptic_enabled.value != prefs.haptic_enabled) {
            theme_store.set_haptic_enabled(prefs.haptic_enabled)
        }
    }

    fun load_profile(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force &&
            _state.value.user != null &&
            last_profile_load_ms != 0L &&
            now - last_profile_load_ms < PROFILE_TTL_MS
        ) {
            load_default_sender()
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val user = auth_api.me()
                last_profile_load_ms = System.currentTimeMillis()
                _state.value = _state.value.copy(user = user, is_loading = false)
                auth_repository.absorb_profile(user)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = user_facing_error(t),
                )
            }
        }
        load_default_sender()
    }

    fun load_badges() {
        hydrate_cached_badges()
        viewModelScope.launch {
            try {
                val result = user_api.fetch_badges()
                if (result != _state.value.badges) {
                    _state.value = _state.value.copy(badges = result)
                }
                persist_cached_badges(result)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_badges", t)
            }
        }
        viewModelScope.launch {
            try {
                val prefs = user_api.fetch_badge_preferences()
                _state.value = _state.value.copy(badge_preferences = prefs)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_badge_preferences", t)
            }
        }
    }

    fun update_badge_preferences(request: org.astermail.android.api.user.UpdateBadgePreferencesRequest) {
        val previous = _state.value.badge_preferences ?: org.astermail.android.api.user.BadgePreferences()
        _state.value = _state.value.copy(
            badge_preferences = previous.copy(
                active_badge_slug = if (request.clear_active_badge == true) {
                    null
                } else {
                    request.active_badge_slug ?: previous.active_badge_slug
                },
                show_badge_profile = request.show_badge_profile ?: previous.show_badge_profile,
                show_badge_signature = request.show_badge_signature ?: previous.show_badge_signature,
                show_badge_ring = request.show_badge_ring ?: previous.show_badge_ring,
            ),
        )
        viewModelScope.launch {
            try {
                val updated = user_api.update_badge_preferences(request)
                _state.value = _state.value.copy(badge_preferences = updated)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(badge_preferences = previous)
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "update_badge_preferences", t)
            }
        }
    }

    private fun hydrate_cached_badges() {
        if (_state.value.badges.isNotEmpty()) return
        val raw = preferences_cache.read_badges(cache_account_key()) ?: return
        val cached = runCatching {
            cached_preferences_json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(
                    org.astermail.android.api.user.Badge.serializer(),
                ),
                raw,
            )
        }.getOrNull() ?: return
        if (cached.isEmpty()) return
        _state.value = _state.value.copy(badges = cached)
    }

    private fun persist_cached_badges(badges: List<org.astermail.android.api.user.Badge>) {
        val key = cache_account_key() ?: return
        val raw = runCatching {
            cached_preferences_json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    org.astermail.android.api.user.Badge.serializer(),
                ),
                badges,
            )
        }.getOrNull() ?: return
        preferences_cache.write_badges(key, raw)
    }

    fun load_default_sender() {
        val now = System.currentTimeMillis()
        if (now - last_default_sender_load_ms < PREFERENCES_TTL_MS) return
        last_default_sender_load_ms = now
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
                    error = user_facing_error(t),
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
                    error = user_facing_error(t),
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
                    error = user_facing_error(t),
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

    fun load_connection_preference() {
        viewModelScope.launch {
            _state.value = _state.value.copy(connection_loading = true)
            try {
                val response = settings_api.get_connection_preference()
                _state.value = _state.value.copy(
                    connection_method = response.method ?: "direct",
                    connection_loading = false,
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(connection_loading = false)
            }
        }
    }

    fun update_connection_preference(method: String) {
        val previous = _state.value.connection_method
        if (method == previous) return
        _state.value = _state.value.copy(connection_method = method, connection_saving = true)
        viewModelScope.launch {
            try {
                settings_api.update_connection_preference(method)
                _state.value = _state.value.copy(connection_saving = false)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    connection_method = previous,
                    connection_saving = false,
                    action_result = context.getString(R.string.failed_save_connection_preference),
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

    fun logout(on_done: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            auth_repository.logout()
            on_done(auth_repository.is_signed_in.value)
        }
    }

    fun reset_save_status() {
        _state.value = _state.value.copy(save_status = SaveStatus.IDLE)
    }

    fun reset_transient_state() {
        val current = _state.value
        if (current.error == null && current.action_result == null && current.save_status == SaveStatus.IDLE) return
        _state.value = current.copy(error = null, action_result = null, save_status = SaveStatus.IDLE)
    }

    fun reset_for_account_switch() {
        org.astermail.android.folders.folder_lock_store.lock_all()
        load_preferences_job?.cancel()
        save_preferences_job?.cancel()
        prefs_load_succeeded = false
        account_uses_encrypted_prefs = false
        last_preferences_raw_json = null
        last_synced_preferences = null
        last_subscription_load_ms = 0L
        last_tags_load_ms = 0L
        last_preferences_load_ms = 0L
        last_default_sender_load_ms = 0L
        last_profile_load_ms = 0L
        last_aliases_load_ms = 0L
        last_mail_rules_load_ms = 0L
        last_domain_addresses_load_ms = 0L
        last_storage_load_ms = 0L
        last_labels_load_ms.clear()
        _state.value = SettingsUiState()
        hydrate_cached_preferences()
    }

    fun load_blocked_senders() {
        viewModelScope.launch {
            _state.update { it.copy(blocked_senders_loading = true, blocked_senders_error = null) }
            try {
                val response = settings_api.list_blocked_senders()
                val decrypted = withContext(default_dispatcher) {
                    response.blocked_senders.mapNotNull { item ->
                        val address = decrypt_blocked_sender_address(item) ?: return@mapNotNull null
                        BlockedSenderView(
                            id = item.id,
                            sender_token = item.sender_token,
                            address = address,
                            is_domain = item.is_domain,
                            created_at = item.created_at,
                        )
                    }
                }
                _state.update {
                    it.copy(blocked_senders = decrypted, blocked_senders_loading = false)
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        blocked_senders_loading = false,
                        blocked_senders_error = user_facing_error(t),
                    )
                }
            }
        }
    }

    fun block_sender(address: String, on_result: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val normalized = address.trim().lowercase()
            if (normalized.isEmpty()) {
                on_result(false)
                return@launch
            }
            try {
                val is_domain = !normalized.contains('@')
                val request = withContext(default_dispatcher) {
                    build_block_sender_request(normalized, is_domain)
                }
                settings_api.block_sender(request)
                _state.update { s ->
                    s.copy(
                        blocked_senders = listOf(
                            BlockedSenderView(
                                id = request.sender_token,
                                sender_token = request.sender_token,
                                address = normalized,
                                is_domain = is_domain,
                                created_at = null,
                            ),
                        ) + s.blocked_senders.filter { it.address != normalized },
                    )
                }
                on_result(true)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_block_sender),
                )
                on_result(false)
            }
        }
    }

    fun unblock_sender(sender_token: String) {
        viewModelScope.launch {
            val previous = _state.value.blocked_senders
            _state.update { s -> s.copy(blocked_senders = s.blocked_senders.filter { it.sender_token != sender_token }) }
            try {
                settings_api.unblock_sender(sender_token)
            } catch (_: Throwable) {
                _state.update { s ->
                    s.copy(
                        blocked_senders = previous,
                        action_result = context.getString(R.string.failed_unblock_sender),
                    )
                }
            }
        }
    }

    private fun blocked_senders_hmac_key(): ByteArray {
        val raw = derive_encryption_key()
        try {
            val info = BLOCKED_SENDERS_HMAC_INFO.toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(raw + info)
        } finally {
            raw.fill(0)
        }
    }

    private fun hmac_b64(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return android.util.Base64.encodeToString(mac.doFinal(data), android.util.Base64.NO_WRAP)
    }

    private fun blocked_sender_integrity_hash(key: ByteArray, encrypted: String, nonce: String): String =
        hmac_b64(key, "$encrypted:$nonce:$BLOCKED_SENDERS_INTEGRITY_INFO".toByteArray(Charsets.UTF_8))

    private fun decrypt_blocked_sender_address(item: BlockedSenderInfo): String? {
        if (item.encrypted_sender_data.isBlank() || item.sender_data_nonce.isBlank()) return null
        return try {
            val hmac_key = blocked_senders_hmac_key()
            try {
                if (item.integrity_hash.isNotBlank()) {
                    val expected = blocked_sender_integrity_hash(
                        hmac_key,
                        item.encrypted_sender_data,
                        item.sender_data_nonce,
                    )
                    if (expected != item.integrity_hash) return null
                }
            } finally {
                hmac_key.fill(0)
            }
            val json = decrypt_alias_field(item.encrypted_sender_data, item.sender_data_nonce)
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
            parsed["email"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun build_block_sender_request(normalized: String, is_domain: Boolean): BlockSenderRequest {
        val hmac_key = blocked_senders_hmac_key()
        try {
            val prefix = if (is_domain) "domain:" else ""
            val sender_token = hmac_b64(hmac_key, (prefix + normalized).toByteArray(Charsets.UTF_8))
            val sender_hash = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val blocked_at = java.time.format.DateTimeFormatter.ISO_INSTANT
                .format(java.time.Instant.now())
            val payload = buildJsonObject {
                put("email", normalized)
                put("blocked_at", blocked_at)
                put("is_domain", is_domain)
                put("_encrypted_at", blocked_at)
            }.toString()
            val key = derive_encryption_key()
            try {
                val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
                val ciphertext = AesGcm.encrypt(key, nonce, payload.toByteArray(Charsets.UTF_8))
                val encrypted_sender_data =
                    android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
                val sender_data_nonce =
                    android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
                return BlockSenderRequest(
                    sender_token = sender_token,
                    sender_hash = sender_hash,
                    encrypted_sender_data = encrypted_sender_data,
                    sender_data_nonce = sender_data_nonce,
                    integrity_hash = blocked_sender_integrity_hash(
                        hmac_key,
                        encrypted_sender_data,
                        sender_data_nonce,
                    ),
                    is_domain = is_domain,
                )
            } finally {
                key.fill(0)
            }
        } finally {
            hmac_key.fill(0)
        }
    }

    private fun allowed_senders_hmac_key(): ByteArray {
        val raw = derive_encryption_key()
        try {
            val info = ALLOWED_SENDERS_HMAC_INFO.toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(raw + info)
        } finally {
            raw.fill(0)
        }
    }

    private fun allowed_sender_integrity_hash(key: ByteArray, encrypted: String, nonce: String): String =
        hmac_b64(key, "$encrypted:$nonce:$ALLOWED_SENDERS_INTEGRITY_INFO".toByteArray(Charsets.UTF_8))

    private fun decrypt_allowed_sender_address(item: AllowedSenderInfo): String? {
        if (item.encrypted_sender_data.isBlank() || item.sender_data_nonce.isBlank()) return null
        return try {
            val hmac_key = allowed_senders_hmac_key()
            try {
                if (item.integrity_hash.isNotBlank()) {
                    val expected = allowed_sender_integrity_hash(
                        hmac_key,
                        item.encrypted_sender_data,
                        item.sender_data_nonce,
                    )
                    if (expected != item.integrity_hash) return null
                }
            } finally {
                hmac_key.fill(0)
            }
            val json = decrypt_alias_field(item.encrypted_sender_data, item.sender_data_nonce)
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
            parsed["email"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun build_allow_sender_request(normalized: String, is_domain: Boolean): AllowSenderRequest {
        val hmac_key = allowed_senders_hmac_key()
        try {
            val prefix = if (is_domain) "domain:" else "email:"
            val sender_token = hmac_b64(hmac_key, (prefix + normalized).toByteArray(Charsets.UTF_8))
            val sender_hash = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val allowed_at = java.time.format.DateTimeFormatter.ISO_INSTANT
                .format(java.time.Instant.now())
            val payload = buildJsonObject {
                put("email", normalized)
                put("allowed_at", allowed_at)
                put("is_domain", is_domain)
                put("_encrypted_at", allowed_at)
            }.toString()
            val key = derive_encryption_key()
            try {
                val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
                val ciphertext = AesGcm.encrypt(key, nonce, payload.toByteArray(Charsets.UTF_8))
                val encrypted_sender_data =
                    android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
                val sender_data_nonce =
                    android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
                return AllowSenderRequest(
                    sender_token = sender_token,
                    sender_hash = sender_hash,
                    encrypted_sender_data = encrypted_sender_data,
                    sender_data_nonce = sender_data_nonce,
                    integrity_hash = allowed_sender_integrity_hash(
                        hmac_key,
                        encrypted_sender_data,
                        sender_data_nonce,
                    ),
                    is_domain = is_domain,
                )
            } finally {
                key.fill(0)
            }
        } finally {
            hmac_key.fill(0)
        }
    }

    fun load_allowed_senders() {
        viewModelScope.launch {
            _state.update { it.copy(allowed_senders_loading = true, allowed_senders_error = null) }
            try {
                val response = settings_api.list_allowed_senders()
                val decrypted = withContext(default_dispatcher) {
                    response.allowed_senders.mapNotNull { item ->
                        val address = decrypt_allowed_sender_address(item) ?: return@mapNotNull null
                        AllowedSenderView(
                            id = item.id,
                            sender_token = item.sender_token,
                            address = address,
                            is_domain = item.is_domain,
                            created_at = item.created_at,
                        )
                    }
                }
                _state.update {
                    it.copy(allowed_senders = decrypted, allowed_senders_loading = false)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update {
                    it.copy(
                        allowed_senders_loading = false,
                        allowed_senders_error = user_facing_error(t),
                    )
                }
            }
        }
    }

    fun allow_sender(address: String, is_domain: Boolean, on_result: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val normalized = address.trim().lowercase()
            if (normalized.isEmpty()) {
                on_result(false)
                return@launch
            }
            try {
                val request = withContext(default_dispatcher) {
                    build_allow_sender_request(normalized, is_domain)
                }
                settings_api.allow_sender(request)
                _state.update { s ->
                    s.copy(
                        allowed_senders = listOf(
                            AllowedSenderView(
                                id = request.sender_token,
                                sender_token = request.sender_token,
                                address = normalized,
                                is_domain = is_domain,
                                created_at = null,
                            ),
                        ) + s.allowed_senders.filter { it.sender_token != request.sender_token },
                        action_result = context.getString(R.string.sender_added_to_allowlist),
                    )
                }
                on_result(true)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_allow_sender),
                )
                on_result(false)
            }
        }
    }

    fun remove_allowed_sender(sender_token: String) {
        viewModelScope.launch {
            val previous = _state.value.allowed_senders
            _state.update { s -> s.copy(allowed_senders = s.allowed_senders.filter { it.sender_token != sender_token }) }
            try {
                settings_api.remove_allowed_sender(sender_token)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update { s ->
                    s.copy(
                        allowed_senders = previous,
                        action_result = context.getString(R.string.failed_remove_allowed_sender),
                    )
                }
            }
        }
    }

    fun load_mail_rules(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && last_mail_rules_load_ms != 0L && now - last_mail_rules_load_ms < LIST_TTL_MS) return
        last_mail_rules_load_ms = now
        viewModelScope.launch {
            try {
                val response = mail_rules_api.list()
                _state.update { it.copy(mail_rules = response.rules) }
            } catch (t: Throwable) {
                last_mail_rules_load_ms = 0L
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("SettingsVM", "load_mail_rules failed", t)
                }
            }
        }
    }

    fun load_aliases(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && last_aliases_load_ms != 0L && now - last_aliases_load_ms < LIST_TTL_MS) return
        last_aliases_load_ms = now
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val page_size = 1000
                val max_pages = 50
                val first_page = settings_api.list_aliases(limit = page_size, offset = 0)
                val max_aliases = first_page.max_aliases
                val all_aliases = mutableListOf<AliasInfo>()
                all_aliases.addAll(first_page.aliases)
                if (first_page.has_more && first_page.aliases.isNotEmpty()) {
                    val effective_page_size = first_page.aliases.size
                    val remaining = (first_page.total - effective_page_size).coerceAtLeast(0)
                    val remaining_pages = ((remaining + effective_page_size - 1) / effective_page_size)
                        .toInt()
                        .coerceAtMost(max_pages - 1)
                    val later_pages = coroutineScope {
                        (1..remaining_pages).map { page ->
                            async {
                                settings_api.list_aliases(
                                    limit = effective_page_size,
                                    offset = effective_page_size * page,
                                )
                            }
                        }.awaitAll()
                    }
                    val seen_ids = all_aliases.mapTo(mutableSetOf()) { it.id }
                    later_pages.forEach { response ->
                        response.aliases.forEach { alias ->
                            if (seen_ids.add(alias.id)) all_aliases.add(alias)
                        }
                    }
                }
                val decrypt_all: suspend () -> List<AliasInfo> = {
                    withContext(default_dispatcher) {
                        coroutineScope {
                            all_aliases.chunked(250).map { chunk ->
                                async { chunk.map { decrypt_alias(it) } }
                            }.awaitAll().flatten()
                        }
                    }
                }
                var decrypted = decrypt_all()
                if (decrypted.any { it.decryption_failed } && auth_repository.try_refresh_vault_keys()) {
                    decrypted = decrypt_all()
                }
                _state.value = _state.value.copy(
                    aliases = decrypted,
                    max_aliases = max_aliases,
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = user_facing_error(t),
                )
            }
        }
    }

    fun load_custom_domain_addresses(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && last_domain_addresses_load_ms != 0L && now - last_domain_addresses_load_ms < LIST_TTL_MS) return
        last_domain_addresses_load_ms = now
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
                    val display_name = addr.encrypted_display_name
                        ?.takeIf { it.isNotBlank() && !addr.display_name_nonce.isNullOrBlank() }
                        ?.let {
                            try {
                                decrypt_alias_field(it, addr.display_name_nonce.orEmpty())
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    addr.copy(encrypted_local_part = local_part, encrypted_display_name = display_name)
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
                load_aliases(force = true)
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
        viewModelScope.launch { create_alias_now(local_part, domain, display_name = display_name) }
    }

    fun set_alias_delivery(alias_id: String, folder_token: String?, to_archive: Boolean) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val next_token = if (to_archive) null else folder_token
        _state.update { s ->
            s.copy(
                aliases = s.aliases.map {
                    if (it.id == alias_id) {
                        it.copy(never_inbox = to_archive, delivery_folder_token = next_token)
                    } else it
                },
            )
        }
        viewModelScope.launch {
            try {
                val request = if (next_token != null) {
                    UpdateAliasRequest(delivery_folder_token = next_token)
                } else {
                    UpdateAliasRequest(never_inbox = to_archive)
                }
                settings_api.update_alias(alias_id, request)
            } catch (_: Throwable) {
                _state.update { s ->
                    s.copy(
                        aliases = s.aliases.map {
                            if (it.id == alias_id) {
                                it.copy(
                                    never_inbox = current.never_inbox,
                                    delivery_folder_token = current.delivery_folder_token,
                                )
                            } else it
                        },
                    )
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun set_alias_delivery_label(alias_id: String, label_token: String?) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val next_token = label_token?.takeIf { it.isNotBlank() }
        if (current.delivery_label_token == next_token) return
        _state.update { s ->
            s.copy(
                aliases = s.aliases.map {
                    if (it.id == alias_id) it.copy(delivery_label_token = next_token) else it
                },
            )
        }
        viewModelScope.launch {
            try {
                val updated = settings_api.update_alias_delivery_label(alias_id, next_token)
                if (!updated) throw IllegalStateException("update_alias_delivery_label failed")
            } catch (_: Throwable) {
                _state.update { s ->
                    s.copy(
                        aliases = s.aliases.map {
                            if (it.id == alias_id) {
                                it.copy(delivery_label_token = current.delivery_label_token)
                            } else it
                        },
                    )
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun run_alias_on_existing(alias_id: String) {
        if (_state.value.alias_details[alias_id]?.apply_busy == true) return
        update_alias_detail(alias_id) { it.copy(apply_busy = true) }
        viewModelScope.launch {
            try {
                val response = settings_api.run_alias_on_existing(alias_id)
                update_alias_detail(alias_id) {
                    it.copy(apply_busy = false, apply_run = response.run ?: it.apply_run)
                }
                start_alias_run_poll(alias_id)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(apply_busy = false) }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_apply_existing_failed),
                )
            }
        }
    }

    fun cancel_alias_run(alias_id: String) {
        if (_state.value.alias_details[alias_id]?.apply_busy == true) return
        update_alias_detail(alias_id) { it.copy(apply_busy = true) }
        viewModelScope.launch {
            try {
                val response = settings_api.cancel_alias_run(alias_id)
                update_alias_detail(alias_id) {
                    it.copy(apply_busy = false, apply_run = response.run ?: it.apply_run)
                }
                start_alias_run_poll(alias_id)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(apply_busy = false) }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_apply_existing_cancel_failed),
                )
                refresh_alias_run(alias_id)
            }
        }
    }

    private fun refresh_alias_run(alias_id: String) {
        viewModelScope.launch {
            val next = try {
                settings_api.get_alias_run(alias_id).run
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                null
            }
            if (next != null) update_alias_detail(alias_id) { it.copy(apply_run = next) }
        }
    }

    private fun start_alias_run_poll(alias_id: String) {
        if (!is_alias_run_active(_state.value.alias_details[alias_id]?.apply_run)) return
        if (alias_run_polls[alias_id]?.isActive == true) return
        alias_run_polls[alias_id] = viewModelScope.launch {
            var delay_ms = ALIAS_RUN_POLL_MIN_MS
            var failures = 0
            while (true) {
                delay(delay_ms)
                val next = try {
                    settings_api.get_alias_run(alias_id).run
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    null
                }
                if (next == null) {
                    failures += 1
                    if (failures >= ALIAS_RUN_POLL_MAX_FAILURES) break
                } else {
                    failures = 0
                    update_alias_detail(alias_id) { it.copy(apply_run = next) }
                    if (!is_alias_run_active(next)) break
                }
                delay_ms = (delay_ms * 3 / 2).coerceAtMost(ALIAS_RUN_POLL_MAX_MS)
            }
            alias_run_polls.remove(alias_id)
        }
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

    fun update_alias_note(alias_id: String, note: String) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val cleaned = note.replace(Regex("[\\x00-\\x08\\x0B-\\x1F\\x7F]"), "").trim()
        _state.update { s ->
            s.copy(aliases = s.aliases.map { if (it.id == alias_id) it.copy(encrypted_note = cleaned.ifBlank { null }) else it })
        }
        viewModelScope.launch {
            try {
                if (cleaned.isBlank()) {
                    settings_api.update_alias_note(alias_id, null, null)
                } else {
                    val (encrypted_note, note_nonce) = encrypt_alias_field(cleaned)
                    settings_api.update_alias_note(alias_id, encrypted_note, note_nonce)
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_note_updated),
                )
            } catch (_: Throwable) {
                _state.update { s ->
                    s.copy(aliases = s.aliases.map { if (it.id == alias_id) it.copy(encrypted_note = current.encrypted_note) else it })
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.something_went_wrong),
                )
            }
        }
    }

    fun toggle_alias_expanded(alias_id: String) {
        val expanded = _state.value.expanded_alias_ids.contains(alias_id)
        set_alias_expanded(alias_id, !expanded)
    }

    fun set_alias_expanded(alias_id: String, expanded: Boolean) {
        _state.update { s ->
            s.copy(
                expanded_alias_ids = if (expanded) {
                    s.expanded_alias_ids + alias_id
                } else {
                    s.expanded_alias_ids - alias_id
                },
            )
        }
        if (expanded) load_alias_detail(alias_id)
    }

    fun load_alias_detail(alias_id: String, force: Boolean = false) {
        val existing = _state.value.alias_details[alias_id]
        if (!force && existing != null && (existing.loaded || existing.loading)) return
        update_alias_detail(alias_id) { it.copy(loading = true) }
        viewModelScope.launch {
            val stats = fetch_alias_section { alias_detail_api.get_stats(alias_id) }
            val pins = fetch_alias_section { alias_detail_api.list_pins(alias_id) }
            val contacts = fetch_alias_section { alias_detail_api.list_contacts(alias_id) }
            val log = fetch_alias_section { alias_detail_api.get_delivery_log(alias_id) }
            val rules = fetch_alias_section { alias_detail_api.list_rules(alias_id) }
            val apply_run = fetch_alias_section { settings_api.get_alias_run(alias_id) }
            val decrypted_pins = withContext(default_dispatcher) {
                pins.value?.pins.orEmpty().map { pin ->
                    DecryptedAliasPin(
                        id = pin.id,
                        sender = decrypt_alias_text(pin.encrypted_sender, pin.sender_nonce)
                            ?: context.getString(R.string.alias_sender_unknown),
                        is_blocked = pin.is_blocked,
                    )
                }
            }
            val decrypted_contacts = withContext(default_dispatcher) {
                contacts.value?.contacts.orEmpty().map { contact ->
                    DecryptedAliasContact(
                        id = contact.id,
                        contact = decrypt_alias_text(contact.encrypted_contact, contact.contact_nonce)
                            ?: context.getString(R.string.alias_contact_unknown),
                        is_blocked = contact.is_blocked,
                    )
                }
            }
            update_alias_detail(alias_id) {
                it.copy(
                    loading = false,
                    loaded = true,
                    stats = stats.value,
                    stats_locked = stats.locked,
                    pin_mode = pins.value?.mode ?: SENDER_PIN_MODE_OFF,
                    pins = decrypted_pins,
                    pins_locked = pins.locked,
                    contacts = decrypted_contacts,
                    contacts_locked = contacts.locked,
                    blocked_events = log.value?.events.orEmpty(),
                    blocked_locked = log.locked,
                    rules = rules.value?.rules.orEmpty(),
                    rules_locked = rules.locked,
                    apply_run = apply_run.value?.run ?: it.apply_run,
                )
            }
            start_alias_run_poll(alias_id)
        }
    }

    private data class AliasSectionResult<T>(val value: T?, val locked: Boolean)

    private suspend fun <T> fetch_alias_section(block: suspend () -> T): AliasSectionResult<T> {
        return try {
            AliasSectionResult(block(), false)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            AliasSectionResult(null, is_feature_locked_error(t))
        }
    }

    private fun is_feature_locked_error(t: Throwable): Boolean =
        t is ApiError.ForbiddenError || t is ApiError.PlanLimitExceeded

    private fun update_alias_detail(alias_id: String, transform: (AliasDetailState) -> AliasDetailState) {
        _state.update { s ->
            val current = s.alias_details[alias_id] ?: AliasDetailState()
            s.copy(alias_details = s.alias_details + (alias_id to transform(current)))
        }
    }

    private fun decrypt_alias_text(ciphertext: String?, nonce: String?): String? {
        if (ciphertext.isNullOrBlank() || nonce.isNullOrBlank()) return null
        return try {
            decrypt_alias_field(ciphertext, nonce)
        } catch (_: Throwable) {
            null
        }
    }

    private fun sha256_base64(text: String): String {
        val normalized = text.lowercase().trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }

    fun update_alias_display_name(alias_id: String, display_name: String) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val cleaned = sanitize_alias_text(display_name)
        _state.update { s ->
            s.copy(
                aliases = s.aliases.map {
                    if (it.id == alias_id) it.copy(encrypted_display_name = cleaned.ifBlank { null }) else it
                },
            )
        }
        viewModelScope.launch {
            try {
                if (cleaned.isBlank()) {
                    settings_api.update_alias_display_name(alias_id, null, null)
                } else {
                    val (encrypted, nonce) = encrypt_alias_field(cleaned)
                    settings_api.update_alias_display_name(alias_id, encrypted, nonce)
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_display_name_updated),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update { s ->
                    s.copy(
                        aliases = s.aliases.map {
                            if (it.id == alias_id) it.copy(encrypted_display_name = current.encrypted_display_name) else it
                        },
                    )
                }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    fun update_alias_websites(alias_id: String, websites: String) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val cleaned = sanitize_alias_text(websites)
        val normalized_list = split_websites_input(cleaned)
        if (cleaned.isNotBlank() && normalized_list.isEmpty()) {
            _state.update { s ->
                s.copy(
                    aliases = s.aliases.map {
                        if (it.id == alias_id) {
                            it.copy(
                                encrypted_websites = current.encrypted_websites,
                                websites = current.websites,
                            )
                        } else {
                            it
                        }
                    },
                    action_result = context.getString(R.string.alias_website_invalid),
                )
            }
            return
        }
        val display_value = normalized_list.joinToString(", ")
        _state.update { s ->
            s.copy(
                aliases = s.aliases.map {
                    if (it.id == alias_id) {
                        it.copy(
                            encrypted_websites = display_value.ifBlank { null },
                            websites = normalized_list,
                        )
                    } else {
                        it
                    }
                },
            )
        }
        viewModelScope.launch {
            try {
                if (normalized_list.isEmpty()) {
                    settings_api.update_alias_websites(alias_id, null, null)
                } else {
                    val (encrypted, nonce) = encrypt_alias_field(serialize_websites_payload(normalized_list))
                    settings_api.update_alias_websites(alias_id, encrypted, nonce)
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_websites_updated),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update { s ->
                    s.copy(
                        aliases = s.aliases.map {
                            if (it.id == alias_id) {
                            it.copy(
                                encrypted_websites = current.encrypted_websites,
                                websites = current.websites,
                            )
                        } else {
                            it
                        }
                        },
                    )
                }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    fun add_alias_website(alias_id: String, website: String) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val normalized = normalize_website_url(sanitize_alias_text(website))
        if (normalized == null) {
            _state.value = _state.value.copy(
                action_result = context.getString(R.string.alias_website_invalid),
            )
            return
        }
        if (current.websites.contains(normalized)) return
        if (current.websites.size >= MAX_ALIAS_WEBSITES) {
            _state.value = _state.value.copy(
                action_result = context.getString(R.string.alias_websites_limit_reached),
            )
            return
        }
        update_alias_websites(alias_id, (current.websites + normalized).joinToString(", "))
    }

    fun remove_alias_website(alias_id: String, website: String) {
        val current = _state.value.aliases.firstOrNull { it.id == alias_id } ?: return
        val remaining = current.websites.filter { it != website }
        if (remaining.size == current.websites.size) return
        update_alias_websites(alias_id, remaining.joinToString(", "))
    }

    private fun normalize_website_url(raw: String): String? {
        val cleaned = raw.replace(Regex("[\\x00-\\x1f\\x7f\\s]"), "")
        if (cleaned.isEmpty()) return null
        val with_scheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(cleaned)) {
            cleaned
        } else {
            "https://$cleaned"
        }
        if (with_scheme.length > MAX_WEBSITE_URL_LENGTH) return null
        return try {
            val uri = java.net.URI(with_scheme)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "https" && scheme != "http") return null
            val host = uri.host
            if (host.isNullOrBlank() || !host.contains(".")) return null
            with_scheme
        } catch (_: Throwable) {
            null
        }
    }

    private fun split_websites_input(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(Regex("[,;\\n]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { normalize_website_url(it) }
            .distinct()
            .take(MAX_ALIAS_WEBSITES)
    }

    private fun serialize_websites_payload(websites: List<String>): String {
        val array = kotlinx.serialization.json.JsonArray(
            websites.map { kotlinx.serialization.json.JsonPrimitive(it) },
        )
        return array.toString()
    }

    private fun websites_payload_to_list(raw: String): List<String> {
        val parsed = try {
            kotlinx.serialization.json.Json.parseToJsonElement(raw)
        } catch (_: Throwable) {
            null
        }
        val entries = if (parsed is kotlinx.serialization.json.JsonArray) {
            parsed.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
        } else {
            raw.split(Regex("[,;\\n]"))
        }
        return entries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { normalize_website_url(it) }
            .distinct()
            .take(MAX_ALIAS_WEBSITES)
    }

    private fun is_valid_email(email: String): Boolean {
        val at = email.indexOf('@')
        if (at <= 0 || at == email.length - 1) return false
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        if (local.isBlank() || domain.isBlank()) return false
        if (email.any { it.isWhitespace() }) return false
        return domain.contains('.')
    }

    private fun sanitize_alias_text(value: String): String =
        value.replace(Regex("[\\x00-\\x08\\x0B-\\x1F\\x7F]"), "").trim().take(500)

    fun set_alias_pin_mode(alias_id: String, mode: Int) {
        val previous = _state.value.alias_details[alias_id]?.pin_mode ?: SENDER_PIN_MODE_OFF
        if (previous == mode) return
        update_alias_detail(alias_id) { it.copy(pin_mode = mode) }
        viewModelScope.launch {
            try {
                alias_detail_api.set_pin_mode(alias_id, mode)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(pin_mode = previous) }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    fun add_alias_pin(alias_id: String, sender: String, is_blocked: Boolean = false) {
        val cleaned = sanitize_alias_text(sender)
        if (!is_valid_email(cleaned)) {
            _state.value = _state.value.copy(
                action_result = context.getString(R.string.alias_pin_invalid_sender),
            )
            return
        }
        update_alias_detail(alias_id) { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val (encrypted, nonce) = encrypt_alias_field(cleaned)
                alias_detail_api.add_pin(
                    alias_id,
                    AddPinRequest(
                        sender_hash = sha256_base64(cleaned),
                        encrypted_sender = encrypted,
                        sender_nonce = nonce,
                        is_blocked = is_blocked,
                    ),
                )
                reload_alias_pins(alias_id)
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_pin_added),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            } finally {
                update_alias_detail(alias_id) { it.copy(busy = false) }
            }
        }
    }

    fun delete_alias_pin(alias_id: String, pin_id: String) {
        val previous = _state.value.alias_details[alias_id]?.pins.orEmpty()
        update_alias_detail(alias_id) { it.copy(pins = it.pins.filterNot { pin -> pin.id == pin_id }) }
        viewModelScope.launch {
            try {
                alias_detail_api.delete_pin(alias_id, pin_id)
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_pin_removed),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(pins = previous) }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    private suspend fun reload_alias_pins(alias_id: String) {
        val result = fetch_alias_section { alias_detail_api.list_pins(alias_id) }
        val decrypted = withContext(default_dispatcher) {
            result.value?.pins.orEmpty().map { pin ->
                DecryptedAliasPin(
                    id = pin.id,
                    sender = decrypt_alias_text(pin.encrypted_sender, pin.sender_nonce)
                        ?: context.getString(R.string.alias_sender_unknown),
                    is_blocked = pin.is_blocked,
                )
            }
        }
        if (result.value != null) {
            update_alias_detail(alias_id) { it.copy(pins = decrypted, pin_mode = result.value.mode) }
        }
    }

    fun add_alias_contact(alias_id: String, contact_email: String) {
        val cleaned = sanitize_alias_text(contact_email)
        if (!is_valid_email(cleaned)) {
            _state.value = _state.value.copy(
                action_result = context.getString(R.string.alias_contact_invalid_email),
            )
            return
        }
        update_alias_detail(alias_id) { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val contact_hash = sha256_base64(cleaned)
                var last_error: Throwable? = null
                var created = false
                for (attempt in 0 until 5) {
                    val reverse_local = generate_ghost_local_part()
                    val (encrypted, nonce) = encrypt_alias_field(cleaned)
                    try {
                        alias_detail_api.create_contact(
                            alias_id,
                            CreateAliasContactRequest(
                                alias_id = alias_id,
                                contact_hash = contact_hash,
                                reverse_alias_hash = sha256_base64("$reverse_local@$GHOST_ALIAS_DOMAIN"),
                                encrypted_contact = encrypted,
                                contact_nonce = nonce,
                            ),
                        )
                        created = true
                        break
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        last_error = t
                        val retryable = (t as? ApiError)?.message.orEmpty()
                            .contains(Regex("in use|already|taken|exists|conflict|duplicate", RegexOption.IGNORE_CASE))
                        if (!retryable) break
                    }
                }
                if (created) {
                    reload_alias_contacts(alias_id)
                    _state.value = _state.value.copy(
                        action_result = context.getString(R.string.alias_contact_added),
                    )
                } else {
                    _state.value = _state.value.copy(
                        action_result = last_error?.let { user_facing_error(it) }
                            ?: context.getString(R.string.something_went_wrong),
                    )
                }
            } finally {
                update_alias_detail(alias_id) { it.copy(busy = false) }
            }
        }
    }

    fun set_alias_contact_blocked(alias_id: String, contact_id: String, is_blocked: Boolean) {
        val previous = _state.value.alias_details[alias_id]?.contacts.orEmpty()
        update_alias_detail(alias_id) { detail ->
            detail.copy(
                contacts = detail.contacts.map {
                    if (it.id == contact_id) it.copy(is_blocked = is_blocked) else it
                },
            )
        }
        viewModelScope.launch {
            try {
                alias_detail_api.set_contact_blocked(alias_id, contact_id, is_blocked)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(contacts = previous) }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    fun delete_alias_contact(alias_id: String, contact_id: String) {
        val previous = _state.value.alias_details[alias_id]?.contacts.orEmpty()
        update_alias_detail(alias_id) { detail ->
            detail.copy(contacts = detail.contacts.filterNot { it.id == contact_id })
        }
        viewModelScope.launch {
            try {
                alias_detail_api.delete_contact(alias_id, contact_id)
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_contact_removed),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(contacts = previous) }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    private suspend fun reload_alias_contacts(alias_id: String) {
        val result = fetch_alias_section { alias_detail_api.list_contacts(alias_id) }
        if (result.value == null) return
        val decrypted = withContext(default_dispatcher) {
            result.value.contacts.map { contact ->
                DecryptedAliasContact(
                    id = contact.id,
                    contact = decrypt_alias_text(contact.encrypted_contact, contact.contact_nonce)
                        ?: context.getString(R.string.alias_contact_unknown),
                    is_blocked = contact.is_blocked,
                )
            }
        }
        update_alias_detail(alias_id) { it.copy(contacts = decrypted) }
    }

    fun set_alias_rule_enabled(alias_id: String, rule_id: String, is_enabled: Boolean) {
        val previous = _state.value.alias_details[alias_id]?.rules.orEmpty()
        update_alias_detail(alias_id) { detail ->
            detail.copy(
                rules = detail.rules.map { if (it.id == rule_id) it.copy(is_enabled = is_enabled) else it },
            )
        }
        viewModelScope.launch {
            try {
                alias_detail_api.update_rule(alias_id, rule_id, UpdateAliasRuleRequest(is_enabled = is_enabled))
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(rules = previous) }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    fun delete_alias_rule(alias_id: String, rule_id: String) {
        val previous = _state.value.alias_details[alias_id]?.rules.orEmpty()
        update_alias_detail(alias_id) { detail ->
            detail.copy(rules = detail.rules.filterNot { it.id == rule_id })
        }
        viewModelScope.launch {
            try {
                alias_detail_api.delete_rule(alias_id, rule_id)
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_rule_removed),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                update_alias_detail(alias_id) { it.copy(rules = previous) }
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    fun create_alias_rule(
        alias_id: String,
        field: String,
        operator: String,
        value: String,
        actions: AliasRuleActions,
    ) {
        val cleaned = sanitize_alias_text(value)
        if (field != "all" && cleaned.isBlank()) {
            _state.value = _state.value.copy(
                action_result = context.getString(R.string.alias_rule_value_required),
            )
            return
        }
        update_alias_detail(alias_id) { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                alias_detail_api.create_rule(
                    alias_id,
                    CreateAliasRuleRequest(
                        conditions = listOf(
                            AliasRuleCondition(field = field, operator = operator, value = cleaned),
                        ),
                        actions = actions,
                    ),
                )
                val result = fetch_alias_section { alias_detail_api.list_rules(alias_id) }
                if (result.value != null) {
                    update_alias_detail(alias_id) { it.copy(rules = result.value.rules) }
                }
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.alias_rule_added),
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            } finally {
                update_alias_detail(alias_id) { it.copy(busy = false) }
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
                    action_result = user_facing_error(t),
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
                    action_result = user_facing_error(t),
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
                    action_result = user_facing_error(t),
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

    data class DomainVerifyOutcome(
        val verified: Boolean,
        val message: String,
        val rate_limited: Boolean = false,
    )

    suspend fun trigger_domain_verification_now(domain_id: String): DomainVerifyOutcome {
        return try {
            val result = settings_api.trigger_domain_verification(domain_id)
            _state.update { s ->
                s.copy(
                    domains = s.domains.map {
                        if (it.id == domain_id) {
                            it.copy(
                                status = result.status.ifBlank { it.status },
                                txt_verified = result.txt_verified,
                                mx_verified = result.mx_verified,
                                spf_verified = result.spf_verified,
                                dkim_verified = result.dkim_verified,
                                dmarc_configured = result.dmarc_configured,
                            )
                        } else {
                            it
                        }
                    },
                )
            }
            if (result.success) {
                DomainVerifyOutcome(true, context.getString(R.string.domain_verify_success))
            } else {
                DomainVerifyOutcome(
                    false,
                    result.message.ifBlank { pending_records_message(result) },
                )
            }
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (t: Throwable) {
            when {
                t is ApiError.RateLimited -> DomainVerifyOutcome(false, rate_limit_message(t), rate_limited = true)
                is_transport_failure(t) -> {
                    load_domains()
                    DomainVerifyOutcome(false, context.getString(R.string.domain_verify_slow))
                }
                else -> DomainVerifyOutcome(false, user_facing_error(t))
            }
        }
    }

    private fun is_transport_failure(t: Throwable): Boolean {
        if (t is ApiError) return t is ApiError.NetworkError || t is ApiError.ServerError
        var cause: Throwable? = t
        while (cause != null) {
            if (cause is java.io.IOException) return true
            if (cause::class.java.name.contains("Timeout", ignoreCase = true)) return true
            cause = cause.cause
        }
        return false
    }

    private fun pending_records_message(
        result: org.astermail.android.api.settings.DomainVerificationResult,
    ): String {
        val pending = buildList {
            if (!result.txt_verified) add("TXT")
            if (!result.mx_verified) add("MX")
            if (!result.spf_verified) add("SPF")
            if (!result.dkim_verified) add("DKIM")
        }
        if (pending.isEmpty()) return context.getString(R.string.domain_verify_pending_generic)
        return context.getString(R.string.domain_verify_pending, pending.joinToString(", "))
    }

    private fun rate_limit_message(error: ApiError.RateLimited): String {
        val minutes = minutes_until(error.resets_at)
        return if (minutes != null && minutes > 0) {
            context.resources.getQuantityString(
                R.plurals.domain_verify_rate_limited_minutes,
                minutes,
                minutes,
            )
        } else {
            context.getString(R.string.domain_verify_rate_limited)
        }
    }

    private fun minutes_until(timestamp: String?): Int? {
        if (timestamp.isNullOrBlank()) return null
        val millis = runCatching { java.time.OffsetDateTime.parse(timestamp).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(timestamp).toEpochMilli() }.getOrNull()
            ?: return null
        val remaining = millis - System.currentTimeMillis()
        if (remaining <= 0) return null
        return ((remaining + 59_999) / 60_000).toInt()
    }

    sealed class AliasAvailability {
        object Available : AliasAvailability()
        object Taken : AliasAvailability()
        data class CheckFailed(val message: String) : AliasAvailability()
    }

    suspend fun check_alias_availability(local_part: String, domain: String): AliasAvailability {
        return try {
            val addr_hash = compute_alias_address_hash(local_part.lowercase(), domain)
            val routing_hash = compute_routing_address_hash(local_part.lowercase(), domain)
            val response = settings_api.check_alias_availability(
                CheckAliasAvailabilityRequest(
                    alias_address_hash = addr_hash,
                    routing_address_hash = routing_hash,
                )
            )
            if (response.available) AliasAvailability.Available else AliasAvailability.Taken
        } catch (t: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("SettingsVM", "check_alias_availability failed for @$domain", t)
            }
            AliasAvailability.CheckFailed(user_facing_error(t))
        }
    }

    fun domain_address_availability(local_part: String, domain_name: String): AliasAvailability {
        val target = "${local_part.trim().lowercase()}@${domain_name.lowercase()}"
        val taken = _state.value.custom_domain_addresses.any {
            !it.decryption_failed && it.address.lowercase() == target
        }
        return if (taken) AliasAvailability.Taken else AliasAvailability.Available
    }

    suspend fun check_directory_availability(key: String, domain: String): Boolean {
        return try {
            val response = settings_api.check_directory_availability(
                DirectoryAvailabilityRequest(
                    directory_hash = compute_directory_address_hash(key, domain),
                    legacy_hash = compute_directory_key_hash(key.lowercase()),
                    domain = domain.lowercase(),
                )
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
            val dir_hash = compute_directory_address_hash(key, domain)
            val (enc_label, label_nonce) = encrypt_alias_field(key.lowercase())
            settings_api.create_directory(
                CreateDirectoryRequest(
                    directory_hash = dir_hash,
                    legacy_hash = compute_directory_key_hash(key.lowercase()),
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
            _state.value = _state.value.copy(action_result = user_facing_error(t))
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
        hydrate_cached_alias_preferences()
        viewModelScope.launch {
            try {
                val prefs = settings_api.get_alias_preferences()
                _state.value = _state.value.copy(alias_preferences = prefs)
                persist_cached_alias_preferences(prefs)
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_alias_preferences", t)
            }
        }
    }

    private fun hydrate_cached_alias_preferences() {
        if (_state.value.alias_preferences != null) return
        val raw = preferences_cache.read_alias_preferences(cache_account_key()) ?: return
        val cached = runCatching {
            cached_preferences_json.decodeFromString(AliasPreferences.serializer(), raw)
        }.getOrNull() ?: return
        _state.value = _state.value.copy(alias_preferences = cached)
    }

    private fun persist_cached_alias_preferences(prefs: AliasPreferences) {
        val key = cache_account_key() ?: return
        val raw = runCatching {
            cached_preferences_json.encodeToString(AliasPreferences.serializer(), prefs)
        }.getOrNull() ?: return
        preferences_cache.write_alias_preferences(key, raw)
    }

    fun update_alias_preference(update: UpdateAliasPreferencesRequest) {
        val previous = _state.value.alias_preferences
        _state.update { it.copy(alias_preferences = it.alias_preferences?.copy(
            alias_default_domain = update.alias_default_domain ?: it.alias_preferences.alias_default_domain,
            alias_sender_format = update.alias_sender_format ?: it.alias_preferences.alias_sender_format,
            readable_reverse_aliases = update.readable_reverse_aliases ?: it.alias_preferences.readable_reverse_aliases,
            alias_always_expand = update.alias_always_expand ?: it.alias_preferences.alias_always_expand,
            alias_unsubscribe_action = update.alias_unsubscribe_action ?: it.alias_preferences.alias_unsubscribe_action,
            alias_disabled_response = update.alias_disabled_response ?: it.alias_preferences.alias_disabled_response,
            alias_delete_action = update.alias_delete_action ?: it.alias_preferences.alias_delete_action,
        ) ?: AliasPreferences(
            alias_default_domain = update.alias_default_domain,
            alias_sender_format = update.alias_sender_format,
            readable_reverse_aliases = update.readable_reverse_aliases,
            alias_always_expand = update.alias_always_expand,
            alias_unsubscribe_action = update.alias_unsubscribe_action,
            alias_disabled_response = update.alias_disabled_response,
            alias_delete_action = update.alias_delete_action,
        )) }
        _state.value.alias_preferences?.let { persist_cached_alias_preferences(it) }
        viewModelScope.launch {
            try {
                settings_api.update_alias_preferences(update)
            } catch (_: Throwable) {
                _state.update { it.copy(alias_preferences = previous) }
                previous?.let { persist_cached_alias_preferences(it) }
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    suspend fun create_alias_now(
        local_part: String,
        domain: String,
        captcha_token: String? = null,
        display_name: String? = null,
        note: String? = null,
    ): Boolean {
        return try {
            val (enc_local, local_nonce) = encrypt_alias_field(local_part.lowercase())
            val addr_hash = compute_alias_address_hash(local_part.lowercase(), domain)
            val routing_hash = compute_routing_address_hash(local_part.lowercase(), domain)
            val name_pair = display_name?.trim()?.takeIf { it.isNotBlank() }?.let { encrypt_alias_field(it) }
            val note_pair = note
                ?.replace(Regex("[\\x00-\\x08\\x0B-\\x1F\\x7F]"), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { encrypt_alias_field(it) }
            settings_api.create_alias(
                CreateAliasRequest(
                    encrypted_local_part = enc_local,
                    local_part_nonce = local_nonce,
                    alias_address_hash = addr_hash,
                    routing_address_hash = routing_hash,
                    domain = domain,
                    encrypted_display_name = name_pair?.first,
                    display_name_nonce = name_pair?.second,
                    encrypted_note = note_pair?.first,
                    note_nonce = note_pair?.second,
                    captcha_token = captcha_token,
                )
            )
            load_aliases(force = true)
            true
        } catch (t: Throwable) {
            _state.value = _state.value.copy(action_result = user_facing_error(t))
            false
        }
    }

    suspend fun create_domain_address_now(local_part: String, domain_id: String, domain_name: String, captcha_token: String? = null, display_name: String? = null): Boolean {
        return try {
            val norm = local_part.trim().lowercase()
            val (enc_local, local_nonce) = encrypt_alias_field(norm)
            val addr_hash = compute_domain_address_hash(norm, domain_name)
            val routing_hash = compute_domain_address_routing_hash(norm, domain_name)
            val name_pair = display_name?.trim()?.takeIf { it.isNotBlank() }?.let { encrypt_alias_field(it) }
            settings_api.create_domain_address(
                domain_id,
                CreateDomainAddressRequest(
                    encrypted_local_part = enc_local,
                    local_part_nonce = local_nonce,
                    local_part_hash = addr_hash,
                    address_routing_hash = routing_hash,
                    encrypted_display_name = name_pair?.first,
                    display_name_nonce = name_pair?.second,
                    captcha_token = captcha_token,
                )
            )
            load_custom_domain_addresses()
            true
        } catch (t: Throwable) {
            _state.value = _state.value.copy(action_result = user_facing_error(t))
            false
        }
    }

    suspend fun import_aliases(
        to_create: List<ImportPreviewRow>,
        to_update: List<ImportPreviewRow>,
        on_progress: (Int) -> Unit,
    ): Pair<Int, Int> {
        var created = 0
        var failed = 0
        var processed = 0

        val system_rows = to_create.filter { SYSTEM_ALIAS_DOMAINS.contains(it.domain) }
        val custom_rows = to_create.filterNot { SYSTEM_ALIAS_DOMAINS.contains(it.domain) }

        if (system_rows.isNotEmpty()) {
            val items = system_rows.map { row ->
                val normalized = row.local_part.lowercase().trim()
                val (enc_local, local_nonce) = encrypt_alias_field(normalized)
                val name_pair = row.display_name?.takeIf { it.isNotBlank() }?.let { encrypt_alias_field(it) }
                BulkCreateAliasItem(
                    encrypted_local_part = enc_local,
                    local_part_nonce = local_nonce,
                    alias_address_hash = compute_alias_address_hash(normalized, row.domain),
                    routing_address_hash = compute_routing_address_hash(normalized, row.domain),
                    domain = row.domain,
                    encrypted_display_name = name_pair?.first,
                    display_name_nonce = name_pair?.second,
                    is_enabled = row.enabled,
                )
            }
            items.chunked(IMPORT_BATCH_SIZE).forEach { batch ->
                try {
                    val response = settings_api.bulk_create_aliases(BulkCreateAliasRequest(batch))
                    created += response.created
                    failed += response.failed
                } catch (_: Throwable) {
                    failed += batch.size
                }
                processed += batch.size
                on_progress(processed)
            }
        }

        custom_rows.groupBy { it.domain }.forEach { (domain_name, rows) ->
            val domain_id = resolve_domain_id(domain_name)
            if (domain_id == null) {
                failed += rows.size
                processed += rows.size
                on_progress(processed)
                return@forEach
            }
            val items = rows.map { row ->
                val normalized = row.local_part.lowercase().trim()
                val (enc_local, local_nonce) = encrypt_alias_field(normalized)
                val name_pair = row.display_name?.takeIf { it.isNotBlank() }?.let { encrypt_alias_field(it) }
                BulkAddAddressItem(
                    encrypted_local_part = enc_local,
                    local_part_nonce = local_nonce,
                    local_part_hash = compute_domain_address_hash(normalized, domain_name),
                    address_routing_hash = compute_domain_address_routing_hash(normalized, domain_name),
                    encrypted_display_name = name_pair?.first,
                    display_name_nonce = name_pair?.second,
                    is_enabled = row.enabled,
                )
            }
            items.chunked(IMPORT_BATCH_SIZE).forEach { batch ->
                try {
                    val response = settings_api.bulk_add_domain_addresses(
                        domain_id,
                        BulkAddAddressesRequest(batch),
                    )
                    created += response.created
                    failed += response.failed
                } catch (_: Throwable) {
                    failed += batch.size
                }
                processed += batch.size
                on_progress(processed)
            }
        }

        to_update.forEach { row ->
            val existing_id = row.existing_id
            if (existing_id == null) {
                failed += 1
            } else {
                val enabled = row.enabled ?: true
                val existing_domain_id = row.existing_domain_id
                val name_pair = row.display_name
                    ?.takeIf { it.isNotBlank() }
                    ?.let { encrypt_alias_field(it) }

                suspend fun send_update(with_name: Boolean) {
                    if (existing_domain_id != null) {
                        settings_api.update_domain_address(
                            existing_domain_id,
                            existing_id,
                            UpdateDomainAddressRequest(
                                is_enabled = enabled,
                                encrypted_display_name = name_pair?.first?.takeIf { with_name },
                                display_name_nonce = name_pair?.second?.takeIf { with_name },
                            ),
                        )
                    } else {
                        settings_api.update_alias(
                            existing_id,
                            UpdateAliasRequest(
                                is_enabled = enabled,
                                encrypted_display_name = name_pair?.first?.takeIf { with_name },
                                display_name_nonce = name_pair?.second?.takeIf { with_name },
                            ),
                        )
                    }
                }

                try {
                    send_update(with_name = true)
                    created += 1
                } catch (_: Throwable) {
                    if (name_pair == null) {
                        failed += 1
                    } else {
                        try {
                            send_update(with_name = false)
                            created += 1
                        } catch (_: Throwable) {
                            failed += 1
                        }
                    }
                }
            }
            processed += 1
            on_progress(processed)
        }

        load_aliases(force = true)
        load_custom_domain_addresses()

        return created to failed
    }

    private fun resolve_domain_id(domain_name: String): String? =
        _state.value.domains.firstOrNull { it.domain_name.equals(domain_name, ignoreCase = true) }?.id

    fun toggle_domain_address(address_id: String, domain_name: String) {
        val domain_id = resolve_domain_id(domain_name) ?: return
        val current = _state.value.custom_domain_addresses.firstOrNull { it.id == address_id } ?: return
        val new_val = !current.is_enabled
        _state.update { s -> s.copy(custom_domain_addresses = s.custom_domain_addresses.map { if (it.id == address_id) it.copy(is_enabled = new_val) else it }) }
        viewModelScope.launch {
            try {
                settings_api.update_domain_address(domain_id, address_id, UpdateDomainAddressRequest(is_enabled = new_val))
            } catch (t: Throwable) {
                _state.update { s ->
                    s.copy(
                        custom_domain_addresses = s.custom_domain_addresses.map { if (it.id == address_id) it.copy(is_enabled = current.is_enabled) else it },
                        action_result = user_facing_error(t),
                    )
                }
            }
        }
    }

    fun delete_domain_address(address_id: String, domain_name: String) {
        val domain_id = resolve_domain_id(domain_name) ?: return
        viewModelScope.launch {
            try {
                settings_api.delete_domain_address(domain_id, address_id)
                _state.update { s -> s.copy(custom_domain_addresses = s.custom_domain_addresses.filter { it.id != address_id }) }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(action_result = user_facing_error(t))
            }
        }
    }

    fun add_domain_now(
        domain_name: String,
        captcha_token: String? = null,
        on_done: (CustomDomain?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val domain = settings_api.add_domain(AddDomainRequest(domain_name = domain_name, captcha_token = captcha_token))
                _state.update { s -> s.copy(domains = s.domains + domain) }
                on_done(domain, null)
            } catch (t: Throwable) {
                on_done(null, add_domain_error(t))
            }
        }
    }

    private fun add_domain_error(t: Throwable): String = when (t) {
        is ApiError.Conflict -> context.getString(R.string.domain_add_conflict)
        is ApiError.ValidationError -> t.messages.firstOrNull { it.isNotBlank() }
            ?: context.getString(R.string.domain_add_invalid)
        is ApiError.PlanLimitExceeded -> t.detail.ifBlank { context.getString(R.string.domain_add_plan_limit) }
        is ApiError.RateLimited -> rate_limit_message(t)
        else -> user_facing_error(t)
    }

    fun load_storage(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && _state.value.storage != null && now - last_storage_load_ms < LIST_TTL_MS) return
        last_storage_load_ms = now
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val overview = settings_api.get_storage_overview()
                _state.value = _state.value.copy(storage = overview, is_loading = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = user_facing_error(t),
                )
            }
        }
    }

    fun load_subscription(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && _state.value.subscription != null && now - last_subscription_load_ms < SUBSCRIPTION_TTL_MS) return
        last_subscription_load_ms = now
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
                    error = user_facing_error(t),
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
                auth_repository.handle_unauthorized_signal(force = true)
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
                auth_repository.handle_unauthorized_signal(force = true)
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
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
        }
    }

    fun disable_vanguard() {
        viewModelScope.launch {
            try {
                val v = security_api.disable_vanguard()
                _state.update { it.copy(vanguard_enabled = v.enabled) }
            } catch (_: Throwable) {
                _state.value = _state.value.copy(action_result = context.getString(R.string.something_went_wrong))
            }
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
        val current = _state.value.preferences ?: return
        val new_value = !current.publish_to_keyservers
        save_preferences(current.copy(publish_to_keyservers = new_value))
        if (!new_value) return
        viewModelScope.launch {
            val failed = try {
                encryption_api.publish_to_keyserver()
                false
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "publish_to_keyserver", t)
                true
            }
            if (failed) {
                save_preferences_job?.join()
                val latest = _state.value.preferences ?: return@launch
                save_preferences(latest.copy(publish_to_keyservers = false))
                _state.update { it.copy(action_result = context.getString(R.string.keyserver_publish_failed)) }
                return@launch
            }
            try {
                val ks = encryption_api.get_keyserver_status()
                _state.update { it.copy(keyserver_status = ks) }
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "keyserver status refresh", t)
            }
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
                val is_set = response.exists ?: (!enc.isNullOrBlank() && !nonce.isNullOrBlank())
                _state.value = _state.value.copy(
                    recovery_email_address = address,
                    recovery_email_set = is_set,
                    recovery_email_verified = response.verified,
                    recovery_email_step_up_required =
                        response.step_up_required ?: response.verified,
                    security_status = _state.value.security_status?.copy(
                        recovery_email_set = is_set,
                        recovery_email_verified = response.verified,
                    ),
                    is_loading = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = user_facing_error(t),
                )
            }
        }
    }

    fun save_recovery_email(email: String, password: String?, totp_code: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING, error = null)
            val normalized = email.trim().lowercase()
            try {
                val password_hash = password?.takeIf { it.isNotBlank() }?.let {
                    auth_repository.derive_password_hash_b64(it)
                        ?: throw IllegalStateException(
                            context.getString(R.string.something_went_wrong),
                        )
                }
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
                        password_hash = password_hash,
                        totp_code = totp_code?.ifBlank { null },
                    ),
                )
                _state.value = _state.value.copy(
                    recovery_email_address = normalized,
                    recovery_email_set = true,
                    recovery_email_verified = false,
                    recovery_email_step_up_required = false,
                    security_status = _state.value.security_status?.copy(
                        recovery_email_set = true,
                        recovery_email_verified = false,
                    ),
                    save_status = SaveStatus.SAVED,
                )
            } catch (t: Throwable) {
                val needs_step_up = (t as? RecoveryEmailError)?.code ==
                    RecoveryEmailApiImpl.STEP_UP_REQUIRED
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    recovery_email_step_up_required =
                        needs_step_up || _state.value.recovery_email_step_up_required,
                    error = if (needs_step_up) null else recovery_email_error_message(t),
                )
            }
        }
    }

    fun resend_recovery_verification() {
        viewModelScope.launch {
            val email = _state.value.recovery_email_address?.takeIf { it.isNotBlank() }
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

    fun remove_recovery_email(password: String, totp_code: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING, error = null)
            try {
                val password_hash = auth_repository.derive_password_hash_b64(password)
                    ?: throw IllegalStateException(context.getString(R.string.something_went_wrong))
                recovery_email_api.remove(
                    RemoveRecoveryEmailRequest(
                        password_hash = password_hash,
                        totp_code = totp_code?.ifBlank { null },
                    ),
                )
                _state.value = _state.value.copy(
                    recovery_email_address = null,
                    recovery_email_set = false,
                    recovery_email_verified = false,
                    recovery_email_step_up_required = false,
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
        (t as? RecoveryEmailError)?.let { error ->
            return when (error.code) {
                RecoveryEmailApiImpl.STEP_UP_REQUIRED ->
                    error.user_message ?: context.getString(R.string.recovery_step_up_description)
                RecoveryEmailApiImpl.TOTP_REQUIRED ->
                    error.user_message ?: context.getString(R.string.totp_code_required_error)
                else -> error.user_message ?: user_facing_error(t)
            }
        }
        val detail = (t as? ApiError.UnknownError)?.detail
        return when (detail) {
            RecoveryEmailApiImpl.RECOVERY_EMAIL_IN_USE ->
                context.getString(R.string.recovery_email_already_in_use)
            RecoveryEmailApiImpl.RECOVERY_EMAIL_COOLDOWN ->
                context.getString(R.string.recovery_email_resend_cooldown)
            else -> user_facing_error(t)
        }
    }

    private fun derive_recovery_email_key(identity_key: String): ByteArray =
        org.astermail.android.recovery.derive_recovery_email_key(identity_key)

    private fun encrypt_recovery_email(email: String, identity_key: String): EncryptedField {
        val encrypted = org.astermail.android.recovery.encrypt_recovery_email(email, identity_key)
        return EncryptedField(
            ciphertext_b64 = encrypted.ciphertext_b64,
            nonce_b64 = encrypted.nonce_b64,
        )
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

    private fun hash_recovery_email(email: String): String =
        org.astermail.android.recovery.hash_recovery_email(email)

    suspend fun send_feedback(category: String, message: String) {
        settings_api.send_feedback(FeedbackRequest(category = category, message = message))
    }

    fun get_recovery_codes(): List<String>? {
        return session_key_store.get_recovery_codes()
    }

    private fun user_facing_error(t: Throwable): String =
        org.astermail.android.api.user_facing_error(t, context.getString(R.string.something_went_wrong))

    fun load_labels(folder_type: String? = null, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val labels_key = folder_type ?: "all"
        if (!force && last_labels_load_ms[labels_key]?.let { now - it < LIST_TTL_MS } == true) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val response = labels_api.list_labels(include_counts = true, folder_type = folder_type)
                last_labels_load_ms[labels_key] = System.currentTimeMillis()
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
                org.astermail.android.folders.folder_lock_store.set_folders(_state.value.labels)
                org.astermail.android.notifications.MailPollingWorker.set_protected_folder_tokens(
                    context,
                    _state.value.labels.filter { org.astermail.android.folders.is_folder_protected(it) }
                        .map { it.label_token },
                )
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("SettingsVM", "load_labels failed", t)
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = user_facing_error(t),
                )
            }
        }
    }

    fun create_label(request: CreateLabelRequest) {
        viewModelScope.launch {
            try {
                val response = labels_api.create_label(request)
                if (response.success) {
                    load_labels(force = true)
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    action_result = user_facing_error(t),
                )
            }
        }
    }

    fun delete_label(
        label_id: String,
        password: String? = null,
        totp_code: String? = null,
        purge_contents: Boolean = false,
        on_result: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                val request = if (password.isNullOrBlank() && !purge_contents) {
                    null
                } else {
                    val password_hash = password?.takeIf { it.isNotBlank() }?.let {
                        auth_repository.derive_password_hash_b64(it)
                            ?: throw IllegalStateException(context.getString(R.string.something_went_wrong))
                    }
                    org.astermail.android.api.labels.DeleteLabelRequest(
                        password_hash = password_hash,
                        totp_code = totp_code?.trim()?.ifBlank { null },
                        purge_contents = purge_contents,
                    )
                }
                labels_api.delete_label(label_id, request)
                org.astermail.android.folders.folder_lock_store.lock(label_id)
                _state.value = _state.value.copy(
                    labels = _state.value.labels.filter { it.id != label_id },
                    action_result = context.getString(R.string.item_deleted),
                )
                on_result?.invoke(true)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    action_result = user_facing_error(t),
                )
                on_result?.invoke(false)
            }
        }
    }

    fun unlock_folder(
        label_id: String,
        password: String,
        on_result: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val ok = try {
                val salt = _state.value.labels.firstOrNull { it.id == label_id }?.password_salt
                    ?: labels_api.get_label(label_id).password_salt
                if (salt.isNullOrBlank()) {
                    false
                } else {
                    val password_hash = withContext(Dispatchers.Default) {
                        org.astermail.android.folders.derive_folder_auth_hash(password, salt)
                    }
                    val response = labels_api.verify_folder_password(
                        label_id,
                        VerifyFolderPasswordRequest(password_hash = password_hash),
                    )
                    if (response.verified) {
                        org.astermail.android.folders.folder_lock_store.mark_unlocked(
                            folder_id = label_id,
                            unlock_token = response.unlock_token,
                            unlock_expires_at = response.unlock_expires_at,
                            encrypted_folder_key = response.encrypted_folder_key,
                            folder_key_nonce = response.folder_key_nonce,
                        )
                        _state.value = _state.value.copy(
                            action_result = context.getString(R.string.folder_unlocked),
                        )
                    }
                    response.verified
                }
            } catch (_: Throwable) {
                false
            }
            on_result(ok)
        }
    }

    fun lock_folder(label_id: String) {
        org.astermail.android.folders.folder_lock_store.lock(label_id)
        _state.value = _state.value.copy(
            action_result = context.getString(R.string.folder_locked),
        )
    }

    fun toggle_folder_notifications(label_token: String) {
        if (label_token.isBlank()) return
        val base = _state.value.preferences ?: UserPreferences()
        val muted = base.muted_folder_tokens
        val is_muting = label_token !in muted
        val next = if (is_muting) muted + label_token else muted - label_token
        org.astermail.android.notifications.MailPollingWorker.set_muted_folder_tokens(context, next)
        save_preferences(base.copy(muted_folder_tokens = next))
        _state.value = _state.value.copy(
            action_result = context.getString(
                if (is_muting) R.string.folder_notifications_muted else R.string.folder_notifications_unmuted,
            ),
        )
    }

    fun load_tags(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && last_tags_load_ms > 0L && now - last_tags_load_ms < TAGS_TTL_MS) return
        last_tags_load_ms = now
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
                    error = user_facing_error(t),
                )
            }
        }
    }

    fun create_tag(
        name: String,
        color: String? = null,
        icon: String? = null,
        on_created: ((String) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                val identity_key = session_key_store.get_identity_key() ?: return@launch
                val name_field = encrypt_field_with_version(name, identity_key, TAG_VERSION_CURRENT)
                val color_field = color?.let { encrypt_field_with_version(it, identity_key, TAG_VERSION_CURRENT) }
                val icon_field = icon?.let { encrypt_field_with_version(it, identity_key, TAG_VERSION_CURRENT) }
                val tag_token = generate_token_b64()
                tags_api.create_tag(
                    CreateTagRequest(
                        tag_token = tag_token,
                        encrypted_name = name_field.ciphertext_b64,
                        name_nonce = name_field.nonce_b64,
                        encrypted_color = color_field?.ciphertext_b64,
                        color_nonce = color_field?.nonce_b64,
                        encrypted_icon = icon_field?.ciphertext_b64,
                        icon_nonce = icon_field?.nonce_b64,
                    ),
                )
                load_tags()
                on_created?.invoke(tag_token)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_create_tag),
                )
            }
        }
    }

    fun create_folder(
        name: String,
        color: String? = null,
        sort_order: Int? = null,
        parent_token: String? = null,
        on_created: ((String) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                val identity_key = session_key_store.get_identity_key() ?: run {
                    _state.value = _state.value.copy(action_result = context.getString(R.string.failed_create_folder))
                    return@launch
                }
                val token = generate_token_b64()
                val name_field = encrypt_field_with_version(name, identity_key, FOLDER_VERSION_CURRENT)
                val color_field = color?.let { encrypt_field_with_version(it, identity_key, FOLDER_VERSION_CURRENT) }
                val created = labels_api.create_label(
                    CreateLabelRequest(
                        label_token = token,
                        encrypted_name = name_field.ciphertext_b64,
                        name_nonce = name_field.nonce_b64,
                        encrypted_color = color_field?.ciphertext_b64,
                        color_nonce = color_field?.nonce_b64,
                        folder_type = "folder",
                        sort_order = sort_order,
                        parent_token = parent_token,
                    ),
                )
                val optimistic = LabelItem(
                    id = created.id?.takeIf { it.isNotBlank() } ?: token,
                    label_token = token,
                    encrypted_name = name,
                    encrypted_color = color,
                    folder_type = "folder",
                    sort_order = sort_order ?: 0,
                    parent_token = parent_token,
                    item_count = 0,
                )
                optimistic_label_tokens.add(token)
                _state.value = _state.value.copy(
                    labels = _state.value.labels + optimistic,
                    action_result = context.getString(R.string.folder_created),
                )
                on_created?.invoke(token)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_create_folder),
                )
            }
        }
    }

    fun rename_folder(label_id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val previous = _state.value.labels
            _state.value = _state.value.copy(
                labels = previous.map { if (it.id == label_id) it.copy(encrypted_name = trimmed) else it },
            )
            try {
                val identity_key = session_key_store.get_identity_key() ?: throw IllegalStateException("no identity key")
                val name_field = encrypt_field_with_version(trimmed, identity_key, FOLDER_VERSION_CURRENT)
                labels_api.update_label(
                    label_id,
                    UpdateLabelRequest(
                        encrypted_name = name_field.ciphertext_b64,
                        name_nonce = name_field.nonce_b64,
                    ),
                )
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.item_renamed),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    labels = previous,
                    action_result = context.getString(R.string.failed_update_folder),
                )
            }
        }
    }

    fun recolor_folder(label_id: String, color: String) {
        viewModelScope.launch {
            val previous = _state.value.labels
            _state.value = _state.value.copy(
                labels = previous.map { if (it.id == label_id) it.copy(encrypted_color = color) else it },
            )
            try {
                val identity_key = session_key_store.get_identity_key() ?: throw IllegalStateException("no identity key")
                val color_field = encrypt_field_with_version(color, identity_key, FOLDER_VERSION_CURRENT)
                labels_api.update_label(
                    label_id,
                    UpdateLabelRequest(
                        encrypted_color = color_field.ciphertext_b64,
                        color_nonce = color_field.nonce_b64,
                    ),
                )
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.item_color_updated),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    labels = previous,
                    action_result = context.getString(R.string.failed_update_folder),
                )
            }
        }
    }

    fun set_folder_parent(label_id: String, parent_token: String?) {
        viewModelScope.launch {
            val previous = _state.value.labels
            _state.value = _state.value.copy(
                labels = previous.map { if (it.id == label_id) it.copy(parent_token = parent_token) else it },
            )
            try {
                labels_api.update_label(
                    label_id,
                    UpdateLabelRequest(parent_token = parent_token.orEmpty()),
                )
                load_labels(force = true)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    labels = previous,
                    action_result = context.getString(R.string.failed_update_folder),
                )
            }
        }
    }

    fun set_folder_lock(label_id: String, password: String, on_result: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                val material = withContext(Dispatchers.Default) {
                    org.astermail.android.folders.prepare_folder_password(password)
                }
                labels_api.set_folder_password(
                    label_id,
                    SetFolderPasswordRequest(
                        password_hash = material.password_hash,
                        password_salt = material.password_salt,
                        encrypted_folder_key = material.encrypted_folder_key,
                        folder_key_nonce = material.folder_key_nonce,
                    ),
                )
                true
            } catch (_: Throwable) {
                false
            }
            if (ok) {
                org.astermail.android.folders.folder_lock_store.mark_unlocked(label_id)
                load_labels(force = true)
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.folder_lock_set),
                )
            } else {
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.failed_update_folder),
                )
            }
            on_result(ok)
        }
    }

    fun remove_folder_lock(label_id: String, password: String, on_result: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                val salt = _state.value.labels.firstOrNull { it.id == label_id }?.password_salt
                    ?: labels_api.get_label(label_id).password_salt
                if (salt.isNullOrBlank()) {
                    false
                } else {
                    val password_hash = withContext(Dispatchers.Default) {
                        org.astermail.android.folders.derive_folder_auth_hash(password, salt)
                    }
                    labels_api.remove_folder_password(
                        label_id,
                        RemoveFolderPasswordRequest(password_hash = password_hash),
                    )
                    true
                }
            } catch (_: Throwable) {
                false
            }
            if (ok) {
                org.astermail.android.folders.folder_lock_store.mark_unlocked(label_id)
                load_labels(force = true)
                _state.value = _state.value.copy(
                    action_result = context.getString(R.string.folder_lock_removed),
                )
            }
            on_result(ok)
        }
    }

    fun move_folder(label_id: String, direction: Int) {
        viewModelScope.launch {
            val siblings = folder_sibling_group(_state.value.labels, label_id)
            val index = siblings.indexOfFirst { it.id == label_id }
            val target = index + direction
            if (index < 0 || target < 0 || target > siblings.lastIndex) return@launch
            val reordered = siblings.toMutableList().apply {
                add(target, removeAt(index))
            }
            val new_orders = reordered.mapIndexed { i, label -> label.id to i }.toMap()
            val previous_labels = _state.value.labels
            _state.value = _state.value.copy(
                labels = previous_labels.map { label ->
                    new_orders[label.id]?.let { label.copy(sort_order = it) } ?: label
                },
            )
            val changed = reordered.mapIndexedNotNull { i, label ->
                if (label.sort_order != i) ReorderLabelEntry(id = label.id, sort_order = i) else null
            }
            if (changed.isEmpty()) return@launch
            try {
                labels_api.bulk_reorder_labels(BulkReorderLabelsRequest(labels = changed))
            } catch (_: Throwable) {
                _state.value = _state.value.copy(labels = previous_labels)
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

    fun rename_tag(tag_id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val previous = _state.value.tags
            _state.value = _state.value.copy(
                tags = previous.map { if (it.id == tag_id) it.copy(encrypted_name = trimmed) else it },
            )
            try {
                val identity_key = session_key_store.get_identity_key() ?: throw IllegalStateException("no identity key")
                val name_field = encrypt_field_with_version(trimmed, identity_key, TAG_VERSION_CURRENT)
                tags_api.update_tag(
                    tag_id,
                    UpdateTagRequest(
                        encrypted_name = name_field.ciphertext_b64,
                        name_nonce = name_field.nonce_b64,
                    ),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    tags = previous,
                    action_result = context.getString(R.string.failed_update_label),
                )
            }
        }
    }

    fun recolor_tag(tag_id: String, color: String) {
        viewModelScope.launch {
            val previous = _state.value.tags
            _state.value = _state.value.copy(
                tags = previous.map { if (it.id == tag_id) it.copy(encrypted_color = color) else it },
            )
            try {
                val identity_key = session_key_store.get_identity_key() ?: throw IllegalStateException("no identity key")
                val color_field = encrypt_field_with_version(color, identity_key, TAG_VERSION_CURRENT)
                tags_api.update_tag(
                    tag_id,
                    UpdateTagRequest(
                        encrypted_color = color_field.ciphertext_b64,
                        color_nonce = color_field.nonce_b64,
                    ),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    tags = previous,
                    action_result = context.getString(R.string.failed_update_label),
                )
            }
        }
    }

    fun set_tag_icon(tag_id: String, icon: String?) {
        viewModelScope.launch {
            val previous = _state.value.tags
            _state.value = _state.value.copy(
                tags = previous.map { if (it.id == tag_id) it.copy(encrypted_icon = icon) else it },
            )
            try {
                val identity_key = session_key_store.get_identity_key() ?: throw IllegalStateException("no identity key")
                val icon_field = encrypt_field_with_version(icon.orEmpty(), identity_key, TAG_VERSION_CURRENT)
                tags_api.update_tag(
                    tag_id,
                    UpdateTagRequest(
                        encrypted_icon = icon_field.ciphertext_b64,
                        icon_nonce = icon_field.nonce_b64,
                    ),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    tags = previous,
                    action_result = context.getString(R.string.failed_update_label),
                )
            }
        }
    }

    fun move_tag(tag_id: String, direction: Int) {
        viewModelScope.launch {
            val current = _state.value.tags
            val rows = org.astermail.android.labels.tag_rows(current)
            val index = rows.indexOfFirst { it.id == tag_id }
            val reordered = org.astermail.android.labels.move_row(rows, index, direction) ?: return@launch
            val changed = org.astermail.android.labels.tag_reorder_entries(reordered)
            if (changed.isEmpty()) return@launch
            val positions = reordered.withIndex().associate { (position, tag) -> tag.id to position }
            _state.value = _state.value.copy(
                tags = current.map { tag -> positions[tag.id]?.let { tag.copy(sort_order = it) } ?: tag },
            )
            try {
                tags_api.bulk_reorder_tags(
                    org.astermail.android.api.tags.BulkReorderTagsRequest(tags = changed),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    tags = current,
                    action_result = context.getString(R.string.failed_update_label),
                )
            }
        }
    }

    fun set_label_icon(label_id: String, icon: String?) {
        viewModelScope.launch {
            val previous = _state.value.labels
            _state.value = _state.value.copy(
                labels = previous.map { if (it.id == label_id) it.copy(encrypted_icon = icon) else it },
            )
            try {
                val identity_key = session_key_store.get_identity_key() ?: throw IllegalStateException("no identity key")
                val icon_field = encrypt_field_with_version(icon.orEmpty(), identity_key, FOLDER_VERSION_CURRENT)
                labels_api.update_label(
                    label_id,
                    UpdateLabelRequest(
                        encrypted_icon = icon_field.ciphertext_b64,
                        icon_nonce = icon_field.nonce_b64,
                    ),
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    labels = previous,
                    action_result = context.getString(R.string.failed_update_label),
                )
            }
        }
    }

    fun move_label_row(label_id: String, direction: Int) {
        viewModelScope.launch {
            val all = _state.value.labels
            val rows = org.astermail.android.labels.label_rows(all)
            val index = rows.indexOfFirst { it.id == label_id }
            val reordered = org.astermail.android.labels.move_row(rows, index, direction) ?: return@launch
            val changed = org.astermail.android.labels.label_reorder_entries(reordered)
            if (changed.isEmpty()) return@launch
            val positions = reordered.withIndex().associate { (position, label) -> label.id to position }
            _state.value = _state.value.copy(
                labels = all.map { label -> positions[label.id]?.let { label.copy(sort_order = it) } ?: label },
            )
            try {
                labels_api.bulk_reorder_labels(BulkReorderLabelsRequest(labels = changed))
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    labels = all,
                    action_result = context.getString(R.string.failed_update_label),
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
            val history = try {
                kotlinx.coroutines.withTimeout(10_000L) {
                    labels_api.get_referral_history().referrals
                }
            } catch (_: Throwable) {
                emptyList()
            }
            _state.value = _state.value.copy(referral = info, referral_history = history)
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

    fun load_preferences(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force &&
            _state.value.preferences != null &&
            last_preferences_load_ms != 0L &&
            now - last_preferences_load_ms < PREFERENCES_TTL_MS
        ) {
            return
        }
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
                        last_preferences_load_ms = System.currentTimeMillis()
                        last_preferences_load_ms = System.currentTimeMillis()
                        last_synced_preferences = decrypted
                        persist_cached_preferences(decrypted)
                        apply_preferences_to_theme_store(decrypted)
                        org.astermail.android.notifications.MailPollingWorker
                            .set_muted_folder_tokens(context, decrypted.muted_folder_tokens)
                        _state.value = _state.value.copy(
                            preferences = decrypted,
                            preferences_authoritative = true,
                            is_loading = false,
                        )
                    } else {
                        prefs_load_succeeded = true
                        last_preferences_load_ms = System.currentTimeMillis()
                        last_preferences_load_ms = System.currentTimeMillis()
                        val fallback = _state.value.preferences
                        _state.value = _state.value.copy(
                            preferences = fallback ?: UserPreferences(),
                            preferences_authoritative = fallback != null,
                            is_loading = false,
                            error = null,
                        )
                    }
                } else {
                    val prefs = load_plaintext_preferences()
                    prefs_load_succeeded = true
                    last_preferences_load_ms = System.currentTimeMillis()
                    last_synced_preferences = prefs
                    persist_cached_preferences(prefs)
                    apply_preferences_to_theme_store(prefs)
                    org.astermail.android.notifications.MailPollingWorker
                        .set_muted_folder_tokens(context, prefs.muted_folder_tokens)
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
                    error = user_facing_error(t),
                )
            }
        }
    }

    private fun apply_signature_defaults(decrypted: List<DecryptedSignature>) {
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
    }

    private fun hydrate_cached_signatures(): Boolean {
        val raw = preferences_cache.read_signatures(cache_account_key()) ?: return false
        val cached = runCatching {
            cached_preferences_json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(DecryptedSignature.serializer()),
                raw,
            )
        }.getOrNull() ?: return false
        apply_signature_defaults(cached)
        _signature_loaded.value = true
        return true
    }

    private fun persist_cached_signatures(decrypted: List<DecryptedSignature>) {
        val key = cache_account_key() ?: return
        val raw = runCatching {
            cached_preferences_json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(DecryptedSignature.serializer()),
                decrypted,
            )
        }.getOrNull() ?: return
        preferences_cache.write_signatures(key, raw)
    }

    private fun apply_saved_default_signature(content: String) {
        val target_id = default_signature_id ?: return
        val updated = apply_default_signature_content(
            current = _signatures.value,
            target_id = target_id,
            content = content,
            default_name = context.getString(org.astermail.android.R.string.default_signature_name),
            is_html = default_signature_is_html,
        )
        _signatures.value = updated
        persist_cached_signatures(updated)
    }

    fun ensure_signatures_hydrated(): Boolean {
        if (_signatures.value.isNotEmpty()) return true
        return hydrate_cached_signatures()
    }

    fun load_signature() {
        ensure_signatures_hydrated()
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
                apply_signature_defaults(decrypted)
                persist_cached_signatures(decrypted)
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
                    error = user_facing_error(t),
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
                signatures_api.update_signature(
                    id,
                    org.astermail.android.api.signatures.UpdateSignatureRequest(
                        encrypted_name = name_enc?.ciphertext_b64,
                        name_nonce = name_enc?.nonce_b64,
                        encrypted_content = content_enc?.ciphertext_b64,
                        content_nonce = content_enc?.nonce_b64,
                        is_html = is_html,
                        alias_id = org.astermail.android.api.signatures.signature_alias_field(alias_id, clear_alias),
                        placement = org.astermail.android.api.signatures.signature_placement_field(placement, placement == null),
                    ),
                )
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_signature()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = user_facing_error(t),
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
                    error = user_facing_error(t),
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
                    error = user_facing_error(t),
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
                apply_saved_default_signature(content)
                _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                load_signature()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = user_facing_error(t),
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
        } catch (t: Throwable) {
            val fallback = derive_passphrase_key()
            try {
                return String(aes_gcm_decrypt(ciphertext, fallback, nonce), Charsets.UTF_8)
            } finally {
                fallback.fill(0)
            }
        } finally {
            key.fill(0)
        }
    }

    private fun encrypt_signature_field(plaintext: String): EncryptedField {
        val data = plaintext.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive_encryption_key()
        try {
            val ct = AesGcm.encrypt(key, nonce, data)
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
        persist_cached_preferences(prefs)
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
                    persist_cached_preferences(to_save)
                    org.astermail.android.notifications.MailPollingWorker
                        .set_muted_folder_tokens(context, to_save.muted_folder_tokens)
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
                    persist_cached_preferences(prefs)
                    _state.value = _state.value.copy(save_status = SaveStatus.SAVED)
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = user_facing_error(t),
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
                    error = user_facing_error(t),
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
            val domain = GHOST_ALIAS_DOMAIN
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
            if (t is kotlinx.coroutines.CancellationException) throw t
            val message = when (t) {
                is ApiError.RateLimited, is ApiError.PlanLimitExceeded ->
                    context.getString(R.string.ghost_alias_limit_reached)
                else -> (t as? ApiError)?.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.could_not_create_ghost_alias)
            }
            GhostAliasResult.Failure(message)
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
        viewModelScope.launch { expire_ghost_alias_now(alias_id) }
    }

    fun extend_ghost_alias(alias_id: String) {
        viewModelScope.launch { extend_ghost_alias_now(alias_id) }
    }

    suspend fun expire_ghost_alias_now(alias_id: String): Boolean {
        return try {
            ghost_alias_api.expire_ghost_alias(alias_id)
            load_ghost_aliases()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun extend_ghost_alias_now(alias_id: String): Boolean {
        return try {
            ghost_alias_api.extend_ghost_alias(alias_id)
            load_ghost_aliases()
            true
        } catch (_: Throwable) {
            false
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
                    error = user_facing_error(t),
                )
            }
        }
    }

    private fun forwarding_notice_for(rule: ForwardingRule?): String? {
        if (rule == null) return null
        val pending = rule.pending_destinations
        if (pending.isNotEmpty()) {
            return context.getString(
                R.string.forwarding_verification_sent,
                pending.joinToString(", ") { it.address },
            )
        }
        if (rule.all_destinations_internal) {
            return context.getString(R.string.forwarding_internal_active)
        }
        return null
    }

    fun clear_forwarding_notice() {
        _state.update { s -> s.copy(forwarding_notice = null) }
    }

    fun create_forwarding_rule(target: String, keep_copy: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(save_status = SaveStatus.SAVING)
            try {
                val created = auto_forward_api.create_rule(
                    CreateForwardingRuleRequest(
                        name = target.take(200),
                        forward_to = listOf(target),
                        keep_copy = keep_copy,
                    ),
                )
                _state.value = _state.value.copy(
                    save_status = SaveStatus.SAVED,
                    forwarding_notice = forwarding_notice_for(created),
                )
                load_forwarding_rules()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = user_facing_error(t),
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
                val rules = auto_forward_api.list_rules().rules
                _state.value = _state.value.copy(
                    save_status = SaveStatus.SAVED,
                    forwarding_rules = rules,
                    forwarding_notice = forwarding_notice_for(rules.firstOrNull { it.id == rule_id }),
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    save_status = SaveStatus.ERROR,
                    error = user_facing_error(t),
                )
            }
        }
    }

    fun resend_forwarding_confirmation(rule_id: String, address: String) {
        if (_state.value.forwarding_resending_address != null) return
        viewModelScope.launch {
            _state.update { s -> s.copy(forwarding_resending_address = address, error = null) }
            try {
                auto_forward_api.resend_confirmation(
                    ResendForwardingConfirmationRequest(id = rule_id, address = address),
                )
                val rules = auto_forward_api.list_rules().rules
                _state.update { s ->
                    s.copy(
                        forwarding_resending_address = null,
                        forwarding_rules = rules,
                        forwarding_notice = context.getString(
                            R.string.forwarding_verification_resent,
                            address,
                        ),
                    )
                }
            } catch (t: Throwable) {
                _state.update { s ->
                    s.copy(
                        forwarding_resending_address = null,
                        error = user_facing_error(t),
                    )
                }
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
                    error = user_facing_error(t),
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
                    error = user_facing_error(t),
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

            val enc_icon = label.encrypted_icon
            val i_nonce = label.icon_nonce
            val icon = if (!enc_icon.isNullOrBlank() && !i_nonce.isNullOrBlank()) {
                decrypt_label_field_with_fallback(enc_icon, i_nonce, all_keys)
            } else enc_icon

            label.copy(encrypted_name = name, encrypted_color = color, encrypted_icon = icon)
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
            val ct = AesGcm.encrypt(key, nonce, data)
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
            return alias.copy(
                encrypted_local_part = "",
                encrypted_display_name = null,
                encrypted_note = null,
                decryption_failed = true,
            )
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
        val enc_note = alias.encrypted_note
        val enc_note_nonce = alias.note_nonce
        val note = if (!enc_note.isNullOrBlank() && !enc_note_nonce.isNullOrBlank()) {
            try {
                decrypt_alias_field(enc_note, enc_note_nonce)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        val enc_websites = alias.encrypted_websites
        val websites_nonce = alias.websites_nonce
        val website_list = if (!enc_websites.isNullOrBlank() && !websites_nonce.isNullOrBlank()) {
            try {
                websites_payload_to_list(decrypt_alias_field(enc_websites, websites_nonce))
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }
        return alias.copy(
            encrypted_local_part = local_part,
            encrypted_display_name = display_name,
            encrypted_note = note,
            encrypted_websites = website_list.joinToString(", ").ifBlank { null },
            websites = website_list,
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

        try {
            val key = derive_passphrase_key()
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
            val ciphertext = AesGcm.encrypt(key, nonce, plaintext.toByteArray(Charsets.UTF_8))
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

    private fun compute_domain_address_hash(local_part: String, domain: String): String {
        val enc_key = derive_encryption_key()
        try {
            val info = "astermail-domain-address-hmac-v1".toByteArray(Charsets.UTF_8)
            val combined = enc_key + info
            val hmac_key_bytes = MessageDigest.getInstance("SHA-256").digest(combined)
            combined.fill(0)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmac_key_bytes, "HmacSHA256"))
            val sig = mac.doFinal("${local_part.lowercase()}@${domain.lowercase()}".toByteArray(Charsets.UTF_8))
            hmac_key_bytes.fill(0)
            return android.util.Base64.encodeToString(sig, android.util.Base64.NO_WRAP)
        } finally {
            enc_key.fill(0)
        }
    }

    private fun compute_domain_address_routing_hash(local_part: String, domain: String): String {
        val data = "${normalize_alias_local_part(local_part)}@${domain.lowercase()}".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(data)
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    private fun compute_directory_key_hash(key: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.lowercase().toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    private fun compute_directory_address_hash(key: String, domain: String): String {
        val data = "${key.lowercase()}@${domain.lowercase()}".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(data)
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

    private fun aes_gcm_decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray =
        AesGcm.decrypt(key, iv, ciphertext)

    private fun derive_encryption_key(): ByteArray {
        session_key_store.get_data_kek()?.let { kek ->
            if (kek.size == 32) return kek
            kek.fill(0)
        }
        return derive_passphrase_key()
    }

    private fun derive_passphrase_key(): ByteArray {
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
        val ciphertext = AesGcm.encrypt(key, nonce, plaintext)
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
                    family_seats = family_seat_usage(r),
                    is_loading = false,
                ) }
            } catch (t: Throwable) {
                _state.update { it.copy(is_loading = false, error = user_facing_error(t)) }
            }
        }
    }

    private fun without_released_seat(state: SettingsUiState): SettingsUiState {
        val seats = state.family_seats ?: return state
        val breakdown = seats.breakdown?.let {
            it.copy(reserved_addresses = (it.reserved_addresses - 1).coerceAtLeast(0))
        }
        return state.copy(
            family_seats = family_seat_usage(
                seats_used = (seats.seats_used - 1).coerceAtLeast(0),
                max_members = seats.max_members,
                breakdown = breakdown,
            ),
        )
    }

    fun release_reservation(id: String, on_done: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                family_api.release_reservation(id)
                _state.update {
                    without_released_seat(
                        it.copy(reserved_addresses = it.reserved_addresses.filter { r -> r.id != id }),
                    )
                }
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
        private const val BLOCKED_SENDERS_HMAC_INFO = "blocked-senders-hmac-v1"
        private const val BLOCKED_SENDERS_INTEGRITY_INFO = "blocked-senders-v1"
        private const val ALLOWED_SENDERS_HMAC_INFO = "allowed-senders-hmac-v1"
        private const val ALLOWED_SENDERS_INTEGRITY_INFO = "allowed-senders-v1"
        private const val SALT_PREFIX = "aster-hkdf-salt-v1:"
        private const val DERIVED_KEY_INFO = "aster-storage-encryption-key-v1"
        private const val TAG_VERSION_CURRENT = "astermail-tags-v1"
        private const val FOLDER_VERSION_CURRENT = "astermail-labels-v1"
        private const val PREFERENCES_KEY_SUFFIX = "astermail-preferences-v1"
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


internal fun apply_default_signature_content(
    current: List<DecryptedSignature>,
    target_id: String,
    content: String,
    default_name: String,
    is_html: Boolean,
): List<DecryptedSignature> = if (current.any { it.id == target_id }) {
    current.map { if (it.id == target_id) it.copy(content = content) else it }
} else {
    current + DecryptedSignature(
        id = target_id,
        name = default_name,
        content = content,
        is_default = true,
        is_html = is_html,
        alias_id = null,
        placement = null,
    )
}
