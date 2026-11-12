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

package org.astermail.android.api.ratchet

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient

class PrekeyBundleFetchException(val status: Int) :
    java.io.IOException("prekey bundle request failed with status $status")

fun is_retryable_prekey_bundle_status(status: Int): Boolean =
    status >= 500 || status == 401 || status == 408 || status == 429

@Serializable
data class RatchetStateResponse(
    val id: String,
    val conversation_id: String,
    val encrypted_state: String,
    val state_nonce: String,
    val state_version: Int,
    val updated_at: String? = null,
)

@Serializable
data class PostRatchetStateRequest(
    val conversation_id: String,
    val encrypted_state: String,
    val state_nonce: String,
)

@Serializable
data class PutRatchetStateRequest(
    val conversation_id: String,
    val encrypted_state: String,
    val state_nonce: String,
    val expected_version: Int,
)

@Serializable
data class PqSecretResponse(
    val key_id: Int,
    val encrypted_secret: String,
    val secret_nonce: String,
)

@Serializable
data class PrekeyBundleResponse(
    val user_id: String,
    val kem_identity_key: String,
    val signed_prekey: String,
    val signed_prekey_signature: String,
    val one_time_prekey: String? = null,
    val pq_prekey: PqPrekeyInfo? = null,
    val pq_kem_public_key: String? = null,
    val x3dh_max_version: Int? = null,
    val pq_capable: Boolean? = null,
    val address_scoped: Boolean? = null,
)

@Serializable
data class RatchetIdentityResponse(
    val user_id: String? = null,
    val kem_identity_key: String,
    val signed_prekey: String,
    val signed_prekey_signature: String? = null,
    val pq_kem_public_key: String? = null,
    val address_scoped: Boolean? = null,
)

sealed class OwnRatchetIdentityLookup {
    data class Found(val identity: RatchetIdentityResponse) : OwnRatchetIdentityLookup()
    object NotFound : OwnRatchetIdentityLookup()
    object Unavailable : OwnRatchetIdentityLookup()
}

@Serializable
data class PqPrekeyInfo(
    val key_id: Int,
    val public_key: String,
)

@Serializable
data class UploadPrekeyBundleRequest(
    val kem_identity_key: String,
    val signed_prekey: String,
    val signed_prekey_signature: String,
    val one_time_prekeys: List<String> = emptyList(),
    val expected_version: Int? = null,
    val pq_kem_public_key: String? = null,
)

@Serializable
data class ReportEnvelopeCapabilityRequest(
    val client_id: String,
    val max_envelope_marker: Int,
    val platform: String? = null,
    val identity_fingerprint: String? = null,
    val pq_identity_fingerprint: String? = null,
    val x3dh_max_version: Int = 1,
    val pgp_external_version: Int? = null,
    val address_identity_version: Int? = null,
)

@Serializable
data class UploadAddressBundleRequest(
    val address: String,
    val kem_identity_key: String,
    val signed_prekey: String,
    val signed_prekey_signature: String,
    val pq_kem_public_key: String? = null,
)

@Serializable
data class UploadAddressBundleResponse(
    val success: Boolean = false,
    val active: Boolean = false,
)

sealed class AddressBundleUpload {
    data class Accepted(val active: Boolean) : AddressBundleUpload()
    object Unsupported : AddressBundleUpload()
    object Rejected : AddressBundleUpload()
    object Failed : AddressBundleUpload()
}

@Serializable
data class EnvelopeCapabilityResponse(
    val success: Boolean,
    val min_supported_marker: Int? = null,
    val pq_hybrid_enabled: Boolean = false,
    val identity_verified: Boolean = false,
    val pq_identity_attested: Boolean = false,
)

@Serializable
data class PlaintextEscrowEntry(
    val message_id: String = "",
    val encrypted_plaintext: String,
    val plaintext_nonce: String,
)

sealed class PutStateOutcome {
    data class Success(val response: RatchetStateResponse) : PutStateOutcome()
    object VersionConflict : PutStateOutcome()
    object NotFound : PutStateOutcome()
    data class Failure(val status: Int) : PutStateOutcome()
}

sealed class PostStateOutcome {
    data class Success(val response: RatchetStateResponse) : PostStateOutcome()
    object AlreadyExists : PostStateOutcome()
    data class Failure(val status: Int) : PostStateOutcome()
}

interface RatchetApi {
    suspend fun fetch_state(conversation_id_b64: String): RatchetStateResponse?
    suspend fun post_state(conversation_id_b64: String, encrypted_state: String, state_nonce: String): PostStateOutcome
    suspend fun put_state(conversation_id_b64: String, encrypted_state: String, state_nonce: String, expected_version: Int): PutStateOutcome
    suspend fun fetch_pq_secret(key_id: Int): PqSecretResponse?
    suspend fun fetch_prekey_bundle(username: String, email: String): PrekeyBundleResponse?
    suspend fun fetch_address_prekey_bundle(username: String, email: String): PrekeyBundleResponse? =
        fetch_prekey_bundle(username, email)
    suspend fun upload_address_bundle(request: UploadAddressBundleRequest): AddressBundleUpload =
        AddressBundleUpload.Unsupported
    suspend fun delete_state(conversation_id_b64: String): Boolean
    suspend fun upload_prekey_bundle(request: UploadPrekeyBundleRequest): Boolean
    suspend fun fetch_own_ratchet_identity(username: String, email: String): OwnRatchetIdentityLookup =
        OwnRatchetIdentityLookup.Unavailable
    suspend fun report_envelope_capability(request: ReportEnvelopeCapabilityRequest): EnvelopeCapabilityResponse?
    suspend fun upload_plaintext_escrow(entry: PlaintextEscrowEntry): Boolean = false
    suspend fun fetch_plaintext_escrow(message_id: String): PlaintextEscrowEntry? = null
}

class RatchetApiImpl(private val client: ApiClient) : RatchetApi {
    private val base = "/api/crypto/v1/ratchet/state"

    override suspend fun fetch_state(conversation_id_b64: String): RatchetStateResponse? {
        val encoded = java.net.URLEncoder.encode(conversation_id_b64, "UTF-8").replace("+", "%20")
        val response = client.http.get("${client.base_url}$base/$encoded")
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) return null
        return try {
            response.body()
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun post_state(
        conversation_id_b64: String,
        encrypted_state: String,
        state_nonce: String,
    ): PostStateOutcome {
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(PostRatchetStateRequest(conversation_id_b64, encrypted_state, state_nonce))
        }
        return when (response.status.value) {
            in 200..299 -> try { PostStateOutcome.Success(response.body()) } catch (_: Throwable) { PostStateOutcome.Failure(response.status.value) }
            409 -> PostStateOutcome.AlreadyExists
            else -> PostStateOutcome.Failure(response.status.value)
        }
    }

    override suspend fun fetch_pq_secret(key_id: Int): PqSecretResponse? {
        val response = client.http.get("${client.base_url}/api/crypto/v1/ratchet/pq-secret/$key_id")
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) return null
        return try { response.body() } catch (_: Throwable) { null }
    }

    override suspend fun put_state(
        conversation_id_b64: String,
        encrypted_state: String,
        state_nonce: String,
        expected_version: Int,
    ): PutStateOutcome {
        val response = client.http.put("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(PutRatchetStateRequest(conversation_id_b64, encrypted_state, state_nonce, expected_version))
        }
        return when (response.status.value) {
            in 200..299 -> try { PutStateOutcome.Success(response.body()) } catch (_: Throwable) { PutStateOutcome.Failure(response.status.value) }
            404 -> PutStateOutcome.NotFound
            409 -> PutStateOutcome.VersionConflict
            else -> PutStateOutcome.Failure(response.status.value)
        }
    }

    override suspend fun delete_state(conversation_id_b64: String): Boolean {
        val encoded = java.net.URLEncoder.encode(conversation_id_b64, "UTF-8").replace("+", "%20")
        val response = client.http.delete("${client.base_url}$base/$encoded") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return response.status.value in 200..299 || response.status.value == 404
    }

    override suspend fun fetch_prekey_bundle(username: String, email: String): PrekeyBundleResponse? {
        val username_enc = java.net.URLEncoder.encode(username, "UTF-8").replace("+", "%20")
        val email_enc = java.net.URLEncoder.encode(email, "UTF-8").replace("+", "%20")
        val response = client.http.get("${client.base_url}/api/crypto/v1/ratchet/prekey-bundle/$username_enc?email=$email_enc")
        if (response.status.value == 404) return null
        if (is_retryable_prekey_bundle_status(response.status.value)) {
            throw PrekeyBundleFetchException(response.status.value)
        }
        if (response.status.value !in 200..299) return null
        return try { response.body() } catch (_: Throwable) { null }
    }

    override suspend fun fetch_address_prekey_bundle(username: String, email: String): PrekeyBundleResponse? {
        val username_enc = java.net.URLEncoder.encode(username, "UTF-8").replace("+", "%20")
        val email_enc = java.net.URLEncoder.encode(email, "UTF-8").replace("+", "%20")
        val response = client.http.get(
            "${client.base_url}/api/crypto/v1/ratchet/prekey-bundle/$username_enc?email=$email_enc&scope=address",
        )
        if (response.status.value == 404) return null
        if (is_retryable_prekey_bundle_status(response.status.value)) {
            throw PrekeyBundleFetchException(response.status.value)
        }
        if (response.status.value !in 200..299) return null
        return try { response.body() } catch (_: Throwable) { null }
    }

    override suspend fun upload_address_bundle(request: UploadAddressBundleRequest): AddressBundleUpload {
        val response = try {
            client.http.put("${client.base_url}/api/crypto/v1/ratchet/address-bundle") {
                contentType(ContentType.Application.Json)
                client.get_csrf()?.let { header("X-CSRF-Token", it) }
                setBody(request)
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Throwable) {
            return AddressBundleUpload.Failed
        }
        val status = response.status.value
        if (status == 404 || status == 405) return AddressBundleUpload.Unsupported
        if (status in 400..499) return AddressBundleUpload.Rejected
        if (status !in 200..299) return AddressBundleUpload.Failed
        val body = try {
            response.body<UploadAddressBundleResponse>()
        } catch (_: Throwable) {
            return AddressBundleUpload.Failed
        }
        return AddressBundleUpload.Accepted(body.active)
    }

    override suspend fun fetch_own_ratchet_identity(username: String, email: String): OwnRatchetIdentityLookup {
        val username_enc = java.net.URLEncoder.encode(username, "UTF-8").replace("+", "%20")
        val email_enc = java.net.URLEncoder.encode(email, "UTF-8").replace("+", "%20")
        val response = client.http.get("${client.base_url}/api/crypto/v1/ratchet/identity/$username_enc?email=$email_enc")
        if (response.status.value == 404) return OwnRatchetIdentityLookup.NotFound
        if (response.status.value !in 200..299) return OwnRatchetIdentityLookup.Unavailable
        return try {
            OwnRatchetIdentityLookup.Found(response.body())
        } catch (_: Throwable) {
            OwnRatchetIdentityLookup.Unavailable
        }
    }

    override suspend fun upload_prekey_bundle(request: UploadPrekeyBundleRequest): Boolean {
        val response = client.http.put("${client.base_url}/api/crypto/v1/ratchet/prekey-bundle") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return response.status.value in 200..299
    }

    override suspend fun report_envelope_capability(
        request: ReportEnvelopeCapabilityRequest,
    ): EnvelopeCapabilityResponse? {
        val response = client.http.post("${client.base_url}/api/crypto/v1/ratchet/envelope-capability") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) return null
        return try { response.body() } catch (_: Throwable) { null }
    }

    override suspend fun upload_plaintext_escrow(entry: PlaintextEscrowEntry): Boolean {
        val response = client.http.post("${client.base_url}/api/crypto/v1/ratchet/plaintext") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(entry)
        }
        return response.status.value in 200..299
    }

    override suspend fun fetch_plaintext_escrow(message_id: String): PlaintextEscrowEntry? {
        val encoded = java.net.URLEncoder.encode(message_id, "UTF-8").replace("+", "%20")
        val response = client.http.get("${client.base_url}/api/crypto/v1/ratchet/plaintext/$encoded")
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) return null
        return try { response.body() } catch (_: Throwable) { null }
    }
}
