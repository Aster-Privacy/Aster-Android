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

package org.astermail.android.ui.auth

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial

private const val TURNSTILE_SITE_KEY = "0x4AAAAAACNiLyqNYRKmMGIY"
private const val TURNSTILE_DOMAIN = "app.astermail.org"

private class TurnstileBridge(
    private val on_token: (String) -> Unit,
    private val on_error: (String) -> Unit,
    private val on_expired: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onToken(token: String) { handler.post { on_token(token) } }

    @JavascriptInterface
    fun onError(msg: String) { handler.post { on_error(msg) } }

    @JavascriptInterface
    fun onExpired() { handler.post { on_expired() } }
}

private val turnstile_allowed_hosts = setOf(
    TURNSTILE_DOMAIN,
    "challenges.cloudflare.com",
)

private class AssetLoaderWebViewClient(
    private val asset_loader: WebViewAssetLoader,
    private val on_render_gone: () -> Unit,
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        return asset_loader.shouldInterceptRequest(request.url)
            ?: super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val host = request.url.host
        if (host == null || host !in turnstile_allowed_hosts) return true
        return false
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: android.webkit.RenderProcessGoneDetail?,
    ): Boolean {
        runCatching {
            (view?.parent as? android.view.ViewGroup)?.removeView(view)
            view?.destroy()
        }
        on_render_gone()
        return true
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurnstileWidget(
    on_token: (String) -> Unit,
    on_error: (String) -> Unit = {},
    on_expired: () -> Unit = {},
    reset_trigger: Int = 0,
    show_fallback: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val theme = if (org.astermail.android.design.AsterMaterial.colors.is_dark) "dark" else "light"
    val current_on_token = rememberUpdatedState(on_token)
    val current_on_error = rememberUpdatedState(on_error)
    val current_on_expired = rememberUpdatedState(on_expired)
    val web_view_ref = remember { mutableStateOf<WebView?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var exhausted by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var last_failure_at by remember { mutableLongStateOf(0L) }
    val guarded_on_token: (String) -> Unit = { token ->
        attempts = 0
        exhausted = false
        current_on_token.value(token)
    }
    val guarded_on_error: (String) -> Unit = { reason ->
        last_failure_at = SystemClock.elapsedRealtime()
        if (attempts >= turnstile_max_auto_resets) exhausted = true
        current_on_error.value(reason)
    }
    val guarded_on_expired: () -> Unit = {
        last_failure_at = SystemClock.elapsedRealtime()
        current_on_expired.value()
    }
    val live_on_expired = rememberUpdatedState(guarded_on_expired)
    val live_on_token = rememberUpdatedState(guarded_on_token)
    val live_on_error = rememberUpdatedState(guarded_on_error)

    LaunchedEffect(reset_trigger) {
        if (reset_trigger <= 0) return@LaunchedEffect
        val automatic = SystemClock.elapsedRealtime() - last_failure_at < turnstile_auto_window_ms
        if (!automatic) {
            attempts = 0
            exhausted = false
            val view = web_view_ref.value
            if (view == null) generation += 1 else view.evaluateJavascript("_reset()", null)
            return@LaunchedEffect
        }
        if (exhausted) return@LaunchedEffect
        if (attempts >= turnstile_max_auto_resets) {
            exhausted = true
            return@LaunchedEffect
        }
        delay(turnstile_reset_backoff_ms(attempts))
        attempts += 1
        web_view_ref.value?.evaluateJavascript("_reset()", null)
    }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    if (exhausted && show_fallback) {
        Text(
            text = stringResource(R.string.captcha_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = AsterMaterial.colors.text_secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TextButton(onClick = {
            attempts = 0
            exhausted = false
            generation += 1
        }) {
            Text(text = stringResource(R.string.captcha_retry))
        }
    } else if (!exhausted || web_view_ref.value != null) key(generation) {
    AndroidView(
        factory = { context ->
            runCatching {
                val asset_loader = WebViewAssetLoader.Builder()
                    .setDomain(TURNSTILE_DOMAIN)
                    .addPathHandler(
                        "/assets/",
                        WebViewAssetLoader.AssetsPathHandler(context),
                    )
                    .build()

                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setGeolocationEnabled(false)
                    settings.userAgentString = (settings.userAgentString ?: "")
                        .replace("; wv", "")
                    webViewClient = AssetLoaderWebViewClient(asset_loader) {
                        web_view_ref.value = null
                        exhausted = true
                        live_on_error.value("render_process_gone")
                    }
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(
                        TurnstileBridge(
                            on_token = { live_on_token.value(it) },
                            on_error = { live_on_error.value(it) },
                            on_expired = { live_on_expired.value() },
                        ),
                        "AsterBridge",
                    )
                    loadUrl(
                        "https://$TURNSTILE_DOMAIN/assets/turnstile.html?sitekey=$TURNSTILE_SITE_KEY&theme=$theme",
                    )
                }.also { web_view_ref.value = it }
            }.getOrElse { error ->
                web_view_ref.value = null
                exhausted = true
                current_on_error.value(error.message ?: "webview_unavailable")
                android.widget.FrameLayout(context)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(75.dp),
        onRelease = { view ->
            (view as? WebView)?.let { web_view ->
                runCatching {
                    web_view.removeJavascriptInterface("AsterBridge")
                    web_view.stopLoading()
                    web_view.loadUrl("about:blank")
                    web_view.removeAllViews()
                    web_view.destroy()
                }
            }
            web_view_ref.value = null
        },
    )
    }
    }
}

private const val turnstile_max_auto_resets = 3
private const val turnstile_auto_window_ms = 1_500L

internal fun turnstile_reset_backoff_ms(attempt: Int): Long =
    1_000L shl attempt.coerceIn(0, 4)
