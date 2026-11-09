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

import org.astermail.android.BuildConfig
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.astermail.android.R
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Locale
import org.astermail.android.util.passphrase_chars
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.AuthSaltCollisionException
import org.astermail.android.crypto.AuthSaltGuard
import org.astermail.android.crypto.hkdf_sha256
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.crypto.VaultKdf
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.Argon2Params
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.ClientPgpKeyData
import org.astermail.android.api.auth.DeleteAccountRequest
import org.astermail.android.api.auth.EnrollPasswordV2Request
import org.astermail.android.api.auth.LoginRequest
import org.astermail.android.api.auth.LoginResponse
import org.astermail.android.api.auth.LoginResult
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.RegisterRequest
import org.astermail.android.api.auth.RotateLoginSaltRequest
import org.astermail.android.api.auth.SessionRefresher
import org.astermail.android.api.auth.TotpLoginVerifyRequest
import org.astermail.android.api.keys.CurrentVaultResult
import org.astermail.android.api.recovery.ConsumeInactiveKeySetRequest
import org.astermail.android.api.recovery.FetchInactiveKeySetRequest
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.api.recovery.RecoveryShareData
import org.astermail.android.api.recovery.SaveRecoveryBackupRequest
import org.astermail.android.api.recovery.UnlockInactiveWithCodeRequest
import org.astermail.android.api.settings.ChangePasswordRequest
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.PgpKeyPairResult
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshotStore
import org.astermail.android.storage.StoredAccount
import org.astermail.android.notifications.UnifiedPushState
import org.astermail.android.security.LockdownStore
import org.astermail.android.storage.ThemeStore
import org.astermail.android.storage.TokenStore
import org.astermail.android.storage.TrustedDeviceStore

data class RegisterSuccess(val recovery_codes: List<String>, val recovery_backup_saved: Boolean = true)

data class TotpChallenge(
    val pending_login_token: String,
    val available_methods: List<String>,
    val password_hash_bytes: ByteArray,
    val password_bytes: ByteArray,
    val salt_bytes: ByteArray,
    val email: String,
    val remember_me: Boolean,
    val user_hash: String = "",
    val login_enrollment: PendingLoginEnrollment? = null,
)

private const val UNAUTHORIZED_CHECK_COOLDOWN_MS = 10_000L
private const val VAULT_STEP_INLINE_TIMEOUT_MS = 15_000L
private const val RECOVERY_BACKUP_ATTEMPTS = 3
private const val SIGNUP_PGP_KEYGEN_ATTEMPTS = 3
private const val PUBLISHED_PGP_KEY_LOOKUP_ATTEMPTS = 3
private const val PUBLISHED_PGP_KEY_LOOKUP_BACKOFF_MS = 750L
private const val RECOVERY_BACKUP_RETRY_DELAY_MS = 1_200L
private const val ACCOUNT_SLOT_TIMEOUT_MS = 10_000L
private const val ACCOUNT_SLOT_RELEASE_TIMEOUT_MS = 3_000L

sealed interface LoginOutcome {
    data object Success : LoginOutcome
    data class NeedsTotp(val challenge: TotpChallenge) : LoginOutcome
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth_api: AuthApi,
    private val recovery_api: RecoveryApi,
    private val keys_api: org.astermail.android.api.keys.KeysApi,
    private val recovery_email_api: org.astermail.android.api.recovery_email.RecoveryEmailApi,
    private val settings_api: SettingsApi,
    private val encryption_api: org.astermail.android.api.encryption.EncryptionApi,
    private val api_client: ApiClient,
    private val session_refresher: SessionRefresher,
    private val token_store: TokenStore,
    private val session_key_store: SessionKeyStore,
    private val account_store: AccountStore,
    private val database_provider: dagger.Lazy<org.astermail.android.storage.search.AsterDatabase>,
    private val session_snapshot_store: SessionSnapshotStore,
    private val trusted_device_store: TrustedDeviceStore,
    private val mail_repository: org.astermail.android.mail.MailRepository,
    private val theme_store: ThemeStore,
    private val ratchet_bootstrap_service: org.astermail.android.mail.ratchet.RatchetBootstrapService,
    private val system_folder_bootstrap: org.astermail.android.mail.SystemFolderBootstrap,
    private val password_change_sent_mail: org.astermail.android.mail.PasswordChangeSentMail,
    private val identity_pins: dagger.Lazy<org.astermail.android.mail.ratchet.RatchetIdentityPinStore>,
    private val offer_preferences_store: org.astermail.android.ui.upgrade.OfferPreferencesStore,
    private val login_pins: LoginProofPinStore,
    private val external_pgp_key_store: dagger.Lazy<org.astermail.android.crypto.ExternalPgpKeyStore>,
    @ApplicationContext private val context: Context,
) {

    private val database: org.astermail.android.storage.search.AsterDatabase
        get() = database_provider.get()

    private val login_salt_records: LoginSaltRecordSource
        get() = login_pins

    private val _is_signed_in = MutableStateFlow(token_store.access_token != null)
    val is_signed_in: StateFlow<Boolean> = _is_signed_in.asStateFlow()
    private val _active_account_id = MutableStateFlow(
        if (token_store.access_token != null) account_store.get_current_id() else null,
    )
    val active_account_id: StateFlow<String?> = _active_account_id.asStateFlow()

    private val _session_expired = MutableStateFlow(false)
    val session_expired: StateFlow<Boolean> = _session_expired.asStateFlow()

    private val _forced_account_switch = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val forced_account_switch: SharedFlow<String> = _forced_account_switch.asSharedFlow()

    fun consume_session_expired() {
        _session_expired.value = false
    }

    private val unauthorized_check_running = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var last_unauthorized_check_ms = 0L

    private val background_scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    private val dead_session_mutex = kotlinx.coroutines.sync.Mutex()
    private val vault_commit_mutex = org.astermail.android.crypto.storage_rotation.VaultWriteLock.mutex
    private val vault_updates by lazy {
        org.astermail.android.crypto.storage_rotation.VaultUpdates(api_client, session_key_store)
    }
    private val storage_rekey_service by lazy {
        org.astermail.android.crypto.storage_rotation.StorageRekeyService(context, api_client, session_key_store)
    }

    init {
        session_refresher.on_auth_failure { presented ->
            handle_refresh_auth_failure(presented)
        }
        org.astermail.android.crypto.StorageKeyGate.install {
            storage_rekey_service.ensure_random_storage_key()
            val key = org.astermail.android.crypto.StorageWriteKey.write_key(session_key_store)
            key?.fill(0)
            key != null
        }
        org.astermail.android.crypto.storage_rotation.StorageRekeyService.install_backup_builder(
            org.astermail.android.crypto.storage_rotation.RecoveryBackupBuilder { vault -> build_refresh_backup(vault) },
        )
    }

    private fun build_refresh_backup(vault: org.json.JSONObject): kotlinx.serialization.json.JsonObject? {
        val codes = org.astermail.android.crypto.storage_rotation.vault_recovery_codes(vault)
        if (codes.isEmpty()) return null
        val passphrase = session_key_store.get_passphrase() ?: return null
        val recovery_key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        try {
            val backup_plain = backup_vault_bytes(vault, passphrase.copyOf())
            val vault_backup = try {
                encrypt_vault_backup(backup_plain, recovery_key)
            } finally {
                backup_plain.fill(0)
            }
            val shares = codes.map { generate_recovery_share(it, recovery_key) }
            val request = SaveRecoveryBackupRequest(
                recovery_shares = shares,
                encrypted_vault_backup = vault_backup.encrypted_data,
                vault_backup_nonce = vault_backup.nonce,
                recovery_key_salt = vault_backup.salt,
                refresh_only = true,
            )
            return refresh_backup_json.encodeToJsonElement(SaveRecoveryBackupRequest.serializer(), request)
                as? kotlinx.serialization.json.JsonObject
        } finally {
            recovery_key.fill(0)
            passphrase.fill(0)
        }
    }

    private val refresh_backup_json = kotlinx.serialization.json.Json {
        explicitNulls = false
        encodeDefaults = true
    }

    private suspend fun run_vault_step_inline() {
        try {
            withTimeoutOrNull(VAULT_STEP_INLINE_TIMEOUT_MS) { storage_rekey_service.ensure_random_storage_key() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }
    private val pgp_publish_attempted_user_ids =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val pgp_address_heal_attempted =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val recovery_email_backfill_attempted_user_ids =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val system_folder_heal_attempted_user_ids =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    fun trigger_system_folder_bootstrap() {
        if (!_is_signed_in.value) return
        val user_id = session_key_store.get_user_id() ?: return
        if (!system_folder_heal_attempted_user_ids.add(user_id)) return
        background_scope.launch {
            runCatching { system_folder_bootstrap.ensure_system_folders() }
                .onFailure { system_folder_heal_attempted_user_ids.remove(user_id) }
        }
    }

    private val account_key_loader = AccountKeyLoader(keys_api, session_key_store)

    private fun load_account_keks() {
        background_scope.launch { runCatching { account_key_loader.load() } }
        background_scope.launch {
            try {
                external_pgp_key_store.get().load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            }
        }
    }

    fun trigger_ratchet_bootstrap() {
        if (!_is_signed_in.value) return
        load_account_keks()
        if (BuildConfig.DEBUG) android.util.Log.w("RatchetBootstrap", "trigger_ratchet_bootstrap firing")
        schedule_ratchet_bootstrap(bypass_debounce = false)
    }

    private fun schedule_ratchet_bootstrap(bypass_debounce: Boolean) {
        ratchet_bootstrap_service.schedule_until_published(
            scope = background_scope,
            is_foreground = ::app_in_foreground,
            bypass_debounce = bypass_debounce,
        )
    }

    private fun app_in_foreground(): Boolean = runCatching {
        androidx.lifecycle.ProcessLifecycleOwner.get()
            .lifecycle.currentState
            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    }.getOrDefault(true)

    suspend fun handle_unauthorized_signal(force: Boolean = false) {
        if (!_is_signed_in.value) return
        val now = System.currentTimeMillis()
        if (!force && now - last_unauthorized_check_ms < UNAUTHORIZED_CHECK_COOLDOWN_MS) return
        if (!unauthorized_check_running.compareAndSet(false, true)) return
        last_unauthorized_check_ms = now
        try {
            auth_api.me()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.UnauthorizedError) {
            val presented = token_store.refresh_token
            when (try_refresh_session()) {
                RefreshOutcome.Success -> {
                    val refreshed = token_store.refresh_token
                    try {
                        auth_api.me()
                    } catch (e2: CancellationException) {
                        throw e2
                    } catch (e2: ApiError.UnauthorizedError) {
                        sign_out_dead_session(refreshed)
                    } catch (_: Throwable) {
                    }
                }
                RefreshOutcome.AuthFailed -> sign_out_dead_session(presented)
                RefreshOutcome.Transient -> {
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        } finally {
            unauthorized_check_running.set(false)
        }
    }

    private suspend fun handle_refresh_auth_failure(presented: String?) {
        if (presented.isNullOrEmpty()) return
        if (!_is_signed_in.value || presented != token_store.refresh_token) return
        if (!org.astermail.android.api.auth.confirm_session_rejected { auth_api.me() }) return
        sign_out_dead_session(presented)
    }

    private suspend fun sign_out_dead_session(presented: String?) {
        dead_session_mutex.withLock {
            if (!_is_signed_in.value) return
            if (presented != token_store.refresh_token) return
            val owner = resolve_account_id(session_token_owner(presented, null)) ?: session_key_store.get_user_id()
            withContext(NonCancellable) { force_sign_out(owner) }
        }
    }

    private fun resolve_account_id(subject: String?): String? {
        if (subject == null) return null
        return account_store.get_all().firstOrNull { same_account(it.id, subject) }?.id ?: subject
    }

    private fun current_tokens_belong_to(account_id: String): Boolean {
        val owner = session_token_owner(token_store.refresh_token, token_store.access_token) ?: return true
        return same_account(owner, account_id)
    }

    private fun persist_current_session_locked(csrf: String?) {
        val access = token_store.access_token
        val refresh = token_store.refresh_token
        if (access.isNullOrEmpty() && refresh.isNullOrEmpty()) return
        val owner = resolve_account_id(session_token_owner(refresh, access))
            ?: session_key_store.get_user_id()
            ?: return
        runCatching { session_snapshot_store.update_tokens(owner, null, access, refresh, csrf) }
    }

    private suspend fun install_session_tokens(access: String, refresh: String?, csrf: String?) {
        SessionRefreshGate.mutex.withLock {
            persist_current_session_locked(null)
            token_store.save(access, refresh ?: access)
            csrf?.let { api_client.set_csrf(it) }
            api_client.invalidate_bearer_cache()
        }
    }

    suspend fun refresh_session(): RefreshOutcome = try_refresh_session()

    suspend fun refresh_session_if_expiring() {
        if (!_is_signed_in.value) return
        val needs_refresh = org.astermail.android.api.auth.access_token_needs_refresh(
            token_store.access_token,
            System.currentTimeMillis() / 1000L,
            org.astermail.android.api.auth.RefreshTrigger.Foreground,
        )
        if (!needs_refresh) return
        try_refresh_session()
    }

    private suspend fun try_refresh_session(): RefreshOutcome {
        if (token_store.refresh_token == null) return RefreshOutcome.AuthFailed
        val outcome = session_refresher.refresh()
        if (outcome == RefreshOutcome.Success) {
            runCatching { refresh_session_snapshot() }
            background_scope.launch { runCatching { backfill_server_recovery_email() } }
        }
        return outcome
    }

    suspend fun login(
        email: String,
        password: String,
        captcha_token: String? = null,
        remember_me: Boolean = true,
    ): Result<LoginOutcome> = runCatching {
        val normalized = normalize_email(email)
        val trusted_token = trusted_device_store.get_token(normalized)
        val dotless_hash = CryptoNative.hash_email(normalized)
        val dotted_hash = CryptoNative.hash_email_keeping_dots(normalized)
        val (user_hash, salt_resp) = runCatching {
            dotless_hash to auth_api.get_user_salt(dotless_hash)
        }.getOrElse { error ->
            if (dotted_hash == dotless_hash) throw error
            dotted_hash to auth_api.get_user_salt(dotted_hash)
        }
        val kdf_version = LoginProof.read_kdf_version(salt_resp.kdf_version)
        val salt_bytes = base64_decode(salt_resp.salt)
        require_usable_auth_salt(salt_bytes)
        LoginSaltRule.require_v1_proof_allowed(
            salt_bytes,
            kdf_version,
            login_salt_records.find(LoginSaltRule.record_keys(user_hash, dotless_hash, dotted_hash)),
            LoginSaltCheck.sign_in,
            account_seen_before(dotless_hash, dotted_hash),
        )
        val password_bytes = password.toByteArray(Charsets.UTF_8)
        val v1_bytes = derive_login_hash(password_bytes, salt_bytes)
        val sign_in_proof = try {
            LoginProof.select_sign_in_proof(
                v1_bytes,
                kdf_version,
                LoginProof.pin_keys(null, user_hash),
                login_pins,
            )
        } catch (refused: SecurityException) {
            password_bytes.fill(0)
            throw refused
        } finally {
            v1_bytes.fill(0)
        }
        val password_hash_bytes = sign_in_proof.proof
        val login_enrollment = sign_in_proof.enrollment
        val password_hash_b64 = base64_encode(password_hash_bytes)

        val integrity = PlayIntegrityLogin.fetch(context, user_hash)

        val login_result = try {
            auth_api.login(
                LoginRequest(
                    user_hash = user_hash,
                    password_hash = password_hash_b64,
                    captcha_token = captcha_token,
                    remember_me = remember_me,
                    integrity_token = integrity?.token,
                    integrity_nonce = integrity?.nonce,
                ),
                trusted_device_token = trusted_token,
            )
        } catch (failed: Throwable) {
            login_enrollment?.wipe()
            throw failed
        }

        when (login_result) {
            is LoginResult.TotpRequired -> {
                LoginOutcome.NeedsTotp(
                    TotpChallenge(
                        pending_login_token = login_result.challenge.pending_login_token,
                        available_methods = login_result.challenge.available_methods,
                        password_hash_bytes = password_hash_bytes,
                        password_bytes = password_bytes,
                        salt_bytes = salt_bytes,
                        email = normalized,
                        remember_me = remember_me,
                        user_hash = user_hash,
                        login_enrollment = login_enrollment,
                    ),
                )
            }
            is LoginResult.Success -> {
                login_result.trusted_device_token?.takeIf { it.isNotBlank() }?.let { token ->
                    trusted_device_store.put_token(normalized, token)
                }
                complete_login(
                    login_result.response,
                    password_bytes,
                    password_hash_bytes,
                    salt_bytes,
                    user_hash,
                    login_enrollment,
                )
                LoginOutcome.Success
            }
        }
    }

    suspend fun verify_totp(
        code: String,
        challenge: TotpChallenge,
        trust_device: Boolean,
        use_backup_code: Boolean = false,
    ): Result<Unit> = runCatching {
        val request = TotpLoginVerifyRequest(
            code = code.trim(),
            pending_login_token = challenge.pending_login_token,
            trust_device = trust_device,
            remember_me = challenge.remember_me,
            device_label = login_device_label(),
        )
        val outcome = if (use_backup_code) {
            auth_api.verify_backup_code_login(request)
        } else {
            auth_api.verify_totp_login(request)
        }
        finish_second_factor_login(outcome, challenge, trust_device)
    }

    suspend fun begin_webauthn(
        challenge: TotpChallenge,
    ): Result<org.astermail.android.api.auth.WebAuthnAssertionOptions> = runCatching {
        auth_api.initiate_webauthn_assertion(
            org.astermail.android.api.auth.WebAuthnAssertionInitiateRequest(
                pending_login_token = challenge.pending_login_token,
            ),
        )
    }

    suspend fun verify_webauthn(
        request: org.astermail.android.api.auth.WebAuthnAssertionVerifyRequest,
        challenge: TotpChallenge,
        trust_device: Boolean,
    ): Result<Unit> = runCatching {
        val outcome = auth_api.verify_webauthn_assertion(request)
        finish_second_factor_login(outcome, challenge, trust_device)
    }

    fun webauthn_origin(): String = api_client.webauthn_origin.trimEnd('/')

    fun login_device_label(): String? {
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty().trim()
        val model = android.os.Build.MODEL.orEmpty().trim()
        val label = when {
            model.isEmpty() -> manufacturer
            manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "${manufacturer.replaceFirstChar { it.titlecase(Locale.ROOT) }} $model"
        }
        return label.take(64).takeIf { it.isNotBlank() }
    }

    private suspend fun finish_second_factor_login(
        outcome: org.astermail.android.api.auth.TotpVerifyOutcome,
        challenge: TotpChallenge,
        trust_device: Boolean,
    ) {
        if (trust_device) {
            outcome.trusted_device_token?.takeIf { it.isNotBlank() }?.let { token ->
                trusted_device_store.put_token(challenge.email, token)
            }
        }
        complete_login(
            outcome.response,
            challenge.password_bytes,
            challenge.password_hash_bytes,
            challenge.salt_bytes,
            challenge.user_hash,
            challenge.login_enrollment,
        )
    }

    private suspend fun backfill_server_recovery_email() {
        val user_id = session_key_store.get_user_id() ?: return
        if (!recovery_email_backfill_attempted_user_ids.add(user_id)) return
        val state = recovery_email_api.get_state()
        if (state.has_server_enc || !state.verified) return
        val ciphertext = state.encrypted_email ?: return
        val nonce = state.email_nonce ?: return
        val identity_key = session_key_store.get_identity_key()?.takeIf { it.isNotBlank() } ?: return
        val plaintext = org.astermail.android.recovery.decrypt_recovery_email(
            ciphertext,
            nonce,
            identity_key,
        )
        recovery_email_api.backfill_server_enc(
            org.astermail.android.api.recovery_email.BackfillServerEncRequest(
                plaintext_email = plaintext,
                email_hash = org.astermail.android.recovery.hash_recovery_email(plaintext),
            ),
        )
    }

    private suspend fun complete_login(
        login_resp: LoginResponse,
        password_bytes: ByteArray,
        password_hash_bytes: ByteArray,
        salt_bytes: ByteArray,
        user_hash: String,
        login_enrollment: PendingLoginEnrollment?,
    ) {
        mail_repository.pause_pending_drain()
        try {
            complete_login_while_drain_paused(
                login_resp,
                password_bytes,
                password_hash_bytes,
                salt_bytes,
                user_hash,
                login_enrollment,
            )
        } catch (failed: Throwable) {
            login_enrollment?.wipe()
            throw failed
        } finally {
            mail_repository.resume_pending_drain()
        }
    }

    private suspend fun complete_login_while_drain_paused(
        login_resp: LoginResponse,
        password_bytes: ByteArray,
        password_hash_bytes: ByteArray,
        salt_bytes: ByteArray,
        user_hash: String,
        login_enrollment: PendingLoginEnrollment?,
    ) {
        var rotation_password: ByteArray? = null
        try {
            finish_login_session(
                login_resp,
                password_bytes,
                password_hash_bytes,
                salt_bytes,
                user_hash,
                login_enrollment,
            ) { kept -> rotation_password = kept }
        } catch (failed: Throwable) {
            rotation_password?.fill(0)
            throw failed
        }
        val password_for_rotation = rotation_password
        val session_pin_keys = LoginProof.pin_keys(login_resp.user_id, user_hash)
        start_retired_login_salt_refresh(login_resp.user_id)
        when {
            login_enrollment == null -> {
                password_for_rotation?.fill(0)
                runCatching { login_pins.pin(session_pin_keys) }
            }
            password_for_rotation != null -> start_login_salt_rotation(
                login_resp.user_id,
                login_record_keys(user_hash, login_resp.email),
                session_pin_keys,
                login_enrollment,
                password_for_rotation,
            )
            else -> start_login_enrollment(login_resp.user_id, session_pin_keys, login_enrollment)
        }
    }

    private suspend fun finish_login_session(
        login_resp: LoginResponse,
        password_bytes: ByteArray,
        password_hash_bytes: ByteArray,
        salt_bytes: ByteArray,
        user_hash: String,
        login_enrollment: PendingLoginEnrollment?,
        keep_for_rotation: (ByteArray) -> Unit,
    ) {
        val access = login_resp.access_token ?: throw ApiError.UnknownError(context.getString(R.string.error_generic))
        val previous_user_id = session_key_store.get_user_id()
        claim_account_slot(
            access = access,
            refresh = login_resp.refresh_token,
            csrf = api_client.get_csrf(),
            previous_user_id = previous_user_id,
            account_known = account_store.account_exists(login_resp.user_id),
        )
        if (previous_user_id != null && previous_user_id != login_resp.user_id) {
            SessionRefreshGate.mutex.withLock { persist_current_session_locked(null) }
            session_key_store.clear()
            if (!clear_decrypted_mail_cache_blocking()) {
                throw ApiError.UnknownError(context.getString(R.string.auth_previous_account_data_not_cleared))
            }
            mail_repository.clear_account_data()
            cancel_all_notifications()
            runCatching { org.astermail.android.notifications.MailPollingWorker.reset_new_mail_baseline(context) }
            runCatching { org.astermail.android.notifications.NotificationDedupe.clear(context) }
        }
        val served_vault_bytes = runCatching { base64_decode(login_resp.encrypted_vault) }.getOrNull()
        if (AuthSaltGuard.collides_with_vault_salt(salt_bytes, served_vault_bytes)) {
            runCatching { session_key_store.clear() }
            runCatching { token_store.clear() }
            throw AuthSaltCollisionException("auth salt equals the vault key salt")
        }
        withContext(NonCancellable) {
            install_session_tokens(access, login_resp.refresh_token, null)
            session_key_store.put(password_hash_bytes)
            session_key_store.put_passphrase(password_bytes)
            session_key_store.put_password_salt(salt_bytes)
            runCatching { login_salt_records.put(login_record_keys(user_hash, login_resp.email), salt_bytes) }
            session_key_store.put_user_id(login_resp.user_id)
            session_key_store.put_user_email(login_resp.email)
            session_key_store.put_encrypted_vault(
                login_resp.encrypted_vault,
                login_resp.vault_nonce,
                reported_vault_kdf(login_resp.vault_kdf_iterations),
            )

            try {
                val vault_bytes = base64_decode(login_resp.encrypted_vault)
                val nonce_bytes = base64_decode(login_resp.vault_nonce)
                val unwrapped = CryptoNative.unwrap_vault(
                    vault_bytes,
                    nonce_bytes,
                    password_bytes,
                    login_resp.vault_kdf_iterations,
                )
                if (unwrapped.iterations != session_key_store.get_vault_kdf_iterations()) {
                    session_key_store.put_encrypted_vault(
                        login_resp.encrypted_vault,
                        login_resp.vault_nonce,
                        unwrapped.iterations,
                    )
                }
                val vault_plain = unwrapped.plaintext
                val vault_json = String(vault_plain, Charsets.UTF_8)
                vault_plain.fill(0)
                val vault_obj = org.json.JSONObject(vault_json)
                val identity_key = vault_obj.optString("identity_key", "")
                    .ifBlank { vault_obj.optString("identity_private_key", "") }
                if (identity_key.isNotBlank()) {
                    session_key_store.put_identity_key(identity_key)
                } else {
                    if (BuildConfig.DEBUG) android.util.Log.w("AuthRepository", "vault decrypted but no identity_key field present")
                }
                val codes_array = vault_obj.optJSONArray("recovery_codes")
                if (codes_array != null) {
                    val codes = (0 until codes_array.length()).map { codes_array.getString(it) }
                    session_key_store.put_recovery_codes(codes)
                }
                absorb_previous_keys_and_keks(vault_obj)
                absorb_data_kek(vault_obj)
                extract_ratchet_keys(vault_obj)
                absorb_stretched_storage_keys(vault_obj, password_bytes)
                schedule_storage_rekey()
                if (login_enrollment != null && LoginSaltRule.needs_rotation(1, salt_bytes)) {
                    keep_for_rotation(password_bytes.copyOf())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("AuthRepository", "vault decryption failed: ${t.javaClass.simpleName}")
            } finally {
                password_bytes.fill(0)
                password_hash_bytes.fill(0)
            }

            val profile = runCatching { withTimeoutOrNull(8_000L) { auth_api.me() } }.getOrNull()
            profile?.let { LockdownStore.set_enabled(context, it.lockdown_mode_enabled) }
            val previous_account = if (profile == null) {
                account_store.get_all().firstOrNull { it.id == login_resp.user_id }
            } else {
                null
            }
            account_store.add_or_update(
                StoredAccount(
                    id = login_resp.user_id,
                    email = login_resp.email,
                    display_name = profile?.display_name ?: previous_account?.display_name,
                    profile_color = profile?.profile_color ?: previous_account?.profile_color,
                    profile_picture = profile?.profile_picture ?: previous_account?.profile_picture,
                    added_at = System.currentTimeMillis(),
                ),
                max_accounts = AccountStore.unlimited_accounts,
            )
            run_vault_step_inline()
            runCatching { save_session_snapshot(login_resp.user_id) }
            _active_account_id.value = login_resp.user_id
            _is_signed_in.value = true
            _session_expired.value = false
        }
        runCatching { UnifiedPushState.clear_backend_registration(context) }
        runCatching { UnifiedPushState.sync_registration(context) }
        runCatching { org.astermail.android.notifications.PersistentPushService.start_if_enabled(context) }
        background_scope.launch { runCatching { ensure_pgp_key_published() } }
        background_scope.launch { runCatching { system_folder_bootstrap.ensure_system_folders() } }
        background_scope.launch { runCatching { backfill_server_recovery_email() } }
        schedule_ratchet_bootstrap(bypass_debounce = true)
        load_account_keks()
    }

    private fun scoped_session_client(access: String, refresh: String?, csrf: String?): ApiClient {
        val tokens = io.ktor.client.plugins.auth.providers.BearerTokens(access, refresh ?: access)
        val provider = object : org.astermail.android.api.TokenProvider {
            override suspend fun load(): io.ktor.client.plugins.auth.providers.BearerTokens = tokens
            override suspend fun refresh(): io.ktor.client.plugins.auth.providers.BearerTokens? = null
            override suspend fun clear() {}
        }
        return ApiClient(
            base_url = org.astermail.android.api.BuildConfig.API_BASE_URL,
            token_provider = provider,
            initial_csrf = csrf,
            allow_cleartext_for_test = org.astermail.android.api.BuildConfig.API_BASE_URL.startsWith("http://"),
            device_id = org.astermail.android.api.DeviceIdStore.get(context),
        )
    }

    private suspend fun claim_account_slot(
        access: String,
        refresh: String?,
        csrf: String?,
        previous_user_id: String?,
        account_known: Boolean,
    ) {
        val client = scoped_session_client(access, refresh, csrf)
        try {
            val billing = org.astermail.android.api.billing.BillingApiImpl(client)
            val link = try {
                withTimeoutOrNull(ACCOUNT_SLOT_TIMEOUT_MS) { billing.link_account_device() }
                    ?.let { AccountSlotLink.Linked(it.max_accounts) }
                    ?: AccountSlotLink.Unknown
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ApiError.PlanLimitExceeded) {
                AccountSlotLink.LimitReached
            } catch (_: Throwable) {
                AccountSlotLink.Unknown
            }
            if (link is AccountSlotLink.Linked && link.max_accounts != 0) {
                runCatching { account_store.set_max_accounts(link.max_accounts) }
            }
            if (decide_account_slot(link, account_known, account_store.can_add()) == AccountSlotDecision.Allow) return
            val max_accounts = if (link == AccountSlotLink.LimitReached) {
                runCatching {
                    withTimeoutOrNull(ACCOUNT_SLOT_TIMEOUT_MS) { billing.get_account_limit().max_accounts }
                }.getOrNull()
            } else {
                account_store.get_max_accounts()
            }
            reject_new_session(client, previous_user_id)
            throw AccountLimitReachedException(max_accounts?.takeIf { it > 0 })
        } finally {
            runCatching { client.close() }
        }
    }

    private suspend fun release_account_slot_with_current_tokens() {
        val access = token_store.access_token?.takeIf { it.isNotEmpty() } ?: return
        val client = runCatching {
            scoped_session_client(access, token_store.refresh_token, api_client.get_csrf())
        }.getOrNull() ?: return
        try {
            withContext(NonCancellable) {
                withTimeoutOrNull(ACCOUNT_SLOT_RELEASE_TIMEOUT_MS) {
                    runCatching { org.astermail.android.api.billing.BillingApiImpl(client).unlink_account_device() }
                }
            }
        } finally {
            runCatching { client.close() }
        }
    }

    private suspend fun reject_new_session(client: ApiClient, previous_user_id: String?) {
        withContext(NonCancellable) {
            runCatching {
                withTimeoutOrNull(ACCOUNT_SLOT_TIMEOUT_MS) {
                    org.astermail.android.api.auth.AuthApiImpl(client).logout()
                }
            }
            restore_previous_csrf(previous_user_id)
        }
    }

    private suspend fun restore_previous_csrf(previous_user_id: String?) {
        SessionRefreshGate.mutex.withLock {
            if (token_store.access_token.isNullOrEmpty() && token_store.refresh_token.isNullOrEmpty()) {
                api_client.clear_csrf()
                return@withLock
            }
            val owner = resolve_account_id(session_token_owner(token_store.refresh_token, token_store.access_token))
                ?: previous_user_id
                ?: return@withLock
            val snapshot = runCatching { session_snapshot_store.load(owner) }.getOrNull() ?: return@withLock
            snapshot.passphrase?.fill(0)
            snapshot.session_key?.fill(0)
            snapshot.password_salt?.fill(0)
            snapshot.csrf_token?.let { api_client.set_csrf(it) }
        }
    }

    private fun start_login_enrollment(
        user_id: String,
        pin_keys: List<String>,
        enrollment: PendingLoginEnrollment,
    ) {
        background_scope.launch {
            try {
                flush_login_enrollment(user_id, pin_keys, enrollment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            } finally {
                enrollment.wipe()
            }
        }
    }

    private suspend fun flush_login_enrollment(
        user_id: String,
        pin_keys: List<String>,
        enrollment: PendingLoginEnrollment,
    ) {
        val enrolled = LoginProof.flush_enrollment(enrollment, pin_keys, login_pins) { v1, v2 ->
            enroll_login_v2(v1, v2)
        }
        if (enrolled && session_key_store.get_user_id() == user_id) {
            session_key_store.put(enrollment.v2)
            runCatching { save_session_snapshot(user_id) }
        }
    }

    private fun start_retired_login_salt_refresh(user_id: String) {
        background_scope.launch {
            try {
                refresh_retired_login_salts(user_id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun refresh_retired_login_salts(user_id: String) {
        val encoded = auth_api.get_retired_login_salts().retired_salts
        val salts = org.astermail.android.storage.RetiredLoginSalts.decode_base64(encoded)
        try {
            if (salts.isNotEmpty()) session_key_store.retire_password_salts(user_id, salts)
        } finally {
            salts.forEach { it.fill(0) }
        }
    }

    private fun start_login_salt_rotation(
        user_id: String,
        record_keys: List<String>,
        pin_keys: List<String>,
        enrollment: PendingLoginEnrollment,
        password_bytes: ByteArray,
    ) {
        background_scope.launch {
            try {
                val rotated = try {
                    rotate_login_salt(user_id, record_keys, pin_keys, enrollment.v1, password_bytes)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    false
                }
                if (!rotated) flush_login_enrollment(user_id, pin_keys, enrollment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            } finally {
                password_bytes.fill(0)
                enrollment.wipe()
            }
        }
    }

    private suspend fun rotate_login_salt(
        user_id: String,
        record_keys: List<String>,
        pin_keys: List<String>,
        current_v1: ByteArray,
        password_bytes: ByteArray,
    ): Boolean {
        val new_salt = ByteArray(LoginSaltRule.min_v1_login_salt_bytes).also { SecureRandom().nextBytes(it) }
        val new_v1 = withContext(Dispatchers.Default) {
            CryptoNative.derive_pbkdf2_hash(password_bytes, new_salt, pbkdf2_iterations)
        }
        val new_v2 = LoginProof.derive_v2(new_v1)
        try {
            ensure_csrf_ready()
            val response = auth_api.rotate_login_salt(
                RotateLoginSaltRequest(
                    password_hash = base64_encode(current_v1),
                    new_password_salt = base64_encode(new_salt),
                    new_password_hash = base64_encode(new_v1),
                    new_password_hash_v2 = base64_encode(new_v2),
                ),
            )
            if (LoginProof.read_kdf_version(response.kdf_version) != 2) return false
            withContext(NonCancellable) {
                runCatching { login_pins.pin(pin_keys) }
                runCatching { login_salt_records.put(record_keys, new_salt) }
                if (session_key_store.get_user_id() == user_id) {
                    session_key_store.get_password_salt()?.let { previous ->
                        try {
                            if (!previous.contentEquals(new_salt)) {
                                session_key_store.retire_password_salts(user_id, listOf(previous))
                            }
                        } finally {
                            previous.fill(0)
                        }
                    }
                    session_key_store.put(new_v2)
                    session_key_store.put_password_salt(new_salt)
                    runCatching { save_session_snapshot(user_id) }
                }
            }
            return true
        } finally {
            new_v1.fill(0)
            new_v2.fill(0)
        }
    }

    private fun login_record_keys(user_hash: String?, account_email: String?): List<String> {
        val email = account_email?.takeIf { it.isNotBlank() }?.let(::normalize_email)
        return LoginSaltRule.record_keys(
            user_hash,
            email?.let { runCatching { CryptoNative.hash_email(it) }.getOrNull() },
            email?.let { runCatching { CryptoNative.hash_email_keeping_dots(it) }.getOrNull() },
        )
    }

    private fun session_record_keys(): List<String> = login_record_keys(null, session_key_store.get_user_email())

    private fun seed_login_salt_record_from_session() {
        runCatching {
            val keys = session_record_keys()
            if (keys.isEmpty() || login_salt_records.find(keys) != null) return@runCatching
            val salt = session_key_store.get_password_salt() ?: return@runCatching
            try {
                login_salt_records.put(keys, salt)
            } finally {
                salt.fill(0)
            }
        }
    }

    private fun account_seen_before(dotless_hash: String, dotted_hash: String): Boolean {
        val emails = runCatching { account_store.get_all().map { it.email } }.getOrDefault(emptyList()) +
            listOfNotNull(runCatching { session_key_store.get_user_email() }.getOrNull())
        return emails.any { stored ->
            if (stored.isBlank()) return@any false
            val email = normalize_email(stored)
            runCatching {
                CryptoNative.hash_email(email) == dotless_hash ||
                    CryptoNative.hash_email_keeping_dots(email) == dotted_hash
            }.getOrDefault(false)
        }
    }

    private suspend fun enroll_login_v2(v1: ByteArray, v2: ByteArray): Boolean {
        ensure_csrf_ready()
        val response = auth_api.enroll_password_v2(
            EnrollPasswordV2Request(
                password_hash = base64_encode(v1),
                password_hash_v2 = base64_encode(v2),
            ),
        )
        return LoginProof.read_kdf_version(response.kdf_version) == 2
    }

    private class StepUpSalt(
        val salt: ByteArray,
        val kdf_version: Int,
        val pin_keys: List<String>,
        val record_keys: List<String>,
    )

    private suspend fun step_up_salt(): StepUpSalt? {
        val user_hash = session_key_store.get_user_email()?.let { CryptoNative.hash_email(it) }
        val pin_keys = LoginProof.pin_keys(session_key_store.get_user_id(), user_hash)
        val record_keys = (LoginSaltRule.record_keys(user_hash) + session_record_keys()).distinct()
        val served = user_hash?.let { hash ->
            runCatching {
                val response = auth_api.get_user_salt(hash)
                base64_decode(response.salt) to LoginProof.read_kdf_version(response.kdf_version)
            }.getOrNull()
        }
        if (served != null) return StepUpSalt(served.first, served.second, pin_keys, record_keys)
        val stored = session_key_store.get_password_salt() ?: return null
        val kdf_version = if (login_pins.is_pinned(pin_keys)) 2 else 1
        return StepUpSalt(stored, kdf_version, pin_keys, record_keys)
    }

    private suspend fun derive_step_up_proof(password_bytes: ByteArray, step_up: StepUpSalt): ByteArray {
        seed_login_salt_record_from_session()
        LoginSaltRule.require_v1_proof_allowed(
            step_up.salt,
            step_up.kdf_version,
            login_salt_records.find(step_up.record_keys),
            LoginSaltCheck.step_up,
            account_seen_before = false,
        )
        val v1 = derive_login_hash(password_bytes, step_up.salt)
        try {
            return LoginProof.select_step_up_proof(v1, step_up.kdf_version, step_up.pin_keys, login_pins)
        } finally {
            v1.fill(0)
        }
    }

    private val pending_recovery_backup = java.util.concurrent.atomic.AtomicReference<SaveRecoveryBackupRequest?>(null)

    private suspend fun upload_recovery_backup(request: SaveRecoveryBackupRequest): Boolean {
        repeat(RECOVERY_BACKUP_ATTEMPTS) { attempt ->
            val uploaded = runCatching { recovery_api.backup(request) }
            if (uploaded.isSuccess) {
                pending_recovery_backup.set(null)
                return true
            }
            val failure = uploaded.exceptionOrNull()
            if (failure is CancellationException) throw failure
            if (attempt < RECOVERY_BACKUP_ATTEMPTS - 1) {
                delay(RECOVERY_BACKUP_RETRY_DELAY_MS * (attempt + 1))
            }
        }
        pending_recovery_backup.set(request)
        return false
    }

    suspend fun retry_recovery_backup(): Boolean {
        val pending = pending_recovery_backup.get() ?: return true
        return upload_recovery_backup(pending)
    }

    suspend fun check_username_availability(username: String, domain: String): Result<Boolean> =
        runCatching { auth_api.check_username_availability(username, domain) }

    suspend fun register(
        email: String,
        password: String,
        captcha_token: String? = null,
        remember_me: Boolean = true,
        display_name: String? = null,
    ): Result<RegisterSuccess> {
        mail_repository.pause_pending_drain()
        try {
            return register_while_drain_paused(email, password, captcha_token, remember_me, display_name)
        } finally {
            mail_repository.resume_pending_drain()
        }
    }

    private suspend fun register_while_drain_paused(
        email: String,
        password: String,
        captcha_token: String?,
        remember_me: Boolean,
        display_name: String?,
    ): Result<RegisterSuccess> = runCatching {
        val trimmed = email.trim().lowercase(java.util.Locale.ROOT)
        val at_index = trimmed.indexOf('@')
        val username = if (at_index > 0) trimmed.substring(0, at_index) else trimmed
        val email_domain = if (at_index > 0) trimmed.substring(at_index + 1) else "astermail.org"
        if (email_domain != "astermail.org" && email_domain != "aster.cx") {
            throw ApiError.ValidationError(
                listOf(context.getString(R.string.error_unsupported_email_domain)),
                "CLIENT_MESSAGE",
            )
        }
        val canonical_email = "$username@$email_domain"

        val user_hash = CryptoNative.hash_email(canonical_email)

        val salt_bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val password_bytes = password.toByteArray(Charsets.UTF_8)
        val password_hash_bytes = CryptoNative.derive_pbkdf2_hash(
            password_bytes,
            salt_bytes,
            pbkdf2_iterations,
        )
        password_bytes.fill(0)
        val password_hash_v2_bytes = LoginProof.derive_v2(password_hash_bytes)

        val identity = CryptoNative.generate_identity_keypair_struct()
        val prekey = CryptoNative.generate_identity_keypair_struct()
        val signature = CryptoNative.sign_with_identity(identity.private_key, prekey.public_key)

        val pgp_keys = generate_signup_pgp_keys(username, canonical_email, password)
            ?: throw ApiError.ValidationError(
                listOf(context.getString(R.string.error_generic)),
                "CLIENT_MESSAGE",
            )

        val recovery_codes = generate_recovery_codes()
        val recovery_key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

        val signup_data_kek = org.astermail.android.crypto.StorageWriteKey.random_data_kek()
        val vault_json = build_vault_json(
            identity_private_b64 = base64_encode(identity.private_key),
            prekey_private_b64 = base64_encode(prekey.private_key),
            recovery_codes = recovery_codes,
            pgp_private_key = pgp_keys.armored_private_key,
            data_kek_b64 = base64_encode(signup_data_kek),
        )
        val vault_plaintext = vault_json.toByteArray(Charsets.UTF_8)
        val raw_password_bytes = password.toByteArray(Charsets.UTF_8)
        val vault_envelope = CryptoNative.encrypt_vault_with_password(vault_plaintext, raw_password_bytes)
        raw_password_bytes.fill(0)
        vault_plaintext.fill(0)

        val client_pgp_key = pgp_keys.let { keys ->
            runCatching {
                val (encrypted_private_key, private_key_nonce) =
                    encrypt_pgp_private_key_for_server(keys.armored_private_key, password)
                ClientPgpKeyData(
                    fingerprint = keys.fingerprint.uppercase(java.util.Locale.ROOT),
                    key_id = keys.key_id.uppercase(java.util.Locale.ROOT),
                    public_key_armored = keys.armored_public_key,
                    encrypted_private_key = encrypted_private_key,
                    private_key_nonce = private_key_nonce,
                )
            }.getOrNull()
        }

        val vault_for_backup = backup_vault_bytes(
            org.json.JSONObject(vault_json),
            password.toByteArray(Charsets.UTF_8),
        )
        val vault_backup = encrypt_vault_backup(vault_for_backup, recovery_key)
        vault_for_backup.fill(0)
        val recovery_shares = recovery_codes.map { code -> generate_recovery_share(code, recovery_key) }
        recovery_key.fill(0)

        val register_resp = auth_api.register(
            RegisterRequest(
                username = username,
                display_name = display_name?.trim()?.takeIf { it.isNotEmpty() },
                user_hash = user_hash,
                password_hash = base64_encode(password_hash_bytes),
                password_hash_v2 = base64_encode(password_hash_v2_bytes),
                password_salt = base64_encode(salt_bytes),
                argon2_params = Argon2Params(memory = 65536, iterations = 3, parallelism = 4),
                identity_key = base64_encode(pgp_keys.armored_public_key.toByteArray(Charsets.UTF_8)),
                signed_prekey = base64_encode(prekey.public_key),
                signed_prekey_signature = base64_encode(signature),
                encrypted_vault = base64_encode(vault_envelope.encrypted_vault),
                vault_nonce = base64_encode(vault_envelope.vault_nonce),
                email_domain = email_domain,
                remember_me = remember_me,
                captcha_token = captcha_token,
                pgp_key = client_pgp_key,
                encrypted_vault_backup = vault_backup.encrypted_data,
                vault_backup_nonce = vault_backup.nonce,
                recovery_key_salt = vault_backup.salt,
                recovery_shares = recovery_shares,
                vault_format = MASTER_KEY_VAULT_FORMAT,
            ),
        )

        val access = register_resp.access_token
            ?: throw ApiError.UnknownError(context.getString(R.string.error_generic))
        val previous_user_id = session_key_store.get_user_id()
        if (previous_user_id != null && previous_user_id != register_resp.user_id) {
            SessionRefreshGate.mutex.withLock { persist_current_session_locked(null) }
            session_key_store.clear()
            runCatching {
                withTimeoutOrNull(3_000L) {
                    database.decrypted_mail_dao().clear_all()
                    database.folder_row_dao().clear_all()
                    database.message_body_dao().clear_all()
                    database.thread_snapshot_dao().clear_all()
                }
            }
            mail_repository.clear_account_data()
            cancel_all_notifications()
            runCatching { org.astermail.android.notifications.MailPollingWorker.reset_new_mail_baseline(context) }
            runCatching { org.astermail.android.notifications.NotificationDedupe.clear(context) }
        }
        install_session_tokens(access, register_resp.refresh_token, null)
        session_key_store.put(password_hash_bytes)
        session_key_store.put_passphrase(password.toByteArray(Charsets.UTF_8))
        session_key_store.put_password_salt(salt_bytes)
        session_key_store.put_user_id(register_resp.user_id)
        session_key_store.put_user_email(canonical_email)
        val stored_identity = pgp_keys.armored_private_key
        session_key_store.put_identity_key(stored_identity)
        session_key_store.put_encrypted_vault(
            base64_encode(vault_envelope.encrypted_vault),
            base64_encode(vault_envelope.vault_nonce),
        )
        absorb_data_kek(org.json.JSONObject(vault_json))
        signup_data_kek.fill(0)

        runCatching { system_folder_bootstrap.ensure_system_folders() }

        runCatching { login_salt_records.put(login_record_keys(user_hash, canonical_email), salt_bytes) }
        start_login_enrollment(
            register_resp.user_id,
            LoginProof.pin_keys(register_resp.user_id, user_hash),
            PendingLoginEnrollment(password_hash_bytes, password_hash_v2_bytes),
        )

        identity.private_key.fill(0)
        prekey.private_key.fill(0)
        signature.fill(0)
        password_hash_bytes.fill(0)
        password_hash_v2_bytes.fill(0)

        session_key_store.put_recovery_codes(recovery_codes)

        val profile = runCatching { auth_api.me() }.getOrNull()
        account_store.add_or_update(
            StoredAccount(
                id = register_resp.user_id,
                email = canonical_email,
                display_name = profile?.display_name,
                profile_color = profile?.profile_color,
                profile_picture = profile?.profile_picture,
                added_at = System.currentTimeMillis(),
            ),
            max_accounts = AccountStore.unlimited_accounts,
        )
        save_session_snapshot(register_resp.user_id)
        _active_account_id.value = register_resp.user_id
        _is_signed_in.value = true
        _session_expired.value = false
        runCatching { UnifiedPushState.clear_backend_registration(context) }
        runCatching { UnifiedPushState.sync_registration(context) }
        runCatching { org.astermail.android.notifications.PersistentPushService.start_if_enabled(context) }
        schedule_ratchet_bootstrap(bypass_debounce = true)
        load_account_keks()
        RegisterSuccess(recovery_codes = recovery_codes)
    }

    fun refresh_session_snapshot() {
        runCatching { session_key_store.get_user_id()?.let { save_session_snapshot(it) } }
    }

    private fun save_session_snapshot(account_id: String) {
        if (!current_tokens_belong_to(account_id)) return
        if (session_key_store.get_user_id()?.let { same_account(it, account_id) } == false) return
        runCatching {
            session_snapshot_store.save(
                account_id = account_id,
                token_access = token_store.access_token,
                token_refresh = token_store.refresh_token,
                csrf_token = api_client.get_csrf(),
                session_key = session_key_store.get(),
                passphrase = session_key_store.get_passphrase(),
                identity_key = session_key_store.get_identity_key(),
                encrypted_vault = session_key_store.get_encrypted_vault()?.first,
                vault_nonce = session_key_store.get_encrypted_vault()?.second,
                vault_kdf_iterations = session_key_store.get_vault_kdf_iterations(),
                password_salt = session_key_store.get_password_salt(),
                user_id = session_key_store.get_user_id(),
                user_email = session_key_store.get_user_email(),
                recovery_codes = session_key_store.get_recovery_codes(),
                previous_keys = session_key_store.get_previous_keys(),
                legacy_keks = session_key_store.get_legacy_keks(),
            )
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun try_restore_session(account_id: String): Boolean {
        mail_repository.pause_pending_drain()
        try {
            return restore_session_while_drain_paused(account_id)
        } finally {
            mail_repository.resume_pending_drain()
        }
    }

    private suspend fun restore_session_while_drain_paused(account_id: String): Boolean {
        val restored = withContext(NonCancellable) {
            SessionRefreshGate.mutex.withLock {
                if (!account_store.account_exists(account_id)) return@withLock false
                val snapshot = session_snapshot_store.load(account_id) ?: return@withLock false
                if (!same_account(session_token_owner(snapshot.token_refresh, snapshot.token_access) ?: account_id, account_id)) {
                    return@withLock false
                }
                persist_current_session_locked(api_client.get_csrf())
                if (!clear_decrypted_mail_cache_blocking()) return@withLock false
                mail_repository.clear_account_data()
                cancel_all_notifications()
                token_store.save(snapshot.token_access, snapshot.token_refresh)
                api_client.set_csrf(snapshot.csrf_token)
                api_client.invalidate_bearer_cache()
                session_key_store.clear()
                snapshot.session_key?.let { session_key_store.put(it) }
                snapshot.passphrase?.let { session_key_store.put_passphrase(it) }
                snapshot.identity_key?.let { session_key_store.put_identity_key(it) }
                snapshot.password_salt?.let { session_key_store.put_password_salt(it) }
                session_key_store.put_user_id(snapshot.user_id ?: account_id)
                snapshot.user_email?.let { session_key_store.put_user_email(it) }
                seed_login_salt_record_from_session()
                val ev = snapshot.encrypted_vault
                val vn = snapshot.vault_nonce
                if (ev != null && vn != null) {
                    session_key_store.put_encrypted_vault(ev, vn, snapshot.vault_kdf_iterations)
                }
                snapshot.recovery_codes?.let { session_key_store.put_recovery_codes(it) }
                snapshot.previous_keys?.let { session_key_store.put_previous_keys(it) }
                snapshot.legacy_keks?.let { session_key_store.put_legacy_keks(it) }
                account_store.set_current(account_id)
                true
            }
        }
        if (!restored) return false
        runCatching { try_recover_identity_key() }
        run_vault_step_inline()
        runCatching { offer_preferences_store.reset() }
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        _active_account_id.value = account_id
        _is_signed_in.value = true
        _session_expired.value = false
        background_scope.launch { runCatching { ensure_csrf_ready() } }
        background_scope.launch { runCatching { refresh_session_if_expiring() } }
        background_scope.launch { runCatching { ensure_pgp_key_published() } }
        schedule_ratchet_bootstrap(bypass_debounce = true)
        load_account_keks()
        background_scope.launch { runCatching { system_folder_bootstrap.ensure_system_folders() } }
        return true
    }

    suspend fun ensure_csrf_ready(): Boolean {
        if (!_is_signed_in.value) return true
        if (api_client.get_csrf() != null) return true
        return when (try_refresh_session()) {
            RefreshOutcome.Success -> api_client.get_csrf() != null
            RefreshOutcome.AuthFailed, RefreshOutcome.Transient -> false
        }
    }

    fun has_stored_session(account_id: String): Boolean = session_snapshot_store.has(account_id)

    suspend fun change_password(
        current_password: String,
        new_password: String,
    ): Result<org.astermail.android.mail.SentMailResealSummary> = runCatching {
        require(new_password.length >= 12) { "new password must be at least 12 characters" }
        require(new_password.length <= 128) { "new password must be at most 128 characters" }

        val current_password_bytes = current_password.toByteArray(Charsets.UTF_8)
        val new_password_bytes = new_password.toByteArray(Charsets.UTF_8)

        val step_up = step_up_salt()
            ?: throw ApiError.UnknownError(context.getString(R.string.session_expired_sign_in))
        val stored_salt = step_up.salt

        val current_password_hash = try {
            derive_step_up_proof(current_password_bytes, step_up)
        } catch (refused: SecurityException) {
            current_password_bytes.fill(0)
            new_password_bytes.fill(0)
            throw refused
        }
        val account_on_v2 = login_pins.is_pinned(step_up.pin_keys)

        vault_commit_mutex.lock()
        var vault_lock_held = true
        val release_vault_lock = {
            if (vault_lock_held) {
                vault_lock_held = false
                vault_commit_mutex.unlock()
            }
        }
        try {
            val server_vault = try {
                keys_api.fetch_current_vault()
            } catch (cancelled: CancellationException) {
                current_password_bytes.fill(0)
                new_password_bytes.fill(0)
                throw cancelled
            } catch (_: Throwable) {
                CurrentVaultResult.Unavailable
            }
            val (encrypted_vault_b64, vault_nonce_b64) = when (server_vault) {
                is CurrentVaultResult.Available -> server_vault.encrypted_vault to server_vault.vault_nonce
                CurrentVaultResult.Missing -> session_key_store.get_encrypted_vault()
                CurrentVaultResult.Unavailable -> null
            } ?: run {
                current_password_bytes.fill(0)
                new_password_bytes.fill(0)
                throw if (server_vault == CurrentVaultResult.Unavailable) {
                    ApiError.NetworkError
                } else {
                    ApiError.UnknownError(context.getString(R.string.session_unavailable_sign_in_again))
                }
            }

            val reported_iterations = when (server_vault) {
                is CurrentVaultResult.Available -> server_vault.vault_kdf_iterations
                else -> session_key_store.get_vault_kdf_iterations()
            }
            val vault_plain = try {
                CryptoNative.unwrap_vault(
                    base64_decode(encrypted_vault_b64),
                    base64_decode(vault_nonce_b64),
                    current_password_bytes,
                    reported_iterations,
                ).plaintext
            } catch (cancelled: CancellationException) {
                current_password_bytes.fill(0)
                new_password_bytes.fill(0)
                throw cancelled
            } catch (_: Throwable) {
                current_password_bytes.fill(0)
                new_password_bytes.fill(0)
                throw ApiError.ValidationError(
                    listOf(context.getString(R.string.current_password_incorrect)),
                    "CLIENT_MESSAGE",
                )
            }

            val vault_obj = org.json.JSONObject(String(vault_plain, Charsets.UTF_8))
            vault_plain.fill(0)
            org.astermail.android.crypto.storage_rotation.preserve_vault_fields(
                org.json.JSONObject(vault_obj.toString()),
                vault_obj,
                org.astermail.android.crypto.storage_rotation.local_storage_keys(session_key_store),
                false,
                java.time.Instant.now().toString(),
            )

            val sent_mail_conversion = password_change_sent_mail.convert_before_change(
                vault_obj.optString("identity_key", "").ifBlank { vault_obj.optString("identity_private_key", "") },
                current_password_bytes,
            )

            val current_identity = vault_obj.optString("identity_private_key", "")
            if (current_identity.isNotBlank()) {
                val previous = vault_obj.optJSONArray("previous_keys") ?: org.json.JSONArray()
                val rotated = org.json.JSONArray().put(current_identity)
                for (i in 0 until previous.length()) {
                    if (rotated.length() >= 10) break
                    rotated.put(previous.getString(i))
                }
                vault_obj.put("previous_keys", rotated)
            }

            val preserved_data_kek = try {
                ensure_master_key_vault(vault_obj, current_password_bytes)
            } catch (_: UnsupportedVaultKdfException) {
                current_password_bytes.fill(0)
                new_password_bytes.fill(0)
                throw ApiError.ValidationError(
                    listOf(context.getString(R.string.password_change_web_required)),
                    "CLIENT_MESSAGE",
                )
            }

            val outgoing_password_key = CryptoNative.derive_storage_key(current_password_bytes)
            try {
                retain_password_storage_key(vault_obj, outgoing_password_key, ::base64_encode)
            } finally {
                outgoing_password_key.fill(0)
            }

            org.astermail.android.mail.ratchet.fold_local_ratchet_keys_into_vault(vault_obj, session_key_store)
            val vault_key_fingerprints = org.astermail.android.mail.ratchet.collect_vault_key_fingerprints(vault_obj)

            val updated_vault_bytes = vault_obj.toString().toByteArray(Charsets.UTF_8)
            var vault_iterations = VaultKdf.LEVEL2_ITERATIONS
            var new_envelope = CryptoNative.encrypt_vault_with_password(
                updated_vault_bytes,
                new_password_bytes,
                vault_iterations,
            )
            updated_vault_bytes.fill(0)

            val new_salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val new_password_hash = CryptoNative.derive_pbkdf2_hash(
                new_password_bytes, new_salt, pbkdf2_iterations,
            )
            val new_password_hash_v2 = LoginProof.derive_v2(new_password_hash)

            withContext(NonCancellable) {
                val request = ChangePasswordRequest(
                    current_password_hash = base64_encode(current_password_hash),
                    new_password_hash = base64_encode(new_password_hash),
                    new_password_hash_v2 = base64_encode(new_password_hash_v2),
                    new_password_salt = base64_encode(new_salt),
                    new_encrypted_vault = base64_encode(new_envelope.encrypted_vault),
                    new_vault_nonce = base64_encode(new_envelope.vault_nonce),
                    vault_format = MASTER_KEY_VAULT_FORMAT,
                    vault_key_fingerprints = vault_key_fingerprints.takeIf { it.isNotEmpty() },
                    vault_kdf_iterations = VaultKdf.to_server(vault_iterations),
                )
                val response = try {
                    settings_api.change_password(request)
                } catch (error: ApiError.Conflict) {
                    if (!org.astermail.android.crypto.storage_rotation.is_level2_not_admitted(error)) throw error
                    vault_iterations = VaultKdf.DEFAULT_ITERATIONS
                    val fallback_bytes = vault_obj.toString().toByteArray(Charsets.UTF_8)
                    new_envelope = try {
                        CryptoNative.encrypt_vault_with_password(fallback_bytes, new_password_bytes, vault_iterations)
                    } finally {
                        fallback_bytes.fill(0)
                    }
                    settings_api.change_password(
                        request.copy(
                            new_encrypted_vault = base64_encode(new_envelope.encrypted_vault),
                            new_vault_nonce = base64_encode(new_envelope.vault_nonce),
                            vault_kdf_iterations = null,
                        ),
                    )
                }

                session_key_store.put_data_kek(preserved_data_kek)
                session_key_store.put_legacy_keks(
                    append_storage_keks(session_key_store.get_legacy_keks().orEmpty(), vault_legacy_kek_values(vault_obj)),
                )
                session_key_store.put(if (account_on_v2) new_password_hash_v2 else new_password_hash)
                session_key_store.put_passphrase(new_password_bytes)
                session_key_store.put_password_salt(new_salt)
                runCatching { login_salt_records.put(step_up.record_keys, new_salt) }
                session_key_store.put_encrypted_vault(
                    base64_encode(new_envelope.encrypted_vault),
                    base64_encode(new_envelope.vault_nonce),
                    vault_iterations,
                )
                release_vault_lock()
                schedule_storage_rekey()

                SessionRefreshGate.mutex.withLock {
                    response.csrf_token?.let { api_client.set_csrf(it) }
                    response.access_token?.let {
                        token_store.save(it, response.refresh_token ?: token_store.refresh_token ?: it)
                        api_client.invalidate_bearer_cache()
                    }
                }

                val pgp_rewrapped = runCatching { rewrap_server_pgp_key(new_password) }.isSuccess

                runCatching { session_key_store.get_user_id()?.let { save_session_snapshot(it) } }

                val reseal = password_change_sent_mail.reseal_after_change(
                    sent_mail_conversion,
                    current_password_bytes,
                    new_password_bytes,
                    pgp_rewrapped,
                )

                mail_repository.clear_caches()
                database.decrypted_mail_dao().clear_all()
                database.folder_row_dao().clear_all()
                database.message_body_dao().clear_all()
                database.thread_snapshot_dao().clear_all()
                session_key_store.get_user_email()?.let { trusted_device_store.clear(it) }

                current_password_hash.fill(0)
                new_password_hash.fill(0)
                new_password_hash_v2.fill(0)
                current_password_bytes.fill(0)
                new_password_bytes.fill(0)
                stored_salt.fill(0)
                preserved_data_kek.fill(0)
                reseal
            }
        } finally {
            release_vault_lock()
        }
    }

    private fun cancel_all_notifications() {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.cancelAll()
        }
    }

    suspend fun save_recovery_email(email: String): Result<Unit> = runCatching {
        val normalized = org.astermail.android.recovery.normalize_recovery_email(email)
        val identity_key = session_key_store.get_identity_key()
            ?: throw IllegalStateException(context.getString(R.string.something_went_wrong))
        val encrypted = org.astermail.android.recovery.encrypt_recovery_email(normalized, identity_key)
        recovery_email_api.save(
            org.astermail.android.api.recovery_email.SaveRecoveryEmailRequest(
                encrypted_email = encrypted.ciphertext_b64,
                email_nonce = encrypted.nonce_b64,
                email_hash = org.astermail.android.recovery.hash_recovery_email(normalized),
                plaintext_email = normalized,
                password_hash = null,
                totp_code = null,
            ),
        )
        Unit
    }

    suspend fun logout(): Result<Unit> = sign_out_internal(remove_account = true)

    suspend fun force_sign_out(expected_account_id: String? = null): Result<Unit> {
        val previous_id = session_key_store.get_user_id()
        if (expected_account_id != null && !same_account(expected_account_id, previous_id)) return Result.success(Unit)
        val result = sign_out_internal(remove_account = false, expected_account_id = expected_account_id)
        if (!_is_signed_in.value) {
            _session_expired.value = true
        } else {
            _active_account_id.value?.takeIf { it != previous_id }?.let { _forced_account_switch.tryEmit(it) }
        }
        return result
    }

    suspend fun logout_all(): Result<Unit> = runCatching {
        var remaining = account_store.count() + 1
        while (remaining-- > 0) {
            sign_out_internal(remove_account = true)
            if (!_is_signed_in.value) break
        }
        runCatching { org.astermail.android.mail.clear_folder_cache_stats(context, null) }
        _active_account_id.value = null
        _is_signed_in.value = false
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private suspend fun sign_out_internal(
        remove_account: Boolean,
        expected_account_id: String? = null,
    ): Result<Unit> = runCatching {
        val current_id = session_key_store.get_user_id()
        if (expected_account_id != null && !same_account(expected_account_id, current_id)) return@runCatching
        val current_email = session_key_store.get_user_email()
        if (remove_account) {
            current_email?.let { runCatching { trusted_device_store.clear(it) } }
            try {
                withTimeoutOrNull(5_000L) {
                    org.astermail.android.api.billing.BillingApiImpl(api_client).unlink_account_device()
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
            }
        } else {
            release_account_slot_with_current_tokens()
        }
        try {
            withTimeoutOrNull(5_000L) {
                auth_api.logout()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
        }
        val cleared = SessionRefreshGate.mutex.withLock {
            if (session_key_store.get_user_id() != current_id) return@withLock false
            seed_login_salt_record_from_session()
            runCatching { token_store.clear() }
            runCatching { api_client.clear_csrf() }
            runCatching { api_client.invalidate_bearer_cache() }
            runCatching { session_key_store.clear() }
            runCatching { storage_rekey_service.forget_session() }
            true
        }
        if (!cleared) return@runCatching
        runCatching { org.astermail.android.notifications.PersistentPushService.stop(context) }
        runCatching { org.astermail.android.folders.folder_lock_store.lock_all() }
        runCatching { mail_repository.clear_account_data() }
        runCatching { current_id?.let { identity_pins.get().clear_account(it) } }
        runCatching { org.astermail.android.util.purge_sensitive_export_files(context, 0L) }
        runCatching { org.astermail.android.ui.mail.evict_email_image_cache() }
        runCatching { wipe_decrypted_caches(context.cacheDir) }
        runCatching { org.astermail.android.billing.AttachmentLimits.reset() }
        runCatching { org.astermail.android.billing.AvailablePlansCache.reset() }
        runCatching { org.astermail.android.billing.PlanLimitsCache.reset() }
        runCatching { theme_store.clear() }
        runCatching { org.astermail.android.ui.theme.custom_theme_image.delete(context) }
        runCatching { offer_preferences_store.reset() }
        runCatching { org.astermail.android.ui.compose.compose_seed_store.clear(context) }
        runCatching { org.astermail.android.notifications.MutedFolderSync.reset(context) }
        runCatching { org.astermail.android.notifications.QuietHoursSync.reset(context) }
        runCatching { org.astermail.android.imports.ExternalAccountHealthStore.reset(context) }
        cancel_all_notifications()
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching {
            org.astermail.android.mail.AsterProfileResolverHolder.shared?.clear()
            org.astermail.android.mail.OwnAddressAvatars.clear()
            org.astermail.android.security.SenderTrustStore.clear()
            org.astermail.android.contacts.ContactPhotoDirectory.clear()
            org.astermail.android.notifications.NotificationAvatars.clear()
        }
        runCatching { database.decrypted_mail_dao().clear_all() }
        runCatching { database.folder_row_dao().clear_all() }
        runCatching { database.message_body_dao().clear_all() }
        runCatching { database.thread_snapshot_dao().clear_all() }
        current_id?.let { id ->
            runCatching { database.contact_cache_dao().clear_for_account(id) }
            runCatching { database.contact_group_cache_dao().clear_for_account(id) }
        }
        current_id?.let { runCatching { org.astermail.android.mail.clear_folder_cache_stats(context, it) } }
        if (remove_account) {
            val outbox_ids = runCatching {
                current_id?.let { id -> database.pending_send_dao().get_for_account(id).map { it.id } }
            }.getOrNull()
            runCatching {
                current_id?.let { database.pending_send_dao().clear_for_account(it) }
                    ?: database.pending_send_dao().clear_all()
            }
            runCatching {
                wipe_outbox_attachments(context.filesDir, if (current_id == null) null else outbox_ids.orEmpty())
            }
            current_id?.let { runCatching { mail_repository.clear_pending_actions(it) } }
        }
        runCatching {
            wipe_orphaned_outbox_attachments(context.filesDir, database.pending_send_dao().get_all().map { it.id })
        }
        if (current_id != null) {
            runCatching { session_snapshot_store.remove(current_id) }
            if (remove_account) runCatching { account_store.remove(current_id) }
            if (remove_account) {
                runCatching { org.astermail.android.ui.drawer.folder_expansion_store.clear(context, current_id) }
            }
        }
        val candidates = runCatching {
            account_store.get_all()
                .filter { it.id != current_id && session_snapshot_store.has(it.id) }
        }.getOrDefault(emptyList())
        for (candidate in candidates) {
            val restored = runCatching { try_restore_session(candidate.id) }.getOrDefault(false)
            if (restored) return@runCatching
        }
        _active_account_id.value = null
        _is_signed_in.value = false
    }

    private var uid_update_attempted_for: String? = null

    private fun uid_form(address: String): String {
        val lowered = address.trim().lowercase(java.util.Locale.ROOT)
        val at = lowered.lastIndexOf('@')

        if (at <= 0) return lowered

        return lowered.substring(0, at).replace(".", "") + lowered.substring(at)
    }

    suspend fun refresh_profile(): Result<Unit> = runCatching {
        val profile = auth_api.me()
        val email = adopt_server_email(profile)?.let { uid_form(it) }
        absorb_profile(profile)
        if (profile.pgp_uid_update_required &&
            !email.isNullOrBlank() &&
            uid_update_attempted_for != email
        ) {
            uid_update_attempted_for = email
            val uid_updated = runCatching {
                add_address_to_identity_key(email, profile.display_name.orEmpty())
            }.getOrDefault(false)
            if (!uid_updated) uid_update_attempted_for = null
        }
        val account_email = profile.email?.trim()?.takeIf { profile_matches_session(profile) }
        if (!account_email.isNullOrBlank()) {
            runCatching { heal_identity_key_account_addresses(account_email, profile.display_name.orEmpty()) }
        }
    }

    private fun profile_matches_session(
        profile: org.astermail.android.api.auth.UserInfo,
    ): Boolean {
        val session_id = session_key_store.get_user_id() ?: return true

        return profile.user_id.isBlank() || profile.user_id == session_id
    }

    private fun adopt_server_email(profile: org.astermail.android.api.auth.UserInfo): String? {
        val server_email = profile.email?.trim()?.takeIf { it.isNotBlank() }
        val stored_email = session_key_store.get_user_email()

        if (server_email == null) return stored_email
        if (server_email.equals(stored_email, ignoreCase = true)) return stored_email
        if (!profile_matches_session(profile)) return stored_email

        session_key_store.put_user_email(server_email)
        refresh_session_snapshot()

        return server_email
    }

    fun absorb_profile(profile: org.astermail.android.api.auth.UserInfo) {
        val current_id = session_key_store.get_user_id() ?: profile.user_id
        val server_email = profile.email?.trim()
            ?.takeIf { it.isNotBlank() && profile_matches_session(profile) }
        val email = server_email ?: session_key_store.get_user_email() ?: return
        if (account_store.get_current_id() != current_id) return
        if (profile.user_id.isNotBlank() && !same_account(profile.user_id, current_id)) return
        account_store.add_or_update(
            StoredAccount(
                id = current_id,
                email = email,
                display_name = profile.display_name,
                profile_color = profile.profile_color,
                profile_picture = profile.profile_picture,
                added_at = System.currentTimeMillis(),
            ),
        )
        org.astermail.android.mail.AsterProfileResolverHolder.shared?.prime(
            email = email,
            display_name = profile.display_name,
            profile_picture = profile.profile_picture,
            profile_color = profile.profile_color,
        )
    }

    suspend fun restore_inactive_key_sets(old_password: String): Int {
        val sets = runCatching { recovery_api.list_inactive_key_sets().inactive_key_sets }
            .getOrDefault(emptyList())
        if (sets.isEmpty()) return 0

        val user_id = session_key_store.get_user_id() ?: return 0
        val stored_vault = session_key_store.get_encrypted_vault() ?: return 0
        val passphrase = session_key_store.get_passphrase() ?: return 0

        try {
            val vault_plain = runCatching {
                CryptoNative.unwrap_vault(
                    base64_decode(stored_vault.first),
                    base64_decode(stored_vault.second),
                    passphrase,
                    session_key_store.get_vault_kdf_iterations(),
                ).plaintext
            }.getOrNull() ?: return 0
            val vault_obj = runCatching {
                org.json.JSONObject(String(vault_plain, Charsets.UTF_8))
            }.getOrNull()
            vault_plain.fill(0)
            if (vault_obj == null) return 0

            val old_password_bytes = old_password.toByteArray(Charsets.UTF_8)
            val recovered_keks = mutableListOf<String>()
            val recovered_ratchet = mutableListOf<org.json.JSONObject>()
            val unlocked = mutableListOf<String>()
            val old_vaults = mutableListOf<org.json.JSONObject>()

            try {
                for (set in sets) {
                    val fetched = runCatching {
                        recovery_api.fetch_inactive_key_set(FetchInactiveKeySetRequest(set.id))
                    }.getOrNull() ?: continue

                    val old_plain = runCatching {
                        CryptoNative.unwrap_vault(
                            base64_decode(fetched.encrypted_vault),
                            base64_decode(fetched.vault_nonce),
                            old_password_bytes,
                            fetched.vault_kdf_iterations,
                        ).plaintext
                    }.getOrNull() ?: continue

                    val old_vault = runCatching {
                        org.json.JSONObject(String(old_plain, Charsets.UTF_8))
                    }.getOrNull()
                    old_plain.fill(0)
                    if (old_vault == null) continue

                    val derived = runCatching {
                        val raw = CryptoNative.derive_storage_key(old_password_bytes)
                        val encoded = base64_encode(raw)
                        raw.fill(0)
                        encoded
                    }.getOrNull()

                    recovered_keks.addAll(harvest_storage_keks(old_vault, derived))
                    recovered_ratchet.addAll(retain_previous_ratchet_keys(old_vault))
                    unlocked.add(set.id)
                    old_vaults.add(old_vault)
                }
            } finally {
                old_password_bytes.fill(0)
            }

            if (unlocked.isEmpty()) return 0

            val old_password_chars = old_password.toCharArray()
            val current_chars = passphrase_chars(passphrase)
            val identity_keys = try {
                merge_recovered_identity_keys(vault_obj, old_vaults, old_password_chars, current_chars)
            } finally {
                old_password_chars.fill(' ')
                current_chars.fill(' ')
            }
            val committed = commit_recovered_keys(
                user_id = user_id,
                passphrase = passphrase,
                vault_obj = vault_obj,
                identity_keys = identity_keys,
                recovered_keks = recovered_keks,
                recovered_ratchet = recovered_ratchet,
                base_encrypted_vault = stored_vault.first,
            )
            if (!committed.written || committed.dropped_any) return 0

            val absorbed = unlocked.filterIndexed { index, _ -> identity_keys.absorbed.getOrElse(index) { false } }
            absorbed.forEach { id ->
                runCatching { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest(id)) }
            }

            return absorbed.size
        } finally {
            passphrase.fill(0)
        }
    }

    suspend fun restore_inactive_key_sets_with_code(code: String): CodeRestoreResult {
        val code_hash = hash_recovery_code(canonicalize_recovery_code(code))
        val key_sets = recovery_api
            .unlock_inactive_key_sets_with_code(UnlockInactiveWithCodeRequest(code_hash))
            .key_sets
        if (key_sets.isEmpty()) return CodeRestoreResult()

        val user_id = session_key_store.get_user_id() ?: return CodeRestoreResult()
        val stored_vault = session_key_store.get_encrypted_vault() ?: return CodeRestoreResult()
        val passphrase = session_key_store.get_passphrase() ?: return CodeRestoreResult()

        try {
            val vault_plain = runCatching {
                CryptoNative.unwrap_vault(
                    base64_decode(stored_vault.first),
                    base64_decode(stored_vault.second),
                    passphrase,
                    session_key_store.get_vault_kdf_iterations(),
                ).plaintext
            }.getOrNull() ?: return CodeRestoreResult()
            val vault_obj = runCatching {
                org.json.JSONObject(String(vault_plain, Charsets.UTF_8))
            }.getOrNull()
            vault_plain.fill(0)
            if (vault_obj == null) return CodeRestoreResult()

            val recovered_keks = mutableListOf<String>()
            val recovered_ratchet = mutableListOf<org.json.JSONObject>()
            val opened = mutableListOf<String>()
            val password_bound = mutableListOf<Boolean>()
            val old_vaults = mutableListOf<org.json.JSONObject>()
            val unlocked_keys = mutableMapOf<String, String>()

            for (key_set in key_sets) {
                val backup = runCatching {
                    open_recovery_vault_backup(
                        code = code,
                        encrypted_recovery_key = base64_decode(key_set.encrypted_recovery_key),
                        recovery_key_nonce = base64_decode(key_set.recovery_key_nonce),
                        code_salt = base64_decode(key_set.code_salt),
                        encrypted_vault_backup = base64_decode(key_set.encrypted_vault_backup),
                        vault_backup_nonce = base64_decode(key_set.vault_backup_nonce),
                        recovery_key_salt = base64_decode(key_set.recovery_key_salt),
                    )
                }.getOrNull() ?: continue

                unlocked_keys.putAll(read_unlocked_keys(backup))
                val old_vault = strip_backup_fields(backup)
                recovered_keks.addAll(harvest_storage_keks(old_vault, null))
                recovered_ratchet.addAll(retain_previous_ratchet_keys(old_vault))
                opened.add(key_set.inactive_vault_id)
                password_bound.add(!carries_master_key(old_vault))
                old_vaults.add(old_vault)
            }

            if (opened.isEmpty()) return CodeRestoreResult()

            val current_chars = passphrase_chars(passphrase)
            val identity_keys = try {
                merge_identity_keys_with(
                    vault_obj,
                    old_vaults,
                    relock_with_unlocked_keys(unlocked_keys, current_chars),
                )
            } finally {
                current_chars.fill('\u0000')
                unlocked_keys.clear()
            }
            val committed = commit_recovered_keys(
                user_id = user_id,
                passphrase = passphrase,
                vault_obj = vault_obj,
                identity_keys = identity_keys,
                recovered_keks = recovered_keks,
                recovered_ratchet = recovered_ratchet,
                base_encrypted_vault = stored_vault.first,
            )
            if (!committed.written) return CodeRestoreResult(restored = 0, incomplete = opened.size)

            val absorbed = if (committed.dropped_any) {
                emptyList()
            } else {
                opened.filterIndexed { index, _ ->
                    identity_keys.absorbed.getOrElse(index) { false } && !password_bound[index]
                }
            }
            absorbed.forEach { id ->
                runCatching { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest(id)) }
            }

            return CodeRestoreResult(restored = opened.size, incomplete = opened.size - absorbed.size)
        } finally {
            passphrase.fill(0)
        }
    }

    suspend fun commit_recovered_keys(
        user_id: String,
        passphrase: ByteArray,
        vault_obj: org.json.JSONObject,
        identity_keys: RecoveredIdentityKeys,
        recovered_keks: List<String>,
        recovered_ratchet: List<org.json.JSONObject>,
        base_encrypted_vault: String,
    ): RecoveredKeyCommitResult {
        if (session_key_store.get_user_id() != user_id) return RECOVERED_KEYS_NOT_WRITTEN
        val local_vault = session_key_store.get_encrypted_vault()
        if (local_vault == null || !same_vault_ciphertext(local_vault.first, base_encrypted_vault)) {
            return RECOVERED_KEYS_NOT_WRITTEN
        }
        var merged_keks: MergedLegacyKeks? = null
        var merged_ratchet: MergedRatchetKeys? = null
        val outcome = vault_updates.with_passphrase(passphrase).update { draft ->
            if (!same_vault_ciphertext(draft.snapshot.encrypted_vault, base_encrypted_vault)) {
                return@update org.astermail.android.crypto.storage_rotation.VaultEdit.ABORT
            }
            val vault = draft.vault
            val keks = merge_legacy_keks_reporting(
                vault.optJSONArray("legacy_keks"),
                recovered_keks,
                java.time.Instant.now().toString(),
            )
            vault.put("previous_keys", org.json.JSONArray(identity_keys.previous_keys))
            vault.put("legacy_identity_keys", org.json.JSONArray(identity_keys.legacy_identity_keys))
            vault.put("legacy_keks", keks.keks)
            val ratchet = merge_previous_ratchet_keys_reporting(
                vault.optJSONArray("ratchet_previous_keys"),
                recovered_ratchet,
            )
            vault.put("ratchet_previous_keys", ratchet.keys)
            merged_keks = keks
            merged_ratchet = ratchet
            org.astermail.android.crypto.storage_rotation.VaultEdit.CHANGED
        }
        return when (outcome) {
            is org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome.Written -> {
                val keks = merged_keks ?: return RECOVERED_KEYS_NOT_WRITTEN
                val ratchet = merged_ratchet ?: return RECOVERED_KEYS_NOT_WRITTEN
                absorb_previous_keys_and_keks(outcome.vault)
                absorb_recovered_ratchet_keys(ratchet.keys)
                RecoveredKeyCommitResult(
                    written = true,
                    dropped_keks = keks.dropped,
                    dropped_ratchet_keys = ratchet.dropped,
                )
            }
            is org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome.Failed ->
                if (outcome.reason == "encrypt_failed" || outcome.reason == "roundtrip_failed") {
                    RECOVERED_KEYS_ROUNDTRIP_FAILED
                } else {
                    RECOVERED_KEYS_NOT_WRITTEN
                }
            else -> RECOVERED_KEYS_NOT_WRITTEN
        }
    }

    private fun absorb_recovered_ratchet_keys(vault_ratchet_keys: org.json.JSONArray) {
        org.astermail.android.mail.ratchet.merge_retained_ratchet_keys(
            session_key_store.get_ratchet_previous_keys_json(),
            vault_ratchet_keys.toString(),
            limit = Int.MAX_VALUE,
        )?.let { session_key_store.put_ratchet_previous_keys(it) }
    }

    private fun absorb_previous_keys_and_keks(vault_obj: org.json.JSONObject) {
        if (vault_obj.has("previous_keys") || vault_obj.has("legacy_identity_keys")) {
            session_key_store.put_previous_keys(vault_older_identity_keys(vault_obj))
        }
        vault_obj.optJSONArray("legacy_keks")?.let { array ->
            val keks = (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.optString("k", "")?.takeIf { it.isNotBlank() }
            }
            if (keks.isNotEmpty()) session_key_store.put_legacy_keks(keks)
        }
    }

    private fun ensure_master_key_vault(
        vault_obj: org.json.JSONObject,
        current_password_bytes: ByteArray,
    ): ByteArray =
        ensure_master_key_vault(
            vault_obj = vault_obj,
            derive_storage_key = { CryptoNative.derive_storage_key(current_password_bytes) },
            encode = ::base64_encode,
            decode = ::base64_decode,
            generate = { org.astermail.android.crypto.StorageWriteKey.random_data_kek() },
            extra_password_keys = {
                val salts = listOfNotNull(session_key_store.get_password_salt()) +
                    session_key_store.get_retired_password_salts()
                try {
                    org.astermail.android.crypto.storage_rotation.storage_password_keys(current_password_bytes, salts)
                } finally {
                    salts.forEach { it.fill(0) }
                }
            },
        )

    private fun schedule_storage_rekey() {
        runCatching { org.astermail.android.crypto.storage_rotation.StorageRekeyWorker.enqueue(context) }
    }

    private fun absorb_data_kek(vault_obj: org.json.JSONObject) {
        session_key_store.put_storage_kdf_version(read_storage_kdf_version(vault_obj))
        val data_kek = vault_obj.optString("data_kek", "")
        if (data_kek.isBlank()) return
        runCatching {
            val decoded = base64_decode(data_kek)
            if (decoded.size == 32) session_key_store.put_data_kek(decoded)
        }
        val current = session_key_store.get_legacy_keks().orEmpty()
        if (!current.contains(data_kek)) {
            session_key_store.put_legacy_keks(listOf(data_kek) + current)
        }
    }

    private suspend fun absorb_stretched_storage_keys(vault_obj: org.json.JSONObject, passphrase: ByteArray) {
        if (!is_stretched_or_unrecognized(read_storage_kdf_version(vault_obj))) return
        val passphrase_copy = passphrase.copyOf()
        val extra = try {
            withContext(Dispatchers.Default) {
                stretched_storage_keks_to_absorb(vault_obj, passphrase_copy, ::base64_encode)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        } finally {
            passphrase_copy.fill(0)
        }
        if (extra.isEmpty()) return
        val current = session_key_store.get_legacy_keks().orEmpty()
        val merged = append_storage_keks(current, extra)
        if (merged != current) session_key_store.put_legacy_keks(merged)
    }

    suspend fun add_address_to_identity_key(new_address: String, display_name: String): Boolean =
        withContext(Dispatchers.Default) {
            add_address_to_identity_key_blocking(new_address, display_name)
        }

    private suspend fun add_address_to_identity_key_blocking(
        new_address: String,
        display_name: String,
    ): Boolean {
        val identity_key = session_key_store.get_identity_key() ?: return true
        if (!identity_key.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return true

        val passphrase_bytes = session_key_store.get_passphrase() ?: return false
        try {
            val passphrase = passphrase_chars(passphrase_bytes)
            try {
                val updated = org.astermail.android.crypto.add_address_to_pgp_key(
                    identity_key,
                    passphrase,
                    display_name,
                    new_address,
                ) ?: run {
                    if (!org.astermail.android.crypto.pgp_key_covers_address(
                            identity_key,
                            new_address,
                        )
                    ) {
                        return false
                    }

                    republish_pgp_key_with_password(identity_key, passphrase)

                    return true
                }

                val stored = withContext(NonCancellable) {
                    if (!store_identity_key_in_vault(updated, passphrase_bytes)) {
                        return@withContext false
                    }
                    session_key_store.put_identity_key(updated)
                    true
                }

                if (!stored) return false

                republish_pgp_key_with_password(updated, passphrase)
                return true
            } finally {
                passphrase.fill(' ')
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return false
        } finally {
            passphrase_bytes.fill(0)
        }
    }

    private suspend fun store_identity_key_in_vault(
        identity_key: String,
        passphrase: ByteArray,
    ): Boolean {
        val outcome = vault_updates.update_identity(vault_updates.with_passphrase(passphrase)) { draft ->
            replace_vault_identity_key(draft.vault, identity_key)
            org.astermail.android.crypto.storage_rotation.VaultEdit.CHANGED
        }
        return outcome is org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome.Written
    }

    fun identity_keys_present(): Boolean =
        session_key_store.get_identity_key() != null && session_key_store.has_ratchet_keys()

    fun try_recover_identity_key(): Boolean {
        if (identity_keys_present()) return true
        val identity_already_present = session_key_store.get_identity_key() != null
        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault() ?: return identity_already_present
        val passphrase = session_key_store.get_passphrase() ?: return identity_already_present
        return try {
            val vault_plain = CryptoNative.unwrap_vault(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
                session_key_store.get_vault_kdf_iterations(),
            ).plaintext
            val vault_json = String(vault_plain, Charsets.UTF_8)
            vault_plain.fill(0)
            val vault_obj = org.json.JSONObject(vault_json)
            val identity_key = vault_obj.optString("identity_key", "")
                .ifBlank { vault_obj.optString("identity_private_key", "") }
            if (identity_key.isNotBlank() && !identity_already_present) {
                session_key_store.put_identity_key(identity_key)
                absorb_previous_keys_and_keks(vault_obj)
            }
            absorb_data_kek(vault_obj)
            extract_ratchet_keys(vault_obj)
            schedule_storage_rekey()
            identity_already_present || identity_key.isNotBlank()
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("AuthRepository", "identity recovery failed: ${t.javaClass.simpleName}")
            false
        } finally {
            passphrase.fill(0)
        }
    }

    suspend fun try_refresh_vault_keys(): Boolean {
        return try {
            val vault = auth_api.get_vault()
            session_key_store.put_encrypted_vault(
                vault.encrypted_vault,
                vault.vault_nonce,
                reported_vault_kdf(vault.vault_kdf_iterations),
            )
            val passphrase = session_key_store.get_passphrase() ?: return false
            try {
                val vault_plain = CryptoNative.unwrap_vault(
                    base64_decode(vault.encrypted_vault),
                    base64_decode(vault.vault_nonce),
                    passphrase,
                    vault.vault_kdf_iterations,
                ).plaintext
                val vault_json = String(vault_plain, Charsets.UTF_8)
                vault_plain.fill(0)
                val vault_obj = org.json.JSONObject(vault_json)
                val before = vault_key_snapshot(session_key_store)
                val new_identity_key = vault_obj.optString("identity_key", "")
                    .ifBlank { vault_obj.optString("identity_private_key", "") }
                if (new_identity_key.isNotBlank()) {
                    session_key_store.put_identity_key(new_identity_key)
                }
                absorb_previous_keys_and_keks(vault_obj)
                absorb_data_kek(vault_obj)
                extract_ratchet_keys(vault_obj)
                absorb_stretched_storage_keys(vault_obj, passphrase)
                vault_keys_changed(before, vault_key_snapshot(session_key_store))
            } finally {
                passphrase.fill(0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun delete_account(password: String, totp_code: String? = null): Result<Unit> = runCatching {
        require(password.isNotBlank()) { "password required" }
        val password_hash = derive_password_hash_b64(password)
            ?: throw ApiError.UnknownError(context.getString(R.string.session_expired_sign_in))
        auth_api.delete_account(
            DeleteAccountRequest(
                password_hash = password_hash,
                totp_code = totp_code?.takeIf { it.isNotBlank() },
            ),
        )
        val current_id = session_key_store.get_user_id()
        val current_email = session_key_store.get_user_email()
        runCatching {
            login_pins.unpin(LoginProof.pin_keys(current_id, current_email?.let { CryptoNative.hash_email(it) }))
        }
        runCatching { login_salt_records.remove(login_record_keys(null, current_email)) }
        SessionRefreshGate.mutex.withLock {
            token_store.clear()
            api_client.clear_csrf()
            api_client.invalidate_bearer_cache()
            session_key_store.clear()
        }
        org.astermail.android.folders.folder_lock_store.lock_all()
        mail_repository.clear_account_data()
        runCatching { current_id?.let { identity_pins.get().clear_account(it) } }
        runCatching { org.astermail.android.util.purge_sensitive_export_files(context, 0L) }
        runCatching { org.astermail.android.ui.mail.evict_email_image_cache() }
        runCatching { wipe_decrypted_caches(context.cacheDir) }
        runCatching { theme_store.clear() }
        runCatching { org.astermail.android.ui.theme.custom_theme_image.delete(context) }
        runCatching { offer_preferences_store.reset() }
        runCatching { org.astermail.android.ui.compose.compose_seed_store.clear(context) }
        runCatching { org.astermail.android.notifications.MutedFolderSync.reset(context) }
        runCatching { org.astermail.android.notifications.QuietHoursSync.reset(context) }
        runCatching { org.astermail.android.imports.ExternalAccountHealthStore.reset(context) }
        cancel_all_notifications()
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching {
            org.astermail.android.mail.AsterProfileResolverHolder.shared?.clear()
            org.astermail.android.mail.OwnAddressAvatars.clear()
            org.astermail.android.security.SenderTrustStore.clear()
            org.astermail.android.contacts.ContactPhotoDirectory.clear()
            org.astermail.android.notifications.NotificationAvatars.clear()
        }
        database.decrypted_mail_dao().clear_all()
        database.folder_row_dao().clear_all()
        database.message_body_dao().clear_all()
        database.thread_snapshot_dao().clear_all()
        current_email?.let { trusted_device_store.clear(it) }
        if (current_id != null) {
            runCatching { forget_deleted_account_device_recovery(context, current_id) }
            runCatching { org.astermail.android.mail.clear_folder_cache_stats(context, current_id) }
            runCatching { mail_repository.clear_pending_actions(current_id) }
            runCatching { database.contact_cache_dao().clear_for_account(current_id) }
            runCatching { database.contact_group_cache_dao().clear_for_account(current_id) }
            val outbox_ids = runCatching {
                database.pending_send_dao().get_for_account(current_id).map { it.id }
            }.getOrDefault(emptyList())
            runCatching { database.pending_send_dao().clear_for_account(current_id) }
            runCatching { wipe_outbox_attachments(context.filesDir, outbox_ids) }
            account_store.remove(current_id)
            runCatching { session_snapshot_store.remove(current_id) }
        }
        runCatching {
            wipe_orphaned_outbox_attachments(context.filesDir, database.pending_send_dao().get_all().map { it.id })
        }
        val candidates = account_store.get_all()
            .filter { it.id != current_id && session_snapshot_store.has(it.id) }
        for (candidate in candidates) {
            if (try_restore_session(candidate.id)) return@runCatching
        }
        _active_account_id.value = null
        _is_signed_in.value = false
    }

    fun cached_password_hash_b64(): String? {
        val cached = session_key_store.get() ?: return null
        val encoded = base64_encode(cached)
        cached.fill(0)
        return encoded
    }

    suspend fun stored_password_hash_b64(): String? {
        cached_password_hash_b64()?.let { return it }
        val stored = session_key_store.get_passphrase() ?: return null
        val password = String(stored, Charsets.UTF_8)
        stored.fill(0)
        return runCatching { derive_password_hash_b64(password) }.getOrNull()
    }

    suspend fun derive_password_hash_b64(password: String): String? {
        val step_up = step_up_salt() ?: return null
        val salt = step_up.salt
        require_usable_auth_salt(salt)
        val password_bytes = password.toByteArray(Charsets.UTF_8)
        val hash = try {
            derive_step_up_proof(password_bytes, step_up)
        } finally {
            password_bytes.fill(0)
            salt.fill(0)
        }
        val encoded = base64_encode(hash)
        hash.fill(0)
        return encoded
    }

    private fun extract_ratchet_keys(vault_obj: org.json.JSONObject) {
        org.astermail.android.mail.ratchet.apply_vault_ratchet_keys(
            org.astermail.android.mail.ratchet.parse_vault_ratchet_keys(vault_obj),
            session_key_store,
        )
    }

    private fun generate_signup_pgp_keys(
        username: String,
        canonical_email: String,
        password: String,
    ): PgpKeyPairResult? {
        repeat(SIGNUP_PGP_KEYGEN_ATTEMPTS) {
            val passphrase_chars = password.toCharArray()
            try {
                return PgpKeyGenerator.generate(username, canonical_email, passphrase_chars)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            } finally {
                passphrase_chars.fill(' ')
            }
        }
        return null
    }

    private fun build_vault_json(
        identity_private_b64: String,
        prekey_private_b64: String,
        recovery_codes: List<String>? = null,
        pgp_private_key: String? = null,
        data_kek_b64: String? = null,
    ): String {
        val obj = org.json.JSONObject()
        obj.put("version", 1)
        if (data_kek_b64 != null) install_signup_data_kek(obj, data_kek_b64)
        obj.put("identity_private_key", identity_private_b64)
        obj.put("signed_prekey_private", prekey_private_b64)
        obj.put("created_at", System.currentTimeMillis() / 1000L)
        if (!recovery_codes.isNullOrEmpty()) {
            val arr = org.json.JSONArray()
            recovery_codes.forEach { arr.put(it) }
            obj.put("recovery_codes", arr)
        }
        if (pgp_private_key != null) {
            obj.put("identity_key", pgp_private_key)
        }
        return obj.toString()
    }

    private suspend fun ensure_pgp_key_published() {
        val user_id = session_key_store.get_user_id() ?: return
        if (!pgp_publish_attempted_user_ids.add(user_id)) return

        if (!is_pgp_private_key(session_key_store.get_identity_key())) {
            install_identity_key_if_missing(user_id)
        }

        val identity_key = session_key_store.get_identity_key() ?: return
        if (!is_pgp_private_key(identity_key)) return

        val passphrase_bytes = session_key_store.get_passphrase() ?: return

        try {
            val published = published_pgp_key_state()
            if (published == PublishedPgpKey.Absent) {
                val passphrase = passphrase_chars(passphrase_bytes)
                try {
                    republish_pgp_key_with_password(identity_key, passphrase, published)
                } finally {
                    passphrase.fill(' ')
                }
            }
        } finally {
            passphrase_bytes.fill(0)
        }

        val profile = withTimeoutOrNull(8_000L) { auth_api.me() } ?: return
        if (!profile_matches_session(profile)) return
        val account_email = profile.email?.trim()?.takeIf { it.isNotBlank() } ?: return
        heal_identity_key_account_addresses(account_email, profile.display_name.orEmpty())
    }

    private suspend fun install_identity_key_if_missing(user_id: String) {
        if (published_pgp_key_state() != PublishedPgpKey.Absent) return
        val profile = withTimeoutOrNull(8_000L) { auth_api.me() } ?: return
        if (!profile_matches_session(profile)) return
        val account_email = profile.email?.trim()?.takeIf { it.isNotBlank() } ?: return
        val display_name = profile.display_name?.trim()?.takeIf { it.isNotBlank() } ?: account_email
        val passphrase = session_key_store.get_passphrase() ?: return

        try {
            if (session_key_store.get_user_id() != user_id) return
            val edit = MissingIdentityKeyEdit(
                generate_identity_key = { generate_missing_identity_key(passphrase, display_name, account_email) },
                now = { java.time.Instant.now().toString() },
            )
            val outcome = vault_updates.update_identity(vault_updates.with_passphrase(passphrase)) { draft ->
                edit.apply(draft.vault)
            }
            val vault_obj = when (outcome) {
                is org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome.Written -> outcome.vault
                is org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome.Unchanged -> outcome.vault
                else -> return
            }
            if (session_key_store.get_user_id() != user_id) return
            val identity_key = vault_identity_key(vault_obj)
            if (!is_pgp_private_key(identity_key)) return
            session_key_store.put_identity_key(identity_key)
            absorb_previous_keys_and_keks(vault_obj)
            absorb_data_kek(vault_obj)

            val installed_key = edit.installed_key
            val written = outcome is org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome.Written
            if (written && installed_key != null) {
                val password = passphrase_chars(passphrase)
                try {
                    runCatching { republish_pgp_key_with_password(installed_key, password) }
                        .onFailure { if (it is CancellationException) throw it }
                } finally {
                    password.fill(' ')
                }
            }
        } finally {
            passphrase.fill(0)
        }

        system_folder_heal_attempted_user_ids.remove(user_id)
        trigger_system_folder_bootstrap()
        load_account_keks()
        schedule_ratchet_bootstrap(bypass_debounce = true)
    }

    private fun generate_missing_identity_key(
        passphrase: ByteArray,
        display_name: String,
        account_email: String,
    ): String? {
        val password = passphrase_chars(passphrase)
        return try {
            runCatching { PgpKeyGenerator.generate(display_name, account_email, password) }
                .getOrNull()
                ?.armored_private_key
        } finally {
            password.fill(' ')
        }
    }

    private suspend fun heal_identity_key_account_addresses(
        account_email: String,
        display_name: String,
    ) = withContext(Dispatchers.Default) {
        if (org.astermail.android.crypto.identity_addresses_for(account_email).size < 2) return@withContext
        val user_id = session_key_store.get_user_id() ?: return@withContext
        val attempt_key = "$user_id|${account_email.lowercase(Locale.ROOT)}"
        if (!pgp_address_heal_attempted.add(attempt_key)) return@withContext

        val healed = try {
            heal_identity_key_account_addresses_once(account_email, display_name)
        } catch (cancelled: CancellationException) {
            pgp_address_heal_attempted.remove(attempt_key)
            throw cancelled
        } catch (_: Throwable) {
            false
        }
        if (BuildConfig.DEBUG && !healed) {
            android.util.Log.w("AuthRepository", "pgp address heal skipped")
        }
    }

    private suspend fun heal_identity_key_account_addresses_once(
        account_email: String,
        display_name: String,
    ): Boolean {
        val identity_key = session_key_store.get_identity_key() ?: return false
        if (!identity_key.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return false
        val local_fingerprint = pgp_key_fingerprint(identity_key) ?: return false

        val info = encryption_api.get_pgp_key_info()
        if (!pgp_key_role_is_internal(info.role)) return false
        if (!info.fingerprint.equals(local_fingerprint, ignoreCase = true)) return false
        val published = PublishedPgpKey.Present(info.fingerprint)

        val local_missing =
            org.astermail.android.crypto.account_addresses_missing_from_pgp_key(identity_key, account_email)
        val published_missing = info.public_key_armored.isNotBlank() &&
            org.astermail.android.crypto.account_addresses_missing_from_pgp_key(
                info.public_key_armored,
                account_email,
            ).isNotEmpty()
        if (local_missing.isEmpty() && !published_missing) return true

        val passphrase_bytes = session_key_store.get_passphrase() ?: return false
        try {
            val passphrase = passphrase_chars(passphrase_bytes)
            try {
                if (local_missing.isEmpty()) {
                    republish_pgp_key_with_password(identity_key, passphrase, published)
                    return true
                }

                val updated = org.astermail.android.crypto.add_account_addresses_to_pgp_key(
                    identity_key,
                    passphrase,
                    display_name,
                    account_email,
                ) ?: return false

                val stored = withContext(NonCancellable) {
                    if (!store_identity_key_in_vault(updated, passphrase_bytes)) {
                        return@withContext false
                    }
                    session_key_store.put_identity_key(updated)
                    true
                }
                if (!stored) return false

                republish_pgp_key_with_password(updated, passphrase, published)
                return true
            } finally {
                passphrase.fill(' ')
            }
        } finally {
            passphrase_bytes.fill(0)
        }
    }

    suspend fun select_signing_identity_key(): String? {
        val identity_key = session_key_store.get_identity_key() ?: return null
        if (!identity_key.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return null

        val published_fingerprint = try {
            encryption_api.get_pgp_key_info().let { internal_pgp_fingerprint_or_blank(it.fingerprint, it.role) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return identity_key
        }
        if (published_fingerprint.isBlank()) return identity_key

        matching_signing_key(published_fingerprint)?.let { return it }

        if (try_refresh_vault_keys()) {
            matching_signing_key(published_fingerprint)?.let { return it }
        }

        log_pgp_republish_skipped(PgpRepublishDecision.SKIP_PUBLISHED_KEY_DIFFERS)
        return null
    }

    private suspend fun published_pgp_key_state(): PublishedPgpKey {
        repeat(PUBLISHED_PGP_KEY_LOOKUP_ATTEMPTS) { attempt ->
            try {
                val info = encryption_api.get_pgp_key_info()
                return published_pgp_key_from_info(info.fingerprint, info.role)
            } catch (_: org.astermail.android.api.ApiError.NotFoundError) {
                return PublishedPgpKey.Absent
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!published_pgp_key_lookup_is_transient(error)) return PublishedPgpKey.Unknown
                if (attempt < PUBLISHED_PGP_KEY_LOOKUP_ATTEMPTS - 1) {
                    delay(PUBLISHED_PGP_KEY_LOOKUP_BACKOFF_MS * (attempt + 1))
                }
            }
        }
        return PublishedPgpKey.Unknown
    }

    private fun log_pgp_republish_skipped(decision: PgpRepublishDecision) {
        if (BuildConfig.DEBUG) {
            android.util.Log.w("AuthRepository", "pgp republish skipped: ${decision.name.lowercase()}")
        }
    }

    private fun matching_signing_key(published_fingerprint: String): String? {
        val candidates = buildList {
            session_key_store.get_identity_key()?.let { add(it) }
            session_key_store.get_previous_keys()?.let { addAll(it) }
        }.filter { it.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY") }

        return candidates.firstOrNull { candidate ->
            pgp_key_fingerprint(candidate)?.equals(published_fingerprint, ignoreCase = true) == true
        }
    }

    private fun pgp_key_fingerprint(armored_private_key: String): String? = try {
        val secret_ring = org.bouncycastle.openpgp.PGPSecretKeyRing(
            org.bouncycastle.openpgp.PGPUtil.getDecoderStream(armored_private_key.byteInputStream()),
            org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator(),
        )
        String.format(Locale.US, "%040X", BigInteger(1, secret_ring.publicKey.fingerprint))
    } catch (_: Throwable) {
        null
    }

    fun exportable_private_key(
        fingerprint: String,
        password: String,
        encrypted_blob_b64: String?,
        nonce_b64: String?,
    ): String? {
        val password_chars = password.toCharArray()
        try {
            val candidates = buildList {
                session_key_store.get_identity_key()?.let { add(it) }
                session_key_store.get_previous_keys()?.let { addAll(it) }
            }
            return PrivateKeyExport.select(
                candidates = candidates,
                fingerprint = fingerprint,
                password = password_chars,
                encrypted_blob_b64 = encrypted_blob_b64,
                nonce_b64 = nonce_b64,
                pbkdf2_iterations = pgp_private_key_pbkdf2_iterations,
            )
        } finally {
            password_chars.fill(' ')
        }
    }

    private suspend fun republish_pgp_key_with_password(
        identity_key: String,
        password: String,
        published: PublishedPgpKey? = null,
    ) {
        val password_chars = password.toCharArray()
        try {
            republish_pgp_key_with_password(identity_key, password_chars, published)
        } finally {
            password_chars.fill(' ')
        }
    }

    private suspend fun republish_pgp_key_with_password(
        identity_key: String,
        password: CharArray,
        published: PublishedPgpKey? = null,
    ) {
        val secret_ring = org.bouncycastle.openpgp.PGPSecretKeyRing(
            org.bouncycastle.openpgp.PGPUtil.getDecoderStream(identity_key.byteInputStream()),
            org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator(),
        )

        val public_out = ByteArrayOutputStream()
        ArmoredOutputStream(public_out).use { armored ->
            secret_ring.publicKeys.forEach { it.encode(armored) }
        }

        val master_public = secret_ring.publicKey
        val fingerprint = String.format(
            Locale.US,
            "%040X",
            BigInteger(1, master_public.fingerprint),
        )
        val key_id = String.format(Locale.US, "%016X", master_public.keyID)

        val decision = decide_pgp_republish(
            local_fingerprint = fingerprint,
            local_key_non_standard = armored_pgp_key_is_non_standard(identity_key),
            published = published ?: published_pgp_key_state(),
        )
        if (decision != PgpRepublishDecision.REPUBLISH) {
            log_pgp_republish_skipped(decision)
            throw PgpRepublishBlocked(decision)
        }

        val (encrypted_private_key, private_key_nonce) =
            encrypt_pgp_private_key_for_server(identity_key, password)

        encryption_api.republish_pgp_key(
            org.astermail.android.api.encryption.RepublishPgpKeyRequest(
                fingerprint = fingerprint,
                key_id = key_id,
                public_key_armored = public_out.toString(Charsets.UTF_8.name()),
                encrypted_private_key = encrypted_private_key,
                private_key_nonce = private_key_nonce,
            ),
        )
    }

    private suspend fun rewrap_server_pgp_key(new_password: String) {
        val current_identity = session_key_store.get_identity_key() ?: return
        if (!current_identity.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return
        val published = published_pgp_key_state()
        val identity_key = (published as? PublishedPgpKey.Present)
            ?.fingerprint
            ?.takeIf { it.isNotBlank() }
            ?.let { matching_signing_key(it) }
            ?: current_identity
        republish_pgp_key_with_password(identity_key, new_password, published)
    }

    private fun encrypt_pgp_private_key_for_server(
        armored_private_key: String,
        password: String,
    ): Pair<String, String> {
        val password_chars = password.toCharArray()
        try {
            return encrypt_pgp_private_key_for_server(armored_private_key, password_chars)
        } finally {
            password_chars.fill(' ')
        }
    }

    private fun encrypt_pgp_private_key_for_server(
        armored_private_key: String,
        password: CharArray,
    ): Pair<String, String> {
        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val nonce = ByteArray(12).also { rng.nextBytes(it) }

        val derived = PasswordKdf.derive_aes_key(password, salt, pgp_private_key_pbkdf2_iterations)
        val ciphertext = AesGcm.encrypt(derived, nonce, armored_private_key.toByteArray(Charsets.UTF_8))
        derived.fill(0)

        return base64_encode(salt + ciphertext) to base64_encode(nonce)
    }

    private fun reported_vault_kdf(value: Int?): Int =
        SessionKeyStore.sanitize_vault_kdf_iterations(value ?: SessionKeyStore.default_vault_kdf_iterations)

    private fun decrypt_vault_aes_gcm(
        encrypted_vault_b64: String,
        vault_nonce_b64: String,
        password_bytes: ByteArray,
    ): ByteArray {
        val combined = base64_decode(encrypted_vault_b64)
        val nonce = base64_decode(vault_nonce_b64)
        val salt = combined.copyOfRange(0, 16)
        val ciphertext = combined.copyOfRange(16, combined.size)

        val derived = PasswordKdf.derive_aes_key(password_bytes, salt, vault_pbkdf2_iterations)
        try {
            return AesGcm.decrypt(derived, nonce, ciphertext)
        } finally {
            derived.fill(0)
        }
    }

    suspend fun rotate_recovery_codes(step_up_token: String): List<String> {
        val passphrase = session_key_store.get_passphrase()
            ?: throw ApiError.UnknownError(context.getString(R.string.session_unavailable_sign_in_again))

        try {
            val codes = generate_recovery_codes()
            val outcome = withContext(NonCancellable) {
                vault_updates.with_passphrase(passphrase).replace_through(
                    edit = { draft ->
                        val codes_array = org.json.JSONArray()
                        codes.forEach { codes_array.put(it) }
                        draft.vault.put("recovery_codes", codes_array)
                        org.astermail.android.crypto.storage_rotation.VaultEdit.CHANGED
                    },
                    upgrade = true,
                    send = { vault_obj, sealed, snapshot ->
                        val recovery_key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                        val backup_plain = backup_vault_bytes(vault_obj, passphrase.copyOf())
                        val vault_backup = encrypt_vault_backup(backup_plain, recovery_key)
                        backup_plain.fill(0)
                        val shares = codes.map { generate_recovery_share(it, recovery_key) }
                        recovery_key.fill(0)
                        recovery_api.backup(
                            SaveRecoveryBackupRequest(
                                recovery_shares = shares,
                                encrypted_vault_backup = vault_backup.encrypted_data,
                                vault_backup_nonce = vault_backup.nonce,
                                recovery_key_salt = vault_backup.salt,
                                step_up_token = step_up_token,
                                encrypted_vault = sealed.encrypted_vault,
                                vault_nonce = sealed.vault_nonce,
                                vault_format = stored_vault_format(vault_obj),
                                expected_vault_sha256 = snapshot.vault_sha256,
                                vault_kdf_iterations = VaultKdf.to_server(sealed.kdf_iterations),
                            ),
                        )
                        true
                    },
                )
            }
            if (outcome !is org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome.Written) {
                throw ApiError.UnknownError(context.getString(R.string.error_generic))
            }
            withContext(NonCancellable) {
                session_key_store.put_recovery_codes(codes)
                runCatching { session_key_store.get_user_id()?.let { save_session_snapshot(it) } }
            }
            return codes
        } finally {
            passphrase.fill(0)
        }
    }

    private fun backup_vault_bytes(vault: org.json.JSONObject, passphrase: ByteArray): ByteArray {
        val chars = passphrase_chars(passphrase)
        try {
            return build_backup_vault(vault, chars).toString().toByteArray(Charsets.UTF_8)
        } finally {
            chars.fill('\u0000')
            passphrase.fill(0)
        }
    }

    private fun generate_recovery_share(code: String, recovery_key: ByteArray): RecoveryShareData {
        val code_hash = hash_recovery_code(code)
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val derived = PasswordKdf.derive_aes_key(canonicalize_recovery_code(code), salt, pbkdf2_iterations)
        val encrypted = AesGcm.encrypt(derived, nonce, recovery_key)
        derived.fill(0)
        return RecoveryShareData(
            code_hash = code_hash,
            code_salt = base64_encode(salt),
            encrypted_recovery_key = base64_encode(encrypted),
            recovery_key_nonce = base64_encode(nonce),
        )
    }

    private data class VaultBackupResult(val encrypted_data: String, val nonce: String, val salt: String)

    private fun encrypt_vault_backup(vault: ByteArray, recovery_key: ByteArray): VaultBackupResult {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val derived = hkdf_sha256(recovery_key, salt, HKDF_INFO.toByteArray(Charsets.UTF_8), 32)
        val encrypted = AesGcm.encrypt(derived, nonce, vault)
        derived.fill(0)
        return VaultBackupResult(base64_encode(encrypted), base64_encode(nonce), base64_encode(salt))
    }

    private suspend fun clear_decrypted_mail_cache_blocking(): Boolean {
        repeat(3) { attempt ->
            val cleared = runCatching {
                withTimeoutOrNull(5_000L) {
                    database.decrypted_mail_dao().clear_all()
                    database.folder_row_dao().clear_all()
                    database.message_body_dao().clear_all()
                    database.thread_snapshot_dao().clear_all()
                } != null
            }.getOrDefault(false)
            if (cleared) return true
            if (attempt < 2) delay(250L)
        }
        return false
    }

    private fun cached_vault_bytes(): ByteArray? =
        runCatching { session_key_store.get_encrypted_vault()?.first?.let { base64_decode(it) } }.getOrNull()

    private fun require_usable_auth_salt(salt: ByteArray) =
        AuthSaltGuard.require_usable_auth_salt(
            salt,
            cached_vault_bytes(),
            session_key_store.get_remembered_vault_salts(),
        )

    private fun derive_login_hash(password_bytes: ByteArray, salt: ByteArray): ByteArray =
        AuthSaltGuard.derive_with_usable_auth_salt(
            salt,
            cached_vault_bytes(),
            session_key_store.get_remembered_vault_salts(),
        ) { usable_salt -> CryptoNative.derive_pbkdf2_hash(password_bytes, usable_salt, pbkdf2_iterations) }

    private fun base64_encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun base64_decode(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.DEFAULT)

    private fun normalize_email(input: String): String {
        val trimmed = input.trim().lowercase(java.util.Locale.ROOT)
        return if (trimmed.contains('@')) trimmed else "$trimmed@astermail.org"
    }

    companion object {
        private const val pbkdf2_iterations = 310000
        private const val vault_pbkdf2_iterations = 310000
        private const val pgp_private_key_pbkdf2_iterations = 310000
        private const val HKDF_INFO = "Aster Mail_Recovery_Vault_v1"
    }
}
