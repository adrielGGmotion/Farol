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

package com.farol.launcher.sheet.drawer.search.recyclers.adapters.suggestions

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.window.SplashScreen
import androidx.appcompat.content.res.AppCompatResources
import com.farol.launcher.R
import com.farol.launcher.sheet.drawer.search.SearchEngine
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.utils.Utils
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.Objects
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.size

open class AutosuggestAdapter(private val activity: ThemedActivity) : SuggestionSearchAdapter(activity, true) {
    companion object {
        private const val TAG = "com.farol.launcher.WebAdapter"
        private const val MAX_RESULTS = 5
        private const val AUTOSUGGEST_URL = "https://kagi.com/api/autosuggest?q="
    }

    private val suggestionResults = ArrayList<SuggestionEntry>()
    private var runningTask: CompletableFuture<ArrayList<SuggestionEntry>?>? = null
    private var currentQuery = ""

    init { setHasStableIds(true) }

    override fun update(query: String?) {
        runningTask?.takeUnless { it.isDone }?.cancel(true)
        if (!query.isNullOrEmpty()) {
            val engine = SearchEngine.getEngine(activity.applicationContext)
            val constraint = query.lowercase(java.util.Locale.getDefault())
            currentQuery = constraint
            runningTask = Utils.submitTask(Callable {
                val results = ArrayList<SuggestionEntry>()
                try {
                    val connection = URL(AUTOSUGGEST_URL + constraint).openConnection()
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val root = JSONArray(reader.lines().collect(Collectors.joining("\n")))
                    if (root.length() > 1) {
                        val target = root.get(1)
                        if (target is JSONArray) {
                            for (index in 0 until minOf(target.length(), MAX_RESULTS)) {
                                val result = target.getString(index)
                                results.add(SuggestionEntry(result, engine.getQuery(result).toUri()))
                            }
                        }
                    }
                } catch (exception: Exception) {
                    Log.e(TAG, "update: ", exception)
                }
                results
            })
            runningTask?.thenApply { results ->
                UiUtils.post {
                    if (currentQuery == constraint) {
                        suggestionResults.clear()
                        for (entry in results!!) suggestionResults.add(0, entry)
                        notifyInternal()
                    }
                    runningTask = null
                }
                results
            }
        } else {
            currentQuery = ""
            suggestionResults.clear()
            notifyInternal()
        }
    }

    override fun submit(): Boolean {
        val options = ActivityOptions.makeBasic()
        if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
            options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)
        }
        val engine = SearchEngine.getEngine(activity.applicationContext)
        val intent = Intent(Intent.ACTION_VIEW, engine.getQuery(currentQuery).toUri())
        if (engine.getIsWebOnly()) configureIntentToLaunchInBrowser(intent)
        activity.startActivity(intent, options.toBundle())
        return true
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        val holder = super.onCreateViewHolder(container, viewType)
        holder.icon.setLooseClipping(false)
        holder.icon.setIcon(LayerDrawable(arrayOf<Drawable>(
            activity.getAttributeData(com.google.android.material.R.attr.colorSecondaryContainer).toDrawable(),
            AppCompatResources.getDrawable(activity, R.drawable.ic_search)!!
        )))
        return holder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val entry = suggestionResults[index]
        viewHolder.itemView.setOnClickListener {
            val options = ActivityOptions.makeScaleUpAnimation(viewHolder.icon, 0, 0,
                viewHolder.icon.width, viewHolder.icon.height)
            if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
                options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)
            }
            val engine = SearchEngine.getEngine(activity.applicationContext)
            val intent = Intent(Intent.ACTION_VIEW, entry.uri)
            if (engine.getIsWebOnly()) configureIntentToLaunchInBrowser(intent)
            activity.startActivity(intent, options.toBundle())
        }
        viewHolder.label.text = entry.label
    }

    override fun getItemCount(): Int = suggestionResults.size
    override fun getItemId(position: Int): Long = suggestionResults[position].hashCode().toLong()

    private fun configureIntentToLaunchInBrowser(intent: Intent) {
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pm = activity.packageManager
        val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
        val resolveInfo = pm.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo != null) intent.setPackage(resolveInfo.activityInfo.packageName)
    }

    private class SuggestionEntry(val label: String, val uri: Uri) {
        private val hash = Objects.hash(label, uri)
        override fun hashCode(): Int = hash
    }
}
