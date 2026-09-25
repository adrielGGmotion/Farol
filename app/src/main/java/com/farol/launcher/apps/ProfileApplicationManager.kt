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

package com.farol.launcher.apps

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.farol.launcher.BuildConfig
import com.farol.launcher.Farol
import com.farol.launcher.apps.interfaces.LauncherApplicationListener
import com.farol.launcher.preferences.Entry
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.utils.Utils
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.view.size
import androidx.core.content.edit

class ProfileApplicationManager(farol: Farol, @JvmField val handle: UserHandle) {
    private val visibleApplicationList: MutableList<LauncherApplication> =
        Collections.synchronizedList(ArrayList())
    private val applicationList: MutableList<LauncherApplication> = Collections.synchronizedList(ArrayList())
    private val applicationMap: MutableMap<String, LauncherApplication> = HashMap()
    private val listeners: MutableList<LauncherApplicationListener> = Collections.synchronizedList(ArrayList())
    private val applicationLabels: SharedPreferences = farol.getSharedPreferences(Entry.APPLICATION_LABELS)
    private val hiddenApplications: SharedPreferences = farol.getSharedPreferences(
        Entry.HIDDEN_APPS,
        handle.hashCode().toString()
    )
    private val packageManager: PackageManager = farol.packageManager
    private val readyListeners = ArrayList<OnLoadReadyListener>()
    private var loaded = false
    private val iconPacks: IconPackManager

    init {
        val launcherApps = farol.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(getReceiver(launcherApps))

        iconPacks = IconPackManager.from(farol)
        Utils.submitTask { loadApplications(farol) }
    }

    private fun getReceiver(launcherApps: LauncherApps): LauncherApps.Callback {
        return object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                if (handle != user || BuildConfig.APPLICATION_ID == packageName) {
                    return
                }

                removeApplication(packageName)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                if (handle != user || BuildConfig.APPLICATION_ID == packageName) {
                    return
                }

                val applicationInfo = getApplicationInfo(packageName)

                if (applicationInfo != null && applicationInfo.enabled) {
                    addApplication(createApplication(applicationInfo))
                }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                if (handle != user || BuildConfig.APPLICATION_ID == packageName) {
                    return
                }

                val applicationInfo = getApplicationInfo(packageName)

                if (applicationInfo != null) {
                    val application = get(packageName)

                    if (application != null) {
                        if (!applicationInfo.enabled) {
                            removeApplication(packageName)
                        } else {
                            application.info = applicationInfo
                            updateApplication(application)
                        }
                    } else {
                        addApplication(createApplication(applicationInfo))
                    }
                } else {
                    removeApplication(packageName)
                }
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                for (packageName in packageNames) {
                    if (replacing) {
                        onPackageChanged(packageName, user)
                    } else {
                        onPackageAdded(packageName, user)
                    }
                }
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                for (packageName in packageNames) {
                    if (!replacing) {
                        onPackageRemoved(packageName, user)
                    }
                }
            }

            private fun getApplicationInfo(packageName: String): ApplicationInfo? {
                val containsPackage = applicationMap.containsKey(packageName)
                if (launcherApps.getActivityList(packageName, handle).isEmpty()) {
                    return null
                }

                val applicationInfo: ApplicationInfo
                try {
                    applicationInfo = launcherApps.getApplicationInfo(packageName, 0, handle)

                    if (launcherApps.getActivityList(packageName, handle).isEmpty()) {
                        if (containsPackage) {
                            removeApplication(packageName)
                        }

                        return null
                    }
                } catch (exception: PackageManager.NameNotFoundException) {
                    Log.e(TAG, "Package " + packageName + " does not exist.")
                    return null
                }

                return applicationInfo
            }
        }
    }

    private fun loadApplications(farol: Farol) {
        val launcherApps = farol.getSystemService(LauncherApps::class.java)
        val activityInfoList: List<LauncherActivityInfo> = launcherApps.getActivityList(null, handle)
        val iconPackApps = ArrayList<ApplicationInfo>()
        val otherApps = ArrayList<ApplicationInfo>()

        for (activityInfo in activityInfoList) {
            val applicationInfo = activityInfo.applicationInfo
            if (applicationInfo == null || BuildConfig.APPLICATION_ID == applicationInfo.packageName) {
                continue
            }

            if (iconPacks.checkPackValidity(applicationInfo.packageName)) {
                iconPackApps.add(applicationInfo)
            } else {
                otherApps.add(applicationInfo)
            }
        }

        for (appInfo in iconPackApps) {
            if (!applicationMap.containsKey(appInfo.packageName)) {
                addApplication(createApplication(appInfo))
            }
        }

        for (application in applicationList) {
            iconPacks.updateIcon(application.info.packageName)
        }

        for (appInfo in otherApps) {
            if (!applicationMap.containsKey(appInfo.packageName)) {
                addApplication(createApplication(appInfo))
            }
        }

        loaded = true
        UiUtils.post {
            for (listener in readyListeners) {
                listener.onReady(this)
            }

            readyListeners.clear()
        }
    }

    fun isReady(): Boolean = loaded

    fun addOnReadyListener(listener: OnLoadReadyListener?) {
        if (listener != null) {
            if (loaded) {
                listener.onReady(this)
            } else {
                readyListeners.add(listener)
            }
        }
    }

    fun updateApplication(application: LauncherApplication) {
        iconPacks.updateIcon(application.info.packageName)
        notifyUpdate(application)
    }

    fun update() {
        for (index in applicationList.indices) {
            updateApplication(applicationList[index])
        }
    }

    fun updateLabel(application: LauncherApplication, label: String?) {
        val oldLabel = application.label

        if (label != null) {
            if (label.isNotEmpty() && application.label != label) {
                application.label = label
            }
        } else {
            application.label = application.info.loadLabel(packageManager).toString()
        }

        if (oldLabel != application.label) {
            applicationLabels.edit {
                    putString(application.info.packageName, label)
                }

            if (isVisibleToUser(application.info.packageName)) {
                notifyUpdate(application)
            }
        }
    }

    fun notifyUpdate(application: LauncherApplication) {
        for (listener in listeners) {
            listener.onUpdated(application)
        }
    }

    private fun createApplication(applicationInfo: ApplicationInfo): LauncherApplication {
        val application = LauncherApplication(applicationInfo, handle, getLabel(applicationInfo))
        application.category = CategoryManager.getInstance()
            .getCategoryIdentifier(applicationInfo, handle)
        return application
    }

    private fun getLabel(applicationInfo: ApplicationInfo): String {
        var label = applicationLabels.getString(applicationInfo.packageName, null)

        if (label == null) {
            label = applicationInfo.loadLabel(packageManager).toString()
        }

        return label
    }

    /**
     * Method to get the {@link LauncherApplication} object at index
     *
     * @param index The index of the application
     * @return {@link LauncherApplication} at index
     */
    fun get(index: Int): LauncherApplication? {
        return if (index >= 0 && index < visibleApplicationList.size) {
            visibleApplicationList[index]
        } else {
            LauncherApplication.FALLBACK_APP
        }
    }

    /**
     * Method to get the {@link LauncherApplication} object at index
     *
     * @param index  The index of the application
     * @param hidden If true, account for hidden items
     * @return {@link LauncherApplication} at index
     */
    fun get(index: Int, hidden: Boolean): LauncherApplication? {
        return if (hidden) {
            if (index >= 0 && index < applicationList.size) {
                applicationList[index]
            } else {
                LauncherApplication.FALLBACK_APP
            }
        } else {
            get(index)
        }
    }

    /**
     * Method to get the {@link LauncherApplication} object by package name
     *
     * @param packageName The package name of the application
     * @return {@link LauncherApplication} at index
     */
    fun get(packageName: String): LauncherApplication? = applicationMap[packageName]

    fun getActualSize(): Int = applicationList.size

    fun getSize(): Int = visibleApplicationList.size

    @Synchronized
    private fun addApplication(application: LauncherApplication) {
        applicationMap[application.info.packageName] = application

        addApplicationToList(application, applicationList)
        if (isVisibleToUser(application.info.packageName)) {
            addApplicationToList(application, visibleApplicationList)

            for (listener in listeners) {
                listener.onInserted(application)
            }
        }

        iconPacks.add(application)
        iconPacks.updateIcon(application.info.packageName)

        CategoryManager.getInstance().addApplication(application)
    }

    @Synchronized
    private fun addApplicationToList(
        applicationToAdd: LauncherApplication,
        list: MutableList<LauncherApplication>
    ) {
        var left = 0
        var right = list.size - 1

        while (left <= right) {
            val middle = (left + right) / 2
            val application = list[middle]
            val compareValue = application.label.compareTo(applicationToAdd.label, ignoreCase = true)

            if (compareValue < 0) {
                left = middle + 1
            } else if (compareValue > 0) {
                right = middle - 1
            } else if (application.info.packageName != applicationToAdd.info.packageName) {
                list.add(middle, applicationToAdd)
                return
            } else {
                return
            }
        }

        list.add(left, applicationToAdd)
    }

    @Synchronized
    private fun removeApplication(packageName: String) {
        val application = applicationMap[packageName]

        if (application != null) {
            for (listener in listeners) {
                listener.onPrepareRemoval()
            }

            applicationMap.remove(packageName)
            visibleApplicationList.remove(application)
            applicationList.remove(application)

            iconPacks.remove(application)
            CategoryManager.getInstance().removeApplication(application)

            for (listener in listeners) {
                listener.onRemoved(application)
            }
        }
    }

    @Synchronized
    fun showApplication(application: LauncherApplication) {
        hiddenApplications.edit {
                remove(application.info.packageName)
            }

        if (!visibleApplicationList.contains(application)) {
            addApplicationToList(application, visibleApplicationList)
            CategoryManager.getInstance().addApplication(application)

            for (listener in listeners) {
                listener.onShowed(application)
            }
        }
    }

    @Synchronized
    fun hideApplication(application: LauncherApplication) {
        hiddenApplications.edit {
                putBoolean(application.info.packageName, true)
            }

        for (listener in listeners) {
            listener.onPrepareHiding()
        }

        visibleApplicationList.remove(application)
        CategoryManager.getInstance().removeApplication(application)

        for (listener in listeners) {
            listener.onHidden(application)
        }
    }

    fun addApplicationListener(listener: LauncherApplicationListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeApplicationListener(listener: LauncherApplicationListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    fun isVisibleToUser(application: LauncherApplication?): Boolean {
        return application != null && isVisibleToUser(application.info.packageName)
    }

    fun isVisibleToUser(packageName: String): Boolean = !hiddenApplications.contains(packageName)

    /**
     * @return Index of application in the hidden list
     **/
    fun indexOf(application: LauncherApplication): Int = applicationList.indexOf(application)

    fun interface OnLoadReadyListener {
        fun onReady(manager: ProfileApplicationManager)
    }

    companion object {
        private const val TAG = "ProfileApplicationManager"
    }
}
