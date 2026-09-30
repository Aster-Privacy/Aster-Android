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

package org.astermail.android.contacts.sync

fun merge_contact_cards(base: ContactCard?, local: ContactCard, remote: ContactCard): ContactCard {
    val origin = base ?: remote
    return ContactCard(
        first_name = merge_scalar(origin.first_name, local.first_name, remote.first_name),
        middle_name = merge_scalar(origin.middle_name, local.middle_name, remote.middle_name),
        last_name = merge_scalar(origin.last_name, local.last_name, remote.last_name),
        prefix = merge_scalar(origin.prefix, local.prefix, remote.prefix),
        suffix = merge_scalar(origin.suffix, local.suffix, remote.suffix),
        phonetic_first_name = merge_scalar(origin.phonetic_first_name, local.phonetic_first_name, remote.phonetic_first_name),
        phonetic_middle_name = merge_scalar(origin.phonetic_middle_name, local.phonetic_middle_name, remote.phonetic_middle_name),
        phonetic_last_name = merge_scalar(origin.phonetic_last_name, local.phonetic_last_name, remote.phonetic_last_name),
        nickname = merge_scalar(origin.nickname, local.nickname, remote.nickname),
        company = merge_scalar(origin.company, local.company, remote.company),
        department = merge_scalar(origin.department, local.department, remote.department),
        job_title = merge_scalar(origin.job_title, local.job_title, remote.job_title),
        notes = merge_scalar(origin.notes, local.notes, remote.notes),
        birthday = merge_scalar(origin.birthday, local.birthday, remote.birthday),
        emails = merge_list(origin.emails, local.emails, remote.emails),
        phones = merge_list(origin.phones, local.phones, remote.phones),
        addresses = merge_list(origin.addresses, local.addresses, remote.addresses),
        websites = merge_list(origin.websites, local.websites, remote.websites),
        relations = merge_list(origin.relations, local.relations, remote.relations),
        messengers = merge_list(origin.messengers, local.messengers, remote.messengers),
        dates = merge_list(origin.dates, local.dates, remote.dates),
        starred = merge_scalar(origin.starred, local.starred, remote.starred),
    ).normalized()
}

internal fun <T> merge_scalar(base: T, local: T, remote: T): T = when {
    local == base -> remote
    remote == base -> local
    else -> remote
}

internal fun <T> merge_list(base: List<T>, local: List<T>, remote: List<T>): List<T> {
    if (local == base) return remote
    if (remote == base) return local
    val removed_locally = base.filter { it !in local }.toSet()
    val added_locally = local.filter { it !in base }
    val out = remote.filter { it !in removed_locally }.toMutableList()
    for (item in added_locally) if (item !in out) out.add(item)
    return out
}
