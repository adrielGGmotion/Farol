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

package com.stario.launcher.activities.launcher.sheets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.stario.launcher.activities.launcher.Launcher
import com.stario.launcher.sheet.SheetDialog
import com.stario.launcher.sheet.SheetDialogFragment

class LauncherSheets {
    companion object {
        const val INTENT_SHEET_CLASS_EXTRA = "com.stario.launcher.INTENT_SHEET_CLASS_EXTRA"
        const val ACTION_REMOVE_SHEET = "com.stario.launcher.ACTION_REMOVE_SHEET"
        const val ACTION_MOVE_SHEET = "com.stario.launcher.ACTION_MOVE_SHEET"
        const val ACTION_ADD_SHEET = "com.stario.launcher.ACTION_ADD_SHEET"

        @JvmStatic
        fun attach(launcher: Launcher, slideListener: SheetDialog.OnSlideListener) {
            val controller = launcher.getSheetsController()
            controller.setSlideListener(slideListener)
            controller.addSheetDialog(launcher, SheetDialogFragment.IMPLEMENTATIONS)

            val filter = IntentFilter().apply {
                addAction(ACTION_ADD_SHEET)
                addAction(ACTION_MOVE_SHEET)
                addAction(ACTION_REMOVE_SHEET)
            }
            LocalBroadcastManager.getInstance(launcher).registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val classes = getClasses(intent) ?: return
                    when (intent.action) {
                        ACTION_ADD_SHEET -> controller.addSheetDialog(launcher, *classes)
                        ACTION_MOVE_SHEET -> controller.moveSheetDialog(launcher, *classes)
                        ACTION_REMOVE_SHEET -> controller.removeSheetDialog(*classes)
                    }
                }
            }, filter)
        }

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        private fun getClasses(intent: Intent?): Array<Class<out SheetDialogFragment>>? {
            val extra = intent?.getSerializableExtra(INTENT_SHEET_CLASS_EXTRA) ?: return null
            val elements = if (extra is Array<*>) extra.asList() else listOf(extra)
            val classes = elements.filterIsInstance<Class<*>>()
                .filter { SheetDialogFragment::class.java.isAssignableFrom(it) }
                .map { it as Class<out SheetDialogFragment> }
            return classes.takeIf { it.isNotEmpty() }?.toTypedArray()
        }
    }
}
