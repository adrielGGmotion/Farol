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

package com.stario.launcher.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.stario.launcher.utils.Utils

class HomeWatcher(private val context: Context) {
    private val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
    private var receiver: InnerReceiver? = null
    private var listener: OnHomePressedListener? = null

    fun setOnHomePressedListener(listener: OnHomePressedListener?) {
        this.listener = listener
        receiver = InnerReceiver()
    }

    fun startWatch() {
        receiver?.let { value ->
            if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
                context.registerReceiver(value, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(value, filter)
            }
        }
    }

    fun stopWatch() {
        receiver?.let(context::unregisterReceiver)
    }

    private inner class InnerReceiver : BroadcastReceiver() {
        private val systemDialogReasonRecentApps = "recentapps"
        private val systemDialogReasonHomeKey = "homekey"

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                when (intent.getStringExtra("reason")) {
                    systemDialogReasonHomeKey, systemDialogReasonRecentApps -> listener?.onHomePressed()
                }
            }
        }
    }

    fun interface OnHomePressedListener {
        fun onHomePressed()
    }
}
