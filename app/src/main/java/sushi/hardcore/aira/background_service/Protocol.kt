package sushi.hardcore.aira.background_service

import java.nio.ByteBuffer

class Protocol {
    companion object {
        const val MESSAGE: Byte = 0x00
        const val FILE: Byte = 0x01
        const val ASK_PROFILE_INFO: Byte = 0x02
        const val NAME: Byte = 0x03
        const val AVATAR: Byte = 0x04
        const val REMOVE_AVATAR: Byte = 0x05
        const val ASK_LARGE_FILES: Byte = 0x06
        const val ACCEPT_LARGE_FILES: Byte = 0x07
        const val LARGE_FILE_CHUNK: Byte = 0x08
        const val ACK_CHUNK: Byte = 0x09
        const val ABORT_FILES_TRANSFER: Byte = 0x0a

        fun askProfileInfo(): ByteArray {
            return byteArrayOf(ASK_PROFILE_INFO)
        }

        fun name(name: String): ByteArray {
            return byteArrayOf(NAME)+name.toByteArray()
        }

        fun avatar(avatar: ByteArray): ByteArray {
            return byteArrayOf(AVATAR)+avatar
        }

        fun removeAvatar(): ByteArray {
            return byteArrayOf(REMOVE_AVATAR)
        }

        fun newMessage(msg: String): ByteArray {
            return byteArrayOf(MESSAGE)+msg.toByteArray()
        }

        fun newFile(fileName: String, buffer: ByteArray): ByteArray {
            val fileNameBytes = fileName.toByteArray()
            return byteArrayOf(FILE)+ByteBuffer.allocate(2).putShort(fileNameBytes.size.toShort()).array()+fileNameBytes+buffer
        }

        fun askLargeFiles(files: List<SendFile>): ByteArray {
            var buff = byteArrayOf(ASK_LARGE_FILES)
            for (file in files) {
                val fileName = file.fileName.toByteArray()
                buff += ByteBuffer.allocate(8).putLong(file.fileSize).array()
                buff += ByteBuffer.allocate(2).putShort(fileName.size.toShort()).array()
                buff += fileName
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