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

package org.astermail.android.ui.common

import android.content.Context
import java.text.DateFormat
import java.util.Date
import org.astermail.android.R
import org.astermail.android.ui.mail.escape_print_text
import org.astermail.android.ui.mail.print_email_document

fun print_recovery_codes(
    context: Context,
    account_email: String,
    codes: List<String>,
    created_at: Date = Date(),
    on_failure: () -> Unit,
) {
    val title = context.getString(R.string.recovery_codes_print_title)
    val created = context.getString(
        R.string.recovery_codes_print_created,
        DateFormat.getDateInstance(DateFormat.LONG).format(created_at),
    )
    val note = context.getString(R.string.recovery_codes_print_note)
    val html = build_recovery_codes_print_html(title, account_email, created, note, codes)

    print_email_document(
        context = context,
        job_name = title,
        html = html,
        allow_network = false,
        failure_message = context.getString(R.string.print_not_available),
        on_failure = on_failure,
    )
}

private fun build_recovery_codes_print_html(
    title: String,
    account_email: String,
    created: String,
    note: String,
    codes: List<String>,
): String = buildString {
    append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
    append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
    append("<style>")
    append("body{font-family:sans-serif;color:#111;background:#fff;margin:0;padding:24px;}")
    append("h1{font-size:20px;margin:0 0 8px 0;}")
    append("p{font-size:12px;color:#444;margin:0 0 4px 0;}")
    append("ol{font-family:monospace;font-size:14px;margin:20px 0;padding-left:28px;}")
    append("li{padding:3px 0;letter-spacing:0.5px;}")
    append(".note{margin-top:20px;font-size:12px;color:#444;}")
    append("</style></head><body dir=\"auto\">")
    append("<h1>").append(escape_print_text(title)).append("</h1>")
    if (account_email.isNotBlank()) {
        append("<p>").append(escape_print_text(account_email)).append("</p>")
    }
    append("<p>").append(escape_print_text(created)).append("</p>")
    append("<ol>")
    codes.forEach { append("<li>").append(escape_print_text(it)).append("</li>") }
    append("</ol>")
    append("<p class=\"note\">").append(escape_print_text(note)).append("</p>")
    append("</body></html>")
}
