package sushi.hardcore.aira.background_service

class ApplicationKeys(
    val localKey: ByteArray,
    val localIv: ByteArray,
    val peerKey: ByteArray,
    val peerIv: ByteArray,
)