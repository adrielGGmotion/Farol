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

package com.stario.launcher.sheet.briefing.dialog.page

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.prof18.rssparser.model.RssItem
import com.stario.launcher.R
import com.stario.launcher.Stario
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.ui.common.text.ClickableSpanTextView
import com.stario.launcher.ui.utils.animation.Animation
import com.stario.launcher.utils.Utils
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

internal open class FeedPageAdapter(
    private val context: Stario
) : RecyclerView.Adapter<FeedPageAdapter.ViewHolder>() {
    private var items: List<RssItem> = Collections.synchronizedList(ArrayList())
    @Volatile
    private var lastUpdate: Long = -1

    fun update(items: List<RssItem>) {
        val filteredList = ArrayList<RssItem>()

        for (item in items) {
            val itemTitle = item.title
            if (itemTitle != null && itemTitle.isNotBlank()) {
                filteredList.add(item)
            }
        }

        if (filteredList.isNotEmpty()) {
            val diffResult = DiffUtil.calculateDiff(RssItemDiffUtil(this.items, filteredList))

            this.items = Collections.synchronizedList(filteredList)

            diffResult.dispatchUpdatesTo(this)

            lastUpdate = System.currentTimeMillis()
        }
    }

    fun shouldUpdate(): Boolean =
        System.currentTimeMillis() - lastUpdate > UPDATE_TIME_THRESHOLD &&
            Utils.isNetworkAvailable(context)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        internal val description: ClickableSpanTextView = itemView.findViewById(R.id.description)
        internal val representative: ViewGroup = itemView.findViewById(R.id.representative)
        internal val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        internal val display: ImageView = itemView.findViewById(R.id.display)
        internal val category: TextView = itemView.findViewById(R.id.category)
        internal val author: TextView = itemView.findViewById(R.id.author)
        internal val title: TextView = itemView.findViewById(R.id.title)

        init {
            itemView.setClipToOutline(true)
            itemView.setOnClickListener(this)

            description.setOnSpanClickListener { view, span ->
                Vibrations.getInstance().vibrate()
                span.onClick(view)
            }
        }

        override fun onClick(view: View) {
            val index = getBindingAdapterPosition()
            if (index == RecyclerView.NO_POSITION) {
                return
            }

            val item = items[index]
            Vibrations.getInstance().vibrate()

            val intent = item.link?.let { link ->
                Intent(Intent.ACTION_VIEW, Uri.parse(link))
            } ?: item.guid?.let { guid ->
                Intent(Intent.ACTION_VIEW, Uri.parse(guid))
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = items[position]

        viewHolder.title.setVisibility(View.GONE)
        viewHolder.author.setVisibility(View.GONE)
        viewHolder.category.setVisibility(View.GONE)
        viewHolder.description.setVisibility(View.GONE)
        viewHolder.timestamp.setVisibility(View.GONE)
        viewHolder.display.setAlpha(0f)

        val image = item.image
        if (image != null) {
            viewHolder.representative.setVisibility(View.VISIBLE)

            Glide.with(context)
                .load(image)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        exception: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        viewHolder.representative.setVisibility(View.GONE)
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        viewHolder.representative.setVisibility(View.VISIBLE)
                        viewHolder.display.animate()
                            .alpha(1f)
                            .setDuration(Animation.MEDIUM.getDuration().toLong())

                        if (item.categories.isNotEmpty()) {
                            val text = cleanHtml(item.categories[0])!!

                            if (text.toString().isNotEmpty()) {
                                viewHolder.category.setText(text)
                                viewHolder.category.setVisibility(View.VISIBLE)
                            }
                        }

                        return false
                    }
                })
                .into(viewHolder.display)
        } else {
            viewHolder.representative.setVisibility(View.GONE)
        }

        val itemTitle = item.title
        if (itemTitle != null && itemTitle.isNotEmpty()) {
            val title = cleanHtml(itemTitle)!!

            if (title.toString().isNotEmpty()) {
                viewHolder.title.setText(title)
                viewHolder.title.setVisibility(View.VISIBLE)
            }
        }

        var content: String? = null
        val itemDescription = item.description
        if (itemDescription != null && !itemDescription.isEmpty()) {
            content = itemDescription
        } else {
            val itemContent = item.content
            if (itemContent != null && !itemContent.isEmpty()) {
                content = itemContent
            }
        }

        if (content != null) {
            val description = cleanHtml(content)!!

            if (description.toString().isNotEmpty()) {
                viewHolder.description.setText(description)
                viewHolder.description.setVisibility(View.VISIBLE)
            }
        }

        val itemAuthor = item.author
        if (itemAuthor != null && itemAuthor.isNotEmpty()) {
            val author = cleanHtml(itemAuthor)!!

            if (author.toString().isNotEmpty()) {
                viewHolder.author.setText(author)
                viewHolder.author.setVisibility(View.VISIBLE)
            }
        }

        val pubDate = item.pubDate
        if (pubDate != null && pubDate.isNotEmpty()) {
            try {
                val date: Date? = Utils.parseDate(pubDate)
                val output = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault())

                if (date != null) {
                    viewHolder.timestamp.setText(output.format(date))
                } else {
                    viewHolder.timestamp.setText(pubDate)
                }
            } catch (exception: Exception) {
                viewHolder.timestamp.setText(pubDate)
            } finally {
                viewHolder.timestamp.setVisibility(View.VISIBLE)
            }
        }
    }

    private fun cleanHtml(html: String?): Spanned? {
        if (html == null) {
            return null
        }

        return HtmlCompat.fromHtml(
            Jsoup.clean(html, CONTENT_SAFELIST),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(container.getContext())

        return ViewHolder(inflater.inflate(R.layout.article, container, false))
    }

    override fun getItemCount(): Int = items.size

    companion object {
        private val CONTENT_SAFELIST = Safelist().apply {
            addTags(
                "p", "br", "b", "strong", "i", "em", "u", "strike",
                "del", "a", "ul", "ol", "li", "h1", "h2", "h3", "h4"
            )
            addAttributes("a", "href")
        }

        private const val UPDATE_TIME_THRESHOLD = 900_000L
    }
}
