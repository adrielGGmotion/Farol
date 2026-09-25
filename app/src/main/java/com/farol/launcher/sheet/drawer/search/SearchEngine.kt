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

package com.farol.launcher.sheet.drawer.search

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.preferences.Entry
import com.farol.launcher.sheet.drawer.search.recyclers.adapters.WebAdapter
import androidx.core.content.edit

enum class SearchEngine(private val label: String, private val url: String, private val query: String, private val drawable: Int) {
    GOOGLE("Google", "google.com", "/search?q=", R.drawable.ic_google),
    DUCK_DUCK_GO("DuckDuckGo", "duckduckgo.com", "/?q=", R.drawable.ic_duck),
    BING("Bing", "bing.com", "/search?q=", R.drawable.ic_bing),
    BRAVE("Brave", "search.brave.com", "/search?q=", R.drawable.ic_brave),
    KAGI("Kagi", "kagi.com", "/search?q=", R.drawable.ic_kagi),
    PERPLEXITY("Perplexity AI", "perplexity.ai", "/?s=o&q=", R.drawable.ic_perplexity),
    ECOSIA("Ecosia", "ecosia.org", "/search?q=", R.drawable.ic_ecosia),
    YANDEX("Yandex", "yandex.com", "/search/?text=", R.drawable.ic_yandex),
    YAHOO("Yahoo", "search.yahoo.com", "/search?p=", R.drawable.ic_yahoo),
    CHATGPT("ChatGPT", "chatgpt.com", "/?q=", R.drawable.ic_chatgpt);

    fun getUrl(): String = url
    fun getQuery(query: String): String = "https://$url${this.query}$query"
    fun getDrawable(context: Context): Drawable? = AppCompatResources.getDrawable(context, drawable)
    fun getIsWebOnly(): Boolean = this == CHATGPT
    override fun toString(): String = label

    companion object {
        const val SEARCH_ENGINE = "com.stario.SEARCH_ENGINE"

        @JvmStatic
        fun getEngine(farol: Farol): SearchEngine {
            val preferences = farol.getSharedPreferences(Entry.SEARCH)
            if (preferences.getBoolean(WebAdapter.SEARCH_RESULTS, false)) return KAGI
            val engineString = preferences.getString(SEARCH_ENGINE, null)
            return entries.firstOrNull { it != GOOGLE && it.url == engineString } ?: GOOGLE
        }

        @JvmStatic
        fun setEngine(farol: Farol, engine: SearchEngine) {
            farol.getSharedPreferences(Entry.SEARCH).edit {putString(SEARCH_ENGINE, engine.url)}
        }
    }
}
