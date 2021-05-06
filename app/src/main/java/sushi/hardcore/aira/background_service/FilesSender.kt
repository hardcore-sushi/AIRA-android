package sushi.hardcore.aira.background_service

import android.content.Context
import androidx.core.app.NotificationManagerCompat

class FilesSender(
        val files: List<SendFile>,
        context: Context,
        notificationManager: NotificationManagerCompat,
        sessionName: String
): FilesTransfer(context, notificationManager, sessionName) {
    val lastChunkSizes = mutableListOf<Int>()
    var nextChunk: ByteArray? = null
    val msgQueue = mutableListOf<ByteArray>()
}