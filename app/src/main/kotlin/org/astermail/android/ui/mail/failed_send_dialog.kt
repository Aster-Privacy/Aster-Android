// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import org.astermail.android.R
import org.astermail.android.mail.MailViewModel

@Composable
fun failed_send_dialog(mail_vm: MailViewModel) {
    val host_state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!host_state.isAtLeast(Lifecycle.State.RESUMED)) return

    val send_problem by mail_vm.send_problem.collectAsStateWithLifecycle()
    val failed_send_count by mail_vm.failed_send_count.collectAsStateWithLifecycle()
    val failed_send_notice by mail_vm.failed_send_notice.collectAsStateWithLifecycle()
    val pending_identity_changes by mail_vm.identity_changes.collectAsStateWithLifecycle()
    val open_failed_send = failed_send_notice
    if (send_problem && open_failed_send != null) {
        val identity_change_pending = open_failed_send.reason == org.astermail.android.mail.SendFailureReason.IDENTITY_CHANGED &&
            org.astermail.android.mail.identity_change_pending_for(
                open_failed_send.recipients,
                pending_identity_changes.map { it.sender_email },
            )
        val reason_text = when (open_failed_send.reason) {
            org.astermail.android.mail.SendFailureReason.POST_QUANTUM -> stringResource(R.string.outbox_failed_reason_post_quantum)
            org.astermail.android.mail.SendFailureReason.IDENTITY_CHANGED -> if (identity_change_pending) {
                stringResource(R.string.outbox_failed_reason_identity)
            } else {
                stringResource(R.string.outbox_failed_reason_identity_unverified)
            }
            org.astermail.android.mail.SendFailureReason.KEY_CHANGED -> stringResource(R.string.outbox_failed_reason_key_changed)
            org.astermail.android.mail.SendFailureReason.WEAK_PASSWORD -> stringResource(R.string.message_password_too_weak)
            org.astermail.android.mail.SendFailureReason.ENCRYPTION -> stringResource(R.string.outbox_failed_reason_encryption)
            org.astermail.android.mail.SendFailureReason.SENDER_REFUSED -> stringResource(R.string.outbox_failed_reason_sender_refused)
            org.astermail.android.mail.SendFailureReason.REJECTED -> stringResource(R.string.outbox_failed_reason_rejected)
            org.astermail.android.mail.SendFailureReason.CONNECTION -> stringResource(R.string.outbox_failed_reason_connection)
            org.astermail.android.mail.SendFailureReason.ATTACHMENT -> stringResource(R.string.outbox_failed_reason_attachment)
            org.astermail.android.mail.SendFailureReason.OTHER -> stringResource(R.string.send_problem_failed_message)
        }
        val subject_text = open_failed_send.subject.ifBlank { stringResource(R.string.no_subject) }
        val shown_recipients = open_failed_send.recipients.take(3).joinToString(", ")
        val hidden_recipients = open_failed_send.recipients.size - 3
        val recipients_text = if (hidden_recipients > 0) "$shown_recipients +$hidden_recipients" else shown_recipients
        val detail_text = stringResource(R.string.outbox_failed_detail, subject_text, recipients_text)
        val waiting_text = if (open_failed_send.more_count > 0) {
            "\n\n" + stringResource(R.string.outbox_failed_waiting, open_failed_send.more_count)
        } else {
            ""
        }
        val failed_id = open_failed_send.id
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { mail_vm.dismiss_failed_send(failed_id) },
            title = stringResource(R.string.outbox_failed_title),
            message = reason_text + "\n\n" + detail_text + waiting_text,
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.discard),
                    onClick = { mail_vm.discard_failed_send(failed_id) },
                )
                if (open_failed_send.reason == org.astermail.android.mail.SendFailureReason.POST_QUANTUM) {
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.post_quantum_send_anyway),
                        onClick = { mail_vm.retry_failed_send(failed_id, allow_non_post_quantum = true) },
                    )
                } else if (identity_change_pending) {
                    val failed_recipients = open_failed_send.recipients
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.identity_trust_new_key),
                        onClick = { mail_vm.trust_new_keys_and_retry(failed_id, failed_recipients) },
                    )
                } else {
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.retry),
                        onClick = { mail_vm.retry_failed_send(failed_id) },
                    )
                }
            },
        )
    } else if (send_problem) {
        val has_failed = failed_send_count > 0
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { mail_vm.dismiss_send_problem() },
            title = stringResource(R.string.send_problem_title),
            message = if (has_failed) {
                stringResource(R.string.send_problem_failed_message)
            } else {
                stringResource(R.string.send_problem_message)
            },
            footer = {
                if (has_failed) {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.discard),
                        onClick = { mail_vm.discard_failed_sends() },
                    )
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.retry),
                        onClick = { mail_vm.retry_failed_sends() },
                    )
                } else {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.ok),
                        onClick = { mail_vm.dismiss_send_problem() },
                    )
                }
            },
        )
    }
}
