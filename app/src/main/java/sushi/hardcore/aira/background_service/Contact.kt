package sushi.hardcore.aira.background_service

class Contact(val uuid: String, val publicKey: ByteArray, var name: String, var verified: Boolean, var seen: Boolean)