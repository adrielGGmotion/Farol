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

package com.farol.launcher.sheet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import android.view.WindowInsets
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.farol.launcher.activities.launcher.Launcher
import com.farol.launcher.sheet.behavior.SheetBehavior
import com.farol.launcher.ui.utils.UiUtils
import androidx.core.view.isEmpty
import androidx.core.view.size

open class SheetsFocusController : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var pendingCheckForLongPress: CheckForLongPress? = null
    private var longClickListener: OnLongClickListener? = null
    private var sheetSlideListener: SheetDialog.OnSlideListener? = null
    private var hasPerformedLongPress = false
    private val targetPointers = ArrayList<Int>()
    private var dispatchedMoveEvent = false
    private var controllerActive = true
    private val systemGestureInsets = Rect()
    private val wrappers = arrayOfNulls<SheetWrapper>(SheetType.entries.size)
    private var deltaX = 0f
    private var deltaY = 0f
    private var sheetType: SheetType? = null
    private var moveSlop = ViewConfiguration.get(getContext()).scaledTouchSlop.toFloat()
    private var pointerX = 0f
    private var pointerY = 0f

    fun setControllerEnabled(enabled: Boolean) {
        controllerActive = enabled
    }

    private fun isTouchInSystemInsets(x: Float, y: Float): Boolean {
        val params = layoutParams as MarginLayoutParams

        return x < systemGestureInsets.left + params.leftMargin ||
                x - params.leftMargin > width - systemGestureInsets.right ||
                y < systemGestureInsets.top + params.topMargin ||
                y - params.topMargin > height - systemGestureInsets.bottom
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!controllerActive) {
            return super.onInterceptTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchInSystemInsets(ev.rawX, ev.rawY)) {
                    return false
                }

                targetPointers.clear()
                targetPointers.add(0)

                pointerX = ev.getX(getPointer(ev))
                pointerY = ev.getY(getPointer(ev))

                deltaX = 0f
                deltaY = 0f
                dispatchedMoveEvent = false

                postCheckForLongClick()

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (targetPointers.isEmpty()) {
                    return false
                }

                deltaX = pointerX - ev.getX(getPointer(ev))
                deltaY = pointerY - ev.getY(getPointer(ev))

                val movedEnough = kotlin.math.abs(deltaX) >= moveSlop || kotlin.math.abs(deltaY) >= moveSlop
                if (movedEnough) {
                    removeCheck()
                }

                return movedEnough
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCheck()

                sheetType = null
                targetPointers.clear()

                return false
            }

            else -> return false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!controllerActive) {
            return super.onTouchEvent(ev)
        }

        val action = ev.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dispatchSheetMotionEvent(MotionEvent.obtain(ev))

            removeCheck()

            sheetType = null
            targetPointers.clear()
        } else {
            if (action == MotionEvent.ACTION_DOWN) {
                if (isTouchInSystemInsets(ev.rawX, ev.rawY)) {
                    return false
                }

                targetPointers.add(0, 0)

                pointerX = ev.getX(getPointer(ev))
                pointerY = ev.getY(getPointer(ev))

                deltaX = 0f
                deltaY = 0f

                dispatchedMoveEvent = false
                dispatchSheetMotionEvent(MotionEvent.obtain(ev))
                postCheckForLongClick()
            } else {
                if (hasPerformedLongPress) {
                    hideAllSheets()
                    removeCheck()

                    return super.onTouchEvent(ev)
                }

                deltaY = pointerY - ev.getY(getPointer(ev))
                deltaX = pointerX - ev.getX(getPointer(ev))

                if (ev.action == MotionEvent.ACTION_MOVE &&
                    (kotlin.math.abs(deltaX) >= moveSlop || kotlin.math.abs(deltaY) >= moveSlop)
                ) {
                    if (sheetType == null) {
                        sheetType = if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {
                            if (kotlin.math.sign(deltaY) >= 0) SheetType.BOTTOM_SHEET else SheetType.TOP_SHEET
                        } else {
                            if (kotlin.math.sign(deltaX) >= 0) SheetType.RIGHT_SHEET else SheetType.LEFT_SHEET
                        }

                        hideAllSheets(sheetType!!)
                    }

                    removeCheck()
                }

                dispatchSheetMotionEvent(sheetType, MotionEvent.obtain(ev))
            }
        }

        return true
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val gestureInsets: Insets = compat.getInsets(WindowInsetsCompat.Type.systemGestures())

        systemGestureInsets.set(
            gestureInsets.left,
            gestureInsets.top,
            gestureInsets.right,
            gestureInsets.bottom
        )

        return super.dispatchApplyWindowInsets(insets)
    }

    private fun getPointer(event: MotionEvent): Int {
        if (targetPointers.isEmpty()) {
            return 0
        }

        val pointerId = targetPointers[0]
        val pointerIndex = event.findPointerIndex(pointerId)

        return kotlin.math.max(0, kotlin.math.min(event.pointerCount - 1, pointerIndex))
    }

    private fun removeCheck() {
        val check = pendingCheckForLongPress
        if (check != null) {
            removeCallbacks(check)

            pendingCheckForLongPress = null
        }
    }

    private fun postCheckForLongClick() {
        if (pendingCheckForLongPress == null) {
            hasPerformedLongPress = false

            pendingCheckForLongPress = CheckForLongPress()
            postDelayed(pendingCheckForLongPress, ViewConfiguration.getLongPressTimeout().toLong())
        }
    }

    override fun setOnLongClickListener(listener: OnLongClickListener?) {
        longClickListener = listener
    }

    override fun performLongClick(): Boolean = false

    override fun cancelLongPress() {
        super.cancelLongPress()

        removeCheck()
    }

    fun getLastX(): Float = pointerX

    fun getLastY(): Float = pointerY

    fun updateSheetSystemUI(value: Boolean) {
        for (wrapper in wrappers) {
            if (wrapper?.dialogFragment != null) {
                wrapper.dialogFragment.updateSheetSystemUI(value)
            }
        }
    }

    fun hasSheetFocus(): Boolean = sheetType != null && dispatchedMoveEvent

    private inner class CheckForLongPress : Runnable {
        private val mOriginalWindowAttachCount = windowAttachCount

        override fun run() {
            if (parent != null &&
                mOriginalWindowAttachCount == windowAttachCount &&
                !hasPerformedLongPress
            ) {
                if (longClickListener != null && longClickListener!!.onLongClick(this@SheetsFocusController)) {
                    hasPerformedLongPress = true

                    if (pendingCheckForLongPress != null) {
                        removeCallbacks(pendingCheckForLongPress)
                    }

                    for (instance in wrappers) {
                        if (instance != null) {
                            val behavior = instance.dialogFragment.getBehavior()

                            if (behavior != null) {
                                val dialog = instance.dialogFragment.getSheetDialog()

                                if (dialog != null) {
                                    dialog.hide()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setSlideListener(slideListener: SheetDialog.OnSlideListener?) {
        this.sheetSlideListener = slideListener
    }

    fun removeSheetDialog(vararg dialogFragmentClass: Class<out SheetDialogFragment>) {
        removeSheetDialog(dialogFragmentClass.asList())
    }

    fun removeSheetDialog(dialogFragmentClass: List<Class<out SheetDialogFragment>>) {
        for (clazz in dialogFragmentClass) {
            for (index in wrappers.indices) {
                val wrapper = wrappers[index]

                if (wrapper != null && wrapper.dialogFragment.javaClass == clazz) {
                    if (wrapper.dialogFragment.isAdded) {
                        wrapper.dialogFragment.dismissAllowingStateLoss()
                    }

                    wrappers[index] = null

                    break
                }
            }
        }
    }

    fun moveSheetDialog(launcher: Launcher, dialogFragmentClass: List<Class<out SheetDialogFragment>>) {
        removeSheetDialog(dialogFragmentClass)
        addSheetDialog(launcher, dialogFragmentClass)
    }

    fun moveSheetDialog(launcher: Launcher, vararg dialogFragmentClass: Class<out SheetDialogFragment>) {
        moveSheetDialog(launcher, dialogFragmentClass.asList())
    }

    fun addSheetDialog(launcher: Launcher, dialogFragmentClass: List<Class<out SheetDialogFragment>>) {
        val manager = launcher.supportFragmentManager

        for (clazz in dialogFragmentClass) {
            val type = SheetType.getSheetTypeForSheetDialogFragment(launcher, clazz)

            if (type == null || type == SheetType.UNDEFINED || wrappers[type.ordinal] != null) {
                continue
            }

            try {
                val existingFragment = manager.findFragmentByTag(type.toString())
                val fragment: SheetDialogFragment

                if (existingFragment != null && clazz.isInstance(existingFragment)) {
                    fragment = existingFragment as SheetDialogFragment
                } else {
                    if (existingFragment != null) {
                        manager.beginTransaction().remove(existingFragment).commitNowAllowingStateLoss()
                    }

                    val constructor = clazz.getConstructor(SheetType::class.java)
                    fragment = constructor.newInstance(type)
                }

                wrappers[type.ordinal] = SheetWrapper(launcher, type, fragment)
            } catch (exception: Exception) {
                throw RuntimeException(
                    clazz.name + "(" + SheetType::class.java.name + ")" +
                            "has to be visible to public scope."
                )
            }
        }
    }

    fun addSheetDialog(launcher: Launcher, vararg dialogFragmentClass: Class<out SheetDialogFragment>) {
        addSheetDialog(launcher, dialogFragmentClass.asList())
    }

    private fun dispatchSheetMotionEvent(event: MotionEvent) {
        for (wrapper in wrappers) {
            if (wrapper != null) {
                if (wrapper.dialogFragment.isAdded &&
                    wrapper.dialogFragment.onMotionEvent(event) &&
                    event.action == MotionEvent.ACTION_MOVE
                ) {
                    dispatchedMoveEvent = true
                }
            }
        }
    }

    private fun dispatchSheetMotionEvent(type: SheetType?, event: MotionEvent) {
        if (type == null) {
            return
        }

        for (index in wrappers.indices) {
            if (index == type.ordinal) {
                continue
            }

            if (wrappers[index] != null) {
                val behavior = wrappers[index]!!.dialogFragment.getBehavior()

                if (behavior != null &&
                    (behavior.getState() == SheetBehavior.STATE_SETTLING || behavior.getState() == SheetBehavior.STATE_EXPANDED)
                ) {
                    return
                }
            }
        }

        val wrapper = wrappers[type.ordinal]

        if (wrapper != null) {
            if (wrapper.dialogFragment.isAdded) {
                if (wrapper.dialogFragment.onMotionEvent(event) && event.action == MotionEvent.ACTION_MOVE) {
                    dispatchedMoveEvent = true
                }
            } else {
                wrapper.show()
            }
        } else if (type == SheetType.TOP_SHEET) {
            UiUtils.expandStatusBar(context)
        }
    }

    fun hideAllSheets(vararg keepVisible: SheetType) {
        for (index in wrappers.indices) {
            var hide = true

            for (type in keepVisible) {
                if (type.ordinal == index) {
                    hide = false

                    break
                }
            }

            if (hide) {
                val instance = wrappers[index]

                if (instance != null && instance.dialogFragment.getSheetDialog() != null) {
                    instance.dialogFragment.hide(false)
                }
            }
        }
    }

    fun interface OnDragStartListener {
        fun onDragStart()
    }

    interface OnLongClickEventListener {
        fun onDown(duration: Long)

        fun onFinished()
    }

    inner class SheetWrapper(
        launcher: Launcher,
        type: SheetType,
        val dialogFragment: SheetDialogFragment
    ) {
        private var showRunnable: Runnable? = null

        init {
            dialogFragment.isCancelable = false
            dialogFragment.setOnSlideListener { slideOffset -> sheetSlideListener?.onSlide(slideOffset) }

            val manager: FragmentManager = launcher.supportFragmentManager
            if (dialogFragment.requiresEagerInitialization() && manager.findFragmentByTag(type.toString()) == null) {
                manager.beginTransaction()
                    .add(dialogFragment, type.toString())
                    .commitNowAllowingStateLoss()
            }

            showRunnable = Runnable {
                val runnableManager = launcher.supportFragmentManager

                if (!runnableManager.isDestroyed && runnableManager.findFragmentByTag(type.toString()) == null) {
                    dialogFragment.show(runnableManager, type.toString())
                }

                showRunnable = null
            }
        }

        fun show() {
            showRunnable?.run()
        }
    }

    companion object {
        /**
         * Use this if your view is or can be a direct or indirect child of SheetFocusController
         *
         * @param clickListener click listener to invoke on a valid gesture
         * @return touch listener
         */
        @JvmStatic
        fun createClickTouchListener(clickListener: OnClickListener?): OnTouchListener {
            return createClickTouchListener(clickListener, null, null)
        }

        /**
         * Use this if your view is or can be a direct or indirect child of SheetFocusController
         *
         * @param clickListener          click listener to invoke on a valid gesture
         * @param longClickListener      long click listener to invoke on a valid gesture
         * @param longClickEventListener event listener for long click. <code>longClickListener</code> has to be provided.
         * @return touch listener
         */
        @JvmStatic
        fun createClickTouchListener(
            clickListener: OnClickListener?,
            longClickListener: OnLongClickListener?,
            longClickEventListener: OnLongClickEventListener?
        ): OnTouchListener {
            return createClickTouchListener(clickListener, longClickListener, longClickEventListener, null, null, null)
        }

        /**
         * Use this if your view is or can be a direct or indirect child of SheetFocusController
         *
         * @param clickListener          click listener to invoke on a valid gesture
         * @param longClickListener      long click listener to invoke on a valid gesture
         * @param longClickEventListener event listener for long click. <code>longClickListener</code> has to be provided.
         * @param viewHolder             target view holder
         * @param itemTouchHelper        drag RecyclerView item touch helper
         * @param dragStartListener      event listener for when a drag starts
         * @return touch listener
         */
        @JvmStatic
        fun createClickTouchListener(
            clickListener: OnClickListener?,
            longClickListener: OnLongClickListener?,
            longClickEventListener: OnLongClickEventListener?,
            viewHolder: RecyclerView.ViewHolder?,
            itemTouchHelper: ItemTouchHelper?,
            dragStartListener: OnDragStartListener?
        ): OnTouchListener {
            return object : OnTouchListener {
                private var longPressPerformed = false
                private var longPressRunnable: Runnable? = null
                private var isClickCandidate = false
                private var isFinishedCalled = false
                private var dragStarted = false
                private var dragReady = false
                private var touchSlop: Int? = null
                private var startX = 0f
                private var startY = 0f

                @SuppressLint("ClickableViewAccessibility")
                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    if (touchSlop == null) {
                        touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
                    }

                    val parentController = findParentController(view)

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y

                            longPressPerformed = false
                            isClickCandidate = true
                            isFinishedCalled = false
                            dragStarted = false
                            dragReady = false

                            if (longClickListener != null && parentController != null) {
                                parentController.cancelLongPress()
                            }

                            longPressRunnable = Runnable {
                                dragReady = true

                                if (longClickListener != null) {
                                    longPressPerformed = longClickListener.onLongClick(view)

                                    triggerFinished()
                                }
                            }

                            val duration = ViewConfiguration.getLongPressTimeout()
                            view.postDelayed(longPressRunnable, duration.toLong())
                            if (longClickListener != null && longClickEventListener != null) {
                                longClickEventListener.onDown(duration.toLong())
                            }

                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = kotlin.math.abs(event.x - startX)
                            val dy = kotlin.math.abs(event.y - startY)

                            if (dx > touchSlop!! || dy > touchSlop!!) {
                                isClickCandidate = false

                                if (dragReady && !dragStarted && itemTouchHelper != null && viewHolder != null) {
                                    triggerFinished()
                                    dragStarted = true

                                    if (view.parent != null) {
                                        view.parent.requestDisallowInterceptTouchEvent(false)
                                    }

                                    itemTouchHelper.startDrag(viewHolder)

                                    if (dragStartListener != null) {
                                        dragStartListener.onDragStart()
                                    }

                                    return true
                                }

                                if (!dragReady && longPressRunnable != null) {
                                    view.removeCallbacks(longPressRunnable)
                                }
                            }
                            return dragStarted
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (longPressRunnable != null) {
                                view.removeCallbacks(longPressRunnable)
                            }

                            val parentLongPressed = parentController != null && parentController.hasPerformedLongPress

                            if (event.actionMasked == MotionEvent.ACTION_UP && isClickCandidate &&
                                !longPressPerformed && !parentLongPressed && clickListener != null
                            ) {
                                clickListener.onClick(view)
                            }

                            triggerFinished()

                            isClickCandidate = false
                            longPressPerformed = false
                            dragReady = false
                            dragStarted = false

                            return true
                        }

                        else -> return false
                    }
                }

                private fun findParentController(view: View): SheetsFocusController? {
                    var parent: ViewParent? = view.parent

                    while (parent is View) {
                        if (parent is SheetsFocusController) {
                            return parent
                        }

                        parent = parent.parent
                    }

                    return null
                }

                private fun triggerFinished() {
                    if (!isFinishedCalled && longClickListener != null && longClickEventListener != null) {
                        longClickEventListener.onFinished()
                        isFinishedCalled = true
                    }
                }
            }
        }
    }
}
