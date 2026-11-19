//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail.ratchet

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.astermail.android.storage.LockKeyHolder

suspend fun settle_ratchet_vault(sync_vault: RatchetSyncVault, syncer: () -> RatchetStateSyncer): Boolean {
    if (sync_vault.unconfirmed().isNotEmpty()) syncer().settle_intents()
    return sync_vault.flush_pending()
}

@Singleton
class RatchetVaultFlushTriggers @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sync_vault: RatchetSyncVault,
    private val syncer: dagger.Lazy<RatchetStateSyncer>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val running = Mutex()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        settle()
        LockKeyHolder.add_open_listener { settle() }
        runCatching {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    settle()
                }
            })
        }
    }

    fun settle() {
        scope.launch {
            if (!running.tryLock()) return@launch
            try {
                settle_ratchet_vault(sync_vault) { syncer.get() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            } finally {
                running.unlock()
            }
        }
    }
}
