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

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.astermail.android.storage.SessionKeyStore

@Singleton
class SentMailResealFinisher @Inject constructor(
    private val session_key_store: SessionKeyStore,
    private val resealer: SentMailResealer,
) {
    private val running = AtomicBoolean(false)

    fun mark_pending(old_passphrase: ByteArray) {
        if (old_passphrase.isEmpty()) return
        session_key_store.put_pending_reseal_passphrase(old_passphrase)
    }

    fun mark_done() {
        session_key_store.clear_pending_reseal_passphrase()
    }

    suspend fun finish_pending(): SentMailResealSummary? {
        val old = session_key_store.get_pending_reseal_passphrase() ?: return null
        val current = session_key_store.get_passphrase()
        if (current == null || current.isEmpty() || old.isEmpty()) {
            old.fill(0)
            current?.fill(0)
            return null
        }
        if (old.contentEquals(current)) {
            mark_done()
            old.fill(0)
            current.fill(0)
            return null
        }
        if (!running.compareAndSet(false, true)) {
            old.fill(0)
            current.fill(0)
            return null
        }
        return try {
            val summary = resealer.run(old, current)
            if (summary.failed == 0) mark_done()
            summary
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } finally {
            running.set(false)
            old.fill(0)
            current.fill(0)
        }
    }
}
