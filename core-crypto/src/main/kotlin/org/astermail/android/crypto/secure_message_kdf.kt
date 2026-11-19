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

package org.astermail.android.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter

object SecureMessageKdf {
    const val V1 = 1
    const val V2 = 2
    const val PBKDF2_ITERATIONS = 310_000
    const val ARGON2ID_MEMORY_KIB = 65_536
    const val ARGON2ID_ITERATIONS = 3
    const val ARGON2ID_PARALLELISM = 1
    const val OUTPUT_BYTES = 64

    @Volatile
    var write_v2: Boolean = false

    fun write_version(): Int = if (write_v2) V2 else V1

    fun parse(version: Int?): Int = when (version) {
        null, V1 -> V1
        V2 -> V2
        else -> throw IllegalArgumentException("unsupported kdf version")
    }

    fun derive(password_bytes: ByteArray, salt: ByteArray, version: Int): ByteArray =
        when (parse(version)) {
            V2 -> argon2id(password_bytes, salt)
            else -> pbkdf2(password_bytes, salt)
        }

    private fun pbkdf2(password_bytes: ByteArray, salt: ByteArray): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(password_bytes, salt, PBKDF2_ITERATIONS)
        return (generator.generateDerivedMacParameters(OUTPUT_BYTES * 8) as KeyParameter).key
    }

    private fun argon2id(password_bytes: ByteArray, salt: ByteArray): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(ARGON2ID_MEMORY_KIB)
            .withIterations(ARGON2ID_ITERATIONS)
            .withParallelism(ARGON2ID_PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        val out = ByteArray(OUTPUT_BYTES)
        generator.generateBytes(password_bytes, out)
        return out
    }
}
