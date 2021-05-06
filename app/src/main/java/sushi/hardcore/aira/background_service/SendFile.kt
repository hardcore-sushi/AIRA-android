package sushi.hardcore.aira.background_service

import java.io.InputStream

class SendFile(
        fileName: String,
        fileSize: Long,
        val inputStream: InputStream
): PendingFile(fileName, fileSize)