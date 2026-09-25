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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import com.stario.launcher.Stario
import com.stario.launcher.apps.interfaces.LauncherProfileListener
import com.stario.launcher.utils.Utils
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import androidx.core.view.size

class ProfileManager private constructor(stario: Stario) {
    private val profilesMap: MutableMap<UserHandle, ProfileApplicationManager> = HashMap()
    private val profilesList = ArrayList<ProfileApplicationManager>()
    private val listeners: MutableList<LauncherProfileListener> = Collections.synchronizedList(ArrayList())
    private val iconPacks: IconPackManager

    init {
        instance = this

        iconPacks = IconPackManager.from(stario, ::update)
        CategoryManager.from(stario)

        val launcherApps = stario.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = stario.getSystemService(Context.USER_SERVICE) as UserManager
        val profiles = launcherApps.profiles
        profiles.sortWith { handle1, handle2 ->
            val diff = userManager.getUserCreationTime(handle1) -
                userManager.getUserCreationTime(handle2)
            if (diff < 0) -1 else if (diff > 0) 1 else 0
        }

        for (handle in profiles) {
            val manager = ProfileApplicationManager(stario, handle)
            if (owner != null) {
                owner = handle
            }

            profilesMap[handle] = manager
            profilesList.add(manager)

            for (listener in listeners) {
                listener.onInserted(handle)
            }
        }

        if (owner == null) {
            owner = Process.myUserHandle()
        }
    }

    fun getProfile(handle: UserHandle?): ProfileApplicationManager? {
        return if (handle == null) {
            owner?.let { profilesMap[it] }
        } else {
            profilesMap[handle]
        }
    }

    fun getProfile(index: Int): ProfileApplicationManager? {
        return if (index < 0 || index >= profilesList.size) {
            null
        } else {
            profilesList[index]
        }
    }

    fun getProfiles(): List<ProfileApplicationManager> = Collections.unmodifiableList(profilesList)

    fun getApplication(packageName: String): LauncherApplication? {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null) {
                return application
            }
        }

        return null
    }

    fun update() {
        for (manager in profilesList) {
            manager.update()
        }
    }

    fun updateIcon(packageName: String, icon: Drawable?) {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null && icon != null && icon != application.getIcon()) {
                application.icon = icon
                notifyUpdate(packageName)
            }
        }
    }

    fun updateLabel(packageName: String, label: String?) {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null) {
                manager.updateLabel(application, label)
            }
        }
    }

    fun notifyUpdate(packageName: String) {
        for (manager in profilesList) {
            val application = manager.get(packageName)

            if (application != null) {
                manager.notifyUpdate(application)
            }
        }
    }

    fun size(): Int = profilesList.size

    fun addLauncherProfileListener(listener: LauncherProfileListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeLauncherProfileListener(listener: LauncherProfileListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    companion object {
        const val PROFILE_AVAILABLE_EXTRA = "com.stario.launcher.PROFILE_AVAILABLE_EXTRA"
        private const val PROFILE_AVAILABLE_INTENT = "com.stario.launcher.PROFILE_AVAILABLE_INTENT"
        private var instance: ProfileManager? = null
        private var owner: UserHandle? = null

        @JvmStatic
        fun from(stario: Stario): ProfileManager = from(stario, true)

        @JvmStatic
        fun from(stario: Stario, refreshIcons: Boolean): ProfileManager {
            val manager = instance
            if (manager == null) {
                val created = ProfileManager(stario)
                val receiver = getReceiver(created)

                ContextCompat.registerReceiver(
                    stario,
                    receiver,
                    getIntentFilter(),
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else if (refreshIcons) {
                manager.iconPacks.refresh()
                manager.update()
            }

            return instance!!
        }

        private fun getReceiver(instance: ProfileManager): BroadcastReceiver {
            return object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    @Suppress("DEPRECATION")
                    val handle = if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
                        intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_USER)
                    }

                    if (handle == null) {
                        return
                    }

                    val action = intent.action
                    if (action != null) {
                        when (action) {
                            Intent.ACTION_MANAGED_PROFILE_ADDED -> {
                                if (instance.profilesMap.containsKey(handle)) {
                                    return
                                }

                                val launcherApps = context.getSystemService(
                                    Context.LAUNCHER_APPS_SERVICE
                                ) as LauncherApps

                                for (profileHandle in launcherApps.profiles) {
                                    if (handle == profileHandle) {
                                        val manager = ProfileApplicationManager(
                                            context.applicationContext as Stario,
                                            profileHandle
                                        )

                                        instance.profilesMap[profileHandle] = manager
                                        instance.profilesList.add(manager)

                                        for (listener in instance.listeners) {
                                            listener.onInserted(profileHandle)
                                        }
                                    }
                                }
                            }

                            Intent.ACTION_MANAGED_PROFILE_REMOVED -> {
                                val manager = instance.profilesMap.remove(handle)

                                if (manager == null) {
                                    return
                                }

                                instance.profilesList.remove(manager)
                                for (listener in instance.listeners) {
                                    listener.onRemoved(handle)
                                }
                            }

                            Intent.ACTION_MANAGED_PROFILE_AVAILABLE -> {
                                val availabilityIntent = Intent(getProfileAvailabilityIntentAction(handle))
                                availabilityIntent.putExtra(PROFILE_AVAILABLE_EXTRA, true)

                                availabilityIntent.setPackage(context.packageName)
                                context.sendBroadcast(availabilityIntent)
                            }

                            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE -> {
                                val availabilityIntent = Intent(getProfileAvailabilityIntentAction(handle))
                                availabilityIntent.putExtra(PROFILE_AVAILABLE_EXTRA, false)

                                availabilityIntent.setPackage(context.packageName)
                                context.sendBroadcast(availabilityIntent)
                            }
                        }
                    }
                }
            }
        }

        @JvmStatic
        fun getOwner(): UserHandle {
            if (instance == null || owner == null) {
                throw IllegalStateException("ProfileManager not initialized")
            }
            return owner!!
        }

        @JvmStatic
        fun getProfileAvailabilityIntentAction(handle: UserHandle?): String {
            return PROFILE_AVAILABLE_INTENT + if (handle != null) ":" + handle else ""
        }

        private fun getIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            intentFilter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            return intentFilter
        }

        @JvmStatic
        fun getInstance(): ProfileManager {
            return instance ?: throw RuntimeException("Applications not initialized.")
        }
    }
}
