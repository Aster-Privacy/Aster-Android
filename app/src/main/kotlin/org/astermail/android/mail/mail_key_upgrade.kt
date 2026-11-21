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
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.api.ApiClient
import org.astermail.android.crypto.storage_rotation.KtorResealApi
import org.astermail.android.crypto.storage_rotation.VaultEdit
import org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome
import org.astermail.android.crypto.storage_rotation.VaultUpdater
import org.astermail.android.crypto.storage_rotation.session_vault_updater
import org.astermail.android.folders.folder_lock_store
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject

private const val MAIL_LEGACY_PREFS_NAME = "mail_legacy_migration"
const val MAIL_KEY_UPGRADE_START_DELAY_MS = 2_000L
const val MAIL_KEY_UPGRADE_RETRY_DELAY_MS = 60_000L
const val MAIL_KEY_UPGRADE_MAX_SILENT_RETRIES = 3

enum class MailKeyUpgradePhase { IDLE, RUNNING, FAILED, DONE }

data class MailKeyUpgradeState(
    val phase: MailKeyUpgradePhase = MailKeyUpgradePhase.IDLE,
    val processed: Int = 0,
    val forced: Boolean = false,
) {
    val is_blocking: Boolean
        get() = forced && (phase == MailKeyUpgradePhase.RUNNING || phase == MailKeyUpgradePhase.FAILED)
}

sealed class MailKeyUpgradeStep {
    object Done : MailKeyUpgradeStep()
    object Idle : MailKeyUpgradeStep()
    class Retry(val attempts: Int) : MailKeyUpgradeStep()
    object Failed : MailKeyUpgradeStep()
}

fun next_mail_key_upgrade_step(result: MailLegacyMigrationResult, forced: Boolean, attempts: Int): MailKeyUpgradeStep =
    when {
        result == MailLegacyMigrationResult.DONE -> MailKeyUpgradeStep.Done
        result == MailLegacyMigrationResult.DEFERRED || result == MailLegacyMigrationResult.UNAVAILABLE ->
            MailKeyUpgradeStep.Idle
        !forced && (result == MailLegacyMigrationResult.PAUSED || attempts < MAIL_KEY_UPGRADE_MAX_SILENT_RETRIES) ->
            MailKeyUpgradeStep.Retry(if (result == MailLegacyMigrationResult.PAUSED) attempts else attempts + 1)
        else -> MailKeyUpgradeStep.Failed
    }

suspend fun write_mail_legacy_retired_marker(
    updater: VaultUpdater,
    store: SessionKeyStore,
    now: () -> String = { Instant.now().toString() },
): Boolean {
    val owner = store.get_user_id()?.takeIf { it.isNotBlank() } ?: return false
    val outcome = updater.update { draft ->
        if (vault_marks_mail_legacy_retired(draft.vault)) return@update VaultEdit.UNCHANGED
        draft.vault.put(MAIL_LEGACY_RETIRED_FIELD, JSONObject().put("v", 1).put("at", now()))
        VaultEdit.CHANGED
    }
    val vault = when (outcome) {
        is VaultUpdateOutcome.Written -> outcome.vault
        is VaultUpdateOutcome.Unchanged -> outcome.vault
        else -> return false
    }
    if (!vault_marks_mail_legacy_retired(vault)) return false
    if (store.get_user_id() != owner) return false
    store.pin_mail_legacy_retired(owner)
    return true
}

class PrefsMailLegacyFlagStore(private val prefs: SharedPreferences) : MailLegacyFlagStore {
    override fun get(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()

    override fun put(key: String, value: String?) {
        runCatching {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
    }
}

object MailLegacyFolderHook {
    @Volatile
    private var listener: ((String) -> Unit)? = null

    fun attach(block: (String) -> Unit) {
        listener = block
    }

    fun folder_opened(label_token: String) {
        listener?.let { runCatching { it(label_token) } }
    }
}

@Singleton
class MailKeyUpgrade @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api_client: ApiClient,
    private val session_key_store: SessionKeyStore,
    private val mail_repository: dagger.Lazy<MailRepository>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val run_lock = Mutex()
    private val _state = MutableStateFlow(MailKeyUpgradeState())
    val state: StateFlow<MailKeyUpgradeState> = _state.asStateFlow()

    @Volatile private var generation = 0
    @Volatile private var active_user: String? = null
    private var timer: Job? = null
    private val folder_runs = java.util.Collections.synchronizedSet(HashSet<String>())
    @Volatile private var reseal_user: String? = null
    private val reseal_lock = Mutex()
    private val server_seal_resealer by lazy {
        ServerSealResealer(KtorResealApi(api_client)) { mail_repository.get().mail_legacy_crypto() }
    }

    private val flags: MailLegacyFlagStore by lazy {
        runCatching { SecurePrefs.open(context, MAIL_LEGACY_PREFS_NAME) }.getOrNull()
            ?.let { PrefsMailLegacyFlagStore(it) }
            ?: MemoryMailLegacyFlagStore()
    }

    init {
        MailLegacyFolderHook.attach { token -> offer_folder(token) }
        ServerSealHook.attach { item_id, encrypted -> offer_server_seal(item_id, encrypted) }
    }

    private fun migration(user_id: String): MailLegacyMigration {
        val api = KtorResealApi(api_client)
        return MailLegacyMigration(
            MailLegacyMigrationDeps(
                user_id = user_id,
                api = api,
                crypto = mail_repository.get().mail_legacy_crypto(),
                flags = flags,
                is_retired = { session_key_store.mail_legacy_retired() },
                protected_folder_tokens = { folder_lock_store.known_protected_tokens() },
                write_marker = { write_mail_legacy_retired_marker(session_vault_updater(api, session_key_store), session_key_store) },
                is_draft_busy = { LocalDraftActivity.is_in_use(it) },
                on_progress = { processed, _ -> publish_progress(user_id, processed) },
            ),
        )
    }

    @Synchronized
    private fun publish_progress(user_id: String, processed: Int) {
        if (active_user != user_id) return
        val current = _state.value
        if (current.phase == MailKeyUpgradePhase.RUNNING) _state.value = current.copy(processed = processed)
    }

    @Synchronized
    fun start() {
        val user_id = session_key_store.get_user_id()?.takeIf { it.isNotBlank() } ?: return
        reseal_user = user_id
        if (active_user == user_id) return
        stop()
        if (session_key_store.mail_legacy_retired()) return
        active_user = user_id
        schedule(MAIL_KEY_UPGRADE_START_DELAY_MS, false, 0)
    }

    private fun offer_server_seal(item_id: String, encrypted: String) {
        val user_id = reseal_user ?: return
        scope.launch {
            reseal_lock.withLock {
                if (reseal_user != user_id || session_key_store.get_user_id() != user_id) return@withLock
                server_seal_resealer.reseal(item_id, encrypted)
            }
        }
    }

    @Synchronized
    fun stop() {
        generation += 1
        reseal_user = null
        timer?.cancel()
        timer = null
        active_user = null
        folder_runs.clear()
        _state.value = MailKeyUpgradeState()
    }

    @Synchronized
    fun retry() {
        if (active_user == null || _state.value.phase == MailKeyUpgradePhase.RUNNING) return
        schedule(0, true, MAIL_KEY_UPGRADE_MAX_SILENT_RETRIES)
    }

    @Synchronized
    private fun schedule(delay_ms: Long, forced: Boolean, attempts: Int) {
        val run_generation = generation
        timer?.cancel()
        timer = scope.launch {
            delay(delay_ms)
            step(run_generation, forced, attempts)
        }
    }

    private suspend fun step(run_generation: Int, forced: Boolean, attempts: Int) {
        val user_id = active_user ?: return
        if (run_generation != generation) return
        _state.value = MailKeyUpgradeState(MailKeyUpgradePhase.RUNNING, 0, forced)
        val result = runCatching { run_lock.withLock { migration(user_id).run() } }
            .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else MailLegacyMigrationResult.PAUSED }
        synchronized(this) {
            if (run_generation != generation) return
            val processed = _state.value.processed
            when (val next = next_mail_key_upgrade_step(result, forced, attempts)) {
                MailKeyUpgradeStep.Done -> _state.value = MailKeyUpgradeState(MailKeyUpgradePhase.DONE, processed, false)
                MailKeyUpgradeStep.Idle -> _state.value = MailKeyUpgradeState()
                is MailKeyUpgradeStep.Retry -> {
                    _state.value = MailKeyUpgradeState()
                    schedule(MAIL_KEY_UPGRADE_RETRY_DELAY_MS, false, next.attempts)
                }
                MailKeyUpgradeStep.Failed -> _state.value = MailKeyUpgradeState(MailKeyUpgradePhase.FAILED, processed, true)
            }
        }
    }

    fun offer_folder(label_token: String) {
        val user_id = active_user ?: return
        if (session_key_store.mail_legacy_retired()) return
        val folder_id = folder_lock_store.unlocked_protected_folder_id(label_token) ?: return
        val unlock_key = "$label_token:${folder_lock_store.unlock_session_marker(folder_id) ?: return}"
        if (!folder_runs.add(unlock_key)) return
        val run_generation = generation
        scope.launch {
            val result = run_lock.withLock {
                migration(user_id).run_folder(label_token) {
                    folder_lock_store.unlocked_protected_folder_id(label_token) != null
                }
            }
            if (result == MailLegacyMigrationResult.PAUSED) folder_runs.remove(unlock_key)
            if (result == MailLegacyMigrationResult.DONE) {
                synchronized(this@MailKeyUpgrade) {
                    if (run_generation == generation && _state.value.phase != MailKeyUpgradePhase.RUNNING) {
                        _state.value = MailKeyUpgradeState(MailKeyUpgradePhase.DONE, _state.value.processed, false)
                    }
                }
            }
        }
    }
}
