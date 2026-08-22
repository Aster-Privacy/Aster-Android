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

package org.astermail.android.billing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.flow.MutableStateFlow

const val BILLING_RETURN_HOST = "app.astermail.org"
const val BILLING_RETURN_PATH = "/settings/billing"
const val BILLING_RETURN_BASE = "https://$BILLING_RETURN_HOST$BILLING_RETURN_PATH"
const val BILLING_RETURN_SUCCESS = "$BILLING_RETURN_BASE?billing=success"
const val BILLING_RETURN_CANCELLED = "$BILLING_RETURN_BASE?billing=cancelled"
const val PRICING_URL = "https://astermail.org/pricing"
const val FAMILY_MANAGE_URL = "https://app.astermail.org/settings/family"
const val CREDITS_URL = "https://app.astermail.org/settings/credits"
const val ACADEMIC_URL = "https://app.astermail.org/settings/billing?academic=1"

enum class billing_return_outcome { success, cancelled, open }

object billing_return_store {
    val outcome = MutableStateFlow<billing_return_outcome?>(null)
}

fun parse_billing_return(uri: Uri?): billing_return_outcome? {
    if (uri == null) return null
    if (uri.scheme != "https" || uri.host != BILLING_RETURN_HOST) return null
    if (uri.path?.trimEnd('/') != BILLING_RETURN_PATH) return null
    val value = uri.getQueryParameter("billing") ?: uri.getQueryParameter("addon_purchase") ?: return billing_return_outcome.open
    return when (value) {
        "success" -> billing_return_outcome.success
        "cancelled", "canceled", "cancel" -> billing_return_outcome.cancelled
        else -> billing_return_outcome.open
    }
}

fun open_billing_tab(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    val tab = runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().apply {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchUrl(context, uri)
        }
    }
    if (tab.isSuccess) return true
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

fun open_billing_in_app(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BILLING_RETURN_BASE))
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    runCatching { context.startActivity(intent) }
}
