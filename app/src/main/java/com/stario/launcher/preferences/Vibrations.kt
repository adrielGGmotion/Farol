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

package com.stario.launcher.preferences

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.stario.launcher.Stario
import com.stario.launcher.exceptions.NoExistingInstanceException

class Vibrations private constructor(stario: Stario) {
    private val settings = stario.getSharedPreferences(Entry.STARIO)
    private val vibrator = stario.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun vibrate() {
        if (settings.getBoolean(PREFERENCE_ENTRY, true)) {
            vibrator.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        @JvmField
        const val PREFERENCE_ENTRY = "com.stario.VIBRATIONS"

        private var instance: Vibrations? = null

        @JvmStatic
        fun from(stario: Stario) {
            if (instance == null) instance = Vibrations(stario)
        }

        @JvmStatic
        @Throws(NoExistingInstanceException::class)
        fun getInstance(): Vibrations =
            instance ?: throw NoExistingInstanceException(Vibrations::class.java)
    }
}
