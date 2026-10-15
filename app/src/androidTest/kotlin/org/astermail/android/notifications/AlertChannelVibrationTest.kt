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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.billing.PaymentFailedNotifier
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertChannelVibrationTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private var saved_sound = true
    private var saved_vibrate = true

    @Before
    fun set_up() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        saved_sound = MailPollingWorker.is_sound_enabled(context)
        saved_vibrate = MailPollingWorker.is_vibrate_enabled(context)
        remove_all_alert_channels()
    }

    @After
    fun tear_down() {
        remove_all_alert_channels()
        MailPollingWorker.set_notification_alerts(context, saved_sound, saved_vibrate)
        LoginAlertNotifier.create_channel(context)
    }

    @Test
    fun an_old_mail_channel_without_vibration_is_replaced_by_one_that_vibrates() {
        create_legacy("aster_new_mail", NotificationManager.IMPORTANCE_DEFAULT)
        assertFalse(manager.getNotificationChannel("aster_new_mail").shouldVibrate())

        MailPollingWorker.set_notification_alerts(context, sound = true, vibrate = true)

        val channel = manager.getNotificationChannel(MailPollingWorker.CHANNEL_ID)
        assertNotNull(channel)
        log(channel)
        assertTrue(channel.shouldVibrate())
        assertArrayEquals(ALERT_VIBRATION_PATTERN, channel.vibrationPattern)
        assertNotNull(channel.sound)
        assertNull(manager.getNotificationChannel("aster_new_mail"))
    }

    @Test
    fun the_upgrade_keeps_an_importance_the_user_lowered() {
        val suffix = System.nanoTime()
        val legacy_id = "test_legacy_$suffix"
        val current_id = "test_current_$suffix"
        create_legacy(legacy_id, NotificationManager.IMPORTANCE_LOW)

        create_alert_channel(
            manager,
            NotificationChannel(current_id, current_id, NotificationManager.IMPORTANCE_DEFAULT),
            vibrate = true,
            replaced_ids = listOf(legacy_id),
        )

        val channel = manager.getNotificationChannel(current_id)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertTrue(channel.shouldVibrate())
        assertNull(manager.getNotificationChannel(legacy_id))
        manager.deleteNotificationChannel(current_id)
    }

    @Test
    fun every_old_mail_channel_is_removed() {
        for (id in MailPollingWorker.LEGACY_CHANNEL_IDS) {
            create_legacy(id, NotificationManager.IMPORTANCE_DEFAULT)
        }

        MailPollingWorker.set_notification_alerts(context, sound = true, vibrate = true)

        val remaining = manager.notificationChannels.map { it.id }
        for (id in MailPollingWorker.LEGACY_CHANNEL_IDS) {
            assertFalse(id in remaining)
        }
        assertEquals(1, remaining.count { it in MailPollingWorker.alert_channel_ids() })
    }

    @Test
    fun the_vibrate_setting_controls_the_active_channel() {
        MailPollingWorker.set_notification_alerts(context, sound = true, vibrate = false)
        assertFalse(manager.getNotificationChannel(MailPollingWorker.CHANNEL_ID_SOUND_ONLY).shouldVibrate())

        MailPollingWorker.set_notification_alerts(context, sound = false, vibrate = true)
        val vibrate_only = manager.getNotificationChannel(MailPollingWorker.CHANNEL_ID_VIBRATE_ONLY)
        log(vibrate_only)
        assertTrue(vibrate_only.shouldVibrate())
        assertNull(vibrate_only.sound)

        MailPollingWorker.set_notification_alerts(context, sound = true, vibrate = true)
        val both = manager.getNotificationChannel(MailPollingWorker.CHANNEL_ID)
        assertTrue(both.shouldVibrate())
        assertNotNull(both.sound)
        assertNull(manager.getNotificationChannel(MailPollingWorker.CHANNEL_ID_VIBRATE_ONLY))
    }

    @Test
    fun sign_in_and_billing_alerts_vibrate_after_the_upgrade() {
        create_legacy("aster_login_alerts", NotificationManager.IMPORTANCE_HIGH)
        create_legacy("aster_billing", NotificationManager.IMPORTANCE_HIGH)

        LoginAlertNotifier.create_channel(context)
        PaymentFailedNotifier.create_channel(context)

        for (id in listOf(LoginAlertNotifier.CHANNEL_ID, PaymentFailedNotifier.CHANNEL_ID)) {
            val channel = manager.getNotificationChannel(id)
            log(channel)
            assertTrue(channel.shouldVibrate())
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        }
        assertNull(manager.getNotificationChannel("aster_login_alerts"))
        assertNull(manager.getNotificationChannel("aster_billing"))
    }

    @Test
    fun a_new_mail_notification_from_an_old_install_posts_to_the_vibrating_channel() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName, "android.permission.POST_NOTIFICATIONS",
        )
        create_legacy("aster_new_mail", NotificationManager.IMPORTANCE_DEFAULT)
        MailPollingWorker.set_notification_alerts(context, sound = true, vibrate = true)
        val id = MailPollingWorker.message_notification_id("vibration-check".hashCode())

        MailPollingWorker.show_message(context, "Sender", "Subject", "Preview", id, "vibration-check")
        Thread.sleep(1500)

        val posted = manager.activeNotifications.firstOrNull { it.id == id }
        assertNotNull(posted)
        assertEquals(MailPollingWorker.CHANNEL_ID, posted!!.notification.channelId)
        assertTrue(manager.getNotificationChannel(posted.notification.channelId).shouldVibrate())
        val vibrations = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys vibrator_manager")
            .let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
            .lines()
            .filter { "NOTIFICATION" in it && "250" in it }
        for (line in vibrations.takeLast(3)) {
            Log.i("AlertChannelVibration", "vibrator: ${line.trim().take(300)}")
        }
        manager.cancel(id)
        manager.cancel(MailPollingWorker.SUMMARY_NOTIFICATION_ID)
    }

    private fun create_legacy(id: String, importance: Int) {
        val channel = NotificationChannel(id, id, importance)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }

    private fun remove_all_alert_channels() {
        val ids = MailPollingWorker.alert_channel_ids() + MailPollingWorker.LEGACY_CHANNEL_IDS + listOf(
            LoginAlertNotifier.CHANNEL_ID,
            "aster_login_alerts",
            PaymentFailedNotifier.CHANNEL_ID,
            "aster_billing",
        )
        for (id in ids) {
            manager.deleteNotificationChannel(id)
        }
    }

    private fun log(channel: NotificationChannel) {
        Log.i(
            "AlertChannelVibration",
            "${channel.id} vibrate=${channel.shouldVibrate()} " +
                "pattern=${channel.vibrationPattern?.toList()} importance=${channel.importance} sound=${channel.sound}",
        )
    }
}
