package sushi.hardcore.aira.background_service

import android.content.Context
import androidx.core.app.NotificationManagerCompat

open class FilesTransfer(context: Context, notificationManager: NotificationManagerCompat, total: Int) {
    val fileTransferNotification = FileTransferNotification(context, notificationManager, total)
    var index = 0
}