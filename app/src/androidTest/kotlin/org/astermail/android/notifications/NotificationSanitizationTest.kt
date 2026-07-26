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

package org.astermail.android.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.security.LockdownStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationSanitizationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val forbidden = listOf(
        "ASTER_BUNDLE_V2",
        "ASTER_RATCHET_UNDECRYPTABLE",
        "double_ratchet",
        "-----BEGIN PGP",
        "\"s\":\"",
        "\"b\":\"",
        "<p",
        "<div",
        "</",
        "&nbsp;",
    )

    @Before
    fun setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, "android.permission.POST_NOTIFICATIONS",
            )
        }
        MailPollingWorker.set_push_enabled(context, false)
        MailPollingWorker.set_notify_new_email(context, true)
        MailPollingWorker.set_muted_folder_tokens(context, emptyList())
        LockdownStore.set_enabled(context, false)
        MailPollingWorker.create_channel(context)
        manager.cancelAll()
        await_empty()
    }

    @After
    fun teardown() {
        manager.cancelAll()
    }

    private fun await_empty() {
        repeat(50) {
            if (manager.activeNotifications.isEmpty()) return
            Thread.sleep(100)
        }
    }

    private fun await_notification(id: Int): Notification? {
        repeat(50) {
            manager.activeNotifications.firstOrNull { it.id == id }?.let { return it.notification }
            Thread.sleep(100)
        }
        return null
    }

    private fun visible_strings(notification: Notification): List<String> {
        val out = mutableListOf<String>()
        fun collect(target: Notification?) {
            if (target == null) return
            val extras = target.extras ?: return
            listOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_SUMMARY_TEXT,
                Notification.EXTRA_INFO_TEXT,
            ).forEach { key -> extras.getCharSequence(key)?.let { out.add(it.toString()) } }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { out.add(it.toString()) }
            target.tickerText?.let { out.add(it.toString()) }
            collect(target.publicVersion)
        }
        collect(notification)
        return out
    }

    private fun assert_clean(notification: Notification, label: String) {
        val strings = visible_strings(notification)
        assertTrue("$label posted nothing renderable", strings.isNotEmpty())
        strings.forEach { rendered ->
            forbidden.forEach { marker ->
                assertFalse(
                    "$label leaked \"$marker\" in notification text: $rendered",
                    rendered.contains(marker),
                )
            }
        }
    }

    private fun assert_nothing_active_leaks(label: String) {
        manager.activeNotifications.forEach { active ->
            visible_strings(active.notification).forEach { rendered ->
                forbidden.forEach { marker ->
                    assertFalse(
                        "$label leaked \"$marker\" in a posted notification: $rendered",
                        rendered.contains(marker),
                    )
                }
            }
        }
    }

    private fun envelope_payload(item_id: String, subject: String, body_text: String): String {
        val envelope = org.json.JSONObject()
            .put("subject", subject)
            .put("from", "PM <pm@astermail.org>")
            .put("body_text", body_text)
            .toString()
        val encoded = android.util.Base64.encodeToString(
            envelope.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP,
        )
        return org.json.JSONObject()
            .put("type", "new_mail")
            .put("item_id", item_id)
            .put("encrypted_envelope", encoded)
            .toString()
    }

    @Test
    fun internal_bundle_push_renders_decoded_subject_and_body() {
        val item_id = "bundle-ok-" + System.nanoTime()
        val body = "ASTER_BUNDLE_V2{\"s\":\"Pages update\",\"b\":\"<p>Here is the doc you asked for</p>\"}"
        val result = handle_push_payload(context, envelope_payload(item_id, "", body))

        assertEquals(PushResult.Shown, result)
        val notification = await_notification(MailPollingWorker.message_notification_id(item_id.hashCode()))
        assertTrue("internal bundle push must post a notification", notification != null)
        assert_clean(notification!!, "internal bundle push")

        val extras = notification.extras
        assertEquals("PM", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("Pages update", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()
        assertTrue(
            "decoded body must be the preview line, got $lines",
            lines.contains("Here is the doc you asked for"),
        )
    }

    @Test
    fun unextractable_bundle_push_renders_subject_without_framing_preview() {
        val item_id = "bundle-unextractable-" + System.nanoTime()
        val body = "Fwd: ASTER_BUNDLE_V2{\"s\":\"pages\",\"b\":\"<p>secret body</p>\"}"
        val result = handle_push_payload(context, envelope_payload(item_id, "Invoice", body))

        assertEquals(PushResult.Shown, result)
        val notification = await_notification(MailPollingWorker.message_notification_id(item_id.hashCode()))
        assertTrue("push with a real subject must post", notification != null)
        assert_clean(notification!!, "unextractable bundle push")
        assertEquals("Invoice", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertTrue("no preview line when the body is still framed", lines == null || lines.isEmpty())
    }

    @Test
    fun bundle_fragment_push_renders_subject_without_framing_preview() {
        val item_id = "bundle-fragment-" + System.nanoTime()
        val body = "ASTER_BUNDLE_V2{\"s\":\"pages"
        val result = handle_push_payload(context, envelope_payload(item_id, "Invoice", body))

        assertEquals(PushResult.Shown, result)
        val notification = await_notification(MailPollingWorker.message_notification_id(item_id.hashCode()))
        assertTrue("push with a real subject must post", notification != null)
        assert_clean(notification!!, "bundle fragment push")
        assertEquals("Invoice", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertTrue("no preview line for a bundle fragment", lines == null || lines.isEmpty())
    }

    @Test
    fun truncated_bundle_push_never_renders_framing() {
        val item_id = "bundle-truncated-" + System.nanoTime()
        val body = "ASTER_BUNDLE_V2{\"s\":\"pages"
        val result = handle_push_payload(context, envelope_payload(item_id, "", body))

        assertFalse("unparseable bundle must not be rendered as mail", result == PushResult.Shown)
        Thread.sleep(500)
        assert_nothing_active_leaks("truncated bundle push")
    }

    @Test
    fun ratchet_ciphertext_push_never_renders_framing() {
        val item_id = "ratchet-" + System.nanoTime()
        val body = "{\"v\":\"double_ratchet_v2\",\"ephemeral_key\":\"AAAA\",\"ciphertext\":\"BBBB\"}"
        val result = handle_push_payload(context, envelope_payload(item_id, "", body))

        assertFalse("ratchet ciphertext must not be rendered as mail", result == PushResult.Shown)
        Thread.sleep(500)
        assert_nothing_active_leaks("ratchet ciphertext push")
    }

    @Test
    fun show_message_sanitizes_reported_bundle_preview() {
        val id = MailPollingWorker.message_notification_id("screenshot".hashCode())
        MailPollingWorker.show_message(
            context = context,
            sender = "PM",
            subject = "",
            preview = "ASTER_BUNDLE_V2{\"s\":\"pages\",\"b\":\"<p>secret body</p>\"}",
            message_id = id,
        )

        val notification = await_notification(id)
        assertTrue("show_message must post", notification != null)
        assert_clean(notification!!, "reported bundle preview")
        assertEquals("PM", notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(
            context.getString(R.string.notif_new_message),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertTrue("no preview line when the body is ciphertext", lines == null || lines.isEmpty())
    }

    @Test
    fun show_message_strips_raw_html_preview() {
        val id = MailPollingWorker.message_notification_id("rawhtml".hashCode())
        MailPollingWorker.show_message(
            context = context,
            sender = "Alice <alice@example.com>",
            subject = "Lunch",
            preview = "<div><p>Hello&nbsp;<b>there</b></p></div>",
            message_id = id,
        )

        val notification = await_notification(id)
        assertTrue("show_message must post", notification != null)
        assert_clean(notification!!, "raw html preview")
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()
        assertTrue("html must be stripped to text, got $lines", lines.contains("Hello there"))
    }

    @Test
    fun show_message_sanitizes_poisoned_sender_and_subject() {
        val id = MailPollingWorker.message_notification_id("poisoned".hashCode())
        MailPollingWorker.show_message(
            context = context,
            sender = "ASTER_BUNDLE_V2{\"s\":\"x\",\"b\":\"y\"}",
            subject = "{\"v\":\"double_ratchet_v1\",\"ciphertext\":\"zz\"}",
            preview = " ASTER_RATCHET_UNDECRYPTABLE ",
            message_id = id,
        )

        val notification = await_notification(id)
        assertTrue("show_message must post", notification != null)
        assert_clean(notification!!, "poisoned sender and subject")
        assertEquals(
            context.getString(R.string.app_name),
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        )
        assertEquals(
            context.getString(R.string.notif_new_message),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
    }

    @Test
    fun pgp_armor_preview_is_never_rendered() {
        val id = MailPollingWorker.message_notification_id("pgp".hashCode())
        MailPollingWorker.show_message(
            context = context,
            sender = "Bob",
            subject = "Encrypted",
            preview = "-----BEGIN PGP MESSAGE-----\nhQIMA1234\n-----END PGP MESSAGE-----",
            message_id = id,
        )

        val notification = await_notification(id)
        assertTrue("show_message must post", notification != null)
        assert_clean(notification!!, "pgp armor preview")
        val lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        assertTrue("no preview line for pgp armor", lines == null || lines.isEmpty())
    }
}
