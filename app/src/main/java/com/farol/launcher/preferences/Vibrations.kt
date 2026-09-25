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

package com.farol.launcher.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import com.farol.launcher.Farol
import com.farol.launcher.exceptions.NoExistingInstanceException

open class Vibrations private constructor(farol: Farol) {
    private val settings: SharedPreferences = farol.getSharedPreferences(Entry.FAROL)
    private val vibrator: Vibrator? = farol.getSystemService(Vibrator::class.java)

    open fun vibrate() {
        if (settings.getBoolean(PREFERENCE_ENTRY, true)) {
            vibrator!!.vibrate(
                VibrationEffect.createOneShot(5L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    companion object {
        const val PREFERENCE_ENTRY = "com.stario.VIBRATIONS"

        private var instance: Vibrations? = null

        @JvmStatic
        fun from(activity: Farol) {
            if (instance == null) {
                instance = Vibrations(activity)
            }
        }

        @JvmStatic
        fun getInstance(): Vibrations {
            return instance ?: throw NoExistingInstanceException(Vibrations::class.java)
        }
    }
}
