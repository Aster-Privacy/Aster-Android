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

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionBadgeLabelTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val e2e_text = context.getString(R.string.end_to_end_encrypted)
    private val transit_text = context.getString(R.string.protected_in_transit)

    private fun message(
        id: String,
        is_external: Boolean,
        has_recipient_key: Boolean? = null,
    ) = ThreadMessage(
        id = id,
        sender_name = "Someone",
        sender_email = "someone@example.com",
        to_label = "me",
        timestamp = 0L,
        body = "hello",
        is_encrypted = true,
        is_external = is_external,
        has_recipient_key = has_recipient_key,
    )

    private fun render(messages: List<ThreadMessage>) {
        compose_rule.setContent {
            AsterTheme {
                Text(encryption_badge_label(thread_is_end_to_end_encrypted(messages)))
            }
        }
    }

    @Test
    fun external_mail_reads_protected_in_transit() {
        render(listOf(message("a", is_external = true)))

        compose_rule.onNodeWithText(transit_text).assertExists()
        compose_rule.onNodeWithText(e2e_text).assertDoesNotExist()
    }

    @Test
    fun internal_mail_still_reads_end_to_end_encrypted() {
        render(listOf(message("a", is_external = false)))

        compose_rule.onNodeWithText(e2e_text).assertExists()
        compose_rule.onNodeWithText(transit_text).assertDoesNotExist()
    }

    @Test
    fun a_mixed_thread_reads_protected_in_transit() {
        render(
            listOf(
                message("a", is_external = false),
                message("b", is_external = true),
            ),
        )

        compose_rule.onNodeWithText(transit_text).assertExists()
    }

    @Test
    fun external_mail_encrypted_to_a_known_key_reads_end_to_end_encrypted() {
        render(listOf(message("a", is_external = true, has_recipient_key = true)))

        compose_rule.onNodeWithText(e2e_text).assertExists()
    }

    @Test
    fun an_empty_thread_reads_protected_in_transit() {
        render(emptyList())

        compose_rule.onNodeWithText(transit_text).assertExists()
    }
}
