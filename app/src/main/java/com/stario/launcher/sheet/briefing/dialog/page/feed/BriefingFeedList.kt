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

package com.stario.launcher.sheet.briefing.dialog.page.feed

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import com.stario.launcher.preferences.Entry
import com.stario.launcher.themes.ThemedActivity
import org.json.JSONArray
import java.util.ArrayList
import androidx.core.content.edit
import androidx.core.view.size

open class BriefingFeedList private constructor(activity: ThemedActivity) {
    private val listeners: MutableList<FeedListener> = ArrayList()
    private val state: SharedPreferences = activity.getApplicationContext()
        .getSharedPreferences(Entry.BRIEFING)
    private val items: MutableList<Feed> = ArrayList()

    init {
        load(state.getString(FEEDS_KEY, null))
    }

    fun get(position: Int): Feed = items[position]

    fun size(): Int = items.size

    private fun load(feedsSerial: String?) {
        if (feedsSerial == null) {
            return
        }

        try {
            val array = JSONArray(feedsSerial)

            for (index in 0 until array.length()) {
                val feed = Feed.deserialize(array.get(index) as String)

                if (feed != null && !items.contains(feed)) {
                    items.add(feed)
                    for (listener in listeners) {
                        listener.onInserted(size() - 1)
                    }
                }
            }
        } catch (exception: Exception) {
            Log.e("BriefingFeedList", "Error loading feeds.", exception)

            state.edit {
                    remove(FEEDS_KEY)
                }
        }
    }

    fun add(feed: Feed?): Boolean {
        if (feed == null || items.contains(feed)) {
            return false
        }

        items.add(feed)
        serialize()

        for (listener in listeners) {
            listener.onInserted(size() - 1)
        }

        return true
    }

    fun updateName(feed: Feed?, name: String?) {
        val position = if (feed == null) -1 else items.indexOf(feed)
        updateName(position, name)
    }

    fun updateName(position: Int, name: String?) {
        if (position < 0 || position >= items.size || name == null) {
            return
        }

        items[position].title = name
        serialize()

        for (listener in listeners) {
            listener.onUpdated(position)
        }
    }

    fun remove(feed: Feed?) {
        val position = if (feed == null) -1 else items.indexOf(feed)
        remove(position)
    }

    fun remove(position: Int) {
        if (position < 0 || position >= items.size) {
            return
        }

        items.removeAt(position)
        serialize()

        for (listener in listeners) {
            listener.onRemoved(position)
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun serialize() {
        val serials = ArrayList<String?>()
        for (item in items) {
            serials.add(item.serialize())
        }

        state.edit(commit = true) {
                putString(FEEDS_KEY, JSONArray(serials).toString())
            }
    }

    fun addOnFeedUpdateListener(listener: FeedListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }

    fun removeOnFeedUpdateListener(listener: FeedListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    interface FeedListener {
        fun onInserted(index: Int) {}

        fun onUpdated(index: Int) {}

        fun onRemoved(index: Int) {}
    }

    companion object {
        private const val FEEDS_KEY = "com.stario.FEEDS"
        private var instance: BriefingFeedList? = null

        @JvmStatic
        fun from(activity: ThemedActivity): BriefingFeedList {
            if (instance == null) {
                instance = BriefingFeedList(activity)
            }

            return instance!!
        }

        @JvmStatic
        fun getInstance(): BriefingFeedList {
            return instance ?: throw RuntimeException("BriefingFeedList not initialized.")
        }
    }
}
