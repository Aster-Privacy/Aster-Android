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

package org.astermail.android.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class probe_view_model : ViewModel() {
    var payload: String = ""
}

@RunWith(AndroidJUnit4::class)
class SharedSettingsScopeTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val shared = mutableListOf<probe_view_model>()
    private val per_destination = mutableListOf<probe_view_model>()

    @Composable
    private fun destination(label: String) {
        val host = LocalContext.current.host_activity()
        assertNotNull("the settings host activity must resolve from the compose context", host)
        shared += viewModel<probe_view_model>(host!!)
        per_destination += viewModel<probe_view_model>()
        Text(label)
    }

    @Test
    fun settings_destinations_share_one_view_model_instance() {
        compose_rule.setContent {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = "settings") {
                composable("settings") {
                    destination("open signature")
                    Text("go", modifier = androidx.compose.ui.Modifier)
                }
                composable("signature") { destination("signature tab") }
            }
            androidx.compose.material3.TextButton(onClick = { nav.navigate("signature") }) {
                Text("navigate")
            }
        }

        compose_rule.waitForIdle()
        shared.first().payload = "already loaded"

        compose_rule.onNodeWithText("navigate").performClick()
        compose_rule.waitForIdle()

        assertSame(
            "every settings destination must reuse the activity-scoped view model",
            shared.first(),
            shared.last(),
        )
        assertNotSame(
            "the regression this guards against: a fresh empty view model per destination",
            per_destination.first(),
            per_destination.last(),
        )
        assertSame(
            "the shared view model must still hold its loaded state on the next tab",
            "already loaded",
            shared.last().payload,
        )
    }
}
