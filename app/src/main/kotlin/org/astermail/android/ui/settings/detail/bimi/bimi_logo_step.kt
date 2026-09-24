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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.domains.BIMI_MAX_LOGO_BYTES
import org.astermail.android.api.domains.BimiState
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
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

internal fun bimi_has_valid_logo(state: BimiUiState): Boolean =
    state.view?.preview_png != null && state.bimi_state != BimiState.off && state.logo_errors.isEmpty()

@Composable
internal fun bimi_logo_step(state: BimiUiState, domain_name: String, vm: BimiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { read_bimi_logo_capped(context, uri) }
            vm.upload_logo(bytes)
        }
    }
    val open_picker = { if (!state.busy) picker.launch(arrayOf("image/svg+xml")) }
    val has_logo = bimi_has_valid_logo(state)
    val is_setup = !state.replacing

    if (is_setup) {
        bimi_stepper(current = 1)
        v_gap(AsterSpacing.lg)
    }

    if (state.show_remove_record_note) {
        bimi_alert(tone = BimiAlertTone.warning, message = stringResource(R.string.domain_bimi_turn_off_remove_record))
        v_gap(AsterSpacing.md)
    }

    if (has_logo) {
        bimi_file_row(state = state, on_replace = open_picker)
    } else {
        bimi_dropzone(uploading = state.uploading, enabled = !state.busy, on_choose = open_picker)
    }

    if (state.logo_errors.isNotEmpty()) {
        v_gap(AsterSpacing.md)
        val lines = state.logo_errors.map { bimi_logo_error_res(it) }.distinct().map { stringResource(it) }
        bimi_alert(
            tone = BimiAlertTone.error,
            title = stringResource(R.string.domain_bimi_errors_title),
            items = lines,
        )
    }

    state.error?.let { kind ->
        v_gap(AsterSpacing.md)
        bimi_alert(tone = BimiAlertTone.error, message = bimi_error_text(kind))
    }

    v_gap(AsterSpacing.xl)
    bimi_rules_list(errors = state.logo_errors, has_logo = has_logo)

    if (has_logo) {
        v_gap(AsterSpacing.xl)
        bimi_inbox_preview(domain_name = domain_name, png = state.view?.preview_png)
    }

    if (state.adjustments.isNotEmpty()) {
        v_gap(AsterSpacing.lg)
        bimi_adjustments_row(state.adjustments)
    }

    v_gap(AsterSpacing.lg)
    bimi_note(icon = Icons.Rounded.Visibility, text = stringResource(R.string.domain_bimi_logo_public_note))
}

@Composable
private fun bimi_dropzone(uploading: Boolean, enabled: Boolean, on_choose: () -> Unit) {
    val colors = AsterMaterial.colors
    val dash_color = colors.border_primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = dash_color,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Image,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(32.dp),
        )
        v_gap(AsterSpacing.sm)
        Text(
            text = stringResource(R.string.domain_bimi_rule_svg),
            color = colors.text_secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        v_gap(AsterSpacing.md)
        AsterSecondaryButton(
            label = stringResource(if (uploading) R.string.domain_bimi_uploading else R.string.domain_bimi_choose_file),
            onClick = on_choose,
            enabled = enabled,
            is_loading = uploading,
        )
    }
}

@Composable
private fun bimi_file_row(state: BimiUiState, on_replace: () -> Unit) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border_secondary, shape)
            .padding(start = AsterSpacing.md, end = AsterSpacing.xs, top = AsterSpacing.xs, bottom = AsterSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bimi_logo_image(
            png = state.view?.preview_png,
            size = 44.dp,
            content_description = stringResource(R.string.domain_bimi_preview_alt),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
            bimi_first_line_icon(Icons.Rounded.CheckCircle, colors.success, 16.dp, 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.domain_bimi_logo_ready),
                color = colors.text_primary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        bimi_compact_button(
            label = stringResource(if (state.uploading) R.string.domain_bimi_uploading else R.string.domain_bimi_replace_logo),
            on_click = on_replace,
            enabled = !state.busy,
        )
    }
}
