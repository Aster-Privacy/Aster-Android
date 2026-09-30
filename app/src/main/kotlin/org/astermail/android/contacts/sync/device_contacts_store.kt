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
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import org.astermail.android.contacts.decode_contact_photo_data_uri
import org.astermail.android.contacts.encode_contact_photo_bytes

class DeviceContactsStore(
    private val provider: ContentProviderClient,
    private val account: Account,
    private val labels: DeviceLabels,
) : ContactSyncDevice {

    private val pending = ArrayList<ContentProviderOperation>()
    private val pending_links = ArrayList<Pair<Int, String>>()
    private var pending_bytes = 0

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
                RawContacts.SYNC4,
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
                        schema = c.getString(6)?.toIntOrNull() ?: 1,
                    ),
                )
            }
        }
        return out
    }

    override fun read_cards(raw_ids: Collection<Long>): Map<Long, ContactCard> {
        if (raw_ids.isEmpty()) return emptyMap()
        val starred = mutableMapOf<Long, Boolean>()
        val links = mutableMapOf<Long, String>()
        val device_photos = mutableMapOf<Long, String>()
        val rows = mutableMapOf<Long, MutableList<DeviceDataRow>>()
        val data_columns = (1..15).map { "data$it" }
        val mimetype_marks = SYNCED_MIMETYPES.joinToString(",") { "?" }
        for (chunk in raw_ids.chunked(QUERY_CHUNK)) {
            val id_marks = chunk.joinToString(",") { "?" }
            val id_args = chunk.map { it.toString() }.toTypedArray()
            provider.query(
                raw_uri,
                arrayOf(RawContacts._ID, RawContacts.STARRED, RawContacts.SYNC3),
                "${RawContacts._ID} IN ($id_marks)",
                id_args,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    starred[c.getLong(0)] = c.getInt(1) != 0
                    c.getString(2)?.takeIf { it.isNotBlank() }?.let { links[c.getLong(0)] = it }
                }
            }
            provider.query(
                data_uri,
                arrayOf(Data.RAW_CONTACT_ID, Photo.PHOTO),
                "${Data.RAW_CONTACT_ID} IN ($id_marks) AND ${Data.MIMETYPE}=?",
                id_args + Photo.CONTENT_ITEM_TYPE,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.isNull(1)) continue
                    val bytes = runCatching { c.getBlob(1) }.getOrNull() ?: continue
                    if (bytes.isNotEmpty()) device_photos[c.getLong(0)] = device_photo_fingerprint(bytes)
                }
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
            contact_card_from_device_rows(
                rows[raw_id].orEmpty(),
                starred[raw_id] == true,
                labels,
                card_photo(device_photos[raw_id], links[raw_id]),
            )
        }
    }

    private fun card_photo(device_fp: String?, link: String?): String {
        if (device_fp == null) return ""
        val linked_device = link?.substringBefore('|')
        val linked_remote = link?.substringAfter('|', "").orEmpty()
        return if (linked_device == device_fp && linked_remote.isNotEmpty()) {
            linked_remote
        } else {
            DEVICE_PHOTO_PREFIX + device_fp
        }
    }

    private fun thumbnail(raw_id: Long): ByteArray? {
        provider.query(
            data_uri,
            arrayOf(Photo.PHOTO),
            "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
            arrayOf(raw_id.toString(), Photo.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.isNull(0)) continue
                val bytes = runCatching { c.getBlob(0) }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) return bytes
            }
        }
        return null
    }

    override fun read_photo(raw_id: Long): String? {
        val display_uri = Uri.withAppendedPath(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw_id),
            RawContacts.DisplayPhoto.CONTENT_DIRECTORY,
        )
        val display = runCatching {
            provider.openAssetFile(display_uri, "r")?.use { fd ->
                fd.createInputStream().use { it.readBytes() }
            }
        }.getOrNull()
        val bytes = display?.takeIf { it.isNotEmpty() } ?: thumbnail(raw_id) ?: return null
        return runCatching { encode_contact_photo_bytes(bytes) }.getOrNull()
    }

    private fun photo_bytes(data_uri: String?): ByteArray? {
        if (data_uri == null) return null
        val raw = decode_contact_photo_data_uri(data_uri) ?: return null
        if (raw.size <= PHOTO_WRITE_MAX_BYTES) return raw
        val scaled = runCatching { encode_contact_photo_bytes(raw) }.getOrNull() ?: return null
        return decode_contact_photo_data_uri(scaled)?.takeIf { it.size <= PHOTO_WRITE_MAX_BYTES }
    }

    private fun record_photo_link(raw_id: Long, remote_fp: String) {
        val device_fp = thumbnail(raw_id)?.let { device_photo_fingerprint(it) }
        val values = android.content.ContentValues().apply {
            put(RawContacts.SYNC3, if (device_fp == null) "" else "$device_fp|$remote_fp")
        }
        provider.update(raw_item_uri(raw_id), values, null, null)
    }

    private fun photo_delete_op(raw_id: Long): ContentProviderOperation =
        ContentProviderOperation.newDelete(data_uri)
            .withSelection(
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
                arrayOf(raw_id.toString(), Photo.CONTENT_ITEM_TYPE),
            )
            .build()

    override fun read_base(raw_id: Long): ContactCard? {
        provider.query(raw_item_uri(raw_id), arrayOf(RawContacts.SYNC2), null, null, null)?.use { c ->
            if (c.moveToFirst()) return contact_card_from_base_json(c.getString(0))
        }
        return null
    }

    override fun insert(source_id: String, revision: Long, card: ContactCard, photo: String?) {
        val rows = device_rows_for_card(card, labels)
        val bytes = if (card.photo.isNotEmpty()) photo_bytes(photo) else null
        val size = bytes?.size ?: 0
        if (pending.size + rows.size + 2 > BATCH_LIMIT || pending_bytes + size > PHOTO_BATCH_BYTES) flush()
        val base = pending.size
        pending.add(
            ContentProviderOperation.newInsert(raw_uri)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.SOURCE_ID, source_id)
                .withValue(RawContacts.SYNC1, revision.toString())
                .withValue(RawContacts.SYNC2, card.to_base_json())
                .withValue(RawContacts.SYNC4, CARD_SCHEMA.toString())
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
        if (bytes != null) {
            pending.add(
                ContentProviderOperation.newInsert(data_uri)
                    .withValueBackReference(Data.RAW_CONTACT_ID, base)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, bytes)
                    .build(),
            )
            pending_links.add(base to card.photo)
            pending_bytes += size
        }
    }

    override fun apply_remote(
        raw_id: Long,
        current: ContactCard?,
        target: ContactCard,
        revision: Long,
        source_id: String,
        expected_version: Int,
        photo: String?,
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
        val current_photo = current?.photo.orEmpty()
        var link_after = false
        var link: String? = null
        if (current_photo != target.photo) {
            val bytes = if (target.photo.isNotEmpty()) photo_bytes(photo) else null
            when {
                bytes != null -> {
                    ops.add(photo_delete_op(raw_id))
                    ops.add(
                        ContentProviderOperation.newInsert(data_uri)
                            .withValue(Data.RAW_CONTACT_ID, raw_id)
                            .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                            .withValue(Photo.PHOTO, bytes)
                            .build(),
                    )
                    link_after = true
                }
                target.photo.isEmpty() -> if (!is_device_photo(current_photo)) {
                    ops.add(photo_delete_op(raw_id))
                    link = ""
                }
                is_device_photo(current_photo) ->
                    link = current_photo.removePrefix(DEVICE_PHOTO_PREFIX) + "|" + target.photo
            }
        }
        val update = ContentProviderOperation.newUpdate(raw_item_uri(raw_id))
            .withValue(RawContacts.SOURCE_ID, source_id)
            .withValue(RawContacts.SYNC1, revision.toString())
            .withValue(RawContacts.SYNC2, target.to_base_json())
            .withValue(RawContacts.SYNC4, CARD_SCHEMA.toString())
            .withValue(RawContacts.STARRED, if (target.starred) 1 else 0)
            .withValue(RawContacts.DIRTY, 0)
        if (link != null) update.withValue(RawContacts.SYNC3, link)
        ops.add(update.build())
        return try {
            provider.applyBatch(ops)
            if (link_after) record_photo_link(raw_id, target.photo)
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
            put(RawContacts.SYNC4, CARD_SCHEMA.toString())
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
        val links = ArrayList(pending_links)
        pending.clear()
        pending_links.clear()
        pending_bytes = 0
        val results = provider.applyBatch(ops)
        for ((index, remote_fp) in links) {
            val uri = results.getOrNull(index)?.uri ?: continue
            runCatching { record_photo_link(ContentUris.parseId(uri), remote_fp) }
        }
    }

    fun purge_all() {
        pending.clear()
        pending_links.clear()
        pending_bytes = 0
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
                is ByteArray -> withValue(column, value)
                else -> withValue(column, value.toString())
            }
        }
        return this
    }

    companion object {
        const val BATCH_LIMIT = 400
        const val QUERY_CHUNK = 400
        const val PHOTO_WRITE_MAX_BYTES = 256 * 1024
        const val PHOTO_BATCH_BYTES = 384 * 1024
    }
}
