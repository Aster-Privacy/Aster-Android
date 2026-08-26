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

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import io.mockk.every
import io.mockk.mockk
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class NotificationReliabilityTest {

    private val stored = linkedMapOf<String, String>()

    @Before
    fun set_up() {
        stored.clear()
    }

    private fun fake_prefs(): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } answers { stored[firstArg()] ?: secondArg() }
        every { prefs.edit() } answers {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            val pending = linkedMapOf<String, String>()
            every { editor.putString(any(), any()) } answers {
                pending[firstArg()] = secondArg()
                editor
            }
            every { editor.commit() } answers {
                stored.putAll(pending)
                true
            }
            editor
        }
        return prefs
    }

    private fun fake_context(): Context {
        val context = mockk<Context>(relaxed = true)
        val prefs = fake_prefs()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    @Test
    fun a_failed_notification_action_is_never_reported_as_done() {
        for (attempt in 0 until MailNotificationActionWorker.MAX_ATTEMPTS + 3) {
            assertNotEquals(
                MailActionAttempt.Done,
                MailNotificationActionWorker.attempt_result(false, attempt),
            )
        }
    }

    @Test
    fun notification_action_retries_are_bounded() {
        assertEquals(
            MailActionAttempt.Retry,
            MailNotificationActionWorker.attempt_result(false, 0),
        )
        assertEquals(
            MailActionAttempt.Retry,
            MailNotificationActionWorker.attempt_result(false, MailNotificationActionWorker.MAX_ATTEMPTS - 2),
        )
        assertEquals(
            MailActionAttempt.GiveUp,
            MailNotificationActionWorker.attempt_result(false, MailNotificationActionWorker.MAX_ATTEMPTS - 1),
        )
        assertEquals(
            MailActionAttempt.GiveUp,
            MailNotificationActionWorker.attempt_result(false, MailNotificationActionWorker.MAX_ATTEMPTS + 5),
        )
    }

    @Test
    fun a_successful_notification_action_is_done_on_every_attempt() {
        assertEquals(MailActionAttempt.Done, MailNotificationActionWorker.attempt_result(true, 0))
        assertEquals(
            MailActionAttempt.Done,
            MailNotificationActionWorker.attempt_result(true, MailNotificationActionWorker.MAX_ATTEMPTS + 5),
        )
    }

    @Test
    fun every_notification_action_has_its_own_failure_title() {
        val archive = MailPollingWorker.action_failed_title_res(MailNotificationActionWorker.ACTION_ARCHIVE)
        val trash = MailPollingWorker.action_failed_title_res(MailNotificationActionWorker.ACTION_TRASH)
        val mark_read = MailPollingWorker.action_failed_title_res(MailNotificationActionWorker.ACTION_MARK_READ)
        assertEquals(R.string.failed_to_archive, archive)
        assertEquals(R.string.failed_to_trash, trash)
        assertEquals(R.string.failed_mark_read, mark_read)
        assertEquals(3, setOf(archive, trash, mark_read).size)
        assertNull(MailPollingWorker.action_failed_title_res("snooze"))
        assertNull(MailPollingWorker.action_failed_title_res(""))
    }

    @Test
    fun a_released_claim_can_be_claimed_again() {
        val context = fake_context()
        assertTrue(MailPollingWorker.claim_item_notification(context, "item_a"))
        assertFalse(MailPollingWorker.claim_item_notification(context, "item_a"))
        MailPollingWorker.release_item_notification(context, "item_a")
        assertFalse(MailPollingWorker.was_item_notified(context, "item_a"))
        assertTrue(MailPollingWorker.claim_item_notification(context, "item_a"))
    }

    @Test
    fun releasing_one_claim_keeps_the_others() {
        val context = fake_context()
        assertTrue(MailPollingWorker.claim_item_notification(context, "item_a"))
        assertTrue(MailPollingWorker.claim_item_notification(context, "item_b"))
        MailPollingWorker.release_item_notification(context, "item_a")
        assertTrue(MailPollingWorker.was_item_notified(context, "item_b"))
        assertFalse(MailPollingWorker.was_item_notified(context, "item_a"))
    }

    @Test
    fun releasing_a_blank_item_is_a_no_op() {
        val context = fake_context()
        assertTrue(MailPollingWorker.claim_item_notification(context, "item_a"))
        MailPollingWorker.release_item_notification(context, "")
        assertTrue(MailPollingWorker.was_item_notified(context, "item_a"))
    }

    @Test
    fun back_to_back_pushes_do_not_cancel_each_other() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, MailPollingWorker.FORCED_NOTIFY_POLICY)
        assertNotEquals(ExistingWorkPolicy.REPLACE, MailPollingWorker.FORCED_NOTIFY_POLICY)
    }

    @Test
    fun an_unverified_session_never_raises_a_login_alert() {
        assertEquals(LoginAlertDecision.Retry, LoginAlertNotifier.alert_decision(null, 0))
        assertEquals(
            LoginAlertDecision.Retry,
            LoginAlertNotifier.alert_decision(null, LoginAlertNotifier.ALERT_MAX_ATTEMPTS - 2),
        )
        assertEquals(
            LoginAlertDecision.GiveUp,
            LoginAlertNotifier.alert_decision(null, LoginAlertNotifier.ALERT_MAX_ATTEMPTS - 1),
        )
        assertEquals(
            LoginAlertDecision.GiveUp,
            LoginAlertNotifier.alert_decision(null, LoginAlertNotifier.ALERT_MAX_ATTEMPTS + 4),
        )
    }

    @Test
    fun a_verified_foreign_session_raises_a_login_alert() {
        assertEquals(LoginAlertDecision.Show, LoginAlertNotifier.alert_decision(false, 0))
        assertEquals(LoginAlertDecision.Skip, LoginAlertNotifier.alert_decision(true, 0))
        assertEquals(
            LoginAlertDecision.Show,
            LoginAlertNotifier.alert_decision(false, LoginAlertNotifier.ALERT_MAX_ATTEMPTS + 4),
        )
    }

    @Test
    fun a_revoked_or_missing_session_counts_as_revoked() {
        assertEquals(RevokeDecision.Revoked, LoginAlertNotifier.revoke_decision(null, 0))
        assertEquals(
            RevokeDecision.Revoked,
            LoginAlertNotifier.revoke_decision(ApiError.NotFoundError, 0),
        )
    }

    @Test
    fun a_transient_revoke_failure_retries_within_bounds() {
        assertEquals(
            RevokeDecision.Retry,
            LoginAlertNotifier.revoke_decision(ApiError.NetworkError, 0),
        )
        assertEquals(
            RevokeDecision.Retry,
            LoginAlertNotifier.revoke_decision(IOException("boom"), LoginAlertNotifier.REVOKE_MAX_ATTEMPTS - 2),
        )
        assertEquals(
            RevokeDecision.Failed,
            LoginAlertNotifier.revoke_decision(ApiError.NetworkError, LoginAlertNotifier.REVOKE_MAX_ATTEMPTS - 1),
        )
    }

    @Test
    fun a_rejected_revoke_fails_without_retrying() {
        assertEquals(
            RevokeDecision.Failed,
            LoginAlertNotifier.revoke_decision(ApiError.UnauthorizedError, 0),
        )
        assertEquals(
            RevokeDecision.Failed,
            LoginAlertNotifier.revoke_decision(ApiError.ForbiddenError(), 0),
        )
        assertEquals(
            RevokeDecision.Failed,
            LoginAlertNotifier.revoke_decision(ApiError.ValidationError(listOf("bad")), 0),
        )
    }

    @Test
    fun a_pending_send_stops_retrying_eventually() {
        assertTrue(UndoSendWorker.should_retry(0))
        assertTrue(UndoSendWorker.should_retry(UndoSendWorker.MAX_ATTEMPTS - 2))
        assertFalse(UndoSendWorker.should_retry(UndoSendWorker.MAX_ATTEMPTS - 1))
        assertFalse(UndoSendWorker.should_retry(UndoSendWorker.MAX_ATTEMPTS + 40))
    }
}
