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

package org.astermail.android.ui.mail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterTextField
import org.astermail.android.design.SquircleShape

private sealed interface pdf_view_state {
    data object loading : pdf_view_state
    data class locked(val incorrect: Boolean) : pdf_view_state
    data object unsupported : pdf_view_state
    data object failed : pdf_view_state
    class ready(val document: pdf_document) : pdf_view_state
}

@Composable
internal fun pdf_attachment_viewer(
    bytes: ByteArray,
    filename: String,
    fallback: @Composable (message: String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember(bytes) { mutableStateOf<pdf_view_state>(pdf_view_state.loading) }
    var is_unlocking by remember(bytes) { mutableStateOf(false) }
    var is_disposed by remember(bytes) { mutableStateOf(false) }

    fun apply_result(result: pdf_open_result) {
        if (is_disposed) {
            if (result is pdf_open_result.opened) result.document.close()
            return
        }
        state = when (result) {
            is pdf_open_result.opened -> pdf_view_state.ready(result.document)
            is pdf_open_result.failed -> when (result.reason) {
                pdf_open_failure.password_required -> pdf_view_state.locked(incorrect = false)
                pdf_open_failure.password_incorrect -> pdf_view_state.locked(incorrect = true)
                pdf_open_failure.password_unsupported -> pdf_view_state.unsupported
                pdf_open_failure.unreadable -> pdf_view_state.failed
            }
        }
    }

    LaunchedEffect(bytes) {
        apply_result(pdf_document.open(context, bytes, null))
    }

    DisposableEffect(bytes) {
        onDispose {
            is_disposed = true
            (state as? pdf_view_state.ready)?.document?.close()
        }
    }

    when (val current = state) {
        pdf_view_state.loading -> CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(36.dp).testTag("pdf_loading"),
        )
        is pdf_view_state.locked -> pdf_password_prompt(
            incorrect = current.incorrect,
            is_unlocking = is_unlocking,
            on_submit = { attempt ->
                if (!is_unlocking) {
                    is_unlocking = true
                    scope.launch {
                        val result = pdf_document.open(context, bytes, attempt)
                        is_unlocking = false
                        apply_result(result)
                    }
                }
            },
        )
        pdf_view_state.unsupported -> fallback(stringResource(R.string.pdf_password_unsupported))
        pdf_view_state.failed -> fallback(stringResource(R.string.pdf_preview_failed))
        is pdf_view_state.ready -> pdf_pages(current.document, filename)
    }
}

@Composable
private fun pdf_password_prompt(
    incorrect: Boolean,
    is_unlocking: Boolean,
    on_submit: (String) -> Unit,
) {
    val colors = AsterMaterial.colors
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = {
        if (is_pdf_password_acceptable(password) && !is_unlocking) {
            val attempt = password
            password = ""
            on_submit(attempt)
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(SquircleShape(20.dp))
            .background(colors.bg_card)
            .padding(20.dp)
            .imePadding()
            .testTag("pdf_password_prompt"),
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(
            imageVector = TablerIcons.Lock,
            contentDescription = null,
            tint = colors.text_primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.pdf_password_title),
            color = colors.text_primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.pdf_password_description),
            color = colors.text_secondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        AsterTextField(
            value = password,
            onValueChange = { if (it.length <= MAX_PDF_PASSWORD_LENGTH) password = it },
            placeholder = stringResource(R.string.pdf_password_hint),
            singleLine = true,
            enabled = !is_unlocking,
            visual_transformation = PasswordVisualTransformation(),
            keyboard_options = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboard_actions = KeyboardActions(onDone = { submit() }),
            error_text = if (incorrect && password.isEmpty()) stringResource(R.string.pdf_password_incorrect) else null,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("pdf_password_field"),
        )
        Spacer(Modifier.height(16.dp))
        AsterButton(
            label = stringResource(R.string.open),
            onClick = submit,
            enabled = password.isNotEmpty() && !is_unlocking,
            is_loading = is_unlocking,
            modifier = Modifier.fillMaxWidth().testTag("pdf_password_submit"),
        )
    }
}

@Composable
private fun pdf_pages(document: pdf_document, filename: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("pdf_pages")) {
        val density = LocalDensity.current
        val target_width_px = with(density) { (maxWidth - 24.dp).roundToPx() }.coerceAtLeast(1)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items((0 until document.page_count).toList(), key = { it }) { index ->
                pdf_page(document, index, target_width_px, filename)
            }
        }
    }
}

@Composable
private fun pdf_page(document: pdf_document, index: Int, target_width_px: Int, filename: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, document, index, target_width_px) {
        value = document.render_page(index, target_width_px)
    }
    val bmp = bitmap
    val description = stringResource(R.string.pdf_page_of_total, index + 1, document.page_count)
    if (bmp != null) {
        DisposableEffect(bmp) {
            onDispose { runCatching { bmp.recycle() } }
        }
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                .clip(SquircleShape(6.dp))
                .testTag("pdf_page_$index"),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f)
                .clip(SquircleShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun pdf_fallback_message(message: String) {
    Text(
        text = message,
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("pdf_fallback_message"),
    )
}
