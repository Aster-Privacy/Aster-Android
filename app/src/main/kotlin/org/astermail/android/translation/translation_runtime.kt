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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.webkit.WebViewCompat

object TranslationRuntime {
    const val MIN_WEBVIEW_MAJOR = 91
    private const val DEFAULT_WEBVIEW_PACKAGE = "com.google.android.webview"

    internal fun parse_major(version_name: String?): Int? =
        version_name?.trim()?.substringBefore('.')?.toIntOrNull()

    internal fun major_supported(major: Int?): Boolean = major == null || major >= MIN_WEBVIEW_MAJOR

    private fun webview_package_info(context: Context) =
        runCatching { WebViewCompat.getCurrentWebViewPackage(context.applicationContext) }.getOrNull()

    fun webview_supported(context: Context): Boolean =
        major_supported(parse_major(webview_package_info(context)?.versionName))

    fun open_webview_update(context: Context) {
        val name = webview_package_info(context)?.packageName ?: DEFAULT_WEBVIEW_PACKAGE
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$name"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$name"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
