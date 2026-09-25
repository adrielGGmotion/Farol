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

package com.farol.launcher.sheet.briefing.rss

import android.util.Log
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import java.util.concurrent.CompletableFuture

class RSSHelper private constructor() {
    companion object {
        private const val TAG = "RSSHelper"
        private var reader: RssParser? = null

        @JvmStatic
        fun futureParse(url: String): CompletableFuture<RssChannel> {
            if (reader == null) {
                reader = RssParserBuilder().build()
            }

            return RSSHelperKt.parseFeed(reader!!, url)
        }

        @JvmStatic
        fun parse(url: String): List<RssItem>? {
            if (reader == null) {
                reader = RssParserBuilder().build()
            }

            return try {
                val channel = RSSHelperKt.parseFeed(reader!!, url).get()
                channel.items
            } catch (exception: Exception) {
                Log.e(TAG, "parse: ", exception)
                null
            }
        }
    }
}
