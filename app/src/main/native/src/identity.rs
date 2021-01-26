use std::{convert::TryInto, path::Path};
use crypto::CryptoError;
use ed25519_dalek::{Keypair, Signer, SIGNATURE_LENGTH, PUBLIC_KEY_LENGTH};
use rusqlite::{Connection, params};
use utils::to_uuid_bytes;
use uuid::Uuid;
use zeroize::Zeroize;
use crate::{crypto, key_value_table::KeyValueTable, print_error, utils};

const DB_NAME: &str = "AIRA.db";
const MAIN_TABLE: &str = "main";
const CONTACTS_TABLE: &str = "contacts";
const FILES_TABLE: &str = "files";

const DATABASE_CORRUPED_ERROR: &str = "Database corrupted";

struct DBKeys;
impl<'a> DBKeys {
    pub const NAME: &'a str = "name";
    pub const KEYPAIR: &'a str = "keypair";
    pub const SALT: &'a str = "salt";
    pub const MASTER_KEY: &'a str = "master_key";
}

fn bool_to_byte(b: bool) -> u8 {
    if b { 75 } else { 30 } //completely arbitrary values
}

fn byte_to_bool(b: u8) -> Result<bool, ()> {
    if b == 75 {
        Ok(true)
    } else if b == 30 {
        Ok(false)
    } else {
        Err(())
    }
}

fn get_database_path(database_folder: &str) -> String {
    Path::new(database_folder).join(DB_NAME).to_str().unwrap().to_owned()
}

struct EncryptedIdentity {
    name: String,
    encrypted_keypair: Vec<u8>,
    salt: Vec<u8>,
    encrypted_master_key: Vec<u8>
}

pub struct Contact {
    pub uuid: Uuid,
    pub public_key: [u8; PUBLIC_KEY_LENGTH],
    pub name: String,
    pub verified: bool,
    pub seen: bool,
}

pub struct Identity {
    pub name: String,
    keypair: Keypair,
    pub master_key: [u8; crypto::MASTER_KEY_LEN],
    database_folder: String,
}

impl Identity {

    pub fn sign(&self, input: &[u8]) -> [u8; SIGNATURE_LENGTH] {
        self.keypair.sign(input).to_bytes()
    }
    
    pub fn get_public_key(&self) -> [u8; PUBLIC_KEY_LENGTH] {
        self.keypair.public.to_bytes()
    }

    fn get_database_path(&self) -> String {
        get_database_path(&self.database_folder)
    }

    pub fn add_contact(&self, name: String, public_key: [u8; PUBLIC_KEY_LENGTH]) -> Result<Contact, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        db.execute(&("CREATE TABLE IF NOT EXISTS ".to_owned()+CONTACTS_TABLE+"(uuid BLOB PRIMARY KEY, name BLOB, key BLOB, verified BLOB, seen BLOB)"), [])?;
        let contact_uuid = Uuid::new_v4();
        let encrypted_name = crypto::encrypt_data(name.as_bytes(), &self.master_key).unwrap();
        let encrypted_public_key = crypto::encrypt_data(&public_key, &self.master_key).unwrap();
        let encrypted_verified = crypto::encrypt_data(&[bool_to_byte(false)], &self.master_key).unwrap();
        let encrypted_seen = crypto::encrypt_data(&[bool_to_byte(true)], &self.master_key).unwrap();
        db.execute(&("INSERT INTO ".to_owned()+CONTACTS_TABLE+" (uuid, name, key, verified, seen) VALUES (?1, ?2, ?3, ?4, ?5)"), params![&contact_uuid.as_bytes()[..], encrypted_name, encrypted_public_key, encrypted_verified, encrypted_seen])?;
        Ok(Contact {
            uuid: contact_uuid,
            public_key: public_key,
            name: name,
            verified: false,
            seen: true,
        })
    }

    pub fn remove_contact(&self, uuid: &Uuid) -> Result<usize, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        self.delete_conversation(uuid)?;
        db.execute(&("DELETE FROM ".to_owned()+CONTACTS_TABLE+" WHERE uuid=?"), [&uuid.as_bytes()[..]])
    }

    pub fn set_verified(&self, uuid: &Uuid) -> Result<usize, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        let encrypted_verified = crypto::encrypt_data(&[bool_to_byte(true)], &self.master_key).unwrap();
        db.execute(&format!("UPDATE {} SET verified=?1 WHERE uuid=?2", CONTACTS_TABLE), [encrypted_verified.as_slice(), &uuid.as_bytes()[..]])
    }

    pub fn change_contact_name(&self, uuid: &Uuid, new_name: &str) -> Result<usize, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        let encrypted_name = crypto::encrypt_data(new_name.as_bytes(), &self.master_key).unwrap();
        db.execute(&format!("UPDATE {} SET name=?1 WHERE uuid=?2", CONTACTS_TABLE), [encrypted_name.as_slice(), uuid.as_bytes()])
    }

    pub fn set_contact_seen(&self, uuid: &Uuid, seen: bool) -> Result<usize, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        let encrypted_seen = crypto::encrypt_data(&[bool_to_byte(seen)], &self.master_key).unwrap();
        db.execute(&format!("UPDATE {} SET seen=?1 WHERE uuid=?2", CONTACTS_TABLE), [encrypted_seen.as_slice(), uuid.as_bytes()])
    }

    pub fn load_contacts(&self) -> Option<Vec<Contact>> {
        match Connection::open(self.get_database_path()) {
            Ok(db) => {
                match db.prepare(&("SELECT uuid, name, key, verified, seen FROM ".to_owned()+CONTACTS_TABLE)) {
                    Ok(mut stmt) => {
                        let mut rows = stmt.query([]).unwrap();
                        let mut contacts = Vec::new();
                        while let Some(row) = rows.next().unwrap() {
                            let encrypted_public_key: Vec<u8> = row.get(2).unwrap();
                            match crypto::decrypt_data(encrypted_public_key.as_slice(), &self.master_key) {
                                Ok(public_key) => {
                                    if public_key.len() == PUBLIC_KEY_LENGTH {
                                        let encrypted_name: Vec<u8> = row.get(1).unwrap();
                                        match crypto::decrypt_data(encrypted_name.as_slice(), &self.master_key) {
                                            Ok(name) => {
                                                let encrypted_verified: Vec<u8> = row.get(3).unwrap();
                                                match crypto::decrypt_data(encrypted_verified.as_slice(), &self.master_key) {
                                                    Ok(verified) => {
                                                        let encrypted_seen: Vec<u8> = row.get(4).unwrap();
                                                        match crypto::decrypt_data(encrypted_seen.as_slice(), &self.master_key) {
                                                            Ok(seen) => {
                                                                let uuid: Vec<u8> = row.get(0).unwrap();
                                                                match to_uuid_bytes(&uuid) {
                                                                    Some(uuid_bytes) => {
                                                                        contacts.push(Contact {
                                                                            uuid: Uuid::from_bytes(uuid_bytes),
                                                                            public_key: public_key.try_into().unwrap(),
                                                                            name: std::str::from_utf8(name.as_slice()).unwrap().to_owned(),
                                                                            verified: byte_to_bool(verified[0]).unwrap(),
                                                                            seen: byte_to_bool(seen[0]).unwrap(),
                                                                        })
                                                                    }
                                                                    None => {}
                                                                }
                                                            }
                                                            Err(e) => print_error!(e)
                                                        }
                                                    }
                                                    Err(e) => print_error!(e)
                                                }
                                            }
                                            Err(e) => print_error!(e)
                                        }
                                    } else {
                                        print_error!("Invalid public key length: database corrupted");
                                    }
                                }
                                Err(e) => print_error!(e)
                            }
                        }
                        Some(contacts)
                    }
                    Err(e) => {
                        print_error!(e);
                        None
                    }
                }            
            }
            Err(e) => {
                print_error!(e);
                None
            }
        }
    }

    pub fn clear_temporary_files(&self) -> Result<usize, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        db.execute(&format!("DELETE FROM {} WHERE contact_uuid IS NULL", FILES_TABLE), [])
    }

    pub fn load_file(&self, uuid: Uuid) -> Option<Vec<u8>> {
        match Connection::open(self.get_database_path()) {
            Ok(db) => {
                match db.prepare(&format!("SELECT uuid, data FROM \"{}\"", FILES_TABLE)) {
                    Ok(mut stmt) => {
                        let mut rows = stmt.query([]).unwrap();
                        while let Some(row) = rows.next().unwrap() {
                            let encrypted_uuid: Vec<u8> = row.get(0).unwrap();
                            match crypto::decrypt_data(encrypted_uuid.as_slice(), &self.master_key){
                                Ok(test_uuid) => {
                                    if test_uuid == uuid.as_bytes() {
                                        let encrypted_data: Vec<u8> = row.get(1).unwrap();
                                        match crypto::decrypt_data(encrypted_data.as_slice(), &self.master_key) {
                                            Ok(data) => return Some(data),
                                            Err(e) => print_error!(e)
                                        }
                                    }
                                }
                                Err(e) => print_error!(e)
                            }
                        }
                        None
                    }
                    Err(e) => {
                        print_error!(e);
                        None
                    }
                }
            }
            Err(e) => {
                print_error!(e);
                None
            }
        }
    }

    pub fn store_file(&self, contact_uuid: Option<Uuid>, data: &[u8]) -> Result<Uuid, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        db.execute(&format!("CREATE TABLE IF NOT EXISTS \"{}\" (contact_uuid BLOB, uuid BLOB, data BLOB)", FILES_TABLE), [])?;
        let file_uuid = Uuid::new_v4();
        let encrypted_uuid = crypto::encrypt_data(file_uuid.as_bytes(), &self.master_key).unwrap();
        let encrypted_data = crypto::encrypt_data(data, &self.master_key).unwrap();
        let query = format!("INSERT INTO \"{}\" (contact_uuid, uuid, data) VALUES (?1, ?2, ?3)", FILES_TABLE);
        match contact_uuid {
            Some(uuid) => db.execute(&query, params![&uuid.as_bytes()[..], &encrypted_uuid, &encrypted_data])?,
            None => db.execute(&query, params![None as Option<Vec<u8>>, &encrypted_uuid, &encrypted_data])?
        };
        Ok(file_uuid)
    }

    pub fn store_msg(&self, contact_uuid: &Uuid, outgoing: bool, data: &[u8]) -> Result<usize, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        db.execute(&format!("CREATE TABLE IF NOT EXISTS \"{}\" (outgoing BLOB, data BLOB)", contact_uuid), [])?;
        let outgoing_byte: u8 = bool_to_byte(outgoing);
        let encrypted_outgoing = crypto::encrypt_data(&[outgoing_byte], &self.master_key).unwrap();
        let encrypted_data = crypto::encrypt_data(data, &self.master_key).unwrap();
        db.execute(&format!("INSERT INTO \"{}\" (outgoing, data) VALUES (?1, ?2)", contact_uuid), params![encrypted_outgoing, encrypted_data])
    }

    pub fn load_msgs(&self, contact_uuid: &Uuid, offset: usize, mut count: usize) -> Option<Vec<(bool, Vec<u8>)>> {
        match Connection::open(self.get_database_path()) {
            Ok(db) => {
                match db.prepare(&format!("SELECT count(*) FROM \"{}\"", contact_uuid)) {
                    Ok(mut stmt) => {
                        let mut rows = stmt.query([]).unwrap();
                        let row = rows.next().unwrap();
                        if row.is_some() {
                            let total: usize = row.unwrap().get(0).unwrap();
                            if offset >= total {
                                print_error!("Offset larger than total numbers of rows");
                                None
                            } else {
                                if offset+count >= total {
                                    count = total-offset;
                                }
                                match db.prepare(&format!("SELECT outgoing, data FROM \"{}\" LIMIT {} OFFSET {}", contact_uuid, count, total-offset-count)) {
                                    Ok(mut stmt) => {
                                        let mut rows = stmt.query([]).unwrap();
                                        let mut msgs = Vec::new();
                                        while let Some(row) = rows.next().unwrap() {
                                            let encrypted_outgoing: Vec<u8> = row.get(0).unwrap();
                                            match crypto::decrypt_data(encrypted_outgoing.as_slice(), &self.master_key){
                                                Ok(outgoing) => {
                                                    match byte_to_bool(outgoing[0]) {
                                                        Ok(outgoing) => {
                                                            let encrypted_data: Vec<u8> = row.get(1).unwrap();
                                                            match crypto::decrypt_data(encrypted_data.as_slice(), &self.master_key) {
                                                                Ok(data) => {
                                                                    msgs.push(
                                                                        (
                                                                            outgoing,
                                                                            data
                                                                        )
                                                                    )
                                                                },
                                                                Err(e) => print_error!(e)
                                                            }
                                                        }
                                                        Err(_) => {}
                                                    }
                                                    
                                                }
                                                Err(e) => print_error!(e)
                                            }
                                        }
                                        Some(msgs)
                                    }
                                    Err(e) => {
                                        print_error!(e);
                                        None
                                    }
                                }
                            }
                        } else {
                            None
                        }
                    }
                    Err(e) => {
                        print_error!(e);
                        None
                    }
                }
            }
            Err(e) => {
                print_error!(e);
                None
            }
        }
    }
    
    #[allow(unused_must_use)]
    pub fn delete_conversation(&self, contact_uuid: &Uuid) -> Result<usize, rusqlite::Error> {
        let db = Connection::open(self.get_database_path())?;
        db.execute(&format!("DELETE FROM {} WHERE contact_uuid=?", FILES_TABLE), &[&contact_uuid.as_bytes()[..]]);
        db.execute(&format!("DROP TABLE IF EXISTS \"{}\"", contact_uuid), [])
    }

    pub fn change_name(&mut self, new_name: String) -> Result<usize, rusqlite::Error> {
        let db = KeyValueTable::new(&self.get_database_path(), MAIN_TABLE)?;
        let result = db.update(DBKeys::NAME, new_name.as_bytes());
        if result.is_ok() {
            self.name = new_name;
        }
        result
    }

    pub fn zeroize(&mut self){
        self.master_key.zeroize();
        self.keypair.secret.zeroize();
    }

    fn load_encrypted_identity(database_folder: &str) -> Result<EncryptedIdentity, rusqlite::Error> {
        let db = KeyValueTable::new(&get_database_path(database_folder), MAIN_TABLE)?;
        let name = db.get(DBKeys::NAME)?;
        let encrypted_keypair = db.get(DBKeys::KEYPAIR)?;
        let salt = db.get(DBKeys::SALT)?;
        let encrypted_master_key = db.get(DBKeys::MASTER_KEY)?;
        Ok(EncryptedIdentity {
            name: std::str::from_utf8(&name).unwrap().to_owned(),
            encrypted_keypair,
            salt,
            encrypted_master_key,
        })
    }

    pub fn load_identity(database_folder: String, password: Option<&[u8]>) -> Result<Identity, String> {
        match Identity::load_encrypted_identity(&database_folder) {
            Ok(encrypted_identity) => {
                let master_key: [u8; crypto::MASTER_KEY_LEN] = if password.is_none() {
                    if encrypted_identity.encrypted_master_key.len() == crypto::MASTER_KEY_LEN {
                        encrypted_identity.encrypted_master_key.try_into().unwrap()
                    } else {
                        return Err(String::from(DATABASE_CORRUPED_ERROR))
                    }
                } else {
                    match crypto::decrypt_master_key(&encrypted_identity.encrypted_master_key, password.unwrap(), &encrypted_identity.salt) {
                        Ok(master_key) => master_key,
                        Err(e) => return Err(
                            match e {
                                CryptoError::DecryptionFailed => "Bad password".to_owned(),
                                CryptoError::InvalidLength => String::from(DATABASE_CORRUPED_ERROR)
                            }
                        )
                    }
                };
                match crypto::decrypt_data(&encrypted_identity.encrypted_keypair, &master_key) {
                    Ok(keypair) => {
                        Ok(Identity{
                            name: encrypted_identity.name,
                            keypair: Keypair::from_bytes(&keypair[..]).unwrap(),
                            master_key: master_key,
                            database_folder: database_folder,
                        })
                    }
                    Err(e) => {
                        print_error!(e);
                        Err(String::from(DATABASE_CORRUPED_ERROR))
                    }
                }
            }
            Err(e) => Err(e.to_string())
        }
    }

    pub fn get_identity_name(database_folder: &str) -> Result<String, rusqlite::Error> {
        let db = KeyValueTable::new(&get_database_path(database_folder), MAIN_TABLE)?;
        Ok(std::str::from_utf8(&db.get(DBKeys::NAME)?).unwrap().to_string())
    }

    pub fn is_protected(database_folder: String) -> Result<bool, rusqlite::Error> {
        let db = KeyValueTable::new(&get_database_path(&database_folder), MAIN_TABLE)?;
        Ok(db.get(DBKeys::MASTER_KEY)?.len() != crypto::MASTER_KEY_LEN)
    }
    
    pub fn create_identidy(database_folder: String, name: &str, password: Option<&[u8]>) -> Result<Identity, rusqlite::Error> {
        let keypair = Keypair::generate(&mut rand_7::rngs::OsRng);
        let master_key = crypto::generate_master_key();
        let encrypted_keypair = crypto::encrypt_data(&keypair.to_bytes(), &master_key).unwrap();
        let db = KeyValueTable::new(&get_database_path(&database_folder), MAIN_TABLE)?;
        db.set(DBKeys::NAME, name.as_bytes())?;
        db.set(DBKeys::KEYPAIR, &encrypted_keypair)?;
        let salt = if password.is_none() { //no password
            db.set(DBKeys::MASTER_KEY, &master_key)?; //storing master_key in plaintext
            [0; crypto::SALT_LEN]
        } else {
            let (salt, encrypted_master_key) = crypto::encrypt_master_key(master_key, password.unwrap());
            db.set(DBKeys::MASTER_KEY, &encrypted_master_key)?;
            salt
        };
        db.set(DBKeys::SALT, &salt)?;

        Ok(Identity {
            name: name.to_owned(),
            keypair,
            master_key,
            database_folder
        })
    }

    fn update_master_key(database_folder: String, master_key: [u8; crypto::MASTER_KEY_LEN], new_password: Option<&[u8]>) -> Result<usize, rusqlite::Error> {
        let db = KeyValueTable::new(&get_database_path(&database_folder), MAIN_TABLE)?;
        let salt = if new_password.is_none() { //no password
            db.update(DBKeys::MASTER_KEY, &master_key)?;
            [0; crypto::SALT_LEN]
        } else {
            let (salt, encrypted_master_key) = crypto::encrypt_master_key(master_key, new_password.unwrap());
            db.update(DBKeys::MASTER_KEY, &encrypted_master_key)?;
            salt
        };
        db.update(DBKeys::SALT, &salt)
    }

    pub fn change_password(database_folder: String, old_password: Option<&[u8]>, new_password: Option<&[u8]>) -> Result<bool, String> {
        match Identity::load_encrypted_identity(&database_folder) {
            Ok(encrypted_identity) => {
                let master_key: [u8; crypto::MASTER_KEY_LEN] = if old_password.is_none() {
                    if encrypted_identity.encrypted_master_key.len() == crypto::MASTER_KEY_LEN {
                        encrypted_identity.encrypted_master_key.try_into().unwrap()
                    } else {
                        return Err(String::from(DATABASE_CORRUPED_ERROR))
                    }
                } else {
                    match crypto::decrypt_master_key(&encrypted_identity.encrypted_master_key, old_password.unwrap(), &encrypted_identity.salt) {
                        Ok(master_key) => master_key,
                        Err(e) => return match e {
                            CryptoError::DecryptionFailed => Ok(false),
                            CryptoError::InvalidLength => Err(String::from(DATABASE_CORRUPED_ERROR))
                        }
                    }
                };
                match Identity::update_master_key(database_folder, master_key, new_password) {
                    Ok(_) => Ok(true),
                    Err(e) => Err(e.to_string())
                }
            }
            Err(e) => Err(e.to_string())
        }
    }
}
