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

package org.astermail.android.ui.contacts

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.contacts.contact_share_file_name
import org.astermail.android.contacts.contact_share_text
import org.astermail.android.contacts.contact_to_vcard

private const val SHARED_CONTACTS_DIR = "exports/shared_contacts"
private const val SHARED_CONTACT_TTL_MS = 60L * 60L * 1000L

fun share_contact_as_text(context: Context, contact: Contact): Boolean {
    val text = contact_share_text(contact)
    if (text.isBlank()) return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return launch_chooser(context, send)
}

suspend fun share_contact_as_vcard(context: Context, contact: Contact): Boolean {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(context.cacheDir, SHARED_CONTACTS_DIR)
            val cutoff = System.currentTimeMillis() - SHARED_CONTACT_TTL_MS
            root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
            val dir = File(root, System.nanoTime().toString()).apply { mkdirs() }
            val target = File(dir, contact_share_file_name(contact))
            target.writeText(contact_to_vcard(contact) + "\r\n", Charsets.UTF_8)
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
        }.getOrNull()
    } ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/x-vcard"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return launch_chooser(context, send)
}

private fun launch_chooser(context: Context, send: Intent): Boolean {
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(chooser) }.isSuccess
}

@Composable
fun contact_entry_type_label(type: String, label: String): String = when (type) {
    ContactEntry.TYPE_HOME -> stringResource(R.string.contact_type_home)
    ContactEntry.TYPE_WORK -> stringResource(R.string.work)
    ContactEntry.TYPE_PERSONAL -> stringResource(R.string.personal)
    ContactEntry.TYPE_MOBILE -> stringResource(R.string.mobile)
    ContactEntry.TYPE_FAX -> stringResource(R.string.contact_type_fax)
    ContactEntry.TYPE_PAGER -> stringResource(R.string.contact_type_pager)
    else -> label.trim().ifEmpty { stringResource(R.string.contact_type_other) }
}
