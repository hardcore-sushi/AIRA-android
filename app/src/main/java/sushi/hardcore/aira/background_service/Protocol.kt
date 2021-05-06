package sushi.hardcore.aira.background_service

import java.nio.ByteBuffer

class Protocol {
    companion object {
        const val MESSAGE: Byte = 0x00
        const val ASK_NAME: Byte = 0x01
        const val TELL_NAME: Byte = 0x02
        const val FILE: Byte = 0x03
        const val ASK_LARGE_FILES: Byte = 0x04
        const val ACCEPT_LARGE_FILES: Byte = 0x05
        const val LARGE_FILE_CHUNK: Byte = 0x06
        const val ACK_CHUNK: Byte = 0x07
        const val ABORT_FILES_TRANSFER: Byte = 0x08

        fun askName(): ByteArray {
            return byteArrayOf(ASK_NAME)
        }

        fun tellName(name: String): ByteArray {
            return byteArrayOf(TELL_NAME)+name.toByteArray()
        }

        fun newMessage(msg: String): ByteArray {
            return byteArrayOf(MESSAGE)+msg.toByteArray()
        }

        fun newFile(fileName: String, buffer: ByteArray): ByteArray {
            return byteArrayOf(FILE)+ByteBuffer.allocate(2).putShort(fileName.length.toShort()).array()+fileName.toByteArray()+buffer
        }

        fun askLargeFiles(files: List<SendFile>): ByteArray {
            var buff = byteArrayOf(ASK_LARGE_FILES)
            for (file in files) {
                buff += ByteBuffer.allocate(8).putLong(file.fileSize).array()
                buff += ByteBuffer.allocate(2).putShort(file.fileName.length.toShort()).array()
                buff += file.fileName.toByteArray()
            }
            return buff
        }

        fun acceptLargeFiles(): ByteArray {
            return byteArrayOf(ACCEPT_LARGE_FILES)
        }

        fun abortFilesTransfer(): ByteArray {
            return byteArrayOf(ABORT_FILES_TRANSFER)
        }

        fun ackChunk(): ByteArray {
            return byteArrayOf(ACK_CHUNK)
        }

        class SmallFile(val rawFileName: ByteArray, val fileContent: ByteArray)

        fun parseSmallFile(buffer: ByteArray): SmallFile? {
            if (buffer.size > 3) {
                val filenameLen = ByteBuffer.wrap(ByteArray(2) +buffer.sliceArray(1..2)).int
                if (buffer.size > 3+filenameLen) {
                    val rawFileName = buffer.sliceArray(3 until 3+filenameLen)
                    return SmallFile(rawFileName, buffer.sliceArray(3+filenameLen until buffer.size))
                }
            }
            return null
        }

        fun parseAskFiles(buffer: ByteArray): List<ReceiveFile>? {
            val files = mutableListOf<ReceiveFile>()
            var n = 1
            while (n < buffer.size) {
                if (buffer.size > n+10) {
                    val fileSize = ByteBuffer.wrap(buffer.sliceArray(n..n+8)).long
                    val fileNameLen = ByteBuffer.wrap(buffer.sliceArray(n+8..n+10)).short
                    if (buffer.size >= n+10+fileNameLen) {
                        val fileName = buffer.sliceArray(n+10 until n+10+fileNameLen).decodeToString()
                        files.add(ReceiveFile(fileName, fileSize))
                        n += 10+fileNameLen
                    } else {
                        return null
                    }
                } else {
                    return null
                }
            }
            return files
        }
    }
}