package com.example.data

import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val dao: NotificationDao) {

    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()

    suspend fun insertNotification(notification: NotificationEntity): Long {
        return dao.insertNotification(notification)
    }

    suspend fun getNotificationById(id: Long): NotificationEntity? {
        return dao.getNotificationById(id)
    }

    suspend fun updateStatus(id: Long, status: String) {
        dao.updateStatus(id, status)
    }

    suspend fun updateReplyStatus(id: Long, replyStatus: String, replyTimestamp: Long = System.currentTimeMillis()) {
        dao.updateReplyWithTimestamp(id, replyStatus, replyTimestamp)
    }

    suspend fun deleteNotification(id: Long) {
        dao.deleteNotification(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
