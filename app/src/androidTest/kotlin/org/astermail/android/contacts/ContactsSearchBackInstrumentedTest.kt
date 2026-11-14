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

package org.astermail.android.contacts

import androidx.compose.material3.Text
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.ContactsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsSearchBackInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val search_input = "contact_search_bar_input"

    private val jarvis_contacts = (1..40).map { n ->
        Contact(id = "j_$n", name = "Jarvis %02d".format(n), email = "jarvis$n@example.test")
    }
    private val other_contacts = (1..5).map { n ->
        Contact(id = "a_$n", name = "Alice %02d".format(n), email = "alice$n@example.test")
    }

    private fun build_vm(contacts: List<Contact> = other_contacts + jarvis_contacts): ContactsViewModel {
        val repository = mockk<ContactsRepository>(relaxed = true)
        every { repository.pending_contact_ids } returns MutableStateFlow(emptySet())
        coEvery { repository.fetch_contacts(any()) } returns Result.success(contacts)
        coEvery { repository.list_contact_groups() } returns Result.success(emptyList())
        coEvery { repository.trash_contact(any()) } returns Result.success(Unit)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ContactsViewModel(repository, context)
    }

    private fun render(vm: ContactsViewModel): () -> NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            AsterTheme {
                nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { Text("home_screen") }
                    composable("contacts") {
                        ContactsScreen(
                            on_back = { nav.popBackStack() },
                            on_open_contact = { id -> nav.navigate("detail/$id") },
                            on_create_contact = {},
                            vm = vm,
                        )
                    }
                    composable("detail/{id}") { entry ->
                        Text("detail_${entry.arguments?.getString("id")}")
                    }
                }
            }
        }
        compose.runOnIdle { nav.navigate("contacts") }
        compose.waitForIdle()
        return { nav }
    }

    private fun search(text: String) {
        compose.onNodeWithTag(search_input).performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun back_from_contact_returns_to_same_search_results_and_scroll() {
        val nav = render(build_vm())
        search("Jarvis")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Jarvis 35"))
        compose.onNodeWithText("Jarvis 35").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("detail_j_35").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag(search_input).assert(hasText("Jarvis"))
        compose.onNodeWithText("Jarvis 35").assertIsDisplayed()
        compose.onNodeWithText("Alice 01").assertDoesNotExist()
        assertEquals("contacts", nav().currentBackStackEntry?.destination?.route)
    }

    @Test
    fun second_back_clears_search_and_third_back_leaves_contacts() {
        val nav = render(build_vm())
        search("Jarvis")
        compose.onNodeWithText("Jarvis 01").performClick()
        compose.waitForIdle()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(search_input).assert(hasText("Jarvis"))

        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals("contacts", nav().currentBackStackEntry?.destination?.route)
        compose.onNodeWithTag(search_input).assert(!hasText("Jarvis"))
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Alice 01"))
        compose.onNodeWithText("Alice 01").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("home_screen").assertIsDisplayed()
    }

    @Test
    fun contact_deleted_from_detail_is_gone_from_search_results() {
        val vm = build_vm()
        render(vm)
        search("Jarvis")
        compose.onNodeWithText("Jarvis 01").performClick()
        compose.waitForIdle()
        compose.runOnIdle { vm.delete_contact("j_1") }
        compose.waitForIdle()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag(search_input).assert(hasText("Jarvis"))
        compose.onNodeWithText("Jarvis 01").assertDoesNotExist()
        compose.onNodeWithText("Jarvis 02").assertIsDisplayed()
    }

    @Test
    fun search_survives_activity_recreation() {
        val vm = build_vm()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AsterTheme {
                ContactsScreen(on_open_contact = {}, on_create_contact = {}, vm = vm)
            }
        }
        compose.waitForIdle()
        search("Jarvis")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag(search_input).assert(hasText("Jarvis"))
        compose.onNodeWithText("Alice 01").assertDoesNotExist()
    }
}
