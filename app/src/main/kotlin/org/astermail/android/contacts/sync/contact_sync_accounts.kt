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

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.astermail.android.R

object ContactSyncAccounts {
    const val AUTHORITY = ContactsContract.AUTHORITY
    const val KEY_ACCOUNT_ID = "aster_account_id"
    const val KEY_SINCE = "contact_sync_since"
    const val KEY_SCHEMA = "contact_sync_schema"
    private const val PREFS = "contact_sync"
    private const val PREF_ENABLED = "enabled"
    private const val PREF_LINKED = "linked_ids"
    private const val PREF_DECLINED = "declined_ids"
    private const val PERIODIC_SECONDS = 4L * 60L * 60L

    private const val WATCH_SETTLE_MS = 500L
    private const val LOCAL_CHANGE_SETTLE_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enabled_state = MutableStateFlow<Boolean?>(null)
    private val local_changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val remote_updates = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val remote_updated: SharedFlow<Unit> = remote_updates
    private var watching = false

    fun account_type(context: Context): String = context.getString(R.string.contact_sync_account_type)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun is_enabled(context: Context): Boolean =
        enabled_state.value ?: prefs(context).getBoolean(PREF_ENABLED, false).also { enabled_state.value = it }

    fun enabled_flow(context: Context): StateFlow<Boolean?> {
        is_enabled(context)
        return enabled_state.asStateFlow()
    }

    fun set_enabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(PREF_ENABLED, enabled)
            .remove(PREF_DECLINED)
            .remove(PREF_LINKED)
            .commit()
        enabled_state.value = enabled
        reconcile(context)
        if (enabled) watch(context)
    }

    fun start(context: Context) {
        if (is_enabled(context)) {
            watch(context)
        } else if (system_accounts(context).isNotEmpty()) {
            val app = context.applicationContext
            scope.launch { runCatching { reconcile(app) } }
        }
    }

    @Synchronized
    private fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        val auth = contact_sync_entry_point(app).auth_repository()
        scope.launch {
            local_changes.collectLatest {
                delay(LOCAL_CHANGE_SETTLE_MS)
                request_sync_current(app)
            }
        }
        scope.launch {
            combine(auth.is_signed_in, auth.active_account_id) { signed_in, id -> signed_in to id }
                .distinctUntilChanged()
                .collectLatest { (_, id) ->
                    delay(WATCH_SETTLE_MS)
                    runCatching { reconcile(app) }
                    if (id != null) request_sync_for(app, id)
                }
        }
    }

    fun has_permissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun system_accounts(context: Context): List<Account> =
        runCatching { AccountManager.get(context).getAccountsByType(account_type(context)).toList() }
            .getOrDefault(emptyList())

    fun aster_account_id(context: Context, account: Account): String? =
        runCatching { AccountManager.get(context).getUserData(account, KEY_ACCOUNT_ID) }.getOrNull()

    fun system_account_for(context: Context, aster_account_id: String): Account? =
        system_accounts(context).firstOrNull { aster_account_id(context, it) == aster_account_id }

    @Synchronized
    fun reconcile(context: Context) {
        val app = context.applicationContext
        val manager = AccountManager.get(app)
        val type = account_type(app)
        val prefs = prefs(app)
        val linked_before = prefs.getStringSet(PREF_LINKED, emptySet()).orEmpty()
        val declined = prefs.getStringSet(PREF_DECLINED, emptySet()).orEmpty().toMutableSet()
        val present_before = system_accounts(app).mapNotNull { aster_account_id(app, it) }.toSet()
        declined.addAll(linked_before - present_before)
        val desired = if (is_enabled(app) && has_permissions(app)) {
            eligible_accounts(app).filterKeys { it !in declined }
        } else {
            emptyMap()
        }
        for (account in system_accounts(app)) {
            val id = aster_account_id(app, account)
            if (id == null || desired[id] != account.name) {
                runCatching { manager.removeAccountExplicitly(account) }
            }
        }
        val kept = system_accounts(app).mapNotNull { aster_account_id(app, it) }.toSet()
        for ((id, email) in desired) {
            if (id in kept) continue
            val account = Account(email, type)
            val data = Bundle().apply { putString(KEY_ACCOUNT_ID, id) }
            val added = runCatching { manager.addAccountExplicitly(account, null, data) }.getOrDefault(false)
            if (!added) continue
            ContentResolver.setIsSyncable(account, AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, AUTHORITY, true)
            ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, PERIODIC_SECONDS)
            make_ungrouped_visible(app, account)
            request_sync(account, manual = true)
        }
        val linked_now = system_accounts(app).mapNotNull { aster_account_id(app, it) }.toSet()
        val known_ids = contact_sync_entry_point(app).account_store().get_all().map { it.id }.toSet()
        prefs.edit()
            .putStringSet(PREF_LINKED, linked_now)
            .putStringSet(PREF_DECLINED, declined.intersect(known_ids))
            .commit()
    }

    private fun eligible_accounts(context: Context): Map<String, String> {
        val entry = contact_sync_entry_point(context)
        val account_store = entry.account_store()
        val snapshots = entry.session_snapshot_store()
        val current_id = account_store.get_current_id()
        val current_signed_in = entry.token_store().access_token != null &&
            entry.session_key_store().get_user_id() == current_id
        return account_store.get_all()
            .filter { it.email.isNotBlank() }
            .filter { (it.id == current_id && current_signed_in) || snapshots.has(it.id) }
            .associate { it.id to it.email }
    }

    private fun make_ungrouped_visible(context: Context, account: Account) {
        val uri = ContactsContract.Settings.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()
        val values = ContentValues().apply {
            put(ContactsContract.Settings.ACCOUNT_NAME, account.name)
            put(ContactsContract.Settings.ACCOUNT_TYPE, account.type)
            put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            put(ContactsContract.Settings.SHOULD_SYNC, 1)
        }
        runCatching { context.contentResolver.insert(uri, values) }
    }

    fun request_sync(account: Account, manual: Boolean = false, extras: Bundle = Bundle.EMPTY) {
        val bundle = Bundle(extras).apply {
            if (manual) {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
        }
        runCatching { ContentResolver.requestSync(account, AUTHORITY, bundle) }
    }

    fun request_sync_for(context: Context, aster_account_id: String, manual: Boolean = false) {
        if (!is_enabled(context)) return
        system_account_for(context, aster_account_id)?.let { request_sync(it, manual) }
    }

    fun notify_local_change() {
        local_changes.tryEmit(Unit)
    }

    fun notify_remote_updated() {
        remote_updates.tryEmit(Unit)
    }

    fun request_sync_current_async(context: Context) {
        val app = context.applicationContext
        scope.launch { request_sync_current(app) }
    }

    private fun request_sync_current(context: Context) {
        if (!is_enabled(context)) return
        val id = runCatching { contact_sync_entry_point(context).account_store().get_current_id() }.getOrNull() ?: return
        request_sync_for(context, id, manual = true)
    }

    fun request_sync_all(context: Context, manual: Boolean = false) {
        if (!is_enabled(context)) return
        system_accounts(context).forEach { request_sync(it, manual) }
    }

    fun load_since(context: Context, account: Account): Long {
        val manager = AccountManager.get(context)
        val schema = runCatching { manager.getUserData(account, KEY_SCHEMA)?.toIntOrNull() }.getOrNull() ?: 0
        if (schema < CARD_SCHEMA) return 0L
        return runCatching { manager.getUserData(account, KEY_SINCE)?.toLongOrNull() }.getOrNull() ?: 0L
    }

    fun save_since(context: Context, account: Account, since: Long) {
        runCatching {
            val manager = AccountManager.get(context)
            manager.setUserData(account, KEY_SINCE, since.toString())
            manager.setUserData(account, KEY_SCHEMA, CARD_SCHEMA.toString())
        }
    }
}
