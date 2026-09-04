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
package org.astermail.android.accountlink

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.BuildConfig as ApiBuildConfig
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.auth.AuthApiImpl
import org.astermail.android.api.devices.DeviceCodeApi
import org.astermail.android.api.devices.DeviceCodeApiImpl
import org.astermail.android.api.devices.DeviceLinkError
import org.astermail.android.api.devices.PendingDevice
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.DeviceLinkBinding
import org.astermail.android.crypto.zeroize
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshot
import org.astermail.android.storage.SessionSnapshotStore

class AccountLinkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccountLinkEntryPoint {
        fun account_store(): AccountStore
        fun session_key_store(): SessionKeyStore
        fun session_snapshot_store(): SessionSnapshotStore
        fun device_code_api(): DeviceCodeApi
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (!caller_shares_signature()) return error_bundle(ERROR_FORBIDDEN)
        val app = context?.applicationContext ?: return error_bundle(ERROR_UNAVAILABLE)
        val entry = EntryPointAccessors.fromApplication(app, AccountLinkEntryPoint::class.java)
        return when (method) {
            METHOD_ACCOUNTS -> list_accounts(entry)
            METHOD_LINK -> link_account(
                entry,
                code = extras?.getString(EXTRA_CODE).orEmpty(),
                account_id = extras?.getString(EXTRA_ACCOUNT_ID).orEmpty(),
                binding_tag = extras?.getString(EXTRA_BINDING_TAG).orEmpty(),
            )
            else -> error_bundle(ERROR_UNKNOWN_METHOD)
        }
    }

    private fun list_accounts(entry: AccountLinkEntryPoint): Bundle {
        val current_id = entry.account_store().get_current_id()
        val rows = entry.account_store().get_all().mapNotNull { account ->
            val is_current = account.id == current_id
            val passphrase = if (is_current) {
                entry.session_key_store().get_passphrase()
            } else {
                entry.session_snapshot_store().load(account.id)?.passphrase
            } ?: return@mapNotNull null
            zeroize(passphrase)
            Bundle().apply {
                putString(KEY_ID, account.id)
                putString(KEY_EMAIL, account.email)
                putString(KEY_DISPLAY_NAME, account.display_name)
                putString(KEY_PROFILE_COLOR, account.profile_color)
                putBoolean(KEY_IS_CURRENT, is_current)
            }
        }
        return Bundle().apply { putParcelableArrayList(KEY_ACCOUNTS, ArrayList(rows)) }
    }

    private fun link_account(
        entry: AccountLinkEntryPoint,
        code: String,
        account_id: String,
        binding_tag: String,
    ): Bundle {
        if (code.isBlank() || account_id.isBlank()) return error_bundle(ERROR_BAD_REQUEST)
        if (!entry.account_store().account_exists(account_id)) return error_bundle(ERROR_NO_ACCOUNT)
        val is_current = entry.account_store().get_current_id() == account_id
        return try {
            if (is_current) {
                val passphrase = entry.session_key_store().get_passphrase() ?: return error_bundle(ERROR_LOCKED)
                confirm(entry.device_code_api(), code, passphrase, binding_tag)
            } else {
                val snapshot = entry.session_snapshot_store().load(account_id) ?: return error_bundle(ERROR_NO_ACCOUNT)
                val passphrase = snapshot.passphrase ?: return error_bundle(ERROR_LOCKED)
                with_snapshot_client(entry.session_snapshot_store(), account_id, snapshot) { client ->
                    confirm(DeviceCodeApiImpl(client), code, passphrase, binding_tag)
                }
            }
            Bundle().apply { putBoolean(KEY_OK, true) }
        } catch (t: Throwable) {
            error_bundle(
                when (t) {
                    is DeviceLinkBinding.BindingMismatchException -> ERROR_BINDING
                    is DeviceLinkError.CodeNotFound -> ERROR_CODE_NOT_FOUND
                    is DeviceLinkError.PlanUpgradeRequired -> ERROR_PLAN
                    is ApiError.UnauthorizedError, is ApiError.ForbiddenError -> ERROR_SESSION
                    else -> ERROR_UNAVAILABLE
                },
            )
        }
    }

    private fun confirm(
        api: DeviceCodeApi,
        code: String,
        passphrase: ByteArray,
        binding_tag: String,
    ) {
        try {
            runBlocking {
                val pending = api.verify_code(code)
                verify_binding(pending, code, binding_tag)
                val envelope = seal_for(pending, passphrase)
                api.confirm_code(code, envelope)
            }
        } finally {
            zeroize(passphrase)
        }
    }

    private fun verify_binding(pending: PendingDevice, code: String, binding_tag: String) {
        val offered = binding_tag.ifBlank { pending.binding_tag }
        if (offered.isBlank()) return
        DeviceLinkBinding.require_match(
            code = code,
            ed25519_pk = pending.ed25519_pk,
            mlkem_pk = pending.mlkem_pk,
            x25519_pk = pending.x25519_pk,
            offered_tag = offered,
        )
    }

    private fun seal_for(pending: PendingDevice, passphrase: ByteArray): String {
        val ed25519_pk = DeviceEnvelope.base64url_decode(pending.ed25519_pk)
        val mlkem_pk = DeviceEnvelope.base64url_decode(pending.mlkem_pk)
        val x25519_pk = DeviceEnvelope.base64url_decode(pending.x25519_pk)
        require(ed25519_pk.size == DeviceEnvelope.ED25519_PK_BYTES) { "bad ed25519 key" }
        val sealed = DeviceEnvelope.seal_secret_for_device(
            secret = passphrase,
            device_mlkem_pk = mlkem_pk,
            device_x25519_pk = x25519_pk,
        )
        return DeviceEnvelope.base64url_encode(sealed)
    }

    private inline fun with_snapshot_client(
        store: SessionSnapshotStore,
        account_id: String,
        snapshot: SessionSnapshot,
        block: (ApiClient) -> Unit,
    ) {
        var tokens = BearerTokens(snapshot.token_access, snapshot.token_refresh)
        lateinit var client: ApiClient
        val provider = object : TokenProvider {
            override suspend fun load(): BearerTokens = tokens
            override suspend fun refresh(): BearerTokens? {
                val response = try {
                    AuthApiImpl(client).refresh(tokens.refreshToken)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return if (t is ApiError.UnauthorizedError || t is ApiError.ForbiddenError) null else tokens
                }
                val refresh = response.refresh_token ?: tokens.refreshToken ?: response.access_token
                tokens = BearerTokens(response.access_token, refresh)
                client.set_csrf(response.csrf_token)
                persist(store, account_id, snapshot, tokens, response.csrf_token)
                return tokens
            }
            override suspend fun clear() {}
        }
        client = ApiClient(
            base_url = ApiBuildConfig.API_BASE_URL,
            allow_cleartext_for_test = ApiBuildConfig.API_BASE_URL.startsWith("http://"),
            token_provider = provider,
            initial_csrf = snapshot.csrf_token,
        )
        try {
            block(client)
        } finally {
            client.close()
        }
    }

    private fun persist(
        store: SessionSnapshotStore,
        account_id: String,
        snapshot: SessionSnapshot,
        tokens: BearerTokens,
        csrf_token: String?,
    ) {
        store.save(
            account_id = account_id,
            token_access = tokens.accessToken,
            token_refresh = tokens.refreshToken,
            csrf_token = csrf_token ?: snapshot.csrf_token,
            session_key = snapshot.session_key,
            passphrase = snapshot.passphrase,
            identity_key = snapshot.identity_key,
            encrypted_vault = snapshot.encrypted_vault,
            vault_nonce = snapshot.vault_nonce,
            password_salt = snapshot.password_salt,
            user_id = snapshot.user_id,
            user_email = snapshot.user_email,
            recovery_codes = snapshot.recovery_codes,
            previous_keys = snapshot.previous_keys,
            legacy_keks = snapshot.legacy_keks,
        )
    }

    private fun caller_shares_signature(): Boolean {
        val pm = context?.packageManager ?: return false
        val caller = Binder.getCallingUid()
        if (caller == Process.myUid()) return true
        return pm.checkSignatures(caller, Process.myUid()) == PackageManager.SIGNATURE_MATCH
    }

    private fun error_bundle(code: String): Bundle = Bundle().apply {
        putBoolean(KEY_OK, false)
        putString(KEY_ERROR, code)
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0

    companion object {
        const val METHOD_ACCOUNTS = "accounts"
        const val METHOD_LINK = "link"
        const val EXTRA_CODE = "code"
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_BINDING_TAG = "binding_tag"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_ID = "id"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PROFILE_COLOR = "profile_color"
        const val KEY_IS_CURRENT = "is_current"
        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        const val ERROR_FORBIDDEN = "forbidden"
        const val ERROR_UNKNOWN_METHOD = "unknown_method"
        const val ERROR_BAD_REQUEST = "bad_request"
        const val ERROR_NO_ACCOUNT = "no_account"
        const val ERROR_LOCKED = "locked"
        const val ERROR_CODE_NOT_FOUND = "code_not_found"
        const val ERROR_BINDING = "binding_mismatch"
        const val ERROR_PLAN = "plan"
        const val ERROR_SESSION = "session"
        const val ERROR_UNAVAILABLE = "unavailable"
    }
}
