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

package org.astermail.android.translation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

internal const val TRANSLATION_ENGINE_ORIGIN = "https://mail-content.invalid/"

internal const val MAX_DETECTION_CHARACTERS = 4000

internal fun translation_engine_document(nonce: String): String =
    "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; " +
        "script-src 'nonce-$nonce' 'wasm-unsafe-eval' https://mail-content.invalid/bergamot/; " +
        "worker-src https://mail-content.invalid/bergamot/; " +
        "connect-src https://mail-content.invalid/bergamot/ https://mail-content.invalid/models/bergamot/; " +
        "img-src 'none'; style-src 'none'; base-uri 'none'; form-action 'none'; " +
        "frame-src 'none'; object-src 'none'\">" +
        "<script nonce=\"$nonce\">import('/bergamot/aster_translate.js').catch(function(){});</script>" +
        "</head><body></body></html>"

internal fun translated_segments_of(json: String): List<String>? = try {
    val array = JSONObject(json).optJSONArray("segments")
    if (array == null) {
        null
    } else {
        (0 until array.length()).map { array.optString(it) }
    }
} catch (_: Throwable) {
    null
}

private const val RENDERER_GONE_STATUS = "{\"state\":\"error\"}"

internal fun translation_engine_nonce(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes)
}

@SuppressLint("SetJavaScriptEnabled")
internal class translation_engine(
    context: Context,
    private val on_detect: (String) -> Unit,
    private val on_status: (String) -> Unit,
    private val on_result: (String) -> Unit,
) {
    private val app_context = context.applicationContext
    private val main_handler = Handler(Looper.getMainLooper())
    private val pending = ArrayDeque<String>()
    private var ready = false
    private var destroyed = false

    private val bridge = object {
        @JavascriptInterface
        fun on_detect(json: String) {
            main_handler.post { if (!destroyed) this@translation_engine.on_detect(json) }
        }

        @JavascriptInterface
        fun on_status(json: String) {
            main_handler.post {
                if (destroyed) return@post
                if (json.contains("\"ready\"")) {
                    ready = true
                    while (pending.isNotEmpty()) evaluate(pending.removeFirst())
                }
                this@translation_engine.on_status(json)
            }
        }

        @JavascriptInterface
        fun on_result(json: String) {
            main_handler.post { if (!destroyed) this@translation_engine.on_result(json) }
        }
    }

    private val web = WebView(app_context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setGeolocationEnabled(false)
        @Suppress("DEPRECATION")
        settings.saveFormData = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = true

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val uri = request?.url ?: return denied()
                if (uri.host != TranslationAssets.CONTENT_HOST) return denied()
                return TranslationAssets.serve(app_context, uri.host, uri.path, true) ?: denied()
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                main_handler.post {
                    if (destroyed) return@post
                    this@translation_engine.on_status(RENDERER_GONE_STATUS)
                    destroy()
                }
                return true
            }
        }
        addJavascriptInterface(bridge, "AsterTranslateBridge")
    }

    init {
        web.loadDataWithBaseURL(
            TRANSLATION_ENGINE_ORIGIN,
            translation_engine_document(translation_engine_nonce()),
            "text/html",
            "UTF-8",
            null,
        )
    }

    fun detect(text: String, accepted: String) {
        val trimmed = text.take(MAX_DETECTION_CHARACTERS)
        enqueue(
            "window.__aster_translate&&window.__aster_translate.detect_text(" +
                "${JSONObject.quote(trimmed)},${JSONObject.quote(accepted)})",
        )
    }

    fun translate(segments: List<String>, from: String, to: String) {
        val payload = JSONArray(segments).toString()
        enqueue(
            "window.__aster_translate&&window.__aster_translate.translate_texts(" +
                "${JSONObject.quote(payload)},${JSONObject.quote(from)},${JSONObject.quote(to)})",
        )
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        pending.clear()
        runCatching {
            web.removeJavascriptInterface("AsterTranslateBridge")
            web.stopLoading()
            web.destroy()
        }
    }

    private fun enqueue(script: String) {
        if (destroyed) return
        if (ready) evaluate(script) else pending.addLast(script)
    }

    private fun evaluate(script: String) {
        if (destroyed) return
        runCatching { web.evaluateJavascript(script, null) }
    }

    private fun denied(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            403,
            "Forbidden",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
}
