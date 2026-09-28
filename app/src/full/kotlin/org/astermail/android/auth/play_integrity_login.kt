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

package org.astermail.android.auth

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object PlayIntegrityLogin {
    private const val TAG = "PlayIntegrityLogin"
    private const val CLOUD_PROJECT_NUMBER = 116575931870L
    private const val PREPARE_TIMEOUT_MS = 8_000L
    private const val REQUEST_TIMEOUT_MS = 6_000L

    @Volatile
    private var provider: StandardIntegrityTokenProvider? = null
    private val prepare_lock = Mutex()

    suspend fun warm_up(context: Context) {
        runCatching { prepared_provider(context) }
    }

    suspend fun fetch(context: Context, user_hash: String): LoginIntegrity? {
        val nonce = login_integrity_nonce()
        val request_hash = login_integrity_request_hash(user_hash, nonce)
        return try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                val token_provider = prepared_provider(context) ?: return@withTimeoutOrNull null
                val response = token_provider
                    .request(StandardIntegrityTokenRequest.builder().setRequestHash(request_hash).build())
                    .await()
                response.token().takeIf { it.isNotBlank() }?.let { LoginIntegrity(token = it, nonce = nonce) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "integrity token unavailable", t)
            null
        }
    }

    private suspend fun prepared_provider(context: Context): StandardIntegrityTokenProvider? {
        provider?.let { return it }
        return prepare_lock.withLock {
            provider?.let { return@withLock it }
            val prepared = try {
                withTimeoutOrNull(PREPARE_TIMEOUT_MS) {
                    IntegrityManagerFactory.createStandard(context.applicationContext)
                        .prepareIntegrityToken(
                            PrepareIntegrityTokenRequest.builder()
                                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                                .build(),
                        )
                        .await()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "integrity provider unavailable", t)
                null
            }
            provider = prepared
            prepared
        }
    }
}
