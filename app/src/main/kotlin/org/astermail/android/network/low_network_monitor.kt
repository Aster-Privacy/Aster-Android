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

package org.astermail.android.network

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat
import org.astermail.android.api.network.is_data_saver_restricted
import org.astermail.android.api.network.low_network_state

object low_network_monitor {

    private const val PREFS_NAME = "aster_low_network"
    private const val KEY_PREFERENCE_ENABLED = "low_network_preference_enabled"

    private var registered = false

    fun start(context: Context) {
        val app_context = context.applicationContext
        low_network_state.set_preference(read_cached_preference(app_context))
        refresh_data_saver(app_context)
        if (registered) return
        registered = true
        runCatching { register_callbacks(app_context) }
    }

    fun apply_preference(context: Context, enabled: Boolean) {
        val app_context = context.applicationContext
        app_context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREFERENCE_ENABLED, enabled)
            .apply()
        low_network_state.set_preference(enabled)
    }

    fun read_cached_preference(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFERENCE_ENABLED, false)

    fun is_active(context: Context): Boolean {
        val app_context = context.applicationContext
        refresh_data_saver(app_context)
        if (!low_network_state.is_preference_enabled()) {
            low_network_state.set_preference(read_cached_preference(app_context))
        }
        return low_network_state.active()
    }

    fun refresh_data_saver(context: Context) {
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            low_network_state.set_data_saver(false)
            return
        }
        val restricted = runCatching {
            is_data_saver_restricted(
                is_metered = manager.isActiveNetworkMetered,
                restrict_background_status = manager.restrictBackgroundStatus,
            )
        }.getOrDefault(false)
        low_network_state.set_data_saver(restricted)
    }

    private fun register_callbacks(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) = refresh_data_saver(context)

                override fun onLost(network: android.net.Network) = refresh_data_saver(context)

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    capabilities: android.net.NetworkCapabilities,
                ) = refresh_data_saver(context)
            },
        )
        ContextCompat.registerReceiver(
            context,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(received_context: Context?, intent: android.content.Intent?) {
                    refresh_data_saver(context)
                }
            },
            android.content.IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
