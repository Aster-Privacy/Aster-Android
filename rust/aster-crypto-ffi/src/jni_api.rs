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

use aes_gcm::aead::{Aead, KeyInit, Payload};
use aes_gcm::{Aes256Gcm, Key, Nonce};
use argon2::{Algorithm, Argon2, Params, Version};
use ed25519_dalek::{Signer, SigningKey, VerifyingKey};
use hkdf::Hkdf;
use hmac::{Hmac, Mac};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jstring};
use jni::JNIEnv;
use pbkdf2::pbkdf2_hmac;
use rand::rngs::OsRng;
use rand::RngCore;
use sha2::{Digest, Sha256};
use std::sync::{Mutex, Once};
use zeroize::{Zeroize, Zeroizing};

static HASH_EMAIL_PEPPER_CELL: Mutex<Option<Zeroizing<Vec<u8>>>> = Mutex::new(None);
static HASH_EMAIL_FALLBACK_WARNED: Once = Once::new();

fn current_hash_email_pepper() -> Option<Zeroizing<Vec<u8>>> {
    let guard = HASH_EMAIL_PEPPER_CELL
        .lock()
        .unwrap_or_else(|p| p.into_inner());
    if let Some(p) = guard.as_ref() {
        if !p.is_empty() {
            return Some(Zeroizing::new(p.to_vec()));
        }
    }
    drop(guard);
    match std::env::var(HASH_EMAIL_PEPPER_ENV) {
        Ok(s) if !s.is_empty() => Some(Zeroizing::new(s.into_bytes())),
        _ => None,
    }
}

const VAULT_SALT_LEN: usize = 16;
const VAULT_NONCE_LEN: usize = 12;
const ARGON2_MIN_ITERATIONS: u32 = 3;
const ARGON2_MAX_ITERATIONS: u32 = 100;
const ARGON2_MEMORY_KIB: u32 = 64 * 1024;
const PBKDF2_LEGACY_MIN_ITERATIONS: u32 = 100_000;
const PBKDF2_NEW_MIN_ITERATIONS: u32 = 600_000;
const PBKDF2_MAX_ITERATIONS: u32 = 10_000_000;
const HASH_EMAIL_PEPPER_ENV: &str = "ASTER_HASH_EMAIL_PEPPER";
const VAULT_KEY_INPUT_MIN_LEN: usize = 32;
const VAULT_VERSION_V1: u8 = 0x01;

fn jint_to_positive_u32(v: jni::sys::jint) -> Option<u32> {
    if v <= 0 {
        None
    } else {
        Some(v as u32)
    }
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_derive_1password_1hash<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    password: JByteArray<'local>,
    salt: JByteArray<'local>,
    iterations: jni::sys::jint,
) -> jbyteArray {
    let iterations_u32 = match jint_to_positive_u32(iterations) {
        Some(n) if n >= ARGON2_MIN_ITERATIONS && n <= ARGON2_MAX_ITERATIONS => n,
        _ => return std::ptr::null_mut(),
    };
    let password_bytes = match env.convert_byte_array(&password) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };
    let salt_bytes = match env.convert_byte_array(&salt) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };
    if password_bytes.is_empty() || salt_bytes.len() < 8 {
        return std::ptr::null_mut();
    }

    let params = match Params::new(ARGON2_MEMORY_KIB, iterations_u32, 1, Some(32)) {
        Ok(p) => p,
        Err(_) => return std::ptr::null_mut(),
    };
    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    let mut hash = Zeroizing::new(vec![0u8; 32]);
    if argon2
        .hash_password_into(&password_bytes, &salt_bytes, &mut hash)
        .is_err()
    {
        return std::ptr::null_mut();
    }

    match env.byte_array_from_slice(&hash) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_derive_1pbkdf2_1hash<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    password: JByteArray<'local>,
    salt: JByteArray<'local>,
    iterations: jni::sys::jint,
) -> jbyteArray {
    let iterations_u32 = match jint_to_positive_u32(iterations) {
        Some(n) if n >= PBKDF2_LEGACY_MIN_ITERATIONS && n <= PBKDF2_MAX_ITERATIONS => n,
        _ => return std::ptr::null_mut(),
    };
    let _ = PBKDF2_NEW_MIN_ITERATIONS;
    let password_bytes = match env.convert_byte_array(&password) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };
    let salt_bytes = match env.convert_byte_array(&salt) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };
    if password_bytes.is_empty() || salt_bytes.len() < 8 {
        return std::ptr::null_mut();
    }

    let mut out = Zeroizing::new([0u8; 32]);
    pbkdf2_hmac::<Sha256>(&password_bytes, &salt_bytes, iterations_u32, out.as_mut_slice());

    match env.byte_array_from_slice(out.as_slice()) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_generate_1identity_1keypair<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jbyteArray {
    let mut csprng = OsRng;
    let signing = SigningKey::generate(&mut csprng);
    let verifying: VerifyingKey = signing.verifying_key();
    let mut out = Vec::with_capacity(64);
    out.extend_from_slice(verifying.as_bytes());
    out.extend_from_slice(&signing.to_bytes());
    let result = match env.byte_array_from_slice(&out) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    };
    out.zeroize();
    result
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_sign_1with_1identity<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    private_key: JByteArray<'local>,
    message: JByteArray<'local>,
) -> jbyteArray {
    let priv_bytes = match env.convert_byte_array(&private_key) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };
    if priv_bytes.len() != 32 {
        return std::ptr::null_mut();
    }
    let msg_bytes = match env.convert_byte_array(&message) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };
    let mut key_arr = Zeroizing::new([0u8; 32]);
    key_arr.copy_from_slice(&priv_bytes[..32]);
    let signing = SigningKey::from_bytes(&key_arr);
    let sig = signing.sign(&msg_bytes);
    match env.byte_array_from_slice(&sig.to_bytes()) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn derive_vault_key(password: &[u8], salt: &[u8]) -> Option<Zeroizing<[u8; 32]>> {
    if password.len() < VAULT_KEY_INPUT_MIN_LEN {
        return None;
    }
    let hk = Hkdf::<Sha256>::new(Some(salt), password);
    let mut okm = Zeroizing::new([0u8; 32]);
    hk.expand(b"aster-vault-v1", okm.as_mut_slice()).ok()?;
    Some(okm)
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_encrypt_1vault<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    plaintext: JByteArray<'local>,
    password_hash: JByteArray<'local>,
) -> jbyteArray {
    let plaintext_bytes = match env.convert_byte_array(&plaintext) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };
    let pw_bytes = match env.convert_byte_array(&password_hash) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };

    let mut salt = [0u8; VAULT_SALT_LEN];
    let mut nonce_bytes = [0u8; VAULT_NONCE_LEN];
    OsRng.fill_bytes(&mut salt);
    OsRng.fill_bytes(&mut nonce_bytes);

    let key_bytes = match derive_vault_key(&pw_bytes, &salt) {
        Some(k) => k,
        None => return std::ptr::null_mut(),
    };
    let key = Key::<Aes256Gcm>::from_slice(key_bytes.as_slice());
    let cipher = Aes256Gcm::new(key);
    let nonce = Nonce::from_slice(&nonce_bytes);

    let mut aad = Vec::with_capacity(1 + VAULT_SALT_LEN);
    aad.push(VAULT_VERSION_V1);
    aad.extend_from_slice(&salt);

    let ct = match cipher.encrypt(
        nonce,
        Payload {
            msg: plaintext_bytes.as_slice(),
            aad: &aad,
        },
    ) {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let mut combined = Vec::with_capacity(1 + VAULT_SALT_LEN + VAULT_NONCE_LEN + ct.len());
    combined.push(VAULT_VERSION_V1);
    combined.extend_from_slice(&salt);
    combined.extend_from_slice(&nonce_bytes);
    combined.extend_from_slice(&ct);

    match env.byte_array_from_slice(&combined) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_decrypt_1vault<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    encrypted: JByteArray<'local>,
    password_hash: JByteArray<'local>,
) -> jbyteArray {
    let combined = match env.convert_byte_array(&encrypted) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };
    let pw_bytes = match env.convert_byte_array(&password_hash) {
        Ok(b) => Zeroizing::new(b),
        Err(_) => return std::ptr::null_mut(),
    };

    if combined.len() >= 1 + VAULT_SALT_LEN + VAULT_NONCE_LEN + 16 && combined[0] == VAULT_VERSION_V1 {
        let salt = &combined[1..1 + VAULT_SALT_LEN];
        let nonce_bytes = &combined[1 + VAULT_SALT_LEN..1 + VAULT_SALT_LEN + VAULT_NONCE_LEN];
        let ct = &combined[1 + VAULT_SALT_LEN + VAULT_NONCE_LEN..];

        if let Some(key_bytes) = derive_vault_key(&pw_bytes, salt) {
            let key = Key::<Aes256Gcm>::from_slice(key_bytes.as_slice());
            let cipher = Aes256Gcm::new(key);
            let nonce = Nonce::from_slice(nonce_bytes);

            let mut aad = Vec::with_capacity(1 + VAULT_SALT_LEN);
            aad.push(VAULT_VERSION_V1);
            aad.extend_from_slice(salt);

            if let Ok(pt) = cipher.decrypt(
                nonce,
                Payload {
                    msg: ct,
                    aad: &aad,
                },
            ) {
                let pt_z = Zeroizing::new(pt);
                return match env.byte_array_from_slice(&pt_z) {
                    Ok(arr) => arr.into_raw(),
                    Err(_) => std::ptr::null_mut(),
                };
            }
        }
    }

    if combined.len() < VAULT_SALT_LEN + VAULT_NONCE_LEN + 16 {
        return std::ptr::null_mut();
    }
    let salt = &combined[..VAULT_SALT_LEN];
    let nonce_bytes = &combined[VAULT_SALT_LEN..VAULT_SALT_LEN + VAULT_NONCE_LEN];
    let ct = &combined[VAULT_SALT_LEN + VAULT_NONCE_LEN..];

    let key_bytes = match derive_vault_key(&pw_bytes, salt) {
        Some(k) => k,
        None => return std::ptr::null_mut(),
    };
    let key = Key::<Aes256Gcm>::from_slice(key_bytes.as_slice());
    let cipher = Aes256Gcm::new(key);
    let nonce = Nonce::from_slice(nonce_bytes);

    let pt = match cipher.decrypt(nonce, ct) {
        Ok(p) => Zeroizing::new(p),
        Err(_) => return std::ptr::null_mut(),
    };

    match env.byte_array_from_slice(&pt) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_generate_1recovery_1bytes<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jbyteArray {
    let mut out = [0u8; 32];
    OsRng.fill_bytes(&mut out);
    match env.byte_array_from_slice(&out) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_hash_1email<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    email: JString<'local>,
) -> jstring {
    let email_str: String = match env.get_string(&email) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let normalized = email_str.trim().to_lowercase();
    use base64::Engine;
    let b64 = match current_hash_email_pepper() {
        Some(pepper) => {
            let mut mac = match <Hmac<Sha256> as Mac>::new_from_slice(pepper.as_slice()) {
                Ok(m) => m,
                Err(_) => return std::ptr::null_mut(),
            };
            mac.update(normalized.as_bytes());
            let digest = mac.finalize().into_bytes();
            base64::engine::general_purpose::STANDARD.encode(digest)
        }
        None => {
            HASH_EMAIL_FALLBACK_WARNED.call_once(|| {
                eprintln!(
                    "aster-crypto-ffi: hash_email called without pepper configured; refusing to hash"
                );
            });
            return std::ptr::null_mut();
        }
    };
    match env.new_string(b64) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_astermail_android_crypto_CryptoNative_set_1hash_1email_1pepper<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pepper: JByteArray<'local>,
) -> jint {
    let bytes = match env.convert_byte_array(&pepper) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    let stored = Zeroizing::new(bytes);
    let mut guard = HASH_EMAIL_PEPPER_CELL
        .lock()
        .unwrap_or_else(|p| p.into_inner());
    *guard = Some(stored);
    1
}
