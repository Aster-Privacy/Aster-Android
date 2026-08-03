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

package org.astermail.android.ui.settings.detail

import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.CircleCheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.settings.DomainPurchaseViewModel
import org.astermail.android.settings.domain_order_terminal_statuses

private val progress_step_statuses = listOf("paid", "registering", "configuring_dns", "activating")

@Composable
private fun progress_step_labels(): List<String> = listOf(
    stringResource(R.string.domain_purchase_step_payment),
    stringResource(R.string.domain_purchase_step_registering),
    stringResource(R.string.domain_purchase_step_dns),
    stringResource(R.string.domain_purchase_step_activating),
    stringResource(R.string.domain_purchase_step_done),
)

private const val slow_note_delay_ms = 90_000L

@Composable
fun DomainPurchaseProgressScreen(
    order_id: String,
    on_back: () -> Unit,
    on_create_address: () -> Unit,
    on_done: () -> Unit,
) {
    val vm: DomainPurchaseViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var retry_key by remember { mutableIntStateOf(0) }
    var show_slow_note by remember { mutableStateOf(false) }

    LaunchedEffect(order_id, retry_key) {
        vm.poll_order(order_id)
    }

    val order = state.order
    val status = order?.status

    LaunchedEffect(order_id, status in domain_order_terminal_statuses) {
        if (status !in domain_order_terminal_statuses) {
            show_slow_note = false
            delay(slow_note_delay_ms)
            show_slow_note = true
        } else {
            show_slow_note = false
        }
    }

    detail_scaffold(
        title = order?.domain?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.domain_purchase_progress_title, it) }
            ?: stringResource(R.string.domain_purchase_title),
        on_back = on_back,
    ) {
        progress_body(
            order = order,
            order_load_failed = state.order_load_failed,
            show_slow_note = show_slow_note,
            on_retry = { retry_key += 1 },
            on_create_address = on_create_address,
            on_done = on_done,
        )
    }
}

@Composable
private fun progress_body(
    order: org.astermail.android.api.domains.DomainOrder?,
    order_load_failed: Boolean,
    show_slow_note: Boolean,
    on_retry: () -> Unit,
    on_create_address: () -> Unit,
    on_done: () -> Unit,
) {
    val colors = AsterMaterial.colors
    if (order == null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (order_load_failed) {
                error_banner(stringResource(R.string.domain_purchase_error))
                v_gap(AsterSpacing.md)
                AsterSecondaryButton(
                    label = stringResource(R.string.retry),
                    onClick = on_retry,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = colors.accent_blue,
                )
            }
        }
        return
    }
    when (order.status) {
        "complete" -> progress_success(order = order, on_create_address = on_create_address, on_done = on_done)
        "refund_pending", "refunded", "failed" -> progress_closed(
            message = stringResource(R.string.domain_purchase_refunded),
            on_done = on_done,
        )
        "expired" -> progress_closed(
            message = stringResource(R.string.domain_purchase_order_expired),
            on_done = on_done,
        )
        "lapsed" -> progress_closed(
            message = stringResource(R.string.domain_purchase_order_lapsed),
            on_done = on_done,
        )
        else -> progress_steps(order = order, show_slow_note = show_slow_note)
    }
}

@Composable
private fun progress_steps(order: org.astermail.android.api.domains.DomainOrder, show_slow_note: Boolean) {
    val colors = AsterMaterial.colors
    val labels = progress_step_labels()
    val raw_index = progress_step_statuses.indexOf(order.status)
    val step_index = maxOf(raw_index, if (order.status == "awaiting_funds") 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 340.dp)) {
            labels.forEachIndexed { i, label ->
                val done = i < step_index
                val active = i == step_index
                Row(
                    modifier = Modifier.padding(vertical = AsterSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        when {
                            done -> Icon(
                                imageVector = TablerIcons.Check,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(18.dp),
                            )
                            active -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent_blue,
                            )
                            else -> Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors.border_secondary, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.width(AsterSpacing.md))
                    Text(
                        text = label,
                        color = when {
                            done -> colors.text_secondary
                            active -> colors.text_primary
                            else -> colors.text_muted
                        },
                        fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        v_gap(AsterSpacing.lg)
        Text(
            text = stringResource(R.string.domain_purchase_please_wait),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        if (show_slow_note) {
            v_gap(AsterSpacing.md)
            Text(
                text = stringResource(R.string.domain_purchase_slow_note),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun progress_success(
    order: org.astermail.android.api.domains.DomainOrder,
    on_create_address: () -> Unit,
    on_done: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = TablerIcons.CircleCheck,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(48.dp),
        )
        v_gap(AsterSpacing.md)
        Text(
            text = order.domain,
            color = colors.text_primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        v_gap(AsterSpacing.sm)
        Text(
            text = stringResource(R.string.domain_purchase_done_note),
            color = colors.text_secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        v_gap(AsterSpacing.sm)
        Text(
            text = stringResource(R.string.domain_purchase_warmup_note),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        v_gap(AsterSpacing.lg)
        AsterButton(
            label = stringResource(R.string.domain_purchase_create_first_address),
            onClick = on_create_address,
            modifier = Modifier.fillMaxWidth(),
        )
        v_gap(AsterSpacing.sm)
        AsterGhostButton(
            label = stringResource(R.string.done),
            onClick = on_done,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun progress_closed(message: String, on_done: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        error_banner(message)
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(
            label = stringResource(R.string.done),
            onClick = on_done,
        )
    }
}
