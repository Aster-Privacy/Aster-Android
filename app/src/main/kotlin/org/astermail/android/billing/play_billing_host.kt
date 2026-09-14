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
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private tailrec fun Context.play_host_activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.play_host_activity()
    else -> null
}

@Composable
fun PlayBillingHost() {
    if (!remember_play_install()) return
    val context = LocalContext.current
    val billing_vm: BillingViewModel = billing_view_model()
    val state by billing_vm.state.collectAsStateWithLifecycle()
    val lifecycle_owner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { billing_vm.ensure_play_config() }

    LaunchedEffect(state.play_purchase_request) {
        if (state.play_purchase_request == null) return@LaunchedEffect
        val activity = context.play_host_activity() ?: return@LaunchedEffect
        billing_vm.launch_play_purchase(activity)
    }

    LaunchedEffect(state.play_enabled) {
        if (!state.play_enabled) return@LaunchedEffect
        billing_vm.play_store.purchase_updates.collect { billing_vm.redeem_play_purchases() }
    }

    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) billing_vm.redeem_play_purchases()
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }
}
