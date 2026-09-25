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

package com.farol.launcher.sheet.briefing.configurator

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.prof18.rssparser.model.RssChannel
import com.farol.launcher.R
import com.farol.launcher.Farol
import com.farol.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import com.farol.launcher.sheet.briefing.dialog.page.feed.Feed
import com.farol.launcher.sheet.briefing.rss.RSSHelper
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.common.text.PulsingTextView
import com.farol.launcher.ui.dialogs.ActionDialog
import com.farol.launcher.ui.utils.UiUtils
import com.farol.launcher.utils.Utils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import carbon.view.SimpleTextWatcher
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty

open class BriefingConfigurator(activity: ThemedActivity) : ActionDialog(activity) {
    private val list = BriefingFeedList.from(activity)

    @Volatile
    private var currentSearchTask: Future<*>? = null
    @Volatile
    private var validatedFeed: Feed? = null

    private var debounceRunnable: Runnable? = null
    private lateinit var contentView: ViewGroup
    private lateinit var limit: PulsingTextView
    private lateinit var preview: LinearLayout
    private lateinit var title: TextView
    private lateinit var query: EditText

    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        contentView = inflater.inflate(R.layout.briefing_configurator, parent, false) as ViewGroup

        query = contentView.findViewById(R.id.query)
        preview = contentView.findViewById(R.id.preview)
        title = contentView.findViewById(R.id.title)
        limit = contentView.findViewById(R.id.limit)

        query.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                debounceRunnable?.let { UiUtils.removeUICallback(it) }

                val currentSearchTask = currentSearchTask
                if (currentSearchTask != null && !currentSearchTask.isDone) {
                    currentSearchTask.cancel(true)
                }

                val text = charSequence.toString()

                if (text.isEmpty()) {
                    showStatus(null, false)
                    return
                }

                var validUrl: String? = null
                if (isValidUrl(text)) {
                    validUrl = text
                } else {
                    val potentialUrl = "https://$text"
                    if (isValidUrl(potentialUrl)) {
                        validUrl = potentialUrl
                    }
                }

                if (validUrl == null) {
                    showStatus(R.string.invalid_url, false)
                    return
                }

                if (!Utils.isNetworkAvailable(activity)) {
                    showStatus(R.string.no_connection, false)
                    return
                }

                val finalValidUrl = validUrl!!
                debounceRunnable = Runnable {
                    showStatus(R.string.searching, true)
                    this@BriefingConfigurator.currentSearchTask = Utils.submitTask(
                        FeedDiscoveryTask(
                            activity.getApplicationContext(),
                            arrayOf(
                                finalValidUrl,
                                finalValidUrl.replace(Regex("/$"), "") + ".rss"
                            )
                        )
                    )
                }

                UiUtils.postDelayed(debounceRunnable!!, DEBOUNCE_DELAY)
            }
        })

        contentView.findViewById<View>(R.id.add).setOnClickListener { view ->
            view.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.bounce_small))

            val feed = validatedFeed
            val feedTitle = feed?.getTitle()
            if (feed != null && feedTitle != null && feedTitle.isNotEmpty()) {
                val added = list.add(feed)

                if (added) {
                    val behavior = getBehavior()
                    behavior.setDraggable(false)
                    behavior.setState(BottomSheetBehavior.STATE_HIDDEN)

                    UiUtils.hideKeyboard(contentView)
                } else {
                    Toast.makeText(
                        activity,
                        R.string.already_subscribed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        contentView.findViewById<View>(R.id.paste).setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            if (clipboard.hasPrimaryClip()) {
                val clip: ClipData? = clipboard.getPrimaryClip()

                if (clip != null &&
                    clip.getDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                ) {
                    val text = clip.getItemAt(0).coerceToText(activity).toString()

                    if (text.isNotEmpty()) {
                        query.setText(text)
                        query.setSelection(text.length)
                    }
                }
            }
        }

        return contentView
    }

    override fun show() {
        if (::query.isInitialized) {
            query.setText(null)
        }

        super.show()
    }

    private fun isValidUrl(url: String): Boolean =
        URLUtil.isValidUrl(url) && Patterns.WEB_URL.matcher(url).matches()

    private fun showStatus(messageRes: Int?, pulsating: Boolean) {
        val resources: Resources = activity.getResources()

        preview.setVisibility(View.GONE)

        if (messageRes != null) {
            limit.setText(resources.getString(messageRes))
            limit.setPulsating(pulsating)
            limit.setVisibility(View.VISIBLE)
        } else {
            limit.setVisibility(View.GONE)
        }

        validatedFeed = null
    }

    private fun showPreview(feed: Feed) {
        validatedFeed = feed

        title.setText(feed.getTitle())
        preview.setVisibility(View.VISIBLE)
        limit.setVisibility(View.GONE)
    }

    private inner class FeedDiscoveryTask(
        private val context: Farol,
        private val urls: Array<String>
    ) : Runnable {
        override fun run() {
            if (Thread.currentThread().isInterrupted) {
                return
            }

            for (url in urls) {
                try {
                    val feed = attemptParse(url)

                    if (Thread.currentThread().isInterrupted) {
                        return
                    }

                    if (feed != null) {
                        contentView.post { showPreview(feed) }
                        return
                    }

                    val discoveredFeedUrl = discoverFeedUrl(url)

                    if (Thread.currentThread().isInterrupted) {
                        return
                    }

                    if (discoveredFeedUrl != null) {
                        val discoveredFeed = attemptParse(discoveredFeedUrl)

                        if (Thread.currentThread().isInterrupted) {
                            return
                        }

                        if (discoveredFeed != null) {
                            contentView.post { showPreview(discoveredFeed) }
                            return
                        }
                    }
                } catch (exception: IOException) {
                    Log.e(TAG, "IOException: ${exception.message}")
                }
            }

            contentView.post { showStatus(R.string.invalid_rss, false) }
        }

        private fun attemptParse(url: String): Feed? {
            var streamFuture: CompletableFuture<RssChannel>? = null

            try {
                streamFuture = RSSHelper.futureParse(url)
                val title = streamFuture.get(10, TimeUnit.SECONDS).title
                    ?: context.getString(R.string.unknown_feed)

                return Feed(title, url)
            } catch (exception: InterruptedException) {
                streamFuture?.cancel(true)
                Thread.currentThread().interrupt()
            } catch (exception: TimeoutException) {
                streamFuture?.cancel(true)
            } catch (exception: Exception) {
                streamFuture?.cancel(true)
            }

            return null
        }

        private fun discoverFeedUrl(url: String): String? {
            val document: Document = Jsoup.connect(url)
                .userAgent(Utils.USER_AGENT)
                .timeout(JSOUP_TIMEOUT)
                .get()

            val nodes: Elements = document.select("link[type*=\"rss\"], link[type*=\"atom\"]")

            for (node: Element in nodes) {
                if (node.hasAttr("href")) {
                    return node.attr("abs:href")
                }
            }

            return null
        }
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    companion object {
        private const val TAG = "BriefingConfigurator"
        private const val DEBOUNCE_DELAY = 300L
        private const val JSOUP_TIMEOUT = 5000
    }
}
