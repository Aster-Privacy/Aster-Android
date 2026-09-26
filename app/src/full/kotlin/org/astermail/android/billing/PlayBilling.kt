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
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object PlayBilling : PlayStore {
    private const val TAG = "PlayBilling"
    private const val CONNECT_TIMEOUT_MS = 15_000L

    override val is_supported: Boolean = true

    private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val purchase_updates: Flow<Unit> = updates.asSharedFlow()

    private val lock = Any()
    private var client: BillingClient? = null
    private var connection: CompletableDeferred<Boolean>? = null
    private class PendingFlow(val product_id: String, val deferred: CompletableDeferred<PlayPurchaseOutcome>)

    private var pending_flow: PendingFlow? = null
    private val product_details = ConcurrentHashMap<String, ProductDetails>()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val outcome = outcome_for(result, purchases, after_launch = true)
        val owner = synchronized(lock) {
            val waiting = pending_flow
            val owns = waiting != null && (purchases.isNullOrEmpty() || purchases.any { waiting.product_id in it.products })
            if (owns) pending_flow = null
            if (owns) waiting else null
        }
        if (owner != null) {
            owner.deferred.complete(outcome)
        } else if (!purchases.isNullOrEmpty()) {
            updates.tryEmit(Unit)
        }
    }

    private fun outcome_for(
        result: BillingResult,
        purchases: List<Purchase>?,
        after_launch: Boolean = false,
    ): PlayPurchaseOutcome =
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val owned = purchases.orEmpty().map { it.to_owned() }
                if (owned.isEmpty()) {
                    PlayPurchaseOutcome.Cancelled
                } else if (owned.all { it.is_pending }) {
                    PlayPurchaseOutcome.Pending
                } else {
                    PlayPurchaseOutcome.Purchased(owned)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PlayPurchaseOutcome.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PlayPurchaseOutcome.AlreadyOwned
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                if (after_launch) PlayPurchaseOutcome.PaymentDeclined else PlayPurchaseOutcome.Unavailable
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            -> PlayPurchaseOutcome.Unavailable
            else -> PlayPurchaseOutcome.Failed(result.responseCode)
        }

    private fun Purchase.to_owned(): PlayOwnedPurchase = PlayOwnedPurchase(
        purchase_token = purchaseToken,
        product_ids = products,
        is_purchased = purchaseState == Purchase.PurchaseState.PURCHASED,
        is_pending = purchaseState == Purchase.PurchaseState.PENDING,
        is_acknowledged = isAcknowledged,
    )

    private fun client_for(context: Context): BillingClient = synchronized(lock) {
        client ?: BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
            .also { client = it }
    }

    private suspend fun ready(context: Context): BillingClient? {
        val billing = client_for(context)
        if (billing.isReady) return billing
        val attempt = synchronized(lock) {
            connection?.takeIf { !it.isCompleted } ?: CompletableDeferred<Boolean>().also { deferred ->
                connection = deferred
                try {
                    billing.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            deferred.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            deferred.complete(false)
                        }
                    })
                } catch (t: Throwable) {
                    if (org.astermail.android.BuildConfig.DEBUG) Log.w(TAG, "startConnection failed", t)
                    deferred.complete(false)
                }
            }
        }
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { attempt.await() } ?: false
        return if (connected || billing.isReady) billing else null
    }

    override suspend fun query_offers(context: Context, product_ids: List<String>): List<PlayOffer> {
        if (product_ids.isEmpty()) return emptyList()
        val billing = ready(context) ?: return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                product_ids.distinct().map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val details = suspendCancellableCoroutine { cont ->
            billing.queryProductDetailsAsync(params) { result, query ->
                val list = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    query.productDetailsList
                } else {
                    if (org.astermail.android.BuildConfig.DEBUG) Log.w(TAG, "queryProductDetails failed: ${result.responseCode}")
                    emptyList()
                }
                if (cont.isActive) cont.resume(list)
            }
        }
        return details.flatMap { product ->
            product_details[product.productId] = product
            product.subscriptionOfferDetails.orEmpty()
                .mapNotNull { offer ->
                    val phases = offer.pricingPhases.pricingPhaseList
                    val phase = phases.lastOrNull() ?: return@mapNotNull null
                    val interval = play_billing_interval(phase.billingPeriod) ?: return@mapNotNull null
                    val intro = if (offer.offerId != null && phases.size > 1) phases.first() else null
                    PlayOffer(
                        product_id = product.productId,
                        base_plan_id = offer.basePlanId,
                        offer_token = offer.offerToken,
                        formatted_price = phase.formattedPrice,
                        price_micros = phase.priceAmountMicros,
                        currency_code = phase.priceCurrencyCode,
                        billing_interval = interval,
                        offer_id = offer.offerId,
                        intro_formatted_price = intro?.formattedPrice,
                        intro_price_micros = intro?.priceAmountMicros,
                    )
                }
        }
    }

    override suspend fun purchase(
        activity: Activity,
        offer: PlayOffer,
        obfuscated_account_id: String,
        old_purchase_token: String?,
        replacement_mode: PlayReplacementMode,
    ): PlayPurchaseOutcome {
        val billing = ready(activity) ?: return PlayPurchaseOutcome.Unavailable
        val details = product_details[offer.product_id] ?: return PlayPurchaseOutcome.Unavailable
        val builder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offer_token)
                        .build(),
                ),
            )
            .setObfuscatedAccountId(obfuscated_account_id)
        if (!old_purchase_token.isNullOrBlank()) {
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(old_purchase_token)
                    .setSubscriptionReplacementMode(
                        when (replacement_mode) {
                            PlayReplacementMode.CHARGE_PRORATED_PRICE ->
                                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_PRORATED_PRICE
                            PlayReplacementMode.WITH_TIME_PRORATION ->
                                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                        },
                    )
                    .build(),
            )
        }
        val waiting = PendingFlow(offer.product_id, CompletableDeferred())
        val previous = synchronized(lock) {
            val existing = pending_flow
            pending_flow = waiting
            existing
        }
        previous?.deferred?.complete(PlayPurchaseOutcome.Cancelled)
        val launch = try {
            billing.launchBillingFlow(activity, builder.build())
        } catch (t: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) Log.w(TAG, "launchBillingFlow failed", t)
            release(waiting)
            return PlayPurchaseOutcome.Unavailable
        }
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            release(waiting)
            return outcome_for(launch, null)
        }
        return try {
            waiting.deferred.await()
        } finally {
            release(waiting)
        }
    }

    private fun release(flow: PendingFlow) {
        synchronized(lock) {
            if (pending_flow === flow) pending_flow = null
        }
    }

    override suspend fun owned_purchases(context: Context): List<PlayOwnedPurchase>? {
        val billing = ready(context) ?: return null
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        return suspendCancellableCoroutine { cont ->
            billing.queryPurchasesAsync(params) { result, purchases ->
                val owned = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.map { it.to_owned() }
                } else {
                    null
                }
                if (cont.isActive) cont.resume(owned)
            }
        }
    }
}
