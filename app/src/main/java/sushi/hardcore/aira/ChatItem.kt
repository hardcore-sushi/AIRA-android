package sushi.hardcore.aira

import sushi.hardcore.aira.background_service.Protocol
import java.util.*

class ChatItem(val outgoing: Boolean, val timestamp: Long, val data: ByteArray) {
    companion object {
        const val OUTGOING_MESSAGE = 0
        const val INCOMING_MESSAGE = 1
        const val OUTGOING_FILE = 2
        const val INCOMING_FILE = 3
    }
    val itemType = if (data[0] == Protocol.MESSAGE) {
        if (outgoing) OUTGOING_MESSAGE else INCOMING_MESSAGE
    } else {
        if (outgoing) OUTGOING_FILE else INCOMING_FILE
    }

    val calendar: Calendar by lazy {
        Calendar.getInstance().apply {
            time = Date(timestamp * 1000)
        }
    }
    val year by lazy {
        calendar.get(Calendar.YEAR)
    }
    val dayOfYear by lazy {
        calendar.get(Calendar.DAY_OF_YEAR)
    }
}