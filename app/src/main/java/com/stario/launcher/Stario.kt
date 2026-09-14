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

package com.stario.launcher

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.preferences.Entry
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.ui.Measurements
import org.chickenhook.restrictionbypass.Unseal

class Stario : Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            Unseal.unseal()
        } catch (exception: Exception) {
            Log.e("Stario", "Could not unseal the process.", exception)
        }

        Vibrations.from(this)
        ProfileManager.from(this)

        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (!Measurements.wereTaken()) {
                        throw RuntimeException("Measurements were not taken.")
                    }
                }
            })
    }

    // warn the usage of malformed preference stores
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        if (!Entry.isValid(name)) {
            Log.w(
                "Stario",
                "getSharedPreferences: $name should be part of " +
                    Entry::class.java.canonicalName
            )
        }

        return super.getSharedPreferences(name, mode)
    }

    fun getSharedPreferences(entry: Entry): SharedPreferences =
        super.getSharedPreferences(entry.toString(), MODE_PRIVATE)

    fun getSharedPreferences(entry: Entry, subPreference: String): SharedPreferences =
        super.getSharedPreferences(entry.toSubPreference(subPreference), MODE_PRIVATE)

    fun getSettings(): SharedPreferences = getSharedPreferences(Entry.STARIO)
}
