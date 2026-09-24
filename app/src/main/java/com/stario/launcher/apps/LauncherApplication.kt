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
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.utils.Utils
import java.util.UUID

open class LauncherApplication(
    @JvmField var info: ApplicationInfo,
    @JvmField var handle: UserHandle,
    @JvmField var label: String
) : Comparable<LauncherApplication> {
    @JvmField
    val systemPackage = info.flags and ApplicationInfo.FLAG_SYSTEM != 0

    @JvmField
    var category: UUID = UUID.randomUUID()

    @JvmField
    var icon: Drawable? = null

    @JvmField
    var notificationCount = 0

    open fun launch(activity: ThemedActivity) {
        val info = Utils.getMainActivity(activity, getInfo().packageName, handle)

        if (info != null) {
            activity.getSystemService(LauncherApps::class.java)
                .startMainActivity(info.componentName, handle, null, null)
        }
    }

    open fun getInfo(): ApplicationInfo = info

    open fun getLabel(): String = label

    open fun getIcon(): Drawable? = icon

    open fun getCategory(): UUID = category

    open fun getProfile(): UserHandle = handle

    override fun hashCode(): Int = info.packageName.hashCode()

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }

        return obj is LauncherApplication &&
            info.packageName == obj.info.packageName && handle == obj.handle
    }

    override fun compareTo(launcherApplication: LauncherApplication): Int {
        val result = getLabel().compareTo(launcherApplication.getLabel())
        return if (result != 0) result else handle.hashCode() - launcherApplication.handle.hashCode()
    }

    companion object {
        @JvmField
        val FALLBACK_APP: LauncherApplication? = null
    }
}
