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

package org.astermail.android.billing

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object PlayBilling : PlayStore {
    override val is_supported: Boolean = false

    override val purchase_updates: Flow<Unit> = emptyFlow()

    override suspend fun query_offers(context: Context, product_ids: List<String>): List<PlayOffer> = emptyList()

    override suspend fun purchase(
        activity: Activity,
        offer: PlayOffer,
        obfuscated_account_id: String,
        old_purchase_token: String?,
    ): PlayPurchaseOutcome = PlayPurchaseOutcome.Unavailable

    override suspend fun owned_purchases(context: Context): List<PlayOwnedPurchase>? = null
}
