package sushi.hardcore.aira.background_service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import sushi.hardcore.aira.R

class FileTransferNotification(
        private val context: Context,
        private val notificationManager: NotificationManagerCompat,
        private val sessionName: String
) {
    private var fileSize = -1
    private var transferred = 0
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var notificationId = -1
    private var isEnded = false

    fun initFileTransferNotification(id: Int, fileName: String, size: Int, cancelIntent: Intent) {
        fileSize = size
        transferred = 0
        notificationBuilder = NotificationCompat.Builder(context, AIRAService.FILE_TRANSFER_NOTIFICATION_CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(sessionName)
                .setContentText(fileName)
                .setOngoing(true)
                .setProgress(fileSize, 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            val cancelPendingIntent = PendingIntent.getBroadcast(context, transferred, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            notificationBuilder.addAction(
                    NotificationCompat.Action(
                            R.drawable.ic_launcher,
                            context.getString(R.string.cancel),
                            cancelPendingIntent
                    )
            )
        }
        synchronized(this) {
            if (!isEnded) {
                notificationId = id
                notificationManager.notify(notificationId, notificationBuilder.build())
            }
        }
    }

    fun updateNotificationProgress(size: Int) {
        transferred += size
        notificationBuilder.setProgress(fileSize, transferred, false)
        synchronized(this) {
            if (!isEnded) {
                notificationManager.notify(notificationId, notificationBuilder.build())
            }
        }
    }

    private fun endNotification(string: Int) {
        synchronized(this) {
            notificationManager.notify(
                    notificationId,
                    NotificationCompat.Builder(context, AIRAService.FILE_TRANSFER_NOTIFICATION_CHANNEL_ID)
                            .setCategory(NotificationCompat.CATEGORY_EVENT)
                            .setSmallIcon(R.drawable.ic_launcher)
                            .setContentTitle(sessionName)
                            .setContentText(context.getString(string))
                            .build()
            )
            isEnded = true
        }
    }

    fun onAborted() {
        if (::notificationBuilder.isInitialized) {
            endNotification(R.string.transfer_aborted)
        }
    }

    fun onCompleted() {
        endNotification(R.string.transfer_completed)
    }

    fun cancel() {
        notificationManager.cancel(notificationId)
    }
}