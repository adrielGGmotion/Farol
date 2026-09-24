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

package com.stario.launcher.sheet.drawer.search.recyclers.adapters.suggestions

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.Toast
import android.window.SplashScreen
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.stario.launcher.R
import com.stario.launcher.apps.LauncherApplication
import com.stario.launcher.apps.ProfileManager
import com.stario.launcher.apps.interfaces.LauncherApplicationListener
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.utils.Utils

open class OptionAdapter(private val activity: ThemedActivity) : SuggestionSearchAdapter(activity, false) {
    companion object {
        private const val TAG = "com.stario.launcher.OptionAdapter"
        private val PREDEFINED_URIS = arrayOf("market://search?q=", "geo:?q=")
    }

    private val applicationManager = ProfileManager.getInstance().getProfile(null)!!
    private val packageManager = activity.packageManager
    private val options = ArrayList<OptionEntry>()
    private val listener: LauncherApplicationListener
    private var recyclerView: RecyclerView? = null
    private var show = false
    private var query: String? = null

    init {
        Utils.submitTask(Runnable {
            for (uri in PREDEFINED_URIS) {
                val resolvers = packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri)), PackageManager.MATCH_ALL)
                for (info in resolvers) {
                    val application = applicationManager.get(info.activityInfo.packageName)
                    if (application != LauncherApplication.FALLBACK_APP) {
                        val entry = OptionEntry(application!!, uri)
                        if (!options.contains(entry)) options.add(entry)
                    }
                }
            }

            val filters = arrayOf(Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH)
            for (filter in filters) {
                val resolvers = packageManager.queryIntentActivities(Intent(filter), PackageManager.MATCH_ALL)
                for (info in resolvers) {
                    val application = applicationManager.get(info.activityInfo.packageName)
                    val activityInfo = info.activityInfo
                    if (application != LauncherApplication.FALLBACK_APP && activityInfo != null &&
                        activityInfo.enabled && activityInfo.exported &&
                        !activityInfo.name.lowercase(java.util.Locale.getDefault()).contains("redirect") &&
                        (activityInfo.permission == null || ContextCompat.checkSelfPermission(activity,
                            activityInfo.permission) == PackageManager.PERMISSION_GRANTED)) {
                        val entry = OptionEntry(application!!, activityInfo, filter)
                        if (!options.contains(entry)) options.add(entry)
                    }
                }
            }
            UiUtils.post { notifyInternal() }
        })

        listener = object : LauncherApplicationListener {
            private fun insert(application: LauncherApplication) {
                recyclerView!!.post {
                    val filters = arrayOf(Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH)
                    for (filter in filters) {
                        val intent = Intent(filter)
                        intent.setPackage(application.getInfo().packageName)
                        val resolved = packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
                        if (resolved.isNotEmpty()) {
                            options.add(OptionEntry(application, resolved[0].activityInfo, filter))
                            notifyInternal()
                        }
                    }
                }
            }

            private fun remove(application: LauncherApplication) {
                recyclerView!!.post {
                    val iterator = options.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (entry.application == application) {
                            iterator.remove()
                            return@post
                        }
                    }
                }
            }

            override fun onInserted(application: LauncherApplication) = insert(application)
            override fun onShowed(application: LauncherApplication) = insert(application)
            override fun onRemoved(application: LauncherApplication) = remove(application)
            override fun onHidden(application: LauncherApplication) = remove(application)
            override fun onUpdated(application: LauncherApplication) {
                recyclerView!!.post {
                    for (index in options.indices) {
                        if (options[index].application == application) {
                            notifyItemChanged(index)
                            return@post
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun update(query: String?) {
        this.query = query
        if (query!!.isNotEmpty()) {
            if (!show) {
                show = true
                invalidateRecyclerVisibility()
            }
        } else if (show) {
            show = false
            invalidateRecyclerVisibility()
        }
    }

    override fun submit(): Boolean {
        val recycler = recyclerView ?: return false
        if (recycler.layoutManager != null && recycler.visibility == View.VISIBLE) {
            val view = recycler.layoutManager?.findViewByPosition(0)
            if (view != null) {
                view.callOnClick()
                return true
            }
        }
        return false
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val entry = options[index]
        viewHolder.label.text = activity.resources.getString(R.string.search_on) + " " + entry.application.getLabel()
        viewHolder.icon.setApplication(entry.application)
        viewHolder.itemView.setOnClickListener {
            val options = ActivityOptions.makeScaleUpAnimation(viewHolder.icon, 0, 0,
                viewHolder.icon.width, viewHolder.icon.height)
            if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
                options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)
            }
            val intent = entry.getIntent()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val uri = intent.data
            if (uri != null) intent.data = Uri.parse(uri.toString() + query)
            else intent.putExtra(SearchManager.QUERY, query)
            try {
                activity.startActivity(intent, options.toBundle())
            } catch (exception: Exception) {
                Toast.makeText(activity, "Unable to launch activity", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "onBindViewHolder: ", exception)
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        applicationManager.addApplicationListener(listener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        applicationManager.removeApplicationListener(listener)
        this.recyclerView = null
    }

    override fun getItemCount(): Int = if (show) options.size else 0
    override fun getItemId(position: Int): Long = options[position].hashCode().toLong()

    private class OptionEntry(val application: LauncherApplication, private val referenceIntent: Intent) {
        constructor(application: LauncherApplication, info: ActivityInfo, filter: String) : this(
            application, Intent().setComponent(ComponentName(info.packageName, info.name)).setAction(filter))
        constructor(application: LauncherApplication, uri: String) : this(
            application, Intent().setPackage(application.getInfo().packageName)
                .setAction(Intent.ACTION_VIEW).setData(Uri.parse(uri)))

        fun getIntent(): Intent = Intent(referenceIntent)
        override fun hashCode(): Int = application.getInfo().hashCode()
        override fun equals(other: Any?): Boolean = this === other || other is OptionEntry && other.application == application
    }
}
