//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.api.imports

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class UploadInitRequest(
    val kind: String,
    val total_chunks: Int,
    val chunk_size: Int,
    val total_size: Long? = null,
    val expected_sha256_b64: String? = null,
)

@Serializable
data class UploadInitResponse(val upload_token: String = "")

@Serializable
data class UploadChunkRequest(
    val chunk_index: Int,
    val chunk_sha256_b64: String,
    val data_b64: String,
)

@Serializable
data class UploadStatusResponse(
    val upload_token: String = "",
    val state: String = "",
    val received_indices: List<Int> = emptyList(),
    val total_chunks: Int = 0,
)

@Serializable
data class CreateJobRequest(
    val kind: String,
    val upload_token: String? = null,
    val account_id: String? = null,
)

@Serializable
data class CreateJobResponse(val job_id: String = "")

@Serializable
data class OkResponse(val ok: Boolean = false)

@Serializable
data class JobSummary(
    val id: String = "",
    val kind: String = "",
    val state: String = "",
    val account_id: String? = null,
    val stats: kotlinx.serialization.json.JsonElement? = null,
    val last_error: String? = null,
    val trigger_source: String = "",
    val created_at: String = "",
    val updated_at: String = "",
    val finished_at: String? = null,
)

@Serializable
data class JobTaskCounts(
    val pending: Int = 0,
    val claimed: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val dead_letter: Int = 0,
)

@Serializable
data class JobFailureReason(
    val reason_code: String = "",
    val count: Int = 0,
)

@Serializable
data class JobRecentFailure(
    val id: String = "",
    val external_uid: Long? = null,
    val message_id: String? = null,
    val folder: String? = null,
    val reason_code: String = "",
    val reason_detail: String? = null,
    val retryable: Boolean = false,
    val created_at: String = "",
)

@Serializable
data class JobDetails(
    val job: JobSummary = JobSummary(),
    val task_counts: JobTaskCounts = JobTaskCounts(),
    val failures_by_reason: List<JobFailureReason> = emptyList(),
    val recent_failures: List<JobRecentFailure> = emptyList(),
)

@Serializable
data class CheckDuplicatesRequest(val message_id_hashes: List<String>)

@Serializable
data class CheckDuplicatesResponse(val duplicates: List<String> = emptyList())

@Serializable
data class ImportedEmailData(
    val message_id_hash: String,
    val encrypted_envelope: String,
    val envelope_nonce: String,
    val folder_token: String? = null,
    val content_hash: String? = null,
    val item_type: String? = null,
    val received_at: String? = null,
    val thread_token: String? = null,
)

@Serializable
data class StoreEmailsRequest(val emails: List<ImportedEmailData>)

@Serializable
data class StoreEmailsResponse(
    val stored_count: Int = 0,
    val duplicate_count: Int = 0,
    val skipped_quota_count: Int = 0,
    val quota_exceeded: Boolean = false,
    val success: Boolean = false,
)

interface ImportApi {
    suspend fun upload_init(req: UploadInitRequest): UploadInitResponse
    suspend fun upload_chunk(token: String, req: UploadChunkRequest): OkResponse
    suspend fun upload_finalize(token: String): OkResponse
    suspend fun upload_status(token: String): UploadStatusResponse
    suspend fun create_job(req: CreateJobRequest): CreateJobResponse
    suspend fun list_jobs(): List<JobSummary>
    suspend fun get_job(job_id: String): JobDetails
    suspend fun pause_job(job_id: String): OkResponse
    suspend fun retry_failed(job_id: String): OkResponse
    suspend fun check_duplicates(job_id: String, hashes: List<String>): CheckDuplicatesResponse
    suspend fun store_emails(job_id: String, emails: List<ImportedEmailData>): StoreEmailsResponse
}

class ImportApiImpl(private val client: ApiClient) : ImportApi {
    private val base = "/api/mail/v1/import"
    private val json = Json { ignoreUnknownKeys = true }

    private suspend inline fun <reified T> decode(response: HttpResponse): T {
        if (response.status != HttpStatusCode.OK) {
            throw ApiError.ServerError(response.status.value)
        }
        return response.body()
    }

    override suspend fun upload_init(req: UploadInitRequest): UploadInitResponse =
        decode(client.http.post("${client.base_url}$base/upload/init") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })

    override suspend fun upload_chunk(token: String, req: UploadChunkRequest): OkResponse =
        decode(client.http.post("${client.base_url}$base/upload/$token/chunk") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })

    override suspend fun upload_finalize(token: String): OkResponse =
        decode(client.http.post("${client.base_url}$base/upload/$token/finalize") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        })

    override suspend fun upload_status(token: String): UploadStatusResponse =
        decode(client.http.get("${client.base_url}$base/upload/$token"))

    override suspend fun create_job(req: CreateJobRequest): CreateJobResponse =
        decode(client.http.post("${client.base_url}$base/jobs") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(req)
        })

    override suspend fun list_jobs(): List<JobSummary> =
        decode(client.http.get("${client.base_url}$base/jobs"))

    override suspend fun get_job(job_id: String): JobDetails =
        decode(client.http.get("${client.base_url}$base/jobs/$job_id"))

    override suspend fun pause_job(job_id: String): OkResponse =
        decode(client.http.post("${client.base_url}$base/jobs/$job_id/pause") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        })

    override suspend fun retry_failed(job_id: String): OkResponse =
        decode(client.http.post("${client.base_url}$base/jobs/$job_id/retry-failed") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        })

    override suspend fun check_duplicates(job_id: String, hashes: List<String>): CheckDuplicatesResponse =
        decode(client.http.post("${client.base_url}$base/jobs/$job_id/check-duplicates") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(CheckDuplicatesRequest(hashes))
        })

    override suspend fun store_emails(job_id: String, emails: List<ImportedEmailData>): StoreEmailsResponse =
        decode(client.http.post("${client.base_url}$base/jobs/$job_id/emails") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(StoreEmailsRequest(emails))
        })
}
