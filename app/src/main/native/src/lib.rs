mod key_value_table;
mod identity;
mod crypto;
mod utils;

use std::{convert::TryInto, fmt::Display, str::FromStr, sync::{Mutex}};
use lazy_static::lazy_static;
use uuid::Uuid;
use identity::{Identity, Contact, Message};
use crate::crypto::{HandshakeKeys, ApplicationKeys};

lazy_static! {
    static ref loaded_identity: Mutex<Option<Identity>> = Mutex::new(None);
}

#[cfg(target_os="android")]
use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JList, JValue};
use jni::sys::{jboolean, jint, jlong, jbyteArray, jobject};

fn jstring_to_string(env: JNIEnv, input: JString) -> String {
    String::from(env.get_string(input).unwrap())
}

fn jstring_to_uuid(env: JNIEnv, input: JString) -> Option<Uuid> {
    match env.get_string(input) {
        Ok(uuid) => Some(Uuid::from_str(&String::from(uuid)).unwrap()),
        Err(_) => None
    }
}

fn jboolean_to_bool(input: jboolean) -> bool {
    input == 1
}

fn bool_to_jboolean(input: bool) -> u8 {
    if input { 1 } else { 0 }
}

fn result_to_jboolean<T, E: Display>(result: Result<T, E>) -> jboolean {
    match result {
        Ok(_) => 1,
        Err(e) => {
            print_error!(e);
            0
        }
    }
}

fn slice_to_jvalue<'a>(env: JNIEnv, input: &'a [u8]) -> JValue<'a> {
    JValue::Object(env.byte_array_from_slice(input).unwrap().into())
}

#[no_mangle]
pub extern fn Java_sushi_hardcore_aira_AIRADatabase_initLogging(_: JNIEnv, _: JClass) -> jboolean {
    bool_to_jboolean(android_log::init("AIRA Native").is_ok())
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern fn Java_sushi_hardcore_aira_CreateIdentityFragment_createNewIdentity(env: JNIEnv, _: JClass, database_folder: JString, name: JString, password: jbyteArray) -> jboolean {
    let database_folder = jstring_to_string(env, database_folder);
    let name = jstring_to_string(env, name);
    match Identity::create_identidy(database_folder, &name, env.convert_byte_array(password).ok().as_deref()) {
        Ok(identity) => {
            *loaded_identity.lock().unwrap() = Some(identity);
            1
        }
        Err(e) => {
            print_error!(e);
            0
        }
    }
}


#[no_mangle]
pub extern fn Java_sushi_hardcore_aira_AIRADatabase_getIdentityName(env: JNIEnv, _: JClass, database_folder: JString) -> jobject {
    *match Identity::get_identity_name(&jstring_to_string(env, database_folder)) {
        Ok(name) => *env.new_string(name).unwrap(),
        Err(e) => {
            print_error!(e);
            JObject::null()
        }
    }
}

#[no_mangle]
pub extern fn Java_sushi_hardcore_aira_AIRADatabase_isIdentityProtected(env: JNIEnv, _: JClass, database_folder: JString) -> jboolean {
    match Identity::is_protected(jstring_to_string(env, database_folder)) {
        Ok(is_protected) => bool_to_jboolean(is_protected),
        Err(e) => {
            print_error!(e);
            0
        }
    }
}

#[no_mangle]
pub extern fn Java_sushi_hardcore_aira_AIRADatabase_loadIdentity(env: JNIEnv, _: JClass, database_folder: JString, password: jbyteArray) -> jboolean {
    let database_folder = jstring_to_string(env, database_folder);
    match Identity::load_identity(database_folder, env.convert_byte_array(password).ok().as_deref()) {
        Ok(identity) => {
            *loaded_identity.lock().unwrap() = Some(identity);
            1
        }
        Err(e) => {
            print_error!(e);
            0
        }
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_changePassword(env: JNIEnv, _: JClass, database_folder: JString, old_password: jbyteArray, new_password: jbyteArray) -> jboolean {
    let database_folder = jstring_to_string(env, database_folder);
    match Identity::change_password(database_folder, env.convert_byte_array(old_password).ok().as_deref(), env.convert_byte_array(new_password).ok().as_deref()) {
        Ok(success) => bool_to_jboolean(success),
        Err(e) => {
            print_error!(e);
            0
        }
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_setIdentityAvatar(env: JNIEnv, _: JClass, database_folder: JString, avatar: jbyteArray) -> jboolean {
    result_to_jboolean(Identity::set_identity_avatar(&jstring_to_string(env, database_folder), &env.convert_byte_array(avatar).unwrap()))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_removeIdentityAvatar(env: JNIEnv, _: JClass, database_folder: JString) -> jboolean {
    result_to_jboolean(Identity::remove_identity_avatar(&jstring_to_string(env, database_folder)))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_getIdentityAvatar(env: JNIEnv, _: JClass, database_folder: JString) -> jbyteArray {
    match Identity::get_identity_avatar(&jstring_to_string(env, database_folder)) {
        Ok(avatar) => env.byte_array_from_slice(&avatar).unwrap(),
        Err(e) => {
            print_error!(e);
            *JObject::null()
        }
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_getIdentityPublicKey(env: JNIEnv, _: JClass) -> jbyteArray {
    env.byte_array_from_slice(&loaded_identity.lock().unwrap().as_ref().unwrap().get_public_key()).unwrap()
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_background_1service_Session_sign(env: JNIEnv, _: JClass, input: jbyteArray) -> jbyteArray {
    env.byte_array_from_slice(&loaded_identity.lock().unwrap().as_ref().unwrap().sign(&env.convert_byte_array(input).unwrap())).unwrap()
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_background_1service_Session_deriveHandshakeKeys(env: JNIEnv, _: JClass, shared_secret: jbyteArray, handshake_hash: jbyteArray, i_am_bob: jboolean) -> jobject {
    let shared_secret = env.convert_byte_array(shared_secret).unwrap();
    let handshake_hash = env.convert_byte_array(handshake_hash).unwrap();
    let handshake_keys = HandshakeKeys::derive_keys(shared_secret.as_slice().try_into().unwrap(), handshake_hash.as_slice().try_into().unwrap(), jboolean_to_bool(i_am_bob));
    
    let args: Vec<JValue<'_>> = [&handshake_keys.local_key[..], &handshake_keys.local_iv, &handshake_keys.local_handshake_traffic_secret, &handshake_keys.peer_key, &handshake_keys.peer_iv, &handshake_keys.peer_handshake_traffic_secret, &handshake_keys.handshake_secret].iter().map(|field|{
        slice_to_jvalue(env, field)
    }).collect();
    let handshake_keys_class = env.find_class("sushi/hardcore/aira/background_service/HandshakeKeys").unwrap();
    *env.new_object(handshake_keys_class, "([B[B[B[B[B[B[B)V", &args).unwrap()
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_background_1service_Session_computeHandshakeFinished(env: JNIEnv, _: JClass, local_handshake_traffic_secret: jbyteArray, handshake_hash: jbyteArray) -> jbyteArray {
    env.byte_array_from_slice(&crypto::compute_handshake_finished(env.convert_byte_array(local_handshake_traffic_secret).unwrap()[..].try_into().unwrap(), env.convert_byte_array(handshake_hash).unwrap()[..].try_into().unwrap())).unwrap()
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_background_1service_Session_verifyHandshakeFinished(env: JNIEnv, _: JClass, peer_handshake_finished: jbyteArray, peer_handshake_traffic_secret: jbyteArray, handshake_hash: jbyteArray) -> jboolean {
    bool_to_jboolean(crypto::verify_handshake_finished(env.convert_byte_array(peer_handshake_finished).unwrap()[..].try_into().unwrap(), env.convert_byte_array(peer_handshake_traffic_secret).unwrap()[..].try_into().unwrap(), env.convert_byte_array(handshake_hash).unwrap()[..].try_into().unwrap()))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_background_1service_Session_deriveApplicationKeys(env: JNIEnv, _: JClass, handshake_secret: jbyteArray, handshake_hash: jbyteArray, i_am_bob: jboolean) -> jobject {
    let handshake_secret = env.convert_byte_array(handshake_secret).unwrap();
    let handshake_hash = env.convert_byte_array(handshake_hash).unwrap();
    let application_keys = ApplicationKeys::derive_keys(handshake_secret.as_slice().try_into().unwrap(), handshake_hash.as_slice().try_into().unwrap(), jboolean_to_bool(i_am_bob));
    
    let args: Vec<JValue<'_>> = [&application_keys.local_key[..], &application_keys.local_iv, &application_keys.peer_key, &application_keys.peer_iv].iter().map(|field|{
        slice_to_jvalue(env, field)
    }).collect();
    let application_keys_class = env.find_class("sushi/hardcore/aira/background_service/ApplicationKeys").unwrap();
    *env.new_object(application_keys_class, "([B[B[B[B)V", &args).unwrap()
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_background_1service_AIRAService_releaseIdentity(_: JNIEnv, _: JClass) {
    let mut identity = loaded_identity.lock().unwrap();
    identity.as_mut().unwrap().zeroize();
    *identity = None;
}

fn new_contact(env: JNIEnv, contact: Contact) -> JObject {
    let contact_class = env.find_class("sushi/hardcore/aira/background_service/Contact").unwrap();
    let avatar_uuid = match contact.avatar {
        Some(uuid) => JValue::Object(*env.new_string(uuid.to_string()).unwrap()),
        None => JValue::Object(JObject::null())
    };
    env.new_object(contact_class, "(Ljava/lang/String;[BLjava/lang/String;Ljava/lang/String;ZZ)V", &[
                   JValue::Object(*env.new_string(contact.uuid.to_string()).unwrap()),
                   slice_to_jvalue(env, &contact.public_key),
                   JValue::Object(*env.new_string(contact.name).unwrap()),
                   avatar_uuid,
                   JValue::Bool(bool_to_jboolean(contact.verified)),
                   JValue::Bool(bool_to_jboolean(contact.seen))
    ]).unwrap()
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_addContact(env: JNIEnv, _: JClass, name: JString, avatarUuid: JString, public_key: jbyteArray) -> jobject {
    *match loaded_identity.lock().unwrap().as_ref().unwrap().add_contact(jstring_to_string(env, name), jstring_to_uuid(env, avatarUuid), env.convert_byte_array(public_key).unwrap().try_into().unwrap()) {
        Ok(contact) => new_contact(env, contact),
        Err(e) => {
            print_error!(e);
            JObject::null()
        }
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_removeContact(env: JNIEnv, _: JClass, uuid: JString) -> jboolean {
    result_to_jboolean(loaded_identity.lock().unwrap().as_ref().unwrap().remove_contact(&Uuid::from_str(&jstring_to_string(env, uuid)).unwrap()))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_loadContacts(env: JNIEnv, _: JClass) -> jobject {
    *match loaded_identity.lock().unwrap().as_ref().unwrap().load_contacts() {
        Some(contacts) => {
            let array_list_class = env.find_class("java/util/ArrayList").unwrap();
            let array_list = env.new_object(array_list_class, "(I)V", &[JValue::Int(contacts.len().try_into().unwrap())]).unwrap();
            let array_list = JList::from_env(&env, array_list).unwrap();
            for contact in contacts {
                array_list.add(new_contact(env, contact)).unwrap();
            }
            *array_list
        }
        None => JObject::null()
    }
}


#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_setVerified(env: JNIEnv, _: JClass, uuid: JString) -> jboolean {
    result_to_jboolean(loaded_identity.lock().unwrap().as_ref().unwrap().set_verified(&jstring_to_uuid(env, uuid).unwrap()))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_changeContactName(env: JNIEnv, _: JClass, contactUuid: JString, newName: JString) -> jboolean {
    result_to_jboolean(loaded_identity.lock().unwrap().as_ref().unwrap().change_contact_name(&jstring_to_uuid(env, contactUuid).unwrap(), &jstring_to_string(env, newName)))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_setContactAvatar(env: JNIEnv, _: JClass, contactUuid: JString, avatarUuid: JString) -> jboolean {
    result_to_jboolean(loaded_identity.lock().unwrap().as_ref().unwrap().set_contact_avatar(&jstring_to_uuid(env, contactUuid).unwrap(), jstring_to_uuid(env, avatarUuid).as_ref()))
}

#[allow(non_snake_case)]
#[no_mangle]    
pub fn Java_sushi_hardcore_aira_AIRADatabase_setContactSeen(env: JNIEnv, _: JClass, contactUuid: JString, seen: jboolean) -> jboolean {
    result_to_jboolean(loaded_identity.lock().unwrap().as_ref().unwrap().set_contact_seen(&jstring_to_uuid(env, contactUuid).unwrap(), jboolean_to_bool(seen)))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_storeMsg(env: JNIEnv, _: JClass, contactUuid: JString, outgoing: jboolean, timestamp: jlong, data: jbyteArray) -> jboolean {
    let message = Message {
        outgoing: jboolean_to_bool(outgoing),
        timestamp: timestamp as u64,
        data: env.convert_byte_array(data).unwrap(),
    };
    result_to_jboolean(loaded_identity.lock().unwrap().as_ref().unwrap().store_msg(&jstring_to_uuid(env, contactUuid).unwrap(), message))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_loadMsgs(env: JNIEnv, _: JClass, uuid: JString, offset: jint, count: jint) -> jobject {
    *match loaded_identity.lock().unwrap().as_ref().unwrap().load_msgs(&jstring_to_uuid(env, uuid).unwrap(), offset as usize, count as usize) {
        Some(msgs) => {
            let array_list_class = env.find_class("java/util/ArrayList").unwrap();
            let array_list = env.new_object(array_list_class, "(I)V", &[JValue::Int(msgs.len().try_into().unwrap())]).unwrap();
            let array_list = JList::from_env(&env, array_list).unwrap();
            let chat_item_class = env.find_class("sushi/hardcore/aira/ChatItem").unwrap();
            for msg in msgs {
                let chat_item_object = env.new_object(chat_item_class, "(ZJ[B)V", &[
                    JValue::Bool(bool_to_jboolean(msg.outgoing)),
                    JValue::Long(msg.timestamp as jlong),
                    slice_to_jvalue(env, &msg.data),
                ]).unwrap();
                array_list.add(chat_item_object).unwrap();
            }
            *array_list
        }
        None => JObject::null()
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_storeFile(env: JNIEnv, _: JClass, contactUuid: JString, data: jbyteArray) -> jbyteArray {
    match loaded_identity.lock().unwrap().as_ref().unwrap().store_file(jstring_to_uuid(env, contactUuid), &env.convert_byte_array(data).unwrap()) {
        Ok(uuid) => env.byte_array_from_slice(uuid.as_bytes()).unwrap(),
        Err(_) => *JObject::null()
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_loadFile(env: JNIEnv, _: JClass, rawUuid: jbyteArray) -> jbyteArray {
    match loaded_identity.lock().unwrap().as_ref().unwrap().load_file(Uuid::from_bytes(env.convert_byte_array(rawUuid).unwrap().try_into().unwrap())) {
        Some(buffer) => env.byte_array_from_slice(&buffer).unwrap(),
        None => *JObject::null()
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_deleteConversation(env: JNIEnv, _: JClass, contactUuid: JString) -> jboolean {
    let contact_uuid = jstring_to_uuid(env, contactUuid).unwrap();
    result_to_jboolean(loaded_identity.lock().unwrap().as_ref().unwrap().delete_conversation(&contact_uuid))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_clearCache(_: JNIEnv, _: JClass) {
    if let Err(e) = loaded_identity.lock().unwrap().as_ref().unwrap().clear_cache() {
        print_error!(e);
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_changeName(env: JNIEnv, _: JClass, new_name: JString) -> jboolean {
    let new_name = jstring_to_string(env, new_name);
    match loaded_identity.lock().unwrap().as_mut().unwrap().change_name(new_name) {
        Ok(u) => bool_to_jboolean(u == 1),
        Err(e) => {
            print_error!(e);
            0
        }
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_getUsePadding(_: JNIEnv, _: JClass) -> jboolean {
    bool_to_jboolean(loaded_identity.lock().unwrap().as_mut().unwrap().use_padding)
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_setUsePadding(_: JNIEnv, _: JClass, use_padding: jboolean) -> jboolean {
    result_to_jboolean(loaded_identity.lock().unwrap().as_mut().unwrap().set_use_padding(jboolean_to_bool(use_padding)))
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_storeAvatar(env: JNIEnv, _: JClass, avatar: jbyteArray) -> jobject {
    *match loaded_identity.lock().unwrap().as_ref().unwrap().store_avatar(&env.convert_byte_array(avatar).unwrap()) {
        Ok(uuid) => *env.new_string(uuid.to_string()).unwrap(),
        Err(_) => JObject::null()
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_getAvatar(env: JNIEnv, _: JClass, avatarUuid: JString) -> jbyteArray {
    match loaded_identity.lock().unwrap().as_ref().unwrap().get_avatar(&jstring_to_uuid(env, avatarUuid).unwrap()) {
        Some(buffer) => env.byte_array_from_slice(&buffer).unwrap(),
        None => *JObject::null()
    }
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_AIRADatabase_getIdentityFingerprint(env: JNIEnv, _: JClass) -> jobject {
    **env.new_string(crypto::generate_fingerprint(&loaded_identity.lock().unwrap().as_ref().unwrap().get_public_key())).unwrap()
}

#[allow(non_snake_case)]
#[no_mangle]
pub fn Java_sushi_hardcore_aira_ChatActivity_generateFingerprint(env: JNIEnv, _: JClass, publicKey: jbyteArray) -> jobject {
    **env.new_string(crypto::generate_fingerprint(&env.convert_byte_array(publicKey).unwrap())).unwrap()
}
