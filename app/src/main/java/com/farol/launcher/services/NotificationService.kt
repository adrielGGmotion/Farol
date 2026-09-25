/*
 * Copyright (C) 2025 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.farol.launcher.services

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.HashMap

open class NotificationService : NotificationListenerService() {
    override fun onNotificationPosted(notification: StatusBarNotification) {
        try {
            sendBroadcastForNotification(notification)
        } catch (exception: Exception) {
            Log.e(TAG, "onNotificationPosted: " + exception)
        }

        super.onNotificationPosted(notification)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        try {
            sendBroadcastForNotification(notification)
        } catch (exception: Exception) {
            Log.e(TAG, "onNotificationRemoved: " + exception)
        }

        super.onNotificationRemoved(notification)
    }

    override fun onListenerConnected() {
        instance = this

        try {
            val intent = Intent()
            intent.setAction(NOTIFICATIONS_EVENT)

            intent.putExtra(
                TARGET_NOTIFICATION,
                convertToNotificationMap(getActiveNotifications())
            )
            sendBroadcast(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "onListenerConnected: " + exception.message)
        }

        super.onListenerConnected()
    }

    @SuppressLint("UnsafeImplicitIntentLaunch")
    private fun sendBroadcastForNotification(notification: StatusBarNotification) {
        val intent = Intent()
        intent.setAction(UPDATE_NOTIFICATIONS)

        var count = 0
        for (statusBarNotification in getActiveNotifications()) {
            if (statusBarNotification.getPackageName() == notification.getPackageName() &&
                !((statusBarNotification.getNotification().flags and Notification.FLAG_GROUP_SUMMARY) ==
                    Notification.FLAG_GROUP_SUMMARY)
            ) {
                count++
            }
        }

        intent.putExtra(TARGET_NOTIFICATION, notification.getPackageName())
        intent.putExtra(NOTIFICATION_COUNT, count)

        sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "NotificationService"
        const val NOTIFICATION_DOTS = "com.stario.NOTIFICATION_DOTS"
        const val NOTIFICATIONS_EVENT = "com.farol.launcher.NOTIFICATIONS_LISTENER_EVENT"
        const val UPDATE_NOTIFICATIONS = "com.farol.launcher.UPDATE_NOTIFICATIONS"
        const val TARGET_NOTIFICATION = "com.farol.launcher.TARGET_NOTIFICATION"
        const val NOTIFICATION_COUNT = "com.farol.launcher.NOTIFICATION_COUNT"

        private var instance: NotificationService? = null

        @JvmStatic
        fun getInstance(): NotificationService? {
            return instance
        }

        @JvmStatic
        @Suppress("ConstantConditions")
        fun convertToNotificationMap(
            notifications: Array<StatusBarNotification>?
        ): HashMap<String, Int> {
            val notificationMap = HashMap<String, Int>()

            if (notifications != null) {
                for (notification in notifications) {
                    val packageName = notification.getPackageName()

                    if (!((notification.getNotification().flags and Notification.FLAG_GROUP_SUMMARY) ==
                            Notification.FLAG_GROUP_SUMMARY)
                    ) {
                        notificationMap[packageName] = notificationMap.getOrDefault(packageName, 0) + 1
                    }
                }
            }

            return notificationMap
        }
    }
}
