//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.GUARD_VETO_INVALID_CODE
import org.astermail.android.api.api_error_code
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.ui.common.page_surface

const val GUARD_VETO_HOST = "app.astermail.org"
const val GUARD_VETO_PATH = "/guard/veto"
private const val MAX_VETO_PART = 512

class guard_veto_link(val id: String, val t: String) {
    override fun toString(): String = "guard_veto_link(id=$id)"
    override fun equals(other: Any?): Boolean = other is guard_veto_link && other.id == id && other.t == t
    override fun hashCode(): Int = id.hashCode() * 31 + t.hashCode()
}

fun parse_guard_veto(raw: String?): guard_veto_link? {
    if (raw.isNullOrBlank()) return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != GUARD_VETO_HOST) return null
    if (uri.path?.trimEnd('/') != GUARD_VETO_PATH) return null
    val params = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
        val parts = pair.split('=', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val key = runCatching { URLDecoder.decode(parts[0], "UTF-8") }.getOrNull() ?: return@mapNotNull null
        val value = runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrNull() ?: return@mapNotNull null
        key to value
    }.toMap()
    val id = params["id"]?.takeIf { is_safe_part(it) } ?: return null
    val t = params["t"]?.takeIf { is_safe_part(it) } ?: return null
    return guard_veto_link(id, t)
}

private fun is_safe_part(value: String): Boolean =
    value.isNotEmpty() &&
        value.length <= MAX_VETO_PART &&
        value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' || it == '.' || it == '~' }

sealed interface guard_veto_state {
    data object confirm : guard_veto_state
    data object working : guard_veto_state
    data object done : guard_veto_state
    data object invalid : guard_veto_state
    data object error : guard_veto_state
}

@HiltViewModel
class GuardVetoViewModel @Inject constructor(private val api: GuardApi) : ViewModel() {

    private val _state = MutableStateFlow<guard_veto_state>(guard_veto_state.confirm)
    val state: StateFlow<guard_veto_state> = _state.asStateFlow()

    fun stop(link: guard_veto_link) {
        val current = _state.value
        if (current != guard_veto_state.confirm && current != guard_veto_state.error) return
        _state.value = guard_veto_state.working
        viewModelScope.launch {
            _state.value = try {
                api.veto_with_token(link.id, link.t)
                guard_veto_state.done
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (is_invalid(t)) guard_veto_state.invalid else guard_veto_state.error
            }
        }
    }

    private fun is_invalid(t: Throwable): Boolean {
        val code = api_error_code(t)
        return t is ApiError.NotFoundError || code == GUARD_VETO_INVALID_CODE || code == GUARD_REQUEST_GONE_CODE
    }
}

@Composable
fun guard_veto_host(link: guard_veto_link, on_close: () -> Unit, on_change_password: (() -> Unit)? = null) {
    val view_model: GuardVetoViewModel = hiltViewModel(key = "guard_veto_${link.id}")
    val state by view_model.state.collectAsStateWithLifecycle()
    guard_veto_screen(
        state = state,
        on_stop = { view_model.stop(link) },
        on_close = on_close,
        on_change_password = on_change_password,
    )
}

@Composable
fun guard_veto_screen(
    state: guard_veto_state,
    on_stop: () -> Unit,
    on_close: () -> Unit,
    on_change_password: (() -> Unit)? = null,
) {
    val colors = AsterMaterial.colors
    BackHandler(enabled = state != guard_veto_state.working) { on_close() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors),
    ) {
        guard_wizard_result {
            when (state) {
                guard_veto_state.confirm -> guard_result_block(
                    title = stringResource(R.string.guard_veto_title),
                    body = stringResource(R.string.guard_rescue_banner),
                    primary_label = stringResource(R.string.guard_rescue_banner_stop),
                    on_primary = on_stop,
                    primary_destructive = true,
                    secondary_label = stringResource(R.string.cancel),
                    on_secondary = on_close,
                )
                guard_veto_state.working -> {
                    guard_wordmark()
                    Spacer(Modifier.height(AsterSpacing.xxl + AsterSpacing.sm))
                    guard_wizard_title(stringResource(R.string.guard_veto_title), Modifier.fillMaxWidth(), TextAlign.Center)
                    Spacer(Modifier.height(AsterSpacing.sm))
                    guard_wizard_body(stringResource(R.string.guard_veto_working), Modifier.fillMaxWidth(), TextAlign.Center)
                    Spacer(Modifier.height(AsterSpacing.xxl))
                    CircularProgressIndicator(color = colors.text_secondary, modifier = Modifier.size(28.dp))
                }
                guard_veto_state.done -> if (on_change_password != null) {
                    guard_result_block(
                        title = stringResource(R.string.guard_veto_done_title),
                        body = stringResource(R.string.guard_veto_done_body),
                        primary_label = stringResource(R.string.change_password),
                        on_primary = on_change_password,
                        secondary_label = stringResource(R.string.done),
                        on_secondary = on_close,
                    )
                } else {
                    guard_result_block(
                        title = stringResource(R.string.guard_veto_done_title),
                        body = stringResource(R.string.guard_veto_done_body),
                        primary_label = stringResource(R.string.done),
                        on_primary = on_close,
                    )
                }
                guard_veto_state.invalid -> guard_result_block(
                    title = stringResource(R.string.guard_veto_invalid_title),
                    body = stringResource(R.string.guard_veto_invalid),
                    primary_label = stringResource(R.string.done),
                    on_primary = on_close,
                )
                guard_veto_state.error -> guard_result_block(
                    title = stringResource(R.string.guard_veto_error_title),
                    body = stringResource(R.string.guard_veto_error),
                    primary_label = stringResource(R.string.guard_rescue_banner_stop),
                    on_primary = on_stop,
                    primary_destructive = true,
                    secondary_label = stringResource(R.string.close),
                    on_secondary = on_close,
                )
            }
        }
    }
}
