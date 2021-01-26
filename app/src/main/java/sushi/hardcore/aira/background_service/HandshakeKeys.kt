package sushi.hardcore.aira.background_service

class HandshakeKeys(
    val localKey: ByteArray,
    val localIv: ByteArray,
    val localHandshakeTrafficSecret: ByteArray,
    val peerKey: ByteArray,
    val peerIv: ByteArray,
    val peerHandshakeTrafficSecret: ByteArray,
    val handshakeSecret: ByteArray
)