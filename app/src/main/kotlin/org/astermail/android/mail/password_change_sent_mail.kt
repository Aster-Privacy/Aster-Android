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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class PasswordChangeSentMail @Inject constructor(
    private val conversion: AccountDataConversion,
    private val resealer: SentMailResealer,
    private val finisher: SentMailResealFinisher,
) {
    suspend fun convert_before_change(identity_key: String, current_password: ByteArray): PasswordChangeConversion =
        withContext(NonCancellable) {
            try {
                conversion.convert_before_password_change(identity_key, current_password)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                PasswordChangeConversion.INCOMPLETE
            }
        }

    suspend fun reseal_after_change(
        before: PasswordChangeConversion,
        old_passphrase: ByteArray,
        new_passphrase: ByteArray,
        pgp_rewrapped: Boolean,
    ): SentMailResealSummary {
        val needed = try {
            conversion.sent_mail_needs_password_reseal(before)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            true
        }
        if (!needed) {
            finisher.mark_done()
            return SentMailResealSummary().let { if (pgp_rewrapped) it else it.copy(failed = it.failed + 1) }
        }

        finisher.mark_pending(old_passphrase)

        val reseal = runCatching {
            resealer.run(old_passphrase, new_passphrase)
        }.getOrElse { SentMailResealSummary(failed = 1) }
            .let { if (pgp_rewrapped) it else it.copy(failed = it.failed + 1) }

        if (reseal.failed == 0) finisher.mark_done()
        return reseal
    }
}
