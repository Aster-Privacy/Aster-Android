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

package org.astermail.android.mail

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.storage.actions.PendingMailActionDao
import org.astermail.android.storage.actions.PendingMailActionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingMailActionQueue @Inject constructor(
    private val dao_provider: dagger.Lazy<PendingMailActionDao>,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _actions = MutableStateFlow<List<PendingMailAction>>(emptyList())
    val actions: StateFlow<List<PendingMailAction>> = _actions.asStateFlow()

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val connectivity: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    init {
        scope.launch {
            runCatching { mutex.withLock { reload_locked() } }
            loaded.complete(Unit)
            if (_actions.value.isNotEmpty()) schedule_drain()
        }
        _online.value = is_network_available()
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _online.value = true
                    if (_actions.value.isNotEmpty()) schedule_drain(restart = true)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }

                override fun onLost(network: Network) {
                    _online.value = is_network_available()
                }
            })
        }
    }

    suspend fun await_loaded() {
        withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await() }
    }

    fun is_network_available(): Boolean {
        val manager = connectivity ?: return true
        return runCatching {
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    fun for_account(account_id: String?): List<PendingMailAction> {
        if (account_id.isNullOrBlank()) return emptyList()
        return _actions.value.filter { it.account_id == account_id }
    }

    fun has_pending(account_id: String?): Boolean {
        if (account_id.isNullOrBlank()) return false
        return _actions.value.any { it.account_id == account_id }
    }

    suspend fun enqueue(account_id: String, kind: PendingActionKind, payload: PendingActionPayload) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                dao_provider.get().insert(
                    PendingMailActionEntity(
                        account_id = account_id,
                        kind = kind.name,
                        payload = encode_pending_payload(payload),
                        created_at_ms = System.currentTimeMillis(),
                    ),
                )
                reload_locked()
            }
        }
        schedule_drain()
    }

    suspend fun complete(id: Long) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                dao_provider.get().delete_by_id(id)
                reload_locked()
            }
        }
    }

    suspend fun record_attempt(id: Long) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                dao_provider.get().bump_attempts(id)
                reload_locked()
            }
        }
    }

    suspend fun clear_account(account_id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching { dao_provider.get().clear_for_account(account_id) }
                reload_locked()
            }
        }
    }

    suspend fun clear_all() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching { dao_provider.get().clear_all() }
                reload_locked()
            }
        }
    }

    fun schedule_drain(restart: Boolean = false) {
        runCatching { PendingMailActionWorker.enqueue(context, restart) }
    }

    private suspend fun reload_locked() {
        val rows = runCatching { dao_provider.get().get_all() }.getOrNull() ?: return
        _actions.value = rows.mapNotNull { it.to_pending_action() }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 3_000L
    }
}
