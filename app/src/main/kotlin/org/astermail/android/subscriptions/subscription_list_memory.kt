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

package org.astermail.android.subscriptions

import javax.inject.Inject
import javax.inject.Singleton
import org.astermail.android.api.subscriptions.MailingListStats
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.mail.MailRepository

data class SubscriptionListSnapshot(
    val items: List<MailingListSubscription>,
    val stats: MailingListStats?,
)

@Singleton
class SubscriptionListMemory @Inject constructor(
    private val repository: MailRepository,
) {
    private val snapshots = HashMap<String, SubscriptionListSnapshot>()

    private fun account_key(): String? =
        repository.get_user_email()?.let { normalize_sender_email(it) }?.takeIf { it.isNotEmpty() }

    @Synchronized
    fun get(): SubscriptionListSnapshot? = account_key()?.let { snapshots[it] }

    @Synchronized
    fun put(items: List<MailingListSubscription>, stats: MailingListStats?) {
        val key = account_key() ?: return
        snapshots.keys.retainAll(setOf(key))
        snapshots[key] = SubscriptionListSnapshot(items, stats)
    }
}
