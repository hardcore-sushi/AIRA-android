package sushi.hardcore.aira

import sushi.hardcore.aira.background_service.Contact

object AIRADatabase {
    external fun isIdentityProtected(databaseFolder: String): Boolean
    external fun loadIdentity(databaseFolder: String, password: ByteArray?): Boolean
    external fun addContact(name: String, publicKey: ByteArray): Contact?
    external fun removeContact(uuid: String): Boolean
    external fun loadContacts(): ArrayList<Contact>?
    external fun setVerified(uuid: String): Boolean
    external fun setContactSeen(contactUuid: String, seen: Boolean): Boolean
    external fun changeContactName(contactUuid: String, newName: String): Boolean
    external fun storeMsg(contactUuid: String, outgoing: Boolean, data: ByteArray): Boolean
    external fun storeFile(contactUuid: String?, data: ByteArray): ByteArray?
    external fun loadMsgs(uuid: String, offset: Int, count: Int): ArrayList<ChatItem>?
    external fun loadFile(rawUuid: ByteArray): ByteArray?
    external fun deleteConversation(contactUuid: String): Boolean
    external fun clearTemporaryFiles(): Int
    external fun getIdentityPublicKey(): ByteArray
    external fun getIdentityFingerprint(): String
    external fun changeName(newName: String): Boolean
    external fun changePassword(databaseFolder: String, oldPassword: ByteArray?, newPassword: ByteArray?): Boolean
}