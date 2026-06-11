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

import android.util.Base64
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class IdentityKeypair(val public_key: ByteArray, val private_key: ByteArray)

data class RecoveryKeyResult(val mnemonic: String, val bytes: ByteArray)

object CryptoNative {
    private const val pbkdf2_algorithm = "PBKDF2WithHmacSHA256"
    private const val derived_key_bits = 256
    private const val gcm_tag_bits = 128
    private const val gcm_nonce_bytes = 12
    private const val vault_salt_bytes = 16
    private const val vault_iterations = 310000

    private val secure_random = SecureRandom()

    fun derive_password_hash(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray = pbkdf2(password, salt, iterations)

    fun derive_pbkdf2_hash(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray = pbkdf2(password, salt, iterations)

    fun generate_identity_keypair(): ByteArray {
        val keypair = generate_identity_keypair_struct()
        return keypair.public_key + keypair.private_key
    }

    fun sign_with_identity(private_key: ByteArray, message: ByteArray): ByteArray {
        val params = Ed25519PrivateKeyParameters(private_key, 0)
        val signer = Ed25519Signer()
        signer.init(true, params)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verify_with_identity(public_key: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val params = Ed25519PublicKeyParameters(public_key, 0)
        val verifier = Ed25519Signer()
        verifier.init(false, params)
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(signature)
    }

    data class VaultEnvelope(val encrypted_vault: ByteArray, val vault_nonce: ByteArray)

    fun encrypt_vault_with_password(plaintext: ByteArray, password: ByteArray): VaultEnvelope {
        val salt = ByteArray(vault_salt_bytes)
        secure_random.nextBytes(salt)
        val nonce = ByteArray(gcm_nonce_bytes)
        secure_random.nextBytes(nonce)
        val key_bytes = pbkdf2(password, salt, vault_iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key_bytes, "AES"), GCMParameterSpec(gcm_tag_bits, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        key_bytes.fill(0)
        return VaultEnvelope(encrypted_vault = salt + ciphertext, vault_nonce = nonce)
    }

    fun decrypt_vault_with_password(
        encrypted_vault: ByteArray,
        vault_nonce: ByteArray,
        password: ByteArray,
    ): ByteArray {
        require(encrypted_vault.size > vault_salt_bytes) { "encrypted_vault too short" }
        require(vault_nonce.size == gcm_nonce_bytes) { "vault_nonce must be $gcm_nonce_bytes bytes" }
        val salt = encrypted_vault.copyOfRange(0, vault_salt_bytes)
        val ciphertext = encrypted_vault.copyOfRange(vault_salt_bytes, encrypted_vault.size)
        val key_bytes = pbkdf2(password, salt, vault_iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key_bytes, "AES"), GCMParameterSpec(gcm_tag_bits, vault_nonce))
            return cipher.doFinal(ciphertext)
        } finally {
            key_bytes.fill(0)
        }
    }

    @Deprecated("Use encrypt_vault_with_password / decrypt_vault_with_password (matches web client format)")
    fun encrypt_vault(plaintext: ByteArray, password_hash: ByteArray): ByteArray {
        val key_bytes = ensure_key_bytes(password_hash)
        val nonce = ByteArray(gcm_nonce_bytes)
        secure_random.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key_bytes, "AES"), GCMParameterSpec(gcm_tag_bits, nonce))
        val encrypted = cipher.doFinal(plaintext)
        return nonce + encrypted
    }

    @Deprecated("Use decrypt_vault_with_password (matches web client format)")
    fun decrypt_vault(ciphertext: ByteArray, password_hash: ByteArray): ByteArray {
        require(ciphertext.size > gcm_nonce_bytes) { "ciphertext too short" }
        val key_bytes = ensure_key_bytes(password_hash)
        val nonce = ciphertext.copyOfRange(0, gcm_nonce_bytes)
        val payload = ciphertext.copyOfRange(gcm_nonce_bytes, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key_bytes, "AES"), GCMParameterSpec(gcm_tag_bits, nonce))
        return cipher.doFinal(payload)
    }

    private const val storage_key_info = "aster-storage-encryption-key-v1"
    private const val storage_salt_prefix = "aster-hkdf-salt-v1:"
    private const val storage_key_bytes = 32

    fun derive_storage_key(passphrase: ByteArray): ByteArray {
        val prefix = storage_salt_prefix.toByteArray(Charsets.UTF_8)
        val combined = ByteArray(prefix.size + passphrase.size)
        System.arraycopy(prefix, 0, combined, 0, prefix.size)
        System.arraycopy(passphrase, 0, combined, prefix.size, passphrase.size)
        val salt = MessageDigest.getInstance("SHA-256").digest(combined)
        combined.fill(0)
        val out = ByteArray(storage_key_bytes)
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(passphrase, salt, storage_key_info.toByteArray(Charsets.UTF_8)))
        hkdf.generateBytes(out, 0, storage_key_bytes)
        salt.fill(0)
        return out
    }

    data class EncryptedField(val ciphertext: ByteArray, val nonce: ByteArray)

    fun encrypt_field(plaintext: ByteArray, storage_key: ByteArray): EncryptedField {
        require(storage_key.size == storage_key_bytes) { "storage_key must be $storage_key_bytes bytes" }
        val nonce = ByteArray(gcm_nonce_bytes)
        secure_random.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(storage_key, "AES"),
            GCMParameterSpec(gcm_tag_bits, nonce),
        )
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedField(ciphertext = ciphertext, nonce = nonce)
    }

    fun decrypt_field(ciphertext: ByteArray, nonce: ByteArray, storage_key: ByteArray): ByteArray {
        require(storage_key.size == storage_key_bytes) { "storage_key must be $storage_key_bytes bytes" }
        require(nonce.size == gcm_nonce_bytes) { "nonce must be $gcm_nonce_bytes bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(storage_key, "AES"),
            GCMParameterSpec(gcm_tag_bits, nonce),
        )
        return cipher.doFinal(ciphertext)
    }

    fun generate_recovery_bytes(): ByteArray {
        val out = ByteArray(32)
        secure_random.nextBytes(out)
        return out
    }

    fun hash_email(email: String): String {
        val normalized = email.lowercase().trim().toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    @JvmStatic
    external fun set_hash_email_pepper(pepper: ByteArray): Int

    fun configure_hash_email_pepper(pepper: String) {
        val bytes = pepper.toByteArray(Charsets.UTF_8)
        runCatching { set_hash_email_pepper(bytes) }
        bytes.fill(0)
    }

    fun generate_identity_keypair_struct(): IdentityKeypair {
        val seed = ByteArray(32)
        secure_random.nextBytes(seed)
        val private_params = Ed25519PrivateKeyParameters(seed, 0)
        val public_params = private_params.generatePublicKey()
        val priv = private_params.encoded
        val pub = public_params.encoded
        seed.fill(0)
        return IdentityKeypair(pub, priv)
    }

    fun derive_identity_public_key(private_key: ByteArray): ByteArray {
        val params = Ed25519PrivateKeyParameters(private_key, 0)
        return params.generatePublicKey().encoded
    }

    fun fingerprint_hex(public_key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(public_key)
        val hex = StringBuilder(digest.size * 3)
        for (i in digest.indices) {
            if (i > 0 && i % 2 == 0) hex.append(' ')
            val b = digest[i].toInt() and 0xff
            val hi = b ushr 4
            val lo = b and 0x0f
            hex.append(if (hi < 10) ('0' + hi) else ('A' + (hi - 10)))
            hex.append(if (lo < 10) ('0' + lo) else ('A' + (lo - 10)))
        }
        return hex.toString()
    }

    fun generate_recovery_key(): RecoveryKeyResult {
        val bytes = generate_recovery_bytes()
        val mnemonic = bytes_to_mnemonic(bytes)
        return RecoveryKeyResult(mnemonic, bytes)
    }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val password_chars = String(password, Charsets.UTF_8).toCharArray()
        val spec = PBEKeySpec(password_chars, salt, iterations, derived_key_bits)
        try {
            val factory = SecretKeyFactory.getInstance(pbkdf2_algorithm)
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            password_chars.fill(' ')
        }
    }

    private fun ensure_key_bytes(key: ByteArray): ByteArray {
        return when (key.size) {
            16, 24, 32 -> key
            else -> MessageDigest.getInstance("SHA-256").digest(key)
        }
    }

    private fun bytes_to_mnemonic(bytes: ByteArray): String {
        val words = recovery_wordlist
        val sb = StringBuilder()
        for (i in bytes.indices) {
            val idx = bytes[i].toInt() and 0xff
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(words[idx % words.size])
        }
        return sb.toString()
    }
}

private val recovery_wordlist: List<String> = listOf(
    "aster", "apple", "april", "arrow", "atlas", "axiom", "amber", "anvil",
    "bread", "bloom", "breeze", "brook", "brave", "braid", "blaze", "butter",
    "candle", "cedar", "cipher", "cloud", "coral", "crane", "crest", "crown",
    "delta", "desert", "dew", "dove", "dream", "drift", "dune", "dusk",
    "echo", "ember", "emerald", "every", "ever", "evolve", "eager", "earth",
    "falcon", "feather", "fern", "flame", "flint", "flood", "forest", "frost",
    "galaxy", "garden", "gentle", "giant", "glade", "glass", "glow", "granite",
    "harbor", "hazel", "heart", "heath", "herald", "hollow", "honey", "humble",
    "iris", "island", "ivory", "ivy", "indigo", "inlet", "ink", "ion",
    "jade", "jasmine", "jewel", "join", "jolly", "juice", "jungle", "juniper",
    "karma", "keel", "kelp", "kernel", "kestrel", "kite", "knight", "koala",
    "lagoon", "laurel", "leaf", "ledger", "lemon", "lichen", "lily", "lotus",
    "maple", "marble", "marsh", "meadow", "mirror", "mist", "mocha", "moss",
    "nectar", "nest", "night", "nimbus", "nomad", "north", "nova", "novel",
    "oasis", "ocean", "opal", "orbit", "orchid", "otter", "owl", "oyster",
    "pine", "pearl", "petal", "pinecone", "plume", "pond", "poppy", "prairie",
    "quartz", "quest", "quick", "quiet", "quill", "quince", "queen", "quiver",
    "raven", "reed", "ripple", "river", "rose", "ruby", "rune", "rust",
    "sage", "sand", "sapphire", "shore", "silver", "sky", "slate", "spruce",
    "tide", "tiger", "timber", "topaz", "torch", "tranquil", "tulip", "turtle",
    "umbra", "unity", "urban", "urchin", "umber", "usher", "utmost", "uproot",
    "valley", "vapor", "velvet", "verdant", "vessel", "vine", "violet", "vivid",
    "walnut", "water", "weave", "whisper", "willow", "winter", "woods", "wren",
    "xenon", "xylem", "xerox", "yarrow", "yield", "yonder", "young", "yucca",
    "zenith", "zephyr", "zinnia", "zircon", "zodiac", "zone", "zoom", "zucchini",
)

fun zeroize(vararg arrays: ByteArray) {
    for (a in arrays) a.fill(0)
}
