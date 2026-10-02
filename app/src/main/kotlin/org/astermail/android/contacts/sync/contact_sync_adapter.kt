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

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.astermail.android.R
import org.astermail.android.api.ApiError
import java.io.IOException
import java.time.Instant

class ContactSyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, true, false) {

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        result: SyncResult,
    ) {
        val app = context.applicationContext
        if (!ContactSyncAccounts.is_enabled(app) || !ContactSyncAccounts.has_permissions(app)) {
            ContactSyncAccounts.reconcile(app)
            return
        }
        val aster_id = ContactSyncAccounts.aster_account_id(app, account) ?: return
        val session = open_contact_sync_session(app, aster_id)
        if (session == null) {
            Log.w(TAG, "contact sync skipped: no session")
            result.stats.numAuthExceptions++
            return
        }
        val labels = DeviceLabels(
            sibling = app.getString(R.string.contact_sync_label_sibling),
            graduation = app.getString(R.string.contact_sync_label_graduation),
            wedding = app.getString(R.string.contact_sync_label_wedding),
            personal = app.getString(R.string.personal),
        )
        val cursor = object : ContactSyncCursor {
            override fun load(): Long = ContactSyncAccounts.load_since(app, account)
            override fun save(since: Long) = ContactSyncAccounts.save_since(app, account, since)
        }
        val options = ContactSyncOptions(
            override_too_many_deletions = extras.getBoolean(ContentResolver.SYNC_EXTRAS_OVERRIDE_TOO_MANY_DELETIONS),
            discard_local_deletions = extras.getBoolean(ContentResolver.SYNC_EXTRAS_DISCARD_LOCAL_DELETIONS),
        )
        session.use { active ->
            val engine = ContactSyncEngine(
                device = DeviceContactsStore(provider, account, labels),
                remote = RepositoryContactSyncRemote(active.repository),
                cursor = cursor,
                ensure_active = {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    active.ensure_active()
                },
                now_iso = { Instant.now().toString() },
            )
            try {
                val stats = runBlocking { engine.sync(options) }
                result.stats.numInserts += stats.inserted_local.toLong()
                result.stats.numUpdates += stats.updated_local.toLong()
                result.stats.numDeletes += stats.deleted_local.toLong()
                if (stats.too_many_deletions) {
                    result.tooManyDeletions = true
                    result.stats.numDeletes = stats.pending_deletions.toLong()
                }
                log_stats(stats)
                if (stats.created_remote + stats.updated_remote + stats.deleted_remote +
                    stats.inserted_local + stats.updated_local + stats.deleted_local > 0
                ) {
                    ContactSyncAccounts.notify_remote_updated()
                }
            } catch (_: ContactSyncSessionLost) {
            } catch (_: InterruptedException) {
            } catch (e: ApiError.UnauthorizedError) {
                log_failure(e)
                result.stats.numAuthExceptions++
            } catch (e: ApiError.ForbiddenError) {
                log_failure(e)
                result.stats.numAuthExceptions++
            } catch (e: ApiError.NetworkError) {
                log_failure(e)
                result.stats.numIoExceptions++
            } catch (e: IOException) {
                log_failure(e)
                result.stats.numIoExceptions++
            } catch (e: ContactResyncRequired) {
                log_failure(e)
                result.stats.numIoExceptions++
            } catch (e: Exception) {
                log_failure(e)
                result.databaseError = true
            }
        }
    }

    private fun log_failure(e: Exception) {
        Log.w(TAG, "contact sync failed: ${e.javaClass.simpleName}")
    }

    private fun log_stats(stats: ContactSyncStats) {
        if (!org.astermail.android.BuildConfig.DEBUG) return
        Log.d(TAG, "contact sync $stats")
    }

    private companion object {
        const val TAG = "ContactSync"
    }
}

class ContactSyncAdapterService : Service() {
    override fun onCreate() {
        super.onCreate()
        synchronized(lock) {
            if (adapter == null) adapter = ContactSyncAdapter(applicationContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = adapter?.syncAdapterBinder

    private companion object {
        val lock = Any()
        var adapter: ContactSyncAdapter? = null
    }
}
