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

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class UnsubscribeLinkTest {

    @get:Rule
    val compose_rule = createAndroidComposeRule<org.astermail.android.MainActivity>()

    private val list_unsubscribe = "<https://news.example/unsub?u=8f2c>, <mailto:leave@news.example>"

    private val newsletter_html = """
        <div style="font-family:sans-serif;max-width:600px;margin:0 auto">
          <img src="https://cdn.news.example/header.png" width="600" height="160" alt="Notícias da Semana">
          <h2>As notícias desta semana</h2>
          <p>Três artigos novos, uma entrevista e o calendário de eventos de outubro.</p>
          <img src="https://cdn.news.example/article-1.jpg" width="600" height="300" alt="">
          <p>Leia a entrevista completa no nosso site.</p>
          <img src="https://cdn.news.example/article-2.jpg" width="600" height="300" alt="">
          <p style="font-size:12px;color:#777">Recebe este e-mail porque subscreveu a newsletter.
          <a href="https://news.example/preferences">Gerir preferências</a></p>
          <img src="https://track.news.example/open.gif" width="1" height="1" alt="">
        </div>
    """.trimIndent()

    private fun newsletter(
        id: String = "news",
        with_header: Boolean = true,
        with_body: Boolean = with_header,
    ) = ThreadMessage(
        id = id,
        sender_name = "Notícias da Semana",
        sender_email = "newsletter@news.example",
        to_label = "diogo@santos.cc",
        to_addresses = listOf("diogo@santos.cc"),
        timestamp = 1_791_300_000_000L,
        body = "As notícias desta semana",
        body_html = if (with_body) newsletter_html else "<p>Olá, até sexta.</p>",
        is_encrypted = false,
        is_external = true,
        spf_result = "pass",
        dkim_result = "pass",
        dmarc_result = "pass",
        raw_headers = if (with_header) {
            listOf(
                "List-Unsubscribe" to list_unsubscribe,
                "List-Unsubscribe-Post" to "List-Unsubscribe=One-Click",
            )
        } else {
            emptyList()
        },
    )

    private val unsubscribe_calls = mutableListOf<UnsubscribeInfo>()

    private fun show(
        list: List<ThreadMessage>,
        dark: Boolean = false,
        tag: String = "pt-PT",
        wait_for_bodies: Boolean = false,
    ) {
        val activity = compose_rule.activity
        val bodies_ready = AtomicInteger(0)
        activity.runOnUiThread {
            val view = ComposeView(activity)
            view.setContent {
                localized(tag) {
                    thread(list, dark, on_body_ready = { bodies_ready.incrementAndGet() })
                }
            }
            activity.setContentView(view)
            allow_screenshots()
        }
        if (wait_for_bodies) {
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
        compose_rule.waitForIdle()
    }

    @Composable
    private fun localized(tag: String, content: @Composable () -> Unit) {
        val activity = compose_rule.activity
        val config = remember(tag) {
            Configuration(activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        }
        val context = remember(config) {
            ContextThemeWrapper(activity, activity.theme).apply { applyOverrideConfiguration(config) }
        }
        CompositionLocalProvider(
            LocalContext provides context,
            LocalResources provides context.resources,
            LocalConfiguration provides config,
            content = content,
        )
    }

    @Composable
    private fun thread(list: List<ThreadMessage>, dark: Boolean, on_body_ready: () -> Unit) {
        var dismissed by remember { mutableStateOf(emptySet<String>()) }
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
                        message_index = index,
                        my_email = "diogo@santos.cc",
                        show_unsub = msg.id !in dismissed,
                        on_unsubscribe = { info ->
                            unsubscribe_calls += info
                            dismissed = dismissed + msg.id
                        },
                        on_body_ready = on_body_ready,
                    )
                }
            }
        }
    }

    private fun localized_string(tag: String, res: Int): String {
        val config = Configuration(compose_rule.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return compose_rule.activity.createConfigurationContext(config).getString(res)
    }

    @Test
    fun link_sits_in_the_sender_row_as_an_accessible_button() {
        show(listOf(newsletter()))
        val label = localized_string("pt-PT", R.string.unsubscribe)
        assertEquals("Cancelar inscrição", label)
        compose_rule.onNodeWithTag("unsubscribe_link")
            .assertContentDescriptionEquals(label)
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val link = compose_rule.onNodeWithTag("unsubscribe_link").fetchSemanticsNode()
        val header = compose_rule.onNodeWithTag("message_header_0").fetchSemanticsNode()
        assertTrue("link should be inside the sender row", header.boundsInRoot.contains(link.boundsInRoot.center))
        val min_px = with(compose_rule.density) { 48.dp.toPx() } - 1f
        assertTrue("touch height ${link.touchBoundsInRoot.height}", link.touchBoundsInRoot.height >= min_px)
        assertTrue("touch width ${link.touchBoundsInRoot.width}", link.touchBoundsInRoot.width >= min_px)
        assertTrue("link should stay visually small", link.boundsInRoot.height < min_px)
    }

    @Test
    fun there_is_no_unsubscribe_card() {
        show(listOf(newsletter()))
        val sentence = localized_string("pt-PT", R.string.detail_unsubscribe_title)
        compose_rule.onAllNodesWithText(sentence).assertCountEquals(0)
        compose_rule.onAllNodesWithTag("compact_banner_label").assertCountEquals(1)
    }

    @Test
    fun tapping_the_link_runs_the_existing_unsubscribe_action_and_hides_it() {
        show(listOf(newsletter()))
        compose_rule.onNodeWithTag("unsubscribe_link").performClick()
        compose_rule.waitForIdle()
        assertEquals(1, unsubscribe_calls.size)
        val info = unsubscribe_calls.single()
        assertEquals("one-click", info.method)
        assertEquals("https://news.example/unsub?u=8f2c", info.unsubscribe_link)
        assertEquals(list_unsubscribe, info.list_unsubscribe_header)
        compose_rule.onAllNodesWithTag("unsubscribe_link").assertCountEquals(0)
    }

    @Test
    fun link_does_not_make_the_sender_row_taller() {
        show(listOf(newsletter(with_header = false, with_body = true)))
        compose_rule.onAllNodesWithTag("unsubscribe_link").assertCountEquals(0)
        val without = compose_rule.onNodeWithTag("message_header_0").fetchSemanticsNode().boundsInRoot.height
        show(listOf(newsletter()))
        compose_rule.onAllNodesWithTag("unsubscribe_link").assertCountEquals(1)
        val with_link = compose_rule.onNodeWithTag("message_header_0").fetchSemanticsNode().boundsInRoot.height
        assertEquals(without, with_link, 1f)
    }

    @Test
    fun tapping_the_recipient_line_still_opens_the_details() {
        show(listOf(newsletter()))
        val recipient = localized_string("pt-PT", R.string.to_label_prefix).replace("%s", "diogo@santos.cc")
        compose_rule.onNodeWithText(recipient).performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText(localized_string("pt-PT", R.string.view_security_details)).assertExists()
        assertEquals(0, unsubscribe_calls.size)
    }

    @Test
    fun a_tap_just_below_the_text_still_hits_the_link() {
        show(listOf(newsletter()))
        val link = compose_rule.onNodeWithTag("unsubscribe_link").fetchSemanticsNode()
        val below = with(compose_rule.density) { 10.dp.toPx() }
        compose_rule.onNodeWithTag("unsubscribe_link").performTouchInput {
            click(Offset(width / 2f, height + below))
        }
        compose_rule.waitForIdle()
        assertEquals("tap ${below}px below ${link.boundsInRoot}", 1, unsubscribe_calls.size)
    }

    @Test
    fun hidden_when_the_message_has_no_unsubscribe_option() {
        show(listOf(newsletter(with_header = false)))
        compose_rule.onNodeWithTag("message_header_0").fetchSemanticsNode()
        compose_rule.onAllNodesWithTag("unsubscribe_link").assertCountEquals(0)
    }

    @Test
    fun thread_shows_the_link_only_on_messages_that_carry_it() {
        show(listOf(newsletter(id = "reply", with_header = false), newsletter(id = "news")))
        compose_rule.onAllNodesWithTag("unsubscribe_link").assertCountEquals(1)
        val link = compose_rule.onNodeWithTag("unsubscribe_link").fetchSemanticsNode()
        val second = compose_rule.onNodeWithTag("message_header_1").fetchSemanticsNode()
        assertTrue(second.boundsInRoot.contains(link.boundsInRoot.center))
    }

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

    private fun capture(dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        show(listOf(newsletter()), dark = !dark, wait_for_bodies = true)
        show(listOf(newsletter()), dark = dark, wait_for_bodies = true)
        repeat(60) {
            compose_rule.waitForIdle()
            Thread.sleep(250)
        }
        compose_rule.onAllNodesWithTag("unsubscribe_link").assertCountEquals(1)
        save_screen("unsubscribe_link_pt_$theme")
        compose_rule.onNodeWithTag("unsubscribe_link").performClick()
        compose_rule.waitForIdle()
        Thread.sleep(800)
        compose_rule.onAllNodesWithTag("unsubscribe_link").assertCountEquals(0)
        save_screen("unsubscribe_link_pt_${theme}_after")
    }

    @Test
    fun capture_light() {
        capture(dark = false)
    }

    @Test
    fun capture_dark() {
        capture(dark = true)
    }
}
