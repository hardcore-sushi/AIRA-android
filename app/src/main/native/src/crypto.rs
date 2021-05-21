use std::{convert::TryInto, fmt::Display};
use hkdf::Hkdf;
use sha2::Sha384;
use hmac::{Hmac, NewMac, Mac};
use scrypt::{scrypt, Params};
use rand::{RngCore, rngs::OsRng};
use aes_gcm::{aead::Aead, NewAead, Nonce};
use aes_gcm_siv::Aes256GcmSiv;
use zeroize::Zeroize;

pub const HASH_OUTPUT_LEN: usize = 48; //SHA384
const KEY_LEN: usize = 16;
pub const IV_LEN: usize = 12;
pub const AES_TAG_LEN: usize = 16;
pub const SALT_LEN: usize = 32;
const PASSWORD_HASH_LEN: usize = 32;
pub const MASTER_KEY_LEN: usize = 32;

fn hkdf_expand_label(key: &[u8], label: &str, context: Option<&[u8]>, okm: &mut [u8]) {
    let hkdf = Hkdf::<Sha384>::from_prk(key).unwrap();
    //can't set info conditionnally because of different array size
    match context {
        Some(context) => {
            let info = [&(label.len() as u32).to_be_bytes(), label.as_bytes(), &(context.len() as u32).to_be_bytes(), context];
            hkdf.expand_multi_info(&info, okm).unwrap();
        }
        None => {
            let info = [&(label.len() as u32).to_be_bytes(), label.as_bytes()];
            hkdf.expand_multi_info(&info, okm).unwrap();
        }
    };    
}

fn get_labels(handshake: bool, i_am_bob: bool) -> (String, String) {
    let mut label = if handshake {
        "handshake"
    } else {
        "application"
    }.to_owned();
    label += "_i_am_";
    let local_label = label.clone() + if i_am_bob {
        "bob"
    } else {
        "alice"
    };
    let peer_label = label + if i_am_bob {
        "alice"
    } else {
        "bob"
    };
    (local_label, peer_label)
}

pub struct HandshakeKeys {
    pub local_key: [u8; KEY_LEN],
    pub local_iv: [u8; IV_LEN],
    pub local_handshake_traffic_secret: [u8; HASH_OUTPUT_LEN],
    pub peer_key: [u8; KEY_LEN],
    pub peer_iv: [u8; IV_LEN],
    pub peer_handshake_traffic_secret: [u8; HASH_OUTPUT_LEN],
    pub handshake_secret: [u8; HASH_OUTPUT_LEN],
}

impl HandshakeKeys {
    pub fn derive_keys(shared_secret: [u8; 32], handshake_hash: [u8; HASH_OUTPUT_LEN], i_am_bob: bool) -> HandshakeKeys {
        let (handshake_secret, _) = Hkdf::<Sha384>::extract(None, &shared_secret);

        let (local_label, peer_label) = get_labels(true, i_am_bob);

        let mut local_handshake_traffic_secret = [0; HASH_OUTPUT_LEN];
        hkdf_expand_label(handshake_secret.as_slice(), &local_label, Some(&handshake_hash), &mut local_handshake_traffic_secret);

        let mut peer_handshake_traffic_secret = [0; HASH_OUTPUT_LEN];
        hkdf_expand_label(handshake_secret.as_slice(), &peer_label, Some(&handshake_hash), &mut peer_handshake_traffic_secret);

        let mut local_handshake_key = [0; KEY_LEN];
        hkdf_expand_label(&local_handshake_traffic_secret, "key", None, &mut local_handshake_key);
        let mut local_handshake_iv = [0; IV_LEN];
        hkdf_expand_label(&local_handshake_traffic_secret, "iv", None, &mut local_handshake_iv);
    
        let mut peer_handshake_key = [0; KEY_LEN];
        hkdf_expand_label(&peer_handshake_traffic_secret, "key", None, &mut peer_handshake_key);
        let mut peer_handshake_iv = [0; IV_LEN];
        hkdf_expand_label(&peer_handshake_traffic_secret,"iv", None, &mut peer_handshake_iv);

        HandshakeKeys {
            local_key: local_handshake_key,
            local_iv: local_handshake_iv,
            local_handshake_traffic_secret: local_handshake_traffic_secret,
            peer_key: peer_handshake_key,
            peer_iv: peer_handshake_iv,
            peer_handshake_traffic_secret: peer_handshake_traffic_secret,
            handshake_secret: handshake_secret.as_slice().try_into().unwrap(),
        }
    }
}

pub struct ApplicationKeys {
    pub local_key: [u8; KEY_LEN],
    pub local_iv: [u8; IV_LEN],
    pub peer_key: [u8; KEY_LEN],
    pub peer_iv: [u8; IV_LEN],
}

impl ApplicationKeys {
    pub fn derive_keys(handshake_secret: [u8; HASH_OUTPUT_LEN], handshake_hash: [u8; HASH_OUTPUT_LEN], i_am_bob: bool) -> ApplicationKeys {
        let mut derived_secret = [0; HASH_OUTPUT_LEN];
        hkdf_expand_label(&handshake_secret, "derived", None, &mut derived_secret);
        let (master_secret, _) = Hkdf::<Sha384>::extract(Some(&derived_secret), b"");

        let (local_label, peer_label) = get_labels(false, i_am_bob);

        let mut local_application_traffic_secret = [0; HASH_OUTPUT_LEN];
        hkdf_expand_label(&master_secret, &local_label, Some(&handshake_hash), &mut local_application_traffic_secret);
    
        let mut peer_application_traffic_secret = [0; HASH_OUTPUT_LEN];
        hkdf_expand_label(&master_secret, &peer_label, Some(&handshake_hash), &mut peer_application_traffic_secret);

        let mut local_application_key = [0; KEY_LEN];
        hkdf_expand_label(&local_application_traffic_secret, "key", None, &mut local_application_key);
        let mut local_application_iv = [0; IV_LEN];
        hkdf_expand_label(&local_application_traffic_secret, "iv", None, &mut local_application_iv);
    
        let mut peer_application_key = [0; KEY_LEN];
        hkdf_expand_label(&peer_application_traffic_secret, "key", None, &mut peer_application_key);
        let mut peer_application_iv = [0; IV_LEN];
        hkdf_expand_label(&peer_application_traffic_secret,"iv", None, &mut peer_application_iv);

        ApplicationKeys {
            local_key: local_application_key,
            local_iv: local_application_iv,
            peer_key: peer_application_key,
            peer_iv: peer_application_iv,
        }
    }
}

pub fn compute_handshake_finished(local_handshake_traffic_secret: [u8; HASH_OUTPUT_LEN], handshake_hash: [u8; HASH_OUTPUT_LEN]) -> [u8; HASH_OUTPUT_LEN] {
    let mut finished_key = [0; HASH_OUTPUT_LEN];
    hkdf_expand_label(&local_handshake_traffic_secret, "finished", None, &mut finished_key);
    let mut hmac = Hmac::<Sha384>::new_from_slice(&finished_key).unwrap();
    hmac.update(&handshake_hash);
    hmac.finalize().into_bytes().as_slice().try_into().unwrap()
}

pub fn verify_handshake_finished(peer_handshake_finished: [u8; HASH_OUTPUT_LEN], peer_handshake_traffic_secret: [u8; HASH_OUTPUT_LEN], handshake_hash: [u8; HASH_OUTPUT_LEN]) -> bool {
    let mut peer_finished_key = [0; HASH_OUTPUT_LEN];
    hkdf_expand_label(&peer_handshake_traffic_secret, "finished", None, &mut peer_finished_key);
    let mut hmac = Hmac::<Sha384>::new_from_slice(&peer_finished_key).unwrap();
    hmac.update(&handshake_hash);
    hmac.verify(&peer_handshake_finished).is_ok()
}



pub fn generate_fingerprint(public_key: &[u8]) -> String {
    let mut raw_fingerprint = [0; 16];
    Hkdf::<Sha384>::new(None, public_key).expand(&[], &mut raw_fingerprint).unwrap();
    hex::encode(raw_fingerprint).to_uppercase()
}



pub fn generate_master_key() -> [u8; MASTER_KEY_LEN] {
    let mut master_key = [0; MASTER_KEY_LEN];
    OsRng.fill_bytes(&mut master_key);
    master_key
}

pub fn encrypt_data(data: &[u8], master_key: &[u8]) -> Result<Vec<u8>, CryptoError> {
    if master_key.len() != MASTER_KEY_LEN {
        return Err(CryptoError::InvalidLength);
    }
    let cipher = Aes256GcmSiv::new_from_slice(master_key).unwrap();
    let mut iv = [0; IV_LEN];
    OsRng.fill_bytes(&mut iv); //use it for IV for now
    let mut cipher_text = iv.to_vec();
    cipher_text.extend(cipher.encrypt(Nonce::from_slice(&iv), data).unwrap());
    Ok(cipher_text)
}

#[derive(Debug, PartialEq, Eq)]
pub enum CryptoError {
    DecryptionFailed,
    InvalidLength
}

impl Display for CryptoError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            CryptoError::DecryptionFailed => "Decryption failed",
            CryptoError::InvalidLength => "Invalid length",
        })
    }
}

pub fn decrypt_data(data: &[u8], master_key: &[u8]) -> Result<Vec<u8>, CryptoError> {
    if data.len() <= IV_LEN || master_key.len() != MASTER_KEY_LEN {
        return Err(CryptoError::InvalidLength);
    }
    let cipher = Aes256GcmSiv::new_from_slice(master_key).unwrap();
    match cipher.decrypt(Nonce::from_slice(&data[..IV_LEN]), &data[IV_LEN..]) {
        Ok(data) => {
            Ok(data)
        },
        Err(_) => Err(CryptoError::DecryptionFailed)
    }
}

fn scrypt_params() -> Params {
    Params::new(16, 8, 1).unwrap()
}

pub fn encrypt_master_key(mut master_key: [u8; MASTER_KEY_LEN], password: &[u8]) -> (
    [u8; SALT_LEN], //salt
    [u8; IV_LEN+MASTER_KEY_LEN+AES_TAG_LEN] //encrypted masterkey with IV
) {
    let mut salt = [0; SALT_LEN];
    OsRng.fill_bytes(&mut salt);
    let mut password_hash = [0; PASSWORD_HASH_LEN];
    scrypt(password, &salt, &scrypt_params(), &mut password_hash).unwrap();
    let mut output = [0; IV_LEN+MASTER_KEY_LEN+AES_TAG_LEN];
    OsRng.fill_bytes(&mut output); //use it for IV for now
    let cipher = Aes256GcmSiv::new_from_slice(&password_hash).unwrap();
    let encrypted_master_key = cipher.encrypt(Nonce::from_slice(&output[..IV_LEN]), master_key.as_ref()).unwrap();
    master_key.zeroize();
    password_hash.zeroize();
    encrypted_master_key.into_iter().enumerate().for_each(|i|{
        output[IV_LEN+i.0] = i.1; //append encrypted master key to IV
    });
    (salt, output)
}

pub fn decrypt_master_key(encrypted_master_key: &[u8], password: &[u8], salt: &[u8]) -> Result<[u8; MASTER_KEY_LEN], CryptoError> {
    if encrypted_master_key.len() != IV_LEN+MASTER_KEY_LEN+AES_TAG_LEN || salt.len() != SALT_LEN {
        return Err(CryptoError::InvalidLength);
    }
    let mut password_hash = [0; PASSWORD_HASH_LEN];
    scrypt(password, salt, &scrypt_params(), &mut password_hash).unwrap();
    let cipher = Aes256GcmSiv::new_from_slice(&password_hash).unwrap();
    let result = match cipher.decrypt(Nonce::from_slice(&encrypted_master_key[..IV_LEN]), &encrypted_master_key[IV_LEN..]) {
        Ok(master_key) => {
            if master_key.len() == MASTER_KEY_LEN {
                Ok(master_key.try_into().unwrap())
            } else {
                return Err(CryptoError::InvalidLength)
            }
        },
        Err(_) => Err(CryptoError::DecryptionFailed)
    };
    password_hash.zeroize();
    result
}
