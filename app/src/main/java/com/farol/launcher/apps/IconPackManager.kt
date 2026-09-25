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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Pair
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import com.farol.launcher.BuildConfig
import com.farol.launcher.Farol
import com.farol.launcher.preferences.Entry
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.icons.AdaptiveIconView
import com.farol.launcher.ui.icons.PathCornerTreatmentAlgorithm
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.utils.ImageUtils
import com.farol.launcher.utils.Utils
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import androidx.core.view.isNotEmpty
import androidx.core.view.size
import androidx.core.content.edit

class IconPackManager private constructor(farol: Farol, listener: OnChangeListener?) {
    private val iconPacks = ArrayList<IconPack>()
    private var listener: OnChangeListener? = listener
    private val preferences: SharedPreferences = farol.getSharedPreferences(Entry.ICONS)
    private val packageManager: PackageManager = farol.packageManager
    private val launcherApps = farol.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private var activeIconPack: IconPack? = null

    fun setActiveIconPack(pack: IconPack?) {
        val cornerRadius = preferences.getFloat(
            AdaptiveIconView.CORNER_RADIUS_ENTRY,
            AdaptiveIconView.DEFAULT_CORNER_RADIUS
        )
        val pathAlgorithm = preferences.getInt(
            PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
            PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
        )
        preferences.edit {

            clear()
                .putString(ICON_PACK_ENTRY, pack?.application?.info?.packageName)

            if (cornerRadius != AdaptiveIconView.DEFAULT_CORNER_RADIUS) {
                putFloat(AdaptiveIconView.CORNER_RADIUS_ENTRY, cornerRadius)
            }

            if (pathAlgorithm != PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY) {
                putInt(PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY, pathAlgorithm)
            }

        }
        activeIconPack = pack

        listener?.onChange()
    }

    fun setIconPackPreference(packageName: String, pack: IconPack?, drawableName: String?) {
        if (pack != null) {
            var json = "{$JSON_ICON_PACK:\"${pack.application.info.packageName}\""

            if (drawableName != null) {
                json += ",$JSON_ICON_DRAWABLE_NAME:\"$drawableName\""
            }

            preferences.edit {
                    putString(packageName, "$json}")
                }
        } else {
            preferences.edit {
                    putString(packageName, BuildConfig.APPLICATION_ID)
                }
        }

        updateIcon(packageName)
    }

    fun getCount(): Int = iconPacks.size

    fun getPack(index: Int): IconPack = iconPacks[index]

    fun getPack(packageName: String): IconPack? {
        for (index in iconPacks.indices) {
            if (iconPacks[index].application.info.packageName == packageName) {
                return getPack(index)
            }
        }

        return null
    }

    fun updateIcon(packageName: String) {
        Utils.submitTask {
            var pack = activeIconPack
            var drawableName: String? = null

            if (preferences.contains(packageName)) {
                val packagePreference = preferences.getString(packageName, null)

                if (packagePreference != null) {
                    if (packagePreference == BuildConfig.APPLICATION_ID) {
                        pack = null
                    } else {
                        try {
                            val json = JSONObject(packagePreference)
                            val target = getPack(json.get(JSON_ICON_PACK) as String)
                            if (target != null) {
                                pack = target

                                if (json.has(JSON_ICON_DRAWABLE_NAME)) {
                                    drawableName = json.get(JSON_ICON_DRAWABLE_NAME) as String
                                }
                            }
                        } catch (exception: Exception) {
                            Log.e("IconPackManager", "loadDrawable: " +
                                "Malformed JSON icon store for package " + packageName)
                        }
                    }
                }
            }

            if (pack != null) {
                pack.loadDrawable(packageName, drawableName).thenAccept { icon ->
                    var resolvedIcon = icon
                    if (resolvedIcon == null) {
                        resolvedIcon = ImageUtils.getIcon(launcherApps, packageName)
                    }

                    UiUtils.post {
                        ProfileManager.getInstance().updateIcon(packageName, resolvedIcon)
                    }
                }
            } else {
                ProfileManager.getInstance().updateIcon(
                    packageName,
                    ImageUtils.getIcon(launcherApps, packageName)
                )
            }
        }
    }

    @Synchronized
    fun remove(application: LauncherApplication?) {
        if (application != null) {
            for (index in iconPacks.indices) {
                if (application == iconPacks[index].application) {
                    iconPacks.removeAt(index)
                    return
                }
            }
        }
    }

    @Synchronized
    fun add(application: LauncherApplication) {
        if (checkPackValidity(application)) {
            val iconPack = IconPack(application)
            iconPacks.add(iconPack)

            if (application.info.packageName == preferences.getString(ICON_PACK_ENTRY, null)) {
                activeIconPack = iconPack
                listener?.onChange()
            }
        }
    }

    @Synchronized
    fun refresh() {
        for (pack in iconPacks) {
            pack.invalidate()
        }
    }

    fun checkPackValidity(application: LauncherApplication): Boolean =
        checkPackValidity(application.info.packageName)

    fun checkPackValidity(packageName: String): Boolean {
        //ADW Launcher
        return Intent("org.adw.launcher.THEMES")
            .setPackage(packageName)
            .resolveActivity(packageManager) != null ||
            //Lawnchair Launcher 14
            Intent("app.lawnchair.icons.THEMED_ICON")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null ||
            //Lawnchair Launcher Legacy
            Intent("ch.deletescape.lawnchair.ICONPACK")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null ||
            //Nova Launcher
            Intent("com.novalauncher.THEME")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null ||
            //GO Launcher
            Intent("com.gau.go.launcherex.theme")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null ||
            //Smart Launcher
            Intent("ginlemon.smartlauncher.THEMES")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null ||
            //TSF Shell
            Intent("com.tsf.shell.themes")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null ||
            //OnePlus Launcher
            Intent("net.oneplus.launcher.icons.ACTION_PICK_ICON")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null ||
            //Moto Launcher
            Intent("com.motorola.launcher3.ACTION_ICON_PACK")
                .setPackage(packageName)
                .resolveActivity(packageManager) != null
    }

    /**
     * @param application {@link LauncherApplication} for which to return all available icons
     * @return {@link CompletableFuture} containing a list of mappings from an existing {@link IconPack}
     * to the respective icon - name and {@link Drawable}
     */
    fun getIcons(
        application: LauncherApplication
    ): CompletableFuture<List<Pair<IconPack?, Pair<String?, Drawable?>>>> {
        val future = CompletableFuture<List<Pair<IconPack?, Pair<String?, Drawable?>>>>()

        Utils.submitTask {
            try {
                val result = ArrayList<Pair<IconPack?, Pair<String?, Drawable?>>>()
                result.add(Pair(null, Pair(null, ImageUtils.getIcon(launcherApps, application.info.packageName))))

                for (pack in iconPacks) {
                    val drawableNames = pack.getDrawableNameList(application.info.packageName).get()

                    if (drawableNames != null && drawableNames.isNotEmpty()) {
                        for (drawableName in drawableNames) {
                            val drawable = pack.getDrawable(drawableName)

                            if (drawable != null) {
                                result.add(Pair(pack, Pair(drawableName, drawable)))
                            }
                        }
                    }
                }

                future.complete(result)
            } catch (exception: ExecutionException) {
                future.complete(ArrayList())
            } catch (exception: InterruptedException) {
                future.complete(ArrayList())
            }
        }

        return future
    }

    @SuppressLint("DiscouragedApi")
    open inner class IconPack internal constructor(internal val application: LauncherApplication) {
        private val exactComponentDrawable = HashMap<String, MutableList<String>>()
        private val packageNameDrawables = HashMap<String, MutableList<String>>()
        private val completionListeners: MutableList<Runnable> = Collections.synchronizedList(ArrayList())
        private var loadTask: CompletableFuture<Boolean?>? = null
        private lateinit var resources: Resources
        private var cached = false

        @Synchronized
        fun load(completionListener: Runnable?) {
            if (cached) {
                completionListener?.run()
                return
            }

            val currentLoadTask = loadTask
            if (currentLoadTask != null && !currentLoadTask.isDone && completionListener != null) {
                completionListeners.add(completionListener)
                return
            }

            loadTask = Utils.submitTask<Boolean> {
                try {
                    if (completionListener != null) {
                        completionListeners.add(completionListener)
                    }

                    var parser: XmlPullParser? = null
                    resources = packageManager.getResourcesForApplication(application.info.packageName)
                    val appFilterId = resources.getIdentifier(
                        "appfilter",
                        "xml",
                        application.info.packageName
                    )

                    if (appFilterId > 0) {
                        parser = resources.getXml(appFilterId)
                    } else {
                        try {
                            val appFilterStream: InputStream = resources.assets.open("appfilter.xml")
                            val factory = XmlPullParserFactory.newInstance()
                            factory.isNamespaceAware = true

                            parser = factory.newPullParser()
                            parser.setInput(appFilterStream, Xml.Encoding.UTF_8.toString())
                        } catch (exception: IOException) {
                            Log.d(ICON_PACK_TAG, "No appfilter.xml file")
                        }
                    }

                    if (parser != null) {
                        var eventType = parser.eventType

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                if (parser.name == "item" || parser.name == "calendar") {
                                    var componentName: String? = null
                                    var drawableName: String? = null
                                    var prefix: String? = null

                                    for (index in 0 until parser.attributeCount) {
                                        if (parser.getAttributeName(index) == "component") {
                                            componentName = parser.getAttributeValue(index)

                                            if (componentName != null &&
                                                componentName.indexOf('{') != -1 &&
                                                componentName.indexOf('{') + 1 < componentName.lastIndexOf('}')
                                            ) {
                                                componentName = componentName.substring(
                                                    componentName.indexOf('{') + 1,
                                                    componentName.lastIndexOf('}')
                                                )
                                            }
                                        } else if (parser.getAttributeName(index) == "drawable") {
                                            drawableName = parser.getAttributeValue(index)
                                        } else if (parser.getAttributeName(index) == "prefix") {
                                            prefix = parser.getAttributeValue(index)
                                        }
                                    }

                                    if (componentName != null && componentName.contains("/")) {
                                        if (drawableName != null) {
                                            saveDrawable(componentName, drawableName)
                                        }

                                        if (prefix != null) {
                                            for (day in 1..31) {
                                                saveDrawable(componentName, prefix + day)
                                            }
                                        }
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                    }

                    var index = 0
                    while (index < completionListeners.size) {
                        completionListeners[index].run()
                        index++
                    }

                    completionListeners.clear()
                    true
                } catch (exception: PackageManager.NameNotFoundException) {
                    Log.d(ICON_PACK_TAG, "Cannot load icon pack")
                    false
                } catch (exception: XmlPullParserException) {
                    Log.d(ICON_PACK_TAG, "Cannot parse icon pack appfilter.xml")
                    false
                } catch (exception: IOException) {
                    Log.e(ICON_PACK_TAG, "", exception)
                    false
                }
            }

            loadTask!!.thenAccept { result -> cached = result!! }
        }

        private fun saveDrawable(componentName: String, drawableName: String) {
            var drawables = exactComponentDrawable[componentName]
            if (drawables == null) {
                drawables = ArrayList()
                drawables.add(drawableName)
                exactComponentDrawable[componentName] = drawables
            } else if (!drawables.contains(drawableName)) {
                drawables.add(drawableName)
            }

            if (changedComponents.containsKey(componentName)) {
                val changedComponent = changedComponents[componentName]!!
                drawables = exactComponentDrawable[changedComponent]
                if (drawables == null) {
                    drawables = ArrayList()
                    drawables.add(drawableName)
                    exactComponentDrawable[changedComponent] = drawables
                } else if (!drawables.contains(drawableName)) {
                    drawables.add(drawableName)
                }
            }

            val packageName = componentName.substring(0, componentName.indexOf('/'))
            drawables = packageNameDrawables[packageName]
            if (drawables == null) {
                drawables = ArrayList()
                drawables.add(drawableName)
                packageNameDrawables[packageName] = drawables
            } else if (!drawables.contains(drawableName)) {
                drawables.add(drawableName)
            }
        }

        internal fun getDrawable(drawableName: String): Drawable? {
            val id = resources.getIdentifier(drawableName, "drawable", application.info.packageName)

            return if (id > 0) {
                ResourcesCompat.getDrawable(resources, id, null)
            } else {
                null
            }
        }

        open fun loadDrawable(packageName: String, drawable: String?): CompletableFuture<Drawable?> {
            val future = CompletableFuture<Drawable?>()

            load(Runnable {
                var drawableName = drawable
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

                if (launchIntent != null) {
                    if (drawableName == null) {
                        val component: ComponentName? = launchIntent.component

                        if (component != null) {
                            val drawableNames = exactComponentDrawable[
                                component.packageName + '/' + component.className
                            ]

                            if (drawableNames != null && drawableNames.isNotEmpty()) {
                                drawableName = drawableNames[0]
                            }
                        }
                    }

                    if (drawableName != null) {
                        future.complete(getDrawable(drawableName))
                    }
                }

                future.complete(null)
            })

            return future
        }

        internal fun getDrawableNameList(packageName: String): CompletableFuture<List<String>?> {
            val future = CompletableFuture<List<String>?>()

            load(Runnable {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

                if (launchIntent != null) {
                    val component: ComponentName? = launchIntent.component

                    if (component != null) {
                        future.complete(packageNameDrawables[component.packageName])
                    }
                }

                future.complete(null)
            })

            return future
        }

        open fun getLabel(): String = application.getLabel()

        open fun getIcon(): Drawable? = application.getIcon()

        open fun getComponentCount(): CompletableFuture<Int> {
            val future = CompletableFuture<Int>()

            UiUtils.post {
                if (cached) {
                    future.complete(exactComponentDrawable.size)
                } else {
                    Utils.submitTask {
                        load(Runnable { future.complete(exactComponentDrawable.size) })
                    }
                }
            }

            return future
        }

        override fun equals(other: Any?): Boolean {
            return other is IconPack && application == other.application
        }

        override fun hashCode(): Int = application.hashCode()

        open fun invalidate() {
            exactComponentDrawable.clear()
            cached = false
            load(null)
        }
    }

    fun interface OnChangeListener {
        fun onChange()
    }

    companion object {
        const val ICON_PACK_ENTRY = "com.stario.ICON_PACK"
        private const val JSON_ICON_PACK = "pack"
        private const val JSON_ICON_DRAWABLE_NAME = "drawable"
        private const val ICON_PACK_TAG = "IconPackManager"
        private val changedComponents = hashMapOf(
            "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity" to
                "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.GoogleAppImplicitMainInfoGatewayInternal",
            "com.google.android.apps.safetyhub/com.google.android.apps.safetyhub.LauncherActivity" to
                "com.google.android.apps.safetyhub/com.google.android.apps.safetyhub.home.HomePageAppInfoEntry"
        )
        private var instance: IconPackManager? = null

        @JvmStatic
        fun from(activity: ThemedActivity): IconPackManager = from(activity.applicationContext)

        @JvmStatic
        fun from(farol: Farol): IconPackManager {
            if (instance == null) {
                instance = IconPackManager(farol, null)
            }

            return instance!!
        }

        @JvmStatic
        fun from(farol: Farol, listener: OnChangeListener?): IconPackManager {
            if (instance == null) {
                instance = IconPackManager(farol, listener)
            } else {
                instance!!.listener = listener
            }

            return instance!!
        }
    }
}
