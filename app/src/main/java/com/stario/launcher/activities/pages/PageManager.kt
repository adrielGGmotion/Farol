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

package com.stario.launcher.activities.pages

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.Pair
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.stario.launcher.R
import com.stario.launcher.activities.launcher.sheets.LauncherSheets
import com.stario.launcher.activities.pages.insert.InsertPageDialog
import com.stario.launcher.preferences.Entry
import com.stario.launcher.preferences.Vibrations
import com.stario.launcher.sheet.SheetDialogFragment
import com.stario.launcher.sheet.SheetType
import com.stario.launcher.themes.ThemedActivity
import com.stario.launcher.ui.Measurements
import com.stario.launcher.ui.common.DragShadowBuilder
import com.stario.launcher.ui.utils.UiUtils
import com.stario.launcher.ui.utils.animation.Animation
import java.lang.reflect.InvocationTargetException
import java.util.Hashtable
import androidx.core.view.isEmpty
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.content.edit

class PageManager : ThemedActivity() {
    private val pages = Hashtable<View, Class<out SheetDialogFragment>>()
    private val placeholders = ArrayList<Pair<ConstraintLayout, SheetType>>()

    @Suppress("DEPRECATION")
    private lateinit var broadcastManager: androidx.localbroadcastmanager.content.LocalBroadcastManager
    private lateinit var pagesContainer: ConstraintLayout
    private lateinit var preferences: SharedPreferences
    private lateinit var inflater: LayoutInflater
    private lateinit var container: ViewGroup
    private var dragging = false
    private lateinit var addLabel: View
    private lateinit var homePage: View
    private lateinit var add: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_manager)

        @Suppress("DEPRECATION")
        broadcastManager = androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
        preferences = applicationContext.getSharedPreferences(Entry.SHEET)
        inflater = LayoutInflater.from(this)

        pagesContainer = findViewById(R.id.pages_container)
        container = findViewById(R.id.container)
        homePage = findViewById(R.id.home)
        add = findViewById(R.id.add)
        addLabel = add.findViewById(R.id.add_label)

        add.setOnClickListener(object : View.OnClickListener {
            private var dialog: InsertPageDialog? = null
            private var showing = false

            override fun onClick(view: View) {
                if (dialog == null) {
                    dialog = InsertPageDialog(this@PageManager) { item ->
                        val type = getAvailableSpace(item.first)
                        preferences.edit {putString(item.second.name, type.toString())}
                        pages[inflatePage(type, item.second)] = item.second

                        if (pages.size == SheetDialogFragment.IMPLEMENTATIONS.size || pages.size == placeholders.size) {
                            hideAddButton()
                        }

                        val intent = Intent(LauncherSheets.ACTION_ADD_SHEET)
                        intent.putExtra(LauncherSheets.INTENT_SHEET_CLASS_EXTRA, item.second)
                        broadcastManager.sendBroadcastSync(intent)
                    }
                    dialog!!.setOnDismissListener { showing = false }
                }

                if (!showing) {
                    dialog!!.setItems(getItems())
                    dialog!!.show()
                    showing = true
                }
            }

            private fun getAvailableSpace(desiredLocation: SheetType): SheetType {
                var firstFreeSpace = SheetType.UNDEFINED
                for (pair in placeholders) {
                    if (pair.first.isEmpty()) {
                        if (desiredLocation == pair.second) {
                            firstFreeSpace = pair.second
                            break
                        } else if (firstFreeSpace == SheetType.UNDEFINED) {
                            firstFreeSpace = pair.second
                        }
                    }
                }
                return firstFreeSpace
            }

            private fun getItems(): List<Pair<SheetType, Class<out SheetDialogFragment>>> {
                val items = ArrayList<Pair<SheetType, Class<out SheetDialogFragment>>>()
                for (clazz in SheetDialogFragment.IMPLEMENTATIONS) {
                    if (pages.values.none { it == clazz }) {
                        items.add(Pair(SheetType.getDefaultSheetTypeForSheetDialogFragment(this@PageManager, clazz), clazz))
                    }
                }
                return items
            }
        })

        loadParams()
        homePage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            homePage.post { for (page in pages.keys) measure(page) }
        }
        loadPlaceholders()
        loadPages(pages)

        if (pages.size == SheetDialogFragment.IMPLEMENTATIONS.size || pages.size == placeholders.size) {
            add.visibility = View.GONE
        }

        findViewById<View>(R.id.gradient).animate()
            .alpha(0.5f).setDuration(Animation.EXTENDED.getDuration().toLong())

        val root = requireNotNull(getRoot())
        UiUtils.Notch.applyNotchMargin(root, UiUtils.Notch.Treatment.CENTER)
        Measurements.addStatusBarListener { value ->
            root.setPadding(root.paddingLeft, value ?: return@addStatusBarListener, root.paddingRight, root.paddingBottom)
        }
        Measurements.addNavListener { value ->
            root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, value ?: return@addNavListener)
        }
    }

    @SuppressLint("FindViewByIdCast")
    private fun loadPlaceholders() {
        placeholders.add(Pair(findViewById(R.id.left), SheetType.LEFT_SHEET))
        placeholders.add(Pair(findViewById(R.id.top), SheetType.TOP_SHEET))
        placeholders.add(Pair(findViewById(R.id.right), SheetType.RIGHT_SHEET))
        placeholders.add(Pair(findViewById(R.id.bottom), SheetType.BOTTOM_SHEET))

        for (pair in placeholders) {
            val view = pair.first
            val type = pair.second
            view.clipChildren = false
            view.clipToOutline = false
            view.clipToPadding = false

            view.setOnDragListener(object : View.OnDragListener {
                override fun onDrag(v: View, event: DragEvent): Boolean {
                    val draggedView = event.localState as View
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> {
                            dragging = true
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            return true
                        }
                        DragEvent.ACTION_DROP -> {
                            val parent = draggedView.parent
                            if (parent is ViewGroup && parent != view) {
                                parent.removeView(draggedView)
                                view.addView(draggedView)

                                val clazz = pages[draggedView]
                                if (clazz != null) {
                                    preferences.edit {putString(clazz.name, type.toString())}
                                    val intent = Intent(LauncherSheets.ACTION_MOVE_SHEET)
                                    intent.putExtra(LauncherSheets.INTENT_SHEET_CLASS_EXTRA, clazz)
                                    broadcastManager.sendBroadcastSync(intent)
                                }
                            }
                            reset(draggedView)
                            return true
                        }
                        DragEvent.ACTION_DRAG_ENDED -> {
                            reset(draggedView)
                            return true
                        }
                    }
                    return false
                }

                private fun reset(view: View) = resetDrag(view)
            })
        }
    }

    private fun loadPages(pages: MutableMap<View, Class<out SheetDialogFragment>>) {
        for (pair in SheetType.getStoredSheets(this)) {
            if (pair.first != SheetType.UNDEFINED) {
                pages[inflatePage(pair.first, pair.second)] = pair.second
            }
        }
    }

    private fun showAddButton() {
        if (add.isVisible) return

        add.setOnTouchListener(null)
        add.animate().cancel()
        add.alpha = 0f
        add.scaleX = ADD_BUTTON_ANIMATION_SCALE_FACTOR
        add.scaleY = ADD_BUTTON_ANIMATION_SCALE_FACTOR
        add.visibility = View.VISIBLE
        add.animate().alpha(1f).scaleY(1f).scaleX(1f)
            .setDuration(Animation.MEDIUM.getDuration().toLong())
            .setInterpolator(FastOutSlowInInterpolator()).setListener(null).start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun hideAddButton() {
        if (add.isGone) return

        add.setOnTouchListener { _, _ -> false }
        add.animate().alpha(0f)
            .scaleY(ADD_BUTTON_ANIMATION_SCALE_FACTOR)
            .scaleX(ADD_BUTTON_ANIMATION_SCALE_FACTOR)
            .setDuration(Animation.MEDIUM.getDuration().toLong())
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    add.visibility = View.GONE
                }
            }).start()
    }

    private fun inflatePage(type: SheetType, clazz: Class<out SheetDialogFragment>): View {
        val page = inflater.inflate(R.layout.system_page, pagesContainer, false) as ViewGroup
        page.clipToOutline = false
        page.clipChildren = false
        measure(page)

        val remove = page.findViewById<View>(R.id.remove)
        remove.setOnClickListener {
            val parent = page.parent
            if (parent != null) {
                (parent as ViewGroup).removeView(page)
                pages.remove(page)
                showAddButton()
                preferences.edit {putString(clazz.name, SheetType.UNDEFINED.toString())}
                val intent = Intent(LauncherSheets.ACTION_REMOVE_SHEET)
                intent.putExtra(LauncherSheets.INTENT_SHEET_CLASS_EXTRA, clazz)
                broadcastManager.sendBroadcastSync(intent)
            }
        }

        var name: String? = null
        try {
            name = clazz.getMethod("getName").invoke(null) as String
        } catch (exception: NoSuchMethodException) {
            Log.e(TAG, "inflatePage: ${clazz.name} does not implement getName(). Defaulting to class name...")
        } catch (exception: InvocationTargetException) {
            Log.e(TAG, "inflatePage: ${clazz.name} does not implement getName(). Defaulting to class name...")
        } catch (exception: IllegalAccessException) {
            Log.e(TAG, "inflatePage: ${clazz.name} getName() method is not publicly visible. Defaulting to class name...")
        } catch (exception: ClassCastException) {
            Log.e(TAG, "inflatePage: ${clazz.name} getName() return type is not ${String::class.java.name}. Defaulting to class name...")
        } finally {
            page.findViewById<TextView>(R.id.name).text = (name ?: clazz.simpleName).replace(" ", System.lineSeparator())
        }

        val pageContainer = page.findViewById<View>(R.id.page_container)
        pageContainer.setOnDragListener(object : View.OnDragListener {
            override fun onDrag(v: View, event: DragEvent): Boolean {
                val draggedPage = event.localState as View
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> return draggedPage != page
                    DragEvent.ACTION_DRAG_ENTERED,
                    DragEvent.ACTION_DRAG_EXITED,
                    DragEvent.ACTION_DRAG_LOCATION,
                    DragEvent.ACTION_DRAG_ENDED -> return true
                    DragEvent.ACTION_DROP -> {
                        val pageParent = page.parent
                        if (pageParent !is ViewGroup) return false

                        val parent = draggedPage.parent
                        val view = pageParent
                        if (parent is ViewGroup && parent != view) {
                            val otherPage = view.getChildAt(0)
                            view.removeView(otherPage)
                            parent.addView(otherPage)

                            for (pair in placeholders) {
                                if (pair.first == parent) {
                                    pages[otherPage]?.let { clazz ->
                                        preferences.edit {putString(clazz.name, pair.second.toString())}
                                    }
                                    break
                                }
                            }

                            parent.removeView(draggedPage)
                            view.addView(draggedPage)

                            for (pair in placeholders) {
                                if (pair.first == view) {
                                    pages[draggedPage]?.let { clazz ->
                                        preferences.edit {putString(clazz.name, pair.second.toString())}
                                    }
                                    break
                                }
                            }

                            val intent = Intent(LauncherSheets.ACTION_MOVE_SHEET)
                            intent.putExtra(LauncherSheets.INTENT_SHEET_CLASS_EXTRA,
                                arrayOf(pages[draggedPage], pages[otherPage]))
                            broadcastManager.sendBroadcastSync(intent)
                        }
                        resetDrag(draggedPage)
                        return true
                    }
                }
                return false
            }
        })
        val touchPoint = Point()
        pageContainer.isHapticFeedbackEnabled = false
        pageContainer.setOnLongClickListener {
            Vibrations.getInstance().vibrate()
            val dragData = ClipData(clazz.name,
                arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                ClipData.Item(page.tag as CharSequence?))
            remove.visibility = View.INVISIBLE
            val shadowBuilder = DragShadowBuilder(page, touchPoint)
            page.startDragAndDrop(dragData, shadowBuilder, page, 0)
            page.visibility = View.INVISIBLE
            true
        }
        @SuppressLint("ClickableViewAccessibility")
        pageContainer.setOnTouchListener { _, event: MotionEvent ->
            touchPoint.x = event.x.toInt()
            touchPoint.y = event.y.toInt()
            false
        }

        for (pair in placeholders) {
            if (pair.second == type) {
                pair.first.addView(page)
                break
            }
        }
        return page
    }

    private fun resetDrag(view: View) {
        view.visibility = View.VISIBLE
        view.findViewById<View>(R.id.remove)?.visibility = View.VISIBLE
        dragging = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun measure(page: View) {
        val params = page.layoutParams
        params.height = homePage.measuredHeight
        params.width = homePage.measuredWidth
        page.layoutParams = params
    }

    private fun loadParams() {
        val params = pagesContainer.layoutParams as ConstraintLayout.LayoutParams
        if (Measurements.isLandscape()) {
            params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
            params.width = 0
            params.dimensionRatio = "H,9:16"
            addLabel.visibility = View.VISIBLE
        } else {
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
            params.height = 0
            params.dimensionRatio = "W,16:9"
            addLabel.visibility = View.GONE
        }
        pagesContainer.layoutParams = params
        getRoot()?.requestLayout()
    }

    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        loadParams()
    }

    override fun isOpaque() = true

    override fun isAffectedByBackGesture() = !dragging

    companion object {
        private const val TAG = "PageManager"
        private const val ADD_BUTTON_ANIMATION_SCALE_FACTOR = 0.5f
    }
}
