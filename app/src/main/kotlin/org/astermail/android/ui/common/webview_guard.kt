// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.translation.TranslationRuntime

object WebViewGuard {
    @Volatile
    private var usable = false

    fun usable(context: Context): Boolean {
        if (usable) return true
        val ok = runCatching {
            android.webkit.CookieManager.getInstance()
            android.webkit.WebSettings.getDefaultUserAgent(context.applicationContext)
            true
        }.getOrDefault(false)
        if (ok) usable = true
        return ok
    }
}

@Composable
fun remember_webview_usable(): Boolean {
    val context = LocalContext.current
    return remember { WebViewGuard.usable(context) }
}

@Composable
fun webview_unavailable_notice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = AsterMaterial.colors
    val name = remember { TranslationRuntime.webview_name(context) }
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.webview_unavailable, name),
            color = colors.text_secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { TranslationRuntime.open_webview_update(context) }) {
            Text(text = stringResource(R.string.translation_webview_update), color = colors.accent_blue)
        }
    }
}
