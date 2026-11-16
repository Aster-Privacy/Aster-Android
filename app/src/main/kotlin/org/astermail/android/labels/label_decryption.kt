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

package org.astermail.android.labels

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.tags.TagItem

private fun open_field(value: String?, nonce: String?, decrypt: (String, String) -> String?): Result<String?> =
    if (!value.isNullOrBlank() && !nonce.isNullOrBlank()) {
        decrypt(value, nonce)?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
    } else {
        Result.success(value)
    }

private fun failed_label(label: LabelItem): LabelItem =
    label.copy(encrypted_name = null, encrypted_color = null, encrypted_icon = null, decryption_failed = true)

private fun failed_tag(tag: TagItem): TagItem =
    tag.copy(encrypted_name = "", encrypted_color = null, encrypted_icon = null, decryption_failed = true)

fun label_after_decryption(label: LabelItem, decrypt: (String, String) -> String?): LabelItem = try {
    val name = open_field(label.encrypted_name, label.name_nonce, decrypt)
    if (name.isFailure) {
        failed_label(label)
    } else {
        label.copy(
            encrypted_name = name.getOrNull(),
            encrypted_color = open_field(label.encrypted_color, label.color_nonce, decrypt).getOrNull(),
            encrypted_icon = open_field(label.encrypted_icon, label.icon_nonce, decrypt).getOrNull(),
            decryption_failed = false,
        )
    }
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    failed_label(label)
}

fun tag_after_decryption(tag: TagItem, decrypt: (String, String) -> String?): TagItem = try {
    val name = open_field(tag.encrypted_name, tag.name_nonce, decrypt)
    if (name.isFailure) {
        failed_tag(tag)
    } else {
        tag.copy(
            encrypted_name = name.getOrNull().orEmpty(),
            encrypted_color = open_field(tag.encrypted_color, tag.color_nonce, decrypt).getOrNull(),
            encrypted_icon = open_field(tag.encrypted_icon, tag.icon_nonce, decrypt).getOrNull(),
            decryption_failed = false,
        )
    }
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    failed_tag(tag)
}

fun readable_label_name(label: LabelItem): String? =
    label.encrypted_name?.takeIf { !label.decryption_failed && it.isNotBlank() }

fun readable_label_icon(label: LabelItem): String? =
    label.encrypted_icon?.takeIf { !label.decryption_failed && it.isNotBlank() }

fun readable_tag_icon(tag: TagItem): String? =
    tag.encrypted_icon?.takeIf { !tag.decryption_failed && it.isNotBlank() }
