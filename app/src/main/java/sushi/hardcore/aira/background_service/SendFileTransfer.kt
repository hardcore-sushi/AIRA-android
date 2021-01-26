package sushi.hardcore.aira.background_service

import java.io.InputStream

class SendFileTransfer(
        fileName: String,
        fileSize: Long,
        val inputStream: InputStream
): FileTransfer(fileName, fileSize) {
    var nextChunk: ByteArray? = null
    val msgQueue = mutableListOf<ByteArray>()
}