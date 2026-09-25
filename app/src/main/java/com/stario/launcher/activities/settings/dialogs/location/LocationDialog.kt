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

package com.stario.launcher.activities.settings.dialogs.location

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.stario.launcher.R
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import com.stario.launcher.ui.recyclers.DividerItemDecorator
import com.stario.launcher.ui.utils.UiUtils

open class LocationDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private var listener: OnLocationUpdate? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val root = inflater.inflate(R.layout.pop_up_location, parent, false)

        val query = root.findViewById<EditText>(R.id.query)
        val recycler = root.findViewById<RecyclerView>(R.id.recycler)

        recycler.addItemDecoration(DividerItemDecorator(getContext(), MaterialDividerItemDecoration.VERTICAL))
        recycler.setLayoutManager(LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false))
        recycler.setItemAnimator(null)
        recycler.setOnTouchListener(object : View.OnTouchListener {
            private val behavior: BottomSheetBehavior<*> = getBehavior()
            private var scrolledToTop = true

            init {
                recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        scrolledToTop = recyclerView.computeVerticalScrollOffset() == 0
                    }
                })
            }

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (UiUtils.isKeyboardVisible(view)) {
                    behavior.setDraggable(false)
                } else if (event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP) {
                    behavior.setDraggable(true)
                } else {
                    behavior.setDraggable(scrolledToTop)
                }

                return false
            }
        })

        val adapter = LocationRecyclerAdapter(activity) { view ->
            UiUtils.hideKeyboard(view)

            val behavior = getBehavior()
            behavior.setDraggable(true)
            behavior.setState(BottomSheetBehavior.STATE_HIDDEN)

            listener?.onUpdate()
        }
        recycler.setAdapter(adapter)

        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(editable: Editable?) {
                adapter.update(editable?.toString())
            }
        })

        return root
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED

    open fun setOnLocationUpdateListener(listener: OnLocationUpdate?) {
        this.listener = listener
    }

    fun interface OnLocationUpdate {
        fun onUpdate()
    }
}
