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

package com.farol.launcher.sheet.drawer.search.recyclers.adapters

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.window.SplashScreen
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.farol.launcher.R
import com.farol.launcher.exceptions.Unauthorized
import com.farol.launcher.preferences.Entry
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.utils.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Objects
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import java.util.stream.Collectors
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import androidx.core.view.size

open class WebAdapter(private val activity: ThemedActivity) : AbstractSearchListAdapter<WebAdapter.ViewHolder>() {
    companion object {
        const val SEARCH_RESULTS = "com.stario.SEARCH_RESULTS"
        const val KAGI_API_KEY = "com.stario.KAGI_API_KEY"
        private const val TAG = "com.farol.launcher.WebAdapter"
        private const val RESULTS_URL = "https://kagi.com/api/v0/search?limit=6&q="
        private val BASE_URL_REGEX = Pattern.compile("^(?:https?://)?(?:www\\.)?([^/?:]+)")
        private val PATH_REGEX = Pattern.compile("^(?:https?://)?(?:www\\.)?[^/]+(/[^/?#]+)+")

        @JvmStatic
        private fun getBreadcrumbString(baseUrl: String, url: String): String {
            val breadcrumb = StringBuilder(baseUrl)
            val pathMatcher = PATH_REGEX.matcher(url)
            if (pathMatcher.find()) {
                val path = pathMatcher.group(1)
                if (path != null) {
                    for (segment in path.split("/")) {
                        if (segment.isNotEmpty()) breadcrumb.append(" > ").append(segment)
                    }
                }
            }
            return breadcrumb.toString()
        }
    }

    private val preferences: SharedPreferences = activity.applicationContext.getSharedPreferences(Entry.SEARCH)
    private val searchResults = ArrayList<WebEntry>()
    private var runningTask: CompletableFuture<ArrayList<WebEntry>?>? = null
    private var listener: UnauthorizedListener? = null

    open class ViewHolder @SuppressLint("ClickableViewAccessibility") constructor(item: ViewGroup) : RecyclerView.ViewHolder(item) {
        val title: TextView = item.findViewById(R.id.title)
        val snippet: TextView = item.findViewById(R.id.snippet)
        val breadcrumbs: TextView = item.findViewById(R.id.breadcrumbs)
        val favicon: ImageView = item.findViewById(R.id.favicon)

        init { item.isHapticFeedbackEnabled = false }
    }

    override fun update(query: String?) {
        searchResults.clear()
        notifyInternal()
        runningTask?.takeUnless { it.isDone }?.cancel(true)

        val searchEnabled = preferences.getBoolean(SEARCH_RESULTS, false)
        val apiKey = preferences.getString(KAGI_API_KEY, null)
        if (searchEnabled && !apiKey.isNullOrEmpty() && !query.isNullOrEmpty()) {
            val constraint = query.lowercase(java.util.Locale.getDefault())
            runningTask = Utils.submitTask(Callable {
                val results = ArrayList<WebEntry>()
                val json = try {
                    getData(constraint, apiKey)
                } catch (exception: Unauthorized) {
                    UiUtils.post { listener?.onDenied() }
                    return@Callable results
                }

                if (json != null && json.has("data")) {
                    val data = json.get("data")
                    if (data is JSONArray) {
                        for (index in 0 until data.length()) {
                            val entry = data.get(index)
                            if (entry is JSONObject) {
                                val url = if (entry.has("url")) entry.get("url") as? String else null
                                val title = if (entry.has("title")) entry.get("title") as? String else null
                                val snippet = if (entry.has("snippet")) entry.get("snippet") as? String else null
                                if (url != null && title != null) results.add(WebEntry(url, title, snippet))
                            }
                        }
                    }
                }
                results
            })

            runningTask?.thenApply { results ->
                UiUtils.post {
                    searchResults.clear()
                    for (entry in results!!) searchResults.add(0, entry)
                    notifyInternal()
                    runningTask = null
                }
                results
            }
        }
    }

    private fun getData(query: String, key: String): JSONObject? {
        try {
            val connection = URL(RESULTS_URL + query).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bot $key")
            connection.addRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (connection.responseCode in 200 until 400) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                return JSONObject(reader.lines().collect(Collectors.joining("\n")))
            } else if (connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw Unauthorized()
            }
        } catch (exception: IOException) {
            Log.e(TAG, "getData: ", exception)
        } catch (exception: JSONException) {
            Log.e(TAG, "getData: ", exception)
        }
        return null
    }

    override fun submit(): Boolean = true

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(activity).inflate(R.layout.search_item, container, false) as ViewGroup)

    override fun onBindViewHolder(viewHolder: ViewHolder, index: Int) {
        val entry = searchResults[index]
        viewHolder.title.text = Html.fromHtml(entry.title, Html.FROM_HTML_MODE_LEGACY)
        if (entry.snippet != null) {
            viewHolder.snippet.text = Html.fromHtml(entry.snippet, Html.FROM_HTML_MODE_LEGACY)
            viewHolder.snippet.visibility = View.VISIBLE
        } else {
            viewHolder.snippet.visibility = View.GONE
        }
        viewHolder.favicon.visibility = View.GONE
        val baseMatcher = BASE_URL_REGEX.matcher(entry.url)
        if (baseMatcher.find() && !activity.isDestroyed) {
            Glide.with(activity)
                .load("https://f" + Math.floor(Math.random() * 9 + 1).toInt() + ".allesedv.com/32/" + baseMatcher.group(1))
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean = false
                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        viewHolder.favicon.visibility = View.VISIBLE
                        viewHolder.favicon.setImageDrawable(resource)
                        return true
                    }
                }).into(viewHolder.favicon)
            viewHolder.breadcrumbs.text = getBreadcrumbString(baseMatcher.group(1)!!, entry.url)
        }
        viewHolder.itemView.setOnClickListener {
            val options = ActivityOptions.makeClipRevealAnimation(viewHolder.itemView, 0, 0,
                viewHolder.itemView.width, viewHolder.itemView.height)
            if (Utils.isMinimumSDK(Build.VERSION_CODES.TIRAMISU)) {
                options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)
            }
            activity.startActivity(Intent(Intent.ACTION_VIEW, entry.url.toUri()), options.toBundle())
        }
    }

    override fun getItemCount(): Int = searchResults.size

    fun setUnauthorizedListener(listener: UnauthorizedListener?) {
        this.listener = listener
    }

    fun interface UnauthorizedListener {
        fun onDenied()
    }

    private class WebEntry(val url: String, val title: String, val snippet: String?) {
        private val hash = Objects.hash(url, title, snippet)
        override fun hashCode(): Int = hash
    }
}
