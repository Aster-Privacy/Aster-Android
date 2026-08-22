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

package org.astermail.android.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.contacts.ContactsApi
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.DecryptedEnvelope
import org.astermail.android.mail.MailRepository

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val mail_api: MailApi,
    private val contacts_api: ContactsApi,
    private val mail_repository: MailRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    sealed class ExportStep {
        object Warning : ExportStep()
        object Scope : ExportStep()
        object Format : ExportStep()
        object Progress : ExportStep()
        object Complete : ExportStep()
    }

    data class ExportUiState(
        val step: ExportStep = ExportStep.Warning,
        val acknowledged: Boolean = false,
        val include_mail: Boolean = true,
        val include_contacts: Boolean = true,
        val format: String = "mbox",
        val processed: Int = 0,
        val total: Int = 0,
        val bytes_written: Long = 0,
        val skipped_undecryptable: Int = 0,
        val error: String? = null,
        val export_file: File? = null,
        val is_running: Boolean = false,
    )

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    private var export_job: Job? = null

    fun set_acknowledged(v: Boolean) = _state.update { it.copy(acknowledged = v) }
    fun proceed_to_scope() = _state.update { it.copy(step = ExportStep.Scope) }
    fun set_include_mail(v: Boolean) = _state.update { it.copy(include_mail = v) }
    fun set_include_contacts(v: Boolean) = _state.update { it.copy(include_contacts = v) }
    fun set_format(f: String) = _state.update { it.copy(format = f) }

    fun proceed_from_scope() {
        if (_state.value.include_mail) {
            _state.update { it.copy(step = ExportStep.Format) }
        } else {
            start_export()
        }
    }

    fun start_export() {
        _state.update {
            it.copy(
                step = ExportStep.Progress,
                is_running = true,
                processed = 0,
                total = 0,
                bytes_written = 0,
                skipped_undecryptable = 0,
                error = null,
            )
        }
        export_job = viewModelScope.launch { run_export() }
    }

    fun cancel_export() {
        export_job?.cancel()
        _state.value.export_file?.delete()
        _state.update { it.copy(is_running = false, export_file = null, step = ExportStep.Scope) }
    }

    fun reset() {
        export_job?.cancel()
        _state.value.export_file?.delete()
        _state.value = ExportUiState()
    }

    fun share_export() {
        val file = _state.value.export_file ?: return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, "Save export")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (t: Throwable) {
            _state.update { it.copy(error = "Could not share file: ${t.message}") }
        }
    }

    private suspend fun run_export() {
        try {
            val ts = System.currentTimeMillis()
            val export_dir = File(context.cacheDir, "exports").apply { mkdirs() }
            export_dir.listFiles()?.forEach { runCatching { it.delete() } }
            val zip_file = File(export_dir, "aster_export_$ts.zip")
            var total_bytes = 0L

            val zos = ZipOutputStream(BufferedOutputStream(zip_file.outputStream()))
            try {
                if (_state.value.include_mail) total_bytes += export_mail(zos)
                if (_state.value.include_contacts) total_bytes += export_contacts(zos)
                total_bytes += write_readme(zos)
            } finally {
                runCatching { zos.close() }
            }

            _state.update {
                it.copy(
                    step = ExportStep.Complete,
                    is_running = false,
                    export_file = zip_file,
                    bytes_written = total_bytes,
                )
            }
        } catch (_: CancellationException) {
            // handled in cancel_export
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    is_running = false,
                    error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.something_went_wrong),
                    ),
                )
            }
        }
    }

    private suspend fun export_mail(zos: ZipOutputStream): Long {
        var bytes = 0L
        val is_mbox = _state.value.format == "mbox"
        if (is_mbox) {
            zos.putNextEntry(ZipEntry("mailbox.mbox"))
        }
        var eml_idx = 0
        var cursor: String? = null
        var has_more = true

        while (has_more) {
            yield()
            val response = mail_api.list_messages(
                limit = 50,
                cursor = cursor,
                item_type = "all",
                include_spam = true,
                include_trash = false,
            )
            if (cursor == null) _state.update { it.copy(total = it.total + response.total) }

            for (item in response.items) {
                val env = mail_repository.decrypt_item_for_export(item)
                if (env == null) {
                    _state.update {
                        it.copy(
                            processed = it.processed + 1,
                            skipped_undecryptable = it.skipped_undecryptable + 1,
                        )
                    }
                    continue
                }
                val item_bytes: Long
                if (is_mbox) {
                    val entry = format_mbox(item, env)
                    val b = entry.toByteArray(Charsets.UTF_8)
                    zos.write(b)
                    item_bytes = b.size.toLong()
                } else {
                    val entry = format_eml(item, env)
                    val name = "eml/${eml_idx.toString().padStart(6, '0')}_${item.id.take(8)}.eml"
                    zos.putNextEntry(ZipEntry(name))
                    val b = entry.toByteArray(Charsets.UTF_8)
                    zos.write(b)
                    item_bytes = b.size.toLong()
                    zos.closeEntry()
                    eml_idx++
                }
                bytes += item_bytes
                _state.update { it.copy(processed = it.processed + 1, bytes_written = it.bytes_written + item_bytes) }
            }
            cursor = response.next_cursor
            has_more = response.has_more
        }

        if (is_mbox) zos.closeEntry()
        return bytes
    }

    private suspend fun export_contacts(zos: ZipOutputStream): Long {
        val sb = StringBuilder("[")
        var cursor: String? = null
        var has_more = true
        var first = true
        while (has_more) {
            yield()
            val r = contacts_api.list_contacts(limit = 100, cursor = cursor)
            for (c in r.items) {
                if (!first) sb.append(",")
                sb.append(
                    org.json.JSONObject()
                        .put("id", c.id)
                        .put("token", c.contact_token ?: "")
                        .put("created_at", c.created_at ?: "")
                        .toString(),
                )
                first = false
            }
            cursor = r.next_cursor
            has_more = r.has_more
        }
        sb.append("]")
        val b = sb.toString().toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry("contacts.json"))
        zos.write(b)
        zos.closeEntry()
        return b.size.toLong()
    }

    private fun format_mbox(item: MailItem, env: DecryptedEnvelope): String {
        val from_email = env.from_email.ifBlank { "unknown@astermail.org" }
        val date = env.sent_at ?: item.created_at ?: ""
        return buildString {
            append("From $from_email $date\n")
            append("From: ${addr_header(env.from_name, env.from_email)}\n")
            val to = env.to.joinToString(", ") { (n, e) -> addr_header(n, e) }
            if (to.isNotBlank()) append("To: $to\n")
            val cc = env.cc.joinToString(", ") { (n, e) -> addr_header(n, e) }
            if (cc.isNotBlank()) append("Cc: $cc\n")
            append("Subject: ${env.subject}\n")
            if (date.isNotBlank()) append("Date: $date\n")
            append("Message-ID: <${item.id}@astermail.org>\n")
            append("Content-Type: text/plain; charset=utf-8\n\n")
            val body = env.body_text.ifBlank { strip_html(env.body_html ?: "") }
            append(body.replace("\nFrom ", "\n>From "))
            append("\n\n")
        }
    }

    private fun format_eml(item: MailItem, env: DecryptedEnvelope): String {
        val date = env.sent_at ?: item.created_at ?: ""
        return buildString {
            append("From: ${addr_header(env.from_name, env.from_email)}\n")
            val to = env.to.joinToString(", ") { (n, e) -> addr_header(n, e) }
            if (to.isNotBlank()) append("To: $to\n")
            val cc = env.cc.joinToString(", ") { (n, e) -> addr_header(n, e) }
            if (cc.isNotBlank()) append("Cc: $cc\n")
            append("Subject: ${env.subject}\n")
            if (date.isNotBlank()) append("Date: $date\n")
            append("Message-ID: <${item.id}@astermail.org>\n")
            append("MIME-Version: 1.0\n")
            append("Content-Type: text/plain; charset=utf-8\n\n")
            val body = env.body_text.ifBlank { strip_html(env.body_html ?: "") }
            append(body)
            append("\n")
        }
    }

    private fun addr_header(name: String, email: String): String =
        if (name.isNotBlank()) "\"$name\" <$email>" else email

    private fun strip_html(html: String): String =
        html.replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .trim()

    private fun write_readme(zos: ZipOutputStream): Long {
        val s = _state.value
        val txt = buildString {
            appendLine("Aster Mail Export")
            appendLine("=================")
            appendLine()
            if (s.include_mail) {
                if (s.format == "mbox") appendLine("- mailbox.mbox  All emails in MBOX format (RFC 4155)")
                else appendLine("- eml/          Individual .eml files")
            }
            if (s.include_contacts) appendLine("- contacts.json Contact metadata")
            appendLine()
            appendLine("Emails in this export are decrypted. Keep this file secure.")
        }
        val b = txt.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry("README.txt"))
        zos.write(b)
        zos.closeEntry()
        return b.size.toLong()
    }
}
