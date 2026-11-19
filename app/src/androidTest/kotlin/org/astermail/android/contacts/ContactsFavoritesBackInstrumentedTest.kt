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

package org.astermail.android.contacts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.contacts.Contact
import org.astermail.android.ui.contacts.ContactDetailScreen
import org.astermail.android.ui.contacts.ContactsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsFavoritesBackInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun build_vm(): ContactsViewModel {
        val store = mutableListOf(
            Contact(id = "a", name = "Amara Okafor", email = "amara@example.test", is_favorite = true),
            Contact(id = "c", name = "Camille Laurent", email = "camille@example.test"),
            Contact(id = "d", name = "Dana Whitfield", email = "dana@example.test", is_favorite = true),
        )
        val repository = mockk<ContactsRepository>(relaxed = true)
        every { repository.pending_contact_ids } returns MutableStateFlow(emptySet())
        coEvery { repository.fetch_contacts(any()) } answers { Result.success(store.toList()) }
        coEvery { repository.fetch_contact(any()) } answers {
            val id = firstArg<String>()
            Result.success(store.first { it.id == id })
        }
        coEvery { repository.update_contact(any(), any()) } answers {
            val id = firstArg<String>()
            val updated = secondArg<Contact>()
            store.replaceAll { if (it.id == id) updated else it }
            Result.success(Unit)
        }
        coEvery { repository.list_contact_groups() } returns Result.success(emptyList())
        coEvery { repository.list_group_memberships() } returns Result.success(emptyMap())
        return ContactsViewModel(repository, context)
    }

    private fun render(vm: ContactsViewModel): () -> NavHostController {
        lateinit var nav: NavHostController
        compose.setContent {
            AsterTheme {
                nav = rememberNavController()
                NavHost(navController = nav, startDestination = "home") {
                    composable("home") { androidx.compose.material3.Text("home_screen") }
                    composable("contacts") {
                        ContactsScreen(
                            on_back = { nav.popBackStack() },
                            on_open_contact = { id -> nav.navigate("detail/$id") },
                            on_create_contact = {},
                            vm = vm,
                        )
                    }
                    composable("detail/{id}") { entry ->
                        ContactDetailScreen(
                            contact_id = entry.arguments?.getString("id").orEmpty(),
                            on_back = { nav.popBackStack() },
                            on_edit = {},
                            vm = vm,
                        )
                    }
                }
            }
        }
        compose.runOnIdle { nav.navigate("contacts") }
        compose.waitForIdle()
        return { nav }
    }

    private fun turn_on_favorites_filter() {
        compose.onAllNodesWithContentDescription(context.getString(R.string.tab_favorites)).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Camille Laurent").assertDoesNotExist()
    }

    private fun unstar_open_contact() {
        compose.onNodeWithContentDescription(context.getString(R.string.unfavorite)).performClick()
        compose.waitForIdle()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(context.getString(R.string.favorite)),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun unstarring_from_favorites_returns_to_favorites_without_the_contact() {
        val nav = render(build_vm())
        turn_on_favorites_filter()
        compose.onNodeWithText("Amara Okafor").performClick()
        compose.waitForIdle()
        unstar_open_contact()

        Espresso.pressBack()
        compose.waitForIdle()

        assertEquals("contacts", nav().currentBackStackEntry?.destination?.route)
        compose.onNodeWithText("Dana Whitfield").assertIsDisplayed()
        compose.onNodeWithText("Amara Okafor").assertDoesNotExist()
        compose.onNodeWithText("Camille Laurent").assertDoesNotExist()
    }

    @Test
    fun unstarring_the_last_favorite_keeps_the_favorites_filter() {
        val nav = render(build_vm())
        turn_on_favorites_filter()
        for (name in listOf("Amara Okafor", "Dana Whitfield")) {
            compose.onNodeWithText(name).performClick()
            compose.waitForIdle()
            unstar_open_contact()
            Espresso.pressBack()
            compose.waitForIdle()
            assertEquals("contacts", nav().currentBackStackEntry?.destination?.route)
            compose.onNodeWithText(name).assertDoesNotExist()
        }
        compose.onNodeWithText("Camille Laurent").assertDoesNotExist()
    }
}
