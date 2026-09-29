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

package org.astermail.android.contacts.sync

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.BuildConfig
import org.astermail.android.api.DeviceIdStore
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.auth.AuthApiImpl
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionRefresher
import org.astermail.android.api.auth.SessionTokenProvider
import org.astermail.android.api.contacts.ContactsApiImpl
import org.astermail.android.auth.AuthRepository
import org.astermail.android.auth.SessionRefreshGate
import org.astermail.android.contacts.ContactsRepository
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.SessionSnapshot
import org.astermail.android.storage.SessionSnapshotStore
import org.astermail.android.storage.TokenStore

class ContactSyncSessionLost : Exception()

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContactSyncEntryPoint {
    fun account_store(): AccountStore
    fun session_key_store(): SessionKeyStore
    fun session_snapshot_store(): SessionSnapshotStore
    fun token_store(): TokenStore
    fun session_refresher(): SessionRefresher
    fun auth_repository(): AuthRepository
}

fun contact_sync_entry_point(context: Context): ContactSyncEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, ContactSyncEntryPoint::class.java)

class ContactSyncSession internal constructor(
    val repository: ContactsRepository,
    private val client: ApiClient,
    private val keys: SessionKeyStore,
    private val still_valid: () -> Boolean,
) : AutoCloseable {
    fun ensure_active() {
        if (!still_valid()) throw ContactSyncSessionLost()
    }

    override fun close() {
        runCatching { client.close() }
        keys.clear()
    }
}

fun open_contact_sync_session(context: Context, account_id: String): ContactSyncSession? {
    val entry = contact_sync_entry_point(context)
    val account_store = entry.account_store()
    if (!account_store.account_exists(account_id)) return null
    val app_keys = entry.session_key_store()
    val is_current = account_store.get_current_id() == account_id && app_keys.get_user_id() == account_id
    return if (is_current) {
        open_current_session(context, entry, account_id)
    } else {
        val snapshot = entry.session_snapshot_store().load(account_id) ?: return null
        open_snapshot_session(context, entry, account_id, snapshot)
    }
}

private fun open_current_session(
    context: Context,
    entry: ContactSyncEntryPoint,
    account_id: String,
): ContactSyncSession? {
    val account_store = entry.account_store()
    val app_keys = entry.session_key_store()
    val token_store = entry.token_store()
    if (token_store.access_token == null) return null
    val keys = SessionKeyStore(null)
    val passphrase = app_keys.get_passphrase() ?: return null
    try {
        keys.put_passphrase(passphrase)
    } finally {
        passphrase.fill(0)
    }
    app_keys.get_identity_key()?.let { keys.put_identity_key(it) }
    app_keys.get_previous_keys()?.let { keys.put_previous_keys(it) }
    app_keys.get_legacy_keks()?.let { keys.put_legacy_keks(it) }
    app_keys.get_account_keks()?.let { keys.put_account_keks(it, keys.account_kek_generation()) }
    app_keys.get_data_kek()?.let { kek ->
        keys.put_data_kek(kek)
        kek.fill(0)
    }
    val still_current = {
        account_store.get_current_id() == account_id && app_keys.get_user_id() == account_id
    }
    lateinit var client: ApiClient
    val refresher = entry.session_refresher()
    val provider = SessionTokenProvider(
        read_access_token = { if (still_current()) token_store.access_token else null },
        read_refresh_token = { if (still_current()) token_store.refresh_token else null },
        refresh_session = { if (still_current()) refresher.refresh() else RefreshOutcome.AuthFailed },
    )
    client = ApiClient(
        base_url = BuildConfig.API_BASE_URL,
        token_provider = provider,
        initial_csrf = token_store.csrf_token,
        allow_cleartext_for_test = BuildConfig.API_BASE_URL.startsWith("http://"),
        device_id = DeviceIdStore.get(context),
        csrf_refresher = {
            if (still_current() && refresher.refresh() == RefreshOutcome.Success) {
                token_store.csrf_token?.also { client.set_csrf(it) }
            } else {
                null
            }
        },
    )
    return ContactSyncSession(
        repository = ContactsRepository(ContactsApiImpl(client), keys),
        client = client,
        keys = keys,
        still_valid = still_current,
    )
}

private fun open_snapshot_session(
    context: Context,
    entry: ContactSyncEntryPoint,
    account_id: String,
    snapshot: SessionSnapshot,
): ContactSyncSession? {
    val account_store = entry.account_store()
    val snapshot_store = entry.session_snapshot_store()
    val passphrase = snapshot.passphrase ?: return null
    val keys = SessionKeyStore(null)
    try {
        keys.put_passphrase(passphrase)
    } finally {
        passphrase.fill(0)
    }
    snapshot.identity_key?.let { keys.put_identity_key(it) }
    snapshot.previous_keys?.let { keys.put_previous_keys(it) }
    snapshot.legacy_keks?.let { keys.put_legacy_keks(it) }

    var lost = false
    var tokens = BearerTokens(snapshot.token_access, snapshot.token_refresh)
    lateinit var client: ApiClient

    suspend fun refresh_tokens(): BearerTokens? = SessionRefreshGate.mutex.withLock {
        if (account_store.get_current_id() == account_id || !account_store.account_exists(account_id)) {
            lost = true
            return@withLock null
        }
        val stored = snapshot_store.load(account_id)
        if (stored == null) {
            lost = true
            return@withLock null
        }
        stored.passphrase?.fill(0)
        if (stored.token_refresh != tokens.refreshToken) {
            tokens = BearerTokens(stored.token_access, stored.token_refresh)
            client.set_csrf(stored.csrf_token)
            return@withLock tokens
        }
        val presented = tokens.refreshToken
        val response = try {
            AuthApiImpl(client).refresh(presented)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (t is ApiError.UnauthorizedError || t is ApiError.ForbiddenError) lost = true
            return@withLock null
        }
        val rotated = response.refresh_token ?: presented ?: response.access_token
        tokens = BearerTokens(response.access_token, rotated)
        snapshot_store.update_tokens(account_id, presented, response.access_token, rotated, client.get_csrf())
        tokens
    }

    val provider = object : TokenProvider {
        override suspend fun load(): BearerTokens = tokens
        override suspend fun refresh(): BearerTokens? = refresh_tokens()
        override suspend fun clear() {}
    }
    client = ApiClient(
        base_url = BuildConfig.API_BASE_URL,
        token_provider = provider,
        initial_csrf = snapshot.csrf_token,
        allow_cleartext_for_test = BuildConfig.API_BASE_URL.startsWith("http://"),
        device_id = DeviceIdStore.get(context),
        csrf_refresher = { refresh_tokens()?.let { client.get_csrf() } },
    )
    return ContactSyncSession(
        repository = ContactsRepository(ContactsApiImpl(client), keys),
        client = client,
        keys = keys,
        still_valid = { !lost && account_store.get_current_id() != account_id },
    )
}
