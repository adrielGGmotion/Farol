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


package com.stario.launcher.activities.settings.dialogs.icons

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.slider.Slider
import com.stario.launcher.R
import com.stario.launcher.preferences.Entry
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.dialogs.ActionDialog
import com.stario.launcher.ui.icons.AdaptiveIconView
import com.stario.launcher.ui.icons.PathCornerTreatmentAlgorithm
import com.stario.launcher.ui.recyclers.DividerItemDecorator
import androidx.core.content.edit

open class IconsDialog(activity: ThemedActivity) : ActionDialog(activity) {
    private val preferences: SharedPreferences = activity.getApplicationContext().getSharedPreferences(Entry.ICONS)
    private var adapter: IconsRecyclerAdapter? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun inflateContent(inflater: LayoutInflater, parent: android.view.ViewGroup): View {
        val root = inflater.inflate(R.layout.pop_up_icons, parent, false)

        val slider = root.findViewById<Slider>(R.id.slider)
        val algorithm = root.findViewById<MaterialButtonToggleGroup>(R.id.algorithm)

        val recycler = root.findViewById<RecyclerView>(R.id.recycler)
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
                if (event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP) {
                    behavior.setDraggable(true)
                } else {
                    behavior.setDraggable(scrolledToTop)
                }

                return false
            }
        })

        adapter = IconsRecyclerAdapter(activity) { dismiss() }

        slider.setValueFrom(0f)
        slider.setValueTo(1f)
        slider.setStepSize(0.01f)

        slider.setValue(
            (preferences.getFloat(AdaptiveIconView.CORNER_RADIUS_ENTRY, AdaptiveIconView.DEFAULT_CORNER_RADIUS) * 100f)
                .toInt() / 100f
        )
        slider.addOnChangeListener { _, value, _ ->
            preferences.edit {
                    putFloat(AdaptiveIconView.CORNER_RADIUS_ENTRY, value)
                }
        }

        val currentPathCornerTreatmentAlgorithm = PathCornerTreatmentAlgorithm.fromIdentifier(
            preferences.getInt(
                PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                PathCornerTreatmentAlgorithm.DEFAULT_PATH_ALGORITHM_ENTRY
            )
        )

        if (currentPathCornerTreatmentAlgorithm == PathCornerTreatmentAlgorithm.SQUIRCLE) {
            algorithm.check(R.id.squircle)
        } else {
            algorithm.check(R.id.regular)
        }

        algorithm.addOnButtonCheckedListener { _, checkedId, isChecked ->
            preferences.edit {
                putInt(
                    PathCornerTreatmentAlgorithm.PATH_ALGORITHM_ENTRY,
                    if (checkedId == R.id.squircle && isChecked) {
                        PathCornerTreatmentAlgorithm.SQUIRCLE.ordinal
                    } else {
                        PathCornerTreatmentAlgorithm.REGULAR.ordinal
                    }
                )
            }
        }

        recycler.addItemDecoration(DividerItemDecorator(getContext(), MaterialDividerItemDecoration.VERTICAL))
        recycler.setLayoutManager(LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false))
        recycler.setAdapter(adapter)

        return root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun show() {
        super.show()
        adapter?.notifyDataSetChanged()
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED
}
