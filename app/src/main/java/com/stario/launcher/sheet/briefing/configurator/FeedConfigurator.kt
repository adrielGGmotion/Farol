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

package com.stario.launcher.sheet.briefing.configurator

import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.stario.launcher.R
import com.stario.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList
import com.stario.launcher.sheet.briefing.dialog.page.feed.Feed
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import carbon.view.SimpleTextWatcher

open class FeedConfigurator(
    activity: ThemedActivity,
    private val feed: Feed
) : ActionDialog(activity) {
    private val list = BriefingFeedList.from(activity)
    private var name: EditText? = null

    override fun inflateContent(inflater: LayoutInflater): View {
        val contentView = inflater.inflate(R.layout.feed_configurator, null) as ViewGroup

        val name = contentView.findViewById<EditText>(R.id.name)
        this.name = name
        val warning = contentView.findViewById<View>(R.id.warning)

        name.setText(feed.getTitle())
        name.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(editable: Editable) {
                if (editable.length == 0) {
                    warning.setVisibility(View.VISIBLE)
                } else {
                    warning.setVisibility(View.GONE)
                }
            }
        })

        return contentView
    }

    override fun dismiss() {
        super.dismiss()

        val name = name ?: return
        val editable = name.getText()
        val currentTitle = feed.getTitle()

        if (editable.length > 0 &&
            (currentTitle == null || currentTitle != editable.toString())
        ) {
            list.updateName(feed, editable.toString())
        }
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED
}
