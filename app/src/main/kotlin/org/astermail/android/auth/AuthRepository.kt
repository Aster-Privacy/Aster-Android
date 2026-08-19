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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.AuthSaltCollisionException
import org.astermail.android.crypto.AuthSaltGuard
import org.astermail.android.crypto.hkdf_sha256
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.Argon2Params
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.ClientPgpKeyData
import org.astermail.android.api.auth.DeleteAccountRequest
import org.astermail.android.api.auth.LoginRequest
import org.astermail.android.api.auth.LoginResponse
import org.astermail.android.api.auth.LoginResult
import org.astermail.android.api.auth.RegisterRequest
import org.astermail.android.api.auth.TotpLoginVerifyRequest
import org.astermail.android.api.labels.CreateLabelRequest
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.api.recovery.RecoveryShareData
import org.astermail.android.api.recovery.SaveRecoveryBackupRequest
import org.astermail.android.api.settings.ChangePasswordRequest
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.PgpKeyGenerator
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

data class RegisterSuccess(val recovery_codes: List<String>)

data class TotpChallenge(
    val pending_login_token: String,
    val available_methods: List<String>,
    val password_hash_bytes: ByteArray,
    val password_bytes: ByteArray,
    val salt_bytes: ByteArray,
    val email: String,
    val remember_me: Boolean,
)

private const val UNAUTHORIZED_CHECK_COOLDOWN_MS = 10_000L

sealed interface LoginOutcome {
    data object Success : LoginOutcome
    data class NeedsTotp(val challenge: TotpChallenge) : LoginOutcome
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth_api: AuthApi,
    private val recovery_api: RecoveryApi,
    private val recovery_email_api: org.astermail.android.api.recovery_email.RecoveryEmailApi,
    private val settings_api: SettingsApi,
    private val labels_api: LabelsApi,
    private val encryption_api: org.astermail.android.api.encryption.EncryptionApi,
    private val api_client: ApiClient,
    private val token_store: TokenStore,
    private val session_key_store: SessionKeyStore,
    private val account_store: AccountStore,
    private val database: org.astermail.android.storage.search.AsterDatabase,
    private val session_snapshot_store: SessionSnapshotStore,
    private val trusted_device_store: TrustedDeviceStore,
    private val mail_repository: org.astermail.android.mail.MailRepository,
    private val theme_store: ThemeStore,
    private val ratchet_bootstrap_service: org.astermail.android.mail.ratchet.RatchetBootstrapService,
    @ApplicationContext private val context: Context,
) {

    private val _is_signed_in = MutableStateFlow(token_store.access_token != null)
    val is_signed_in: StateFlow<Boolean> = _is_signed_in.asStateFlow()

    private val unauthorized_check_running = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var last_unauthorized_check_ms = 0L

    private val background_scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    private val pgp_publish_attempted_user_ids =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val signing_heal_attempted_user_ids =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    fun trigger_ratchet_bootstrap() {
        if (!_is_signed_in.value) return
        if (BuildConfig.DEBUG) android.util.Log.w("RatchetBootstrap", "trigger_ratchet_bootstrap firing")
        background_scope.launch {
            runCatching { ratchet_bootstrap_service.bootstrap_if_needed() }
                .onFailure { if (BuildConfig.DEBUG) android.util.Log.w("RatchetBootstrap", "bootstrap_if_needed threw: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

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
            // The access token has likely expired. Ktor's bearer auth only
            // auto-refreshes on a 401 carrying a WWW-Authenticate challenge,
            // which the backend does not send, so we refresh explicitly before
            // giving up. Only sign out if the refresh fails *definitively*
            // (the backend rejects the refresh token); a transient network
            // failure must not sign the user out.
            when (try_refresh_session()) {
                RefreshOutcome.Success -> {
                    try {
                        auth_api.me()
                    } catch (e2: CancellationException) {
                        throw e2
                    } catch (e2: ApiError.UnauthorizedError) {
                        force_sign_out()
                    } catch (_: Throwable) {
                    }
                }
                RefreshOutcome.AuthFailed -> force_sign_out()
                RefreshOutcome.Transient -> {
                }
            }
        } catch (_: Throwable) {
        } finally {
            unauthorized_check_running.set(false)
        }
    }

    private enum class RefreshOutcome { Success, AuthFailed, Transient }

    private suspend fun try_refresh_session(): RefreshOutcome {
        return try {
            val current_refresh = token_store.refresh_token ?: return RefreshOutcome.AuthFailed
            val response = auth_api.refresh(current_refresh)
            val new_refresh = response.refresh_token ?: current_refresh
            token_store.save(response.access_token, new_refresh)
            api_client.invalidate_bearer_cache()
            runCatching { session_key_store.get_user_id()?.let { save_session_snapshot(it) } }
            RefreshOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.UnauthorizedError) {
            RefreshOutcome.AuthFailed
        } catch (e: ApiError.ForbiddenError) {
            RefreshOutcome.Transient
        } catch (_: Throwable) {
            RefreshOutcome.Transient
        }
    }

    suspend fun login(email: String, password: String, captcha_token: String? = null): Result<LoginOutcome> = runCatching {
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
        val salt_bytes = base64_decode(salt_resp.salt)
        AuthSaltGuard.require_usable_auth_salt(salt_bytes, cached_vault_bytes())
        val password_bytes = password.toByteArray(Charsets.UTF_8)
        val password_hash_bytes = CryptoNative.derive_pbkdf2_hash(
            password_bytes,
            salt_bytes,
            pbkdf2_iterations,
        )
        val password_hash_b64 = base64_encode(password_hash_bytes)

        val remember_me = true

        val login_result = auth_api.login(
            LoginRequest(
                user_hash = user_hash,
                password_hash = password_hash_b64,
                captcha_token = captcha_token,
                remember_me = remember_me,
            ),
            trusted_device_token = trusted_token,
        )

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
                    ),
                )
            }
            is LoginResult.Success -> {
                login_result.trusted_device_token?.takeIf { it.isNotBlank() }?.let { token ->
                    trusted_device_store.put_token(normalized, token)
                }
                complete_login(login_result.response, password_bytes, password_hash_bytes, salt_bytes)
                LoginOutcome.Success
            }
        }
    }

    suspend fun verify_totp(code: String, challenge: TotpChallenge, trust_device: Boolean): Result<Unit> = runCatching {
        val outcome = auth_api.verify_totp_login(
            TotpLoginVerifyRequest(
                code = code,
                pending_login_token = challenge.pending_login_token,
                trust_device = trust_device,
                remember_me = challenge.remember_me,
            ),
        )
        if (trust_device) {
            outcome.trusted_device_token?.takeIf { it.isNotBlank() }?.let { token ->
                trusted_device_store.put_token(challenge.email, token)
            }
        }
        complete_login(outcome.response, challenge.password_bytes, challenge.password_hash_bytes, challenge.salt_bytes)
    }

    private suspend fun complete_login(
        login_resp: LoginResponse,
        password_bytes: ByteArray,
        password_hash_bytes: ByteArray,
        salt_bytes: ByteArray,
    ) {
        val access = login_resp.access_token ?: throw ApiError.UnknownError("missing access_token")
        val previous_user_id = session_key_store.get_user_id()
        if (previous_user_id != null && previous_user_id != login_resp.user_id) {
            session_key_store.clear()
            if (!clear_decrypted_mail_cache_blocking()) {
                throw ApiError.UnknownError("could not clear the previous account's local mail cache")
            }
            runCatching { pending_send_dao_clear_all() }
            mail_repository.clear_caches()
            cancel_all_notifications()
            runCatching { org.astermail.android.notifications.MailPollingWorker.reset_new_mail_baseline(context) }
        }
        val served_vault_bytes = runCatching { base64_decode(login_resp.encrypted_vault) }.getOrNull()
        if (AuthSaltGuard.collides_with_vault_salt(salt_bytes, served_vault_bytes)) {
            runCatching { session_key_store.clear() }
            runCatching { token_store.clear() }
            throw AuthSaltCollisionException("auth salt equals the vault key salt")
        }
        token_store.save(access, login_resp.refresh_token ?: access)
        api_client.invalidate_bearer_cache()
        session_key_store.put(password_hash_bytes)
        session_key_store.put_passphrase(password_bytes)
        session_key_store.put_password_salt(salt_bytes)
        session_key_store.put_user_id(login_resp.user_id)
        session_key_store.put_user_email(login_resp.email)
        session_key_store.put_encrypted_vault(login_resp.encrypted_vault, login_resp.vault_nonce)

        try {
            val vault_bytes = base64_decode(login_resp.encrypted_vault)
            val nonce_bytes = base64_decode(login_resp.vault_nonce)
            val vault_plain = CryptoNative.decrypt_vault_with_password(
                vault_bytes,
                nonce_bytes,
                password_bytes,
            )
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
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("AuthRepository", "vault decryption failed: ${t.javaClass.simpleName}")
        }

        password_bytes.fill(0)
        password_hash_bytes.fill(0)

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
        )
        runCatching { save_session_snapshot(login_resp.user_id) }
        _is_signed_in.value = true
        runCatching { UnifiedPushState.clear_backend_registration(context) }
        runCatching { UnifiedPushState.sync_registration(context) }
        background_scope.launch { runCatching { ensure_pgp_key_published() } }
        background_scope.launch { runCatching { ratchet_bootstrap_service.bootstrap_if_needed() } }
    }

    suspend fun register(email: String, password: String, captcha_token: String? = null): Result<RegisterSuccess> = runCatching {
        val trimmed = email.trim().lowercase()
        val at_index = trimmed.indexOf('@')
        val username = if (at_index > 0) trimmed.substring(0, at_index) else trimmed
        val email_domain = if (at_index > 0) trimmed.substring(at_index + 1) else "astermail.org"
        if (email_domain != "astermail.org" && email_domain != "aster.cx") {
            throw ApiError.ValidationError(listOf("email domain must be astermail.org or aster.cx"))
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

        val identity = CryptoNative.generate_identity_keypair_struct()
        val prekey = CryptoNative.generate_identity_keypair_struct()
        val signature = CryptoNative.sign_with_identity(identity.private_key, prekey.public_key)

        val passphrase_chars = password.toCharArray()
        val pgp_keys = try {
            PgpKeyGenerator.generate(username, canonical_email, passphrase_chars)
        } catch (_: Throwable) {
            null
        } finally {
            passphrase_chars.fill(' ')
        }

        val recovery_codes = generate_recovery_codes()
        val recovery_key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

        val vault_json = build_vault_json(
            identity_private_b64 = base64_encode(identity.private_key),
            prekey_private_b64 = base64_encode(prekey.private_key),
            recovery_codes = recovery_codes,
            pgp_private_key = pgp_keys?.armored_private_key,
        )
        val vault_plaintext = vault_json.toByteArray(Charsets.UTF_8)
        val raw_password_bytes = password.toByteArray(Charsets.UTF_8)
        val vault_envelope = CryptoNative.encrypt_vault_with_password(vault_plaintext, raw_password_bytes)
        raw_password_bytes.fill(0)
        vault_plaintext.fill(0)

        val client_pgp_key = pgp_keys?.let { keys ->
            runCatching {
                val (encrypted_private_key, private_key_nonce) =
                    encrypt_pgp_private_key_for_server(keys.armored_private_key, password)
                ClientPgpKeyData(
                    fingerprint = keys.fingerprint.uppercase(),
                    key_id = keys.key_id.uppercase(),
                    public_key_armored = keys.armored_public_key,
                    encrypted_private_key = encrypted_private_key,
                    private_key_nonce = private_key_nonce,
                )
            }.getOrNull()
        }

        val register_resp = auth_api.register(
            RegisterRequest(
                username = username,
                user_hash = user_hash,
                password_hash = base64_encode(password_hash_bytes),
                password_salt = base64_encode(salt_bytes),
                argon2_params = Argon2Params(memory = 65536, iterations = 3, parallelism = 4),
                identity_key = pgp_keys?.armored_public_key?.let { base64_encode(it.toByteArray(Charsets.UTF_8)) }
                    ?: base64_encode(identity.public_key),
                signed_prekey = base64_encode(prekey.public_key),
                signed_prekey_signature = base64_encode(signature),
                encrypted_vault = base64_encode(vault_envelope.encrypted_vault),
                vault_nonce = base64_encode(vault_envelope.vault_nonce),
                email_domain = email_domain,
                remember_me = true,
                captcha_token = captcha_token,
                pgp_key = client_pgp_key,
            ),
        )

        val access = register_resp.access_token
            ?: throw ApiError.UnknownError("missing access_token on register")
        val previous_user_id = session_key_store.get_user_id()
        if (previous_user_id != null && previous_user_id != register_resp.user_id) {
            session_key_store.clear()
            runCatching {
                withTimeoutOrNull(3_000L) { database.decrypted_mail_dao().clear_all() }
            }
            mail_repository.clear_caches()
            cancel_all_notifications()
            runCatching { org.astermail.android.notifications.MailPollingWorker.reset_new_mail_baseline(context) }
        }
        token_store.save(access, register_resp.refresh_token ?: access)
        api_client.invalidate_bearer_cache()
        session_key_store.put(password_hash_bytes)
        session_key_store.put_passphrase(password.toByteArray(Charsets.UTF_8))
        session_key_store.put_password_salt(salt_bytes)
        session_key_store.put_user_id(register_resp.user_id)
        session_key_store.put_user_email(canonical_email)
        val stored_identity = pgp_keys?.armored_private_key ?: base64_encode(identity.private_key)
        session_key_store.put_identity_key(stored_identity)
        session_key_store.put_encrypted_vault(
            base64_encode(vault_envelope.encrypted_vault),
            base64_encode(vault_envelope.vault_nonce),
        )

        create_default_folders(stored_identity)

        identity.private_key.fill(0)
        prekey.private_key.fill(0)
        signature.fill(0)
        password_hash_bytes.fill(0)

        val vault_for_backup = vault_json.toByteArray(Charsets.UTF_8)
        val vault_backup = encrypt_vault_backup(vault_for_backup, recovery_key)
        vault_for_backup.fill(0)
        val recovery_shares = recovery_codes.map { code -> generate_recovery_share(code, recovery_key) }
        recovery_key.fill(0)

        runCatching {
            recovery_api.backup(
                SaveRecoveryBackupRequest(
                    recovery_shares = recovery_shares,
                    encrypted_vault_backup = vault_backup.encrypted_data,
                    vault_backup_nonce = vault_backup.nonce,
                    recovery_key_salt = vault_backup.salt,
                ),
            )
        }

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
        )
        save_session_snapshot(register_resp.user_id)
        _is_signed_in.value = true
        runCatching { UnifiedPushState.clear_backend_registration(context) }
        runCatching { UnifiedPushState.sync_registration(context) }
        background_scope.launch { runCatching { ratchet_bootstrap_service.bootstrap_if_needed() } }
        RegisterSuccess(recovery_codes = recovery_codes)
    }

    private fun save_session_snapshot(account_id: String) {
        runCatching {
            session_snapshot_store.save(
                account_id = account_id,
                token_access = token_store.access_token,
                token_refresh = token_store.refresh_token,
                session_key = session_key_store.get(),
                passphrase = session_key_store.get_passphrase(),
                identity_key = session_key_store.get_identity_key(),
                encrypted_vault = session_key_store.get_encrypted_vault()?.first,
                vault_nonce = session_key_store.get_encrypted_vault()?.second,
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
        val snapshot = session_snapshot_store.load(account_id) ?: return false
        if (!clear_decrypted_mail_cache_blocking()) return false
        runCatching { pending_send_dao_clear_all() }
        mail_repository.clear_caches()
        cancel_all_notifications()
        token_store.save(snapshot.token_access, snapshot.token_refresh)
        api_client.invalidate_bearer_cache()
        session_key_store.clear()
        snapshot.session_key?.let { session_key_store.put(it) }
        snapshot.passphrase?.let { session_key_store.put_passphrase(it) }
        snapshot.identity_key?.let { session_key_store.put_identity_key(it) }
        snapshot.password_salt?.let { session_key_store.put_password_salt(it) }
        snapshot.user_id?.let { session_key_store.put_user_id(it) }
        snapshot.user_email?.let { session_key_store.put_user_email(it) }
        val ev = snapshot.encrypted_vault
        val vn = snapshot.vault_nonce
        if (ev != null && vn != null) {
            session_key_store.put_encrypted_vault(ev, vn)
        }
        snapshot.recovery_codes?.let { session_key_store.put_recovery_codes(it) }
        snapshot.previous_keys?.let { session_key_store.put_previous_keys(it) }
        snapshot.legacy_keks?.let { session_key_store.put_legacy_keks(it) }
        runCatching { try_recover_identity_key() }
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        _is_signed_in.value = true
        background_scope.launch { runCatching { ratchet_bootstrap_service.bootstrap_if_needed() } }
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

    suspend fun change_password(current_password: String, new_password: String): Result<Unit> = runCatching {
        require(new_password.length >= 12) { "new password must be at least 12 characters" }
        require(new_password.length <= 128) { "new password must be at most 128 characters" }

        val current_password_bytes = current_password.toByteArray(Charsets.UTF_8)
        val new_password_bytes = new_password.toByteArray(Charsets.UTF_8)

        val server_salt = session_key_store.get_user_email()?.let { email ->
            runCatching {
                base64_decode(auth_api.get_user_salt(CryptoNative.hash_email(email)).salt)
            }.getOrNull()
        }
        val stored_salt = server_salt
            ?: session_key_store.get_password_salt()
            ?: throw ApiError.UnknownError("session expired - please sign in again")

        val current_password_hash = CryptoNative.derive_pbkdf2_hash(
            current_password_bytes, stored_salt, pbkdf2_iterations,
        )

        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault()
            ?: throw ApiError.UnknownError("vault unavailable - please sign in again")

        val vault_plain = try {
            CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                current_password_bytes,
            )
        } catch (_: Throwable) {
            throw ApiError.ValidationError(listOf(context.getString(R.string.current_password_incorrect)))
        }

        val vault_obj = org.json.JSONObject(String(vault_plain, Charsets.UTF_8))
        vault_plain.fill(0)

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

        val updated_vault_bytes = vault_obj.toString().toByteArray(Charsets.UTF_8)
        val new_envelope = CryptoNative.encrypt_vault_with_password(updated_vault_bytes, new_password_bytes)
        updated_vault_bytes.fill(0)

        val new_salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val new_password_hash = CryptoNative.derive_pbkdf2_hash(
            new_password_bytes, new_salt, pbkdf2_iterations,
        )

        val response = settings_api.change_password(
            ChangePasswordRequest(
                current_password_hash = base64_encode(current_password_hash),
                new_password_hash = base64_encode(new_password_hash),
                new_password_salt = base64_encode(new_salt),
                new_encrypted_vault = base64_encode(new_envelope.encrypted_vault),
                new_vault_nonce = base64_encode(new_envelope.vault_nonce),
            ),
        )

        session_key_store.put(new_password_hash)
        session_key_store.put_passphrase(new_password_bytes)
        session_key_store.put_password_salt(new_salt)
        session_key_store.put_encrypted_vault(
            base64_encode(new_envelope.encrypted_vault),
            base64_encode(new_envelope.vault_nonce),
        )

        response.csrf_token?.let { api_client.set_csrf(it) }
        response.access_token?.let { token_store.save(it, token_store.refresh_token ?: it) }

        runCatching { rewrap_server_pgp_key(new_password) }

        runCatching { session_key_store.get_user_id()?.let { save_session_snapshot(it) } }
        mail_repository.clear_caches()
        database.decrypted_mail_dao().clear_all()
        session_key_store.get_user_email()?.let { trusted_device_store.clear(it) }

        current_password_hash.fill(0)
        new_password_hash.fill(0)
        current_password_bytes.fill(0)
        new_password_bytes.fill(0)
        stored_salt.fill(0)
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

    suspend fun force_sign_out(): Result<Unit> = sign_out_internal(remove_account = false)

    suspend fun logout_all(): Result<Unit> = runCatching {
        var remaining = account_store.count() + 1
        while (remaining-- > 0) {
            sign_out_internal(remove_account = true)
            if (!_is_signed_in.value) break
        }
        _is_signed_in.value = false
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private suspend fun sign_out_internal(remove_account: Boolean): Result<Unit> = runCatching {
        val current_id = session_key_store.get_user_id()
        val current_email = session_key_store.get_user_email()
        if (remove_account) {
            current_email?.let { runCatching { trusted_device_store.clear(it) } }
        }
        try {
            withTimeoutOrNull(5_000L) {
                auth_api.logout()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
        }
        runCatching { token_store.clear() }
        runCatching { api_client.invalidate_bearer_cache() }
        runCatching { session_key_store.clear() }
        runCatching { org.astermail.android.folders.folder_lock_store.lock_all() }
        runCatching { mail_repository.clear_caches() }
        runCatching { org.astermail.android.billing.AttachmentLimits.reset() }
        runCatching { theme_store.clear() }
        cancel_all_notifications()
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching {
            org.astermail.android.mail.AsterProfileResolverHolder.shared?.clear()
        }
        runCatching { database.decrypted_mail_dao().clear_all() }
        runCatching { database.pending_send_dao().clear_all() }
        if (current_id != null) {
            runCatching { session_snapshot_store.remove(current_id) }
            if (remove_account) runCatching { account_store.remove(current_id) }
        }
        val next_account = runCatching {
            account_store.get_all()
                .firstOrNull { it.id != current_id && session_snapshot_store.has(it.id) }
        }.getOrNull()
        if (next_account != null) {
            runCatching { account_store.set_current(next_account.id) }
            val restored = runCatching { try_restore_session(next_account.id) }.getOrDefault(false)
            if (restored) return@runCatching
        }
        _is_signed_in.value = false
    }

    suspend fun refresh_profile(): Result<Unit> = runCatching {
        absorb_profile(auth_api.me())
    }

    fun absorb_profile(profile: org.astermail.android.api.auth.UserInfo) {
        val current_id = session_key_store.get_user_id() ?: profile.user_id
        val email = session_key_store.get_user_email() ?: profile.email ?: return
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

    private fun absorb_previous_keys_and_keks(vault_obj: org.json.JSONObject) {
        vault_obj.optJSONArray("previous_keys")?.let { array ->
            session_key_store.put_previous_keys((0 until array.length()).map { array.getString(it) })
        }
        vault_obj.optJSONArray("legacy_keks")?.let { array ->
            val keks = (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.optString("k", "")?.takeIf { it.isNotBlank() }
            }
            if (keks.isNotEmpty()) session_key_store.put_legacy_keks(keks)
        }
    }

    private fun absorb_data_kek(vault_obj: org.json.JSONObject) {
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

    fun try_recover_identity_key(): Boolean {
        val identity_already_present = session_key_store.get_identity_key() != null
        val ratchet_already_present = session_key_store.has_ratchet_keys()
        if (identity_already_present && ratchet_already_present) return true
        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault() ?: return identity_already_present
        val passphrase = session_key_store.get_passphrase() ?: return identity_already_present
        return try {
            val vault_plain = CryptoNative.decrypt_vault_with_password(
                base64_decode(encrypted_vault_b64),
                base64_decode(vault_nonce_b64),
                passphrase,
            )
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
            session_key_store.put_encrypted_vault(vault.encrypted_vault, vault.vault_nonce)
            val passphrase = session_key_store.get_passphrase() ?: return false
            try {
                val vault_plain = CryptoNative.decrypt_vault_with_password(
                    base64_decode(vault.encrypted_vault),
                    base64_decode(vault.vault_nonce),
                    passphrase,
                )
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
                vault_keys_changed(before, vault_key_snapshot(session_key_store))
            } finally {
                passphrase.fill(0)
            }
        } catch (_: Throwable) {
            false
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun delete_account(password: String, totp_code: String? = null): Result<Unit> = runCatching {
        require(password.isNotBlank()) { "password required" }
        val password_hash = derive_password_hash_b64(password)
            ?: throw ApiError.UnknownError("session expired - please sign in again")
        auth_api.delete_account(
            DeleteAccountRequest(
                password_hash = password_hash,
                totp_code = totp_code?.takeIf { it.isNotBlank() },
            ),
        )
        val current_id = session_key_store.get_user_id()
        val current_email = session_key_store.get_user_email()
        token_store.clear()
        api_client.invalidate_bearer_cache()
        session_key_store.clear()
        org.astermail.android.folders.folder_lock_store.lock_all()
        mail_repository.clear_caches()
        runCatching { theme_store.clear() }
        cancel_all_notifications()
        runCatching {
            val loader = coil.Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching {
            org.astermail.android.mail.AsterProfileResolverHolder.shared?.clear()
        }
        database.decrypted_mail_dao().clear_all()
        current_email?.let { trusted_device_store.clear(it) }
        if (current_id != null) {
            account_store.remove(current_id)
            runCatching { session_snapshot_store.remove(current_id) }
        }
        val next_account = account_store.get_all()
            .firstOrNull { it.id != current_id && session_snapshot_store.has(it.id) }
        if (next_account != null) {
            account_store.set_current(next_account.id)
            if (try_restore_session(next_account.id)) return@runCatching
        }
        _is_signed_in.value = false
    }

    suspend fun derive_password_hash_b64(password: String): String? {
        val server_salt = session_key_store.get_user_email()?.let { email ->
            runCatching {
                base64_decode(auth_api.get_user_salt(CryptoNative.hash_email(email)).salt)
            }.getOrNull()
        }
        val salt = server_salt ?: session_key_store.get_password_salt() ?: return null
        AuthSaltGuard.require_usable_auth_salt(salt, cached_vault_bytes())
        val password_bytes = password.toByteArray(Charsets.UTF_8)
        val hash = CryptoNative.derive_pbkdf2_hash(password_bytes, salt, pbkdf2_iterations)
        password_bytes.fill(0)
        salt.fill(0)
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

    private fun build_vault_json(
        identity_private_b64: String,
        prekey_private_b64: String,
        recovery_codes: List<String>? = null,
        pgp_private_key: String? = null,
    ): String {
        val obj = org.json.JSONObject()
        obj.put("version", 1)
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

        val identity_key = session_key_store.get_identity_key() ?: return
        if (!identity_key.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return

        val passphrase_bytes = session_key_store.get_passphrase() ?: return

        try {
            encryption_api.get_pgp_key_info()
            return
        } catch (_: org.astermail.android.api.ApiError.NotFoundError) {
        } catch (_: Throwable) {
            return
        }

        republish_pgp_key_with_password(identity_key, String(passphrase_bytes, Charsets.UTF_8))
    }

    suspend fun select_signing_identity_key(): String? {
        val identity_key = session_key_store.get_identity_key() ?: return null
        if (!identity_key.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return null

        val published_fingerprint = try {
            encryption_api.get_pgp_key_info().fingerprint
        } catch (_: Throwable) {
            return identity_key
        }
        if (published_fingerprint.isBlank()) return identity_key

        matching_signing_key(published_fingerprint)?.let { return it }

        if (try_refresh_vault_keys()) {
            matching_signing_key(published_fingerprint)?.let { return it }
        }

        val current_identity = session_key_store.get_identity_key() ?: return null
        if (!current_identity.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return null

        val user_id = session_key_store.get_user_id() ?: return null
        if (!signing_heal_attempted_user_ids.add(user_id)) return null

        val passphrase_bytes = session_key_store.get_passphrase() ?: return null
        return try {
            republish_pgp_key_with_password(current_identity, String(passphrase_bytes, Charsets.UTF_8))
            current_identity
        } catch (_: Throwable) {
            null
        } finally {
            passphrase_bytes.fill(0)
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

    private suspend fun republish_pgp_key_with_password(identity_key: String, password: String) {
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
        val identity_key = session_key_store.get_identity_key() ?: return
        if (!identity_key.trimStart().startsWith("-----BEGIN PGP PRIVATE KEY")) return
        republish_pgp_key_with_password(identity_key, new_password)
    }

    private fun encrypt_pgp_private_key_for_server(
        armored_private_key: String,
        password: String,
    ): Pair<String, String> {
        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val nonce = ByteArray(12).also { rng.nextBytes(it) }

        val derived = PasswordKdf.derive_aes_key(password, salt, pgp_private_key_pbkdf2_iterations)
        val ciphertext = AesGcm.encrypt(derived, nonce, armored_private_key.toByteArray(Charsets.UTF_8))
        derived.fill(0)

        return base64_encode(salt + ciphertext) to base64_encode(nonce)
    }

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

    private fun generate_recovery_codes(): List<String> {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        return (1..6).map {
            val seg1 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            val seg2 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            val seg3 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            "ASTER-$seg1-$seg2-$seg3"
        }
    }

    private fun hash_recovery_code(code: String): String {
        val cleaned = code.uppercase().trim()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cleaned.toByteArray(Charsets.UTF_8))
        return base64_encode(hash)
    }

    private fun generate_recovery_share(code: String, recovery_key: ByteArray): RecoveryShareData {
        val code_hash = hash_recovery_code(code)
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val derived = PasswordKdf.derive_aes_key(code.uppercase().trim(), salt, pbkdf2_iterations)
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

    private data class DefaultFolder(val folder_type: String, val name_res: Int)

    private val default_folders = listOf(
        DefaultFolder("inbox", R.string.folder_inbox),
        DefaultFolder("sent", R.string.folder_sent),
        DefaultFolder("drafts", R.string.folder_drafts),
        DefaultFolder("trash", R.string.folder_trash),
        DefaultFolder("spam", R.string.folder_spam),
        DefaultFolder("archive", R.string.folder_archive),
    )

    private suspend fun create_default_folders(identity_key: String) {
        for (folder in default_folders) {
            runCatching {
                val name = context.getString(folder.name_res)
                val name_field = encrypt_folder_field(name, identity_key)
                labels_api.create_label(
                    CreateLabelRequest(
                        label_token = generate_folder_token_b64(),
                        encrypted_name = name_field.ciphertext_b64,
                        name_nonce = name_field.nonce_b64,
                        folder_type = folder.folder_type,
                    ),
                )
            }
        }
    }

    private data class EncryptedFolderField(val ciphertext_b64: String, val nonce_b64: String)

    private fun encrypt_folder_field(plaintext: String, identity_key: String): EncryptedFolderField {
        val data = plaintext.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive_folder_field_key(identity_key)
        try {
            val ct = AesGcm.encrypt(key, nonce, data)
            data.fill(0)
            return EncryptedFolderField(
                ciphertext_b64 = base64_encode(ct),
                nonce_b64 = base64_encode(nonce),
            )
        } finally {
            key.fill(0)
        }
    }

    private fun derive_folder_field_key(identity_key: String): ByteArray {
        val material = (identity_key + FOLDER_FIELD_VERSION).toByteArray(Charsets.UTF_8)
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(material)
        material.fill(0)
        return key
    }

    private fun generate_folder_token_b64(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return base64_encode(bytes)
    }

    private suspend fun clear_decrypted_mail_cache_blocking(): Boolean {
        repeat(3) { attempt ->
            val cleared = runCatching {
                withTimeoutOrNull(5_000L) { database.decrypted_mail_dao().clear_all() } != null
            }.getOrDefault(false)
            if (cleared) return true
            if (attempt < 2) delay(250L)
        }
        return false
    }

    private suspend fun pending_send_dao_clear_all() {
        database.pending_send_dao().clear_all()
    }

    private fun cached_vault_bytes(): ByteArray? =
        runCatching { session_key_store.get_encrypted_vault()?.first?.let { base64_decode(it) } }.getOrNull()

    private fun base64_encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun base64_decode(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.DEFAULT)

    private fun normalize_email(input: String): String {
        val trimmed = input.trim().lowercase()
        return if (trimmed.contains('@')) trimmed else "$trimmed@astermail.org"
    }

    companion object {
        private const val pbkdf2_iterations = 310000
        private const val vault_pbkdf2_iterations = 310000
        private const val pgp_private_key_pbkdf2_iterations = 310000
        private const val HKDF_INFO = "Aster Mail_Recovery_Vault_v1"
        private const val FOLDER_FIELD_VERSION = "astermail-labels-v1"
    }
}
