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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astermail.android.R
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.DialogConfirmStyle

@Composable
fun scheduled_actions_dialog(
    email: Email,
    on_dismiss: () -> Unit,
    on_send_now: () -> Unit,
    on_reschedule: (String) -> Unit,
    on_cancel_send: () -> Unit,
) {
    val context = LocalContext.current
    val when_label = java.text.DateFormat
        .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(email.received_at))
    val recipients = email.sender_name.ifBlank { stringResource(R.string.no_recipients) }
    val open_picker: () -> Unit = {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = email.received_at }
        val date_picker = android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val picked = java.util.Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        if (picked.timeInMillis <= System.currentTimeMillis()) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.scheduled_time_in_past),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            on_reschedule(
                                java.time.Instant.ofEpochMilli(picked.timeInMillis).toString(),
                            )
                        }
                    },
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context),
                ).show()
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        )
        date_picker.datePicker.minDate = System.currentTimeMillis()
        date_picker.show()
    }

    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = email.subject.ifBlank { stringResource(R.string.no_subject) },
        message = stringResource(R.string.scheduled_recipients, recipients) +
            "\n" +
            stringResource(R.string.scheduled_for, when_label),
        confirm_label = stringResource(R.string.scheduled_cancel_send),
        cancel_label = stringResource(R.string.close),
        confirm_style = DialogConfirmStyle.destructive,
        on_confirm = on_cancel_send,
        extra_content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsterButton(
                    label = stringResource(R.string.scheduled_send_now),
                    onClick = on_send_now,
                    modifier = Modifier.fillMaxWidth(),
                )
                AsterButton(
                    label = stringResource(R.string.scheduled_reschedule),
                    onClick = open_picker,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
