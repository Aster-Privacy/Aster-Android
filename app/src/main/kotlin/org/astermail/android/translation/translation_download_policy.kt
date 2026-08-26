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

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object TranslationDownloadPolicy {
    private const val PREFS_NAME = "aster_translation"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_CONSENT = "granted_packs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun wifi_only(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun set_wifi_only(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun metered(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun download_blocked(context: Context): Boolean = wifi_only(context) && metered(context)

    private fun granted(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_CONSENT, emptySet()) ?: emptySet()

    fun route_packs(from: String, to: String): List<String> {
        if (from == to) return emptyList()
        if (from == PIVOT_LANGUAGE || to == PIVOT_LANGUAGE) return listOf("$from$to")
        return listOf("$from$PIVOT_LANGUAGE", "$PIVOT_LANGUAGE$to")
    }

    fun route_consent_granted(context: Context, from: String, to: String): Boolean {
        val packs = route_packs(from, to)
        if (packs.isEmpty()) return true
        return granted(context).containsAll(packs)
    }

    fun grant_route_consent(context: Context, from: String, to: String) {
        val updated = granted(context).toMutableSet()
        updated.addAll(route_packs(from, to))
        prefs(context).edit().putStringSet(KEY_CONSENT, updated).apply()
    }

    fun clear_consent(context: Context) {
        prefs(context).edit().remove(KEY_CONSENT).apply()
    }
}
