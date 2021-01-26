package sushi.hardcore.aira.background_service

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sushi.hardcore.aira.R

open class FileTransfer(val fileName: String, val fileSize: Long) {
    var transferred = 0
    lateinit var notificationBuilder: NotificationCompat.Builder
    var notificationId = -1

    fun updateNotificationProgress(notificationManager: NotificationManagerCompat) {
        notificationBuilder.setProgress(fileSize.toInt(), transferred, false)
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun endNotification(context: Context, notificationManager: NotificationManagerCompat, sessionName: String, string: Int) {
        notificationManager.notify(
                notificationId,
                NotificationCompat.Builder(context, AIRAService.FILE_TRANSFER_NOTIFICATION_CHANNEL_ID)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(sessionName)
                        .setContentText(context.getString(string))
                        .build()
        )
    }

    fun onAborted(context: Context, notificationManager: NotificationManagerCompat, sessionName: String) {
        if (::notificationBuilder.isInitialized) {
            endNotification(context, notificationManager, sessionName, R.string.transfer_aborted)
        }
    }

    fun onCompleted(context: Context, notificationManager: NotificationManagerCompat, sessionName: String) {
        endNotification(context, notificationManager, sessionName, R.string.transfer_completed)
    }
}