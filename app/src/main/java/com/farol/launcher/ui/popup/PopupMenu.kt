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

package com.farol.launcher.ui.popup

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.VectorDrawable
import android.transition.Transition
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow

import androidx.annotation.Size
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.transition.platform.MaterialElevationScale
import com.farol.launcher.R
import com.farol.launcher.themes.ThemedActivity
import com.farol.launcher.ui.Measurements
import com.farol.launcher.ui.recyclers.DividerItemDecorator
import com.farol.launcher.ui.recyclers.overscroll.OverScrollRecyclerView
import com.farol.launcher.ui.utils.animation.Animation

import java.util.HashMap
import java.util.Map
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isEmpty
import androidx.core.view.size

open class PopupMenu {
    companion object {
        const val PIVOT_DEFAULT: Short = 0
        const val PIVOT_CENTER_VERTICAL: Short = 1
        const val PIVOT_CENTER_HORIZONTAL: Short = 2

        private const val GENERAL_ID = 1
        private const val SHORTCUT_GROUP_ID = 2
        private const val MAX_SHORTCUT_COUNT = 4
        private const val INSET_FRACTION = 0.2f
        private const val ICON_SIZE = 64
        private const val SCREEN_MARGIN_DP = 20
        private const val PADDING = 9
        private const val WIDTH = 220
    }

    private val dismissListener: PopupWindow.OnDismissListener
    private val recyclers: HashMap<Int, kotlin.collections.Map.Entry<RecyclerView, RecyclerAdapter>>
    private val dismissOnItemClick: Boolean
    private val observer: LifecycleObserver
    private val popupWindow: PopupWindow
    private val activity: ThemedActivity
    private val root: LinearLayout

    private var oldOrientationFlags: Int = 0
    private var shortcutCount: Int = 0

    constructor(activity: ThemedActivity) : this(activity, true)

    constructor(activity: ThemedActivity, dismissOnItemClick: Boolean) {
        this.activity = activity
        this.dismissOnItemClick = dismissOnItemClick
        this.recyclers = HashMap()

        val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        this.root = inflater.inflate(R.layout.popup_window, android.widget.FrameLayout(activity), false) as LinearLayout
        this.popupWindow = PopupWindow(
            root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED)

        val enter: Transition = MaterialElevationScale(true)
        enter.setDuration(Animation.SHORT.getDuration().toLong())
        enter.setInterpolator(DecelerateInterpolator())

        popupWindow.setEnterTransition(enter)

        val exit: Transition = MaterialElevationScale(false)
        exit.setDuration(Animation.SHORT.getDuration().toLong())
        exit.setInterpolator(PathInterpolator(0.5f, 0f, 0.9f, 1.1f))

        popupWindow.setExitTransition(exit)

        this.observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                popupWindow.dismiss()
            }
        }
        this.dismissListener = PopupWindow.OnDismissListener {
            activity.setTouchEnabled(true)
            activity.setRequestedOrientation(oldOrientationFlags)
            activity.lifecycle.removeObserver(observer)
        }
        setOnDismissListener(dismissListener)
    }

    private fun getRecycler(identifier: Int): kotlin.collections.Map.Entry<RecyclerView, RecyclerAdapter> {
        if (recyclers.containsKey(identifier)) {
            return recyclers[identifier]!!
        }

        val recycler: RecyclerView = OverScrollRecyclerView(activity)
        val adapter = RecyclerAdapter(
            activity,
            if (dismissOnItemClick) View.OnClickListener { dismiss() } else null
        )

        val padding = Measurements.dpToPx(PADDING.toFloat())

        recycler.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        recycler.setPadding(padding, padding, padding, padding)

        recycler.setBackground(AppCompatResources.getDrawable(activity, R.drawable.popup_background))
        recycler.setItemAnimator(null)
        recycler.addItemDecoration(
            DividerItemDecorator(
                activity,
                MaterialDividerItemDecoration.VERTICAL,
                Measurements.dpToPx(PADDING.toFloat()) / 3
            )
        )
        recycler.setClipToOutline(true)
        recycler.setClipToPadding(false)

        recycler.setLayoutManager(LinearLayoutManager(activity))
        recycler.setAdapter(adapter)

        val entry = Map.entry(recycler, adapter)
        recyclers[identifier] = entry

        return entry
    }

    open fun addShortcuts(launcherApps: LauncherApps, shortcuts: List<ShortcutInfo?>) {
        if (shortcuts.isEmpty()) {
            return
        }

        val adapter = getRecycler(SHORTCUT_GROUP_ID).value
        var index = 0
        while (index < shortcuts.size && shortcutCount < MAX_SHORTCUT_COUNT) {
            val shortcut = shortcuts[index]

            if (shortcut != null) {
                val label: CharSequence? = shortcut.getShortLabel()
                var icon: Drawable? = launcherApps.getShortcutIconDrawable(
                    shortcut,
                    Measurements.getDotsPerInch()
                )

                if (icon == null) {
                    if (label == null || label.toString().isBlank()) {
                        index++
                        continue
                    }

                    icon = generateCharacterDrawable(label.toString())
                }

                var drawable = icon!!
                val width = Math.max(1, drawable.getIntrinsicWidth())
                val height = Math.max(1, drawable.getIntrinsicHeight())

                val paddingHorizontal = (Math.max(width, height) - width) / 2
                val paddingVertical = (Math.max(width, height) - height) / 2

                val bitmap = createBitmap(width + 2 * paddingHorizontal, height + 2 * paddingVertical)

                val canvas = Canvas(bitmap)
                drawable.setBounds(
                    paddingHorizontal,
                    paddingVertical,
                    width + paddingHorizontal,
                    height + paddingVertical
                )
                drawable.draw(canvas)

                if (drawable is BitmapDrawable || drawable is VectorDrawable) {
                    val threshold = bitmap.getWidth() / 10

                    val centerX = bitmap.getWidth() / 2
                    val centerY = bitmap.getWidth() / 2
                    var target = bitmap.getWidth() / 2

                    while (
                        target > threshold &&
                        Color.alpha(bitmap[centerX, target]) == 255 &&
                        Color.alpha(bitmap[target, centerY]) == 255 &&
                        Color.alpha(bitmap[centerX, width - target]) == 255 &&
                        Color.alpha(bitmap[height - target, centerY]) == 255 &&
                        Color.alpha(
                            bitmap[centerX / 2 + target, centerY / 2 + target]
                        ) == 255 &&
                        Color.alpha(
                            bitmap[centerY / 2 + target, centerX / 2 + target]
                        ) == 255
                    ) {
                        target -= 2
                    }

                    drawable = if (target <= threshold) {
                        bitmap.toDrawable(activity.getResources())
                    } else {
                        LayerDrawable(
                            arrayOf(
                                Color.WHITE.toDrawable(),
                                InsetDrawable(
                                    bitmap.toDrawable(activity.getResources()),
                                    INSET_FRACTION
                                )
                            )
                        )
                    }
                } else {
                    drawable = bitmap.toDrawable(activity.getResources())
                }

                if (label != null) {
                    adapter.add(
                        Item(
                            label.toString(),
                            drawable,
                            View.OnClickListener {
                                launcherApps.startShortcut(shortcut, null, null)
                            }
                        )
                    )

                    shortcutCount++
                }
            }

            index++
        }
    }

    private fun generateCharacterDrawable(text: String?): Drawable? {
        if (text == null || text.isBlank()) {
            return null
        }

        val bitmap = createBitmap(ICON_SIZE, ICON_SIZE)

        val paint = Paint()
        paint.setStyle(Paint.Style.FILL)
        paint.setColor(activity.getAttributeData(com.google.android.material.R.attr.colorOnPrimaryContainer))
        paint.setTextAlign(Paint.Align.CENTER)
        paint.setTypeface(ResourcesCompat.getFont(activity, R.font.dm_sans_medium))
        paint.setTextSize(ICON_SIZE * 0.7f)

        val canvas = Canvas(bitmap)
        canvas.drawColor(activity.getAttributeData(com.google.android.material.R.attr.colorPrimaryContainer))
        canvas.drawText(
            text[0].toString(),
            bitmap.getWidth() / 2f,
            (canvas.getHeight() / 2f - (paint.descent() + paint.ascent()) / 2).toInt().toFloat(),
            paint
        )

        return bitmap.toDrawable(activity.getResources())
    }

    open fun add(item: Item?) {
        val adapter = getRecycler(GENERAL_ID).value

        adapter.add(item)
    }

    open fun add(items: List<Item?>) {
        for (item in items) {
            add(item)
        }
    }

    open fun setOnDismissListener(listener: PopupWindow.OnDismissListener?) {
        popupWindow.setOnDismissListener {
            dismissListener.onDismiss()

            listener?.onDismiss()
        }
    }

    open fun show(activity: Activity, parent: View, pivotAxis: Short): PopupWindow? {
        return show(activity, parent, null, pivotAxis)
    }

    open fun show(
        activity: Activity,
        parent: View,
        pivotAxis: Short,
        interceptTouches: Boolean
    ): PopupWindow? {
        return show(activity, parent, null, pivotAxis, interceptTouches)
    }

    open fun show(
        activity: Activity,
        parent: View,
        margins: Rect?,
        pivotAxis: Short
    ): PopupWindow? {
        return show(activity, parent, margins, pivotAxis, false)
    }

    open fun show(
        activity: Activity,
        parent: View,
        margins: Rect?,
        pivotAxis: Short,
        interceptTouches: Boolean
    ): PopupWindow? {
        val window: Window? = activity.getWindow()

        if (window == null) {
            return null
        }

        val location = IntArray(2)
        parent.getLocationInWindow(location)

        val width = parent.getMeasuredWidth()
        val height = parent.getMeasuredHeight()

        var gravity = Gravity.NO_GRAVITY

        if (window.getDecorView().getWidth() / 2 < location[0] + width / 2) {
            gravity = gravity or Gravity.RIGHT

            location[0] = window.getDecorView().getWidth() -
                    location[0] - (parent.getWidth() * parent.getScaleX()).toInt()

            if (margins != null && Measurements.isLandscape()) {
                location[0] += margins.right
            }
        } else {
            gravity = gravity or Gravity.LEFT

            if (margins != null && Measurements.isLandscape()) {
                location[0] += margins.left
            }
        }

        if (Measurements.isLandscape()) {
            location[0] += (parent.getWidth() * parent.getScaleX()).toInt() +
                    Measurements.dpToPx(PADDING.toFloat())
        }

        if (window.getDecorView().getHeight() / 2 > location[1] + height / 2) {
            gravity = gravity or Gravity.TOP

            if (!Measurements.isLandscape()) {
                location[1] += (parent.getHeight() * parent.getScaleY()).toInt() +
                        Measurements.dpToPx(PADDING.toFloat())

                if (margins != null) {
                    location[1] += margins.top
                }
            }
        } else {
            gravity = gravity or Gravity.BOTTOM

            location[1] = window.getDecorView().getHeight() - location[1]

            if (!Measurements.isLandscape()) {
                location[1] += Measurements.dpToPx(PADDING.toFloat())

                if (margins != null) {
                    location[1] -= margins.bottom
                }
            } else {
                location[1] -= (parent.getHeight() * parent.getScaleY()).toInt()
            }
        }

        root.post { root.setPadding(0, 0, 0, 0) }

        return showAtLocation(parent, location, gravity, pivotAxis, interceptTouches)
    }

    open fun showAtLocation(
        activity: Activity,
        parent: View,
        x: Float,
        y: Float,
        pivotAxis: Short
    ): PopupWindow? {
        return showAtLocation(activity, parent, x, y, pivotAxis, true)
    }

    open fun showAtLocation(
        activity: Activity,
        parent: View,
        x: Float,
        y: Float,
        pivotAxis: Short,
        interceptTouches: Boolean
    ): PopupWindow? {
        val window: Window? = activity.getWindow()

        if (window == null) {
            return null
        }

        val location = IntArray(2)
        parent.getLocationInWindow(location)

        val width = parent.getMeasuredWidth()
        val height = parent.getMeasuredHeight()

        var gravity = Gravity.NO_GRAVITY

        if (width / 2f < x) {
            gravity = gravity or Gravity.RIGHT

            location[0] = window.getDecorView().getWidth() -
                    location[0] - x.toInt() - Measurements.dpToPx(WIDTH.toFloat()) / 2
        } else {
            gravity = gravity or Gravity.LEFT

            location[0] = location[0] + x.toInt() - Measurements.dpToPx(WIDTH.toFloat()) / 2
        }

        if (height / 2f > y) {
            gravity = gravity or Gravity.TOP

            location[1] = (location[1] + y).toInt()
        } else {
            gravity = gravity or Gravity.BOTTOM

            location[1] = window.getDecorView().getHeight() - location[1] - y.toInt()
        }

        return showAtLocation(parent, location, gravity, pivotAxis, interceptTouches)
    }

    private fun showAtLocation(
        parent: View,
        @Size(2) location: IntArray,
        gravity: Int,
        pivotAxis: Short,
        interceptTouches: Boolean
    ): PopupWindow? {
        for (entry in recyclers.values) {
            if ((gravity and Gravity.TOP) == Gravity.TOP) {
                root.addView(entry.key, 0)
            } else {
                root.addView(entry.key)
            }
        }

        if (!popupWindow.isShowing()) {
            root.post {
                if ((pivotAxis.toInt() and PIVOT_CENTER_HORIZONTAL.toInt()) ==
                    PIVOT_CENTER_HORIZONTAL.toInt()
                ) {
                    root.setPivotX(root.getMeasuredWidth() / 2f)
                } else {
                    if ((gravity and Gravity.LEFT) == Gravity.LEFT) {
                        root.setPivotX(0f)
                    } else if ((gravity and Gravity.RIGHT) == Gravity.RIGHT) {
                        root.setPivotX(root.getMeasuredWidth().toFloat())
                    }
                }

                if ((pivotAxis.toInt() and PIVOT_CENTER_VERTICAL.toInt()) ==
                    PIVOT_CENTER_VERTICAL.toInt()
                ) {
                    root.setPivotY(root.getMeasuredHeight() / 2f)
                } else {
                    if ((gravity and Gravity.TOP) == Gravity.TOP) {
                        root.setPivotY(0f)
                    } else if ((gravity and Gravity.BOTTOM) == Gravity.BOTTOM) {
                        root.setPivotY(root.getMeasuredHeight().toFloat())
                    }
                }
            }

            val padding = Measurements.dpToPx(SCREEN_MARGIN_DP.toFloat())

            popupWindow.setWidth(Measurements.dpToPx(WIDTH.toFloat()) + padding * 2)
            popupWindow.showAtLocation(
                parent,
                gravity,
                location[0] - padding,
                location[1] - padding
            )

            val params = root.getLayoutParams() as ViewGroup.MarginLayoutParams?
            if (params != null) {
                params.leftMargin = padding
                params.topMargin = padding
                params.rightMargin = padding
                params.bottomMargin = padding
            }

            oldOrientationFlags = activity.getRequestedOrientation()

            activity.setTouchEnabled(!interceptTouches)
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
            activity.lifecycle.addObserver(observer)

            return popupWindow
        }

        return null
    }

    open fun dismiss() {
        popupWindow.dismiss()
    }

    open class Item(
        @JvmField val label: String?,
        @JvmField val icon: Drawable?,
        @JvmField val listener: View.OnClickListener?
    )
}
