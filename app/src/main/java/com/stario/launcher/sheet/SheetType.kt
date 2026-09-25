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

package com.stario.launcher.sheet

import android.content.SharedPreferences
import android.util.Log
import android.view.View
import androidx.core.util.Pair
import com.stario.launcher.preferences.Entry
import com.stario.launcher.sheet.briefing.dialog.BriefingDialog
import com.stario.launcher.sheet.drawer.dialog.ApplicationsDialog
import com.stario.launcher.sheet.widgets.dialog.WidgetsDialog
import com.stario.launcher.themes.ThemedActivity
import androidx.core.content.edit

@Suppress("UNCHECKED_CAST")
enum class SheetType(private val stringType: String, private val axesValue: Int) {
    TOP_SHEET("com.stario.launcher.TOP_SHEET", View.SCROLL_AXIS_VERTICAL),
    BOTTOM_SHEET("com.stario.launcher.BOTTOM_SHEET", View.SCROLL_AXIS_VERTICAL),
    LEFT_SHEET("com.stario.launcher.LEFT_SHEET", View.SCROLL_AXIS_HORIZONTAL),
    RIGHT_SHEET("com.stario.launcher.RIGHT_SHEET", View.SCROLL_AXIS_HORIZONTAL),
    UNDEFINED("com.stario.launcher.UNDEFINED", View.SCROLL_AXIS_NONE);

    fun getAxes(): Int = axesValue

    override fun toString(): String = stringType

    companion object {
        private const val TAG = "SheetType"

        @JvmStatic
        fun deserialize(serial: String?): SheetType? {
            if (serial == null) {
                return null
            }

            return when (serial) {
                TOP_SHEET.stringType -> TOP_SHEET
                RIGHT_SHEET.stringType -> RIGHT_SHEET
                BOTTOM_SHEET.stringType -> BOTTOM_SHEET
                LEFT_SHEET.stringType -> LEFT_SHEET
                UNDEFINED.stringType -> UNDEFINED
                else -> null
            }
        }

        @JvmStatic
        fun getStoredSheets(activity: ThemedActivity): List<Pair<SheetType, Class<out SheetDialogFragment>>> {
            val list = ArrayList<Pair<SheetType, Class<out SheetDialogFragment>>>()
            val preferences: SharedPreferences = activity.applicationContext.getSharedPreferences(Entry.SHEET)
            preferences.all.forEach { (string, obj) ->
                try {
                    val clazz = Class.forName(string)

                    if (SheetDialogFragment::class.java.isAssignableFrom(clazz)) {
                        if (obj is String) {
                            val type = deserialize(obj)

                            if (type != null) {
                                list.add(Pair(type, clazz as Class<out SheetDialogFragment>))
                            } else {
                                Log.e(TAG, "getSheets: $obj does not map to a valid ${SheetType::class.java.name} serial String.")
                                preferences.edit {remove(string)}
                            }
                        } else {
                            Log.e(TAG, "getSheets: $string can only map to a ${SheetType::class.java.name} serial String.")
                            preferences.edit {remove(string)}
                        }
                    } else {
                        Log.e(TAG, "getSheets: $string does not extend ${SheetDialogFragment::class.java.name}")
                        preferences.edit {remove(string)}
                    }
                } catch (exception: ClassNotFoundException) {
                    Log.e(TAG, "getSheets: Could not get class $string")
                    preferences.edit {remove(string)}
                }
            }

            return list
        }

        @JvmStatic
        fun getSheetTypeForSheetDialogFragment(
            activity: ThemedActivity,
            clazz: Class<out SheetDialogFragment>
        ): SheetType? {
            var type: SheetType? = null
            val typeString = activity.applicationContext
                .getSharedPreferences(Entry.SHEET)
                .getString(clazz.name, null)

            if (typeString != null) {
                type = deserialize(typeString)
            }

            if (type != null) {
                return type
            }

            return getDefaultSheetTypeForSheetDialogFragment(activity, clazz, true)
        }

        @JvmStatic
        fun getDefaultSheetTypeForSheetDialogFragment(
            activity: ThemedActivity,
            clazz: Class<out SheetDialogFragment>
        ): SheetType? = getDefaultSheetTypeForSheetDialogFragment(activity, clazz, false)

        private fun getDefaultSheetTypeForSheetDialogFragment(
            activity: ThemedActivity,
            clazz: Class<out SheetDialogFragment>,
            writeToPreferences: Boolean
        ): SheetType? {
            var type: SheetType? = null
            val preferences = activity.applicationContext.getSharedPreferences(Entry.SHEET)

            if (clazz == ApplicationsDialog::class.java) {
                if (writeToPreferences) {
                    preferences.edit {
                            putString(ApplicationsDialog::class.java.name, BOTTOM_SHEET.toString())
                        }
                }

                type = BOTTOM_SHEET
            } else if (clazz == BriefingDialog::class.java) {
                if (writeToPreferences) {
                    preferences.edit {
                            putString(BriefingDialog::class.java.name, LEFT_SHEET.toString())
                        }
                }

                type = LEFT_SHEET
            } else if (clazz == WidgetsDialog::class.java) {
                if (writeToPreferences) {
                    preferences.edit {
                            putString(WidgetsDialog::class.java.name, RIGHT_SHEET.toString())
                        }
                }

                type = RIGHT_SHEET
            }

            return type
        }
    }
}
