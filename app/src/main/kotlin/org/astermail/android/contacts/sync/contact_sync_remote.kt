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

import org.astermail.android.api.ApiError
import org.astermail.android.api.contacts.ContactResyncRequiredError
import org.astermail.android.contacts.ContactUndecryptableException
import org.astermail.android.contacts.ContactsRepository

const val REVISION_CONFLICT_CODE = "CONTACT_REVISION_CONFLICT"

class RepositoryContactSyncRemote(
    private val repository: ContactsRepository,
) : ContactSyncRemote {

    override suspend fun changes(since: Long, limit: Int): RemoteChangesPage {
        val page = try {
            repository.fetch_raw_changes(since, limit)
        } catch (_: ContactResyncRequiredError) {
            throw ContactResyncRequired()
        }
        return RemoteChangesPage(
            contacts = page.records.map { RemoteContact(it.id, it.revision, it.json) },
            deleted_ids = page.deleted_ids,
            undecryptable_ids = page.undecryptable_ids,
            next_since = page.next_since,
            has_more = page.has_more,
        )
    }

    override suspend fun fetch(id: String): RemoteContact? {
        val record = try {
            repository.fetch_raw_contact(id)
        } catch (_: ContactUndecryptableException) {
            throw RemoteUndecryptable()
        }
        return record?.let { RemoteContact(it.id, it.revision, it.json) }
    }

    override suspend fun create(json: String): RemoteCreated {
        val response = try {
            repository.create_raw_contact(json, unique_token = false)
        } catch (_: ApiError.Conflict) {
            try {
                repository.create_raw_contact(json, unique_token = true)
            } catch (_: ApiError.PlanLimitExceeded) {
                throw RemotePlanLimit()
            }
        } catch (_: ApiError.PlanLimitExceeded) {
            throw RemotePlanLimit()
        }
        val id = response.id?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("missing contact id")
        return RemoteCreated(id, response.revision ?: 1L)
    }

    override suspend fun update(id: String, json: String, expected_revision: Long): Long {
        val response = try {
            repository.update_raw_contact(id, json, expected_revision)
        } catch (e: ApiError.Conflict) {
            if (e.code == REVISION_CONFLICT_CODE) throw RemoteConflict()
            throw e
        } catch (_: ApiError.NotFoundError) {
            throw RemoteNotFound()
        }
        return response.revision ?: throw IllegalStateException("missing contact revision")
    }
}
