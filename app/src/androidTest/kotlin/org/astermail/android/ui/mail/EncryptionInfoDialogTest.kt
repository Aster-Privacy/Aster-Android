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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionInfoDialogTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val e2e_text =
        "Messages to this recipient are encrypted with their identity key. Only they can read them."
    private val transit_text =
        "Messages to this recipient are encrypted in transit using TLS. " +
            "The recipient's server may be able to read them."

    private fun count_nodes(node: SemanticsNode, predicate: (SemanticsNode) -> Boolean): Int =
        (if (predicate(node)) 1 else 0) + node.children.sumOf { count_nodes(it, predicate) }

    private fun SemanticsNodeInteraction.count_text_nodes(): Int =
        count_nodes(fetchSemanticsNode()) { it.config.getOrNull(SemanticsProperties.Text) != null }

    private fun SemanticsNodeInteraction.count_icon_nodes(): Int =
        count_nodes(fetchSemanticsNode()) {
            it.config.getOrNull(SemanticsProperties.ContentDescription) != null
        }

    @Test
    fun the_encrypted_body_is_one_plain_paragraph_with_no_icons() {
        compose_rule.setContent { AsterTheme { encryption_info_body(is_encrypted = true) } }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText(e2e_text).assertIsDisplayed()
        assertEquals(
            "the dialog body must be a single explanatory paragraph",
            1,
            compose_rule.onRoot().count_text_nodes(),
        )
        assertEquals(
            "the dialog body must not draw icons or a feature list",
            0,
            compose_rule.onRoot().count_icon_nodes(),
        )
    }

    @Test
    fun an_unencrypted_thread_gets_the_transit_wording_instead() {
        compose_rule.setContent { AsterTheme { encryption_info_body(is_encrypted = false) } }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText(transit_text).assertIsDisplayed()
        compose_rule.onNodeWithText(e2e_text).assertDoesNotExist()
    }
}
