package sushi.hardcore.aira

import sushi.hardcore.aira.background_service.Protocol

class ChatItem(val outgoing: Boolean, val data: ByteArray) {
    companion object {
        const val MESSAGE = 0
        const val FILE = 1
    }
    val itemType = if (data[0] == Protocol.MESSAGE) { MESSAGE } else { FILE }
}