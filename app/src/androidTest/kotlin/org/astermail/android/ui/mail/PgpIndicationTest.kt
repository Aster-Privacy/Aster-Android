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

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.astermail.android.crypto.PgpSignatureStatus
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PgpIndicationTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun message(
        id: String,
        is_external: Boolean,
        pgp_encrypted: Boolean = false,
    ) = ThreadMessage(
        id = id,
        sender_name = "Someone",
        sender_email = "someone@example.com",
        to_label = "me",
        timestamp = 0L,
        body = "hello",
        is_external = is_external,
        pgp_encrypted = pgp_encrypted,
    )

    private fun save_device_screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = instrumentation.targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun capture_pgp_details_panel() {
        compose_rule.setContent {
            AsterTheme {
                message_details_panel(
                    sender = "Jane Perry <jane@example.com>",
                    reply_to = null,
                    date_text = "31 August 2026 at 15:43",
                    is_encrypted = true,
                    tracker_count = 0,
                    received_on = null,
                    authentication = "SPF pass, DKIM pass, DMARC pass",
                    authentication_failed = false,
                    on_show_trackers = null,
                    pgp_encrypted = true,
                    pgp_signature = PgpSignatureStatus.UNVERIFIED,
                )
            }
        }
        compose_rule.waitForIdle()
        save_device_screenshot("pgp_details_panel")
    }

    @Test
    fun an_inbound_pgp_message_shows_the_openpgp_wording_and_its_signature() {
        compose_rule.setContent {
            AsterTheme {
                message_details_panel(
                    sender = "Someone <someone@example.com>",
                    reply_to = null,
                    date_text = "31 Aug 2026",
                    is_encrypted = true,
                    tracker_count = 0,
                    received_on = null,
                    authentication = null,
                    authentication_failed = false,
                    on_show_trackers = null,
                    pgp_encrypted = true,
                    pgp_signature = PgpSignatureStatus.UNVERIFIED,
                )
            }
        }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("End-to-end encrypted (OpenPGP)").assertIsDisplayed()
        compose_rule.onNodeWithText("Digital signature").assertIsDisplayed()
        compose_rule.onNodeWithText("Signed, not verified").assertIsDisplayed()
    }

    @Test
    fun an_ordinary_external_message_keeps_the_transit_wording() {
        compose_rule.setContent {
            AsterTheme {
                message_details_panel(
                    sender = "Someone <someone@example.com>",
                    reply_to = null,
                    date_text = "31 Aug 2026",
                    is_encrypted = false,
                    tracker_count = 0,
                    received_on = null,
                    authentication = null,
                    authentication_failed = false,
                    on_show_trackers = null,
                )
            }
        }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("Encrypted in transit").assertIsDisplayed()
        compose_rule.onNodeWithText("Digital signature").assertDoesNotExist()
    }

    @Test
    fun a_thread_holding_an_inbound_pgp_message_reads_as_openpgp() {
        val messages = listOf(
            message(id = "a", is_external = false),
            message(id = "b", is_external = true, pgp_encrypted = true),
        )
        compose_rule.setContent {
            AsterTheme {
                Text(
                    encryption_badge_label(
                        thread_is_end_to_end_encrypted(messages),
                        thread_is_pgp_encrypted(messages),
                    ),
                )
            }
        }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("End-to-end encrypted (OpenPGP)").assertIsDisplayed()
    }

    @Test
    fun the_encryption_dialog_explains_openpgp() {
        compose_rule.setContent {
            AsterTheme { encryption_info_body(is_encrypted = true, is_pgp = true) }
        }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText(
            "The sender encrypted this message with OpenPGP before sending it.",
            substring = true,
        ).assertIsDisplayed()
    }
}
