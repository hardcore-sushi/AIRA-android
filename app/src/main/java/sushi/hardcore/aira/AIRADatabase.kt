package sushi.hardcore.aira

import sushi.hardcore.aira.background_service.Contact

object AIRADatabase {
    external fun initLogging(): Boolean
    external fun isIdentityProtected(databaseFolder: String): Boolean
    external fun getIdentityName(databaseFolder: String): String?
    external fun loadIdentity(databaseFolder: String, password: ByteArray?): Boolean
    external fun addContact(name: String, avatarUuid: String?, publicKey: ByteArray): Contact?
    external fun removeContact(uuid: String): Boolean
    external fun loadContacts(): ArrayList<Contact>?
    external fun setVerified(uuid: String): Boolean
    external fun setContactSeen(contactUuid: String, seen: Boolean): Boolean
    external fun changeContactName(contactUuid: String, newName: String): Boolean
    external fun setContactAvatar(contactUuid: String, avatarUuid: String?): Boolean
    external fun storeMsg(contactUuid: String, outgoing: Boolean, timestamp: Long, data: ByteArray): Boolean
    external fun storeFile(contactUuid: String?, data: ByteArray): ByteArray?
    external fun loadMsgs(uuid: String, offset: Int, count: Int): ArrayList<ChatItem>?
    external fun loadFile(rawUuid: ByteArray): ByteArray?
    external fun deleteConversation(contactUuid: String): Boolean
    external fun clearCache()
    external fun getIdentityPublicKey(): ByteArray
    external fun getIdentityFingerprint(): String
    external fun getUsePadding(): Boolean
    external fun setUsePadding(usePadding: Boolean): Boolean
    external fun storeAvatar(avatar: ByteArray): String?
    external fun getAvatar(avatarUuid: String): ByteArray?
    external fun changeName(newName: String): Boolean
    external fun changePassword(databaseFolder: String, oldPassword: ByteArray?, newPassword: ByteArray?): Boolean
    external fun setIdentityAvatar(databaseFolder: String, avatar: ByteArray): Boolean
    external fun removeIdentityAvatar(databaseFolder: String): Boolean
    external fun getIdentityAvatar(databaseFolder: String): ByteArray?

    fun init() {
        System.loadLibrary("aira")
        initLogging()
    }

    fun loadAvatar(avatarUuid: String?): ByteArray? {
        return avatarUuid?.let {
            getAvatar(it)
        }
    }
}