//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.ui.settings.detail.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import org.astermail.android.R
import org.astermail.android.api.settings.sending_block_kind_alias
import org.astermail.android.api.settings.sending_block_kind_domain_address
import org.astermail.android.api.settings.sending_block_kind_primary
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.compose.compose_send_error_bar
import org.astermail.android.ui.compose.sender_mismatch_confirm_dialog
import org.astermail.android.ui.compose.sending_block_row
import org.astermail.android.ui.settings.detail.alias_sending_list
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi", application = Application::class)
class SendingBlocksScreenshotTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }

    @get:Rule
    val compose_rule = createComposeRule()

    private val rows = listOf(
        sending_block_row(sending_block_kind_primary, "primary", "alex@astermail.org", "", true),
        sending_block_row(sending_block_kind_alias, "a1", "alex.shopping@astermail.org", "Shopping", false),
        sending_block_row(sending_block_kind_alias, "a2", "alex.news@aster.cx", "Newsletters", true),
        sending_block_row(sending_block_kind_alias, "g1", "k7q2m9x@aster.cx", "", false),
        sending_block_row(sending_block_kind_domain_address, "d1", "alex@alexrivera.studio", "Studio", false),
        sending_block_row(sending_block_kind_domain_address, "d2", "hello@alexrivera.studio", "Studio", false),
    )

    private fun out_dir(): File = File(System.getProperty("user.dir"), "build/screenshots/sending_blocks").apply { mkdirs() }

    private fun window_roots(): List<View> {
        val global_class = Class.forName("android.view.WindowManagerGlobal")
        val global = global_class.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val names = global_class.getMethod("getViewRootNames").invoke(global) as Array<String>
        val get_root = global_class.getMethod("getRootView", String::class.java)
        return names.mapNotNull { get_root.invoke(global, it) as? View }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        var root: View? = null
        compose_rule.mainClock.autoAdvance = false
        compose_rule.setContent {
            root = LocalView.current
            AsterTheme(use_dark_theme = dark) {
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary)) {
                    content()
                }
            }
        }
        compose_rule.mainClock.advanceTimeBy(1_500)
        compose_rule.waitForIdle()
        val bitmap = compose_rule.runOnIdle {
            val main = requireNotNull(root).rootView
            val out = Bitmap.createBitmap(main.width, main.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            main.draw(canvas)
            window_roots().filter { it !== main && it.width > 0 && it.height > 0 }.forEach { view ->
                val save = canvas.save()
                canvas.translate(((main.width - view.width) / 2f), ((main.height - view.height) / 2f))
                view.draw(canvas)
                canvas.restoreToCount(save)
            }
            out
        }
        val file = File(out_dir(), "${name}_${if (dark) "dark" else "light"}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun settings_list_primary_off_with_search() = shoot("1_sending_list_search") {
        Box(Modifier.padding(top = 24.dp)) {
            alias_sending_list(
                all_rows = rows,
                ghost_ids = setOf("g1"),
                loaded = true,
                pending_ids = emptySet(),
                query = "alex",
                on_query_change = {},
                on_toggle = { _, _ -> },
            )
        }
    }

    @Test
    fun compose_blocked_send_error() = shoot("2_blocked_send_error") {
        Column(Modifier.padding(top = 24.dp)) {
            compose_send_error_bar(
                message = stringResource(R.string.error_alias_sending_disabled),
                show_upgrade = false,
                on_upgrade = {},
                on_dismiss = {},
            )
        }
    }

    @Test
    fun reply_sender_mismatch_warning() = shoot("3_mismatch_warning") {
        sender_mismatch_confirm_dialog(
            received = "alex.shopping@astermail.org",
            from = "alex@astermail.org",
            can_use_received = true,
            on_use_received = {},
            on_cancel = {},
            on_send_anyway = {},
        )
    }
}
