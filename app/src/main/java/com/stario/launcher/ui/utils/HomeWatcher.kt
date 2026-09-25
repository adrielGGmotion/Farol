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

open class HomeWatcher(private val context: Context) {
    @Suppress("DEPRECATION")
    private val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)

    private var receiver: InnerReceiver? = null
    private var listener: OnHomePressedListener? = null

    open fun setOnHomePressedListener(listener: OnHomePressedListener?) {
        this.listener = listener
        receiver = InnerReceiver()
    }

    open fun startWatch() {
        val receiver = receiver ?: return
        if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    open fun stopWatch() {
        receiver?.let { context.unregisterReceiver(it) }
    }

    private inner class InnerReceiver : BroadcastReceiver() {
        private val SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps"
        private val SYSTEM_DIALOG_REASON_HOME_KEY = "homekey"

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            @Suppress("DEPRECATION")
            if (action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                val reason: String? = intent.getStringExtra("reason")

                if (reason == SYSTEM_DIALOG_REASON_HOME_KEY ||
                    reason == SYSTEM_DIALOG_REASON_RECENT_APPS
                ) {
                    listener?.onHomePressed()
                }
            }
        }
    }

    fun interface OnHomePressedListener {
        fun onHomePressed()
    }
}
