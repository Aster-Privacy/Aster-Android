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

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.components.aster_menu_item
import org.astermail.android.notifications.UnifiedPushState
import org.astermail.android.notifications.external_push_distributors

internal data class PushDistributorOption(val package_name: String, val label: String)

internal data class PushDistributorUi(
    val selected: String,
    val options: List<PushDistributorOption>,
    val fallback_label: String?,
)

private fun app_label(context: Context, package_name: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(package_name, PackageManager.GET_META_DATA)).toString()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: package_name

private fun load_push_distributors(context: Context): PushDistributorUi {
    if (UnifiedPushState.distributor_missing(context)) UnifiedPushState.sync_registration(context)
    val own = context.packageName
    val external = external_push_distributors(own, UnifiedPushState.installed_distributors(context))
        .map { PushDistributorOption(it, app_label(context, it)) }
        .sortedBy { it.label.lowercase() }
    return PushDistributorUi(
        selected = UnifiedPushState.current_distributor(context),
        options = external,
        fallback_label = UnifiedPushState.fallback_notice(context),
    )
}

@Composable
internal fun push_distributor_row() {
    val context = LocalContext.current
    val colors = AsterMaterial.colors
    var reload by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun choose(package_name: String, label: String?) {
        pending = package_name
        scope.launch {
            withContext(Dispatchers.IO) { UnifiedPushState.choose_distributor(context, package_name, label) }
            reload++
        }
    }
    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload++
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }
    val ui by produceState<PushDistributorUi?>(initialValue = null, reload) {
        value = withContext(Dispatchers.IO) { load_push_distributors(context) }
        pending = null
    }
    val state = ui ?: return
    val own = context.packageName
    val built_in_label = stringResource(R.string.push_distributor_built_in)
    val selected = pending ?: state.selected
    val selected_label = state.options.firstOrNull { it.package_name == selected }?.label ?: built_in_label
    Column(modifier = Modifier.fillMaxWidth()) {
        alias_select_row(
            title = stringResource(R.string.push_distributor_title),
            subtitle = stringResource(R.string.push_distributor_subtitle),
            value = selected_label,
            test_tag = "push_distributor_selector",
        ) { expanded, dismiss ->
            aster_menu(expanded = expanded, on_dismiss = dismiss) {
                aster_menu_item(
                    label = built_in_label,
                    selected = selected == own || state.options.none { it.package_name == selected },
                    test_tag = "push_distributor_option_built_in",
                    on_click = {
                        dismiss()
                        if (selected != own) {
                            choose(own, null)
                        }
                    },
                )
                state.options.forEach { option ->
                    aster_menu_item(
                        label = option.label,
                        selected = selected == option.package_name,
                        test_tag = "push_distributor_option_${option.package_name}",
                        on_click = {
                            dismiss()
                            if (selected != option.package_name) {
                                choose(option.package_name, option.label)
                            }
                        },
                    )
                }
            }
        }
        if (state.options.isEmpty()) {
            Text(
                text = stringResource(R.string.push_distributor_none_installed),
                color = colors.text_tertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = AsterSpacing.sm),
            )
        }
        val fallback = state.fallback_label
        if (fallback != null) {
            Text(
                text = stringResource(R.string.push_distributor_fallback_body, fallback),
                color = colors.warning,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = AsterSpacing.lg, end = AsterSpacing.lg, bottom = AsterSpacing.sm)
                    .testTag("push_distributor_fallback_notice"),
            )
        }
    }
}
