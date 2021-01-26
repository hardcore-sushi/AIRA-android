package sushi.hardcore.aira.background_service

import java.nio.ByteBuffer

class Protocol {
    companion object {
        const val MESSAGE: Byte = 0x00
        const val ASK_NAME: Byte = 0x01
        const val TELL_NAME: Byte = 0x02
        const val FILE: Byte = 0x03
        const val ASK_LARGE_FILE: Byte = 0x04
        const val ACCEPT_LARGE_FILE: Byte = 0x05
        const val LARGE_FILE_CHUNK: Byte = 0x06
        const val ACK_CHUNK: Byte = 0x07
        const val ABORT_FILE_TRANSFER: Byte = 0x08

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

        fun askLargeFile(fileSize: Long, fileName: String): ByteArray {
            return byteArrayOf(ASK_LARGE_FILE)+ByteBuffer.allocate(8).putLong(fileSize).array()+fileName.toByteArray()
        }

        fun acceptLargeFile(): ByteArray {
            return byteArrayOf(ACCEPT_LARGE_FILE)
        }

        fun abortFileTransfer(): ByteArray {
            return byteArrayOf(ABORT_FILE_TRANSFER)
        }

        fun ackChunk(): ByteArray {
            return byteArrayOf(ACK_CHUNK)
        }
    }
}