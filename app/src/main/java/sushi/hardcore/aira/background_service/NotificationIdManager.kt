package sushi.hardcore.aira.background_service

class NotificationIdManager {

    private enum class NotificationType {
        MESSAGE,
        FILE_TRANSFER
    }

    private inner class Notification(
            val sessionId: Int,
            val type: NotificationType
    )


    private val notificationIds = mutableMapOf<Int, Notification>()
    private var lastNotificationId = 1 //got some bugs when starting before 1

    private fun registerNewId(sessionId: Int, type: NotificationType): Int {
        lastNotificationId++
        notificationIds[lastNotificationId] = Notification(sessionId, type)
        return lastNotificationId
    }

    fun getMessageNotificationId(sessionId: Int): Int {
        for ((id, notification) in notificationIds) {
            if (notification.sessionId == sessionId) {
                if (notification.type == NotificationType.MESSAGE) {
                    return id
                }
            }
        }
        return registerNewId(sessionId, NotificationType.MESSAGE)
    }

    fun getFileTransferNotificationId(sessionId: Int): Int {
        for ((id, notification) in notificationIds) {
            if (notification.sessionId == sessionId) {
                if (notification.type == NotificationType.FILE_TRANSFER) {
                    return id
                }
            }
        }
        return registerNewId(sessionId, NotificationType.FILE_TRANSFER)
    }
}