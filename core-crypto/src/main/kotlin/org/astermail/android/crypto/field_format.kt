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

package org.astermail.android.crypto

object FieldFormat {

    const val V1 = 1
    const val V2 = 2
    const val KIND_TEMPLATE = "template"

    private const val AAD_PREFIX = "aster-field-v2"

    @Volatile
    var write_v2: Boolean = false

    @Volatile
    var write_source: () -> Boolean = { false }

    fun write_version(): Int = if (write_v2 || runCatching { write_source() }.getOrDefault(false)) V2 else V1

    fun candidates(kind: String, field: String, record_id: String?): List<ByteArray?> = buildList {
        val has_record = !record_id.isNullOrEmpty()
        if (has_record) add(aad(kind, field, record_id))
        if (has_record && BoundFieldRecords.is_bound(kind, record_id)) return@buildList
        add(aad(kind, field, null))
        add(null)
    }

    fun aad(kind: String, field: String, record_id: String?): ByteArray =
        "$AAD_PREFIX|kind=$kind|field=$field|record=${record_id.orEmpty()}".toByteArray(Charsets.UTF_8)

    fun encrypt(
        plaintext: ByteArray,
        storage_key: ByteArray,
        kind: String,
        field: String,
        record_id: String?,
        version: Int = write_version(),
    ): CryptoNative.EncryptedField {
        val nonce = CryptoNative.random_field_nonce()
        return CryptoNative.EncryptedField(
            ciphertext = seal(plaintext, storage_key, nonce, kind, field, record_id, version),
            nonce = nonce,
        )
    }

    fun seal(
        plaintext: ByteArray,
        storage_key: ByteArray,
        nonce: ByteArray,
        kind: String,
        field: String,
        record_id: String?,
        version: Int,
    ): ByteArray {
        require(version == V1 || version == V2) { "unsupported field version" }
        CryptoNative.require_field_inputs(storage_key, nonce)
        val bound = if (version == V2) aad(kind, field, record_id) else null
        return AesGcm.encrypt(storage_key, nonce, plaintext, bound)
    }

    fun decrypt(
        ciphertext: ByteArray,
        nonce: ByteArray,
        storage_key: ByteArray,
        kind: String,
        field: String,
        record_id: String?,
    ): ByteArray {
        CryptoNative.require_field_inputs(storage_key, nonce)
        val candidates = candidates(kind, field, record_id)
        var last: Throwable? = null
        for ((index, candidate) in candidates.withIndex()) {
            try {
                val opened = AesGcm.decrypt(storage_key, nonce, ciphertext, candidate)
                if (index == 0 && !record_id.isNullOrEmpty()) BoundFieldRecords.mark(kind, record_id)
                return opened
            } catch (failure: javax.crypto.AEADBadTagException) {
                last = failure
            }
        }
        throw last ?: IllegalStateException("field did not open")
    }
}
