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

package org.astermail.android.ui.settings.detail.bimi

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.domains.BIMI_MAX_LOGO_BYTES
import org.astermail.android.api.domains.BimiState
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.settings.BimiUiState
import org.astermail.android.settings.BimiViewModel
import org.astermail.android.ui.settings.detail.v_gap

internal fun read_bimi_logo_capped(context: Context, uri: Uri): ByteArray? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val limit = BIMI_MAX_LOGO_BYTES + 1
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = input.read(buffer, total, limit - total)
            if (read < 0) break
            total += read
        }
        buffer.copyOf(total)
    }
} catch (_: Throwable) {
    null
}

@Composable
internal fun bimi_logo_step(state: BimiUiState, domain_name: String, vm: BimiViewModel) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { read_bimi_logo_capped(context, uri) }
            vm.upload_logo(bytes)
        }
    }
    val has_preview = state.bimi_state != BimiState.off && state.view?.preview_png != null

    if (state.show_remove_record_note) {
        bimi_note(stringResource(R.string.domain_bimi_turn_off_remove_record), colors.warning)
        v_gap(AsterSpacing.md)
    }

    Text(
        text = stringResource(R.string.domain_bimi_logo_requirements),
        color = colors.text_primary,
        fontSize = 14.sp,
    )
    v_gap(AsterSpacing.xs)
    Text(
        text = stringResource(R.string.domain_bimi_logo_public_note),
        color = colors.text_tertiary,
        fontSize = 12.sp,
    )
    v_gap(AsterSpacing.md)
    AsterSecondaryButton(
        label = stringResource(if (state.uploading) R.string.domain_bimi_uploading else R.string.domain_bimi_choose_file),
        onClick = { picker.launch(arrayOf("image/svg+xml")) },
        enabled = !state.uploading,
        is_loading = state.uploading,
    )

    if (state.logo_errors.isNotEmpty()) {
        v_gap(AsterSpacing.md)
        val lines = state.logo_errors.map { bimi_logo_error_res(it) }.distinct().map { stringResource(it) }
        bimi_bullet_list(
            title = stringResource(R.string.domain_bimi_errors_title),
            lines = lines,
            tint = colors.danger,
        )
    } else if (has_preview) {
        v_gap(AsterSpacing.lg)
        bimi_preview(domain_name = domain_name, png = state.view?.preview_png)
        val adjustments = state.adjustments.mapNotNull { bimi_adjustment_res(it) }.distinct()
        if (adjustments.isNotEmpty()) {
            v_gap(AsterSpacing.md)
            bimi_bullet_list(
                title = stringResource(R.string.domain_bimi_adjustments_title),
                lines = adjustments.map { stringResource(it) },
                tint = colors.accent_blue,
            )
        }
    }

    v_gap(AsterSpacing.lg)
    if (state.replacing) {
        AsterButton(
            label = stringResource(R.string.domain_bimi_done),
            onClick = vm::go_to_manage,
            enabled = !state.uploading,
        )
    } else {
        AsterButton(
            label = stringResource(R.string.domain_bimi_continue),
            onClick = vm::go_to_publish,
            enabled = has_preview && !state.uploading && state.logo_errors.isEmpty(),
        )
    }
}
