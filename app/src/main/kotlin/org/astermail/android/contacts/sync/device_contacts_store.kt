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

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.OperationApplicationException
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

class DeviceContactsStore(
    private val provider: ContentProviderClient,
    private val account: Account,
    private val labels: DeviceLabels,
) : ContactSyncDevice {

    private val pending = ArrayList<ContentProviderOperation>()

    private fun Uri.as_sync_adapter(): Uri = buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
        .build()

    private val raw_uri = RawContacts.CONTENT_URI.as_sync_adapter()
    private val data_uri = Data.CONTENT_URI.as_sync_adapter()

    private fun raw_item_uri(raw_id: Long) = ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw_id).as_sync_adapter()

    private val account_selection = "${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.ACCOUNT_TYPE}=?"
    private val account_args = arrayOf(account.name, account.type)

    override fun list_states(): List<DeviceContactState> {
        val out = mutableListOf<DeviceContactState>()
        provider.query(
            raw_uri,
            arrayOf(
                RawContacts._ID,
                RawContacts.SOURCE_ID,
                RawContacts.SYNC1,
                RawContacts.DIRTY,
                RawContacts.DELETED,
                RawContacts.VERSION,
            ),
            account_selection,
            account_args,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    DeviceContactState(
                        raw_id = c.getLong(0),
                        source_id = c.getString(1)?.takeIf { it.isNotBlank() },
                        sync_revision = c.getString(2)?.toLongOrNull(),
                        dirty = c.getInt(3) != 0,
                        deleted = c.getInt(4) != 0,
                        version = c.getInt(5),
                    ),
                )
            }
        }
        return out
    }

    override fun read_cards(raw_ids: Collection<Long>): Map<Long, ContactCard> {
        if (raw_ids.isEmpty()) return emptyMap()
        val starred = mutableMapOf<Long, Boolean>()
        val rows = mutableMapOf<Long, MutableList<DeviceDataRow>>()
        val data_columns = (1..15).map { "data$it" }
        val mimetype_marks = SYNCED_MIMETYPES.joinToString(",") { "?" }
        for (chunk in raw_ids.chunked(QUERY_CHUNK)) {
            val id_marks = chunk.joinToString(",") { "?" }
            val id_args = chunk.map { it.toString() }.toTypedArray()
            provider.query(
                raw_uri,
                arrayOf(RawContacts._ID, RawContacts.STARRED),
                "${RawContacts._ID} IN ($id_marks)",
                id_args,
                null,
            )?.use { c ->
                while (c.moveToNext()) starred[c.getLong(0)] = c.getInt(1) != 0
            }
            provider.query(
                data_uri,
                arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE) + data_columns,
                "${Data.RAW_CONTACT_ID} IN ($id_marks) AND ${Data.MIMETYPE} IN ($mimetype_marks)",
                id_args + SYNCED_MIMETYPES,
                "${Data.RAW_CONTACT_ID}, ${Data._ID}",
            )?.use { c ->
                while (c.moveToNext()) {
                    val raw_id = c.getLong(0)
                    val mimetype = c.getString(1) ?: continue
                    val values = HashMap<String, Any?>()
                    data_columns.forEachIndexed { index, column ->
                        val position = index + 2
                        if (!c.isNull(position)) {
                            values[column] = runCatching { c.getString(position) }.getOrNull()
                        }
                    }
                    rows.getOrPut(raw_id) { mutableListOf() }.add(DeviceDataRow(mimetype, values))
                }
            }
        }
        return raw_ids.filter { it in starred }.associateWith { raw_id ->
            contact_card_from_device_rows(rows[raw_id].orEmpty(), starred[raw_id] == true, labels)
        }
    }

    override fun read_base(raw_id: Long): ContactCard? {
        provider.query(raw_item_uri(raw_id), arrayOf(RawContacts.SYNC2), null, null, null)?.use { c ->
            if (c.moveToFirst()) return contact_card_from_base_json(c.getString(0))
        }
        return null
    }

    override fun insert(source_id: String, revision: Long, card: ContactCard) {
        val rows = device_rows_for_card(card, labels)
        if (pending.size + rows.size + 1 > BATCH_LIMIT) flush()
        val base = pending.size
        pending.add(
            ContentProviderOperation.newInsert(raw_uri)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.SOURCE_ID, source_id)
                .withValue(RawContacts.SYNC1, revision.toString())
                .withValue(RawContacts.SYNC2, card.to_base_json())
                .withValue(RawContacts.STARRED, if (card.starred) 1 else 0)
                .withYieldAllowed(true)
                .build(),
        )
        for (row in rows) {
            pending.add(
                ContentProviderOperation.newInsert(data_uri)
                    .withValueBackReference(Data.RAW_CONTACT_ID, base)
                    .with_row(row)
                    .build(),
            )
        }
    }

    override fun apply_remote(
        raw_id: Long,
        current: ContactCard?,
        target: ContactCard,
        revision: Long,
        source_id: String,
        expected_version: Int,
    ): Boolean {
        flush()
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newAssertQuery(raw_item_uri(raw_id))
                .withValue(RawContacts.VERSION, expected_version)
                .withValue(RawContacts.DELETED, 0)
                .withExpectedCount(1)
                .build(),
        )
        for (group in changed_device_groups(current, target)) {
            ops.add(
                ContentProviderOperation.newDelete(data_uri)
                    .withSelection(
                        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
                        arrayOf(raw_id.toString(), group.mimetype),
                    )
                    .build(),
            )
            for (row in device_rows_for_group(target, group, labels)) {
                ops.add(
                    ContentProviderOperation.newInsert(data_uri)
                        .withValue(Data.RAW_CONTACT_ID, raw_id)
                        .with_row(row)
                        .build(),
                )
            }
        }
        ops.add(
            ContentProviderOperation.newUpdate(raw_item_uri(raw_id))
                .withValue(RawContacts.SOURCE_ID, source_id)
                .withValue(RawContacts.SYNC1, revision.toString())
                .withValue(RawContacts.SYNC2, target.to_base_json())
                .withValue(RawContacts.STARRED, if (target.starred) 1 else 0)
                .withValue(RawContacts.DIRTY, 0)
                .build(),
        )
        return try {
            provider.applyBatch(ops)
            true
        } catch (_: OperationApplicationException) {
            false
        }
    }

    override fun set_identity(raw_id: Long, source_id: String, revision: Long, base: ContactCard) {
        flush()
        val values = android.content.ContentValues().apply {
            put(RawContacts.SOURCE_ID, source_id)
            put(RawContacts.SYNC1, revision.toString())
            put(RawContacts.SYNC2, base.to_base_json())
        }
        provider.update(raw_item_uri(raw_id), values, null, null)
    }

    override fun purge(raw_id: Long) {
        flush()
        provider.delete(raw_item_uri(raw_id), null, null)
    }

    override fun flush() {
        if (pending.isEmpty()) return
        val ops = ArrayList(pending)
        pending.clear()
        provider.applyBatch(ops)
    }

    fun purge_all() {
        pending.clear()
        provider.delete(raw_uri, account_selection, account_args)
    }

    private fun ContentProviderOperation.Builder.with_row(row: DeviceDataRow): ContentProviderOperation.Builder {
        withValue(Data.MIMETYPE, row.mimetype)
        for ((column, value) in row.values) {
            when (value) {
                null -> withValue(column, null)
                is Int -> withValue(column, value)
                is Long -> withValue(column, value)
                is Boolean -> withValue(column, if (value) 1 else 0)
                else -> withValue(column, value.toString())
            }
        }
        return this
    }

    companion object {
        const val BATCH_LIMIT = 400
        const val QUERY_CHUNK = 400
    }
}
