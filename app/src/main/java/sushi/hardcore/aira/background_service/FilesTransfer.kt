package sushi.hardcore.aira.background_service

import android.content.Context
import androidx.core.app.NotificationManagerCompat

open class FilesTransfer(context: Context, notificationManager: NotificationManagerCompat, sessionName: String) {
    val fileTransferNotification = FileTransferNotification(context, notificationManager, sessionName)
    var index = 0
}