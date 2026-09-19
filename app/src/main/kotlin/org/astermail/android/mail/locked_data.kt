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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.SessionKeyStore

interface LockedSentMailCounts {
    fun read(account_id: String): Int
    fun write(account_id: String, count: Int)
}

object NoLockedSentMailCounts : LockedSentMailCounts {
    override fun read(account_id: String): Int = 0
    override fun write(account_id: String, count: Int) = Unit
}

@Singleton
class LockedSentMailStore @Inject constructor(
    @ApplicationContext context: Context,
) : LockedSentMailCounts {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    override fun read(account_id: String): Int {
        if (account_id.isEmpty()) return 0
        return runCatching { prefs.getInt(KEY_PREFIX + account_id, 0) }.getOrDefault(0).coerceAtLeast(0)
    }

    override fun write(account_id: String, count: Int) {
        if (account_id.isEmpty()) return
        runCatching {
            val editor = prefs.edit()
            if (count > 0) editor.putInt(KEY_PREFIX + account_id, count) else editor.remove(KEY_PREFIX + account_id)
            editor.apply()
        }
        _version.update { it + 1 }
    }

    fun notify_changed() {
        _version.update { it + 1 }
    }

    companion object {
        private const val PREFS_NAME = "aster_locked_sent_mail"
        private const val KEY_PREFIX = "aster_locked_sent_mail_"
    }
}

data class LockedDataStatus(
    val inactive_key_sets: Int,
    val locked_sent_mail: Int,
    val signature: String,
)

data class LockedDataRecovery(
    val restored_key_sets: Int = 0,
    val recovered_sent_mail: Int = 0,
    val failed: Boolean = false,
)

fun locked_data_signature(inactive_key_set_ids: List<String>, locked_sent_mail: Int): String {
    val ids = inactive_key_set_ids.filter { it.isNotEmpty() }.sorted()
    return ids.joinToString(",") + "|" + if (locked_sent_mail > 0) "sent" else ""
}

fun locked_data_status(inactive_key_set_ids: List<String>, locked_sent_mail: Int): LockedDataStatus {
    val ids = inactive_key_set_ids.filter { it.isNotEmpty() }
    val locked = locked_sent_mail.coerceAtLeast(0)
    return LockedDataStatus(
        inactive_key_sets = ids.size,
        locked_sent_mail = locked,
        signature = locked_data_signature(ids, locked),
    )
}

fun has_locked_data(status: LockedDataStatus?): Boolean =
    status != null && (status.inactive_key_sets > 0 || status.locked_sent_mail > 0)

fun should_show_locked_data_banner(
    status: LockedDataStatus?,
    vault_unlocked: Boolean,
    preferences_loaded: Boolean,
    dismissed_signature: String,
): Boolean {
    if (!vault_unlocked || !preferences_loaded || status == null) return false
    if (!has_locked_data(status)) return false
    return status.signature != dismissed_signature
}

class LockedDataService internal constructor(
    private val list_inactive_key_set_ids: suspend () -> List<String>?,
    private val locked_counts: LockedSentMailCounts,
    private val current_passphrase: () -> ByteArray?,
    private val restore_inactive_key_sets: suspend (String) -> Int,
    private val recover_with_conversion: suspend (String, String) -> AccountDataConversionSummary?,
    private val reseal_sent_mail: suspend (ByteArray, ByteArray) -> SentMailResealSummary,
    private val on_changed: () -> Unit,
) {
    @Inject
    constructor(
        recovery_api: RecoveryApi,
        locked_sent_mail_store: LockedSentMailStore,
        session_key_store: SessionKeyStore,
        auth_repository: AuthRepository,
        conversion: AccountDataConversion,
        resealer: SentMailResealer,
    ) : this(
        list_inactive_key_set_ids = {
            kotlin.runCatching { recovery_api.list_inactive_key_sets().inactive_key_sets.map { it.id } }.getOrNull()
        },
        locked_counts = locked_sent_mail_store,
        current_passphrase = { session_key_store.get_passphrase() },
        restore_inactive_key_sets = { auth_repository.restore_inactive_key_sets(it) },
        recover_with_conversion = { account_id, password ->
            conversion.recover_sent_mail_with_password(account_id, password)
        },
        reseal_sent_mail = { old, current -> resealer.run(old, current) },
        on_changed = { locked_sent_mail_store.notify_changed() },
    )

    suspend fun status(account_id: String): LockedDataStatus? {
        if (account_id.isEmpty()) return null
        val ids = try {
            list_inactive_key_set_ids()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } ?: return null
        return locked_data_status(ids, locked_counts.read(account_id))
    }

    suspend fun recover_locked_data(account_id: String, password: String): LockedDataRecovery {
        if (account_id.isEmpty() || password.isEmpty()) return LockedDataRecovery()
        var restored = 0
        var recovered = 0
        var failed = false

        try {
            restored = restore_inactive_key_sets(password)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed = true
        }

        try {
            val (count, sent_failed) = recover_sent_mail(account_id, password)
            recovered = count
            failed = failed || sent_failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed = true
        }

        runCatching { on_changed() }
        return LockedDataRecovery(restored, recovered, failed)
    }

    private suspend fun recover_sent_mail(account_id: String, password: String): Pair<Int, Boolean> {
        val current = current_passphrase()
        if (current == null || current.isEmpty()) return 0 to true
        val old = password.toByteArray(Charsets.UTF_8)
        try {
            if (old.contentEquals(current)) return 0 to false
            val converted = recover_with_conversion(account_id, password)
            if (converted != null) return converted.converted to (converted.failed > 0)
            val summary = reseal_sent_mail(old, current)
            locked_counts.write(account_id, summary.unreadable)
            return summary.rewritten to (summary.failed > 0)
        } finally {
            old.fill(0)
            current.fill(0)
        }
    }
}
