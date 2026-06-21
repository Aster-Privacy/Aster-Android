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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.auth.Argon2Params
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.DeleteAccountRequest
import org.astermail.android.api.auth.LoginRequest
import org.astermail.android.api.auth.LoginResponse
import org.astermail.android.api.auth.LoginResult
import org.astermail.android.api.auth.RegisterRequest
import org.astermail.android.api.auth.TotpLoginVerifyRequest
import org.astermail.android.api.settings.ChangePasswordRequest
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.RecoveryKeyResult
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshotStore
import org.astermail.android.storage.StoredAccount
import org.astermail.android.notifications.UnifiedPushState
import org.astermail.android.security.LockdownStore
import org.astermail.android.storage.ThemeStore
import org.astermail.android.storage.TokenStore
import org.astermail.android.storage.TrustedDeviceStore

data class RegisterSuccess(val recovery: RecoveryKeyResult)

data class TotpChallenge(
    val pending_login_token: String,
    val available_methods: List<String>,
    val password_hash_bytes: ByteArray,
    val password_bytes: ByteArray,
    val salt_bytes: ByteArray,
    val email: String,
    val remember_me: Boolean,
)

sealed interface LoginOutcome {
    data object Success : LoginOutcome
    data class NeedsTotp(val challenge: TotpChallenge) : LoginOutcome
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth_api: AuthApi,
    private val settings_api: SettingsApi,
    private val api_client: ApiClient,
    private val token_store: TokenStore,
    private val session_key_store: SessionKeyStore,
    private val account_store: AccountStore,
    private val database: org.astermail.android.storage.search.AsterDatabase,
    private val session_snapshot_store: SessionSnapshotStore,
    private val trusted_device_store: TrustedDeviceStore,
    private val mail_repository: org.astermail.android.mail.MailRepository,
    private val theme_store: ThemeStore,
    @ApplicationContext private val context: Context,
) {

    private val _is_signed_in = MutableStateFlow(token_store.access_token != null)
    val is_signed_in: StateFlow<Boolean> = _is_signed_in.asStateFlow()

    private val unauthorized_check_running = java.util.concurrent.atomic.AtomicBoolean(false)

    suspend fun handle_unauthorized_signal() {
        if (!_is_signed_in.value) return
        if (!unauthorized_check_running.compareAndSet(false, true)) return
        try {
            auth_api.me()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.UnauthorizedError) {
            // The access token has likely expired. Ktor's bearer auth only
            // auto-refreshes on a 401 carrying a WWW-Authenticate challenge,
            // which the backend does not send, so we refresh explicitly before
            // giving up. Only sign out if the refresh itself fails.
            if (try_refresh_session()) {
                try {
                    auth_api.me()
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: ApiError.UnauthorizedError) {
                    logout()
                } catch (_: Throwable) {
                }
            } else {
                logout()
            }
        } catch (_: Throwable) {
        } finally {
            unauthorized_check_running.set(false)
        }
    }

    private suspend fun try_refresh_session(): Boolean {
        return try {
            val current_refresh = token_store.refresh_token ?: return false
            val response = auth_api.refresh(current_refresh)
            val new_refresh = response.refresh_token ?: current_refresh
            token_store.save(response.access_token, new_refresh)
            api_client.invalidate_bearer_cache()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun login(email: String, password: String, captcha_token: String? = null): Result<LoginOutcome> = runCatching {
        val normalized = normalize_email(email)
        val trusted_token = trusted_device_store.get_token(normalized)
        val user_hash = CryptoNative.hash_email(normalized)
        val salt_resp = auth_api.get_user_salt(user_hash)
        val salt_bytes = base64_decode(salt_resp.salt)
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
            runCatching {
                withTimeoutOrNull(3_000L) { database.decrypted_mail_dao().clear_all() }
            }
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
            val prev_keys_array = vault_obj.optJSONArray("previous_keys")
            if (prev_keys_array != null) {
                val keys = (0 until prev_keys_array.length()).map { prev_keys_array.getString(it) }
                session_key_store.put_previous_keys(keys)
            }
            val keks_array = vault_obj.optJSONArray("legacy_keks")
            if (keks_array != null) {
                val keks = (0 until keks_array.length()).mapNotNull { i ->
                    keks_array.optJSONObject(i)?.optString("k", "")?.takeIf { it.isNotBlank() }
                }
                if (keks.isNotEmpty()) session_key_store.put_legacy_keks(keks)
            }
            extract_ratchet_keys(vault_obj)
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("AuthRepository", "vault decryption failed: ${t.javaClass.simpleName}")
        }

        password_bytes.fill(0)
        password_hash_bytes.fill(0)

        val profile = runCatching { withTimeoutOrNull(8_000L) { auth_api.me() } }.getOrNull()
        profile?.let { LockdownStore.set_enabled(context, it.lockdown_mode_enabled) }
        account_store.add_or_update(
            StoredAccount(
                id = login_resp.user_id,
                email = login_resp.email,
                display_name = profile?.display_name,
                profile_color = profile?.profile_color,
                profile_picture = profile?.profile_picture,
                added_at = System.currentTimeMillis(),
            ),
        )
        runCatching { save_session_snapshot(login_resp.user_id) }
        _is_signed_in.value = true
        runCatching { UnifiedPushState.try_register(context) }
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
        val recovery = CryptoNative.generate_recovery_key()

        val passphrase_chars = password.toCharArray()
        val pgp_keys = try {
            PgpKeyGenerator.generate(username, canonical_email, passphrase_chars)
        } catch (_: Throwable) {
            null
        } finally {
            passphrase_chars.fill(' ')
        }

        val vault_json = build_vault_json(
            identity_private_b64 = base64_encode(identity.private_key),
            prekey_private_b64 = base64_encode(prekey.private_key),
            recovery_mnemonic = recovery.mnemonic,
            pgp_private_key = pgp_keys?.armored_private_key,
        )
        val vault_plaintext = vault_json.toByteArray(Charsets.UTF_8)
        val raw_password_bytes = password.toByteArray(Charsets.UTF_8)
        val vault_envelope = CryptoNative.encrypt_vault_with_password(vault_plaintext, raw_password_bytes)
        raw_password_bytes.fill(0)
        vault_plaintext.fill(0)

        val register_resp = auth_api.register(
            RegisterRequest(
                username = username,
                user_hash = user_hash,
                password_hash = base64_encode(password_hash_bytes),
                password_salt = base64_encode(salt_bytes),
                argon2_params = Argon2Params(memory = 65536, iterations = 3, parallelism = 4),
                identity_key = pgp_keys?.armored_public_key ?: base64_encode(identity.public_key),
                signed_prekey = base64_encode(prekey.public_key),
                signed_prekey_signature = base64_encode(signature),
                encrypted_vault = base64_encode(vault_envelope.encrypted_vault),
                vault_nonce = base64_encode(vault_envelope.vault_nonce),
                email_domain = email_domain,
                remember_me = true,
                captcha_token = captcha_token,
            ),
        )

        val access = register_resp.access_token
            ?: throw ApiError.UnknownError("missing access_token on register")
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

        identity.private_key.fill(0)
        prekey.private_key.fill(0)
        signature.fill(0)
        password_hash_bytes.fill(0)

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
        runCatching { UnifiedPushState.try_register(context) }
        RegisterSuccess(recovery = recovery)
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

    suspend fun try_restore_session(account_id: String): Boolean {
        val snapshot = session_snapshot_store.load(account_id) ?: return false
        runCatching { database.decrypted_mail_dao().clear_all() }
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
        return true
    }

    fun has_stored_session(account_id: String): Boolean = session_snapshot_store.has(account_id)

    suspend fun change_password(current_password: String, new_password: String): Result<Unit> = runCatching {
        require(new_password.length >= 8) { "new password must be at least 8 characters" }
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
            ?: throw ApiError.UnknownError("session expired â€” please sign in again")

        val current_password_hash = CryptoNative.derive_pbkdf2_hash(
            current_password_bytes, stored_salt, pbkdf2_iterations,
        )

        val (encrypted_vault_b64, vault_nonce_b64) = session_key_store.get_encrypted_vault()
            ?: throw ApiError.UnknownError("vault unavailable â€” please sign in again")

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
        response.access_token?.let { token_store.save(it, it) }

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

    suspend fun logout(): Result<Unit> = runCatching {
        val current_id = session_key_store.get_user_id()
        token_store.clear()
        api_client.invalidate_bearer_cache()
        session_key_store.clear()
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
        if (current_id != null) {
            account_store.remove(current_id)
            runCatching { session_snapshot_store.remove(current_id) }
        }
        _is_signed_in.value = false
        try {
            withTimeoutOrNull(5_000L) {
                auth_api.logout()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun refresh_profile(): Result<Unit> = runCatching {
        val profile = auth_api.me()
        val current_id = session_key_store.get_user_id() ?: profile.user_id
        val email = session_key_store.get_user_email() ?: profile.email ?: return@runCatching
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
                val prev_keys_array = vault_obj.optJSONArray("previous_keys")
                if (prev_keys_array != null) {
                    val keys = (0 until prev_keys_array.length()).map { prev_keys_array.getString(it) }
                    session_key_store.put_previous_keys(keys)
                }
                val keks_array = vault_obj.optJSONArray("legacy_keks")
                if (keks_array != null) {
                    val keks = (0 until keks_array.length()).mapNotNull { i ->
                        keks_array.optJSONObject(i)?.optString("k", "")?.takeIf { it.isNotBlank() }
                    }
                    if (keks.isNotEmpty()) session_key_store.put_legacy_keks(keks)
                }
            }
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
                val old_identity_key = session_key_store.get_identity_key()
                val new_identity_key = vault_obj.optString("identity_key", "")
                    .ifBlank { vault_obj.optString("identity_private_key", "") }
                if (new_identity_key.isNotBlank()) {
                    session_key_store.put_identity_key(new_identity_key)
                }
                val prev_keys_array = vault_obj.optJSONArray("previous_keys")
                if (prev_keys_array != null) {
                    val keys = (0 until prev_keys_array.length()).map { prev_keys_array.getString(it) }
                    session_key_store.put_previous_keys(keys)
                }
                val keks_array = vault_obj.optJSONArray("legacy_keks")
                if (keks_array != null) {
                    val keks = (0 until keks_array.length()).mapNotNull { i ->
                        keks_array.optJSONObject(i)?.optString("k", "")?.takeIf { it.isNotBlank() }
                    }
                    if (keks.isNotEmpty()) session_key_store.put_legacy_keks(keks)
                }
                new_identity_key.isNotBlank() && new_identity_key != old_identity_key
            } finally {
                passphrase.fill(0)
            }
        } catch (_: Throwable) {
            false
        }
    }

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
        _is_signed_in.value = false
    }

    fun derive_password_hash_b64(password: String): String? {
        val salt = session_key_store.get_password_salt() ?: return null
        val password_bytes = password.toByteArray(Charsets.UTF_8)
        val hash = CryptoNative.derive_pbkdf2_hash(password_bytes, salt, pbkdf2_iterations)
        password_bytes.fill(0)
        salt.fill(0)
        val encoded = base64_encode(hash)
        hash.fill(0)
        return encoded
    }

    private fun extract_ratchet_keys(vault_obj: org.json.JSONObject) {
        val identity_jwk = vault_obj.optString("ratchet_identity_key", "")
        val identity_public = vault_obj.optString("ratchet_identity_public", "")
        val spk_jwk = vault_obj.optString("ratchet_signed_prekey", "")
        val spk_public = vault_obj.optString("ratchet_signed_prekey_public", "")
        if (identity_jwk.isNotBlank() && spk_jwk.isNotBlank() && spk_public.isNotBlank()) {
            session_key_store.put_ratchet_keys(
                identity_jwk = identity_jwk,
                identity_public_b64 = identity_public,
                signed_prekey_jwk = spk_jwk,
                signed_prekey_public_b64 = spk_public,
            )
        }
    }

    private fun build_vault_json(
        identity_private_b64: String,
        prekey_private_b64: String,
        recovery_mnemonic: String? = null,
        pgp_private_key: String? = null,
    ): String {
        val obj = org.json.JSONObject()
        obj.put("version", 1)
        obj.put("identity_private_key", identity_private_b64)
        obj.put("signed_prekey_private", prekey_private_b64)
        obj.put("created_at", System.currentTimeMillis() / 1000L)
        if (recovery_mnemonic != null) {
            obj.put("recovery_codes", org.json.JSONArray().put(recovery_mnemonic))
        }
        if (pgp_private_key != null) {
            obj.put("identity_key", pgp_private_key)
        }
        return obj.toString()
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

        val password_chars = String(password_bytes, Charsets.UTF_8).toCharArray()
        val key_spec = PBEKeySpec(password_chars, salt, vault_pbkdf2_iterations, 256)
        password_chars.fill(' ')
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(key_spec).encoded
        key_spec.clearPassword()

        val secret_key = SecretKeySpec(derived, "AES")
        derived.fill(0)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret_key, GCMParameterSpec(128, nonce))
        return cipher.doFinal(ciphertext)
    }

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
    }
}
