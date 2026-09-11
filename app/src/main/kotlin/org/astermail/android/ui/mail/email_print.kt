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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.astermail.android.util.clip_units

internal const val REMOTE_IMAGE_PROXY_BASE = "https://app.astermail.org/api/images/v1/proxy?url="

private const val PRINT_JOB_NAME_MAX_UNITS = 80

private const val CONTEXT_UNWRAP_LIMIT = 16

private val print_job_name_forbidden = Regex("[\\p{Cntrl}\\u2028\\u2029/\\\\:*?\"<>|]")

private val print_whitespace_run = Regex("\\s+")

private val active_print_views = mutableSetOf<WebView>()

internal data class email_print_labels(
    val from: String,
    val to: String,
    val cc: String,
    val date: String,
    val image_blocked: String,
)

internal fun escape_print_text(value: String): String = buildString(value.length) {
    for (ch in value) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

internal fun print_job_name(subject: String, fallback: String): String {
    val cleaned = subject
        .replace(print_job_name_forbidden, " ")
        .replace(print_whitespace_run, " ")
        .trim()
        .clip_units(PRINT_JOB_NAME_MAX_UNITS)
        .trim()
    return cleaned.ifEmpty { fallback.clip_units(PRINT_JOB_NAME_MAX_UNITS) }
}

internal fun print_sender_line(msg: ThreadMessage): String {
    val name = displayed_sender_name(msg.display_sender_name, msg.sender_name).trim()
    val address = displayed_sender_email(msg.display_sender_email, msg.sender_email).trim()
    return when {
        address.isEmpty() -> name
        name.isEmpty() || name.equals(address, ignoreCase = true) -> address
        else -> "$name <$address>"
    }
}

internal fun print_recipient_line(addresses: List<String>, fallback: String): String =
    addresses
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")
        .ifEmpty { fallback.trim() }

internal fun build_email_print_body(
    msg: ThreadMessage,
    allow_external: Boolean,
    sanitize_options: EmailHtmlSanitizer.SanitizeOptions,
    image_blocked_label: String,
): String {
    val plain = "<pre class=\"aster-print-plain\">${escape_print_text(msg.body)}</pre>"
    val html = msg.body_html?.takeIf { it.isNotBlank() } ?: return plain
    return runCatching {
        val sanitized = EmailHtmlSanitizer.sanitize(html, sanitize_options)
        if (allow_external) {
            proxy_external_urls(sanitized, REMOTE_IMAGE_PROXY_BASE)
        } else {
            EmailHtmlSanitizer.neutralize_blocked_backgrounds(
                EmailHtmlSanitizer.replace_blocked_images(sanitized, image_blocked_label),
            )
        }
    }.getOrElse { plain }
}

private fun print_meta_row(label: String, value: String): String =
    "<tr><th>${escape_print_text(label)}</th><td>${escape_print_text(value)}</td></tr>"

internal fun build_email_print_html(
    msg: ThreadMessage,
    subject: String,
    date_text: String,
    labels: email_print_labels,
    body_html: String,
): String {
    val to_line = print_recipient_line(msg.to_addresses, msg.to_label)
    val cc_line = print_recipient_line(msg.cc_addresses, "")
    val rows = buildString {
        append(print_meta_row(labels.from, print_sender_line(msg)))
        if (to_line.isNotEmpty()) append(print_meta_row(labels.to, to_line))
        if (cc_line.isNotEmpty()) append(print_meta_row(labels.cc, cc_line))
        if (date_text.isNotBlank()) append(print_meta_row(labels.date, date_text))
    }
    val heading = subject.trim().takeIf { it.isNotEmpty() }?.let { "<h1>${escape_print_text(it)}</h1>" }.orEmpty()
    return buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<style>")
        append("body{font-family:sans-serif;color:#111;background:#fff;margin:0;padding:24px;overflow-wrap:anywhere;}")
        append(".aster-print-header h1{font-size:20px;line-height:1.3;margin:0 0 12px 0;}")
        append(".aster-print-meta{border-collapse:collapse;font-size:12px;color:#444;}")
        append(".aster-print-meta th{text-align:start;font-weight:600;padding:2px 12px 2px 0;vertical-align:top;white-space:nowrap;}")
        append(".aster-print-meta td{padding:2px 0;}")
        append(".aster-print-rule{border:none;border-top:1px solid #ccc;margin:16px 0;}")
        append(".aster-print-body img{max-width:100%;height:auto;}")
        append(".aster-print-plain{white-space:pre-wrap;font-family:inherit;margin:0;}")
        append(".blocked-image{display:inline-block;color:#666;border:1px dashed #bbb;padding:2px 6px;font-size:12px;}")
        append("</style></head><body dir=\"auto\">")
        append("<header class=\"aster-print-header\">")
        append(heading)
        append("<table class=\"aster-print-meta\">")
        append(rows)
        append("</table></header><hr class=\"aster-print-rule\"/>")
        append("<div class=\"aster-print-body\">")
        append(body_html)
        append("</div></body></html>")
    }
}

internal fun find_activity(context: Context): Activity? {
    var current: Context? = context
    repeat(CONTEXT_UNWRAP_LIMIT) {
        when (val candidate = current) {
            is Activity -> return candidate
            is ContextWrapper -> current = candidate.baseContext
            else -> return null
        }
    }
    return null
}

internal class retaining_print_adapter(
    private val delegate: PrintDocumentAdapter,
    private val failure_message: CharSequence,
    private val on_finished: () -> Unit,
) : PrintDocumentAdapter() {

    private var finished = false

    override fun onStart() {
        runCatching { delegate.onStart() }
    }

    override fun onLayout(
        old_attributes: PrintAttributes?,
        new_attributes: PrintAttributes?,
        cancellation_signal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?,
    ) {
        try {
            delegate.onLayout(old_attributes, new_attributes, cancellation_signal, callback, extras)
        } catch (failure: RuntimeException) {
            callback?.onLayoutFailed(failure_message)
        }
    }

    override fun onWrite(
        pages: Array<PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellation_signal: CancellationSignal?,
        callback: WriteResultCallback?,
    ) {
        try {
            delegate.onWrite(pages, destination, cancellation_signal, callback)
        } catch (failure: RuntimeException) {
            callback?.onWriteFailed(failure_message)
        }
    }

    override fun onFinish() {
        try {
            runCatching { delegate.onFinish() }
        } finally {
            release()
        }
    }

    fun release() {
        if (finished) return
        finished = true
        on_finished()
    }
}

internal fun print_email_document(
    context: Context,
    job_name: String,
    html: String,
    allow_network: Boolean,
    failure_message: String,
    on_failure: () -> Unit,
) {
    val activity = find_activity(context)
    val print_manager = activity?.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (activity == null || print_manager == null) {
        on_failure()
        return
    }
    val web_view = runCatching { WebView(activity) }.getOrNull()
    if (web_view == null) {
        on_failure()
        return
    }
    var released = false
    var started = false
    fun release() {
        if (released) return
        released = true
        active_print_views.remove(web_view)
        runCatching { web_view.destroy() }
    }
    web_view.settings.apply {
        javaScriptEnabled = false
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        loadsImagesAutomatically = true
        blockNetworkLoads = !allow_network
        blockNetworkImage = !allow_network
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
    web_view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

        override fun onPageFinished(view: WebView, url: String?) {
            if (started || released) return
            started = true
            val job = runCatching {
                val adapter = retaining_print_adapter(
                    delegate = view.createPrintDocumentAdapter(job_name),
                    failure_message = failure_message,
                    on_finished = { release() },
                )
                print_manager.print(job_name, adapter, PrintAttributes.Builder().build())
            }.getOrNull()
            if (job == null) {
                release()
                on_failure()
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val was_started = started
            release()
            if (!was_started) on_failure()
            return true
        }
    }
    active_print_views.add(web_view)
    runCatching {
        web_view.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
    }.onFailure {
        release()
        on_failure()
    }
}
