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
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EmailAuthBadgeScreenshotTest {

    @get:Rule
    val compose_rule = createAndroidComposeRule<org.astermail.android.MainActivity>()

    private fun message(
        id: String,
        name: String,
        email: String,
        results: Triple<String?, String?, String?>,
        body: String,
        display_sender_name: String? = null,
        display_sender_email: String? = null,
        item_type: String = "received",
    ) = ThreadMessage(
        id = id,
        sender_name = name,
        sender_email = email,
        to_label = "me",
        to_addresses = listOf("me@astermail.org"),
        timestamp = 1_790_000_000_000L,
        body = body,
        is_encrypted = false,
        item_type = item_type,
        spf_result = results.first,
        dkim_result = results.second,
        dmarc_result = results.third,
        is_external = true,
        display_sender_name = display_sender_name,
        display_sender_email = display_sender_email,
    )

    private val messages = listOf(
        message(
            "acme", "Acme Store", "orders@acme-store.example",
            Triple("pass", "pass", "pass"),
            "Hi, your order #4821 left our warehouse today and should arrive tomorrow.",
        ),
        message(
            "digest", "Weekly Digest", "digest@news-example.example",
            Triple("pass", "softfail", "none"),
            "This week: three new articles and an invitation to our next meetup.",
        ),
        message(
            "club", "Riverside Book Club", "hello@riverside-club.example",
            Triple("none", "none", "none"),
            "We meet on October 15. The agenda and reading list are attached.",
        ),
        message(
            "partner", "forward", "forward@alias-service.example",
            Triple("pass", "pass", "pass"),
            "Received through your forwarding alias.",
            display_sender_name = "Partner Shop",
            display_sender_email = "deals@partner-shop.example",
        ),
        message(
            "bank", "Example Bank", "security@example-bank.example",
            Triple("fail", "none", "fail"),
            "We detected an unusual sign-in. Click here to confirm your password.",
        ),
    )

    private fun show(list: List<ThreadMessage>, dark: Boolean) {
        val activity = compose_rule.activity
        val bodies_ready = AtomicInteger(0)
        activity.runOnUiThread {
            val view = ComposeView(activity)
            view.setContent { thread(list, dark, on_body_ready = { bodies_ready.incrementAndGet() }) }
            activity.setContentView(view)
            allow_screenshots()
        }
        val deadline = System.currentTimeMillis() + 60_000
        while (bodies_ready.get() < list.size && System.currentTimeMillis() < deadline) {
            compose_rule.waitForIdle()
            Thread.sleep(200)
        }
        repeat(10) {
            compose_rule.waitForIdle()
            Thread.sleep(200)
        }
    }

    @androidx.compose.runtime.Composable
    private fun thread(list: List<ThreadMessage>, dark: Boolean, on_body_ready: () -> Unit) {
        AsterTheme(use_dark_theme = dark) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AsterMaterial.colors.bg_primary)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                list.forEachIndexed { index, msg ->
                    expanded_message(
                        msg = msg,
                        is_last = index == list.lastIndex,
                        on_collapse = {},
                        on_reply = {},
                        on_reply_all = {},
                        on_forward = {},
                        on_more = {},
                        is_first_card = true,
                        is_last_card = true,
                        message_index = index,
                        on_body_ready = on_body_ready,
                    )
                }
            }
        }
    }

    // The app blocks screenshots until an app lock is set up; the dialogs
    // inherit the flag from the activity window when they open.
    private fun allow_screenshots() {
        compose_rule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun save_screen(name: String) {
        compose_rule.activity.runOnUiThread { allow_screenshots() }
        compose_rule.waitForIdle()
        Thread.sleep(600)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: instrumentation.targetContext.getExternalFilesDir(null)?.absolutePath
                ?: return,
        )
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    // Only the inconclusive (Weekly Digest) and failed (Example Bank) messages
    // get a badge; mail that passed, lacks checks or came through a forwarding
    // alias gets none.
    private val with_badge = listOf("digest", "bank")

    private fun capture_all(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        show(messages, dark)
        compose_rule.onAllNodesWithTag("email_auth_badge").assertCountEquals(with_badge.size)
        val label = compose_rule.activity.getString(
            R.string.email_auth_label,
            compose_rule.activity.getString(R.string.email_auth_partial),
        )
        compose_rule.onAllNodesWithTag("email_auth_badge")[0]
            .assertContentDescriptionEquals(label)
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        save_screen("email_auth_badges_$theme")
        with_badge.forEachIndexed { index, id ->
            compose_rule.onAllNodesWithTag("email_auth_badge")[index].performScrollTo().performClick()
            compose_rule.onNodeWithTag("email_auth_check_spf").assertExists()
            compose_rule.onNodeWithTag("email_auth_check_dkim").assertExists()
            compose_rule.onNodeWithTag("email_auth_check_dmarc").assertExists()
            save_screen("email_auth_dialog_${id}_$theme")
            compose_rule.onNodeWithText(compose_rule.activity.getString(R.string.done)).performClick()
            compose_rule.waitForIdle()
            compose_rule.onNodeWithTag("email_auth_checks").assertDoesNotExist()
        }
    }

    @Test
    fun shows_a_badge_only_for_failed_or_inconclusive_checks_light() {
        capture_all(dark = false)
    }

    @Test
    fun shows_a_badge_only_for_failed_or_inconclusive_checks_dark() {
        capture_all(dark = true)
    }

    // Mail without a badge still lists its checks in the security details.
    @Test
    fun security_details_list_the_checks_of_a_message_without_a_badge() {
        show(messages.take(1), dark = false)
        compose_rule.onAllNodesWithTag("email_auth_badge").assertCountEquals(0)
        val activity = compose_rule.activity
        compose_rule.onNodeWithText(activity.getString(R.string.to_label_prefix, "me")).performClick()
        compose_rule.onNodeWithText(activity.getString(R.string.view_encryption_details))
            .performScrollTo()
            .performClick()
        val passed = activity.getString(R.string.auth_result_pass)
        compose_rule.onNodeWithText(
            activity.getString(R.string.auth_summary_format, passed, passed, passed),
        ).assertExists()
        save_screen("email_auth_security_details_acme_light")
    }

    @Test
    fun sent_messages_and_messages_without_results_have_no_badge() {
        show(
            listOf(
                message("sent", "Me", "me@astermail.org", Triple("fail", "none", "fail"), "Sent.", item_type = "sent"),
                message("none", "Shop", "news@shop.example", Triple(null, null, null), "No results."),
            ),
            dark = false,
        )
        compose_rule.onNodeWithTag("message_header_0").assertExists()
        compose_rule.onNodeWithTag("message_header_1").assertExists()
        compose_rule.onAllNodesWithTag("email_auth_badge").assertCountEquals(0)
    }
}
