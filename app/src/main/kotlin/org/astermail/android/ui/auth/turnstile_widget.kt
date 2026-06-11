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
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

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

private class AssetLoaderWebViewClient(
    private val asset_loader: WebViewAssetLoader,
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        return asset_loader.shouldInterceptRequest(request.url)
            ?: super.shouldInterceptRequest(view, request)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurnstileWidget(
    on_token: (String) -> Unit,
    on_error: (String) -> Unit = {},
    on_expired: () -> Unit = {},
    reset_trigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val current_on_token = rememberUpdatedState(on_token)
    val current_on_error = rememberUpdatedState(on_error)
    val current_on_expired = rememberUpdatedState(on_expired)
    val web_view_ref = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(reset_trigger) {
        if (reset_trigger > 0) {
            web_view_ref.value?.evaluateJavascript("_reset()", null)
        }
    }

    AndroidView(
        factory = { context ->
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
                settings.userAgentString = settings.userAgentString
                    .replace("; wv", "")
                webViewClient = AssetLoaderWebViewClient(asset_loader)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                addJavascriptInterface(
                    TurnstileBridge(
                        on_token = { current_on_token.value(it) },
                        on_error = { current_on_error.value(it) },
                        on_expired = { current_on_expired.value() },
                    ),
                    "AsterBridge",
                )
                loadUrl(
                    "https://$TURNSTILE_DOMAIN/assets/turnstile.html?sitekey=$TURNSTILE_SITE_KEY",
                )
            }.also { web_view_ref.value = it }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(75.dp),
        onRelease = { web_view ->
            runCatching {
                web_view.removeJavascriptInterface("AsterBridge")
                web_view.stopLoading()
                web_view.loadUrl("about:blank")
                web_view.removeAllViews()
                web_view.destroy()
            }
            web_view_ref.value = null
        },
    )
}
