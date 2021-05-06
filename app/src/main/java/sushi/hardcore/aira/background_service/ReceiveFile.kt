package sushi.hardcore.aira.background_service

import java.io.OutputStream

class ReceiveFile (
    fileName: String,
    fileSize: Long
): PendingFile(fileName, fileSize) {
    var outputStream: OutputStream? = null
}