package org.astermail.android.ui.mail

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.mail.MimeParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PgpMessageScreenTest {

    @get:Rule
    val compose_rule = createAndroidComposeRule<org.astermail.android.MainActivity>()

    private val decrypted_pgp_payload =
        "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Type: text/plain; charset=UTF-8; format=flowed\r\n" +
            "\r\n" +
            "This is a test email sent from sourcehut to confirm that PGP is working as =\r\n" +
            "you \r\n" +
            "expect. This email is signed with this key:\r\n" +
            "\r\n" +
            "447B 69E4 B34B E90B C829 A0E9 6597 04D1 A38A 93AE\r\n" +
            "\r\n" +
            "You may update your PGP settings here:\r\n" +
            "\r\n" +
            "https://meta.sr.ht/privacy\r\n" +
            "\r\n" +
            "--\r\n" +
            "Drew DeVault\r\n" +
            "sourcehut\r\n"

    private fun resolved_body(raw: String): String {
        if (!MimeParser.looks_like_mime(raw)) return raw
        val parsed = MimeParser.parse(raw)
        return parsed.text ?: raw
    }

    private fun message(body: String) = ThreadMessage(
        id = "pgp-1",
        sender_name = "Drew DeVault",
        sender_email = "sir@cmpwn.com",
        to_label = "me",
        to_addresses = listOf("lewisw@astermail.org"),
        timestamp = 1_754_700_000_000L,
        body = body,
        body_html = null,
        is_encrypted = false,
        is_read = true,
        item_type = "received",
        spf_result = "pass",
        dkim_result = "pass",
        dmarc_result = "pass",
        is_external = true,
    )

    private fun capture(name: String, body: String, dark: Boolean) {
        val body_ready = java.util.concurrent.atomic.AtomicBoolean(false)
        val activity = compose_rule.activity
        var host: androidx.compose.ui.platform.ComposeView? = null
        activity.runOnUiThread {
            val view = androidx.compose.ui.platform.ComposeView(activity)
            host = view
            view.setContent {
                AsterTheme(use_dark_theme = dark) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AsterMaterial.colors.bg_primary),
                    ) {
                        expanded_message(
                            msg = message(body),
                            is_last = true,
                            on_body_ready = { body_ready.set(true) },
                            on_collapse = {},
                            on_reply = {},
                            on_reply_all = {},
                            on_forward = {},
                            on_more = {},
                        )
                    }
                }
            }
            activity.setContentView(view)
        }
        val deadline = System.currentTimeMillis() + 20_000
        while (!body_ready.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
        }
        Thread.sleep(3000)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        if (host == null) return
        val dir = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: instrumentation.targetContext.getExternalFilesDir(null)!!.absolutePath,
        )
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun message_screen_before_fix_light() {
        capture("app_pgp_before_light", decrypted_pgp_payload, dark = false)
    }

    @Test
    fun message_screen_after_fix_light() {
        capture("app_pgp_after_light", resolved_body(decrypted_pgp_payload), dark = false)
    }

    @Test
    fun message_screen_after_fix_dark() {
        capture("app_pgp_after_dark", resolved_body(decrypted_pgp_payload), dark = true)
    }
}
