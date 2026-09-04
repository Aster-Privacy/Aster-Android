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

package org.astermail.android.storage.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DecryptedMailDao {

    @Query("SELECT * FROM decrypted_mail_cache ORDER BY timestamp DESC")
    suspend fun get_all(): List<DecryptedMailEntity>

    @Query("SELECT id FROM decrypted_mail_cache")
    suspend fun get_all_ids(): List<String>

    @Query(
        "SELECT * FROM decrypted_mail_cache WHERE is_trashed = 0 AND is_archived = 0 AND is_spam = 0 " +
            "AND NOT (TRIM(sender_email) = '' AND TRIM(subject) = '' AND TRIM(preview) = '') " +
            "AND preview NOT LIKE '%ASTER_BUNDLE_V2%' AND subject NOT LIKE '%ASTER_BUNDLE_V2%' " +
            "AND preview NOT LIKE '%double_ratchet_v1%' AND subject NOT LIKE '%double_ratchet_v1%' " +
            "AND preview NOT LIKE '%double_ratchet_v2%' AND subject NOT LIKE '%double_ratchet_v2%' " +
            "AND preview NOT LIKE '%ASTER_RATCHET_UNDECRYPTABLE%' " +
            "AND subject NOT LIKE '%ASTER_RATCHET_UNDECRYPTABLE%' " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun get_warm_window(limit: Int): List<DecryptedMailEntity>

    @Query(
        "SELECT id FROM decrypted_mail_cache WHERE timestamp > :min_timestamp " +
            "AND is_archived = 0 AND is_spam = 0 AND is_trashed = 0",
    )
    suspend fun inbox_ids_newer_than(min_timestamp: String): List<String>

    @Query(
        "SELECT id, thread_token FROM decrypted_mail_cache WHERE timestamp > :min_timestamp " +
            "AND is_archived = 0 AND is_spam = 0 AND is_trashed = 0",
    )
    suspend fun inbox_window_rows_newer_than(min_timestamp: String): List<InboxWindowRow>

    @Query("SELECT COUNT(*) FROM decrypted_mail_cache")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert_all(items: List<DecryptedMailEntity>)

    @Query("DELETE FROM decrypted_mail_cache WHERE id IN (:ids)")
    suspend fun delete_by_ids(ids: List<String>)

    @Query("DELETE FROM decrypted_mail_cache")
    suspend fun clear_all()

    @Query("DELETE FROM decrypted_mail_cache WHERE instr(',' || labels || ',', ',' || :folder_token || ',') > 0")
    suspend fun delete_by_folder_token(folder_token: String)

    @Query(
        "DELETE FROM decrypted_mail_cache WHERE preview LIKE '%ASTER_BUNDLE_V2%' " +
            "OR subject LIKE '%ASTER_BUNDLE_V2%' " +
            "OR preview LIKE '%double_ratchet_v1%' " +
            "OR preview LIKE '%double_ratchet_v2%' " +
            "OR subject LIKE '%double_ratchet_v1%' " +
            "OR subject LIKE '%double_ratchet_v2%' " +
            "OR preview LIKE '%ASTER_RATCHET_UNDECRYPTABLE%' " +
            "OR subject LIKE '%ASTER_RATCHET_UNDECRYPTABLE%'",
    )
    suspend fun delete_bundle_poisoned(): Int

    @Query(
        "UPDATE decrypted_mail_cache SET " +
            "preview = CASE WHEN preview LIKE '-----BEGIN PGP MESSAGE%' THEN '' ELSE preview END, " +
            "subject = CASE WHEN subject LIKE '-----BEGIN PGP MESSAGE%' THEN '' ELSE subject END " +
            "WHERE preview LIKE '-----BEGIN PGP MESSAGE%' OR subject LIKE '-----BEGIN PGP MESSAGE%'",
    )
    suspend fun clear_armored_previews(): Int

    @Query(
        "DELETE FROM decrypted_mail_cache WHERE " +
            "TRIM(sender_email) = '' AND TRIM(subject) = '' AND TRIM(preview) = ''",
    )
    suspend fun delete_blank_rows(): Int

    @Query(
        "UPDATE decrypted_mail_cache SET tag_tokens = CASE " +
            "WHEN tag_tokens IS NULL OR TRIM(tag_tokens) = '' THEN :token " +
            "WHEN instr(',' || tag_tokens || ',', ',' || :token || ',') > 0 THEN tag_tokens " +
            "ELSE tag_tokens || ',' || :token END WHERE id IN (:ids)",
    )
    suspend fun add_tag_token(ids: List<String>, token: String)

    @Query(
        "UPDATE decrypted_mail_cache SET tag_tokens = " +
            "TRIM(REPLACE(',' || tag_tokens || ',', ',' || :token || ',', ','), ',') " +
            "WHERE id IN (:ids) AND tag_tokens IS NOT NULL",
    )
    suspend fun remove_tag_token(ids: List<String>, token: String)

    @Query(
        "UPDATE decrypted_mail_cache SET labels = CASE " +
            "WHEN labels IS NULL OR TRIM(labels) = '' THEN :token " +
            "WHEN instr(',' || labels || ',', ',' || :token || ',') > 0 THEN labels " +
            "ELSE labels || ',' || :token END WHERE id IN (:ids)",
    )
    suspend fun add_label_token(ids: List<String>, token: String)

    @Query(
        "UPDATE decrypted_mail_cache SET labels = " +
            "TRIM(REPLACE(',' || labels || ',', ',' || :token || ',', ','), ',') " +
            "WHERE id IN (:ids) AND labels IS NOT NULL",
    )
    suspend fun remove_label_token(ids: List<String>, token: String)

    @Query("UPDATE decrypted_mail_cache SET is_read = :is_read WHERE id = :id")
    suspend fun update_read(id: String, is_read: Boolean)

    @Query("UPDATE decrypted_mail_cache SET is_starred = :is_starred WHERE id = :id")
    suspend fun update_starred(id: String, is_starred: Boolean)

    @Query("UPDATE decrypted_mail_cache SET is_read = :is_read WHERE id IN (:ids)")
    suspend fun set_read(ids: List<String>, is_read: Boolean)

    @Query("UPDATE decrypted_mail_cache SET is_starred = :is_starred WHERE id IN (:ids)")
    suspend fun set_starred(ids: List<String>, is_starred: Boolean)

    @Query("UPDATE decrypted_mail_cache SET is_pinned = :is_pinned WHERE id IN (:ids)")
    suspend fun set_pinned(ids: List<String>, is_pinned: Boolean)

    @Query("UPDATE decrypted_mail_cache SET is_trashed = 1 WHERE id IN (:ids)")
    suspend fun mark_trashed(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET is_trashed = 0 WHERE id IN (:ids)")
    suspend fun mark_untrashed(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET is_archived = 1 WHERE id IN (:ids)")
    suspend fun mark_archived(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET is_archived = 0 WHERE id IN (:ids)")
    suspend fun mark_unarchived(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET is_spam = 1 WHERE id IN (:ids)")
    suspend fun mark_spam(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET is_spam = 0 WHERE id IN (:ids)")
    suspend fun mark_unspam(ids: List<String>)

    @Query(
        "UPDATE decrypted_mail_cache SET is_trashed = 0, is_spam = 0, is_archived = 0 " +
            "WHERE id IN (:ids)",
    )
    suspend fun mark_restored(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET has_attachments = 1 WHERE id IN (:ids)")
    suspend fun mark_has_attachments(ids: List<String>)

    @Query("SELECT id FROM decrypted_mail_cache WHERE has_attachments = 0")
    suspend fun ids_without_attachments(): List<String>

    @Query("SELECT id FROM decrypted_mail_cache WHERE has_attachments = 1")
    suspend fun ids_with_attachments(): List<String>

    @Query("DELETE FROM decrypted_mail_cache WHERE id IN (:ids)")
    suspend fun remove_items(ids: List<String>)
}
