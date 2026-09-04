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

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.security.MessageDigest
import org.astermail.android.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.astermail.android.api.imports.CreateJobRequest
import org.astermail.android.api.imports.ImportApi
import org.astermail.android.api.imports.JobDetails
import org.astermail.android.api.imports.JobSummary
import org.astermail.android.api.imports.UploadChunkRequest
import org.astermail.android.api.imports.UploadInitRequest
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard

private const val CHUNK_SIZE = 4 * 1024 * 1024
private const val MAX_TOTAL_BYTES = 10L * 1024 * 1024 * 1024
private const val max_visible_sync_jobs = 5
private const val ACTION_FAILED = "action_failed"

data class ImportUiState(
    val is_uploading: Boolean = false,
    val current_chunk: Int = 0,
    val total_chunks: Int = 0,
    val file_name: String? = null,
    val error: String? = null,
    val job_created: Boolean = false,
    val jobs: List<JobSummary> = emptyList(),
    val expanded_job_id: String? = null,
    val expanded_details: JobDetails? = null,
    val expanded_details_failed: Boolean = false,
    val acting_job_id: String? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val api: ImportApi,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var poll_job: Job? = null

    fun toggle_job(job_id: String) {
        if (_state.value.expanded_job_id == job_id) {
            stop_polling()
            _state.value = _state.value.copy(
                expanded_job_id = null,
                expanded_details = null,
                expanded_details_failed = false,
            )
        } else {
            _state.value = _state.value.copy(
                expanded_job_id = job_id,
                expanded_details = null,
                expanded_details_failed = false,
            )
            start_polling(job_id)
        }
    }

    private fun start_polling(job_id: String) {
        poll_job?.cancel()
        poll_job = viewModelScope.launch {
            while (isActive && _state.value.expanded_job_id == job_id) {
                runCatching { withContext(Dispatchers.IO) { api.get_job(job_id) } }
                    .onSuccess { d ->
                        _state.value = _state.value.copy(expanded_details = d, expanded_details_failed = false)
                    }
                    .onFailure {
                        if (_state.value.expanded_details == null) {
                            _state.value = _state.value.copy(expanded_details_failed = true)
                        }
                    }
                delay(3000)
            }
        }
    }

    private fun stop_polling() {
        poll_job?.cancel()
        poll_job = null
    }

    fun pause(job_id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(acting_job_id = job_id, error = null)
            val result = runCatching { withContext(Dispatchers.IO) { api.pause_job(job_id) } }

            _state.value = _state.value.copy(
                acting_job_id = null,
                error = if (result.isFailure) ACTION_FAILED else null,
            )
            load_jobs()
        }
    }

    fun retry_failed(job_id: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(acting_job_id = job_id, error = null)
            val result = runCatching { withContext(Dispatchers.IO) { api.retry_failed(job_id) } }

            _state.value = _state.value.copy(
                acting_job_id = null,
                error = if (result.isFailure) ACTION_FAILED else null,
            )
            load_jobs()
        }
    }

    override fun onCleared() {
        stop_polling()
        super.onCleared()
    }

    fun load_jobs() {
        viewModelScope.launch {
            runCatching { api.list_jobs() }
                .onSuccess { jobs -> _state.value = _state.value.copy(jobs = jobs) }
                .onFailure { _state.value = _state.value.copy(error = ACTION_FAILED) }
        }
    }

    fun start_import(uri: Uri, file_name: String, total_size: Long, read_bytes: suspend (offset: Long, len: Int) -> ByteArray) {
        val kind = when {
            file_name.endsWith(".mbox", ignoreCase = true) -> "mbox"
            file_name.endsWith(".eml", ignoreCase = true) -> "eml"
            else -> "mbox"
        }
        if (total_size > MAX_TOTAL_BYTES) {
            _state.value = _state.value.copy(error = "file_too_large")
            return
        }
        val total_chunks = ((total_size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
        _state.value = _state.value.copy(
            is_uploading = true,
            current_chunk = 0,
            total_chunks = total_chunks,
            file_name = file_name,
            error = null,
            job_created = false,
        )
        viewModelScope.launch {
            try {
                val init = withContext(Dispatchers.IO) {
                    api.upload_init(UploadInitRequest(
                        kind = kind,
                        total_chunks = total_chunks,
                        chunk_size = CHUNK_SIZE,
                        total_size = total_size,
                    ))
                }
                val token = init.upload_token
                var offset = 0L
                var index = 0
                while (offset < total_size) {
                    val len = minOf(CHUNK_SIZE.toLong(), total_size - offset).toInt()
                    val chunk = withContext(Dispatchers.IO) { read_bytes(offset, len) }
                    val sha = MessageDigest.getInstance("SHA-256").digest(chunk)
                    val sha_b64 = Base64.encodeToString(sha, Base64.NO_WRAP)
                    val data_b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    withContext(Dispatchers.IO) {
                        api.upload_chunk(token, UploadChunkRequest(
                            chunk_index = index,
                            chunk_sha256_b64 = sha_b64,
                            data_b64 = data_b64,
                        ))
                    }
                    index += 1
                    offset += len
                    _state.value = _state.value.copy(current_chunk = index)
                }
                withContext(Dispatchers.IO) { api.upload_finalize(token) }
                withContext(Dispatchers.IO) {
                    api.create_job(CreateJobRequest(kind = kind, upload_token = token))
                }
                _state.value = _state.value.copy(is_uploading = false, job_created = true)
                load_jobs()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_uploading = false,
                    error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.something_went_wrong),
                    ),
                )
            }
        }
    }
}

private fun friendly_kind_label(kind: String, context: android.content.Context): String {
    return when (kind.lowercase()) {
        "external_sync" -> context.getString(R.string.import_kind_external_sync)
        "oauth_initial" -> context.getString(R.string.import_kind_oauth_initial)
        "mbox" -> context.getString(R.string.import_kind_mbox)
        "eml" -> context.getString(R.string.import_kind_eml)
        else -> kind.split('_').joinToString(" ") { part ->
            if (part.isEmpty()) part else part.replaceFirstChar { ch -> ch.uppercaseChar() }
        }
    }
}

private fun friendly_state_label(state_value: String, context: android.content.Context): String {
    return when (state_value.lowercase()) {
        "queued" -> context.getString(R.string.import_state_queued)
        "running" -> context.getString(R.string.import_state_running)
        "paused_quota" -> context.getString(R.string.import_state_paused_quota)
        "needs_reauth" -> context.getString(R.string.import_state_needs_reauth)
        "done" -> context.getString(R.string.import_state_done)
        "failed" -> context.getString(R.string.import_state_failed)
        "cancelled" -> context.getString(R.string.import_state_cancelled)
        else -> state_value.split('_').joinToString(" ") { part ->
            if (part.isEmpty()) part else part.replaceFirstChar { ch -> ch.uppercaseChar() }
        }
    }
}

@Composable
private fun state_pill(state_value: String, on_click: (() -> Unit)? = null) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val (bg, fg) = when (state_value.lowercase()) {
        "needs_reauth" -> colors.warning.copy(alpha = 0.18f) to colors.warning
        "failed" -> colors.danger.copy(alpha = 0.15f) to colors.danger
        "done" -> colors.success.copy(alpha = 0.15f) to colors.success
        "paused_quota" -> colors.warning.copy(alpha = 0.15f) to colors.warning
        "running", "queued" -> colors.accent_blue.copy(alpha = 0.15f) to colors.accent_blue
        "cancelled" -> colors.text_tertiary.copy(alpha = 0.15f) to colors.text_tertiary
        else -> colors.text_tertiary.copy(alpha = 0.15f) to colors.text_tertiary
    }
    Box(
        modifier = Modifier
            .clip(SquircleShape(999.dp))
            .background(bg)
            .then(if (on_click != null) Modifier.clickable(onClick = on_click) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = friendly_state_label(state_value, context),
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun provider_row(
    icon_res: Int,
    label: String,
    trailing_label: String?,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(18.dp))
            .border(1.dp, colors.text_tertiary.copy(alpha = 0.18f), SquircleShape(18.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(SquircleShape(8.dp))
                .background(colors.text_tertiary.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = icon_res),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.size(AsterSpacing.md))
        Text(
            text = label,
            color = colors.text_primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailing_label != null) {
            Box(
                modifier = Modifier
                    .clip(SquircleShape(999.dp))
                    .background(colors.text_tertiary.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = trailing_label,
                    color = colors.text_secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun ImportScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    vm: ImportViewModel = hiltViewModel(),
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load_jobs() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        var file_name = "import.mbox"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name_idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val size_idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (name_idx >= 0) file_name = cursor.getString(name_idx) ?: file_name
                if (size_idx >= 0) size = cursor.getLong(size_idx)
            }
        }
        if (size <= 0) {
            resolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(8192)
                var n = stream.read(buf)
                while (n >= 0) { size += n; n = stream.read(buf) }
            }
        }
        val final_size = size
        val final_name = file_name
        scope.launch {
            vm.start_import(uri, final_name, final_size) { offset, len ->
                val bytes = ByteArray(len)
                val stream = resolver.openInputStream(uri)
                    ?: throw java.io.IOException("cannot open import stream")
                var read = 0
                stream.use { s ->
                    var remaining = offset
                    val skip_buf = ByteArray(8192)
                    while (remaining > 0) {
                        val n = s.read(skip_buf, 0, minOf(skip_buf.size.toLong(), remaining).toInt())
                        if (n <= 0) break
                        remaining -= n
                    }
                    while (read < len) {
                        val n = s.read(bytes, read, len - read)
                        if (n <= 0) break
                        read += n
                    }
                }
                if (read < len) throw java.io.IOException("short read: expected $len, got $read")
                bytes
            }
        }
    }

    detail_scaffold(title = stringResource(R.string.import_title), on_back = on_back) {
        section_label(stringResource(R.string.import_section_connect))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.import_connect_title),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.import_connect_description),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.size(AsterSpacing.md))
                val open_url: (String) -> Unit = { url ->
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    )
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    org.astermail.android.ui.common.start_external_intent(context, intent)
                }
                provider_row(
                    icon_res = R.drawable.ic_brand_gmail,
                    label = stringResource(R.string.import_provider_gmail),
                    trailing_label = null,
                    on_click = { on_open("external_accounts_gmail") },
                )
                Spacer(Modifier.size(AsterSpacing.sm))
                provider_row(
                    icon_res = R.drawable.ic_brand_outlook,
                    label = stringResource(R.string.import_provider_outlook),
                    trailing_label = null,
                    on_click = { open_url("https://app.astermail.org/settings?section=import&provider=microsoft") },
                )
                Spacer(Modifier.size(AsterSpacing.sm))
                provider_row(
                    icon_res = R.drawable.ic_external_link,
                    label = stringResource(R.string.import_provider_other_imap),
                    trailing_label = null,
                    on_click = { on_open("external_accounts") },
                )
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.import_section_file))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.mail_import),
                    color = colors.text_primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(AsterSpacing.xs))
                Text(
                    text = stringResource(R.string.import_description),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.size(AsterSpacing.lg))

                if (state.is_uploading) {
                    val progress = if (state.total_chunks > 0) state.current_chunk.toFloat() / state.total_chunks else 0f
                    Text(
                        text = stringResource(R.string.import_chunk_progress, state.current_chunk, state.total_chunks),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.size(AsterSpacing.sm))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accent_blue,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(18.dp))
                            .background(colors.accent_blue)
                            .clickable {
                                launcher.launch("*/*")
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.import_pick_file),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (state.job_created) {
                    Spacer(Modifier.size(AsterSpacing.sm))
                    Text(
                        text = stringResource(R.string.import_job_created),
                        color = colors.text_secondary,
                        fontSize = 13.sp,
                    )
                }
                state.error?.let { err ->
                    Spacer(Modifier.size(AsterSpacing.sm))
                    val upload_failed_text = stringResource(R.string.import_upload_failed)
                    val text = when (err) {
                        "file_too_large" -> stringResource(R.string.import_file_too_large)
                        ACTION_FAILED -> stringResource(R.string.something_went_wrong)
                        else -> err.ifBlank { upload_failed_text }
                    }
                    Text(text = text, color = colors.danger, fontSize = 13.sp)
                }
            }
        }

        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.import_section_sync))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AsterSpacing.lg)) {
                Text(
                    text = stringResource(R.string.import_sync_title),
                    color = colors.text_primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(AsterSpacing.sm))
                if (state.jobs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.import_no_jobs),
                        color = colors.text_tertiary,
                        fontSize = 13.sp,
                    )
                } else {
                    for (job in state.jobs.take(max_visible_sync_jobs)) {
                        val is_expanded = state.expanded_job_id == job.id
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.toggle_job(job.id) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = friendly_kind_label(job.kind, context),
                                        color = colors.text_primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = relative_time_label(job.created_at),
                                        color = colors.text_tertiary,
                                        fontSize = 11.sp,
                                    )
                                }
                                val reauth_action: (() -> Unit)? =
                                    if (job.state.equals("needs_reauth", ignoreCase = true)) {
                                        { on_open("external_accounts") }
                                    } else null
                                state_pill(state_value = job.state, on_click = reauth_action)
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = is_expanded,
                                enter = androidx.compose.animation.expandVertically(
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    expandFrom = androidx.compose.ui.Alignment.Top,
                                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)),
                                exit = androidx.compose.animation.shrinkVertically(
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                                    shrinkTowards = androidx.compose.ui.Alignment.Top,
                                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 140)),
                            ) {
                                androidx.compose.foundation.layout.Column {
                                val details = state.expanded_details
                                Spacer(Modifier.size(AsterSpacing.xs))
                                if (details == null) {
                                    Text(
                                        text = if (state.expanded_details_failed) {
                                            stringResource(R.string.failed_to_load)
                                        } else {
                                            stringResource(R.string.loading)
                                        },
                                        color = if (state.expanded_details_failed) colors.danger else colors.text_tertiary,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    val tc = details.task_counts
                                    val total = tc.pending + tc.claimed + tc.done + tc.failed + tc.dead_letter
                                    val progress = if (total > 0) (tc.done + tc.failed + tc.dead_letter).toFloat() / total else 0f
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = colors.accent_blue,
                                    )
                                    Spacer(Modifier.size(AsterSpacing.xs))
                                    Text(text = stringResource(R.string.import_job_state, friendly_state_label(details.job.state, context)), color = colors.text_secondary, fontSize = 12.sp)
                                    Text(text = stringResource(R.string.import_task_pending, tc.pending), color = colors.text_tertiary, fontSize = 12.sp)
                                    Text(text = stringResource(R.string.import_task_claimed, tc.claimed), color = colors.text_tertiary, fontSize = 12.sp)
                                    Text(text = stringResource(R.string.import_task_done, tc.done), color = colors.text_tertiary, fontSize = 12.sp)
                                    Text(text = stringResource(R.string.import_task_failed, tc.failed), color = colors.danger, fontSize = 12.sp)
                                    if (tc.dead_letter > 0) {
                                        Text(text = stringResource(R.string.import_task_dead, tc.dead_letter), color = colors.danger, fontSize = 12.sp)
                                    }
                                    if (details.failures_by_reason.isNotEmpty()) {
                                        Spacer(Modifier.size(AsterSpacing.xs))
                                        Text(
                                            text = stringResource(R.string.import_recent_failures),
                                            color = colors.text_secondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        for (reason in details.failures_by_reason) {
                                            Text(
                                                text = stringResource(R.string.import_failure_reason, reason.reason_code, reason.count),
                                                color = colors.text_tertiary,
                                                fontSize = 11.sp,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.size(AsterSpacing.sm))
                                    Row(horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm)) {
                                        org.astermail.android.design.components.AsterButton(
                                            label = stringResource(R.string.import_pause),
                                            onClick = { vm.pause(job.id) },
                                            enabled = state.acting_job_id != job.id,
                                        )
                                        org.astermail.android.design.components.AsterButton(
                                            label = stringResource(R.string.import_retry_failed),
                                            onClick = { vm.retry_failed(job.id) },
                                            enabled = state.acting_job_id != job.id && tc.failed > 0,
                                        )
                                    }
                                }
                                Spacer(Modifier.size(AsterSpacing.sm))
                                }
                            }
                        }
                    }
                }
            }
        }

        v_gap(AsterSpacing.xxl)
    }
}
