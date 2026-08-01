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

package org.astermail.android.mail

const val all_mail_folder = "all"

private const val spam_suffix = "+spam"
private const val trash_suffix = "+trash"

fun all_mail_folder_id(include_spam: Boolean, include_trash: Boolean): String = buildString {
    append(all_mail_folder)
    if (include_spam) append(spam_suffix)
    if (include_trash) append(trash_suffix)
}

fun is_all_mail_folder(folder: String): Boolean =
    folder == all_mail_folder || folder.startsWith(all_mail_folder + "+")

fun all_mail_includes_spam(folder: String): Boolean =
    is_all_mail_folder(folder) && folder.contains(spam_suffix)

fun all_mail_includes_trash(folder: String): Boolean =
    is_all_mail_folder(folder) && folder.contains(trash_suffix)

val all_mail_folder_ids: List<String> = listOf(
    all_mail_folder_id(include_spam = false, include_trash = false),
    all_mail_folder_id(include_spam = true, include_trash = false),
    all_mail_folder_id(include_spam = false, include_trash = true),
    all_mail_folder_id(include_spam = true, include_trash = true),
)
