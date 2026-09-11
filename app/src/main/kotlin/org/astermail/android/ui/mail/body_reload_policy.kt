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

internal enum class renderer_gone_action { regenerate, plain_text_fallback, stop }

internal class body_reload_policy(
    private val max_reloads_per_load: Int = 2,
    private val max_watchdog_regenerations: Int = 1,
    private val max_regenerations: Int = 2,
) {
    private var reloads = 0
    private var watchdog_regenerations = 0
    private var regenerations = 0
    private var fell_back = false

    fun page_alive(painted: Boolean, visual_ready: Boolean, content_height: Int): Boolean =
        painted || visual_ready || content_height > 0

    fun begin_load() {
        reloads = 0
    }

    fun reloads_remaining(): Boolean = reloads < max_reloads_per_load

    fun should_reload(painted: Boolean, visual_ready: Boolean, content_height: Int): Boolean {
        if (page_alive(painted, visual_ready, content_height)) return false
        if (reloads >= max_reloads_per_load) return false
        reloads++
        return true
    }

    fun should_regenerate(painted: Boolean, visual_ready: Boolean, content_height: Int): Boolean {
        if (page_alive(painted, visual_ready, content_height)) return false
        if (watchdog_regenerations >= max_watchdog_regenerations) return false
        if (regenerations >= max_regenerations) return false
        watchdog_regenerations++
        regenerations++
        return true
    }

    fun on_renderer_gone(): renderer_gone_action {
        if (regenerations < max_regenerations) {
            regenerations++
            return renderer_gone_action.regenerate
        }
        if (!fell_back) {
            fell_back = true
            return renderer_gone_action.plain_text_fallback
        }
        return renderer_gone_action.stop
    }
}
