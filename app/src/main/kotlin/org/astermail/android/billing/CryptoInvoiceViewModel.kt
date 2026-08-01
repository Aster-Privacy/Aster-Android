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

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.BuildConfig
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.CryptoNativeInvoiceStatus

enum class CryptoInvoiceLoadError { none, not_found, unavailable }

data class CryptoInvoiceUiState(
    val invoice: CryptoNativeInvoiceStatus? = null,
    val is_loading: Boolean = true,
    val load_error: CryptoInvoiceLoadError = CryptoInvoiceLoadError.none,
    val is_cancelling: Boolean = false,
    val cancel_error: String? = null,
    val poll_token: Int = 0,
)

@HiltViewModel
class CryptoInvoiceViewModel @Inject constructor(
    application: Application,
    private val billing_api: BillingApi,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(CryptoInvoiceUiState())
    val state: StateFlow<CryptoInvoiceUiState> = _state.asStateFlow()

    private var invoice_id: String? = null
    private var consecutive_failures = 0

    suspend fun poll(id: String) {
        if (invoice_id != id) {
            invoice_id = id
            consecutive_failures = 0
            _state.update { CryptoInvoiceUiState(poll_token = it.poll_token) }
        }
        if (_state.value.load_error == CryptoInvoiceLoadError.not_found) return
        if (is_terminal(_state.value.invoice?.status)) return
        consecutive_failures = 0
        val session_start_ms = SystemClock.elapsedRealtime()
        while (true) {
            val invoice = fetch()
            if (invoice == null) {
                if (_state.value.load_error == CryptoInvoiceLoadError.not_found) return
                if (consecutive_failures >= MAX_CONSECUTIVE_FAILURES) return
                delay(failure_delay_ms())
                continue
            }
            if (is_terminal(invoice.status)) return
            delay(poll_delay_ms(invoice.status, SystemClock.elapsedRealtime() - session_start_ms))
        }
    }

    fun refresh() {
        if (invoice_id == null) return
        consecutive_failures = 0
        _state.update {
            it.copy(
                is_loading = it.invoice == null,
                load_error = CryptoInvoiceLoadError.none,
                poll_token = it.poll_token + 1,
            )
        }
    }

    private suspend fun fetch(): CryptoNativeInvoiceStatus? {
        val id = invoice_id ?: return null
        return try {
            val invoice = billing_api.get_crypto_native_invoice(id)
            consecutive_failures = 0
            _state.update {
                it.copy(invoice = invoice, is_loading = false, load_error = CryptoInvoiceLoadError.none)
            }
            invoice
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("CryptoInvoiceVM", "fetch failed", t)
            consecutive_failures += 1
            val is_definitive = t is ApiError.NotFoundError || t is ApiError.ForbiddenError
            _state.update {
                when {
                    is_definitive -> it.copy(
                        is_loading = false,
                        load_error = CryptoInvoiceLoadError.not_found,
                    )
                    it.invoice != null -> it
                    else -> it.copy(
                        is_loading = false,
                        load_error = CryptoInvoiceLoadError.unavailable,
                    )
                }
            }
            null
        }
    }

    private fun is_terminal(status: String?): Boolean = status != null && status in TERMINAL_STATUSES

    private fun poll_delay_ms(status: String, session_elapsed_ms: Long): Long = when {
        status == "manual_review" -> MANUAL_REVIEW_INTERVAL_MS
        session_elapsed_ms < FAST_WINDOW_MS -> FAST_INTERVAL_MS
        else -> SLOW_INTERVAL_MS
    }

    private fun failure_delay_ms(): Long {
        val steps = (consecutive_failures - 1).coerceIn(0, 8)
        return (FAILURE_BASE_DELAY_MS shl steps).coerceAtMost(MAX_FAILURE_DELAY_MS)
    }

    fun cancel() {
        val id = invoice_id ?: return
        if (_state.value.is_cancelling) return
        _state.update { it.copy(is_cancelling = true, cancel_error = null) }
        viewModelScope.launch {
            try {
                val result = billing_api.cancel_crypto_native_invoice(id)
                _state.update {
                    it.copy(
                        is_cancelling = false,
                        invoice = it.invoice?.copy(status = result.status),
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        is_cancelling = false,
                        cancel_error = org.astermail.android.api.user_facing_error(
                            t,
                            ctx.getString(R.string.crypto_native_cancel_failed),
                        ),
                    )
                }
            }
        }
    }

    fun clear_cancel_error() {
        _state.update { it.copy(cancel_error = null) }
    }

    companion object {
        private const val FAST_INTERVAL_MS = 6_000L
        private const val FAST_WINDOW_MS = 60_000L
        private const val SLOW_INTERVAL_MS = 20_000L
        private const val MANUAL_REVIEW_INTERVAL_MS = 60_000L
        private const val FAILURE_BASE_DELAY_MS = 6_000L
        private const val MAX_FAILURE_DELAY_MS = 60_000L
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private val TERMINAL_STATUSES = setOf("paid", "expired", "cancelled")
    }
}
