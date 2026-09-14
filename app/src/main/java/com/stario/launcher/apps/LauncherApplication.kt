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

package com.stario.launcher.apps

import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.utils.Utils
import java.util.UUID

class LauncherApplication(
    @JvmField var info: ApplicationInfo,
    @JvmField var handle: UserHandle,
    @JvmField var label: String,
) : Comparable<LauncherApplication> {
    @JvmField val systemPackage: Boolean = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
    @JvmField var category: UUID = UUID.randomUUID()
    @JvmField var icon: Drawable? = null
    @JvmField var notificationCount: Int = 0

    fun launch(activity: ThemedActivity) {
        val activityInfo: LauncherActivityInfo? =
            Utils.getMainActivity(activity, getInfo().packageName, handle)
        if (activityInfo != null) {
            activity.getSystemService(LauncherApps::class.java)
                .startMainActivity(activityInfo.componentName, handle, null, null)
        }
    }

    fun getInfo(): ApplicationInfo = info
    fun getLabel(): String = label
    fun getIcon(): Drawable? = icon
    fun getCategory(): UUID = category
    fun getProfile(): UserHandle = handle

    override fun hashCode(): Int = info.packageName.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LauncherApplication) return false
        return info.packageName == other.info.packageName && handle == other.handle
    }

    override fun compareTo(other: LauncherApplication): Int {
        val result = getLabel().compareTo(other.getLabel())
        return if (result != 0) result else handle.hashCode() - other.handle.hashCode()
    }

    companion object {
        @JvmField
        val FALLBACK_APP: LauncherApplication? = null
    }
}
